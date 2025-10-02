package me.vincentzz.graph.scope;

public sealed interface Scope<T> permits Include, Exclude {
    boolean isInScope(T resource);
}
