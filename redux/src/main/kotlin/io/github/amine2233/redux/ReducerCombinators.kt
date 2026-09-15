package io.github.amine2233.redux

/** Reducer that returns the state unchanged. */
public fun <State, A : Action> identityReducer(): Reducer<State, A> = Reducer { state, _ -> state }

/** Runs this reducer on the [Part] of a bigger state, for the actions the [prism] extracts. */
public fun <State, A : Action, LiftedState, LiftedAction : Action> Reducer<State, A>.lifted(
    lens: Lens<LiftedState, State>,
    prism: Prism<LiftedAction, A>
): Reducer<LiftedState, LiftedAction> = Reducer { state, action ->
    val lowered = prism.extract(action) ?: return@Reducer state
    lens.set(state, reduce(lens.get(state), lowered))
}

/** Runs this reducer only when the state is non-null. */
public fun <State, A : Action> Reducer<State, A>.optional(): Reducer<State?, A> =
    Reducer { state, action -> state?.let { reduce(it, action) } }

/** Runs this reducer on the map entry addressed by the key carried in the action; unknown keys are ignored. */
public fun <State, A : Action, KeyedState, KeyedAction : Action, Key> Reducer<State, A>.keyed(
    lens: Lens<KeyedState, Map<Key, State>>,
    prism: Prism<KeyedAction, Pair<Key, A>>
): Reducer<KeyedState, KeyedAction> = Reducer { state, action ->
    val (key, lowered) = prism.extract(action) ?: return@Reducer state
    val map = lens.get(state)
    val child = map[key] ?: return@Reducer state
    lens.set(state, map + (key to reduce(child, lowered)))
}

/** Runs this reducer on the list element addressed by the index carried in the action; out-of-range indices are ignored. */
public fun <State, A : Action, IndexedState, IndexedAction : Action> Reducer<State, A>.offset(
    lens: Lens<IndexedState, List<State>>,
    prism: Prism<IndexedAction, Pair<Int, A>>
): Reducer<IndexedState, IndexedAction> = Reducer { state, action ->
    val (index, lowered) = prism.extract(action) ?: return@Reducer state
    val list = lens.get(state)
    if (index !in list.indices) return@Reducer state
    lens.set(state, list.mapIndexed { i, child -> if (i == index) reduce(child, lowered) else child })
}
