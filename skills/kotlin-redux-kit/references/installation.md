# Installation

Artifacts are published to **GitHub Packages** (not Maven Central). Both modules share one version, released with
semantic versioning from the `main` branch (`vX.Y.Z` tags).

| Artifact | Purpose | Scope |
|---|---|---|
| `io.github.amine2233:redux` | `Store`, `Reducer`, `Middleware`, `CombinedReducer`, `Lens`, `Prism`, combinators, `UndoableStore` | `implementation` |
| `io.github.amine2233:redux-test` | `TestStore`, `scenario {}` DSL, `forwardedActions`, `ActionCaptureMiddleware` (depends on `kotlinx-coroutines-test`) | `testImplementation` |

Requirements: Kotlin 2.x, JVM 21 toolchain, `kotlinx-coroutines-core` (pulled transitively as `api`).

## 1. Repository with credentials

GitHub Packages needs a token with `read:packages`, even for public repositories.

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.github.com/amine2233/kotlin-redux-kit") {
            credentials {
                username = providers.gradleProperty("gpr.user").orElse(System.getenv("GITHUB_ACTOR") ?: "").get()
                password = providers.gradleProperty("gpr.key").orElse(System.getenv("GITHUB_TOKEN") ?: "").get()
            }
        }
    }
}
```

Put `gpr.user` / `gpr.key` in `~/.gradle/gradle.properties` (never in the repo), or export `GITHUB_ACTOR` /
`GITHUB_TOKEN` in CI.

## 2. Version catalog

```toml
# gradle/libs.versions.toml
[versions]
redux-kit = "1.0.0"

[libraries]
redux = { module = "io.github.amine2233:redux", version.ref = "redux-kit" }
redux-test = { module = "io.github.amine2233:redux-test", version.ref = "redux-kit" }
```

## 3. Module dependencies

```kotlin
// feature/build.gradle.kts
dependencies {
    implementation(libs.redux)
    testImplementation(libs.redux.test)
    testImplementation(libs.kotlin.test)          // or JUnit 5 — redux-test does not force a framework
}
```

For Compose consumption add `androidx.lifecycle:lifecycle-runtime-compose` (for `collectAsStateWithLifecycle`); the
library itself has no Compose dependency.

## Verify

```kotlin
import io.github.amine2233.redux.Store
```
compiles, and `./gradlew :feature:dependencies --configuration testRuntimeClasspath | grep redux` lists both artifacts
with the same version.

## Common failures

- `401 Unauthorized` from `maven.pkg.github.com` → token missing or lacks `read:packages`.
- `Could not find io.github.amine2233:redux-test` → the repository block is in a module instead of
  `settings.gradle.kts`, or `repositoriesMode` rejects project repositories.
- Version mismatch between `redux` and `redux-test` → always use the single `redux-kit` version ref.
