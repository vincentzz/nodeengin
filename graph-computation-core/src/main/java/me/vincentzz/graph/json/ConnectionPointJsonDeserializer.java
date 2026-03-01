package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.node.ConnectionPoint;

import java.io.IOException;
import java.nio.file.Path;

/**
 * JSON deserializer for ConnectionPoint.
 * Consumes: {"nodePath": "/root/node", "rid": {"type": "FalconRawTopic", "data": {...}}}
 */
public class ConnectionPointJsonDeserializer extends JsonDeserializer<ConnectionPoint> {

    @Override
    public ConnectionPoint deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        return deserializeFromNode(node);
    }

    static ConnectionPoint deserializeFromNode(JsonNode node) {
        String nodePath = node.get("nodePath").asText();
        JsonNode ridNode = node.get("rid");
        ResourceIdentifier rid = ResourceIdentifierJsonDeserializer.deserializeFromNode(ridNode);
        return ConnectionPoint.of(Path.of(nodePath), rid);
    }
}
