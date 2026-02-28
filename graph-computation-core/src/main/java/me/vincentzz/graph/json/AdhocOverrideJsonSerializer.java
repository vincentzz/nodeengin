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
 * Produces:
 * {
 *   "inputs": [{"connectionPoint": {...}, "result": {...}}, ...],
 *   "outputs": [{"connectionPoint": {...}, "result": {...}}, ...],
 *   "flywires": [{flywire}, ...]
 * }
 */
public class AdhocOverrideJsonSerializer extends JsonSerializer<AdhocOverride> {

    @Override
    public void serialize(AdhocOverride adhoc, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();

        // inputs (was adhocInputs)
        gen.writeArrayFieldStart("inputs");
        for (Map.Entry<ConnectionPoint, Result<Object>> entry : adhoc.adhocInputs().entrySet()) {
            gen.writeStartObject();
            gen.writeFieldName("connectionPoint");
            serializers.findValueSerializer(ConnectionPoint.class)
                    .serialize(entry.getKey(), gen, serializers);
            gen.writeFieldName("result");
            serializers.findValueSerializer(entry.getValue().getClass())
                    .serialize(entry.getValue(), gen, serializers);
            gen.writeEndObject();
        }
        gen.writeEndArray();

        // outputs (was adhocOutputs)
        gen.writeArrayFieldStart("outputs");
        for (Map.Entry<ConnectionPoint, Result<Object>> entry : adhoc.adhocOutputs().entrySet()) {
            gen.writeStartObject();
            gen.writeFieldName("connectionPoint");
            serializers.findValueSerializer(ConnectionPoint.class)
                    .serialize(entry.getKey(), gen, serializers);
            gen.writeFieldName("result");
            serializers.findValueSerializer(entry.getValue().getClass())
                    .serialize(entry.getValue(), gen, serializers);
            gen.writeEndObject();
        }
        gen.writeEndArray();

        // flywires (was adhocFlywires)
        gen.writeArrayFieldStart("flywires");
        for (var flywire : adhoc.adhocFlywires()) {
            serializers.findValueSerializer(flywire.getClass())
                    .serialize(flywire, gen, serializers);
        }
        gen.writeEndArray();

        gen.writeEndObject();
    }
}
