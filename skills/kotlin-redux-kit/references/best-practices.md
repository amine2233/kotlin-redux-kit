# Best practices and project structure

## Module layout

```
app/                      AppState, AppAction, lenses/prisms, appReducer, appMiddlewares, DI, navigation
feature/search/           SearchState, SearchAction, searchReducer, SearchMiddleware, SearchScreen (+ tests)
feature/cart/             same shape
core/redux-support/       optional: shared cross-cutting middlewares (logging, analytics) written against AppState
```

A feature module depends on `redux` and its own domain; it never depends on `app`. `app` depends on every feature and
does the wiring ([composition.md](composition.md)).

## The three rules

1. **One store per lifetime.** A screen-scoped `Store` in a ViewModel for isolated screens; one app store when
   features share state. Do not spawn derived stores — pass the slice and a `dispatch` lambda.
2. **Reducers are total and pure.** Exhaustive `when`, `copy`, no I/O, no clocks, no random. If a reducer needs the
   current time, put it in the action (`Saved(at = clock.now())`), emitted by a middleware.
3. **Middlewares own effects and get dependencies by constructor.** Interfaces for the dependencies
   (`GithubApi`, `Clock`, `SessionStore`), fakes in tests. No service locator lookups inside `intercept`.

## Naming

| Thing | Convention | Example |
|---|---|---|
| State | `<Feature>State`, data class, defaults for the initial state | `SearchState()` |
| Action | `<Feature>Action`, sealed interface, past tense for facts, `Requested` for intents | `SearchRequested`, `SearchSucceeded` |
| Reducer | `val <feature>Reducer = Reducer<S, A> { … }` | `searchReducer` |
| Middleware | `<Feature><Purpose>Middleware` class | `SearchApiMiddleware`, `CartPersistenceMiddleware` |
| Lens / Prism | `<feature>Lens`, `<feature>Prism` next to the parent state/action | `searchLens`, `cartPrism` |
| Wrapper action | parent sealed member named after the feature holding `action` | `AppAction.Search(action)` |

## Action design

- Intent vs fact: `SearchRequested` (from UI) → middleware → `SearchStarted`, `SearchSucceeded` / `SearchFailed`
  (facts). The reducer only needs facts; the middleware may swallow the intent.
- Carry data, not commands: `Succeeded(repos)` not `SetRepos(repos)`; `Failed(message)` with a displayable message
  decided by the middleware.
- Avoid boolean flag actions (`SetLoading(true)`) — they leak effect sequencing into the reducer's caller.
- Keep child actions small and local; parent code only sees them through the prism.

## Middleware patterns

**Intent → lifecycle**

```kotlin
if (action != Feature.Requested) return next(action)
next(Feature.Started)
runCatching { repo.load() }
    .onSuccess { next(Feature.Succeeded(it)) }
    .onFailure { next(Feature.Failed(it.userMessage())) }
```

**Persist after reduce** — read state after `next`:

```kotlin
next(action)
if (action is SettingsAction) settingsStore.save(getState().settings)
```

**Guard / filter** — swallow when preconditions fail:

```kotlin
if (action is CartAction.Checkout && getState().items.isEmpty()) return   // swallowed
next(action)
```

**Cross-cutting** (logging, analytics, crash breadcrumbs) — written against the app types and placed first:

```kotlin
val logging = Middleware<AppState, AppAction> { _, action, next -> Log.d("redux", action.toString()); next(action) }
val appMiddlewares = listOf(logging) + featureMiddlewares
```

**Cancellation** — a middleware runs in the coroutine that dispatched; `viewModelScope` cancels it with the screen.
For "latest request wins" keep a `Job` in the middleware and cancel the previous one before starting a new load.

## State design

- Prefer `sealed interface Content { Loading, Loaded(data), Failed(message) }` over three booleans when the states
  are exclusive.
- Keep derived data out of state; compute it in the screen or in an extension property on the state.
- Collections: `List` for ordered UI rows (with `offset()`), `Map<Id, State>` for entities addressed by id (with
  `keyed()`), immutable and replaced on every change.
- Nullable slices for "not loaded yet" (`profile: ProfileState?`) paired with `optional()`.

## Threading

- `Store` is safe for concurrent `dispatch`; the reducer is applied atomically.
- Middlewares run on the dispatching coroutine. Push blocking work into the dependency (`withContext(Dispatchers.IO)`
  inside the repository) so tests can replace it with a fake that returns immediately.
- Do not use `Dispatchers.Main` inside a middleware; the UI reads `state` on Main already.

## Anti-patterns

| Smell | Fix |
|---|---|
| `store.state.value` read inside a reducer | reducers get `state` as a parameter |
| Middleware mutating a captured `var` to pass data to the reducer | emit an action carrying the data |
| A second `Store` created to observe a slice | pass `state.slice` down; use `map { }.distinctUntilChanged()` |
| `runBlocking` in a middleware | the chain is already suspending |
| `GlobalScope` as store scope | `viewModelScope` or an explicit app scope |
| Catching `CancellationException` in a middleware | let it propagate; use `runCatching` only around the dependency call and rethrow cancellation |

## Versioning

`redux` and `redux-test` are released together with semantic versioning (`vX.Y.Z`). A major bump means a public API
change (signature of `Store`, `Middleware`, combinators). Keep both artifacts on one catalog version.
