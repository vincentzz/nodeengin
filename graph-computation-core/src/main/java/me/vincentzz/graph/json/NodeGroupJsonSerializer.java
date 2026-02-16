package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.node.NodeGroup;
import me.vincentzz.graph.scope.Exclude;
import me.vincentzz.graph.scope.Include;
import me.vincentzz.graph.scope.Scope;
import me.vincentzz.lang.PathUtils;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Custom Jackson serializer for NodeGroup that creates a structural JSON representation.
 * Focuses on topology and metadata rather than executable computation logic.
 */
public class NodeGroupJsonSerializer extends JsonSerializer<NodeGroup> {
    
    @Override
    public void serialize(NodeGroup nodeGroup, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        // Basic properties
        gen.writeStringField("name", nodeGroup.name());
        gen.writeStringField("type", "NodeGroup");
        gen.writeNumberField("nodeCount", nodeGroup.size());
        
        // Serialize nodes
        gen.writeArrayFieldStart("nodes");
        for (CalculationNode node : nodeGroup.nodes()) {
            serializeCalculationNode(node, gen);
        }
        gen.writeEndArray();
        
        // Serialize scope
        gen.writeObjectFieldStart("outputScope");
        serializeScope(nodeGroup.getOutputScope(), gen);
        gen.writeEndObject();
        
        // Serialize input/output metadata
        gen.writeArrayFieldStart("inputs");
        for (ResourceIdentifier input : nodeGroup.inputs()) {
            serializeResourceIdentifier(input, gen);
        }
        gen.writeEndArray();
        
        gen.writeArrayFieldStart("outputs");
        for (ResourceIdentifier output : nodeGroup.outputs()) {
            serializeResourceIdentifier(output, gen);
        }
        gen.writeEndArray();
        
        // Calculate all available outputs (before scope filtering)
        Set<ResourceIdentifier> allAvailableOutputs = nodeGroup.nodes().stream()
            .flatMap(n -> n.outputs().stream())
            .collect(Collectors.toUnmodifiableSet());
            
        gen.writeArrayFieldStart("allAvailableOutputs");
        for (ResourceIdentifier output : allAvailableOutputs) {
            serializeResourceIdentifier(output, gen);
        }
        gen.writeEndArray();
        
        // Metadata
        gen.writeObjectFieldStart("metadata");
        gen.writeNumberField("totalInputs", nodeGroup.inputs().size());
        gen.writeNumberField("totalOutputs", nodeGroup.outputs().size());
        gen.writeNumberField("totalAvailableOutputs", allAvailableOutputs.size());
        gen.writeBooleanField("isLeafGroup", nodeGroup.nodes().stream().allMatch(n -> !(n instanceof NodeGroup)));
        gen.writeEndObject();
        
        gen.writeEndObject();
    }
    
    private void serializeCalculationNode(CalculationNode node, JsonGenerator gen) throws IOException {
        gen.writeStartObject();
        
        gen.writeStringField("name", node.name());
        
        if (node instanceof NodeGroup nestedGroup) {
            gen.writeStringField("type", "NodeGroup");
            gen.writeNumberField("nodeCount", nestedGroup.size());
            
            // Recursively serialize nested NodeGroup content without the outer object wrapper
            serializeNodeGroupContent(nestedGroup, gen);
        } else {
            gen.writeStringField("type", "AtomicNode");
            gen.writeStringField("className", node.getClass().getSimpleName());
            
            // Serialize inputs and outputs only for AtomicNodes (NodeGroups handle their own)
            gen.writeArrayFieldStart("inputs");
            for (ResourceIdentifier input : node.inputs()) {
                serializeResourceIdentifier(input, gen);
            }
            gen.writeEndArray();
            
            gen.writeArrayFieldStart("outputs");
            for (ResourceIdentifier output : node.outputs()) {
                serializeResourceIdentifier(output, gen);
            }
            gen.writeEndArray();
        }
        
        gen.writeEndObject();
    }
    
    /**
     * Serialize NodeGroup content without the outer object wrapper.
     * Used for nested NodeGroups within serializeCalculationNode.
     */
    private void serializeNodeGroupContent(NodeGroup nodeGroup, JsonGenerator gen) throws IOException {
        // Serialize nodes
        gen.writeArrayFieldStart("nodes");
        for (CalculationNode node : nodeGroup.nodes()) {
            serializeCalculationNode(node, gen);
        }
        gen.writeEndArray();
        
        // Serialize scope
        gen.writeObjectFieldStart("outputScope");
        serializeScope(nodeGroup.getOutputScope(), gen);
        gen.writeEndObject();
        
        // Serialize input/output metadata
        gen.writeArrayFieldStart("inputs");
        for (ResourceIdentifier input : nodeGroup.inputs()) {
            serializeResourceIdentifier(input, gen);
        }
        gen.writeEndArray();
        
        gen.writeArrayFieldStart("outputs");
        for (ResourceIdentifier output : nodeGroup.outputs()) {
            serializeResourceIdentifier(output, gen);
        }
        gen.writeEndArray();
        
        // Calculate all available outputs (before scope filtering)
        Set<ResourceIdentifier> allAvailableOutputs = nodeGroup.nodes().stream()
            .flatMap(n -> n.outputs().stream())
            .collect(Collectors.toUnmodifiableSet());
            
        gen.writeArrayFieldStart("allAvailableOutputs");
        for (ResourceIdentifier output : allAvailableOutputs) {
            serializeResourceIdentifier(output, gen);
        }
        gen.writeEndArray();
        
        // Metadata
        gen.writeObjectFieldStart("metadata");
        gen.writeNumberField("totalInputs", nodeGroup.inputs().size());
        gen.writeNumberField("totalOutputs", nodeGroup.outputs().size());
        gen.writeNumberField("totalAvailableOutputs", allAvailableOutputs.size());
        gen.writeBooleanField("isLeafGroup", nodeGroup.nodes().stream().allMatch(n -> !(n instanceof NodeGroup)));
        gen.writeEndObject();
    }
    
    private void serializeScope(Scope<ConnectionPoint> scope, JsonGenerator gen) throws IOException {
        if (scope == null) {
            gen.writeStringField("type", "null");
            gen.writeArrayFieldStart("resources");
            gen.writeEndArray();
        } else if (scope instanceof Include<ConnectionPoint> include) {
            gen.writeStringField("type", "Include");
            gen.writeArrayFieldStart("resources");
            for (ConnectionPoint cp : include.resources()) {
                serializeConnectionPoint(cp, gen);
            }
            gen.writeEndArray();
        } else if (scope instanceof Exclude<ConnectionPoint> exclude) {
            gen.writeStringField("type", "Exclude");
            gen.writeArrayFieldStart("resources");
            for (ConnectionPoint cp : exclude.resources()) {
                serializeConnectionPoint(cp, gen);
            }
            gen.writeEndArray();
        } else {
            gen.writeStringField("type", "unknown");
            gen.writeStringField("className", scope.getClass().getSimpleName());
            gen.writeArrayFieldStart("resources");
            gen.writeEndArray();
        }
    }
    
    private void serializeConnectionPoint(ConnectionPoint cp, JsonGenerator gen) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("nodePath", PathUtils.toUnixString(cp.nodePath()));
        gen.writeObjectFieldStart("resourceIdentifier");
        serializeResourceIdentifierFields(cp.rid(), gen);
        gen.writeEndObject();
        gen.writeEndObject();
    }
    
    private void serializeResourceIdentifier(ResourceIdentifier rid, JsonGenerator gen) throws IOException {
        gen.writeStartObject();
        serializeResourceIdentifierFields(rid, gen);
        gen.writeEndObject();
    }
    
    private void serializeResourceIdentifierFields(ResourceIdentifier rid, JsonGenerator gen) throws IOException {
        gen.writeStringField("id", rid.toString());
        gen.writeStringField("className", rid.getClass().getSimpleName());
        
        // Extract resource name and type if possible
        String ridString = rid.toString();
        if (ridString.contains(":")) {
            String[] parts = ridString.split(":", 2);
            gen.writeStringField("resourceName", parts[0]);
            gen.writeStringField("resourceType", parts[1]);
        } else {
            gen.writeStringField("resourceName", ridString);
            gen.writeStringField("resourceType", "unknown");
        }
    }
}
