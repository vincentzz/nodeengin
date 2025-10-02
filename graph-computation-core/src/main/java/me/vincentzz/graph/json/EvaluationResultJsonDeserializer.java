package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import me.vincentzz.graph.model.AdhocOverride;
import me.vincentzz.graph.model.EvaluationResult;
import me.vincentzz.graph.model.NodeEvaluation;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.model.Snapshot;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.lang.Result.Result;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * JSON deserializer for EvaluationResult.
 * Deserializes entry lists back to Maps with object keys.
 */
public class EvaluationResultJsonDeserializer extends JsonDeserializer<EvaluationResult> {
    
    @Override
    public EvaluationResult deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        JsonNode node = p.getCodec().readTree(p);
        
        // Deserialize snapshot
        Snapshot snapshot = deserializeSnapshot(node.get("snapshot"), p, ctxt);
        
        // Deserialize requestedNodePath from string
        String requestedNodePathString = node.get("requestedNodePath").asText();
        Path requestedNodePath = Paths.get(requestedNodePathString);
        
        // Deserialize adhocOverride
        Optional<AdhocOverride> adhocOverride = deserializeAdhocOverride(node.get("adhocOverride"), p, ctxt);
        
        // Deserialize results from entry list
        Map<ResourceIdentifier, Result<Object>> results = deserializeResultsEntryList(
            node.get("results"), p, ctxt);
        
        // Deserialize nodeEvaluationMap from nested entry list
        Map<Path, NodeEvaluation> nodeEvaluationMap = deserializeNodeEvaluationMapEntryList(
            node.get("nodeEvaluationMap"), p, ctxt);
        
        // Deserialize graph
        CalculationNode graph = deserializeCalculationNode(node.get("graph"), p, ctxt);

        return new EvaluationResult(snapshot, requestedNodePath, adhocOverride, results, nodeEvaluationMap, graph);
    }
    
    private Snapshot deserializeSnapshot(JsonNode snapshotNode, JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonParser snapshotParser = snapshotNode.traverse(p.getCodec());
        snapshotParser.nextToken();
        return ctxt.readValue(snapshotParser, Snapshot.class);
    }
    
    private Optional<AdhocOverride> deserializeAdhocOverride(JsonNode adhocOverrideNode, JsonParser p, DeserializationContext ctxt) throws IOException {
        if (adhocOverrideNode == null || adhocOverrideNode.isNull()) {
            return Optional.empty();
        }
        
        JsonParser adhocParser = adhocOverrideNode.traverse(p.getCodec());
        adhocParser.nextToken();
        AdhocOverride adhocOverride = ctxt.readValue(adhocParser, AdhocOverride.class);
        return Optional.of(adhocOverride);
    }
    
    @SuppressWarnings("unchecked")
    private Map<ResourceIdentifier, Result<Object>> deserializeResultsEntryList(JsonNode entryListNode, JsonParser p, DeserializationContext ctxt) throws IOException {
        Map<ResourceIdentifier, Result<Object>> map = new HashMap<>();
        
        if (entryListNode != null && entryListNode.isArray()) {
            for (JsonNode entryNode : entryListNode) {
                // Deserialize key (ResourceIdentifier)
                JsonNode keyNode = entryNode.get("key");
                JsonParser keyParser = keyNode.traverse(p.getCodec());
                keyParser.nextToken();
                ResourceIdentifier key = ctxt.readValue(keyParser, ResourceIdentifier.class);
                
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
    
    private Map<Path, NodeEvaluation> deserializeNodeEvaluationMapEntryList(JsonNode entryListNode, JsonParser p, DeserializationContext ctxt) throws IOException {
        Map<Path, NodeEvaluation> map = new HashMap<>();
        
        if (entryListNode != null && entryListNode.isArray()) {
            for (JsonNode entryNode : entryListNode) {
                // Deserialize outer key (Path)
                String pathString = entryNode.get("key").asText();
                Path path = Paths.get(pathString);
                
                // Deserialize outer value (NodeEvaluation)
                JsonNode valueNode = entryNode.get("value");
                JsonParser valueParser = valueNode.traverse(p.getCodec());
                valueParser.nextToken();
                NodeEvaluation nodeEvaluation = ctxt.readValue(valueParser, NodeEvaluation.class);
                
                map.put(path, nodeEvaluation);
            }
        }
        
        return map;
    }
    
    private CalculationNode deserializeCalculationNode(JsonNode calculationNodeNode, JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonParser nodeParser = calculationNodeNode.traverse(p.getCodec());
        nodeParser.nextToken();
        return ctxt.readValue(nodeParser, CalculationNode.class);
    }
}
