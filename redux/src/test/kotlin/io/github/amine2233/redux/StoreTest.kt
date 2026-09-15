package io.github.amine2233.redux

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class StoreTest {
    @Test
    fun `dispatchSuspend applies the reducer`() =
        runTest {
            val store = Store(CounterState(), counterReducer, scope = this)

            store.dispatchSuspend(CounterAction.Add(5))

            assertEquals(5, store.state.value.count)
        }

    @Test
    fun `dispatch runs on the store scope and updates state`() =
        runTest {
            val store = Store(CounterState(), counterReducer, scope = this)

            store.dispatch(CounterAction.Increment)
            yield()

            assertEquals(1, store.state.value.count)
        }

    @Test
    fun `middlewares run in order and chain through next`() =
        runTest {
            val order = mutableListOf<String>()
            val first =
                Middleware<CounterState, CounterAction> { _, action, next ->
                    order += "first"
                    next(action)
                }
            val second =
                Middleware<CounterState, CounterAction> { _, action, next ->
                    order += "second"
                    next(action)
                }
            val store = Store(CounterState(), counterReducer, listOf(first, second), this)

            store.dispatchSuspend(CounterAction.Increment)

            assertEquals(listOf("first", "second"), order)
            assertEquals(1, store.state.value.count)
        }

    @Test
    fun `middleware can forward additional actions`() =
        runTest {
            val logging =
                Middleware<CounterState, CounterAction> { _, action, next ->
                    next(CounterAction.Log("before $action"))
                    next(action)
                }
            val store = Store(CounterState(), counterReducer, listOf(logging), this)

            store.dispatchSuspend(CounterAction.Increment)

            assertEquals(1, store.state.value.count)
            assertEquals(listOf("before Increment"), store.state.value.log)
        }

    @Test
    fun `middleware can swallow an action`() =
        runTest {
            val blocking = Middleware<CounterState, CounterAction> { _, _, _ -> }
            val store = Store(CounterState(), counterReducer, listOf(blocking), this)

            store.dispatchSuspend(CounterAction.Increment)

            assertEquals(0, store.state.value.count)
        }

    @Test
    fun `getState after next returns the reduced state`() =
        runTest {
            var seen = -1
            val observer =
                Middleware<CounterState, CounterAction> { getState, action, next ->
                    next(action)
                    seen = getState().count
                }
            val store = Store(CounterState(), counterReducer, listOf(observer), this)

            store.dispatchSuspend(CounterAction.Add(3))

            assertEquals(3, seen)
        }

    @Test
    fun `concurrent dispatches do not lose updates`() =
        runTest {
            val store = Store(CounterState(), counterReducer, scope = this)

            List(100) { async { store.dispatchSuspend(CounterAction.Increment) } }.awaitAll()

            assertEquals(100, store.state.value.count)
        }
}
