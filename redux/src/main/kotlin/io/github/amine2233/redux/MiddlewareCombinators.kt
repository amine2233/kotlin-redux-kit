package io.github.amine2233.redux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// We create a wrapper Store to adapt the state and actions for the lifted middleware
private class LiftedStore<S, A : Action, E : Effect, LiftedS, LiftedA : Action>(
    private val originalStore: Store<S, A, E>,
    private val lens: Lens<S, LiftedS>,
    private val prism: Prism<A, LiftedA>,
) : Store<LiftedS, LiftedA, E> {
    override val state: StateFlow<LiftedS> = originalStore.select(lens)
    override val effects: Flow<E> = originalStore.effects
    override val scope: CoroutineScope = originalStore.scope

    override fun dispatch(action: LiftedA) {
        originalStore.dispatch(prism.embed(action))
    }

    override fun emitEffect(effect: E) {
        originalStore.emitEffect(effect)
    }

    override fun dispatchFrom(flow: Flow<LiftedA>): Job =
        scope.launch {
            flow.collect { dispatch(it) }
        }

    override fun dispatchFrom(channel: ReceiveChannel<LiftedA>): Job =
        scope.launch {
            for (action in channel) dispatch(action)
        }

    override fun <SubState> select(lens: Lens<LiftedS, SubState>): StateFlow<SubState> = originalStore.select(this.lens.then(lens))

    override fun close() {
        // usually close is not called from middleware
    }
}

public fun <State, A : Action, E : Effect, LiftedState, LiftedAction : Action> Middleware<LiftedState, LiftedAction, E>.lifted(
    lens: Lens<State, LiftedState>,
    prism: Prism<A, LiftedAction>,
): Middleware<State, A, E> =
    Middleware { store, action, next ->
        val lowered = prism.extract(action) ?: return@Middleware next(action)
        val liftedStore = LiftedStore(store, lens, prism)
        intercept(liftedStore, lowered) { forwarded -> next(prism.embed(forwarded)) }
    }

public fun <State, A : Action, E : Effect> Middleware<State, A, E>.optional(): Middleware<State?, A, E> =
    Middleware { store, action, next ->
        if (store.state.value == null) return@Middleware next(action)

        // Creating an Optional store is more complex, but for simplicity here:
        val optionalLens =
            Lens<State?, State>(
                get = { checkNotNull(it) { "State became null while ${this::class.simpleName} was running" } },
                set = { _, subVal -> subVal },
            )
        val prism = Prism<A, A>(embed = { it }, extract = { it })
        val liftedStore = LiftedStore(store, optionalLens, prism)
        intercept(liftedStore, action, next)
    }

public fun <State, A : Action, E : Effect, KeyedState, KeyedAction : Action, Key> Middleware<KeyedState, KeyedAction, E>.keyed(
    lens: Lens<State, Map<Key, KeyedState>>,
    prism: Prism<A, Pair<Key, KeyedAction>>,
): Middleware<State, A, E> =
    Middleware { store, action, next ->
        val (key, lowered) = prism.extract(action) ?: return@Middleware next(action)
        if (key !in lens.get(store.state.value)) return@Middleware next(action)

        val keyedLens =
            Lens<State, KeyedState>(
                get = { lens.get(it).getValue(key) },
                set = { state, subVal -> lens.set(state, lens.get(state) + (key to subVal)) },
            )
        val keyedPrism =
            Prism<A, KeyedAction>(
                embed = { prism.embed(key to it) },
                extract = { prism.extract(it)?.takeIf { pair -> pair.first == key }?.second },
            )
        val liftedStore = LiftedStore(store, keyedLens, keyedPrism)
        intercept(liftedStore, lowered) { forwarded -> next(prism.embed(key to forwarded)) }
    }

public fun <State, A : Action, E : Effect, IndexedState, IndexedAction : Action> Middleware<IndexedState, IndexedAction, E>.offset(
    lens: Lens<State, List<IndexedState>>,
    prism: Prism<A, Pair<Int, IndexedAction>>,
): Middleware<State, A, E> =
    Middleware { store, action, next ->
        val (index, lowered) = prism.extract(action) ?: return@Middleware next(action)
        if (index !in lens.get(store.state.value).indices) return@Middleware next(action)

        val offsetLens =
            Lens<State, IndexedState>(
                get = { lens.get(it)[index] },
                set = { state, subVal ->
                    val list = lens.get(state).toMutableList()
                    list[index] = subVal
                    lens.set(state, list)
                },
            )
        val offsetPrism =
            Prism<A, IndexedAction>(
                embed = { prism.embed(index to it) },
                extract = { prism.extract(it)?.takeIf { pair -> pair.first == index }?.second },
            )
        val liftedStore = LiftedStore(store, offsetLens, offsetPrism)
        intercept(liftedStore, lowered) { forwarded -> next(prism.embed(index to forwarded)) }
    }
