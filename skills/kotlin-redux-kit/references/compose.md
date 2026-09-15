# Jetpack Compose integration

The library ships no Compose code: `Store.state` is a `StateFlow`, which is all Compose needs. Add
`androidx.lifecycle:lifecycle-runtime-compose` for `collectAsStateWithLifecycle()`.

## Who owns the store

| Scope of the state | Owner | Scope passed to `Store` |
|---|---|---|
| One screen | its `ViewModel` | `viewModelScope` |
| Whole app (navigation, session) | an application-scoped holder (Hilt `@Singleton`, Koin `single`, or the `Application`) | an application `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` |
| Preview / test | the composable itself | `rememberCoroutineScope()` |

Never build a `Store` inside a composable body without `remember`; it would be recreated on every recomposition.

## ViewModel

```kotlin
class SearchViewModel(api: GithubApi) : ViewModel() {
    val store = Store(SearchState(), searchReducer, listOf(SearchMiddleware(api)), viewModelScope)
}
```

With Hilt, inject the dependencies, not the store; the ViewModel is the store's owner.

## Screen: read once, dispatch from callbacks

```kotlin
@Composable
fun SearchRoute(viewModel: SearchViewModel = hiltViewModel()) {
    val state by viewModel.store.state.collectAsStateWithLifecycle()
    SearchScreen(state = state, dispatch = viewModel.store::dispatch)
}

@Composable
fun SearchScreen(state: SearchState, dispatch: (SearchAction) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { dispatch(SearchAction.QueryChanged(it)) },
            label = { Text("Query") },
        )
        Button(onClick = { dispatch(SearchAction.SearchRequested) }, enabled = !state.isLoading) { Text("Search") }
        when {
            state.isLoading -> CircularProgressIndicator()
            state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error)
            else -> LazyColumn { items(state.repos, key = Repo::id) { RepoRow(it) } }
        }
    }
}
```

Splitting `Route` (store-aware) from `Screen` (state + dispatch) keeps the screen previewable and testable without
a store.

## Slices and recomposition

`collectAsStateWithLifecycle()` recomposes readers when the whole `State` changes. With one app store, derive the
slice so a screen only recomposes for its own data:

```kotlin
val search by remember(store) { store.state.map { it.search }.distinctUntilChanged() }
    .collectAsStateWithLifecycle(initialValue = store.state.value.search)
```

Or pass `state.search` down to a `Screen` composable — Compose skips it when the slice is `equals`-stable.

## Dispatching from effects

```kotlin
LaunchedEffect(Unit) { store.dispatch(SearchAction.ScreenOpened) }   // fire and forget

LaunchedEffect(itemId) {
    store.dispatchSuspend(DetailAction.Load(itemId))                   // await the chain, then act
    if (store.state.value.detail == null) onNotFound()
}
```

`dispatch` is safe from the main thread: the chain runs in the store scope. Middlewares doing I/O switch
dispatchers themselves (`withContext(Dispatchers.IO)` inside the repository, not in the reducer).

## One-shot events (navigation, snackbars)

Keep them in state as consumable values and let the screen acknowledge them with an action:

```kotlin
data class SearchState(/* … */, val message: String? = null)
sealed interface SearchAction : Action { /* … */ data object MessageShown : SearchAction }

state.message?.let { message ->
    LaunchedEffect(message) {
        snackbarHostState.showSnackbar(message)
        dispatch(SearchAction.MessageShown)
    }
}
```

Navigation is the same pattern with a `destination: Destination?` field consumed by a `NavigationMiddleware` or
by the `Route` composable calling `navController.navigate(...)` then dispatching `NavigationConsumed`.

## Previews

```kotlin
@Preview
@Composable
private fun SearchScreenPreview() {
    SearchScreen(state = SearchState(repos = listOf(Repo(1, "kotlin-redux-kit"))), dispatch = {})
}
```

Because `Screen` takes plain state and a lambda, previews need no store. If a preview must exercise middlewares,
build `Store(..., scope = rememberCoroutineScope())` inside `remember { }`.

## Undo / redo in the UI

```kotlin
val state by store.state.collectAsStateWithLifecycle()   // Undoable<EditorState>
TopAppBar(actions = {
    IconButton(onClick = store::undo, enabled = state.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
    IconButton(onClick = store::redo, enabled = state.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
})
EditorScreen(state = state.present, dispatch = store::dispatch)
```
