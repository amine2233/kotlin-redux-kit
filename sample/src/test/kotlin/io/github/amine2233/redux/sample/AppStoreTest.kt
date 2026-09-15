package io.github.amine2233.redux.sample

import io.github.amine2233.redux.UndoableAction
import io.github.amine2233.redux.lifted
import io.github.amine2233.redux.optional
import io.github.amine2233.redux.sample.complex.AppAction
import io.github.amine2233.redux.sample.complex.AppState
import io.github.amine2233.redux.sample.complex.EditorAction
import io.github.amine2233.redux.sample.complex.ProfileAction
import io.github.amine2233.redux.sample.complex.ProfileState
import io.github.amine2233.redux.sample.complex.appReducer
import io.github.amine2233.redux.sample.complex.counterLens
import io.github.amine2233.redux.sample.complex.counterPrism
import io.github.amine2233.redux.sample.complex.editorLens
import io.github.amine2233.redux.sample.complex.editorPrism
import io.github.amine2233.redux.sample.complex.editorTextLens
import io.github.amine2233.redux.sample.complex.namedLens
import io.github.amine2233.redux.sample.complex.namedPrism
import io.github.amine2233.redux.sample.complex.profileLens
import io.github.amine2233.redux.sample.complex.profileMiddleware
import io.github.amine2233.redux.sample.complex.profilePrism
import io.github.amine2233.redux.sample.complex.tabPrism
import io.github.amine2233.redux.sample.complex.tabsLens
import io.github.amine2233.redux.sample.complex.todosLens
import io.github.amine2233.redux.sample.complex.todosPrism
import io.github.amine2233.redux.sample.medium.Todo
import io.github.amine2233.redux.sample.medium.TodosAction
import io.github.amine2233.redux.sample.medium.TodosMiddleware
import io.github.amine2233.redux.sample.simple.CounterAction
import io.github.amine2233.redux.sample.simple.CounterState
import io.github.amine2233.redux.test.forwardedActions
import io.github.amine2233.redux.test.scenario
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The app store is exercised without the logging middleware (android.util.Log is not available on the JVM). */
class AppStoreTest {
    private val todo = Todo(7, "Lifted")
    private val middlewares =
        listOf(
            TodosMiddleware(FakeRepository(listOf(todo))).lifted(todosLens, todosPrism),
            profileMiddleware.optional().lifted(profileLens, profilePrism),
        )

    @Test
    fun `lifted counter and todos slices only react to their own actions`() =
        runTest {
            scenario(AppState(), appReducer, middlewares) {
                whenDispatch(counterPrism, CounterAction.Increment, CounterAction.Increment)
                whenDispatch(todosPrism, TodosAction.LoadRequested)

                expectState(counterLens) { assertEquals(2, it.count) }
                expectState(todosLens) { assertEquals(listOf(todo), it.todos) }
                expectActions(todosPrism) { assertEquals(listOf(TodosAction.LoadStarted, TodosAction.LoadSucceeded(listOf(todo))), it) }
                expectActions(counterPrism) { assertEquals(2, it.size) }
            }
        }

    @Test
    fun `offset addresses one tab, out-of-range index is ignored`() =
        runTest {
            scenario(AppState(), appReducer) {
                whenDispatch(tabPrism, 1 to CounterAction.Increment, 9 to CounterAction.Increment)

                expectState(tabsLens) { assertEquals(listOf(0, 1, 0), it.map(CounterState::count)) }
            }
        }

    @Test
    fun `keyed addresses one entry, unknown key is ignored`() =
        runTest {
            scenario(AppState(), appReducer) {
                whenDispatch(namedPrism, "likes" to CounterAction.Increment, "missing" to CounterAction.Increment)

                expectState(namedLens) { assertEquals(mapOf("likes" to CounterState(1), "shares" to CounterState()), it) }
            }
        }

    @Test
    fun `optional profile ignores actions until signed in`() =
        runTest {
            scenario(AppState(), appReducer, middlewares) {
                whenDispatch(profilePrism, ProfileAction.RefreshRequested)
                expectState(profileLens) { assertNull(it) }
                expectActions(profilePrism) { assertEquals(listOf(ProfileAction.RefreshRequested), it) }

                whenDispatch(AppAction.SignedIn("Amine"))
                whenDispatch(profilePrism, ProfileAction.RefreshRequested)
                expectState(profileLens) { assertEquals("Amine", it?.name) }
                expectActions(profilePrism) { assertTrue(ProfileAction.RefreshStarted in it) }
            }
        }

    @Test
    fun `optional middleware passes through on null and runs on a present profile`() =
        runTest {
            val lifted = profileMiddleware.optional().lifted(profileLens, profilePrism)
            val request = AppAction.Profile(ProfileAction.RefreshRequested)

            assertEquals(listOf(request), lifted.forwardedActions(AppState(), request))

            val signedIn = AppState(profile = ProfileState("A", "a@example.com"))
            assertEquals(AppAction.Profile(ProfileAction.RefreshStarted), lifted.forwardedActions(signedIn, request).first())
        }

    @Test
    fun `editor slice records history and undoes`() =
        runTest {
            scenario(AppState(), appReducer) {
                whenDispatch(
                    editorPrism,
                    UndoableAction.Perform(EditorAction.Committed("a")),
                    UndoableAction.Perform(EditorAction.Committed("ab")),
                )
                whenDispatch(editorPrism, UndoableAction.Undo)

                expectState(editorTextLens) { assertEquals("a", it.text) }
                expectState(editorLens) { assertTrue(it.canUndo && it.canRedo) }
            }
        }

    @Test
    fun `reset all restores the initial state`() =
        runTest {
            scenario(AppState(), appReducer) {
                whenDispatch(counterPrism, CounterAction.Increment)
                whenDispatch(AppAction.SignedIn("x"), AppAction.ResetAll)

                expectState { assertEquals(AppState(), it) }
            }
        }
}
