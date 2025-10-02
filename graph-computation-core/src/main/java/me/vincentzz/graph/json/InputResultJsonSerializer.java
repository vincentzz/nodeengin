package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import me.vincentzz.graph.model.input.InputResult;

import java.io.IOException;

/**
 * JSON serializer for InputResult.
 */
public class InputResultJsonSerializer extends JsonSerializer<InputResult> {
    
    @Override
    public void serialize(InputResult inputResult, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        // Serialize inputContext directly using our custom serializer
        gen.writeFieldName("inputContext");
        // Use our custom serializer directly instead of findValueSerializer to avoid Jackson record handling
        InputContextJsonSerializer inputContextSerializer = new InputContextJsonSerializer();
        inputContextSerializer.serialize(inputResult.inputContext(), gen, serializers);
        
        // Serialize value using existing Result serializer
        gen.writeFieldName("value");
        serializers.findValueSerializer(inputResult.value().getClass())
            .serialize(inputResult.value(), gen, serializers);
        
        gen.writeEndObject();
    }
}
