package me.vincentzz.graph.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import me.vincentzz.graph.model.AdhocOverride;
import me.vincentzz.graph.model.EvaluationBundle;
import me.vincentzz.graph.model.EvaluationResult;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.lang.Result.Failure;
import me.vincentzz.lang.Result.Result;
import me.vincentzz.lang.Result.Success;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON utility for CalculationNodes and EvaluationResults.
 * Provides serialization/deserialization using the new format.
 */
public class ConstructionalJsonUtil {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    @SuppressWarnings("unchecked")
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        SimpleModule module = new SimpleModule("GraphJsonModule");

        // CalculationNode (graph definition)
        module.addSerializer(CalculationNode.class, new ConstructionalJsonSerializer());
        module.addDeserializer(CalculationNode.class, new ConstructionalJsonDeserializer());

        // ResourceIdentifier
        module.addSerializer(ResourceIdentifier.class, new ResourceIdentifierJsonSerializer());
        module.addDeserializer(ResourceIdentifier.class, new ResourceIdentifierJsonDeserializer());

        // Result
        Class<Result<?>> resultClass = (Class<Result<?>>) (Class<?>) Result.class;
        module.addSerializer(resultClass, new ResultJsonSerializer());
        module.addDeserializer(resultClass, new ResultJsonDeserializer());

        // ConnectionPoint
        module.addSerializer(me.vincentzz.graph.node.ConnectionPoint.class, new ConnectionPointJsonSerializer());
        module.addDeserializer(me.vincentzz.graph.node.ConnectionPoint.class, new ConnectionPointJsonDeserializer());

        // Flywire
        module.addSerializer(me.vincentzz.graph.node.Flywire.class, new FlywireJsonSerializer());
        module.addDeserializer(me.vincentzz.graph.node.Flywire.class, new FlywireJsonDeserializer());

        // AdhocOverride
        module.addSerializer(AdhocOverride.class, new AdhocOverrideJsonSerializer());
        module.addDeserializer(AdhocOverride.class, new AdhocOverrideJsonDeserializer());

        // EvaluationResult
        module.addSerializer(EvaluationResult.class, new EvaluationResultJsonSerializer());
        module.addDeserializer(EvaluationResult.class, new EvaluationResultJsonDeserializer());

        // EvaluationBundle
        module.addSerializer(EvaluationBundle.class, new EvaluationBundleJsonSerializer());
        module.addDeserializer(EvaluationBundle.class, new EvaluationBundleJsonDeserializer());

        // Snapshot
        module.addSerializer(me.vincentzz.graph.model.Snapshot.class, new SnapshotJsonSerializer());
        module.addDeserializer(me.vincentzz.graph.model.Snapshot.class, new SnapshotJsonDeserializer());

        // NodeEvaluation
        module.addSerializer(me.vincentzz.graph.model.NodeEvaluation.class, new NodeEvaluationJsonSerializer());
        module.addDeserializer(me.vincentzz.graph.model.NodeEvaluation.class, new NodeEvaluationJsonDeserializer());

        mapper.registerModule(module);
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    // ===== GRAPH DEFINITION SERIALIZATION =====

    public static Result<String> toJson(CalculationNode node) {
        try {
            return Success.of(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node));
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to serialize CalculationNode to JSON", e));
        }
    }

    public static Result<String> toJsonCompact(CalculationNode node) {
        try {
            return Success.of(OBJECT_MAPPER.writeValueAsString(node));
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to serialize CalculationNode to compact JSON", e));
        }
    }

    public static Result<Void> toJsonFile(CalculationNode node, File file) {
        try {
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, node);
            return Success.of(null);
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to write CalculationNode to file", e));
        }
    }

    public static Result<Void> toJsonFile(CalculationNode node, Path path) {
        try (OutputStream os = Files.newOutputStream(path)) {
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(os, node);
            return Success.of(null);
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to write CalculationNode to path", e));
        }
    }

    // ===== GRAPH DEFINITION DESERIALIZATION =====

    public static Result<CalculationNode> fromJson(String json) {
        try {
            return Success.of(OBJECT_MAPPER.readValue(json, CalculationNode.class));
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to deserialize JSON to CalculationNode", e));
        }
    }

    public static Result<CalculationNode> fromJsonFile(File file) {
        try {
            return Success.of(OBJECT_MAPPER.readValue(file, CalculationNode.class));
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to read CalculationNode from file", e));
        }
    }

    public static Result<CalculationNode> fromJsonFile(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            return Success.of(OBJECT_MAPPER.readValue(is, CalculationNode.class));
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to read CalculationNode from path", e));
        }
    }

    // ===== EVALUATION RESULT SERIALIZATION =====

    public static Result<String> toJsonEvaluationResult(EvaluationResult evaluationResult) {
        try {
            return Success.of(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(evaluationResult));
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to serialize EvaluationResult to JSON", e));
        }
    }

    public static Result<String> toJsonCompact(EvaluationResult evaluationResult) {
        try {
            return Success.of(OBJECT_MAPPER.writeValueAsString(evaluationResult));
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to serialize EvaluationResult to compact JSON", e));
        }
    }

    public static Result<Void> toJsonFile(EvaluationResult evaluationResult, File file) {
        try {
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, evaluationResult);
            return Success.of(null);
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to write EvaluationResult to file", e));
        }
    }

    public static Result<Void> toJsonFile(EvaluationResult evaluationResult, Path path) {
        try (OutputStream os = Files.newOutputStream(path)) {
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(os, evaluationResult);
            return Success.of(null);
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to write EvaluationResult to path", e));
        }
    }

    // ===== EVALUATION RESULT DESERIALIZATION =====

    public static Result<EvaluationResult> fromJsonEvaluationResult(String json) {
        try {
            return Success.of(OBJECT_MAPPER.readValue(json, EvaluationResult.class));
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to deserialize JSON to EvaluationResult", e));
        }
    }

    public static Result<EvaluationResult> fromJsonEvaluationResultFile(File file) {
        try {
            return Success.of(OBJECT_MAPPER.readValue(file, EvaluationResult.class));
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to read EvaluationResult from file", e));
        }
    }

    public static Result<EvaluationResult> fromJsonEvaluationResultFile(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            return Success.of(OBJECT_MAPPER.readValue(is, EvaluationResult.class));
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to read EvaluationResult from path", e));
        }
    }

    // ===== EVALUATION BUNDLE SERIALIZATION =====

    public static Result<String> toJsonEvaluationBundle(EvaluationBundle bundle) {
        try {
            return Success.of(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(bundle));
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to serialize EvaluationBundle to JSON", e));
        }
    }

    public static Result<Void> toJsonFile(EvaluationBundle bundle, File file) {
        try {
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, bundle);
            return Success.of(null);
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to write EvaluationBundle to file", e));
        }
    }

    public static Result<Void> toJsonFile(EvaluationBundle bundle, Path path) {
        try (OutputStream os = Files.newOutputStream(path)) {
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(os, bundle);
            return Success.of(null);
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to write EvaluationBundle to path", e));
        }
    }

    // ===== EVALUATION BUNDLE DESERIALIZATION =====

    public static Result<EvaluationBundle> fromJsonEvaluationBundle(String json) {
        try {
            return Success.of(OBJECT_MAPPER.readValue(json, EvaluationBundle.class));
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to deserialize JSON to EvaluationBundle", e));
        }
    }

    public static Result<EvaluationBundle> fromJsonEvaluationBundleFile(File file) {
        try {
            return Success.of(OBJECT_MAPPER.readValue(file, EvaluationBundle.class));
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to read EvaluationBundle from file", e));
        }
    }

    public static Result<EvaluationBundle> fromJsonEvaluationBundleFile(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            return Success.of(OBJECT_MAPPER.readValue(is, EvaluationBundle.class));
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to read EvaluationBundle from path", e));
        }
    }

    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
}
