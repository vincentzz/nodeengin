package me.vincentzz.graph.scope;

import java.util.Set;

public record Exclude<T>(ScopeSet<T> scopeSet) implements Scope<T> {
    public static <T> Exclude<T> of(ScopeSet<T> scopeSet) {
        return new Exclude<>(scopeSet);
    }

    public static <T> Exclude<T> of(Set<T> elements) {
        return new Exclude<>(new FullSet<>(elements));
    }

    @Override
    public boolean isInScope(T resource) {
        return !scopeSet.isInScope(resource);
    }
}