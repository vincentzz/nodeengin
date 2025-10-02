package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import me.vincentzz.graph.model.output.OutputContext;
import me.vincentzz.graph.model.output.OutputValueType;

import java.io.IOException;

/**
 * Custom JSON deserializer for OutputContext record.
 */
public class OutputContextJsonDeserializer extends JsonDeserializer<OutputContext> {

    @Override
    public OutputContext deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        System.err.println("DEBUG OUTPUT_CONTEXT_DESER: OutputContextJsonDeserializer.deserialize() called!");
        OutputValueType resultType = null;
        
        // Parse the JSON object
        if (p.getCurrentToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected START_OBJECT token");
        }
        
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = p.getCurrentName();
            p.nextToken(); // Move to field value
            
            switch (fieldName) {
                case "resultType":
                    String resultTypeStr = p.getValueAsString();
                    resultType = OutputValueType.valueOf(resultTypeStr);
                    break;
                    
                default:
                    // Skip unknown fields
                    p.skipChildren();
                    break;
            }
        }
        
        if (resultType == null) {
            throw new IOException("Missing required field: resultType");
        }
        
        System.err.println("DEBUG OUTPUT_CONTEXT_DESER: Successfully created OutputContext with resultType: " + resultType);
        return new OutputContext(resultType);
    }
}
