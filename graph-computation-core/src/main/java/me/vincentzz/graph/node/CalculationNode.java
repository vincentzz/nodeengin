package me.vincentzz.graph.node;

import me.vincentzz.graph.model.ResourceIdentifier;

import java.util.Map;
import java.util.Set;

public sealed interface CalculationNode permits AtomicNode, NodeGroup {
    String name();

    Set<ResourceIdentifier> inputs();

    Set<ResourceIdentifier> outputs();
    
    /**
     * Get construction parameters for JSON serialization.
     * Returns a Map containing the parameters needed to reconstruct this node.
     * 
     * @return Map of construction parameters
     */
    Map<String, Object> getConstructionParameters();
}
