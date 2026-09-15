package io.github.amine2233.redux.test

import io.github.amine2233.redux.Action
import io.github.amine2233.redux.CombinedReducer
import io.github.amine2233.redux.Lens
import io.github.amine2233.redux.Prism
import io.github.amine2233.redux.Reducer
import io.github.amine2233.redux.keyed
import io.github.amine2233.redux.lifted

data class AppState(
    val counter: CounterState = CounterState(),
    val counters: Map<String, CounterState> = emptyMap(),
    val title: String = "",
)

sealed interface AppAction : Action {
    data class Counter(
        val action: CounterAction,
    ) : AppAction

    data class Keyed(
        val key: String,
        val action: CounterAction,
    ) : AppAction

    data class SetTitle(
        val title: String,
    ) : AppAction
}

val counterLens = Lens<AppState, CounterState>({ it.counter }) { whole, part -> whole.copy(counter = part) }
val countersLens = Lens<AppState, Map<String, CounterState>>({ it.counters }) { whole, part -> whole.copy(counters = part) }

val counterPrism = Prism<AppAction, CounterAction>(AppAction::Counter) { (it as? AppAction.Counter)?.action }
val keyedPrism =
    Prism<AppAction, Pair<String, CounterAction>>({ (key, action) -> AppAction.Keyed(key, action) }) {
        (it as? AppAction.Keyed)?.let { keyed -> keyed.key to keyed.action }
    }

val appReducer =
    CombinedReducer(
        Reducer<AppState, AppAction> { state, action -> if (action is AppAction.SetTitle) state.copy(title = action.title) else state },
        counterReducer.lifted(counterLens, counterPrism),
        counterReducer.keyed(countersLens, keyedPrism),
    )
