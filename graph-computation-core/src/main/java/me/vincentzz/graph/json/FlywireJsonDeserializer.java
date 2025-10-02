package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.node.Flywire;

import java.io.IOException;

/**
 * JSON deserializer for Flywire.
 * Handles the deserialization of flywire objects with source and target ConnectionPoints.
 */
public class FlywireJsonDeserializer extends JsonDeserializer<Flywire> {
    
    @Override
    public Flywire deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        JsonNode node = p.getCodec().readTree(p);
        
        // Deserialize source ConnectionPoint
        JsonNode sourceNode = node.get("source");
        JsonParser sourceParser = sourceNode.traverse(p.getCodec());
        sourceParser.nextToken();
        ConnectionPoint source = ctxt.readValue(sourceParser, ConnectionPoint.class);
        
        // Deserialize target ConnectionPoint
        JsonNode targetNode = node.get("target");
        JsonParser targetParser = targetNode.traverse(p.getCodec());
        targetParser.nextToken();
        ConnectionPoint target = ctxt.readValue(targetParser, ConnectionPoint.class);
        
        return new Flywire(source, target);
    }
}
