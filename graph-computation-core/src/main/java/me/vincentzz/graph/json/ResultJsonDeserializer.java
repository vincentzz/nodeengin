package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import me.vincentzz.lang.Result.Failure;
import me.vincentzz.lang.Result.Result;
import me.vincentzz.lang.Result.Success;

import java.io.IOException;
import java.lang.reflect.RecordComponent;

/**
 * JSON deserializer for Result&lt;T&gt;.
 * Consumes: {"success": {"type": "Ask", "data": {...}}} or {"failure": "error message"}
 */
public class ResultJsonDeserializer extends JsonDeserializer<Result<Object>> {

    @Override
    public Result<Object> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        return deserializeFromNode(node);
    }

    static Result<Object> deserializeFromNode(JsonNode node) {
        if (node.has("success")) {
            JsonNode successNode = node.get("success");
            Object value = deserializeTypedValue(successNode);
            return Success.of(value);
        } else if (node.has("failure")) {
            String errorMessage = node.get("failure").asText();
            return Failure.of(new RuntimeException(errorMessage));
        } else {
            throw new IllegalArgumentException("Invalid Result JSON: must have 'success' or 'failure' field");
        }
    }

    /**
     * Deserialize a typed value. May be:
     * - A primitive (string, number, boolean)
     * - A typed object: {"type": "Ask", "data": {"price": ..., "size": ..., "time": ...}}
     * - null
     */
    static Object deserializeTypedValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isFloatingPointNumber()) {
            return node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isObject() && node.has("type") && node.has("data")) {
            return reconstructTypedObject(node.get("type").asText(), node.get("data"));
        }
        // Fallback
        return node.toString();
    }

    private static Object reconstructTypedObject(String typeName, JsonNode dataNode) {
        Class<?> clazz = NodeTypeRegistry.getValueClass(typeName);
        if (clazz == null) {
            throw new IllegalArgumentException("Unknown typed value: " + typeName);
        }

        if (!clazz.isRecord()) {
            throw new IllegalArgumentException("Typed value must be a record: " + typeName);
        }

        RecordComponent[] components = clazz.getRecordComponents();
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            String fieldName = components[i].getName();
            Class<?> fieldType = components[i].getType();
            JsonNode fieldNode = dataNode.get(fieldName);
            args[i] = deserializeFieldValue(fieldNode, fieldType);
        }

        try {
            var constructor = clazz.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to construct " + typeName, e);
        }
    }

    static Object deserializeFieldValue(JsonNode fieldNode, Class<?> fieldType) {
        if (fieldNode == null || fieldNode.isNull()) {
            return getDefaultValue(fieldType);
        }
        if (fieldType == String.class) {
            return fieldNode.asText();
        } else if (fieldType == int.class || fieldType == Integer.class) {
            return fieldNode.asInt();
        } else if (fieldType == long.class || fieldType == Long.class) {
            return fieldNode.asLong();
        } else if (fieldType == double.class || fieldType == Double.class) {
            return fieldNode.asDouble();
        } else if (fieldType == float.class || fieldType == Float.class) {
            return (float) fieldNode.asDouble();
        } else if (fieldType == boolean.class || fieldType == Boolean.class) {
            return fieldNode.asBoolean();
        } else if (fieldType == java.math.BigDecimal.class) {
            return new java.math.BigDecimal(fieldNode.asText());
        } else if (fieldType == java.time.Instant.class) {
            return java.time.Instant.parse(fieldNode.asText());
        } else if (fieldType == Class.class) {
            // Handle Class<?> fields (e.g., attribute in FalconResourceId)
            return NodeTypeRegistry.resolveClass(fieldNode.asText());
        } else {
            return fieldNode.asText();
        }
    }

    private static Object getDefaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        if (type == double.class) return 0.0;
        return null;
    }
}
