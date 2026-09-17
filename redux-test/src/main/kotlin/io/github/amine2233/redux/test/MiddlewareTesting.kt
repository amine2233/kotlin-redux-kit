package io.github.amine2233.redux.test

import io.github.amine2233.redux.Action
import io.github.amine2233.redux.DefaultStore
import io.github.amine2233.redux.Effect
import io.github.amine2233.redux.Middleware
import io.github.amine2233.redux.Reducer

/**
 * Runs the middleware in isolation against a fixed [state] and returns the actions it forwarded
 * to `next`, in order. An empty list means the action was swallowed.
 */
public suspend fun <State, A : Action, E : Effect> Middleware<State, A, E>.forwardedActions(
    state: State,
    action: A,
): List<A> {
    val forwarded = mutableListOf<A>()

    // Create a dummy store just for testing the middleware forwarding
    val dummyReducer = Reducer<State, A> { s, _ -> s }
    val store = DefaultStore<State, A, E>(state, dummyReducer)

    intercept(store, action) { forwarded += it }
    return forwarded.toList()
}
