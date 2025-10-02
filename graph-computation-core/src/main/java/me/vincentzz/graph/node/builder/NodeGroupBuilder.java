package me.vincentzz.graph.node.builder;

import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.node.Flywire;
import me.vincentzz.graph.node.NodeGroup;
import me.vincentzz.graph.scope.Scope;

import java.util.*;
import java.util.stream.Collectors;

public final class NodeGroupBuilder implements NodeBuilder {
    private String name;
    private Map<String, NodeBuilder> nodeMap = new HashMap<>();
    private Set<Flywire> flywires = new HashSet<>();
    private Scope<ConnectionPoint> exports;

    public NodeGroupBuilder(NodeGroup nodeGroup) {
        this.name = nodeGroup.name();
        this.exports = nodeGroup.exports();
        nodeMap.putAll(nodeGroup.nodes().stream().collect(Collectors.toMap(n -> n.name(), n -> NodeBuilder.fromNode(n))));
        flywires.addAll(nodeGroup.flywires());
    }

    public Set<Flywire> flywires() {
        return Set.copyOf(flywires);
    }

    public Set<CalculationNode> nodes() {
        return nodeMap.values().stream().map(b -> b.toNode()).collect(Collectors.toUnmodifiableSet());
    }

    public void addFlywire(Flywire flywire) {
        this.flywires.add(flywire);
    }

    public void addFlywires(Set<Flywire> flywires) {
        flywires.forEach(f -> addFlywire(f));
    }

    public void addNode(CalculationNode node) {
        nodeMap.put(node.name(), NodeBuilder.fromNode(node));
    }

    public void addNodes(Set<CalculationNode> nodes) {
        nodes.forEach(n -> addNode(n));
    }

    public void deleteNodes(Set<String> nodeNames) {
        nodeNames.forEach(n -> deleteNode(n));
    }

    public void deleteNode(String nodeName) {
        nodeMap.remove(nodeName);
    }

    public void clearFlywires() {
        this.flywires = new HashSet<>();
    }

    public void deleteFlywire(Flywire flywire) {
        flywires.remove(flywire);
    }

    public void setExports(Scope<ConnectionPoint> scope) {
        this.exports = scope;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public CalculationNode toNode() {
        return new NodeGroup(name, nodes(), flywires(), exports);
    }
}
