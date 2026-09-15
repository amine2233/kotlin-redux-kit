# kotlin-redux-kit

Minimal Redux for Kotlin and Jetpack Compose, built on coroutines and `StateFlow`.

| Module | Artifact | Purpose |
|---|---|---|
| `redux` | `io.github.amine2233:redux` | `Store`, `Reducer`, `Middleware`, `CombinedReducer` |
| `redux-test` | `io.github.amine2233:redux-test` | `TestStore`, `scenario {}` DSL, `ActionCaptureMiddleware` |

## Install

```toml
# gradle/libs.versions.toml
[versions]
redux-kit = "0.1.0"

[libraries]
redux = { module = "io.github.amine2233:redux", version.ref = "redux-kit" }
redux-test = { module = "io.github.amine2233:redux-test", version.ref = "redux-kit" }
```

```kotlin
dependencies {
    implementation(libs.redux)
    testImplementation(libs.redux.test)
}
```

## Usage

```kotlin
data class CounterState(val count: Int = 0)

sealed interface CounterAction : Action {
    data object Increment : CounterAction
    data class Add(val value: Int) : CounterAction
}

val counterReducer = Reducer<CounterState, CounterAction> { state, action ->
    when (action) {
        CounterAction.Increment -> state.copy(count = state.count + 1)
        is CounterAction.Add -> state.copy(count = state.count + action.value)
    }
}

val analytics = Middleware<CounterState, CounterAction> { getState, action, next ->
    next(action)
    tracker.log(action, getState())
}

val store = Store(
    initialState = CounterState(),
    reducer = counterReducer,
    middlewares = listOf(analytics),
    scope = viewModelScope,
)
```

A middleware may call `next` zero or more times: skip it to swallow the action, call it several
times to emit a sequence (`LoginRequested -> LoginStarted -> LoginSucceeded`).

### Composition

Inspired by [swift-unidirectional-flow](https://github.com/mecid/swift-unidirectional-flow): scope a
feature reducer/middleware to a slice of a bigger state with a `Lens` (state) and a `Prism` (action).

```kotlin
data class AppState(val counter: CounterState = CounterState(), val tabs: List<CounterState> = emptyList())

sealed interface AppAction : Action {
    data class Counter(val action: CounterAction) : AppAction
    data class Tab(val index: Int, val action: CounterAction) : AppAction
}

val counterLens = Lens<AppState, CounterState>({ it.counter }) { whole, part -> whole.copy(counter = part) }
val counterPrism = Prism<AppAction, CounterAction>(AppAction::Counter) { (it as? AppAction.Counter)?.action }

val tabsLens = Lens<AppState, List<CounterState>>({ it.tabs }) { whole, part -> whole.copy(tabs = part) }
val tabPrism = Prism<AppAction, Pair<Int, CounterAction>>({ (i, a) -> AppAction.Tab(i, a) }) {
    (it as? AppAction.Tab)?.let { tab -> tab.index to tab.action }
}

val appReducer = CombinedReducer(
    counterReducer.lifted(counterLens, counterPrism),
    counterReducer.offset(tabsLens, tabPrism),
)
val appMiddlewares = listOf(analytics.lifted(counterLens, counterPrism))
```

| Combinator | Reducer | Middleware |
|---|---|---|
| `lifted(lens, prism)` | reduces the child slice for matching actions | runs on the child slice, re-embeds forwarded actions |
| `optional()` | `State?` — null stays null | passes through while the state is null |
| `keyed(lens, prism)` | `Map<Key, State>` entry addressed by the action | same; unknown keys pass through |
| `offset(lens, prism)` | `List<State>` element addressed by the action | same; out-of-range indices pass through |

Non-matching actions are left untouched by reducers and passed straight to `next` by middlewares.
`identityReducer()` is available as a no-op reducer.

### Compose

`Store.state` is a `StateFlow`, so no extra module is needed:

```kotlin
@Composable
fun Counter(store: Store<CounterState, CounterAction>) {
    val state by store.state.collectAsStateWithLifecycle()
    Button(onClick = { store.dispatch(CounterAction.Increment) }) { Text("${state.count}") }
}
```

### Testing

```kotlin
@Test
fun `increment twice`() = runTest {
    scenario(initialState = CounterState(), reducer = counterReducer, middlewares = listOf(analytics)) {
        whenDispatch(CounterAction.Increment, CounterAction.Increment)

        expectState { assertEquals(2, it.count) }
        expectStates { assertEquals(listOf(0, 1, 2), it.map(CounterState::count)) }
        expectActions { assertEquals(2, it.size) }
        expectEventually(timeoutMillis = 500) { it.count == 2 }
    }
}
```

`TestStore` drives the real `Store` with your production reducer and middlewares and records
every state and every action that reached the reducer, including those forwarded by middlewares.

## Development

```sh
mise install          # temurin-21 + gradle
mise run test
mise run build
mise run publish-local
```

Local environment variables go in `.env` (git-ignored, loaded by mise).

## License

MIT
