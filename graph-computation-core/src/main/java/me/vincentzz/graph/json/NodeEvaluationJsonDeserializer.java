package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import me.vincentzz.graph.model.NodeEvaluation;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.model.input.InputResult;
import me.vincentzz.graph.model.output.OutputResult;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * JSON deserializer for NodeEvaluation.
 * Deserializes entry lists back to Maps with ResourceIdentifier keys.
 */
public class NodeEvaluationJsonDeserializer extends JsonDeserializer<NodeEvaluation> {
    
    @Override
    public NodeEvaluation deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        JsonNode node = p.getCodec().readTree(p);
        
        // Deserialize inputs from entry list
        Map<ResourceIdentifier, InputResult> inputs = deserializeInputsEntryList(
            node.get("inputs"), p, ctxt);
        
        // Deserialize outputs from entry list
        Map<ResourceIdentifier, OutputResult> outputs = deserializeOutputsEntryList(
            node.get("outputs"), p, ctxt);
        
        return new NodeEvaluation(inputs, outputs);
    }
    
    private Map<ResourceIdentifier, InputResult> deserializeInputsEntryList(JsonNode entryListNode, JsonParser p, DeserializationContext ctxt) throws IOException {
        Map<ResourceIdentifier, InputResult> map = new HashMap<>();
        
        if (entryListNode != null && entryListNode.isArray()) {
            for (JsonNode entryNode : entryListNode) {
                // Deserialize key (ResourceIdentifier)
                JsonNode keyNode = entryNode.get("key");
                JsonParser keyParser = keyNode.traverse(p.getCodec());
                keyParser.nextToken();
                ResourceIdentifier key = ctxt.readValue(keyParser, ResourceIdentifier.class);
                
                // Deserialize value (InputResult) using custom deserializer directly
                JsonNode valueNode = entryNode.get("value");
                JsonParser valueParser = valueNode.traverse(p.getCodec());
                valueParser.nextToken();
                InputResultJsonDeserializer inputResultDeserializer = new InputResultJsonDeserializer();
                InputResult value = inputResultDeserializer.deserialize(valueParser, ctxt);
                
                map.put(key, value);
            }
        }
        
        return map;
    }
    
    private Map<ResourceIdentifier, OutputResult> deserializeOutputsEntryList(JsonNode entryListNode, JsonParser p, DeserializationContext ctxt) throws IOException {
        Map<ResourceIdentifier, OutputResult> map = new HashMap<>();
        
        if (entryListNode != null && entryListNode.isArray()) {
            for (JsonNode entryNode : entryListNode) {
                // Deserialize key (ResourceIdentifier)
                JsonNode keyNode = entryNode.get("key");
                JsonParser keyParser = keyNode.traverse(p.getCodec());
                keyParser.nextToken();
                ResourceIdentifier key = ctxt.readValue(keyParser, ResourceIdentifier.class);
                
                // Deserialize value (OutputResult) using custom deserializer directly
                JsonNode valueNode = entryNode.get("value");
                JsonParser valueParser = valueNode.traverse(p.getCodec());
                valueParser.nextToken();
                OutputResultJsonDeserializer outputResultDeserializer = new OutputResultJsonDeserializer();
                OutputResult value = outputResultDeserializer.deserialize(valueParser, ctxt);
                
                map.put(key, value);
            }
        }
        
        return map;
    }
}
