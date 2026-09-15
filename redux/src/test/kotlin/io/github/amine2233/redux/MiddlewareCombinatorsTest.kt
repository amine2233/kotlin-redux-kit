package io.github.amine2233.redux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MiddlewareCombinatorsTest {
    /** Sees the child state, then forwards a `Log` describing it before the original action. */
    private val logging =
        Middleware<CounterState, CounterAction> { getState, action, next ->
            next(CounterAction.Log("count=${getState().count}"))
            next(action)
        }

    private fun CoroutineScope.store(
        middleware: Middleware<AppState, AppAction>,
        initial: AppState = AppState(),
    ) = Store(
        initialState = initial,
        reducer =
            CombinedReducer(
                appReducer,
                counterReducer.lifted(counterLens, counterPrism),
                counterReducer.keyed(countersLens, keyedPrism),
                counterReducer.offset(listLens, indexedPrism),
            ),
        middlewares = listOf(middleware),
        scope = this,
    )

    @Test
    fun `lifted runs the child middleware with the lowered state and re-embeds forwarded actions`() =
        runTest {
            val store = store(logging.lifted(counterLens, counterPrism), initial = AppState(counter = CounterState(count = 4)))

            store.dispatchSuspend(AppAction.Counter(CounterAction.Increment))

            assertEquals(CounterState(count = 5, log = listOf("count=4")), store.state.value.counter)
        }

    @Test
    fun `lifted passes non-matching actions through`() =
        runTest {
            val store = store(logging.lifted(counterLens, counterPrism))

            store.dispatchSuspend(AppAction.SetTitle("hello"))

            assertEquals(AppState(title = "hello"), store.state.value)
        }

    @Test
    fun `optional passes through while the state is null and runs once present`() =
        runTest {
            var seen = 0
            val counting =
                Middleware<CounterState, CounterAction> { _, action, next ->
                    seen++
                    next(action)
                }
            val optional = counting.optional()

            optional.intercept({ null }, CounterAction.Increment) {}
            assertEquals(0, seen)

            optional.intercept({ CounterState() }, CounterAction.Increment) {}
            assertEquals(1, seen)
        }

    @Test
    fun `keyed addresses the entry carried by the action and passes unknown keys through`() =
        runTest {
            val store = store(logging.keyed(countersLens, keyedPrism), initial = AppState(counters = mapOf("a" to CounterState(count = 2))))

            store.dispatchSuspend(AppAction.Keyed("a", CounterAction.Increment))
            store.dispatchSuspend(AppAction.Keyed("missing", CounterAction.Increment))

            assertEquals(mapOf("a" to CounterState(count = 3, log = listOf("count=2"))), store.state.value.counters)
        }

    @Test
    fun `offset addresses the element carried by the action and passes out-of-range indices through`() =
        runTest {
            val store =
                store(logging.offset(listLens, indexedPrism), initial = AppState(list = listOf(CounterState(), CounterState(count = 8))))

            store.dispatchSuspend(AppAction.Indexed(1, CounterAction.Increment))
            store.dispatchSuspend(AppAction.Indexed(5, CounterAction.Increment))

            assertEquals(listOf(CounterState(), CounterState(count = 9, log = listOf("count=8"))), store.state.value.list)
        }
}
