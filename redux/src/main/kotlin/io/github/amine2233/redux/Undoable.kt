package io.github.amine2233.redux

import kotlinx.coroutines.CoroutineScope

/** State with its history. [present] is the live state; [past] and [future] are ordered oldest to newest. */
public data class Undoable<State>(
    val present: State,
    val past: List<State> = emptyList(),
    val future: List<State> = emptyList(),
) {
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()
}

public sealed interface UndoableAction<out A : Action> : Action {
    public data class Perform<A : Action>(
        val action: A,
    ) : UndoableAction<A>

    public data object Undo : UndoableAction<Nothing>

    public data object Redo : UndoableAction<Nothing>
}

/**
 * Wraps this reducer so every [UndoableAction.Perform] that changes the state is recorded,
 * and [UndoableAction.Undo] / [UndoableAction.Redo] walk the history.
 * [historyLimit] bounds the number of undoable steps kept.
 */
public fun <State, A : Action> Reducer<State, A>.undoable(historyLimit: Int = Int.MAX_VALUE): Reducer<Undoable<State>, UndoableAction<A>> =
    Reducer { state, action ->
        when (action) {
            is UndoableAction.Perform -> {
                val next = reduce(state.present, action.action)
                if (next == state.present) {
                    state
                } else {
                    Undoable(next, (state.past + state.present).takeLast(historyLimit), emptyList())
                }
            }

            UndoableAction.Undo -> {
                if (!state.canUndo) {
                    state
                } else {
                    Undoable(state.past.last(), state.past.dropLast(1), listOf(state.present) + state.future)
                }
            }

            UndoableAction.Redo -> {
                if (!state.canRedo) {
                    state
                } else {
                    Undoable(state.future.first(), state.past + state.present, state.future.drop(1))
                }
            }
        }
    }

public fun <State> presentLens(): Lens<Undoable<State>, State> = Lens({ it.present }) { whole, part -> whole.copy(present = part) }

public fun <A : Action> performPrism(): Prism<UndoableAction<A>, A> =
    Prism({ UndoableAction.Perform(it) }) { (it as? UndoableAction.Perform)?.action }

public typealias UndoableStore<State, A> = Store<Undoable<State>, UndoableAction<A>>

/** A [Store] whose state carries undo/redo history. Middlewares see the present state and plain actions. */
public fun <State, A : Action> UndoableStore(
    initialState: State,
    reducer: Reducer<State, A>,
    middlewares: List<Middleware<State, A>> = emptyList(),
    scope: CoroutineScope,
    historyLimit: Int = Int.MAX_VALUE,
): UndoableStore<State, A> =
    Store(
        initialState = Undoable(initialState),
        reducer = reducer.undoable(historyLimit),
        middlewares = middlewares.map { it.lifted(presentLens(), performPrism()) },
        scope = scope,
    )

public fun <State, A : Action> UndoableStore<State, A>.dispatch(action: A): Unit = dispatch(UndoableAction.Perform(action))

public suspend fun <State, A : Action> UndoableStore<State, A>.dispatchSuspend(action: A): Unit =
    dispatchSuspend(UndoableAction.Perform(action))

public fun <State, A : Action> UndoableStore<State, A>.undo(): Unit = dispatch(UndoableAction.Undo)

public fun <State, A : Action> UndoableStore<State, A>.redo(): Unit = dispatch(UndoableAction.Redo)
