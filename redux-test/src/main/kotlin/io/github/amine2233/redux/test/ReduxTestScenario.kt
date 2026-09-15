package io.github.amine2233.redux.test

import io.github.amine2233.redux.Action
import io.github.amine2233.redux.Middleware
import io.github.amine2233.redux.Reducer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/**
 * Given/when/then DSL: build a [TestStore] from a precise initial state, dispatch actions,
 * then assert on the final state, the state history or the emitted actions.
 *
 * ```
 * scenario(initialState = CounterState(0), reducer = counterReducer) {
 *     whenDispatch(CounterAction.Increment)
 *     expectState { state -> assertEquals(1, state.count) }
 * }
 * ```
 */
public class ReduxTestScenario<State, A : Action>(
    public val store: TestStore<State, A>
) {
    public suspend fun whenDispatch(action: A) {
        store.dispatch(action)
    }

    public suspend fun whenDispatch(vararg actions: A) {
        actions.forEach { store.dispatch(it) }
    }

    public fun expectState(assertion: (State) -> Unit) {
        assertion(store.getState())
    }

    /** Alias of [expectState] for scenarios describing a functional success. */
    public fun expectSuccess(assertion: (State) -> Unit): Unit = expectState(assertion)

    public fun expectStates(assertion: (List<State>) -> Unit) {
        assertion(store.states())
    }

    public fun expectActions(assertion: (List<A>) -> Unit) {
        assertion(store.actions())
    }

    /** Polls the state until [assertion] holds; fails after [timeoutMillis]. Works with `runTest` virtual time. */
    public suspend fun expectEventually(
        timeoutMillis: Long = 1_000,
        pollMillis: Long = 10,
        assertion: (State) -> Boolean
    ) {
        try {
            withTimeout(timeoutMillis) {
                while (!assertion(store.getState())) delay(pollMillis)
            }
        } catch (_: TimeoutCancellationException) {
            error("Expected state condition was not met within $timeoutMillis ms")
        }
    }
}

public suspend fun <State, A : Action> scenario(
    initialState: State,
    reducer: Reducer<State, A>,
    middlewares: List<Middleware<State, A>> = emptyList(),
    block: suspend ReduxTestScenario<State, A>.() -> Unit
) {
    ReduxTestScenario(TestStore(initialState, reducer, middlewares)).block()
}
