package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import me.vincentzz.graph.model.Snapshot;

import java.io.IOException;
import java.time.Instant;

/**
 * JSON serializer for Snapshot.
 */
public class SnapshotJsonSerializer extends JsonSerializer<Snapshot> {
    
    @Override
    public void serialize(Snapshot snapshot, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        // Serialize logicalTimestamp
        gen.writeFieldName("logicalTimestamp");
        if (snapshot.logicalTimestamp().isPresent()) {
            gen.writeString(snapshot.logicalTimestamp().get().toString());
        } else {
            gen.writeNull();
        }
        
        // Serialize physicalTimestamp
        gen.writeFieldName("physicalTimestamp");
        if (snapshot.physicalTimestamp().isPresent()) {
            gen.writeString(snapshot.physicalTimestamp().get().toString());
        } else {
            gen.writeNull();
        }
        
        gen.writeEndObject();
    }
}
