package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import me.vincentzz.graph.model.NodeEvaluation;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.model.input.InputContext;
import me.vincentzz.graph.model.input.InputResult;
import me.vincentzz.graph.model.input.InputSourceType;
import me.vincentzz.graph.model.output.OutputContext;
import me.vincentzz.graph.model.output.OutputResult;
import me.vincentzz.graph.model.output.OutputValueType;
import me.vincentzz.lang.Result.Result;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * JSON deserializer for NodeEvaluation.
 * Consumes the flattened format:
 * {
 *   "inputs": [{"rid": {...}, "sourceType": "...", "directInput": null, "result": {...}}, ...],
 *   "outputs": [{"rid": {...}, "resultType": "...", "result": {...}}, ...]
 * }
 */
public class NodeEvaluationJsonDeserializer extends JsonDeserializer<NodeEvaluation> {

    @Override
    public NodeEvaluation deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        return deserializeFromNode(node);
    }

    static NodeEvaluation deserializeFromNode(JsonNode node) {
        Map<ResourceIdentifier, InputResult> inputs = new HashMap<>();
        JsonNode inputsArray = node.get("inputs");
        if (inputsArray != null && inputsArray.isArray()) {
            for (JsonNode inputNode : inputsArray) {
                ResourceIdentifier rid = ResourceIdentifierJsonDeserializer.deserializeFromNode(inputNode.get("rid"));

                InputSourceType sourceType = InputSourceType.valueOf(inputNode.get("sourceType").asText());

                Optional<Boolean> directInput = Optional.empty();
                JsonNode directInputNode = inputNode.get("directInput");
                if (directInputNode != null && !directInputNode.isNull()) {
                    directInput = Optional.of(directInputNode.asBoolean());
                }

                InputContext inputContext = new InputContext(sourceType, directInput);
                Result<Object> result = ResultJsonDeserializer.deserializeFromNode(inputNode.get("result"));

                inputs.put(rid, new InputResult(inputContext, result));
            }
        }

        Map<ResourceIdentifier, OutputResult> outputs = new HashMap<>();
        JsonNode outputsArray = node.get("outputs");
        if (outputsArray != null && outputsArray.isArray()) {
            for (JsonNode outputNode : outputsArray) {
                ResourceIdentifier rid = ResourceIdentifierJsonDeserializer.deserializeFromNode(outputNode.get("rid"));

                OutputValueType resultType = OutputValueType.valueOf(outputNode.get("resultType").asText());
                OutputContext outputContext = new OutputContext(resultType);
                Result<Object> result = ResultJsonDeserializer.deserializeFromNode(outputNode.get("result"));

                outputs.put(rid, new OutputResult(outputContext, result));
            }
        }

        return new NodeEvaluation(inputs, outputs);
    }
}
