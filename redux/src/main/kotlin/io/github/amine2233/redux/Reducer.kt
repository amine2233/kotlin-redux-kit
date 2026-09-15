package io.github.amine2233.redux

/**
 * Pure function `(state, action) -> new state`. Side effects belong in a [Middleware].
 */
public fun interface Reducer<State, A : Action> {
    public fun reduce(
        state: State,
        action: A,
    ): State
}
