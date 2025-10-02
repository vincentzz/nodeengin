package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import me.vincentzz.graph.node.NodeGroup;

import java.io.IOException;

/**
 * Custom Jackson deserializer for NodeGroup.
 * Since NodeGroup contains complex polymorphic types that may not be deserializable,
 * this deserializer creates a NodeGroupStructure representation instead of a full NodeGroup.
 */
public class NodeGroupJsonDeserializer extends JsonDeserializer<NodeGroupStructure> {
    
    @Override
    public NodeGroupStructure deserialize(JsonParser p, DeserializationContext ctxt) 
            throws IOException, JsonProcessingException {
        JsonNode node = p.getCodec().readTree(p);
        return parseNodeGroupStructure(node);
    }
    
    private NodeGroupStructure parseNodeGroupStructure(JsonNode node) {
        NodeGroupStructure.Builder builder = NodeGroupStructure.builder()
            .name(node.get("name").asText())
            .type(node.get("type").asText())
            .nodeCount(node.get("nodeCount").asInt());
        
        // Parse nodes
        JsonNode nodesArray = node.get("nodes");
        if (nodesArray != null && nodesArray.isArray()) {
            for (JsonNode nodeElement : nodesArray) {
                builder.addNode(parseNodeStructure(nodeElement));
            }
        }
        
        // Parse scope
        JsonNode scopeNode = node.get("outputScope");
        if (scopeNode != null) {
            builder.outputScope(parseScopeStructure(scopeNode));
        }
        
        // Parse inputs/outputs
        JsonNode inputsArray = node.get("inputs");
        if (inputsArray != null && inputsArray.isArray()) {
            for (JsonNode input : inputsArray) {
                builder.addInput(parseResourceStructure(input));
            }
        }
        
        JsonNode outputsArray = node.get("outputs");
        if (outputsArray != null && outputsArray.isArray()) {
            for (JsonNode output : outputsArray) {
                builder.addOutput(parseResourceStructure(output));
            }
        }
        
        JsonNode allOutputsArray = node.get("allAvailableOutputs");
        if (allOutputsArray != null && allOutputsArray.isArray()) {
            for (JsonNode output : allOutputsArray) {
                builder.addAllAvailableOutput(parseResourceStructure(output));
            }
        }
        
        // Parse metadata
        JsonNode metadataNode = node.get("metadata");
        if (metadataNode != null) {
            NodeGroupStructure.MetadataStructure metadata = new NodeGroupStructure.MetadataStructure(
                metadataNode.get("totalInputs").asInt(),
                metadataNode.get("totalOutputs").asInt(),
                metadataNode.get("totalAvailableOutputs").asInt(),
                metadataNode.get("isLeafGroup").asBoolean()
            );
            builder.metadata(metadata);
        }
        
        return builder.build();
    }
    
    private NodeGroupStructure.NodeStructure parseNodeStructure(JsonNode node) {
        NodeGroupStructure.NodeStructure.Builder builder = NodeGroupStructure.NodeStructure.builder()
            .name(node.get("name").asText())
            .type(node.get("type").asText());
        
        if (node.has("className")) {
            builder.className(node.get("className").asText());
        }
        
        if (node.has("nodeCount")) {
            builder.nodeCount(node.get("nodeCount").asInt());
        }
        
        // Parse nested group if present
        JsonNode nestedGroupNode = node.get("nestedGroup");
        if (nestedGroupNode != null) {
            builder.nestedGroup(parseNodeGroupStructure(nestedGroupNode));
        }
        
        // Parse inputs
        JsonNode inputsArray = node.get("inputs");
        if (inputsArray != null && inputsArray.isArray()) {
            for (JsonNode input : inputsArray) {
                builder.addInput(parseResourceStructure(input));
            }
        }
        
        // Parse outputs
        JsonNode outputsArray = node.get("outputs");
        if (outputsArray != null && outputsArray.isArray()) {
            for (JsonNode output : outputsArray) {
                builder.addOutput(parseResourceStructure(output));
            }
        }
        
        return builder.build();
    }
    
    private NodeGroupStructure.ScopeStructure parseScopeStructure(JsonNode node) {
        String type = node.get("type").asText();
        NodeGroupStructure.ScopeStructure.Builder builder = NodeGroupStructure.ScopeStructure.builder()
            .type(type);
        
        JsonNode resourcesArray = node.get("resources");
        if (resourcesArray != null && resourcesArray.isArray()) {
            for (JsonNode resource : resourcesArray) {
                builder.addResource(parseConnectionPointStructure(resource));
            }
        }
        
        return builder.build();
    }
    
    private NodeGroupStructure.ConnectionPointStructure parseConnectionPointStructure(JsonNode node) {
        String nodeName = node.get("nodePath").asText();
        NodeGroupStructure.ResourceStructure resource = parseResourceStructure(node.get("resourceIdentifier"));
        
        return new NodeGroupStructure.ConnectionPointStructure(nodeName, resource);
    }
    
    private NodeGroupStructure.ResourceStructure parseResourceStructure(JsonNode node) {
        return new NodeGroupStructure.ResourceStructure(
            node.get("id").asText(),
            node.get("className").asText(),
            node.get("resourceName").asText(),
            node.get("resourceType").asText()
        );
    }
}
