package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import me.vincentzz.graph.model.AdhocOverride;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.lang.Result.Result;

import java.io.IOException;
import java.util.Map;

/**
 * JSON serializer for AdhocOverride.
 * Serializes Maps with object keys as entry lists for better JSON compatibility.
 */
public class AdhocOverrideJsonSerializer extends JsonSerializer<AdhocOverride> {
    
    @Override
    public void serialize(AdhocOverride adhocOverride, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        // Serialize adhocInputs as entry list
        gen.writeFieldName("adhocInputs");
        gen.writeStartArray();
        for (Map.Entry<ConnectionPoint, Result<Object>> entry : adhocOverride.adhocInputs().entrySet()) {
            gen.writeStartObject();
            
            gen.writeFieldName("key");
            serializers.findValueSerializer(ConnectionPoint.class).serialize(entry.getKey(), gen, serializers);
            
            gen.writeFieldName("value");
            serializers.findValueSerializer(Result.class).serialize(entry.getValue(), gen, serializers);
            
            gen.writeEndObject();
        }
        gen.writeEndArray();
        
        // Serialize adhocOutputs as entry list
        gen.writeFieldName("adhocOutputs");
        gen.writeStartArray();
        for (Map.Entry<ConnectionPoint, Result<Object>> entry : adhocOverride.adhocOutputs().entrySet()) {
            gen.writeStartObject();
            
            gen.writeFieldName("key");
            serializers.findValueSerializer(ConnectionPoint.class).serialize(entry.getKey(), gen, serializers);
            
            gen.writeFieldName("value");
            serializers.findValueSerializer(Result.class).serialize(entry.getValue(), gen, serializers);
            
            gen.writeEndObject();
        }
        gen.writeEndArray();
        
        // Serialize adhocFlywires as array of properly serialized Flywire objects
        gen.writeFieldName("adhocFlywires");
        gen.writeStartArray();
        System.err.println("DEBUG ADHOC: Serializing " + adhocOverride.adhocFlywires().size() + " adhoc flywires");
        for (me.vincentzz.graph.node.Flywire flywire : adhocOverride.adhocFlywires()) {
            System.err.println("DEBUG ADHOC: Serializing flywire: " + flywire);
            serializers.findValueSerializer(me.vincentzz.graph.node.Flywire.class).serialize(flywire, gen, serializers);
        }
        gen.writeEndArray();
        
        gen.writeEndObject();
    }
}
