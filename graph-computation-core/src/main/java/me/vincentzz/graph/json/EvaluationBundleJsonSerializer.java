package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import me.vincentzz.graph.model.EvaluationBundle;
import me.vincentzz.graph.model.EvaluationResult;
import me.vincentzz.graph.node.CalculationNode;

import java.io.IOException;

/**
 * JSON serializer for EvaluationBundle.
 * Wraps graph and evaluationResult together.
 */
public class EvaluationBundleJsonSerializer extends JsonSerializer<EvaluationBundle> {

    @Override
    public void serialize(EvaluationBundle bundle, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();

        // graph
        gen.writeFieldName("graph");
        serializers.findValueSerializer(CalculationNode.class)
                .serialize(bundle.graph(), gen, serializers);

        // evaluationResult
        gen.writeFieldName("evaluationResult");
        serializers.findValueSerializer(EvaluationResult.class)
                .serialize(bundle.evaluationResult(), gen, serializers);

        gen.writeEndObject();
    }
}
