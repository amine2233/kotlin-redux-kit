package io.github.amine2233.redux.test

import io.github.amine2233.redux.Action
import io.github.amine2233.redux.Effect
import io.github.amine2233.redux.Store
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

public class StoreTestRig<S, A : Action, E : Effect>(
    public val store: Store<S, A, E>,
    public val testScope: TestScope
) {
    public val recordedStates: MutableList<S> = mutableListOf()
    public val recordedEffects: MutableList<E> = mutableListOf()

    init {
        testScope.launch { store.state.toList(recordedStates) }
        testScope.launch { store.effects.toList(recordedEffects) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    public fun dispatch(action: A) {
        store.dispatch(action)
        testScope.runCurrent() // Forces immediate execution
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    public fun assertLastState(predicate: (S) -> Unit) {
        testScope.runCurrent()
        predicate(recordedStates.last())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    public fun assertEffectEmitted(expected: E) {
        testScope.runCurrent()
        assert(expected == recordedEffects.lastOrNull()) { "Expected $expected but got ${recordedEffects.lastOrNull()}" }
    }
}
