# Implementing a feature

Complete example: a GitHub repository search screen. Copy the shape, rename the types.

## Files

```
feature/search/
├── SearchState.kt        data class
├── SearchAction.kt       sealed interface : Action
├── SearchReducer.kt      pure function
├── SearchMiddleware.kt   side effects, dependencies by constructor
├── SearchViewModel.kt    owns the Store
└── SearchScreen.kt       Compose, reads state, dispatches
```

## State

Immutable, with defaults so `SearchState()` is a valid initial state. Model loading/error explicitly.

```kotlin
data class SearchState(
    val query: String = "",
    val repos: List<Repo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
```

## Action

One sealed hierarchy per feature. Name actions after what happened, not what to do
(`SearchSucceeded` rather than `SetRepos`). Every action implements the library marker `Action`.

```kotlin
import io.github.amine2233.redux.Action

sealed interface SearchAction : Action {
    data class QueryChanged(val query: String) : SearchAction
    data object SearchRequested : SearchAction
    data object SearchStarted : SearchAction
    data class SearchSucceeded(val repos: List<Repo>) : SearchAction
    data class SearchFailed(val message: String) : SearchAction
}
```

## Reducer

`Reducer` is a `fun interface`: `(state, action) -> state`. Exhaustive `when` on the sealed action so a new action
is a compile error until handled. No suspension, no I/O, no logging.

```kotlin
import io.github.amine2233.redux.Reducer

val searchReducer = Reducer<SearchState, SearchAction> { state, action ->
    when (action) {
        is SearchAction.QueryChanged -> state.copy(query = action.query)
        SearchAction.SearchRequested -> state
        SearchAction.SearchStarted -> state.copy(isLoading = true, error = null)
        is SearchAction.SearchSucceeded -> state.copy(isLoading = false, repos = action.repos)
        is SearchAction.SearchFailed -> state.copy(isLoading = false, error = action.message)
    }
}
```

## Middleware

Signature: `suspend fun intercept(store: Store<State, A, Effect>, action: A, next: suspend (A) -> Unit)`.

- `next(action)` forwards to the next middleware, then the reducer. Not calling it swallows the action.
- Calling `next` several times emits a sequence — the usual way to turn an intent into a lifecycle.
- `store.state.value` is live. Read it *after* `next(...)` to see the reduced state (useful for persistence).
- Dependencies come through the constructor so tests can stub them; a class is clearer than a lambda once it has any.

```kotlin
import io.github.amine2233.redux.Middleware

class SearchMiddleware(
    private val api: GithubApi,
) : Middleware<SearchState, SearchAction, Effect> {
    override suspend fun intercept(store: Store<SearchState, SearchAction, Effect>, action: SearchAction, next: suspend (SearchAction) -> Unit) {
        if (action != SearchAction.SearchRequested) return next(action)

        next(SearchAction.SearchStarted)
        runCatching { api.search(store.state.value.query) }
            .onSuccess { next(SearchAction.SearchSucceeded(it.items)) }
            .onFailure { next(SearchAction.SearchFailed(it.message ?: "Unknown error")) }
    }
}
```

The intent action (`SearchRequested`) is *not* forwarded: the reducer treats it as a no-op anyway, and swallowing it
keeps the recorded action history in tests meaningful (`SearchStarted -> SearchSucceeded`).

A stateless middleware can stay a lambda:

```kotlin
val analytics = Middleware<SearchState, SearchAction, Effect> { store, action, next ->
    next(action)
    tracker.track(action::class.simpleName.orEmpty(), store.state.value.query)
}
```

## Store

```kotlin
import io.github.amine2233.redux.Store

class SearchViewModel(api: GithubApi) : ViewModel() {
    val store = DefaultStore(
        initialState = SearchState(),
        reducer = searchReducer,
        middlewares = listOf(SearchMiddleware(api), analytics),
        scope = viewModelScope,
    )
}
```

- `store.state: StateFlow<SearchState>` — the single source of truth.
- `store.dispatch(action)` — fire and forget, launched on `scope`. Use from UI callbacks.

Middleware order is the list order; the reducer runs after the last one. Concurrent dispatches are safe: the reducer
is applied with an atomic `update`, so no state change is lost.

## Screen

See [compose.md](compose.md) for the full Compose wiring. Minimal shape:

```kotlin
@Composable
fun SearchScreen(store: Store<SearchState, SearchAction, Effect>) {
    val state by store.state.collectAsStateWithLifecycle()

    Column {
        TextField(
            value = state.query,
            onValueChange = { store.dispatch(SearchAction.QueryChanged(it)) },
        )
        Button(onClick = { store.dispatch(SearchAction.SearchRequested) }, enabled = !state.isLoading) { Text("Search") }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn { items(state.repos, key = Repo::id) { RepoRow(it) } }
    }
}
```

## Tests to write for this feature

- Reducer: one `scenario` per transition (`QueryChanged`, `SearchStarted`, `SearchSucceeded`, `SearchFailed`).
- Middleware: `SearchMiddleware(FakeApi(...)).forwardedActions(state, SearchRequested)` for success and failure.
- Flow: `scenario(SearchState(), searchReducer, listOf(SearchMiddleware(FakeApi(...))))` dispatching
  `SearchRequested` and asserting `expectActions` and `expectState`.

Details in [testing.md](testing.md).
