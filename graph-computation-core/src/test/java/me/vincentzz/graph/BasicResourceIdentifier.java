package me.vincentzz.graph;

import me.vincentzz.graph.model.ResourceIdentifier;

record BasicResourceIdentifier(String name, Class<?> type) implements ResourceIdentifier {}