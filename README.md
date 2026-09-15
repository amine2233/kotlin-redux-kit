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
redux-kit = "1.0.0"

[libraries]
redux = { module = "io.github.amine2233:redux", version.ref = "redux-kit" }
redux-test = { module = "io.github.amine2233:redux-test", version.ref = "redux-kit" }
```

```kotlin
// settings.gradle.kts — artifacts are published to GitHub Packages
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://maven.pkg.github.com/amine2233/kotlin-redux-kit") {
            credentials {
                username = providers.gradleProperty("gpr.user").orElse(System.getenv("GITHUB_ACTOR") ?: "").get()
                password = providers.gradleProperty("gpr.key").orElse(System.getenv("GITHUB_TOKEN") ?: "").get()
            }
        }
    }
}

// build.gradle.kts
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

### Undo / redo

`UndoableStore` is a `Store<Undoable<State>, UndoableAction<A>>`: the state carries `present`,
`past`, `future`, `canUndo`, `canRedo`; your reducer and middlewares stay unchanged.

```kotlin
val store = UndoableStore(CounterState(), counterReducer, listOf(analytics), scope = viewModelScope, historyLimit = 50)

store.dispatch(CounterAction.Increment)   // recorded; a no-op action records nothing
store.undo()
store.redo()

val state by store.state.collectAsStateWithLifecycle()
Button(onClick = store::undo, enabled = state.canUndo) { Text("Undo") }
Text("${state.present.count}")
```

`counterReducer.undoable()`, `presentLens()` and `performPrism()` are public, so the same state
works with `scenario(Undoable(initial), reducer.undoable(), ...)` in tests.

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

Scope a scenario to a feature slice with the `Lens`/`Prism` overloads, or run a middleware in
isolation with `forwardedActions`:

```kotlin
scenario(AppState(), appReducer, listOf(searchMiddleware.lifted(counterLens, counterPrism))) {
    whenDispatch(counterPrism, CounterAction.Increment)

    expectState(counterLens) { assertEquals(1, it.count) }
    expectStates(counterLens) { assertEquals(listOf(0, 1), it.map(CounterState::count)) }
    expectActions(counterPrism) { assertEquals(listOf(CounterAction.Increment), it) }
}

val forwarded = loadMiddleware.forwardedActions(CounterState(), CounterAction.LoadRequested)
assertEquals(listOf(CounterAction.LoadStarted, CounterAction.LoadSucceeded(42)), forwarded)
```

## AI agent skill

This repository ships a `kotlin-redux-kit` skill (`skills/kotlin-redux-kit/`) that teaches coding agents how to
build and test features with this library. It is a valid [Agent Skill](https://skills.sh) and a
[Claude Code](https://claude.com/claude-code) plugin, so it installs into any harness.

### With the `skills` CLI (any agent)

[`skills`](https://github.com/vercel-labs/skills) installs the same `SKILL.md` into the skills directory of every
agent it knows (Claude Code, Codex, Cursor, Gemini CLI, GitHub Copilot, OpenCode, Windsurf, Cline, Zed, Junie, …).

```sh
# see what the repo provides
npx skills add amine2233/kotlin-redux-kit --list

# interactive: pick agents and scope (project or global)
npx skills add amine2233/kotlin-redux-kit

# project-level (committed with the app, shared with the team), specific agents
npx skills add amine2233/kotlin-redux-kit -a claude-code -a codex -a cursor -y

# global (available in every project)
npx skills add amine2233/kotlin-redux-kit -g -a claude-code -a gemini-cli -a github-copilot -y

# every supported agent, no prompts
npx skills add amine2233/kotlin-redux-kit --all

# pin the skill to a library release
npx skills add https://github.com/amine2233/kotlin-redux-kit/tree/v1.0.0 -a claude-code -y

# later
npx skills update
npx skills remove kotlin-redux-kit
```

Where it lands, per agent (project scope → global scope):

| Agent | `--agent` | Project | Global |
|---|---|---|---|
| Claude Code | `claude-code` | `.claude/skills/` | `~/.claude/skills/` |
| Codex | `codex` | `.agents/skills/` | `~/.codex/skills/` |
| Cursor | `cursor` | `.agents/skills/` | `~/.cursor/skills/` |
| Gemini CLI | `gemini-cli` | `.agents/skills/` | `~/.gemini/skills/` |
| GitHub Copilot | `github-copilot` | `.agents/skills/` | `~/.copilot/skills/` |
| OpenCode | `opencode` | `.agents/skills/` | `~/.config/opencode/skills/` |
| Windsurf | `windsurf` | `.windsurf/skills/` | `~/.codeium/windsurf/skills/` |
| Cline / Zed / Warp / Kimi | `cline`, `zed`, `warp`, `kimi-code-cli` | `.agents/skills/` | `~/.agents/skills/` |
| Junie (JetBrains) | `junie` | `.junie/skills/` | `~/.junie/skills/` |
| Amp / universal | `amp`, `universal` | `.agents/skills/` | `~/.config/agents/skills/` |

Full list: `npx skills add --help` or the [supported agents](https://github.com/vercel-labs/skills#supported-agents)
table. Add `--copy` when the agent's sandbox does not follow symlinks.

Without installing anything, `npx skills use amine2233/kotlin-redux-kit@kotlin-redux-kit` prints a prompt that
loads the skill for a one-off session.

### As a Claude Code plugin

```
/plugin marketplace add amine2233/kotlin-redux-kit
/plugin install kotlin-redux-kit@kotlin-redux-kit
```

The plugin form is versioned (`.claude-plugin/plugin.json`) and updates through `/plugin`; the `skills` CLI form
tracks the repo through `npx skills update`.

### Manual

Copy `skills/kotlin-redux-kit/` into your agent's skills directory (see the table above), or reference the raw
`SKILL.md` from your agent's instructions file (`CLAUDE.md`, `AGENTS.md`, `.cursor/rules`, …).

### Versioning

The skill follows the library: every release rewrites `.claude-plugin/plugin.json`, the `redux-kit` version in this
README and in `skills/kotlin-redux-kit/references/installation.md` (`bumpversion.sh`, run by semantic-release
before the release commit). Skill-only changes use `docs(skill):` commits and ship with the next library release;
use `feat(skill):` to force a minor release.

## Development

```sh
mise trust && mise install        # JDK, node, ktlint
cp .env.local.example .env.local  # GITHUB_ACTOR / GITHUB_TOKEN (write:packages), only for publish/release
mise run lint
mise run test
mise run build
```

`mise run format` applies ktlint fixes.

## CI/CD

CI and releases come from [kotlin-ci-shared](https://github.com/amine2233/kotlin-ci-shared).
All logic lives in `mise.toml`:

- `mise run test` / `mise run lint` — what the `CI` workflow runs on pull requests.
- Merging a `feat:` / `fix:` commit into `main` runs semantic-release: it tags
  `vX.Y.Z`, updates `CHANGELOG.md`, publishes `io.github.amine2233:redux` and
  `io.github.amine2233:redux-test` to GitHub Packages and creates the GitHub release with the jars attached.
- `mise run release --dry-run` previews the next version locally.

## License

MIT
