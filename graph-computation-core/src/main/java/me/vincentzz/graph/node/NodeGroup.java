package me.vincentzz.graph.node;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.scope.Exclude;
import me.vincentzz.graph.scope.Include;
import me.vincentzz.graph.scope.Scope;
import me.vincentzz.graph.json.ConstructionalJsonDeserializer;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * An immutable structural container that groups CalculationNodes.
 * Input and output are the union of underlying CalculationNodes.
 * Output visibility can be controlled via Scope for encapsulation.
 * All evaluation logic is handled by CalculationEngine.
 */
@JsonDeserialize(using = ConstructionalJsonDeserializer.class)
public record NodeGroup(
    String name,
    Set<CalculationNode> nodes,
    Set<Flywire> flywires,
    Scope<ConnectionPoint> exports
) implements CalculationNode {
    /**
     * Compact constructor for validation and immutability.
     */
    public NodeGroup {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(nodes, "nodes cannot be null");
        Objects.requireNonNull(flywires, "flywires cannot be null");
        Objects.requireNonNull(exports, "exports cannot be null");
        // Ensure immutability
        nodes = Set.copyOf(nodes);
    }
    
    /**
     * Create a NodeGroup with default scope (exports all outputs).
     * Maintains backwards compatibility.
     * 
     * @param name The name of this node group
     * @param nodes The collection of nodes to group
     * @return An immutable NodeGroup with all outputs exported
     */
    public static NodeGroup of(String name, Set<CalculationNode> nodes) {
        return new NodeGroup(name, nodes, Set.of(), Exclude.of(Set.of()));
    }
    
    /**
     * Create a NodeGroup with specified output scope.
     * 
     * @param name The name of this node group
     * @param nodes The collection of nodes to group
     * @param outputScope The scope controlling which outputs to export
     * @return An immutable NodeGroup with the specified scope
     */
    public static NodeGroup of(String name, Set<CalculationNode> nodes, Set<Flywire> flywires, Scope<ConnectionPoint> outputScope) {
        return new NodeGroup(name, Set.copyOf(nodes), Set.copyOf(flywires), outputScope);
    }


    @Override
    public Set<ResourceIdentifier> inputs() {
        Set<ResourceIdentifier> ridProvidedWithInScope = nodes.stream().flatMap(n -> n.outputs().stream()).collect(Collectors.toUnmodifiableSet());

        return nodes.stream()
                .flatMap(n -> n.inputs().stream())
                .collect(Collectors.toSet()).stream().filter(
                        rid -> !ridProvidedWithInScope.contains(rid)
                ).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<ResourceIdentifier> outputs() {
        return nodes.stream()
            .flatMap(n ->
                    n.outputs().stream().filter(o -> exports.isInScope(new ConnectionPoint(Path.of(n.name()), o)))
            ).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Get the number of direct child nodes.
     * 
     * @return The count of nodes in this group
     */
    public int size() {
        return nodes.size();
    }

    /**
     * Get the output scope for this group.
     * 
     * @return The scope controlling which outputs are exported
     */
    public Scope<ConnectionPoint> getOutputScope() {
        return exports;
    }
    
    @Override
    public Map<String, Object> getConstructionParameters() {
        Map<String, Object> parameters = new HashMap<>();
        
        // Add name
        parameters.put("name", name);
        
        // Add nodes
        List<Map<String, Object>> nodesList = new ArrayList<>();
        for (CalculationNode node : nodes) {
            Map<String, Object> nodeSpec = new HashMap<>();
            nodeSpec.put("type", node.getClass().getSimpleName());
            nodeSpec.put("parameters", node.getConstructionParameters());
            nodesList.add(nodeSpec);
        }
        parameters.put("nodes", nodesList);
        
        // Add flywires
        List<Map<String, Object>> flywiresList = new ArrayList<>();
        for (Flywire flywire : flywires) {
            Map<String, Object> flywireSpec = new HashMap<>();
            
            Map<String, Object> sourceSpec = new HashMap<>();
            sourceSpec.put("nodePath", flywire.source().nodePath());
            sourceSpec.put("resourceIdentifier", createResourceSpec(flywire.source().rid()));
            flywireSpec.put("source", sourceSpec);
            
            Map<String, Object> targetSpec = new HashMap<>();
            targetSpec.put("nodePath", flywire.target().nodePath());
            targetSpec.put("resourceIdentifier", createResourceSpec(flywire.target().rid()));
            flywireSpec.put("target", targetSpec);
            
            flywiresList.add(flywireSpec);
        }
        parameters.put("flywires", flywiresList);
        
        // Add exports
        Map<String, Object> exportsSpec = createScopeSpec(exports);
        parameters.put("exports", exportsSpec);
        
        return parameters;
    }
    
    private Map<String, Object> createResourceSpec(ResourceIdentifier rid) {
        Map<String, Object> resourceSpec = new HashMap<>();
        resourceSpec.put("type", rid.getClass().getSimpleName());
        
        // Simplified parameter extraction - real implementation would be more sophisticated
        List<Object> paramsList = new ArrayList<>();
        paramsList.add(rid.toString());
        paramsList.add(rid.getClass().getSimpleName());
        resourceSpec.put("parameters", paramsList);
        
        return resourceSpec;
    }
    
    private Map<String, Object> createScopeSpec(Scope<ConnectionPoint> scope) {
        Map<String, Object> scopeSpec = new HashMap<>();
        
        if (scope instanceof Include<ConnectionPoint> include) {
            scopeSpec.put("type", "Include");
            List<Map<String, Object>> valuesList = new ArrayList<>();
            for (ConnectionPoint cp : include.resources()) {
                Map<String, Object> valueSpec = new HashMap<>();
                valueSpec.put("nodePath", cp.nodePath());
                valueSpec.put("resourceIdentifier", createResourceSpec(cp.rid()));
                valuesList.add(valueSpec);
            }
            scopeSpec.put("values", valuesList);
        } else if (scope instanceof Exclude<ConnectionPoint> exclude) {
            scopeSpec.put("type", "Exclude");
            List<Map<String, Object>> valuesList = new ArrayList<>();
            for (ConnectionPoint cp : exclude.resources()) {
                Map<String, Object> valueSpec = new HashMap<>();
                valueSpec.put("nodePath", cp.nodePath());
                valueSpec.put("resourceIdentifier", createResourceSpec(cp.rid()));
                valuesList.add(valueSpec);
            }
            scopeSpec.put("values", valuesList);
        }
        
        return scopeSpec;
    }
}
