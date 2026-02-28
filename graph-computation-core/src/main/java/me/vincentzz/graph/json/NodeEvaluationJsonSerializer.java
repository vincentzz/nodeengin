package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import me.vincentzz.graph.model.NodeEvaluation;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.model.input.InputResult;
import me.vincentzz.graph.model.output.OutputResult;

import java.io.IOException;
import java.util.Map;

/**
 * JSON serializer for NodeEvaluation.
 * Produces flattened input/output arrays:
 * {
 *   "inputs": [{"rid": {...}, "sourceType": "...", "directInput": null, "result": {...}}, ...],
 *   "outputs": [{"rid": {...}, "resultType": "...", "result": {...}}, ...]
 * }
 */
public class NodeEvaluationJsonSerializer extends JsonSerializer<NodeEvaluation> {

    @Override
    public void serialize(NodeEvaluation eval, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();

        // Flattened inputs
        gen.writeArrayFieldStart("inputs");
        for (Map.Entry<ResourceIdentifier, InputResult> entry : eval.inputs().entrySet()) {
            gen.writeStartObject();

            gen.writeFieldName("rid");
            serializers.findValueSerializer(ResourceIdentifier.class)
                    .serialize(entry.getKey(), gen, serializers);

            gen.writeStringField("sourceType", entry.getValue().inputContext().sourceType().name());

            if (entry.getValue().inputContext().isDirectInput().isPresent()) {
                gen.writeBooleanField("directInput", entry.getValue().inputContext().isDirectInput().get());
            } else {
                gen.writeNullField("directInput");
            }

            gen.writeFieldName("result");
            serializers.findValueSerializer(entry.getValue().value().getClass())
                    .serialize(entry.getValue().value(), gen, serializers);

            gen.writeEndObject();
        }
        gen.writeEndArray();

        // Flattened outputs
        gen.writeArrayFieldStart("outputs");
        for (Map.Entry<ResourceIdentifier, OutputResult> entry : eval.outputs().entrySet()) {
            gen.writeStartObject();

            gen.writeFieldName("rid");
            serializers.findValueSerializer(ResourceIdentifier.class)
                    .serialize(entry.getKey(), gen, serializers);

            gen.writeStringField("resultType", entry.getValue().outputContext().resultType().name());

            gen.writeFieldName("result");
            serializers.findValueSerializer(entry.getValue().value().getClass())
                    .serialize(entry.getValue().value(), gen, serializers);

            gen.writeEndObject();
        }
        gen.writeEndArray();

        gen.writeEndObject();
    }
}
