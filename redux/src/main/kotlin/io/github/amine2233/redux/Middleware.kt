package io.github.amine2233.redux

/**
 * Intercepts an action before it reaches the [Reducer] to run side effects
 * (navigation, analytics, repository calls) and forward zero or more actions via [next].
 *
 * `getState` is a live accessor: called after `next(action)` it returns the already reduced state.
 */
public fun interface Middleware<State, A : Action> {
    public suspend fun intercept(
        getState: () -> State,
        action: A,
        next: suspend (A) -> Unit,
    )
}
