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
        gen.writeStartObject();

        // Serialize outputContext directly using our custom serializer
        gen.writeFieldName("outputContext");
        // Use our custom serializer directly instead of findValueSerializer to avoid Jackson record handling
        OutputContextJsonSerializer outputContextSerializer = new OutputContextJsonSerializer();
        outputContextSerializer.serialize(outputResult.outputContext(), gen, serializers);

        // Serialize value using existing Result serializer
        gen.writeFieldName("value");
        serializers.findValueSerializer(outputResult.value().getClass())
            .serialize(outputResult.value(), gen, serializers);

        gen.writeEndObject();
    }
}
