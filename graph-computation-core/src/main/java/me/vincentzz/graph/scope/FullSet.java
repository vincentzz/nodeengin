package me.vincentzz.graph.scope;

import java.util.Set;

public record FullSet<T>(Set<T> elements) implements ScopeSet<T> {

    @Override
    public boolean isInScope(T t) {
        return elements.contains(t);
    }
}
