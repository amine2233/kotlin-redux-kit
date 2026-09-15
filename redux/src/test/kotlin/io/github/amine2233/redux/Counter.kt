package io.github.amine2233.redux

data class CounterState(val count: Int = 0, val log: List<String> = emptyList())

sealed interface CounterAction : Action {
    data object Increment : CounterAction
    data class Add(val value: Int) : CounterAction
    data class Log(val message: String) : CounterAction
}

val counterReducer = Reducer<CounterState, CounterAction> { state, action ->
    when (action) {
        CounterAction.Increment -> state.copy(count = state.count + 1)
        is CounterAction.Add -> state.copy(count = state.count + action.value)
        is CounterAction.Log -> state.copy(log = state.log + action.message)
    }
}
