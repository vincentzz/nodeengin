package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
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
 * Deserializes entry lists back to Maps with object keys.
 */
public class AdhocOverrideJsonDeserializer extends JsonDeserializer<AdhocOverride> {
    
    @Override
    public AdhocOverride deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        JsonNode node = p.getCodec().readTree(p);
        
        // Deserialize adhocInputs from entry list
        Map<ConnectionPoint, Result<Object>> adhocInputs = deserializeEntryList(
            node.get("adhocInputs"), p, ctxt);
        
        // Deserialize adhocOutputs from entry list
        Map<ConnectionPoint, Result<Object>> adhocOutputs = deserializeEntryList(
            node.get("adhocOutputs"), p, ctxt);
        
        // Deserialize adhocFlywires as regular set
        Set<Flywire> adhocFlywires = deserializeFlywires(node.get("adhocFlywires"), p, ctxt);
        
        return new AdhocOverride(adhocInputs, adhocOutputs, adhocFlywires);
    }
    
    @SuppressWarnings("unchecked")
    private Map<ConnectionPoint, Result<Object>> deserializeEntryList(JsonNode entryListNode, JsonParser p, DeserializationContext ctxt) throws IOException {
        Map<ConnectionPoint, Result<Object>> map = new HashMap<>();
        
        if (entryListNode != null && entryListNode.isArray()) {
            for (JsonNode entryNode : entryListNode) {
                // Deserialize key (ConnectionPoint)
                JsonNode keyNode = entryNode.get("key");
                JsonParser keyParser = keyNode.traverse(p.getCodec());
                keyParser.nextToken();
                ConnectionPoint key = ctxt.readValue(keyParser, ConnectionPoint.class);
                
                // Deserialize value (Result<Object>)
                JsonNode valueNode = entryNode.get("value");
                JsonParser valueParser = valueNode.traverse(p.getCodec());
                valueParser.nextToken();
                Result<Object> value = ctxt.readValue(valueParser, Result.class);
                
                map.put(key, value);
            }
        }
        
        return map;
    }
    
    @SuppressWarnings("unchecked")
    private Set<Flywire> deserializeFlywires(JsonNode flywireNode, JsonParser p, DeserializationContext ctxt) throws IOException {
        Set<Flywire> flywires = new HashSet<>();
        
        if (flywireNode != null && flywireNode.isArray()) {
            for (JsonNode flywireEntry : flywireNode) {
                // Deserialize each flywire individually using the FlywireJsonDeserializer
                JsonParser flywireParser = flywireEntry.traverse(p.getCodec());
                flywireParser.nextToken();
                Flywire flywire = ctxt.readValue(flywireParser, Flywire.class);
                flywires.add(flywire);
            }
        }
        
        return flywires;
    }
}
