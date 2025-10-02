package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import me.vincentzz.graph.model.output.OutputContext;
import me.vincentzz.graph.model.output.OutputResult;
import me.vincentzz.lang.Result.Result;

import java.io.IOException;

/**
 * JSON deserializer for OutputResult.
 */
public class OutputResultJsonDeserializer extends JsonDeserializer<OutputResult> {
    
    @Override
    public OutputResult deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        System.err.println("DEBUG OUTPUT: *** OutputResultJsonDeserializer.deserialize called! ***");
        System.err.println("DEBUG OUTPUT: *** THIS SHOULD APPEAR IF CUSTOM DESERIALIZER IS USED ***");
        JsonNode node = p.getCodec().readTree(p);
        
        // Parse outputContext manually without using Jackson's traversal to avoid record handling
        JsonNode outputContextNode = node.get("outputContext");
        System.err.println("DEBUG OUTPUT: outputContextNode = " + outputContextNode);
        JsonNode resultTypeNode = outputContextNode.get("resultType");
        String resultTypeStr = resultTypeNode.asText();
        System.err.println("DEBUG OUTPUT: resultTypeStr = " + resultTypeStr);
        me.vincentzz.graph.model.output.OutputValueType resultType = me.vincentzz.graph.model.output.OutputValueType.valueOf(resultTypeStr);
        System.err.println("DEBUG OUTPUT: About to create OutputContext manually");
        OutputContext outputContext = new OutputContext(resultType);
        System.err.println("DEBUG OUTPUT: Successfully created OutputContext: " + outputContext);
        
        // Deserialize value using custom Result deserializer directly
        JsonNode valueNode = node.get("value");
        JsonParser valueParser = valueNode.traverse(p.getCodec());
        valueParser.nextToken();
        System.err.println("DEBUG OUTPUT: About to deserialize Result value using custom deserializer");
        ResultJsonDeserializer resultDeserializer = new ResultJsonDeserializer();
        @SuppressWarnings("unchecked")
        Result<Object> value = resultDeserializer.deserialize(valueParser, ctxt);
        System.err.println("DEBUG OUTPUT: Successfully deserialized Result value");
        
        return new OutputResult(outputContext, value);
    }
}
