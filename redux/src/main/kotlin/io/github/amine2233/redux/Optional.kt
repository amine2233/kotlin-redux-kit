package io.github.amine2233.redux

/**
 * Optique partielle: The property might be absent (null, or a sealed class branch).
 */
public class Optional<T, V>(
    public val getOrNull: (T) -> V?,
    public val set: (T, V) -> T,
) {
    public operator fun invoke(target: T): V? = getOrNull(target)
    
    public fun modify(target: T, transform: (V) -> V): T {
        val current = getOrNull(target) ?: return target
        return set(target, transform(current))
    }

    public infix fun <SubV> then(other: Optional<V, SubV>): Optional<T, SubV> = Optional(
        getOrNull = { target -> getOrNull(target)?.let(other.getOrNull) },
        set = { target, subVal -> modify(target) { other.set(it, subVal) } }
    )
}
