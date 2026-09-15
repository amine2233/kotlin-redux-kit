package io.github.amine2233.redux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PrismTest {
    @Test
    fun `extract returns the target when the source matches`() {
        assertEquals(CounterAction.Increment, counterPrism.extract(AppAction.Counter(CounterAction.Increment)))
    }

    @Test
    fun `extract returns null when the source does not match`() {
        assertNull(counterPrism.extract(AppAction.SetTitle("x")))
    }

    @Test
    fun `embed round-trips through extract`() {
        val embedded = counterPrism.embed(CounterAction.Add(2))

        assertEquals(AppAction.Counter(CounterAction.Add(2)), embedded)
        assertEquals(CounterAction.Add(2), counterPrism.extract(embedded))
    }
}
