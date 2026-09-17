package io.github.amine2233.redux

/**
 * Optique totale: The property always exists.
 */
public class Lens<T, V>(
    public val get: (T) -> V,
    public val set: (T, V) -> T,
) {
    public operator fun invoke(target: T): V = get(target)

    public fun modify(
        target: T,
        transform: (V) -> V,
    ): T = set(target, transform(get(target)))

    public infix fun <SubV> then(other: Lens<V, SubV>): Lens<T, SubV> =
        Lens(
            get = { target -> other.get(get(target)) },
            set = { target, subVal -> modify(target) { other.set(it, subVal) } },
        )

    public infix fun <SubV> then(optional: Optional<V, SubV>): Optional<T, SubV> =
        Optional(
            getOrNull = { target -> optional.getOrNull(get(target)) },
            set = { target, subVal -> modify(target) { optional.set(it, subVal) } },
        )
}
