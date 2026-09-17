# Undo / redo with `UndoableStore`

`UndoableStore<State, A, Effect>` is a type alias for `Store<Undoable<State>, UndoableAction<A>, Effect>`. Your reducer and
middlewares are unchanged; the history lives in the state.

```kotlin
data class Undoable<State>(
    val present: State,
    val past: List<State> = emptyList(),    // oldest → newest
    val future: List<State> = emptyList(),  // next redo first
) {
    val canUndo: Boolean
    val canRedo: Boolean
}

sealed interface UndoableAction<out A : Action> : Action {
    data class Perform<A : Action>(val action: A) : UndoableAction<A>
    data object Undo : UndoableAction<Nothing>
    data object Redo : UndoableAction<Nothing>
}
```

## Create

```kotlin
val store = UndoableStore(
    initialState = EditorState(),
    reducer = editorReducer,
    middlewares = listOf(AutosaveMiddleware(repo)),   // see the plain EditorState / EditorAction
    scope = viewModelScope,
    historyLimit = 50,                                // default unbounded
)
```

Middlewares are lifted automatically with `presentLens()` / `performPrism()`, so they keep receiving `EditorState`
and `EditorAction`; `Undo` / `Redo` pass through them untouched.

## Use

```kotlin
store.dispatch(EditorAction.TextChanged("hello"))   // extension: wraps in Perform
store.undo()                                         // extension: dispatch(UndoableAction.Undo)
store.redo()

val state by store.state.collectAsStateWithLifecycle()
Row {
    IconButton(onClick = store::undo, enabled = state.canUndo) { Icon(Icons.Default.Undo, "Undo") }
    IconButton(onClick = store::redo, enabled = state.canRedo) { Icon(Icons.Default.Redo, "Redo") }
}
Text(state.present.text)
```

## Semantics

- A `Perform` whose reducer output equals the current present (`==` on the data class) records nothing — no empty
  undo steps from no-op actions.
- A `Perform` after an undo clears `future` (standard branching behaviour).
- `Undo` / `Redo` with nothing to walk are no-ops.
- `historyLimit` drops the oldest `past` entries.

## Choosing the granularity

History records one step per *reducer-changing action*. If a text field dispatches `TextChanged` per keystroke,
each keystroke becomes an undo step. When that is too fine, either dispatch on focus loss / debounce in the screen,
or model an explicit `EditCommitted(text)` action that the reducer applies while `TextChanged` only updates a draft
field that you exclude by keeping it outside the undoable slice.

## Undoable slice inside a bigger app state

`reducer.undoable()`, `presentLens()` and `performPrism()` are public, so an undoable feature can be lifted like any
other:

```kotlin
data class AppState(val editor: Undoable<EditorState> = Undoable(EditorState()))
sealed interface AppAction : Action { data class Editor(val action: UndoableAction<EditorAction>) : AppAction }

val editorLens = Lens<AppState, Undoable<EditorState>>({ it.editor }) { w, p -> w.copy(editor = p) }
val editorPrism = Prism<AppAction, UndoableAction<EditorAction>>(AppAction::Editor) { (it as? AppAction.Editor)?.action }

val appReducer = CombinedReducer(
    editorReducer.undoable(historyLimit = 50).lifted(editorLens, editorPrism),
)
val appMiddlewares = listOf(
    AutosaveMiddleware(repo).lifted(presentLens(), performPrism()).lifted(editorLens, editorPrism),
)
```

Dispatch: `store.dispatch(AppAction.Editor(UndoableAction.Perform(EditorAction.TextChanged("x"))))` and
`store.dispatch(AppAction.Editor(UndoableAction.Undo))`.

## Testing

```kotlin
scenario(Undoable(EditorState()), editorReducer.undoable(), listOf(autosave.lifted(presentLens(), performPrism()))) {
    whenDispatch(performPrism(), EditorAction.TextChanged("a"), EditorAction.TextChanged("ab"))
    whenDispatch(UndoableAction.Undo)

    expectState(presentLens()) { assertEquals("a", it.text) }
    expectState { assertTrue(it.canUndo && it.canRedo) }
}
```
