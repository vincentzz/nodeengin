package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import me.vincentzz.graph.node.Flywire;

import java.io.IOException;

/**
 * JSON serializer for Flywire.
 * Produces: {"source": {connectionPoint}, "target": {connectionPoint}}
 */
public class FlywireJsonSerializer extends JsonSerializer<Flywire> {

    @Override
    public void serialize(Flywire flywire, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();

        gen.writeFieldName("source");
        serializers.findValueSerializer(flywire.source().getClass())
                .serialize(flywire.source(), gen, serializers);

        gen.writeFieldName("target");
        serializers.findValueSerializer(flywire.target().getClass())
                .serialize(flywire.target(), gen, serializers);

        gen.writeEndObject();
    }
}
