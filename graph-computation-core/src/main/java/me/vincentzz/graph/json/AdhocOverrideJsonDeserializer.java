package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import me.vincentzz.graph.model.AdhocOverride;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.node.Flywire;
import me.vincentzz.lang.Result.Result;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * JSON deserializer for AdhocOverride.
 * Consumes:
 * {
 *   "inputs": [{"connectionPoint": {...}, "result": {...}}, ...],
 *   "outputs": [{"connectionPoint": {...}, "result": {...}}, ...],
 *   "flywires": [{flywire}, ...]
 * }
 */
public class AdhocOverrideJsonDeserializer extends JsonDeserializer<AdhocOverride> {

    @Override
    public AdhocOverride deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);

        Map<ConnectionPoint, Result<Object>> inputs = deserializeConnectionPointResultMap(node.get("inputs"));
        Map<ConnectionPoint, Result<Object>> outputs = deserializeConnectionPointResultMap(node.get("outputs"));
        Set<Flywire> flywires = deserializeFlywires(node.get("flywires"));

        return new AdhocOverride(inputs, outputs, flywires);
    }

    private Map<ConnectionPoint, Result<Object>> deserializeConnectionPointResultMap(JsonNode arrayNode) {
        Map<ConnectionPoint, Result<Object>> map = new HashMap<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode entryNode : arrayNode) {
                ConnectionPoint cp = ConnectionPointJsonDeserializer.deserializeFromNode(entryNode.get("connectionPoint"));
                Result<Object> result = ResultJsonDeserializer.deserializeFromNode(entryNode.get("result"));
                map.put(cp, result);
            }
        }
        return map;
    }

    private Set<Flywire> deserializeFlywires(JsonNode arrayNode) {
        Set<Flywire> flywires = new HashSet<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode flywireNode : arrayNode) {
                ConnectionPoint source = ConnectionPointJsonDeserializer.deserializeFromNode(flywireNode.get("source"));
                ConnectionPoint target = ConnectionPointJsonDeserializer.deserializeFromNode(flywireNode.get("target"));
                flywires.add(new Flywire(source, target));
            }
        }
        return flywires;
    }
}
