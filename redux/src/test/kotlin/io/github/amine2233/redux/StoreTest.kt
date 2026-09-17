package io.github.amine2233.redux

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class StoreTest {
    @Test
    fun `dispatch applies the reducer`() =
        runTest {
            val store = DefaultStore<CounterState, CounterAction, DummyEffect>(CounterState(), counterReducer, scope = this)

            store.dispatch(CounterAction.Add(5))

            assertEquals(5, store.state.value.count)
        }

    @Test
    fun `dispatch runs on the store scope and updates state`() =
        runTest {
            val store = DefaultStore<CounterState, CounterAction, DummyEffect>(CounterState(), counterReducer, scope = this)

            store.dispatch(CounterAction.Increment)
            yield()

            assertEquals(1, store.state.value.count)
        }

    @Test
    fun `middlewares run in order and chain through next`() =
        runTest {
            val order = mutableListOf<String>()
            val first =
                Middleware<CounterState, CounterAction, DummyEffect> { _, action, next ->
                    order += "first"
                    next(action)
                }
            val second =
                Middleware<CounterState, CounterAction, DummyEffect> { _, action, next ->
                    order += "second"
                    next(action)
                }
            val store =
                DefaultStore<CounterState, CounterAction, DummyEffect>(CounterState(), counterReducer, listOf(first, second), scope = this)

            store.dispatch(CounterAction.Increment)

            assertEquals(listOf("first", "second"), order)
            assertEquals(1, store.state.value.count)
        }

    @Test
    fun `middleware can forward additional actions`() =
        runTest {
            val logging =
                Middleware<CounterState, CounterAction, DummyEffect> { _, action, next ->
                    next(CounterAction.Log("before $action"))
                    next(action)
                }
            val store =
                DefaultStore<CounterState, CounterAction, DummyEffect>(CounterState(), counterReducer, listOf(logging), scope = this)

            store.dispatch(CounterAction.Increment)

            assertEquals(1, store.state.value.count)
            assertEquals(listOf("before Increment"), store.state.value.log)
        }

    @Test
    fun `middleware can swallow an action`() =
        runTest {
            val blocking = Middleware<CounterState, CounterAction, DummyEffect> { _, _, _ -> }
            val store =
                DefaultStore<CounterState, CounterAction, DummyEffect>(CounterState(), counterReducer, listOf(blocking), scope = this)

            store.dispatch(CounterAction.Increment)

            assertEquals(0, store.state.value.count)
        }

    @Test
    fun `getState after next returns the reduced state`() =
        runTest {
            var seen = -1
            val observer =
                Middleware<CounterState, CounterAction, DummyEffect> { store, action, next ->
                    next(action)
                    seen = store.state.value.count
                }
            val store =
                DefaultStore<CounterState, CounterAction, DummyEffect>(CounterState(), counterReducer, listOf(observer), scope = this)

            store.dispatch(CounterAction.Add(3))

            assertEquals(3, seen)
        }

    @Test
    fun `concurrent dispatches do not lose updates`() =
        runTest {
            val store = DefaultStore<CounterState, CounterAction, DummyEffect>(CounterState(), counterReducer, scope = this)

            List(100) { async { store.dispatch(CounterAction.Increment) } }.awaitAll()

            assertEquals(100, store.state.value.count)
        }
}
