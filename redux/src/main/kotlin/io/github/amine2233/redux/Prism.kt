package io.github.amine2233.redux

/**
 * Embeds a [Target] into a [Source] and extracts it back when the source holds one.
 * Typically maps a child action into a parent sealed action:
 * `Prism<AppAction, CounterAction>(embed = AppAction::Counter) { (it as? AppAction.Counter)?.action }`.
 */
public class Prism<Source, Target>(
    public val embed: (Target) -> Source,
    public val extract: (Source) -> Target?
)
