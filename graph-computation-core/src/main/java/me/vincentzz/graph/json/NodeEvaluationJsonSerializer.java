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
 * Serializes Maps with ResourceIdentifier keys as entry lists for better JSON compatibility.
 */
public class NodeEvaluationJsonSerializer extends JsonSerializer<NodeEvaluation> {
    
    @Override
    public void serialize(NodeEvaluation nodeEvaluation, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        // Serialize inputs as entry list
        gen.writeFieldName("inputs");
        gen.writeStartArray();
        for (Map.Entry<ResourceIdentifier, InputResult> entry : nodeEvaluation.inputs().entrySet()) {
            gen.writeStartObject();
            
            gen.writeFieldName("key");
            serializers.findValueSerializer(ResourceIdentifier.class).serialize(entry.getKey(), gen, serializers);
            
            gen.writeFieldName("value");
            // Use our custom serializer directly instead of findValueSerializer to avoid Jackson record handling
            InputResultJsonSerializer inputResultSerializer = new InputResultJsonSerializer();
            inputResultSerializer.serialize(entry.getValue(), gen, serializers);
            
            gen.writeEndObject();
        }
        gen.writeEndArray();
        
        // Serialize outputs as entry list
        gen.writeFieldName("outputs");
        gen.writeStartArray();
        for (Map.Entry<ResourceIdentifier, OutputResult> entry : nodeEvaluation.outputs().entrySet()) {
            gen.writeStartObject();
            
            gen.writeFieldName("key");
            serializers.findValueSerializer(ResourceIdentifier.class).serialize(entry.getKey(), gen, serializers);
            
            gen.writeFieldName("value");
            // Use our custom serializer directly instead of findValueSerializer to avoid Jackson record handling
            OutputResultJsonSerializer outputResultSerializer = new OutputResultJsonSerializer();
            outputResultSerializer.serialize(entry.getValue(), gen, serializers);
            
            gen.writeEndObject();
        }
        gen.writeEndArray();
        
        gen.writeEndObject();
    }
}
