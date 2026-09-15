package io.github.amine2233.redux

import kotlin.test.Test
import kotlin.test.assertEquals

class CombinedReducerTest {
    private val doubling = Reducer<CounterState, CounterAction> { state, _ -> state.copy(count = state.count * 2) }

    @Test
    fun `applies reducers sequentially feeding each output into the next`() {
        val combined = CombinedReducer(counterReducer, doubling)

        val result = combined.reduce(CounterState(count = 1), CounterAction.Add(2))

        assertEquals(6, result.count)
    }

    @Test
    fun `empty reducer list returns the state unchanged`() {
        val combined = CombinedReducer<CounterState, CounterAction>(emptyList())

        val state = CounterState(count = 4)

        assertEquals(state, combined.reduce(state, CounterAction.Increment))
    }
}
