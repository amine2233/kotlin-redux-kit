package io.github.amine2233.redux.test

import io.github.amine2233.redux.Action
import io.github.amine2233.redux.Middleware

/**
 * Pass-through middleware that records every action it sees. Place it anywhere in the chain
 * to assert on the actions reaching that position, e.g. `LoginRequested -> LoginStarted -> LoginSucceeded`.
 */
public class ActionCaptureMiddleware<State, A : Action> : Middleware<State, A> {
    private val captured = mutableListOf<A>()

    public val actions: List<A>
        get() = captured.toList()

    override suspend fun intercept(getState: () -> State, action: A, next: suspend (A) -> Unit) {
        captured += action
        next(action)
    }
}
