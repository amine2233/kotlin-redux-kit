package io.github.amine2233.redux

import kotlinx.coroutines.CoroutineScope

public fun interface Middleware<S, A : Action, E : Effect> {
    public suspend fun intercept(
        store: Store<S, A, E>,
        action: A,
        next: suspend (A) -> Unit,
    )
}

public interface StoreService<S, A : Action, E : Effect> {
    public suspend fun start(
        store: Store<S, A, E>,
        scope: CoroutineScope,
    )

    public fun stop() {}
}
