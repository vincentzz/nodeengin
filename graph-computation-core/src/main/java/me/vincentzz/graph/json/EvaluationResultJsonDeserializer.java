package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import me.vincentzz.graph.model.*;
import me.vincentzz.lang.Result.Result;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * JSON deserializer for EvaluationResult.
 * Consumes the format with:
 * - request as a nested object (requestedResourceIds, snapshot, requestedNodePath, adhocOverride)
 * - results as [{"rid": {...}, "result": {...}}, ...]
 * - evaluations as a JSON object (path keys → values)
 */
public class EvaluationResultJsonDeserializer extends JsonDeserializer<EvaluationResult> {

    @Override
    public EvaluationResult deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);

        // request
        JsonNode requestNode = node.get("request");

        // rids
        Set<ResourceIdentifier> requestedResourceIds = new LinkedHashSet<>();
        JsonNode ridsArray = requestNode.get("rids");
        if (ridsArray != null && ridsArray.isArray()) {
            for (JsonNode ridNode : ridsArray) {
                requestedResourceIds.add(ResourceIdentifierJsonDeserializer.deserializeFromNode(ridNode));
            }
        }

        // snapshot
        Snapshot snapshot = deserializeSnapshot(requestNode.get("snapshot"), p, ctxt);

        // path
        Path requestedNodePath = Paths.get(requestNode.get("path").asText());

        // adhocOverride
        Optional<AdhocOverride> adhocOverride = Optional.empty();
        JsonNode adhocNode = requestNode.get("override");
        if (adhocNode != null && !adhocNode.isNull()) {
            JsonParser adhocParser = adhocNode.traverse(p.getCodec());
            adhocParser.nextToken();
            adhocOverride = Optional.of(ctxt.readValue(adhocParser, AdhocOverride.class));
        }

        EvaluationRequest request = new EvaluationRequest(Set.copyOf(requestedResourceIds), snapshot, requestedNodePath, adhocOverride);

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

        // evaluations — JSON object with path string keys
        Map<Path, NodeEvaluation> evaluations = new HashMap<>();
        JsonNode evalsNode = node.get("evaluations");
        if (evalsNode != null && evalsNode.isObject()) {
            evalsNode.fields().forEachRemaining(entry -> {
                Path path = Paths.get(entry.getKey());
                NodeEvaluation eval = NodeEvaluationJsonDeserializer.deserializeFromNode(entry.getValue());
                evaluations.put(path, eval);
            });
        }

        return new EvaluationResult(request, results, evaluations);
    }

    private Snapshot deserializeSnapshot(JsonNode snapshotNode, JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonParser snapshotParser = snapshotNode.traverse(p.getCodec());
        snapshotParser.nextToken();
        return ctxt.readValue(snapshotParser, Snapshot.class);
    }
}
