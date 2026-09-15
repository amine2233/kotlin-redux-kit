package io.github.amine2233.redux

data class AppState(
    val counter: CounterState = CounterState(),
    val optionalCounter: CounterState? = null,
    val counters: Map<String, CounterState> = emptyMap(),
    val list: List<CounterState> = emptyList(),
    val title: String = ""
)

sealed interface AppAction : Action {
    data class Counter(val action: CounterAction) : AppAction
    data class Keyed(val key: String, val action: CounterAction) : AppAction
    data class Indexed(val index: Int, val action: CounterAction) : AppAction
    data class SetTitle(val title: String) : AppAction
}

val counterLens = Lens<AppState, CounterState>({ it.counter }) { whole, part -> whole.copy(counter = part) }
val countersLens = Lens<AppState, Map<String, CounterState>>({ it.counters }) { whole, part -> whole.copy(counters = part) }
val listLens = Lens<AppState, List<CounterState>>({ it.list }) { whole, part -> whole.copy(list = part) }

val counterPrism = Prism<AppAction, CounterAction>(AppAction::Counter) { (it as? AppAction.Counter)?.action }
val keyedPrism = Prism<AppAction, Pair<String, CounterAction>>({ (key, action) -> AppAction.Keyed(key, action) }) {
    (it as? AppAction.Keyed)?.let { keyed -> keyed.key to keyed.action }
}
val indexedPrism = Prism<AppAction, Pair<Int, CounterAction>>({ (index, action) -> AppAction.Indexed(index, action) }) {
    (it as? AppAction.Indexed)?.let { indexed -> indexed.index to indexed.action }
}

val appReducer = Reducer<AppState, AppAction> { state, action ->
    if (action is AppAction.SetTitle) state.copy(title = action.title) else state
}
