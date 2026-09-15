package io.github.amine2233.redux

/**
 * Reads and immutably writes a [Part] of a [Whole]. Kotlin stand-in for a writable key path:
 * `Lens<AppState, CounterState>({ it.counter }) { whole, part -> whole.copy(counter = part) }`.
 */
public class Lens<Whole, Part>(
    public val get: (Whole) -> Part,
    public val set: (Whole, Part) -> Whole,
)
