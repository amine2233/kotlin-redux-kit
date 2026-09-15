package io.github.amine2233.redux.test

import io.github.amine2233.redux.Action
import io.github.amine2233.redux.Lens
import io.github.amine2233.redux.Middleware
import io.github.amine2233.redux.Prism
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
    public val store: TestStore<State, A>,
) {
    public suspend fun whenDispatch(action: A) {
        store.dispatch(action)
    }

    public suspend fun whenDispatch(vararg actions: A) {
        actions.forEach { store.dispatch(it) }
    }

    /** Dispatches child actions embedded through [prism], e.g. `whenDispatch(counterPrism, Increment)`. */
    public suspend fun <Child> whenDispatch(
        prism: Prism<A, Child>,
        vararg actions: Child,
    ) {
        actions.forEach { store.dispatch(prism.embed(it)) }
    }

    public fun expectState(assertion: (State) -> Unit) {
        assertion(store.getState())
    }

    /** Asserts on the slice of the state read through [lens]. */
    public fun <Part> expectState(
        lens: Lens<State, Part>,
        assertion: (Part) -> Unit,
    ) {
        assertion(lens.get(store.getState()))
    }

    /** Alias of [expectState] for scenarios describing a functional success. */
    public fun expectSuccess(assertion: (State) -> Unit): Unit = expectState(assertion)

    public fun expectStates(assertion: (List<State>) -> Unit) {
        assertion(store.states())
    }

    /** Asserts on the history of the slice read through [lens]. */
    public fun <Part> expectStates(
        lens: Lens<State, Part>,
        assertion: (List<Part>) -> Unit,
    ) {
        assertion(store.states().map(lens.get))
    }

    public fun expectActions(assertion: (List<A>) -> Unit) {
        assertion(store.actions())
    }

    /** Asserts only on the child actions [prism] extracts, in dispatch order. */
    public fun <Child> expectActions(
        prism: Prism<A, Child>,
        assertion: (List<Child>) -> Unit,
    ) {
        assertion(store.actions().mapNotNull(prism.extract))
    }

    /** Polls the state until [assertion] holds; fails after [timeoutMillis]. Works with `runTest` virtual time. */
    public suspend fun expectEventually(
        timeoutMillis: Long = 1_000,
        pollMillis: Long = 10,
        assertion: (State) -> Boolean,
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
    block: suspend ReduxTestScenario<State, A>.() -> Unit,
) {
    ReduxTestScenario(TestStore(initialState, reducer, middlewares)).block()
}
