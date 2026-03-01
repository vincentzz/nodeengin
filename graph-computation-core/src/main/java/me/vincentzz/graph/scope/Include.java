package me.vincentzz.graph.scope;

import java.util.Set;

public record Include<T>(ScopeSet<T> scopeSet) implements Scope<T> {
    public static <T> Include<T> of(ScopeSet<T> scopeSet) {
        return new Include<>(scopeSet);
    }

    public static <T> Include<T> of(Set<T> elements) {
        return new Include<>(new FullSet<>(elements));
    }

    @Override
    public boolean isInScope(T resource) {
        return scopeSet.isInScope(resource);
    }
}