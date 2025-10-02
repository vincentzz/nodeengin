package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import me.vincentzz.graph.model.output.OutputResult;

import java.io.IOException;

/**
 * JSON serializer for OutputResult.
 */
public class OutputResultJsonSerializer extends JsonSerializer<OutputResult> {
    
    @Override
    public void serialize(OutputResult outputResult, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        System.err.println("DEBUG OUTPUT RESULT: OutputResultJsonSerializer.serialize called for: " + outputResult);
        gen.writeStartObject();
        
        // Serialize outputContext directly using our custom serializer
        gen.writeFieldName("outputContext");
        System.err.println("DEBUG OUTPUT RESULT: About to serialize outputContext: " + outputResult.outputContext());
        // Use our custom serializer directly instead of findValueSerializer to avoid Jackson record handling
        OutputContextJsonSerializer outputContextSerializer = new OutputContextJsonSerializer();
        outputContextSerializer.serialize(outputResult.outputContext(), gen, serializers);
        
        // Serialize value using existing Result serializer
        gen.writeFieldName("value");
        System.err.println("DEBUG OUTPUT RESULT: About to serialize result value: " + outputResult.value());
        serializers.findValueSerializer(outputResult.value().getClass())
            .serialize(outputResult.value(), gen, serializers);
        
        gen.writeEndObject();
        System.err.println("DEBUG OUTPUT RESULT: Finished serializing OutputResult");
    }
}
