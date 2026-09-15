package io.github.amine2233.redux.test

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestStoreTest {

    @Test
    fun `records every state and action`() = runTest {
        val store = TestStore(CounterState(), counterReducer)

        store.dispatch(CounterAction.Increment)
        store.dispatch(CounterAction.Increment)

        assertEquals(2, store.getState().count)
        assertEquals(listOf(0, 1, 2), store.states().map { it.count })
        assertEquals(listOf(CounterAction.Increment, CounterAction.Increment), store.actions())
    }

    @Test
    fun `runs middlewares and records forwarded actions`() = runTest {
        val store = TestStore(CounterState(), counterReducer, listOf(loadMiddleware))

        store.dispatch(CounterAction.LoadRequested)

        assertEquals(CounterState(count = 42, loading = false), store.getState())
        assertEquals(listOf(CounterAction.LoadStarted, CounterAction.LoadSucceeded(42)), store.actions())
        assertEquals(listOf(false, true, false), store.states().map { it.loading })
    }

    @Test
    fun `state flow mirrors the store`() = runTest {
        val store = TestStore(CounterState(), counterReducer)

        store.dispatch(CounterAction.Increment)

        assertEquals(store.getState(), store.state.value)
    }
}
