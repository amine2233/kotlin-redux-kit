# Composition: one app store, many features

Runnable reference: `sample/src/main/kotlin/io/github/amine2233/redux/sample/complex/AppStore.kt` in the library repo
(one `AppState` with lifted, offset, keyed, optional and undoable slices) and its tests in `sample/src/test`.

Feature reducers and middlewares are written against their own `FeatureState` / `FeatureAction`, then *lifted* into
the app store. Two small types bridge the gap:

| Type | Bridges | Shape |
|---|---|---|
| `Lens<Whole, Part>` | state | `get: (Whole) -> Part`, `set: (Whole, Part) -> Whole` |
| `Prism<Parent, Child>` | actions | `embed: (Child) -> Parent`, `extract: (Parent) -> Child?` |

`Lens` is the Kotlin stand-in for a writable key path; `set` returns a copy.

## Parent state and action

```kotlin
data class AppState(
    val search: SearchState = SearchState(),
    val profile: ProfileState? = null,                     // loaded later → optional()
    val carts: Map<String, CartState> = emptyMap(),        // by id → keyed()
    val tabs: List<TabState> = emptyList(),                // by position → offset()
)

sealed interface AppAction : Action {
    data class Search(val action: SearchAction) : AppAction
    data class Profile(val action: ProfileAction) : AppAction
    data class Cart(val id: String, val action: CartAction) : AppAction
    data class Tab(val index: Int, val action: TabAction) : AppAction
    data object LoggedOut : AppAction
}
```

Keep the wrappers in the parent module; the child module never imports `AppAction`.

## Lenses and prisms

Declare them once next to the parent types. `extract` returns `null` for non-matching actions.

```kotlin
val searchLens = Lens<AppState, SearchState>({ it.search }) { whole, part -> whole.copy(search = part) }
val searchPrism = Prism<AppAction, SearchAction>(AppAction::Search) { (it as? AppAction.Search)?.action }

val profileLens = Lens<AppState, ProfileState?>({ it.profile }) { whole, part -> whole.copy(profile = part) }
val profilePrism = Prism<AppAction, ProfileAction>(AppAction::Profile) { (it as? AppAction.Profile)?.action }

val cartsLens = Lens<AppState, Map<String, CartState>>({ it.carts }) { whole, part -> whole.copy(carts = part) }
val cartPrism = Prism<AppAction, Pair<String, CartAction>>({ (id, action) -> AppAction.Cart(id, action) }) {
    (it as? AppAction.Cart)?.let { cart -> cart.id to cart.action }
}

val tabsLens = Lens<AppState, List<TabState>>({ it.tabs }) { whole, part -> whole.copy(tabs = part) }
val tabPrism = Prism<AppAction, Pair<Int, TabAction>>({ (index, action) -> AppAction.Tab(index, action) }) {
    (it as? AppAction.Tab)?.let { tab -> tab.index to tab.action }
}
```

`keyed()` prisms target `Pair<Key, Action>`; `offset()` prisms target `Pair<Int, Action>`.

## Combinators

Every combinator exists for both `Reducer` and `Middleware` with the same name and arguments.

| Combinator | Reducer behaviour | Middleware behaviour |
|---|---|---|
| `lifted(lens, prism)` | reduce the slice for matching actions; other actions leave state untouched | run on the slice, forwarded child actions are re-embedded; other actions go straight to `next` |
| `optional()` | `State?` — `null` stays `null` | pass through while the state is `null` |
| `keyed(lens, prism)` | reduce the `Map` entry named by the action; unknown key → untouched | same; unknown key passes through |
| `offset(lens, prism)` | reduce the `List` element at the index; out of range → untouched | same; out of range passes through |
| `CombinedReducer(a, b, …)` | fold: output of `a` feeds `b` | — |
| `identityReducer()` | returns state unchanged | — |

Non-matching actions are never dropped by a lifted middleware; they continue down the chain. That is what lets a
logging middleware placed before `searchMiddleware.lifted(...)` still see every action.

## App reducer and middlewares

```kotlin
val appReducer = CombinedReducer(
    appOwnReducer,                                             // handles LoggedOut etc.
    searchReducer.lifted(searchLens, searchPrism),
    profileReducer.optional().lifted(profileLens, profilePrism),
    cartReducer.keyed(cartsLens, cartPrism),
    tabReducer.offset(tabsLens, tabPrism),
)

fun appMiddlewares(deps: Dependencies) = listOf(
    SearchMiddleware(deps.api).lifted(searchLens, searchPrism),
    ProfileMiddleware(deps.session).optional().lifted(profileLens, profilePrism),
    CartMiddleware(deps.cartRepository).keyed(cartsLens, cartPrism),
)

val appStore = DefaultStore(AppState(), appReducer, appMiddlewares(deps), scope)
```

Order inside `CombinedReducer` matters only when two reducers touch the same field — keep each feature on its own
slice and it never does.

## Dispatching from a feature screen

The screen keeps working against `SearchAction`; wrap at the boundary:

```kotlin
@Composable
fun SearchScreen(store: Store<AppState, AppAction, Effect>) {
    val state by store.state.collectAsStateWithLifecycle()
    val search = state.search
    val dispatch: (SearchAction) -> Unit = { store.dispatch(searchPrism.embed(it)) }
    // …
}
```

For a keyed row: `store.dispatch(cartPrism.embed(cartId to CartAction.Increment))`.

## Seeding keyed / offset slices

`keyed()` and `offset()` ignore unknown keys and out-of-range indices by design. The parent reducer must create the
entry first (e.g. `AppAction.CartOpened(id)` → `state.copy(carts = state.carts + (id to CartState()))`); child
actions for a missing entry are silently no-ops, which is the safe behaviour for late/duplicate UI events.

## Reaching across features

A middleware sees only its slice through `store.state.value`. When a feature needs another feature's data:

- prefer passing it in the action from the parent (`AppAction.Search(SearchAction.QueryChanged(query))` built by the
  screen that knows both), or
- write the middleware against `AppState`/`AppAction` (no lifting) — legitimate for cross-cutting concerns such as
  navigation, analytics, or a "logout clears everything" rule.
