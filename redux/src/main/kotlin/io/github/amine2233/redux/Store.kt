package io.github.amine2233.redux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Single source of truth. Flow is unidirectional:
 * `dispatch(action) -> middlewares -> reducer -> new state -> [state] emits`.
 *
 * Compose consumes [state] with `collectAsStateWithLifecycle()`.
 */
public class Store<State, A : Action>(
    initialState: State,
    private val reducer: Reducer<State, A>,
    private val middlewares: List<Middleware<State, A>> = emptyList(),
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(initialState)
    public val state: StateFlow<State> = _state

    public fun dispatch(action: A) {
        scope.launch { dispatchSuspend(action) }
    }

    /** Runs the full middleware chain and the reducer before returning. */
    public suspend fun dispatchSuspend(action: A) {
        invokeMiddleware(0, action)
    }

    private suspend fun invokeMiddleware(index: Int, action: A) {
        if (index >= middlewares.size) {
            _state.update { reducer.reduce(it, action) }
            return
        }
        middlewares[index].intercept({ _state.value }, action) { nextAction ->
            invokeMiddleware(index + 1, nextAction)
        }
    }
}
