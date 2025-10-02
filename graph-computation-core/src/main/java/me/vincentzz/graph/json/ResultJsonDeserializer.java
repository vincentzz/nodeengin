package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import me.vincentzz.lang.Result.Result;
import me.vincentzz.lang.Result.Success;
import me.vincentzz.lang.Result.Failure;

import java.io.IOException;

/**
 * JSON deserializer for Result<T> types.
 * Deserializes Success and Failure variants from type information.
 */
public class ResultJsonDeserializer extends JsonDeserializer<Result<Object>> {
    
    @Override
    public Result<Object> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        JsonNode node = p.getCodec().readTree(p);
        
        // Handle both old format (success/failure fields) and new format (type field)
        if (node.has("success")) {
            JsonNode successNode = node.get("success");
            Object value = deserializeValue(successNode, p, ctxt);
            return Success.of(value);
        } else if (node.has("failure")) {
            JsonNode failureNode = node.get("failure");
            String errorMessage = failureNode.asText();
            Exception exception = new RuntimeException(errorMessage);
            return Failure.of(exception);
        } else if (node.has("type")) {
            String type = node.get("type").asText();
            if ("Success".equals(type)) {
                JsonNode dataNode = node.get("data");
                Object value = deserializeValue(dataNode, p, ctxt);
                return Success.of(value);
            } else if ("Failure".equals(type)) {
                String errorMessage = node.get("error").asText();
                Exception exception = new RuntimeException(errorMessage);
                return Failure.of(exception);
            } else {
                throw new IllegalArgumentException("Invalid Result type: " + type + " (must be 'Success' or 'Failure')");
            }
        } else {
            throw new IllegalArgumentException("Invalid Result JSON: must have either 'success'/'failure' fields or 'type' field");
        }
    }
    
    @SuppressWarnings("unchecked")
    private Object deserializeValue(JsonNode node, JsonParser p, DeserializationContext ctxt) throws IOException {
        if (node == null || node.isNull()) {
            return null;
        } else if (node.has("type") && node.has("data")) {
            // This is a typed object - reconstruct it properly
            String typeName = node.get("type").asText();
            JsonNode dataNode = node.get("data");
            
            try {
                // Try to reconstruct the typed object using reflection
                return reconstructTypedObject(typeName, dataNode, p, ctxt);
            } catch (Exception e) {
                System.err.println("DEBUG RESULT: Failed to reconstruct typed object " + typeName + ": " + e.getMessage());
                // Fallback to generic object
                try {
                    JsonParser nodeParser = node.traverse(p.getCodec());
                    nodeParser.nextToken();
                    return ctxt.readValue(nodeParser, Object.class);
                } catch (Exception e2) {
                    return node.toString();
                }
            }
        } else {
            // Not a typed object - use standard deserialization
            try {
                JsonParser nodeParser = node.traverse(p.getCodec());
                nodeParser.nextToken();
                return ctxt.readValue(nodeParser, Object.class);
            } catch (Exception e) {
                // Fallback: return as string representation
                return node.toString();
            }
        }
    }
    
    private Object reconstructTypedObject(String typeName, JsonNode dataNode, JsonParser p, DeserializationContext ctxt) throws Exception {
        // Try to find the class for this type name
        Class<?> clazz = findClassForTypeName(typeName);
        if (clazz == null) {
            throw new IllegalArgumentException("Unknown type: " + typeName);
        }
        
        System.err.println("DEBUG RESULT: Reconstructing typed object: " + typeName + " -> " + clazz);
        
        // Check if this is a Java record
        try {
            java.lang.reflect.RecordComponent[] recordComponents = clazz.getRecordComponents();
            if (recordComponents != null && recordComponents.length > 0) {
                // This is a Java record - reconstruct using record components
                Object[] args = new Object[recordComponents.length];
                for (int i = 0; i < recordComponents.length; i++) {
                    String fieldName = recordComponents[i].getName();
                    Class<?> fieldType = recordComponents[i].getType();
                    JsonNode fieldNode = dataNode.get(fieldName);
                    
                    if (fieldNode != null && !fieldNode.isNull()) {
                        args[i] = deserializeFieldValue(fieldNode, fieldType, p, ctxt);
                    } else {
                        args[i] = getDefaultValue(fieldType);
                    }
                }
                
                // Find the canonical constructor and create the record
                java.lang.reflect.Constructor<?> constructor = clazz.getDeclaredConstructors()[0];
                return constructor.newInstance(args);
            }
        } catch (Exception e) {
            System.err.println("DEBUG RESULT: Record reconstruction failed: " + e.getMessage());
        }
        
        // Not a record - try standard Jackson deserialization with the specific class
        try {
            JsonParser dataParser = dataNode.traverse(p.getCodec());
            dataParser.nextToken();
            return ctxt.readValue(dataParser, clazz);
        } catch (Exception e) {
            System.err.println("DEBUG RESULT: Standard deserialization failed: " + e.getMessage());
            throw e;
        }
    }
    
    private Object deserializeFieldValue(JsonNode fieldNode, Class<?> fieldType, JsonParser p, DeserializationContext ctxt) throws Exception {
        if (fieldNode.isNull()) {
            return null;
        } else if (fieldType == String.class) {
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
        } else if (fieldType == java.time.LocalDateTime.class) {
            return java.time.LocalDateTime.parse(fieldNode.asText());
        } else if (fieldType == java.time.LocalDate.class) {
            return java.time.LocalDate.parse(fieldNode.asText());
        } else if (fieldType == java.util.UUID.class) {
            return java.util.UUID.fromString(fieldNode.asText());
        } else if (fieldType.isEnum()) {
            return Enum.valueOf((Class<Enum>) fieldType, fieldNode.asText());
        } else {
            // For complex types, try to deserialize recursively
            JsonParser fieldParser = fieldNode.traverse(p.getCodec());
            fieldParser.nextToken();
            return ctxt.readValue(fieldParser, fieldType);
        }
    }
    
    private Object getDefaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        if (type == double.class) return 0.0;
        if (type == char.class) return '\0';
        return null; // For reference types
    }
    
    private Class<?> findClassForTypeName(String typeName) {
        // Try common attribute types first
        try {
            switch (typeName) {
                case "Ask": return Class.forName("me.vincentzz.falcon.attribute.Ask");
                case "Bid": return Class.forName("me.vincentzz.falcon.attribute.Bid");
                case "MidPrice": return Class.forName("me.vincentzz.falcon.attribute.MidPrice");
                case "Spread": return Class.forName("me.vincentzz.falcon.attribute.Spread");
                case "Volume": return Class.forName("me.vincentzz.falcon.attribute.Volume");
                case "Vwap": return Class.forName("me.vincentzz.falcon.attribute.Vwap");
                case "MarkToMarket": return Class.forName("me.vincentzz.falcon.attribute.MarkToMarket");
                // Add more known types as needed
                default:
                    // Try to find in common packages
                    String[] packages = {
                        "me.vincentzz.falcon.attribute.",
                        "me.vincentzz.graph.model.",
                        "me.vincentzz.graph.model.input.",
                        "me.vincentzz.graph.model.output.",
                        "java.lang.",
                        "java.util.",
                        "java.time."
                    };
                    
                    for (String pkg : packages) {
                        try {
                            return Class.forName(pkg + typeName);
                        } catch (ClassNotFoundException e) {
                            // Try next package
                        }
                    }
                    
                    return null;
            }
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
