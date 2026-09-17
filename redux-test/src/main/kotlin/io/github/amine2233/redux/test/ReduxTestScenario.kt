package io.github.amine2233.redux.test

import io.github.amine2233.redux.Action
import io.github.amine2233.redux.Effect
import io.github.amine2233.redux.Lens
import io.github.amine2233.redux.Middleware
import io.github.amine2233.redux.Prism
import io.github.amine2233.redux.Reducer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

public class ReduxTestScenario<State, A : Action, E : Effect>(
    public val store: TestStore<State, A, E>,
) {
    public suspend fun whenDispatch(action: A) {
        store.dispatch(action)
    }

    public suspend fun whenDispatch(vararg actions: A) {
        actions.forEach { store.dispatch(it) }
    }

    public suspend fun <Child> whenDispatch(
        prism: Prism<A, Child>,
        vararg actions: Child,
    ) {
        actions.forEach { store.dispatch(prism.embed(it)) }
    }

    public fun expectState(assertion: (State) -> Unit) {
        assertion(store.getState())
    }

    public fun <Part> expectState(
        lens: Lens<State, Part>,
        assertion: (Part) -> Unit,
    ) {
        assertion(lens.get(store.getState()))
    }

    public fun expectSuccess(assertion: (State) -> Unit): Unit = expectState(assertion)

    public fun expectStates(assertion: (List<State>) -> Unit) {
        assertion(store.states())
    }

    public fun <Part> expectStates(
        lens: Lens<State, Part>,
        assertion: (List<Part>) -> Unit,
    ) {
        assertion(store.states().map(lens.get))
    }

    public fun expectActions(assertion: (List<A>) -> Unit) {
        assertion(store.actions())
    }

    public fun <Child> expectActions(
        prism: Prism<A, Child>,
        assertion: (List<Child>) -> Unit,
    ) {
        assertion(store.actions().mapNotNull(prism.extract))
    }

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

public suspend fun <State, A : Action, E : Effect> scenario(
    initialState: State,
    reducer: Reducer<State, A>,
    middlewares: List<Middleware<State, A, E>> = emptyList(),
    block: suspend ReduxTestScenario<State, A, E>.() -> Unit,
) {
    ReduxTestScenario(TestStore(initialState, reducer, middlewares)).block()
}

@JvmName("scenarioDummy")
public suspend fun <State, A : Action> scenario(
    initialState: State,
    reducer: Reducer<State, A>,
    middlewares: List<Middleware<State, A, DummyEffect>> = emptyList(),
    block: suspend ReduxTestScenario<State, A, DummyEffect>.() -> Unit,
) {
    ReduxTestScenario(TestStore(initialState, reducer, middlewares)).block()
}
