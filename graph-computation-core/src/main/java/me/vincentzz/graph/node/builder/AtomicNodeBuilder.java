package me.vincentzz.graph.node.builder;

import me.vincentzz.graph.node.AtomicNode;
import me.vincentzz.graph.node.CalculationNode;

public record AtomicNodeBuilder(AtomicNode node) implements NodeBuilder {
    @Override
    public String name() {
        return node.name();
    }

    @Override
    public CalculationNode toNode() {
        return node;
    }
}
