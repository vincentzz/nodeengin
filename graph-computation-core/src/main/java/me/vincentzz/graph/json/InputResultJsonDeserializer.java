package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import me.vincentzz.graph.model.input.InputContext;
import me.vincentzz.graph.model.input.InputResult;
import me.vincentzz.lang.Result.Result;

import java.io.IOException;

/**
 * JSON deserializer for InputResult.
 */
public class InputResultJsonDeserializer extends JsonDeserializer<InputResult> {
    
    @Override
    public InputResult deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        JsonNode node = p.getCodec().readTree(p);

        // Parse inputContext manually without using Jackson's traversal to avoid record handling
        JsonNode inputContextNode = node.get("inputContext");
        JsonNode sourceTypeNode = inputContextNode.get("sourceType");
        String sourceTypeStr = sourceTypeNode.asText();
        me.vincentzz.graph.model.input.InputSourceType sourceType = me.vincentzz.graph.model.input.InputSourceType.valueOf(sourceTypeStr);

        // Handle isDirectInput field (Optional<Boolean>)
        java.util.Optional<Boolean> isDirectInput = java.util.Optional.empty();
        JsonNode isDirectInputNode = inputContextNode.get("isDirectInput");
        if (isDirectInputNode != null && !isDirectInputNode.isNull()) {
            isDirectInput = java.util.Optional.of(isDirectInputNode.asBoolean());
        }

        InputContext inputContext = new InputContext(sourceType, isDirectInput);

        // Deserialize value using custom Result deserializer directly
        JsonNode valueNode = node.get("value");
        JsonParser valueParser = valueNode.traverse(p.getCodec());
        valueParser.nextToken();
        ResultJsonDeserializer resultDeserializer = new ResultJsonDeserializer();
        @SuppressWarnings("unchecked")
        Result<Object> value = resultDeserializer.deserialize(valueParser, ctxt);

        return new InputResult(inputContext, value);
    }
}
