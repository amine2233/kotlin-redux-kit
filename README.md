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
