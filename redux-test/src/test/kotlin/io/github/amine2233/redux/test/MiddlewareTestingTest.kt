package io.github.amine2233.redux.test

import io.github.amine2233.redux.Middleware
import io.github.amine2233.redux.keyed
import io.github.amine2233.redux.lifted
import io.github.amine2233.redux.optional
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MiddlewareTestingTest {

    @Test
    fun `forwardedActions returns the sequence a middleware emits`() = runTest {
        val forwarded = loadMiddleware.forwardedActions(CounterState(), CounterAction.LoadRequested)

        assertEquals(listOf(CounterAction.LoadStarted, CounterAction.LoadSucceeded(42)), forwarded)
    }

    @Test
    fun `forwardedActions is empty when the middleware swallows the action`() = runTest {
        val swallowing = Middleware<CounterState, CounterAction> { _, _, _ -> }

        assertEquals(emptyList(), swallowing.forwardedActions(CounterState(), CounterAction.Increment))
    }

    @Test
    fun `forwardedActions exposes the fixed state to the middleware`() = runTest {
        val echo = Middleware<CounterState, CounterAction> { getState, _, next ->
            next(CounterAction.LoadSucceeded(getState().count))
        }

        assertEquals(listOf(CounterAction.LoadSucceeded(7)), echo.forwardedActions(CounterState(count = 7), CounterAction.Increment))
    }

    @Test
    fun `lifted middleware re-embeds forwarded actions`() = runTest {
        val lifted = loadMiddleware.lifted(counterLens, counterPrism)

        val forwarded = lifted.forwardedActions(AppState(), AppAction.Counter(CounterAction.LoadRequested))

        assertEquals(listOf(AppAction.Counter(CounterAction.LoadStarted), AppAction.Counter(CounterAction.LoadSucceeded(42))), forwarded)
        assertEquals(listOf(AppAction.SetTitle("x")), lifted.forwardedActions(AppState(), AppAction.SetTitle("x")))
    }

    @Test
    fun `keyed middleware addresses the entry and passes unknown keys through`() = runTest {
        val keyed = loadMiddleware.keyed(countersLens, keyedPrism)
        val state = AppState(counters = mapOf("a" to CounterState()))

        assertEquals(
            listOf(AppAction.Keyed("a", CounterAction.LoadStarted), AppAction.Keyed("a", CounterAction.LoadSucceeded(42))),
            keyed.forwardedActions(state, AppAction.Keyed("a", CounterAction.LoadRequested))
        )
        assertEquals(
            listOf(AppAction.Keyed("missing", CounterAction.LoadRequested)),
            keyed.forwardedActions(state, AppAction.Keyed("missing", CounterAction.LoadRequested))
        )
    }

    @Test
    fun `optional middleware passes through on null state`() = runTest {
        val optional = loadMiddleware.optional()

        assertEquals(listOf(CounterAction.LoadRequested), optional.forwardedActions(null, CounterAction.LoadRequested))
        assertEquals(listOf(CounterAction.LoadStarted, CounterAction.LoadSucceeded(42)), optional.forwardedActions(CounterState(), CounterAction.LoadRequested))
    }
}
