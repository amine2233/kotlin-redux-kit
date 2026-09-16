package io.github.amine2233.redux.sample.complex

import io.github.amine2233.redux.Action
import io.github.amine2233.redux.Middleware
import io.github.amine2233.redux.Reducer
import kotlinx.coroutines.delay

/**
 * A slice that does not exist until loaded: `AppState.profile` is `ProfileState?`.
 * The reducer and middleware are written against the non-null state and wrapped with `optional()`.
 */
data class ProfileState(
    val name: String,
    val email: String,
    val isRefreshing: Boolean = false,
)

sealed interface ProfileAction : Action {
    data object RefreshRequested : ProfileAction

    data object RefreshStarted : ProfileAction

    data class Refreshed(
        val name: String,
        val email: String,
    ) : ProfileAction
}

val profileReducer =
    Reducer<ProfileState, ProfileAction> { state, action ->
        when (action) {
            ProfileAction.RefreshRequested -> state
            ProfileAction.RefreshStarted -> state.copy(isRefreshing = true)
            is ProfileAction.Refreshed -> ProfileState(action.name, action.email)
        }
    }

/** Uses `getState()` — only possible because `optional()` guarantees a non-null slice here. */
val profileMiddleware =
    Middleware<ProfileState, ProfileAction> { getState, action, next ->
        if (action != ProfileAction.RefreshRequested) return@Middleware next(action)
        next(ProfileAction.RefreshStarted)
        delay(600)
        val current = getState()
        next(ProfileAction.Refreshed(current.name, "${current.name.lowercase()}+${System.currentTimeMillis() % 1000}@example.com"))
    }
