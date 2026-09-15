package io.github.amine2233.redux.test

import io.github.amine2233.redux.Action
import io.github.amine2233.redux.Middleware

/**
 * Runs the middleware in isolation against a fixed [state] and returns the actions it forwarded
 * to `next`, in order. An empty list means the action was swallowed.
 */
public suspend fun <State, A : Action> Middleware<State, A>.forwardedActions(state: State, action: A): List<A> {
    val forwarded = mutableListOf<A>()
    intercept({ state }, action) { forwarded += it }
    return forwarded.toList()
}
