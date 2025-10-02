package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import me.vincentzz.graph.model.input.InputContext;
import me.vincentzz.graph.model.input.InputSourceType;

import java.io.IOException;
import java.util.Optional;

/**
 * Custom JSON deserializer for InputContext record.
 */
public class InputContextJsonDeserializer extends JsonDeserializer<InputContext> {

    @Override
    public InputContext deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        InputSourceType sourceType = null;
        Optional<Boolean> isDirectInput = Optional.empty();
        
        // Parse the JSON object
        if (p.getCurrentToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected START_OBJECT token");
        }
        
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = p.getCurrentName();
            p.nextToken(); // Move to field value
            
            switch (fieldName) {
                case "sourceType":
                    String sourceTypeStr = p.getValueAsString();
                    sourceType = InputSourceType.valueOf(sourceTypeStr);
                    break;
                    
                case "isDirectInput":
                    if (p.getCurrentToken() == JsonToken.VALUE_NULL) {
                        isDirectInput = Optional.empty();
                    } else {
                        isDirectInput = Optional.of(p.getBooleanValue());
                    }
                    break;
                    
                default:
                    // Skip unknown fields
                    p.skipChildren();
                    break;
            }
        }
        
        if (sourceType == null) {
            throw new IOException("Missing required field: sourceType");
        }
        
        return new InputContext(sourceType, isDirectInput);
    }
}
