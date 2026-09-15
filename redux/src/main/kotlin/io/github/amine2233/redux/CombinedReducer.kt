package io.github.amine2233.redux

/** Applies each reducer in order, feeding the output of one into the next. */
public class CombinedReducer<State, A : Action>(
    private val reducers: List<Reducer<State, A>>,
) : Reducer<State, A> {
    public constructor(vararg reducers: Reducer<State, A>) : this(reducers.toList())

    override fun reduce(
        state: State,
        action: A,
    ): State = reducers.fold(state) { acc, reducer -> reducer.reduce(acc, action) }
}
