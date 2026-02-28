package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import me.vincentzz.graph.model.EvaluationResult;
import me.vincentzz.graph.model.NodeEvaluation;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.lang.PathUtils;
import me.vincentzz.lang.Result.Result;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * JSON serializer for EvaluationResult.
 * Produces the Evaluation Result schema with:
 * - nodeEvaluations as a JSON object (path keys → values)
 * - results as [{"rid": {...}, "result": {...}}, ...]
 * - graph as an embedded Graph Definition
 */
public class EvaluationResultJsonSerializer extends JsonSerializer<EvaluationResult> {

    @Override
    public void serialize(EvaluationResult er, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();

        // snapshot
        gen.writeFieldName("snapshot");
        serializers.findValueSerializer(er.snapshot().getClass())
                .serialize(er.snapshot(), gen, serializers);

        // requestedNodePath
        gen.writeStringField("requestedNodePath", PathUtils.toUnixString(er.requestedNodePath()));

        // adhocOverride
        gen.writeFieldName("adhocOverride");
        if (er.adhocOverride().isPresent()) {
            serializers.findValueSerializer(er.adhocOverride().get().getClass())
                    .serialize(er.adhocOverride().get(), gen, serializers);
        } else {
            gen.writeNull();
        }

        // results — [{"rid": {...}, "result": {...}}, ...]
        gen.writeArrayFieldStart("results");
        for (Map.Entry<ResourceIdentifier, Result<Object>> entry : er.results().entrySet()) {
            gen.writeStartObject();
            gen.writeFieldName("rid");
            serializers.findValueSerializer(ResourceIdentifier.class)
                    .serialize(entry.getKey(), gen, serializers);
            gen.writeFieldName("result");
            serializers.findValueSerializer(entry.getValue().getClass())
                    .serialize(entry.getValue(), gen, serializers);
            gen.writeEndObject();
        }
        gen.writeEndArray();

        // nodeEvaluations — JSON object with path string keys
        gen.writeObjectFieldStart("nodeEvaluations");
        for (Map.Entry<Path, NodeEvaluation> entry : er.nodeEvaluationMap().entrySet()) {
            gen.writeFieldName(PathUtils.toUnixString(entry.getKey()));
            serializers.findValueSerializer(NodeEvaluation.class)
                    .serialize(entry.getValue(), gen, serializers);
        }
        gen.writeEndObject();

        // graph — embedded Graph Definition
        gen.writeFieldName("graph");
        serializers.findValueSerializer(er.graph().getClass())
                .serialize(er.graph(), gen, serializers);

        gen.writeEndObject();
    }
}
