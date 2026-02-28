package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.node.Flywire;

import java.io.IOException;

/**
 * JSON deserializer for Flywire.
 * Consumes: {"source": {connectionPoint}, "target": {connectionPoint}}
 */
public class FlywireJsonDeserializer extends JsonDeserializer<Flywire> {

    @Override
    public Flywire deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        ConnectionPoint source = ConnectionPointJsonDeserializer.deserializeFromNode(node.get("source"));
        ConnectionPoint target = ConnectionPointJsonDeserializer.deserializeFromNode(node.get("target"));
        return new Flywire(source, target);
    }
}
