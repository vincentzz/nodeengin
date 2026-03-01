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
 * Produces the schema with:
 * - request as a nested object (requestedResourceIds, snapshot, requestedNodePath, adhocOverride)
 * - results as [{"rid": {...}, "result": {...}}, ...]
 * - evaluations as a JSON object (path keys → values)
 */
public class EvaluationResultJsonSerializer extends JsonSerializer<EvaluationResult> {

    @Override
    public void serialize(EvaluationResult er, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();

        // request
        gen.writeObjectFieldStart("request");
        {
            // rids
            gen.writeArrayFieldStart("rids");
            for (ResourceIdentifier rid : er.request().rids()) {
                serializers.findValueSerializer(ResourceIdentifier.class)
                        .serialize(rid, gen, serializers);
            }
            gen.writeEndArray();

            // snapshot
            gen.writeFieldName("snapshot");
            serializers.findValueSerializer(er.request().snapshot().getClass())
                    .serialize(er.request().snapshot(), gen, serializers);

            // path
            gen.writeStringField("path", PathUtils.toUnixString(er.request().path()));

            // adhocOverride
            gen.writeFieldName("override");
            if (er.request().override().isPresent()) {
                serializers.findValueSerializer(er.request().override().get().getClass())
                        .serialize(er.request().override().get(), gen, serializers);
            } else {
                gen.writeNull();
            }
        }
        gen.writeEndObject();

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

        // evaluations — JSON object with path string keys
        gen.writeObjectFieldStart("evaluations");
        for (Map.Entry<Path, NodeEvaluation> entry : er.evaluations().entrySet()) {
            gen.writeFieldName(PathUtils.toUnixString(entry.getKey()));
            serializers.findValueSerializer(NodeEvaluation.class)
                    .serialize(entry.getValue(), gen, serializers);
        }
        gen.writeEndObject();

        gen.writeEndObject();
    }
}
