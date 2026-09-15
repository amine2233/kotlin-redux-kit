package io.github.amine2233.redux.test

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReduxTestScenarioTest {

    @Test
    fun `given when then on a pure reducer`() = runTest {
        scenario(initialState = CounterState(count = 1), reducer = counterReducer) {
            whenDispatch(CounterAction.Increment, CounterAction.Increment)

            expectSuccess { assertEquals(3, it.count) }
            expectStates { assertEquals(listOf(1, 2, 3), it.map { state -> state.count }) }
            expectActions { assertEquals(2, it.size) }
        }
    }

    @Test
    fun `expectEventually observes an async middleware transition`() = runTest {
        scenario(CounterState(), counterReducer, listOf(loadMiddleware)) {
            launch { whenDispatch(CounterAction.LoadRequested) }

            expectEventually { it.loading }
            expectEventually { it.count == 42 }
            expectActions { assertEquals(listOf(CounterAction.LoadStarted, CounterAction.LoadSucceeded(42)), it) }
        }
    }

    @Test
    fun `expectEventually fails after the timeout`() = runTest {
        scenario(CounterState(), counterReducer) {
            val failure = assertFailsWith<IllegalStateException> {
                expectEventually(timeoutMillis = 50) { it.count == 99 }
            }
            assertTrue(failure.message!!.contains("50 ms"))
        }
    }

    @Test
    fun `ActionCaptureMiddleware records actions at its position in the chain`() = runTest {
        val capture = ActionCaptureMiddleware<CounterState, CounterAction>()

        scenario(CounterState(), counterReducer, listOf(capture, loadMiddleware)) {
            whenDispatch(CounterAction.LoadRequested)

            assertEquals(listOf(CounterAction.LoadRequested), capture.actions)
            expectState { assertEquals(42, it.count) }
        }
    }
}
