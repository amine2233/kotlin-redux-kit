package io.github.amine2233.redux.sample.simple

import io.github.amine2233.redux.Action
import io.github.amine2233.redux.Reducer

/**
 * Simple example: a pure feature. State + Action + Reducer, no side effects.
 * The same three declarations are reused unchanged by the complex example, lifted into the app store.
 */
data class CounterState(
    val count: Int = 0,
)

sealed interface CounterAction : Action {
    data object Increment : CounterAction

    data object Decrement : CounterAction

    data object Reset : CounterAction
}

val counterReducer =
    Reducer<CounterState, CounterAction> { state, action ->
        when (action) {
            CounterAction.Increment -> state.copy(count = state.count + 1)
            CounterAction.Decrement -> state.copy(count = state.count - 1)
            CounterAction.Reset -> CounterState()
        }
    }
