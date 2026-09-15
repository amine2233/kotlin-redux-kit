package io.github.amine2233.redux.test

import io.github.amine2233.redux.Action
import io.github.amine2233.redux.Middleware
import io.github.amine2233.redux.Reducer
import io.github.amine2233.redux.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

/**
 * Wraps a real [Store] driven by the production reducer and middlewares, and keeps the full
 * history of reduced states and of every action that reached the reducer (including the ones
 * forwarded by middlewares).
 */
public class TestStore<State, A : Action>(
    initialState: State,
    reducer: Reducer<State, A>,
    middlewares: List<Middleware<State, A>> = emptyList(),
    scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
) {
    private val dispatchedActions = mutableListOf<A>()
    private val stateHistory = mutableListOf(initialState)

    private val recorder =
        Middleware<State, A> { getState, action, next ->
            dispatchedActions += action
            next(action)
            stateHistory += getState()
        }

    private val store = Store(initialState, reducer, middlewares + recorder, scope)

    public val state: StateFlow<State>
        get() = store.state

    public fun getState(): State = store.state.value

    public suspend fun dispatch(action: A): Unit = store.dispatchSuspend(action)

    public fun actions(): List<A> = dispatchedActions.toList()

    public fun states(): List<State> = stateHistory.toList()
}
