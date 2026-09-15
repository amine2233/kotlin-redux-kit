package io.github.amine2233.redux

import kotlin.test.Test
import kotlin.test.assertEquals

class LensTest {

    @Test
    fun `get reads the part`() {
        assertEquals(CounterState(count = 3), counterLens.get(AppState(counter = CounterState(count = 3))))
    }

    @Test
    fun `set replaces the part and keeps the rest`() {
        val updated = counterLens.set(AppState(title = "kept"), CounterState(count = 9))

        assertEquals(AppState(counter = CounterState(count = 9), title = "kept"), updated)
    }
}
