package me.vincentzz.graph.scope;

import java.util.Set;

public record Include<T>(Set<T> resources) implements Scope<T> {
    public static <T> Include<T> of(Set<T> resources) {
        return new Include<>(resources);
    }

    @Override
    public boolean isInScope(T resource) {
        return resources.contains(resource);
    }
}