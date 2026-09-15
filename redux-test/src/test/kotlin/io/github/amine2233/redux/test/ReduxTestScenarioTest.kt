package io.github.amine2233.redux.test

import io.github.amine2233.redux.Undoable
import io.github.amine2233.redux.UndoableAction
import io.github.amine2233.redux.lifted
import io.github.amine2233.redux.performPrism
import io.github.amine2233.redux.presentLens
import io.github.amine2233.redux.undoable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReduxTestScenarioTest {
    @Test
    fun `given when then on a pure reducer`() =
        runTest {
            scenario(initialState = CounterState(count = 1), reducer = counterReducer) {
                whenDispatch(CounterAction.Increment, CounterAction.Increment)

                expectSuccess { assertEquals(3, it.count) }
                expectStates { assertEquals(listOf(1, 2, 3), it.map { state -> state.count }) }
                expectActions { assertEquals(2, it.size) }
            }
        }

    @Test
    fun `expectEventually observes an async middleware transition`() =
        runTest {
            scenario(CounterState(), counterReducer, listOf(loadMiddleware)) {
                launch { whenDispatch(CounterAction.LoadRequested) }

                expectEventually { it.loading }
                expectEventually { it.count == 42 }
                expectActions { assertEquals(listOf(CounterAction.LoadStarted, CounterAction.LoadSucceeded(42)), it) }
            }
        }

    @Test
    fun `expectEventually fails after the timeout`() =
        runTest {
            scenario(CounterState(), counterReducer) {
                val failure =
                    assertFailsWith<IllegalStateException> {
                        expectEventually(timeoutMillis = 50) { it.count == 99 }
                    }
                assertTrue(failure.message!!.contains("50 ms"))
            }
        }

    @Test
    fun `lens and prism variants scope the scenario to a feature slice`() =
        runTest {
            val lifted = loadMiddleware.lifted(counterLens, counterPrism)

            scenario(AppState(title = "kept"), appReducer, listOf(lifted)) {
                whenDispatch(counterPrism, CounterAction.Increment, CounterAction.LoadRequested)
                whenDispatch(AppAction.SetTitle("changed"))

                expectState(counterLens) { assertEquals(CounterState(count = 42), it) }
                expectStates(counterLens) { assertEquals(listOf(0, 1, 1, 42, 42), it.map(CounterState::count)) }
                expectActions(counterPrism) {
                    assertEquals(listOf(CounterAction.Increment, CounterAction.LoadStarted, CounterAction.LoadSucceeded(42)), it)
                }
                expectState { assertEquals("changed", it.title) }
            }
        }

    @Test
    fun `keyed slice through the scenario DSL`() =
        runTest {
            scenario(AppState(counters = mapOf("a" to CounterState(), "b" to CounterState())), appReducer) {
                whenDispatch(keyedPrism, "a" to CounterAction.Increment, "missing" to CounterAction.Increment)

                expectState(countersLens) { assertEquals(mapOf("a" to CounterState(count = 1), "b" to CounterState()), it) }
                expectActions(keyedPrism) { assertEquals(2, it.size) }
            }
        }

    @Test
    fun `undoable store through the scenario DSL`() =
        runTest {
            scenario(Undoable(CounterState()), counterReducer.undoable(), listOf(loadMiddleware.lifted(presentLens(), performPrism()))) {
                whenDispatch(performPrism(), CounterAction.Increment, CounterAction.LoadRequested)
                whenDispatch(UndoableAction.Undo)

                expectState(presentLens()) { assertEquals(CounterState(count = 1, loading = true), it) }
                expectState { assertTrue(it.canUndo && it.canRedo) }
                expectActions(performPrism()) { assertEquals(3, it.size) }
            }
        }

    @Test
    fun `ActionCaptureMiddleware records actions at its position in the chain`() =
        runTest {
            val capture = ActionCaptureMiddleware<CounterState, CounterAction>()

            scenario(CounterState(), counterReducer, listOf(capture, loadMiddleware)) {
                whenDispatch(CounterAction.LoadRequested)

                assertEquals(listOf(CounterAction.LoadRequested), capture.actions)
                expectState { assertEquals(42, it.count) }
            }
        }
}
