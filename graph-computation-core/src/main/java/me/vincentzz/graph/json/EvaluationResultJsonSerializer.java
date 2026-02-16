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
 * Serializes Maps with object keys as entry lists for better JSON compatibility.
 */
public class EvaluationResultJsonSerializer extends JsonSerializer<EvaluationResult> {
    
    @Override
    public void serialize(EvaluationResult evaluationResult, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        // Serialize snapshot using registered Snapshot serializer
        gen.writeFieldName("snapshot");
        serializers.findValueSerializer(evaluationResult.snapshot().getClass())
            .serialize(evaluationResult.snapshot(), gen, serializers);
        
        // Serialize requestedNodePath as string
        gen.writeStringField("requestedNodePath", PathUtils.toUnixString(evaluationResult.requestedNodePath()));
        
        // Serialize adhocOverride using registered serializer
        gen.writeFieldName("adhocOverride");
        if (evaluationResult.adhocOverride().isPresent()) {
            serializers.findValueSerializer(evaluationResult.adhocOverride().get().getClass())
                .serialize(evaluationResult.adhocOverride().get(), gen, serializers);
        } else {
            gen.writeNull();
        }
        
        // Serialize results as entry list
        gen.writeFieldName("results");
        gen.writeStartArray();
        for (Map.Entry<ResourceIdentifier, Result<Object>> entry : evaluationResult.results().entrySet()) {
            gen.writeStartObject();
            
            gen.writeFieldName("key");
            serializers.findValueSerializer(ResourceIdentifier.class).serialize(entry.getKey(), gen, serializers);
            
            gen.writeFieldName("value");
            serializers.findValueSerializer(Result.class).serialize(entry.getValue(), gen, serializers);
            
            gen.writeEndObject();
        }
        gen.writeEndArray();
        
        // Serialize nodeEvaluationMap as nested entry list
        gen.writeFieldName("nodeEvaluationMap");
        gen.writeStartArray();
        for (Map.Entry<Path, NodeEvaluation> outerEntry : evaluationResult.nodeEvaluationMap().entrySet()) {
            gen.writeStartObject();
            
            // Outer key is Path
            gen.writeStringField("key", PathUtils.toUnixString(outerEntry.getKey()));
            
            // Outer value is NodeEvaluation - use our custom serializer directly
            gen.writeFieldName("value");
            NodeEvaluationJsonSerializer nodeEvaluationSerializer = new NodeEvaluationJsonSerializer();
            nodeEvaluationSerializer.serialize(outerEntry.getValue(), gen, serializers);
            
            gen.writeEndObject();
        }
        gen.writeEndArray();
        
        // Serialize graph using registered CalculationNode serializer
        gen.writeFieldName("graph");
        serializers.findValueSerializer(evaluationResult.graph().getClass())
            .serialize(evaluationResult.graph(), gen, serializers);
        
        gen.writeEndObject();
    }
}
