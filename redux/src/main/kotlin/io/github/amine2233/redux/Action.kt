package io.github.amine2233.redux

/** Marker for every action dispatched to a [Store]. */
public interface Action

/** Marker for every one-shot effect emitted by a [Store]. */
public interface Effect

public object NoEffect : Effect
