package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import me.vincentzz.graph.model.output.OutputContext;

import java.io.IOException;

/**
 * Custom JSON serializer for OutputContext record.
 */
public class OutputContextJsonSerializer extends JsonSerializer<OutputContext> {

    @Override
    public void serialize(OutputContext outputContext, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        System.err.println("DEBUG OUTPUT CONTEXT: OutputContextJsonSerializer.serialize called for: " + outputContext);
        gen.writeStartObject();
        
        // Serialize resultType as string
        gen.writeStringField("resultType", outputContext.resultType().name());
        System.err.println("DEBUG OUTPUT CONTEXT: Wrote resultType: " + outputContext.resultType().name());
        
        gen.writeEndObject();
        System.err.println("DEBUG OUTPUT CONTEXT: Finished serializing OutputContext");
    }
}
