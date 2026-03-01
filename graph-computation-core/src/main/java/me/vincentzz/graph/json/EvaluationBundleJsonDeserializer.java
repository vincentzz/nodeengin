package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import me.vincentzz.graph.model.EvaluationBundle;
import me.vincentzz.graph.model.EvaluationResult;
import me.vincentzz.graph.node.CalculationNode;

import java.io.IOException;

/**
 * JSON deserializer for EvaluationBundle.
 * Parses graph and evaluationResult from a wrapper object.
 */
public class EvaluationBundleJsonDeserializer extends JsonDeserializer<EvaluationBundle> {

    @Override
    public EvaluationBundle deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);

        // graph
        JsonNode graphNode = node.get("graph");
        JsonParser graphParser = graphNode.traverse(p.getCodec());
        graphParser.nextToken();
        CalculationNode graph = ctxt.readValue(graphParser, CalculationNode.class);

        // evaluationResult
        JsonNode evalNode = node.get("evaluationResult");
        JsonParser evalParser = evalNode.traverse(p.getCodec());
        evalParser.nextToken();
        EvaluationResult evaluationResult = ctxt.readValue(evalParser, EvaluationResult.class);

        return new EvaluationBundle(graph, evaluationResult);
    }
}
