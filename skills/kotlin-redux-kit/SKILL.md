---
name: kotlin-redux-kit
description: Opinionated Redux for Kotlin and Jetpack Compose using the kotlin-redux-kit library (io.github.amine2233:redux and redux-test). Use this skill whenever a Kotlin/Android project depends on kotlin-redux-kit or the user mentions Store, Reducer, Middleware, unidirectional data flow, Lens/Prism composition, undo/redo, or wants to test state with the scenario DSL — even if they just say "add a feature", "wire this screen to the store", "add state for X" or "write tests for this reducer" in a project that already uses this library.
---

# kotlin-redux-kit

Unidirectional data flow for Kotlin: `dispatch(action) -> middlewares -> reducer -> new state -> StateFlow emits`.
Reducers are pure, middlewares own side effects, `Store.state` is a `StateFlow` consumed by Compose. No Compose
dependency inside the library.

Two artifacts, same version: `io.github.amine2233:redux` (runtime) and `io.github.amine2233:redux-test` (test DSL).

## Workflow

1. Check the project already depends on the library (`libs.versions.toml` or `build.gradle.kts`). If not, follow
   [installation.md](references/installation.md) — the artifacts live on GitHub Packages, not Maven Central.
2. Model the feature: `State` (data class), `Action` (sealed interface extending `Action`), `Reducer`, then
   `Middleware` only for side effects. Full example in [implementation.md](references/implementation.md).
3. Scope the feature into the app store with `Lens` + `Prism` and `lifted()` / `keyed()` / `offset()` / `optional()`
   instead of creating a second store — [composition.md](references/composition.md).
4. Expose `store.state` to Compose via `collectAsStateWithLifecycle()`; dispatch from callbacks —
   [compose.md](references/compose.md).
5. Test with `redux-test`: `scenario {}` for reducer + middleware flows, `forwardedActions` for a middleware alone —
   [testing.md](references/testing.md). Every reducer and middleware you add gets a test.

## Quick reference

| Need | API | Reference |
|---|---|---|
| Add the dependency | GitHub Packages repo + catalog aliases `redux`, `redux-test` | [installation.md](references/installation.md) |
| Core types | `Action`, `Reducer<State, A>`, `Middleware<State, A>`, `Store(initialState, reducer, middlewares, scope)` | [implementation.md](references/implementation.md) |
| Dispatch | `store.dispatch(a)` (launches on the store scope) / `store.dispatchSuspend(a)` (awaits the chain) | [implementation.md](references/implementation.md) |
| Combine reducers | `CombinedReducer(r1, r2, …)` — sequential fold | [composition.md](references/composition.md) |
| Scope to a state slice | `Lens<Whole, Part>(get, set)` + `Prism<Parent, Child>(embed, extract)` + `reducer.lifted(lens, prism)` / `middleware.lifted(lens, prism)` | [composition.md](references/composition.md) |
| Map / List / nullable slices | `keyed(lens, prism)`, `offset(lens, prism)`, `optional()` on reducers and middlewares | [composition.md](references/composition.md) |
| Undo / redo | `UndoableStore(...)`, `store.undo()`, `store.redo()`, `state.value.present / canUndo / canRedo` | [undoable-store.md](references/undoable-store.md) |
| Compose wiring | ViewModel owns the store, `collectAsStateWithLifecycle()`, previews with a fake store | [compose.md](references/compose.md) |
| Test a flow | `scenario(initial, reducer, middlewares) { whenDispatch(...); expectState { } }` | [testing.md](references/testing.md) |
| Test a middleware alone | `middleware.forwardedActions(state, action): List<A>` | [testing.md](references/testing.md) |
| Test a slice | `whenDispatch(prism, child)`, `expectState(lens) { }`, `expectActions(prism) { }` | [testing.md](references/testing.md) |
| Working example | `sample/` in the library repo — `simple/` (store), `medium/` (middleware), `complex/AppStore.kt` (every lens/prism/combinator, tested in `sample/src/test`) | [composition.md](references/composition.md) |
| Architecture rules | one app store, feature reducers lifted, dependencies injected into middleware constructors | [best-practices.md](references/best-practices.md) |

## Rules that keep the library predictable

- A reducer never suspends, never touches a repository, never logs. If it needs a side effect, that is a middleware.
- A middleware decides what reaches the reducer: call `next(action)` to let it through, skip it to swallow, call it
  several times to emit a sequence (`LoadRequested -> LoadStarted -> LoadSucceeded`). `getState()` is live: after
  `next(action)` it returns the already reduced state.
- Actions are a sealed hierarchy per feature; parent actions wrap child actions (`AppAction.Counter(CounterAction)`)
  and a `Prism` bridges the two. Never leak a child action type outside its feature except through the prism.
- State is an immutable `data class`; reducers return `state.copy(...)`. Collections are `List`/`Map`, replaced not
  mutated — `keyed()` and `offset()` depend on that.
- `Store` needs a `CoroutineScope` (`viewModelScope` in production, the `runTest` scope in tests). Do not create a
  `GlobalScope` store.
- Public library API is `explicitApi()`: when writing code inside the library itself, mark declarations `public`.

## Deciding between patterns

- One screen, one feature, no sharing → a `Store<FeatureState, FeatureAction>` owned by the ViewModel.
- Several features share state or navigation → one `Store<AppState, AppAction>` and features lifted into it.
- A list of identical rows → `offset()` (index) or `keyed()` (stable id, preferred when rows can move).
- The slice may not exist yet (detail screen before load) → `optional()`, often combined with `lifted()`.
- The user must be able to revert edits → `UndoableStore` around the feature reducer; UI reads `present`.
