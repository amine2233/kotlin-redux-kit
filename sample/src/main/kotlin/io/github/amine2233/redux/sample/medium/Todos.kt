package io.github.amine2233.redux.sample.medium

import io.github.amine2233.redux.Action
import io.github.amine2233.redux.NoEffect
import io.github.amine2233.redux.Store

import io.github.amine2233.redux.Middleware
import io.github.amine2233.redux.Reducer
import kotlinx.coroutines.delay

/**
 * Medium example: a feature with side effects. The reducer stays pure; the middleware talks to a
 * repository and turns one intent (`LoadRequested`) into a sequence of facts
 * (`LoadStarted -> LoadSucceeded | LoadFailed`).
 */
data class Todo(
    val id: Long,
    val title: String,
    val done: Boolean = false,
)

data class TodosState(
    val todos: List<Todo> = emptyList(),
    val draft: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val remaining: Int get() = todos.count { !it.done }
}

sealed interface TodosAction : Action {
    data object LoadRequested : TodosAction

    data object LoadStarted : TodosAction

    data class LoadSucceeded(
        val todos: List<Todo>,
    ) : TodosAction

    data class LoadFailed(
        val message: String,
    ) : TodosAction

    data class DraftChanged(
        val draft: String,
    ) : TodosAction

    data object AddRequested : TodosAction

    data class Added(
        val todo: Todo,
    ) : TodosAction

    data class Toggled(
        val id: Long,
    ) : TodosAction

    data class Removed(
        val id: Long,
    ) : TodosAction

    data object ErrorDismissed : TodosAction
}

val todosReducer =
    Reducer<TodosState, TodosAction> { state, action ->
        when (action) {
            TodosAction.LoadRequested -> {
                state
            }

            TodosAction.LoadStarted -> {
                state.copy(isLoading = true, error = null)
            }

            is TodosAction.LoadSucceeded -> {
                state.copy(isLoading = false, todos = action.todos)
            }

            is TodosAction.LoadFailed -> {
                state.copy(isLoading = false, error = action.message)
            }

            is TodosAction.DraftChanged -> {
                state.copy(draft = action.draft)
            }

            TodosAction.AddRequested -> {
                state
            }

            is TodosAction.Added -> {
                state.copy(todos = state.todos + action.todo, draft = "")
            }

            is TodosAction.Toggled -> {
                state.copy(todos = state.todos.map { if (it.id == action.id) it.copy(done = !it.done) else it })
            }

            is TodosAction.Removed -> {
                state.copy(todos = state.todos.filterNot { it.id == action.id })
            }

            TodosAction.ErrorDismissed -> {
                state.copy(error = null)
            }
        }
    }

/** Dependency of the middleware; an interface so tests inject a fake. */
interface TodoRepository {
    suspend fun load(): List<Todo>

    suspend fun create(title: String): Todo
}

/** Simulates a slow backend that fails on every third load. */
class FakeTodoRepository : TodoRepository {
    private var loads = 0
    private var nextId = 100L

    override suspend fun load(): List<Todo> {
        delay(800)
        if (++loads % 3 == 0) throw IllegalStateException("Network unreachable (simulated)")
        return listOf(
            Todo(1, "Read the kotlin-redux-kit README"),
            Todo(2, "Write a reducer", done = true),
            Todo(3, "Lift it into the app store"),
        )
    }

    override suspend fun create(title: String): Todo {
        delay(300)
        return Todo(nextId++, title)
    }
}


class TodosMiddleware(
    private val repository: TodoRepository,
) : Middleware<TodosState, TodosAction, NoEffect> {
    override suspend fun intercept(
        store: Store<TodosState, TodosAction, NoEffect>,
        action: TodosAction,
        next: suspend (TodosAction) -> Unit,
    ) {
        when (action) {
            TodosAction.LoadRequested -> {
                next(TodosAction.LoadStarted)
                runCatching { repository.load() }
                    .onSuccess { next(TodosAction.LoadSucceeded(it)) }
                    .onFailure { next(TodosAction.LoadFailed(it.message ?: "Unknown error")) }
            }

            TodosAction.AddRequested -> {
                val title = store.state.value.draft.trim()
                if (title.isEmpty()) return
                next(TodosAction.Added(repository.create(title)))
            }

            else -> {
                next(action)
            }
        }
    }
}
