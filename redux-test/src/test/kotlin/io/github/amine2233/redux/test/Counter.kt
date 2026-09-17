package io.github.amine2233.redux.test

import io.github.amine2233.redux.Action
import io.github.amine2233.redux.Middleware
import io.github.amine2233.redux.Reducer
import kotlinx.coroutines.delay

data class CounterState(
    val count: Int = 0,
    val loading: Boolean = false,
)

sealed interface CounterAction : Action {
    data object Increment : CounterAction

    data object LoadRequested : CounterAction

    data object LoadStarted : CounterAction

    data class LoadSucceeded(
        val value: Int,
    ) : CounterAction
}

val counterReducer =
    Reducer<CounterState, CounterAction> { state, action ->
        when (action) {
            CounterAction.Increment -> state.copy(count = state.count + 1)
            CounterAction.LoadRequested -> state
            CounterAction.LoadStarted -> state.copy(loading = true)
            is CounterAction.LoadSucceeded -> state.copy(count = action.value, loading = false)
        }
    }

/** Turns `LoadRequested` into `LoadStarted` then, after a delay, `LoadSucceeded(42)`. */
val loadMiddleware =
    Middleware<CounterState, CounterAction, DummyEffect> { _, action, next ->
        if (action == CounterAction.LoadRequested) {
            next(CounterAction.LoadStarted)
            delay(100)
            next(CounterAction.LoadSucceeded(42))
        } else {
            next(action)
        }
    }
