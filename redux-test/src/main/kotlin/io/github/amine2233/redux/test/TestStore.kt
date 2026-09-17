package io.github.amine2233.redux.test

import io.github.amine2233.redux.Action
import io.github.amine2233.redux.DefaultStore
import io.github.amine2233.redux.Effect
import io.github.amine2233.redux.Middleware
import io.github.amine2233.redux.Reducer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

public interface DummyEffect : Effect

public class TestStore<State, A : Action, E : Effect>(
    initialState: State,
    reducer: Reducer<State, A>,
    middlewares: List<Middleware<State, A, E>> = emptyList(),
    scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
) {
    private val dispatchedActions = mutableListOf<A>()
    private val stateHistory = mutableListOf(initialState)

    private val recorder =
        Middleware<State, A, E> { store, action, next ->
            dispatchedActions += action
            next(action)
            stateHistory += store.state.value
        }

    private val store = DefaultStore<State, A, E>(initialState, reducer, middlewares + recorder, emptyList(), scope)

    public val state: StateFlow<State>
        get() = store.state

    public fun getState(): State = store.state.value

    public suspend fun dispatch(action: A) {
        store.dispatch(action)
    }

    public fun actions(): List<A> = dispatchedActions.toList()

    public fun states(): List<State> = stateHistory.toList()
}
