package io.github.amine2233.redux.test

import io.github.amine2233.redux.Action
import io.github.amine2233.redux.Effect
import io.github.amine2233.redux.Middleware
import io.github.amine2233.redux.Store

public class ActionCaptureMiddleware<State, A : Action, E : Effect> : Middleware<State, A, E> {
    private val captured = mutableListOf<A>()

    public val actions: List<A>
        get() = captured.toList()

    override suspend fun intercept(
        store: Store<State, A, E>,
        action: A,
        next: suspend (A) -> Unit,
    ) {
        captured += action
        next(action)
    }
}
