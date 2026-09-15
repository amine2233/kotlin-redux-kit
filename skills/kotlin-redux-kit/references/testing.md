# Testing with `redux-test`

`io.github.amine2233:redux-test` runs the **real** `Store` with the production reducer and middlewares and records
every state and every action that reached the reducer — including actions forwarded by middlewares. It asserts
with `error()`, so use it with kotlin-test, JUnit 5 or anything else. It depends on `kotlinx-coroutines-test`; wrap
tests in `runTest` — `delay()` in middlewares is skipped through virtual time.

## `scenario {}` — given / when / then

```kotlin
import io.github.amine2233.redux.test.scenario
import kotlinx.coroutines.test.runTest

@Test
fun `successful search`() = runTest {
    scenario(
        initialState = SearchState(query = "kotlin"),
        reducer = searchReducer,
        middlewares = listOf(SearchMiddleware(FakeApi(repos = listOf(repo)))),
    ) {
        whenDispatch(SearchAction.SearchRequested)

        expectState { assertEquals(listOf(repo), it.repos) }
        expectStates { assertEquals(listOf(false, true, false), it.map(SearchState::isLoading)) }
        expectActions { assertEquals(listOf(SearchAction.SearchStarted, SearchAction.SearchSucceeded(listOf(repo))), it) }
    }
}
```

| Method | Meaning |
|---|---|
| `whenDispatch(action)` / `whenDispatch(a, b, c)` | dispatch through the full chain and await it |
| `whenDispatch(prism, child, …)` | dispatch child actions embedded through a `Prism` |
| `expectState { state -> }` / `expectSuccess { }` | assert on the current state |
| `expectState(lens) { part -> }` | assert on a slice |
| `expectStates { list -> }` / `expectStates(lens) { }` | full history, initial state first |
| `expectActions { list -> }` | every action that reached the reducer, in order |
| `expectActions(prism) { children -> }` | only the child actions the prism extracts |
| `expectEventually(timeoutMillis, pollMillis) { state -> Boolean }` | poll until true; works with `runTest` virtual time |
| `store` | the underlying `TestStore` for anything else |

`expectStates` starts with the initial state; a flow that emits `Started` then `Succeeded` yields three entries.

### Async middleware with `launch`

`whenDispatch` awaits the chain, so a middleware that `delay`s blocks until done. To observe intermediate states,
launch the dispatch and poll:

```kotlin
scenario(SearchState(), searchReducer, listOf(SearchMiddleware(slowApi))) {
    launch { whenDispatch(SearchAction.SearchRequested) }

    expectEventually { it.isLoading }
    expectEventually { it.repos.isNotEmpty() }
}
```

`expectEventually` fails with `IllegalStateException("Expected state condition was not met within N ms")`.

## Reducer alone

A reducer is a plain function — call it:

```kotlin
@Test
fun `search failed keeps repos and stores the error`() {
    val state = searchReducer.reduce(SearchState(repos = listOf(repo)), SearchAction.SearchFailed("boom"))
    assertEquals(listOf(repo), state.repos)
    assertEquals("boom", state.error)
}
```

Or `scenario(initial, reducer) { whenDispatch(...); expectState { } }` when several transitions are chained.

## Middleware alone — `forwardedActions`

Runs the middleware against a fixed state and returns what it forwarded to `next`. Empty list = swallowed.

```kotlin
import io.github.amine2233.redux.test.forwardedActions

@Test
fun `failure emits SearchFailed`() = runTest {
    val middleware = SearchMiddleware(FakeApi(error = IOException("offline")))

    val forwarded = middleware.forwardedActions(SearchState(query = "x"), SearchAction.SearchRequested)

    assertEquals(listOf(SearchAction.SearchStarted, SearchAction.SearchFailed("offline")), forwarded)
}

@Test
fun `other actions pass through`() = runTest {
    assertEquals(
        listOf(SearchAction.QueryChanged("a")),
        SearchMiddleware(FakeApi()).forwardedActions(SearchState(), SearchAction.QueryChanged("a")),
    )
}
```

Because `getState` is fixed to the given state, a middleware that reads state *after* `next()` sees the same value;
use `scenario` when that post-reduce read matters.

## Lifted / keyed / offset slices

The overloads taking a `Lens` or `Prism` keep the test written in feature terms while running the app store:

```kotlin
scenario(AppState(), appReducer, appMiddlewares(fakeDeps)) {
    whenDispatch(searchPrism, SearchAction.QueryChanged("kotlin"), SearchAction.SearchRequested)
    whenDispatch(cartPrism, "cart-1" to CartAction.Increment)

    expectState(searchLens) { assertEquals("kotlin", it.query) }
    expectStates(searchLens) { assertEquals(listOf(false, false, true, false), it.map(SearchState::isLoading)) }
    expectActions(searchPrism) { assertEquals(SearchAction.SearchStarted, it[1]) }
}
```

Combinators can also be checked in isolation with `forwardedActions`:

```kotlin
val lifted = SearchMiddleware(fakeApi).lifted(searchLens, searchPrism)
assertEquals(listOf(AppAction.LoggedOut), lifted.forwardedActions(AppState(), AppAction.LoggedOut))   // pass-through
```

## `ActionCaptureMiddleware`

A pass-through recorder to place at a specific position in the chain — e.g. to assert what a preceding middleware
transformed an action into:

```kotlin
val capture = ActionCaptureMiddleware<SearchState, SearchAction>()
scenario(SearchState(), searchReducer, listOf(SearchMiddleware(fakeApi), capture)) {
    whenDispatch(SearchAction.SearchRequested)
    assertEquals(listOf(SearchAction.SearchStarted, SearchAction.SearchSucceeded(emptyList())), capture.actions)
}
```

## `TestStore` directly

```kotlin
val store = TestStore(SearchState(), searchReducer, middlewares)   // scope defaults to Dispatchers.Unconfined
store.dispatch(action)          // suspend, awaits the chain
store.getState(); store.state   // StateFlow
store.states(); store.actions()
```

Pass `scope = this` inside `runTest` when a middleware launches its own coroutines and you want them under the test
scheduler.

## Fakes over mocks

Middleware dependencies are constructor parameters; write a tiny fake (`class FakeApi(val repos: List<Repo> = emptyList(), val error: Throwable? = null) : GithubApi`) rather than a mocking framework. The fake is reused across
reducer, middleware and flow tests.

## Checklist per feature

- [ ] every reducer branch: one assertion on the produced state
- [ ] every middleware: success path, failure path, pass-through of unrelated actions (`forwardedActions`)
- [ ] one `scenario` for the happy end-to-end flow with `expectActions` (the sequence is the contract)
- [ ] lifted features: one test through the app store with the lens/prism overloads
