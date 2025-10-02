package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import me.vincentzz.graph.model.input.InputContext;

import java.io.IOException;

/**
 * Custom JSON serializer for InputContext record.
 */
public class InputContextJsonSerializer extends JsonSerializer<InputContext> {

    @Override
    public void serialize(InputContext inputContext, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        // Serialize sourceType as string
        gen.writeStringField("sourceType", inputContext.sourceType().name());
        
        // Serialize isDirectInput Optional
        if (inputContext.isDirectInput().isPresent()) {
            gen.writeBooleanField("isDirectInput", inputContext.isDirectInput().get());
        } else {
            gen.writeNullField("isDirectInput");
        }
        
        gen.writeEndObject();
    }
}
