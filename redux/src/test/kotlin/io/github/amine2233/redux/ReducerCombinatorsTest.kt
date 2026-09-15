package io.github.amine2233.redux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReducerCombinatorsTest {

    @Test
    fun `identityReducer returns the state unchanged`() {
        val state = CounterState(count = 7)

        assertEquals(state, identityReducer<CounterState, CounterAction>().reduce(state, CounterAction.Increment))
    }

    @Test
    fun `lifted reduces the child for matching actions`() {
        val lifted = counterReducer.lifted(counterLens, counterPrism)

        val result = lifted.reduce(AppState(title = "t"), AppAction.Counter(CounterAction.Add(5)))

        assertEquals(AppState(counter = CounterState(count = 5), title = "t"), result)
    }

    @Test
    fun `lifted ignores non-matching actions`() {
        val lifted = counterReducer.lifted(counterLens, counterPrism)
        val state = AppState(counter = CounterState(count = 1))

        assertEquals(state, lifted.reduce(state, AppAction.SetTitle("x")))
    }

    @Test
    fun `lifted composes with CombinedReducer`() {
        val root = CombinedReducer(appReducer, counterReducer.lifted(counterLens, counterPrism))

        val afterTitle = root.reduce(AppState(), AppAction.SetTitle("hello"))
        val afterCount = root.reduce(afterTitle, AppAction.Counter(CounterAction.Increment))

        assertEquals(AppState(counter = CounterState(count = 1), title = "hello"), afterCount)
    }

    @Test
    fun `optional reduces a present state and keeps null as null`() {
        val optional = counterReducer.optional()

        assertEquals(CounterState(count = 11), optional.reduce(CounterState(count = 10), CounterAction.Increment))
        assertNull(optional.reduce(null, CounterAction.Increment))
    }

    @Test
    fun `keyed reduces the addressed entry and ignores unknown keys`() {
        val keyed = counterReducer.keyed(countersLens, keyedPrism)
        val state = AppState(counters = mapOf("one" to CounterState(count = 10)))

        val updated = keyed.reduce(state, AppAction.Keyed("one", CounterAction.Increment))
        val untouched = keyed.reduce(state, AppAction.Keyed("missing", CounterAction.Increment))

        assertEquals(mapOf("one" to CounterState(count = 11)), updated.counters)
        assertEquals(state, untouched)
    }

    @Test
    fun `offset reduces the addressed element and ignores out-of-range indices`() {
        val offset = counterReducer.offset(listLens, indexedPrism)
        val state = AppState(list = listOf(CounterState(count = 1), CounterState(count = 2)))

        val updated = offset.reduce(state, AppAction.Indexed(1, CounterAction.Increment))
        val untouched = offset.reduce(state, AppAction.Indexed(3, CounterAction.Increment))

        assertEquals(listOf(CounterState(count = 1), CounterState(count = 3)), updated.list)
        assertEquals(state, untouched)
    }
}
