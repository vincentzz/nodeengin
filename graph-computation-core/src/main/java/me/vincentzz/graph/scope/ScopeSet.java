package me.vincentzz.graph.scope;

public sealed interface ScopeSet<T> permits FullSet, RegExMatch {
    boolean isInScope(T t);
}
