package io.github.amine2233.redux.sample.complex

import io.github.amine2233.redux.Action
import io.github.amine2233.redux.Reducer

/**
 * A slice with undo/redo: `AppState.editor` is `Undoable<EditorState>`.
 * `EditorAction.Committed` is the only history-recording change; `DraftChanged` would be too
 * fine-grained (one undo step per keystroke), so the draft lives outside the undoable slice.
 */
data class EditorState(
    val text: String = "",
)

sealed interface EditorAction : Action {
    data class Committed(
        val text: String,
    ) : EditorAction

    data object Cleared : EditorAction
}

val editorReducer =
    Reducer<EditorState, EditorAction> { state, action ->
        when (action) {
            is EditorAction.Committed -> state.copy(text = action.text)
            EditorAction.Cleared -> EditorState()
        }
    }
