package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import me.vincentzz.graph.model.Snapshot;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

/**
 * JSON deserializer for Snapshot.
 */
public class SnapshotJsonDeserializer extends JsonDeserializer<Snapshot> {
    
    @Override
    public Snapshot deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        JsonNode node = p.getCodec().readTree(p);
        
        // Deserialize logicalTimestamp
        Optional<Instant> logicalTimestamp = Optional.empty();
        JsonNode logicalNode = node.get("logicalTimestamp");
        if (logicalNode != null && !logicalNode.isNull()) {
            logicalTimestamp = Optional.of(Instant.parse(logicalNode.asText()));
        }
        
        // Deserialize physicalTimestamp
        Optional<Instant> physicalTimestamp = Optional.empty();
        JsonNode physicalNode = node.get("physicalTimestamp");
        if (physicalNode != null && !physicalNode.isNull()) {
            physicalTimestamp = Optional.of(Instant.parse(physicalNode.asText()));
        }
        
        return new Snapshot(logicalTimestamp, physicalTimestamp);
    }
}
