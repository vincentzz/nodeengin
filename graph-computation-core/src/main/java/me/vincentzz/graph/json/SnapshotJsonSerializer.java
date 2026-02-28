package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import me.vincentzz.graph.model.Snapshot;

import java.io.IOException;

/**
 * JSON serializer for Snapshot.
 * Produces: {"logicalTimestamp": "2025-...", "physicalTimestamp": null}
 */
public class SnapshotJsonSerializer extends JsonSerializer<Snapshot> {

    @Override
    public void serialize(Snapshot snapshot, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();

        gen.writeFieldName("logicalTimestamp");
        if (snapshot.logicalTimestamp().isPresent()) {
            gen.writeString(snapshot.logicalTimestamp().get().toString());
        } else {
            gen.writeNull();
        }

        gen.writeFieldName("physicalTimestamp");
        if (snapshot.physicalTimestamp().isPresent()) {
            gen.writeString(snapshot.physicalTimestamp().get().toString());
        } else {
            gen.writeNull();
        }

        gen.writeEndObject();
    }
}
