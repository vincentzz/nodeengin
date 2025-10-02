package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import me.vincentzz.graph.node.Flywire;

import java.io.IOException;

/**
 * JSON serializer for Flywire.
 * Handles the serialization of flywire objects with source and target ConnectionPoints.
 */
public class FlywireJsonSerializer extends JsonSerializer<Flywire> {
    
    @Override
    public void serialize(Flywire flywire, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        // Serialize source ConnectionPoint
        gen.writeFieldName("source");
        serializers.findValueSerializer(flywire.source().getClass())
            .serialize(flywire.source(), gen, serializers);
        
        // Serialize target ConnectionPoint
        gen.writeFieldName("target");
        serializers.findValueSerializer(flywire.target().getClass())
            .serialize(flywire.target(), gen, serializers);
        
        gen.writeEndObject();
    }
}
