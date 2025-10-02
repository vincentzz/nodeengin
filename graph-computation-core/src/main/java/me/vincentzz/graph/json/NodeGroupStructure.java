package me.vincentzz.graph.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable structural representation of a NodeGroup for JSON serialization.
 * This captures the topology and metadata without the executable computation logic.
 */
public record NodeGroupStructure(
    String name,
    String type,
    int nodeCount,
    List<NodeStructure> nodes,
    ScopeStructure outputScope,
    List<ResourceStructure> inputs,
    List<ResourceStructure> outputs,
    List<ResourceStructure> allAvailableOutputs,
    MetadataStructure metadata
) {
    
    public NodeGroupStructure {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(nodes, "nodes cannot be null");
        Objects.requireNonNull(inputs, "inputs cannot be null");
        Objects.requireNonNull(outputs, "outputs cannot be null");
        Objects.requireNonNull(allAvailableOutputs, "allAvailableOutputs cannot be null");
        
        // Ensure immutability
        nodes = List.copyOf(nodes);
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        allAvailableOutputs = List.copyOf(allAvailableOutputs);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for NodeGroupStructure
     */
    public static class Builder {
        private String name;
        private String type;
        private int nodeCount;
        private List<NodeStructure> nodes = new ArrayList<>();
        private ScopeStructure outputScope;
        private List<ResourceStructure> inputs = new ArrayList<>();
        private List<ResourceStructure> outputs = new ArrayList<>();
        private List<ResourceStructure> allAvailableOutputs = new ArrayList<>();
        private MetadataStructure metadata;
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder type(String type) {
            this.type = type;
            return this;
        }
        
        public Builder nodeCount(int nodeCount) {
            this.nodeCount = nodeCount;
            return this;
        }
        
        public Builder addNode(NodeStructure node) {
            this.nodes.add(node);
            return this;
        }
        
        public Builder outputScope(ScopeStructure outputScope) {
            this.outputScope = outputScope;
            return this;
        }
        
        public Builder addInput(ResourceStructure input) {
            this.inputs.add(input);
            return this;
        }
        
        public Builder addOutput(ResourceStructure output) {
            this.outputs.add(output);
            return this;
        }
        
        public Builder addAllAvailableOutput(ResourceStructure output) {
            this.allAvailableOutputs.add(output);
            return this;
        }
        
        public Builder metadata(MetadataStructure metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public NodeGroupStructure build() {
            return new NodeGroupStructure(
                name, type, nodeCount, nodes, outputScope,
                inputs, outputs, allAvailableOutputs, metadata
            );
        }
    }
    
    /**
     * Structural representation of a CalculationNode
     */
    public record NodeStructure(
        String name,
        String type,
        String className,
        Integer nodeCount,
        NodeGroupStructure nestedGroup,
        List<ResourceStructure> inputs,
        List<ResourceStructure> outputs
    ) {
        
        public NodeStructure {
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(type, "type cannot be null");
            Objects.requireNonNull(inputs, "inputs cannot be null");
            Objects.requireNonNull(outputs, "outputs cannot be null");
            
            // Ensure immutability
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String name;
            private String type;
            private String className;
            private Integer nodeCount;
            private NodeGroupStructure nestedGroup;
            private List<ResourceStructure> inputs = new ArrayList<>();
            private List<ResourceStructure> outputs = new ArrayList<>();
            
            public Builder name(String name) {
                this.name = name;
                return this;
            }
            
            public Builder type(String type) {
                this.type = type;
                return this;
            }
            
            public Builder className(String className) {
                this.className = className;
                return this;
            }
            
            public Builder nodeCount(Integer nodeCount) {
                this.nodeCount = nodeCount;
                return this;
            }
            
            public Builder nestedGroup(NodeGroupStructure nestedGroup) {
                this.nestedGroup = nestedGroup;
                return this;
            }
            
            public Builder addInput(ResourceStructure input) {
                this.inputs.add(input);
                return this;
            }
            
            public Builder addOutput(ResourceStructure output) {
                this.outputs.add(output);
                return this;
            }
            
            public NodeStructure build() {
                return new NodeStructure(
                    name, type, className, nodeCount, nestedGroup, inputs, outputs
                );
            }
        }
    }
    
    /**
     * Structural representation of a Scope
     */
    public record ScopeStructure(
        String type,
        List<ConnectionPointStructure> resources
    ) {
        
        public ScopeStructure {
            Objects.requireNonNull(type, "type cannot be null");
            Objects.requireNonNull(resources, "resources cannot be null");
            
            // Ensure immutability
            resources = List.copyOf(resources);
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String type;
            private List<ConnectionPointStructure> resources = new ArrayList<>();
            
            public Builder type(String type) {
                this.type = type;
                return this;
            }
            
            public Builder addResource(ConnectionPointStructure resource) {
                this.resources.add(resource);
                return this;
            }
            
            public ScopeStructure build() {
                return new ScopeStructure(type, resources);
            }
        }
    }
    
    /**
     * Structural representation of a ConnectionPoint
     */
    public record ConnectionPointStructure(
        String nodeName,
        ResourceStructure resourceIdentifier
    ) {
        
        public ConnectionPointStructure {
            Objects.requireNonNull(nodeName, "nodePath cannot be null");
            Objects.requireNonNull(resourceIdentifier, "resourceIdentifier cannot be null");
        }
    }
    
    /**
     * Structural representation of a ResourceIdentifier
     */
    public record ResourceStructure(
        String id,
        String className,
        String resourceName,
        String resourceType
    ) {
        
        public ResourceStructure {
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(className, "className cannot be null");
            Objects.requireNonNull(resourceName, "resourceName cannot be null");
            Objects.requireNonNull(resourceType, "resourceType cannot be null");
        }
    }
    
    /**
     * Metadata about the NodeGroup
     */
    public record MetadataStructure(
        int totalInputs,
        int totalOutputs,
        int totalAvailableOutputs,
        boolean isLeafGroup
    ) {}
    
    /**
     * Get a pretty-printed string representation of the structure
     */
    public String toPrettyString() {
        StringBuilder sb = new StringBuilder();
        appendNodeGroup(sb, this, 0);
        return sb.toString();
    }
    
    private void appendNodeGroup(StringBuilder sb, NodeGroupStructure group, int indent) {
        String indentStr = "  ".repeat(indent);
        sb.append(String.format("%s📁 %s (%s, %d nodes)\n", 
                               indentStr, group.name(), group.type(), group.nodeCount()));
        
        if (group.outputScope() != null) {
            sb.append(String.format("%s   Scope: %s (%d resources)\n", 
                                   indentStr, group.outputScope().type(), 
                                   group.outputScope().resources().size()));
        }
        
        sb.append(String.format("%s   I/O: %d inputs → %d outputs (%d available)\n",
                               indentStr, group.inputs().size(), 
                               group.outputs().size(), group.allAvailableOutputs().size()));
        
        for (NodeStructure node : group.nodes()) {
            appendNode(sb, node, indent + 1);
        }
    }
    
    private void appendNode(StringBuilder sb, NodeStructure node, int indent) {
        String indentStr = "  ".repeat(indent);
        
        if ("NodeGroup".equals(node.type()) && node.nestedGroup() != null) {
            appendNodeGroup(sb, node.nestedGroup(), indent);
        } else {
            sb.append(String.format("%s🔹 %s (%s)\n", 
                                   indentStr, node.name(), 
                                   node.className() != null ? node.className() : node.type()));
            sb.append(String.format("%s   I/O: %d inputs → %d outputs\n",
                                   indentStr, node.inputs().size(), node.outputs().size()));
        }
    }
}
