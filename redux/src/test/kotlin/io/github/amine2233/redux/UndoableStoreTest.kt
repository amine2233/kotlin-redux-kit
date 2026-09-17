@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.amine2233.redux

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UndoableStoreTest {
    private val reducer = counterReducer.undoable()

    private fun perform(action: CounterAction) = UndoableAction.Perform(action)

    @Test
    fun `perform records the previous state and clears the future`() {
        val state = reducer.reduce(Undoable(CounterState(), future = listOf(CounterState(count = 9))), perform(CounterAction.Increment))

        assertEquals(Undoable(CounterState(count = 1), past = listOf(CounterState())), state)
        assertTrue(state.canUndo)
        assertFalse(state.canRedo)
    }

    @Test
    fun `perform that leaves the state unchanged records nothing`() {
        val initial = Undoable(CounterState())

        assertEquals(initial, reducer.reduce(initial, perform(CounterAction.Add(0))))
    }

    @Test
    fun `undo then redo walk the history`() {
        val two =
            reducer.reduce(
                reducer.reduce(Undoable(CounterState()), perform(CounterAction.Increment)),
                perform(CounterAction.Increment),
            )

        val undone = reducer.reduce(two, UndoableAction.Undo)
        assertEquals(1, undone.present.count)
        assertEquals(listOf(CounterState()), undone.past)
        assertEquals(listOf(CounterState(count = 2)), undone.future)

        val redone = reducer.reduce(undone, UndoableAction.Redo)
        assertEquals(two, redone)
    }

    @Test
    fun `undo and redo are no-ops without history`() {
        val initial = Undoable(CounterState())

        assertEquals(initial, reducer.reduce(initial, UndoableAction.Undo))
        assertEquals(initial, reducer.reduce(initial, UndoableAction.Redo))
    }

    @Test
    fun `history limit drops the oldest entries`() {
        val limited = counterReducer.undoable(historyLimit = 2)

        val state = (1..5).fold(Undoable(CounterState())) { acc, _ -> limited.reduce(acc, perform(CounterAction.Increment)) }

        assertEquals(listOf(3, 4), state.past.map { it.count })
        assertEquals(5, state.present.count)
    }

    @Test
    fun `UndoableStore lifts middlewares over the present state and exposes undo redo`() =
        runTest {
            var observed = -1
            val observer =
                Middleware<CounterState, CounterAction, DummyEffect> { innerStore, action, next ->
                    next(action)
                    observed = innerStore.state.value.count
                }
            val store =
                UndoableStore<CounterState, CounterAction, DummyEffect>(
                    CounterState(),
                    counterReducer,
                    listOf(observer),
                    emptyList(),
                    scope = backgroundScope,
                )

            store.dispatch(CounterAction.Add(3))
            kotlinx.coroutines.yield()
            assertEquals(3, observed)
            assertEquals(3, store.state.value.present.count)

            store.undo()
            kotlinx.coroutines.yield()
            yield()
            assertEquals(0, store.state.value.present.count)
            assertTrue(store.state.value.canRedo)

            store.redo()
            kotlinx.coroutines.yield()
            yield()
            assertEquals(3, store.state.value.present.count)

            store.dispatch(CounterAction.Increment)
            kotlinx.coroutines.yield()
            yield()
            assertEquals(4, store.state.value.present.count)
            assertFalse(store.state.value.canRedo)
        }
}
