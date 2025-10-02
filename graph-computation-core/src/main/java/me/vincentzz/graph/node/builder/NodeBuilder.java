package me.vincentzz.graph.node.builder;

import me.vincentzz.graph.node.AtomicNode;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.graph.node.NodeGroup;

public sealed interface NodeBuilder permits AtomicNodeBuilder, NodeGroupBuilder {
    String name();
    CalculationNode toNode();

    static NodeBuilder fromNode(CalculationNode node) {
        return switch (node) {
            case AtomicNode atomicNode -> new AtomicNodeBuilder(atomicNode);
            case NodeGroup nodeGroup -> new NodeGroupBuilder(nodeGroup);
        };
    }
}
