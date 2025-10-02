package me.vincentzz.graph.scope;

import java.util.Set;

public record Exclude<T>(Set<T> resources) implements Scope<T> {
    public static <T> Exclude<T> of(Set<T> resources) {
        return new Exclude<>(resources);
    }

    @Override
    public boolean isInScope(T resource) {
        return !resources.contains(resource);
    }
}