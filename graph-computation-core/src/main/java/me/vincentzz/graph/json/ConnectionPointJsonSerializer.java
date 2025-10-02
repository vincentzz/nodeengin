package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import me.vincentzz.graph.node.ConnectionPoint;

import java.io.IOException;

/**
 * JSON serializer for ConnectionPoint.
 * Serializes ConnectionPoint as structured object with nodePath and resourceId.
 */
public class ConnectionPointJsonSerializer extends JsonSerializer<ConnectionPoint> {
    
    @Override
    public void serialize(ConnectionPoint connectionPoint, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        // Serialize nodePath as string
        gen.writeStringField("nodePath", connectionPoint.nodePath().toString());
        
        // Serialize resourceId using registered ResourceIdentifier serializer
        gen.writeFieldName("resourceId");
        try {
            JsonSerializer<Object> ridSerializer = serializers.findValueSerializer(connectionPoint.rid().getClass());
            ridSerializer.serialize(connectionPoint.rid(), gen, serializers);
        } catch (Exception e) {
            // Fallback: serialize as simple object with type and data
            gen.writeStartObject();
            gen.writeStringField("type", connectionPoint.rid().getClass().getSimpleName());
            gen.writeStringField("data", connectionPoint.rid().toString());
            gen.writeEndObject();
        }
        
        gen.writeEndObject();
    }
}
