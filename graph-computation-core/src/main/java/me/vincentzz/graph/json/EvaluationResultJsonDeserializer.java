package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import me.vincentzz.graph.model.*;
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
 * Consumes the new format with:
 * - nodeEvaluations as a JSON object (path keys → values)
 * - results as [{"rid": {...}, "result": {...}}, ...]
 * - graph as an embedded Graph Definition
 */
public class EvaluationResultJsonDeserializer extends JsonDeserializer<EvaluationResult> {

    @Override
    public EvaluationResult deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);

        // snapshot
        Snapshot snapshot = deserializeSnapshot(node.get("snapshot"), p, ctxt);

        // requestedNodePath
        Path requestedNodePath = Paths.get(node.get("requestedNodePath").asText());

        // adhocOverride
        Optional<AdhocOverride> adhocOverride = Optional.empty();
        JsonNode adhocNode = node.get("adhocOverride");
        if (adhocNode != null && !adhocNode.isNull()) {
            JsonParser adhocParser = adhocNode.traverse(p.getCodec());
            adhocParser.nextToken();
            adhocOverride = Optional.of(ctxt.readValue(adhocParser, AdhocOverride.class));
        }

        // results — [{"rid": {...}, "result": {...}}, ...]
        Map<ResourceIdentifier, Result<Object>> results = new HashMap<>();
        JsonNode resultsArray = node.get("results");
        if (resultsArray != null && resultsArray.isArray()) {
            for (JsonNode entryNode : resultsArray) {
                ResourceIdentifier rid = ResourceIdentifierJsonDeserializer.deserializeFromNode(entryNode.get("rid"));
                Result<Object> result = ResultJsonDeserializer.deserializeFromNode(entryNode.get("result"));
                results.put(rid, result);
            }
        }

        // nodeEvaluations — JSON object with path string keys
        Map<Path, NodeEvaluation> nodeEvaluationMap = new HashMap<>();
        JsonNode nodeEvalsNode = node.get("nodeEvaluations");
        if (nodeEvalsNode != null && nodeEvalsNode.isObject()) {
            nodeEvalsNode.fields().forEachRemaining(entry -> {
                Path path = Paths.get(entry.getKey());
                NodeEvaluation eval = NodeEvaluationJsonDeserializer.deserializeFromNode(entry.getValue());
                nodeEvaluationMap.put(path, eval);
            });
        }

        // graph
        JsonNode graphNode = node.get("graph");
        JsonParser graphParser = graphNode.traverse(p.getCodec());
        graphParser.nextToken();
        CalculationNode graph = ctxt.readValue(graphParser, CalculationNode.class);

        return new EvaluationResult(snapshot, requestedNodePath, adhocOverride, results, nodeEvaluationMap, graph);
    }

    private Snapshot deserializeSnapshot(JsonNode snapshotNode, JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonParser snapshotParser = snapshotNode.traverse(p.getCodec());
        snapshotParser.nextToken();
        return ctxt.readValue(snapshotParser, Snapshot.class);
    }
}
