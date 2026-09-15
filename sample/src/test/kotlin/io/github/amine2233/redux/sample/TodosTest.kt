package io.github.amine2233.redux.sample

import io.github.amine2233.redux.sample.medium.Todo
import io.github.amine2233.redux.sample.medium.TodoRepository
import io.github.amine2233.redux.sample.medium.TodosAction
import io.github.amine2233.redux.sample.medium.TodosMiddleware
import io.github.amine2233.redux.sample.medium.TodosState
import io.github.amine2233.redux.sample.medium.todosReducer
import io.github.amine2233.redux.test.forwardedActions
import io.github.amine2233.redux.test.scenario
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FakeRepository(
    private val todos: List<Todo> = emptyList(),
    private val failure: Throwable? = null,
) : TodoRepository {
    override suspend fun load(): List<Todo> = failure?.let { throw it } ?: todos

    override suspend fun create(title: String): Todo = Todo(id = 42, title = title)
}

class TodosTest {
    private val todo = Todo(1, "Write tests")

    @Test
    fun `load turns one intent into started then succeeded`() =
        runTest {
            scenario(TodosState(), todosReducer, listOf(TodosMiddleware(FakeRepository(listOf(todo))))) {
                whenDispatch(TodosAction.LoadRequested)

                expectState { assertEquals(listOf(todo), it.todos) }
                expectStates { assertEquals(listOf(false, true, false), it.map(TodosState::isLoading)) }
                expectActions { assertEquals(listOf(TodosAction.LoadStarted, TodosAction.LoadSucceeded(listOf(todo))), it) }
            }
        }

    @Test
    fun `load failure keeps existing todos and exposes the message`() =
        runTest {
            val middleware = TodosMiddleware(FakeRepository(failure = IllegalStateException("offline")))

            assertEquals(
                listOf(TodosAction.LoadStarted, TodosAction.LoadFailed("offline")),
                middleware.forwardedActions(TodosState(), TodosAction.LoadRequested),
            )
        }

    @Test
    fun `add uses the draft and clears it`() =
        runTest {
            scenario(TodosState(draft = "  Ship it "), todosReducer, listOf(TodosMiddleware(FakeRepository()))) {
                whenDispatch(TodosAction.AddRequested)

                expectState { assertEquals(listOf(Todo(42, "Ship it")), it.todos) }
                expectState { assertEquals("", it.draft) }
            }
        }

    @Test
    fun `add with a blank draft is swallowed`() =
        runTest {
            assertEquals(
                emptyList(),
                TodosMiddleware(FakeRepository()).forwardedActions(TodosState(draft = "  "), TodosAction.AddRequested),
            )
        }

    @Test
    fun `toggle and remove are pure reducer transitions`() {
        val toggled = todosReducer.reduce(TodosState(todos = listOf(todo)), TodosAction.Toggled(1))
        assertEquals(true, toggled.todos.single().done)
        assertEquals(0, toggled.remaining)

        val removed = todosReducer.reduce(toggled, TodosAction.Removed(1))
        assertEquals(emptyList(), removed.todos)
    }
}
