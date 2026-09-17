package io.github.amine2233.redux.sample.complex

import android.util.Log
import io.github.amine2233.redux.NoEffect
import io.github.amine2233.redux.DefaultStore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.amine2233.redux.Action
import io.github.amine2233.redux.CombinedReducer
import io.github.amine2233.redux.Lens
import io.github.amine2233.redux.Middleware
import io.github.amine2233.redux.Prism
import io.github.amine2233.redux.Reducer
import io.github.amine2233.redux.Store
import io.github.amine2233.redux.Undoable
import io.github.amine2233.redux.UndoableAction
import io.github.amine2233.redux.keyed
import io.github.amine2233.redux.lifted
import io.github.amine2233.redux.offset
import io.github.amine2233.redux.optional
import io.github.amine2233.redux.performPrism
import io.github.amine2233.redux.presentLens
import io.github.amine2233.redux.sample.medium.FakeTodoRepository
import io.github.amine2233.redux.sample.medium.TodoRepository
import io.github.amine2233.redux.sample.medium.TodosAction
import io.github.amine2233.redux.sample.medium.TodosMiddleware
import io.github.amine2233.redux.sample.medium.TodosState
import io.github.amine2233.redux.sample.medium.todosReducer
import io.github.amine2233.redux.sample.simple.CounterAction
import io.github.amine2233.redux.sample.simple.CounterState
import io.github.amine2233.redux.sample.simple.counterReducer
import io.github.amine2233.redux.undoable

/**
 * Complex example: ONE store for the whole app. Every feature keeps its own State/Action/Reducer/
 * Middleware (the simple and medium examples are reused as-is) and is scoped into the app store with:
 *
 * | slice                                   | combinator            |
 * |-----------------------------------------|-----------------------|
 * | `counter: CounterState`                 | `lifted(lens, prism)` |
 * | `todos: TodosState` (+ its middleware)  | `lifted(lens, prism)` |
 * | `tabs: List<CounterState>`              | `offset(lens, prism)` |
 * | `named: Map<String, CounterState>`      | `keyed(lens, prism)`  |
 * | `profile: ProfileState?`                | `optional().lifted()` |
 * | `editor: Undoable<EditorState>`         | `undoable().lifted()` |
 *
 * A `Lens` says where the slice lives in `AppState`; a `Prism` says which `AppAction`s belong to it.
 */
data class AppState(
    val counter: CounterState = CounterState(),
    val todos: TodosState = TodosState(),
    val tabs: List<CounterState> = List(3) { CounterState() },
    val named: Map<String, CounterState> = mapOf("likes" to CounterState(), "shares" to CounterState()),
    val profile: ProfileState? = null,
    val editor: Undoable<EditorState> = Undoable(EditorState()),
    val editorDraft: String = "",
)

sealed interface AppAction : Action {
    // ---- wrappers: one per slice, carrying the child action (+ address for keyed/offset) ----
    data class Counter(
        val action: CounterAction,
    ) : AppAction

    data class Todos(
        val action: TodosAction,
    ) : AppAction

    data class Tab(
        val index: Int,
        val action: CounterAction,
    ) : AppAction

    data class Named(
        val key: String,
        val action: CounterAction,
    ) : AppAction

    data class Profile(
        val action: ProfileAction,
    ) : AppAction

    data class Editor(
        val action: UndoableAction<EditorAction>,
    ) : AppAction

    // ---- app-level actions handled by the app's own reducer ----
    data class SignedIn(
        val name: String,
    ) : AppAction

    data object SignedOut : AppAction

    data class EditorDraftChanged(
        val draft: String,
    ) : AppAction

    data object TabAdded : AppAction

    data object ResetAll : AppAction
}

// ---- Lenses: how to read and copy-write each slice of AppState ----
val counterLens = Lens<AppState, CounterState>({ it.counter }) { whole, part -> whole.copy(counter = part) }
val todosLens = Lens<AppState, TodosState>({ it.todos }) { whole, part -> whole.copy(todos = part) }
val tabsLens = Lens<AppState, List<CounterState>>({ it.tabs }) { whole, part -> whole.copy(tabs = part) }
val namedLens = Lens<AppState, Map<String, CounterState>>({ it.named }) { whole, part -> whole.copy(named = part) }
val profileLens = Lens<AppState, ProfileState?>({ it.profile }) { whole, part -> whole.copy(profile = part) }
val editorLens = Lens<AppState, Undoable<EditorState>>({ it.editor }) { whole, part -> whole.copy(editor = part) }

// ---- Prisms: embed a child action into AppAction, extract it back (null when it is another slice's) ----
val counterPrism = Prism<AppAction, CounterAction>(AppAction::Counter) { (it as? AppAction.Counter)?.action }
val todosPrism = Prism<AppAction, TodosAction>(AppAction::Todos) { (it as? AppAction.Todos)?.action }
val tabPrism =
    Prism<AppAction, Pair<Int, CounterAction>>({ (index, action) -> AppAction.Tab(index, action) }) {
        (it as? AppAction.Tab)?.let { tab -> tab.index to tab.action }
    }
val namedPrism =
    Prism<AppAction, Pair<String, CounterAction>>({ (key, action) -> AppAction.Named(key, action) }) {
        (it as? AppAction.Named)?.let { named -> named.key to named.action }
    }
val profilePrism = Prism<AppAction, ProfileAction>(AppAction::Profile) { (it as? AppAction.Profile)?.action }
val editorPrism =
    Prism<AppAction, UndoableAction<EditorAction>>(AppAction::Editor) { (it as? AppAction.Editor)?.action }

/** App-level concerns only; feature slices are untouched here. */
val appOwnReducer =
    Reducer<AppState, AppAction> { state, action ->
        when (action) {
            is AppAction.SignedIn -> state.copy(profile = ProfileState(action.name, "${action.name.lowercase()}@example.com"))
            AppAction.SignedOut -> state.copy(profile = null)
            is AppAction.EditorDraftChanged -> state.copy(editorDraft = action.draft)
            AppAction.TabAdded -> state.copy(tabs = state.tabs + CounterState())
            AppAction.ResetAll -> AppState()
            else -> state
        }
    }

val appReducer =
    CombinedReducer(
        appOwnReducer,
        counterReducer.lifted(counterLens, counterPrism),
        todosReducer.lifted(todosLens, todosPrism),
        counterReducer.offset(tabsLens, tabPrism),
        counterReducer.keyed(namedLens, namedPrism),
        profileReducer.optional().lifted(profileLens, profilePrism),
        editorReducer.undoable(historyLimit = 20).lifted(editorLens, editorPrism),
    )

/** Cross-cutting middleware written against the app types: sees every action, placed first. */
val loggingMiddleware = object : Middleware<AppState, AppAction, NoEffect> {
    override suspend fun intercept(store: Store<AppState, AppAction, NoEffect>, action: AppAction, next: suspend (AppAction) -> Unit) {
        android.util.Log.d("redux", action.toString())
        next(action)
    }
}

fun appMiddlewares(todoRepository: TodoRepository): List<Middleware<AppState, AppAction, NoEffect>> =
    listOf(
        loggingMiddleware,
        TodosMiddleware(todoRepository).lifted(todosLens, todosPrism),
        profileMiddleware.optional().lifted(profileLens, profilePrism),
    )

class AppViewModel(
    todoRepository: TodoRepository = FakeTodoRepository(),
) : ViewModel() {
    val store = DefaultStore<AppState, AppAction, NoEffect>(AppState(), appReducer, appMiddlewares(todoRepository), emptyList(), viewModelScope)

    // Typed dispatch helpers so screens keep speaking their feature's language.
    fun counter(action: CounterAction) = store.dispatch(counterPrism.embed(action))

    fun todos(action: TodosAction) = store.dispatch(todosPrism.embed(action))

    fun tab(
        index: Int,
        action: CounterAction,
    ) = store.dispatch(tabPrism.embed(index to action))

    fun named(
        key: String,
        action: CounterAction,
    ) = store.dispatch(namedPrism.embed(key to action))

    fun profile(action: ProfileAction) = store.dispatch(profilePrism.embed(action))

    fun editor(action: EditorAction) = store.dispatch(editorPrism.embed(performPrism<EditorAction>().embed(action)))

    fun undo() = store.dispatch(editorPrism.embed(UndoableAction.Undo))

    fun redo() = store.dispatch(editorPrism.embed(UndoableAction.Redo))
}

/** Convenience for screens and tests: read the present editor text through the two lenses. */
val editorTextLens: Lens<AppState, EditorState> =
    Lens({ presentLens<EditorState>().get(editorLens.get(it)) }) { whole, part ->
        editorLens.set(whole, presentLens<EditorState>().set(editorLens.get(whole), part))
    }
