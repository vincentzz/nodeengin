package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import me.vincentzz.graph.model.ResourceIdentifier;

import java.io.IOException;

/**
 * JSON deserializer for ResourceIdentifier.
 * Consumes: {"type": "FalconResourceId", "data": {"ifo": ..., "source": ..., "attribute": ...}}
 */
public class ResourceIdentifierJsonDeserializer extends JsonDeserializer<ResourceIdentifier> {

    @Override
    public ResourceIdentifier deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        return deserializeFromNode(node);
    }

    static ResourceIdentifier deserializeFromNode(JsonNode node) {
        if (!node.has("type") || !node.has("data")) {
            throw new IllegalArgumentException("Invalid ResourceIdentifier JSON: must have 'type' and 'data' fields");
        }

        String type = node.get("type").asText();
        JsonNode dataNode = node.get("data");

        Class<? extends ResourceIdentifier> resourceClass = NodeTypeRegistry.getResourceClass(type);

        // Use NodeTypeRegistry to construct the resource from data fields
        return NodeTypeRegistry.createResourceFromData(type, resourceClass, dataNode);
    }
}
