package io.github.amine2233.redux

/**
 * Runs this middleware on the [Part] of a bigger state for the actions the [prism] extracts;
 * forwarded actions are embedded back. Other actions pass straight through to `next`.
 */
public fun <State, A : Action, LiftedState, LiftedAction : Action> Middleware<State, A>.lifted(
    lens: Lens<LiftedState, State>,
    prism: Prism<LiftedAction, A>,
): Middleware<LiftedState, LiftedAction> =
    Middleware { getState, action, next ->
        val lowered = prism.extract(action) ?: return@Middleware next(action)
        intercept({ lens.get(getState()) }, lowered) { forwarded -> next(prism.embed(forwarded)) }
    }

/** Runs this middleware only while the state is non-null; a null state passes the action through. */
public fun <State, A : Action> Middleware<State, A>.optional(): Middleware<State?, A> =
    Middleware { getState, action, next ->
        if (getState() == null) return@Middleware next(action)
        intercept({ checkNotNull(getState()) { "State became null while ${this::class.simpleName} was running" } }, action, next)
    }

/** Runs this middleware on the map entry addressed by the key carried in the action; unknown keys pass through. */
public fun <State, A : Action, KeyedState, KeyedAction : Action, Key> Middleware<State, A>.keyed(
    lens: Lens<KeyedState, Map<Key, State>>,
    prism: Prism<KeyedAction, Pair<Key, A>>,
): Middleware<KeyedState, KeyedAction> =
    Middleware { getState, action, next ->
        val (key, lowered) = prism.extract(action) ?: return@Middleware next(action)
        if (key !in lens.get(getState())) return@Middleware next(action)
        intercept({ lens.get(getState()).getValue(key) }, lowered) { forwarded -> next(prism.embed(key to forwarded)) }
    }

/** Runs this middleware on the list element addressed by the index carried in the action; out-of-range indices pass through. */
public fun <State, A : Action, IndexedState, IndexedAction : Action> Middleware<State, A>.offset(
    lens: Lens<IndexedState, List<State>>,
    prism: Prism<IndexedAction, Pair<Int, A>>,
): Middleware<IndexedState, IndexedAction> =
    Middleware { getState, action, next ->
        val (index, lowered) = prism.extract(action) ?: return@Middleware next(action)
        if (index !in lens.get(getState()).indices) return@Middleware next(action)
        intercept({ lens.get(getState())[index] }, lowered) { forwarded -> next(prism.embed(index to forwarded)) }
    }
