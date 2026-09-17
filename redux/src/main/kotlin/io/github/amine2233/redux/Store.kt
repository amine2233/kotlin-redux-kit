package io.github.amine2233.redux

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

public interface Store<S, A : Action, E : Effect> {
    public val state: StateFlow<S>
    public val effects: Flow<E>
    public val scope: CoroutineScope

    public fun dispatch(action: A)

    public fun emitEffect(effect: E)

    public fun dispatchFrom(flow: Flow<A>): Job

    public fun dispatchFrom(channel: ReceiveChannel<A>): Job

    public fun <SubState> select(lens: Lens<S, SubState>): StateFlow<SubState>

    public fun close()
}

public class DefaultStore<S, A : Action, E : Effect>(
    initialState: S,
    private val reducer: Reducer<S, A>,
    private val middlewares: List<Middleware<S, A, E>> = emptyList(),
    private val services: List<StoreService<S, A, E>> = emptyList(),
    override val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    bufferCapacity: Int = 64,
) : Store<S, A, E> {
    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<E>(Channel.BUFFERED)
    override val effects: Flow<E> = _effects.receiveAsFlow()

    private val actionQueue =
        Channel<A>(
            capacity = bufferCapacity,
            onBufferOverflow = BufferOverflow.SUSPEND,
        )

    init {
        scope.launch {
            val chain = buildChain()
            for (action in actionQueue) {
                chain(action)
            }
        }

        services.forEach { service ->
            scope.launch(
                CoroutineExceptionHandler { _, throwable ->
                    println("Error in StoreService: ${throwable.message}")
                },
            ) {
                service.start(this@DefaultStore, this)
            }
        }
    }

    override fun dispatch(action: A) {
        val result = actionQueue.trySend(action)
        if (result.isFailure) {
            scope.launch { actionQueue.send(action) }
        }
    }

    override fun emitEffect(effect: E) {
        scope.launch {
            _effects.send(effect)
        }
    }

    override fun dispatchFrom(flow: Flow<A>): Job =
        scope.launch {
            flow.collect { dispatch(it) }
        }

    override fun dispatchFrom(channel: ReceiveChannel<A>): Job =
        scope.launch {
            for (action in channel) {
                dispatch(action)
            }
        }

    override fun <SubState> select(lens: Lens<S, SubState>): StateFlow<SubState> {
        val upstream = this.state
        return object : StateFlow<SubState> {
            override val value: SubState
                get() = lens.get(upstream.value)
            override val replayCache: List<SubState>
                get() = listOf(value)

            override suspend fun collect(collector: FlowCollector<SubState>): Nothing {
                upstream.map { lens.get(it) }.distinctUntilChanged().collect(collector)
                error("collect should never terminate normally")
            }
        }
    }

    override fun close() {
        services.forEach { it.stop() }
        actionQueue.close()
        _effects.close()
        scope.cancel()
    }

    private fun buildChain(): suspend (A) -> Unit {
        val initialChain: suspend (A) -> Unit = { finalAction: A ->
            _state.value = reducer.reduce(_state.value, finalAction)
        }
        return middlewares.foldRight(initialChain) { middleware, next ->
            { action: A -> middleware.intercept(this@DefaultStore, action, next) }
        }
    }
}
