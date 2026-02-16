package me.vincentzz.graph.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import me.vincentzz.graph.model.AdhocOverride;
import me.vincentzz.graph.model.EvaluationResult;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.graph.node.NodeGroup;
import me.vincentzz.lang.Result.Result;
import me.vincentzz.lang.Result.Success;
import me.vincentzz.lang.Result.Failure;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Comprehensive constructional JSON utility for CalculationNodes.
 * Provides serialization to constructional format and reconstruction via NodeTypeRegistry.
 */
public class ConstructionalJsonUtil {
    
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();
    
    /**
     * Create and configure the ObjectMapper for JSON serialization/deserialization.
     * 
     * @return Configured ObjectMapper
     */
    private static ObjectMapper createObjectMapper() {
        // Ensure required node types are registered before creating ObjectMapper
        registerCommonNodeTypes();
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        // Configure Jackson - disable auto-detection to force use of custom serializers
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // Disable auto-detection to force use of custom serializers
        mapper.configure(com.fasterxml.jackson.databind.MapperFeature.AUTO_DETECT_CREATORS, false);
        mapper.configure(com.fasterxml.jackson.databind.MapperFeature.AUTO_DETECT_FIELDS, false);
        mapper.configure(com.fasterxml.jackson.databind.MapperFeature.AUTO_DETECT_GETTERS, false);
        mapper.configure(com.fasterxml.jackson.databind.MapperFeature.AUTO_DETECT_IS_GETTERS, false);
        mapper.configure(com.fasterxml.jackson.databind.MapperFeature.AUTO_DETECT_SETTERS, false);
        
        // Disable record serialization completely to force use of custom serializers
        try {
            // This should disable the built-in record serialization
            mapper.configure(com.fasterxml.jackson.databind.MapperFeature.USE_ANNOTATIONS, false);
        } catch (Exception e) {
            // Could not disable USE_ANNOTATIONS
        }
        
        // Create the deserializer
        ConstructionalJsonDeserializer deserializer = new ConstructionalJsonDeserializer();
        
        // Create and register the module
        SimpleModule module = new SimpleModule("ConstructionalModule");
        module.addSerializer(CalculationNode.class, new ConstructionalJsonSerializer());
        module.addDeserializer(CalculationNode.class, deserializer);
        
        // Add ResourceIdentifier serializer and deserializer to handle nested ResourceIdentifiers properly
        ResourceIdentifierJsonSerializer ridSerializer = new ResourceIdentifierJsonSerializer();
        module.addSerializer(ResourceIdentifier.class, ridSerializer);
        module.addDeserializer(ResourceIdentifier.class, new ResourceIdentifierJsonDeserializer());
        
        // Add Result serializers  
        @SuppressWarnings("unchecked")
        Class<Result<?>> resultClass = (Class<Result<?>>) (Class<?>) Result.class;
        module.addSerializer(resultClass, new ResultJsonSerializer());
        module.addDeserializer(resultClass, new ResultJsonDeserializer());
        
        // Add ConnectionPoint serializers
        module.addSerializer(me.vincentzz.graph.node.ConnectionPoint.class, new ConnectionPointJsonSerializer());
        module.addDeserializer(me.vincentzz.graph.node.ConnectionPoint.class, new ConnectionPointJsonDeserializer());
        
        // Add Flywire serializers
        module.addSerializer(me.vincentzz.graph.node.Flywire.class, new FlywireJsonSerializer());
        module.addDeserializer(me.vincentzz.graph.node.Flywire.class, new FlywireJsonDeserializer());
        
        // Add AdhocOverride serializers
        module.addSerializer(AdhocOverride.class, new AdhocOverrideJsonSerializer());
        module.addDeserializer(AdhocOverride.class, new AdhocOverrideJsonDeserializer());
        
        // Add EvaluationResult serializers
        EvaluationResultJsonSerializer evalResultSerializer = new EvaluationResultJsonSerializer();
        module.addSerializer(EvaluationResult.class, evalResultSerializer);
        module.addDeserializer(EvaluationResult.class, new EvaluationResultJsonDeserializer());
        
        // Add Snapshot serializers
        module.addSerializer(me.vincentzz.graph.model.Snapshot.class, new SnapshotJsonSerializer());
        module.addDeserializer(me.vincentzz.graph.model.Snapshot.class, new SnapshotJsonDeserializer());
        
        // Add NodeEvaluation serializers
        module.addSerializer(me.vincentzz.graph.model.NodeEvaluation.class, new NodeEvaluationJsonSerializer());
        module.addDeserializer(me.vincentzz.graph.model.NodeEvaluation.class, new NodeEvaluationJsonDeserializer());
        
        // Add InputResult serializers
        module.addSerializer(me.vincentzz.graph.model.input.InputResult.class, new InputResultJsonSerializer());
        module.addDeserializer(me.vincentzz.graph.model.input.InputResult.class, new InputResultJsonDeserializer());
        
        // Add OutputResult serializers
        module.addSerializer(me.vincentzz.graph.model.output.OutputResult.class, new OutputResultJsonSerializer());
        module.addDeserializer(me.vincentzz.graph.model.output.OutputResult.class, new OutputResultJsonDeserializer());
        
        // Add InputContext serializers
        module.addSerializer(me.vincentzz.graph.model.input.InputContext.class, new InputContextJsonSerializer());
        module.addDeserializer(me.vincentzz.graph.model.input.InputContext.class, new InputContextJsonDeserializer());
        
        // Add OutputContext serializers  
        module.addSerializer(me.vincentzz.graph.model.output.OutputContext.class, new OutputContextJsonSerializer());
        module.addDeserializer(me.vincentzz.graph.model.output.OutputContext.class, new OutputContextJsonDeserializer());
        
        // Create mixins to force Jackson to use our custom serializers for records (BEFORE registering module)
        try {
            Class<?> falconResourceIdClass = Class.forName("me.vincentzz.falcon.ifo.FalconResourceId");
            if (falconResourceIdClass != null) {
                // Create an empty mixin interface to force Jackson to bypass record serialization
                mapper.addMixIn(falconResourceIdClass, ResourceIdentifierMixin.class);
            }
        } catch (ClassNotFoundException e) {
            // FalconResourceId not available, skip
        }
        
        // Add mixin for EvaluationResult to force custom serializer
        mapper.addMixIn(EvaluationResult.class, ResourceIdentifierMixin.class);

        // Add mixin for Flywire to force custom serializer
        mapper.addMixIn(me.vincentzz.graph.node.Flywire.class, ResourceIdentifierMixin.class);

        // Add mixin for ConnectionPoint to force custom serializer
        mapper.addMixIn(me.vincentzz.graph.node.ConnectionPoint.class, ResourceIdentifierMixin.class);

        // Add mixin for AdhocOverride to force custom serializer
        mapper.addMixIn(AdhocOverride.class, ResourceIdentifierMixin.class);

        // Add mixins for additional record types
        mapper.addMixIn(me.vincentzz.graph.model.output.OutputContext.class, ResourceIdentifierMixin.class);

        mapper.addMixIn(me.vincentzz.graph.model.input.InputContext.class, ResourceIdentifierMixin.class);

        mapper.addMixIn(me.vincentzz.graph.model.NodeEvaluation.class, ResourceIdentifierMixin.class);

        mapper.addMixIn(me.vincentzz.graph.model.input.InputResult.class, ResourceIdentifierMixin.class);

        mapper.addMixIn(me.vincentzz.graph.model.output.OutputResult.class, ResourceIdentifierMixin.class);

        mapper.addMixIn(me.vincentzz.graph.model.Snapshot.class, ResourceIdentifierMixin.class);

        mapper.registerModule(module);
        
        // Additional step: Force Jackson to recognize our custom serializer for EvaluationResult
        try {
            com.fasterxml.jackson.databind.SerializerProvider serializerProvider = mapper.getSerializerProvider();
            mapper.getSerializerFactory().withSerializerModifier(new com.fasterxml.jackson.databind.ser.BeanSerializerModifier() {
                @Override
                public com.fasterxml.jackson.databind.JsonSerializer<?> modifySerializer(
                        com.fasterxml.jackson.databind.SerializationConfig config,
                        com.fasterxml.jackson.databind.BeanDescription beanDesc,
                        com.fasterxml.jackson.databind.JsonSerializer<?> serializer) {
                    if (beanDesc.getBeanClass() == EvaluationResult.class) {
                        return evalResultSerializer;
                    }
                    return serializer;
                }
            });
        } catch (Exception e) {
            // Could not add serializer modifier
        }

        return mapper;
    }
    
    /**
     * Create a dedicated ObjectMapper specifically for EvaluationResult serialization.
     * This bypasses Jackson's record serialization completely.
     */
    private static ObjectMapper createEvaluationResultMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        // Completely disable all auto-detection
        mapper.configure(com.fasterxml.jackson.databind.MapperFeature.AUTO_DETECT_CREATORS, false);
        mapper.configure(com.fasterxml.jackson.databind.MapperFeature.AUTO_DETECT_FIELDS, false);
        mapper.configure(com.fasterxml.jackson.databind.MapperFeature.AUTO_DETECT_GETTERS, false);
        mapper.configure(com.fasterxml.jackson.databind.MapperFeature.AUTO_DETECT_IS_GETTERS, false);
        mapper.configure(com.fasterxml.jackson.databind.MapperFeature.AUTO_DETECT_SETTERS, false);
        mapper.configure(com.fasterxml.jackson.databind.MapperFeature.USE_ANNOTATIONS, false);
        
        // Create module with only the serializers we need
        SimpleModule module = new SimpleModule("EvaluationResultModule");
        
        // Add all necessary serializers
        module.addSerializer(EvaluationResult.class, new EvaluationResultJsonSerializer());
        module.addSerializer(AdhocOverride.class, new AdhocOverrideJsonSerializer());
        module.addSerializer(me.vincentzz.graph.node.ConnectionPoint.class, new ConnectionPointJsonSerializer());
        module.addSerializer(ResourceIdentifier.class, new ResourceIdentifierJsonSerializer());
        
        @SuppressWarnings("unchecked")
        Class<Result<?>> resultClass = (Class<Result<?>>) (Class<?>) Result.class;
        module.addSerializer(resultClass, new ResultJsonSerializer());
        
        // Force Jackson to use our custom serializer by removing any default serialization
        mapper.addMixIn(EvaluationResult.class, ResourceIdentifierMixin.class);
        
        mapper.registerModule(module);
        
        return mapper;
    }
    
    /**
     * Register common node types to ensure they're available during deserialization.
     * This method ensures that frequently used node types are always registered.
     */
    private static void registerCommonNodeTypes() {
        try {
            // Register core types
            NodeTypeRegistry.registerNodeType("NodeGroup", NodeGroup.class);
            
            // Try to register falcon node types if available (they might be in different modules)
            try {
                Class<?> askProviderClass = Class.forName("me.vincentzz.falcon.node.AskProvider");
                if (askProviderClass != null) {
                    NodeTypeRegistry.registerNodeType("AskProvider", (Class<? extends CalculationNode>) askProviderClass);
                }
            } catch (ClassNotFoundException e) {
                // AskProvider not available, skip
            }
            
            try {
                Class<?> bidProviderClass = Class.forName("me.vincentzz.falcon.node.BidProvider");
                if (bidProviderClass != null) {
                    NodeTypeRegistry.registerNodeType("BidProvider", (Class<? extends CalculationNode>) bidProviderClass);
                }
            } catch (ClassNotFoundException e) {
                // BidProvider not available, skip
            }
            
            try {
                Class<?> midSpreadCalculatorClass = Class.forName("me.vincentzz.falcon.node.MidSpreadCalculator");
                if (midSpreadCalculatorClass != null) {
                    NodeTypeRegistry.registerNodeType("MidSpreadCalculator", (Class<? extends CalculationNode>) midSpreadCalculatorClass);
                }
            } catch (ClassNotFoundException e) {
                // MidSpreadCalculator not available, skip
            }
            
            try {
                Class<?> hardcodeAttributeProviderClass = Class.forName("me.vincentzz.falcon.node.HardcodeAttributeProvider");
                if (hardcodeAttributeProviderClass != null) {
                    NodeTypeRegistry.registerNodeType("HardcodeAttributeProvider", (Class<? extends CalculationNode>) hardcodeAttributeProviderClass);
                }
            } catch (ClassNotFoundException e) {
                // HardcodeAttributeProvider not available, skip
            }
            
            // Register resource types
            try {
                Class<?> falconResourceIdClass = Class.forName("me.vincentzz.falcon.ifo.FalconResourceId");
                if (falconResourceIdClass != null) {
                    NodeTypeRegistry.registerResourceType("FalconResourceId", (Class<? extends ResourceIdentifier>) falconResourceIdClass);
                }
            } catch (ClassNotFoundException e) {
                // FalconResourceId not available, skip
            } catch (Exception e) {
                // Error registering FalconResourceId
            }
            
        } catch (Exception e) {
            // Continue anyway - core functionality should still work
        }
    }
    
    // ===== SERIALIZATION METHODS =====
    
    /**
     * Serialize a CalculationNode to constructional JSON string.
     * 
     * @param node The CalculationNode to serialize
     * @return Result containing JSON string or error
     */
    public static Result<String> toJson(CalculationNode node) {
        try {
            String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(node);
            return Success.of(json);
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to serialize CalculationNode to JSON: " + e.getMessage(), e));
        }
    }
    
    /**
     * Serialize a CalculationNode to compact JSON string.
     * 
     * @param node The CalculationNode to serialize
     * @return Result containing compact JSON string or error
     */
    public static Result<String> toJsonCompact(CalculationNode node) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(node);
            return Success.of(json);
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to serialize CalculationNode to compact JSON: " + e.getMessage(), e));
        }
    }
    
    /**
     * Serialize a CalculationNode to a file.
     * 
     * @param node The CalculationNode to serialize
     * @param file The target file
     * @return Result indicating success or failure
     */
    public static Result<Void> toJsonFile(CalculationNode node, File file) {
        try {
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(file, node);
            return Success.of(null);
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to write CalculationNode to file: " + e.getMessage(), e));
        }
    }
    
    /**
     * Serialize a CalculationNode to a Path.
     * 
     * @param node The CalculationNode to serialize
     * @param path The target path
     * @return Result indicating success or failure
     */
    public static Result<Void> toJsonFile(CalculationNode node, Path path) {
        try {
            try (OutputStream os = Files.newOutputStream(path)) {
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(os, node);
            }
            return Success.of(null);
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to write CalculationNode to path: " + e.getMessage(), e));
        }
    }
    
    // ===== DESERIALIZATION METHODS =====
    
    /**
     * Deserialize a CalculationNode from JSON string.
     * 
     * @param json The JSON string
     * @return Result containing reconstructed CalculationNode or error
     */
    public static Result<CalculationNode> fromJson(String json) {
        try {
            CalculationNode node = OBJECT_MAPPER.readValue(json, CalculationNode.class);
            return Success.of(node);
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to deserialize JSON to CalculationNode: " + e.getMessage(), e));
        }
    }
    
    /**
     * Deserialize a CalculationNode from a file.
     * 
     * @param file The source file
     * @return Result containing reconstructed CalculationNode or error
     */
    public static Result<CalculationNode> fromJsonFile(File file) {
        try {
            CalculationNode node = OBJECT_MAPPER.readValue(file, CalculationNode.class);
            return Success.of(node);
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to read CalculationNode from file: " + e.getMessage(), e));
        }
    }
    
    /**
     * Deserialize a CalculationNode from a Path.
     * 
     * @param path The source path
     * @return Result containing reconstructed CalculationNode or error
     */
    public static Result<CalculationNode> fromJsonFile(Path path) {
        try {
            try (InputStream is = Files.newInputStream(path)) {
                CalculationNode node = OBJECT_MAPPER.readValue(is, CalculationNode.class);
                return Success.of(node);
            }
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to read CalculationNode from path: " + e.getMessage(), e));
        }
    }
    
    // ===== EVALUATION RESULT SERIALIZATION METHODS =====
    
    /**
     * Serialize an EvaluationResult to JSON string.
     * 
     * @param evaluationResult The EvaluationResult to serialize
     * @return Result containing JSON string or error
     */
    public static Result<String> toJsonEvaluationResult(EvaluationResult evaluationResult) {
        try {
            String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(evaluationResult);
            return Success.of(json);
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to serialize EvaluationResult to JSON: " + e.getMessage(), e));
        }
    }
    
    /**
     * Manually serialize a ConnectionPoint to JSON string
     */
    private static String manualSerializeConnectionPoint(me.vincentzz.graph.node.ConnectionPoint cp) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("        \"nodePath\" : \"").append(cp.nodePath()).append("\",\n");
        json.append("        \"resourceId\" : ").append(manualSerializeResourceIdentifier(cp.rid())).append("\n");
        json.append("      }");
        return json.toString();
    }
    
    /**
     * Manually serialize a ResourceIdentifier to JSON string
     */
    private static String manualSerializeResourceIdentifier(ResourceIdentifier rid) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("          \"type\" : \"").append(rid.getClass().getSimpleName()).append("\",\n");
        json.append("          \"data\" : {");
        
        // Handle different ResourceIdentifier types
        if (rid.getClass().getSimpleName().equals("FalconResourceId")) {
            // Use reflection to get FalconResourceId fields
            try {
                var ifoMethod = rid.getClass().getMethod("ifo");
                var sourceMethod = rid.getClass().getMethod("source");
                var attributeMethod = rid.getClass().getMethod("attribute");
                
                Object ifo = ifoMethod.invoke(rid);
                Object source = sourceMethod.invoke(rid);
                Object attribute = attributeMethod.invoke(rid);
                
                json.append("\n            \"ifo\" : \"").append(ifo).append("\",\n");
                json.append("            \"source\" : \"").append(source).append("\",\n");
                json.append("            \"attribute\" : \"").append(((Class<?>)attribute).getSimpleName()).append("\"\n");
            } catch (Exception e) {
                // Fallback - just use toString
                json.append("\n            \"toString\" : \"").append(rid.toString()).append("\"\n");
            }
        } else {
            // For other ResourceIdentifier types, use toString as fallback
            json.append("\n            \"toString\" : \"").append(rid.toString()).append("\"\n");
        }
        
        json.append("          }\n");
        json.append("        }");
        return json.toString();
    }
    
    /**
     * Manually serialize a Result to JSON string
     */
    private static String manualSerializeResult(me.vincentzz.lang.Result.Result<?> result) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        
        if (result instanceof me.vincentzz.lang.Result.Success<?> success) {
            json.append("          \"type\" : \"Success\",\n");
            json.append("          \"data\" : ");
            
            Object value = success.get();
            json.append(manualSerializeObject(value));
        } else if (result instanceof me.vincentzz.lang.Result.Failure<?> failure) {
            json.append("          \"type\" : \"Failure\",\n");
            json.append("          \"error\" : \"").append(failure.getException().getMessage().replace("\"", "\\\"")).append("\"");
        }
        
        json.append("\n        }");
        return json.toString();
    }
    
    /**
     * Manually serialize any object to JSON string
     */
    private static String manualSerializeObject(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + value.toString().replace("\"", "\\\"") + "\"";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof java.time.Instant) {
            return "\"" + value.toString() + "\"";
        } else {
            // For complex objects, try to serialize as JSON using reflection
            return manualSerializeComplexObject(value);
        }
    }
    
    /**
     * Manually serialize a complex object to JSON using reflection
     */
    private static String manualSerializeComplexObject(Object obj) {
        try {
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            
            // Get all fields using reflection
            java.lang.reflect.Field[] fields = obj.getClass().getDeclaredFields();
            boolean first = true;
            
            for (java.lang.reflect.Field field : fields) {
                // Skip static fields and synthetic fields
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                
                try {
                    field.setAccessible(true);
                    Object fieldValue = field.get(obj);
                    
                    if (!first) json.append(",\n");
                    first = false;
                    
                    json.append("            \"").append(field.getName()).append("\" : ");
                    json.append(manualSerializeObject(fieldValue));
                } catch (Exception e) {
                    // Skip fields that can't be accessed
                    continue;
                }
            }
            
            // If no fields were serialized, try to use record components (for Java records)
            if (first) {
                try {
                    java.lang.reflect.Method[] methods = obj.getClass().getDeclaredMethods();
                    for (java.lang.reflect.Method method : methods) {
                        // Look for accessor methods (no parameters, returns something, not static)
                        if (method.getParameterCount() == 0 && 
                            method.getReturnType() != void.class &&
                            !java.lang.reflect.Modifier.isStatic(method.getModifiers()) &&
                            !method.getName().equals("toString") &&
                            !method.getName().equals("hashCode") &&
                            !method.getName().equals("getClass")) {
                            
                            try {
                                Object methodValue = method.invoke(obj);
                                
                                if (!first) json.append(",\n");
                                first = false;
                                
                                json.append("            \"").append(method.getName()).append("\" : ");
                                json.append(manualSerializeObject(methodValue));
                            } catch (Exception e) {
                                // Skip methods that can't be invoked
                                continue;
                            }
                        }
                    }
                } catch (Exception e) {
                    // If reflection fails completely, fallback to toString
                    if (first) {
                        return "\"" + obj.toString().replace("\"", "\\\"") + "\"";
                    }
                }
            }
            
            json.append("\n          }");
            return json.toString();
        } catch (Exception e) {
            // Fallback to string representation if all else fails
            return "\"" + obj.toString().replace("\"", "\\\"") + "\"";
        }
    }
    
    /**
     * Serialize an EvaluationResult to compact JSON string.
     * 
     * @param evaluationResult The EvaluationResult to serialize
     * @return Result containing compact JSON string or error
     */
    public static Result<String> toJsonCompact(EvaluationResult evaluationResult) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(evaluationResult);
            return Success.of(json);
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to serialize EvaluationResult to compact JSON: " + e.getMessage(), e));
        }
    }
    
    /**
     * Serialize an EvaluationResult to a file.
     * 
     * @param evaluationResult The EvaluationResult to serialize
     * @param file The target file
     * @return Result indicating success or failure
     */
    public static Result<Void> toJsonFile(EvaluationResult evaluationResult, File file) {
        try {
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(file, evaluationResult);
            return Success.of(null);
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to write EvaluationResult to file: " + e.getMessage(), e));
        }
    }
    
    /**
     * Serialize an EvaluationResult to a Path.
     * 
     * @param evaluationResult The EvaluationResult to serialize
     * @param path The target path
     * @return Result indicating success or failure
     */
    public static Result<Void> toJsonFile(EvaluationResult evaluationResult, Path path) {
        try {
            try (OutputStream os = Files.newOutputStream(path)) {
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(os, evaluationResult);
            }
            return Success.of(null);
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to write EvaluationResult to path: " + e.getMessage(), e));
        }
    }
    
    // ===== EVALUATION RESULT DESERIALIZATION METHODS =====
    
    /**
     * Deserialize an EvaluationResult from JSON string.
     * 
     * @param json The JSON string
     * @return Result containing reconstructed EvaluationResult or error
     */
    public static Result<EvaluationResult> fromJsonEvaluationResult(String json) {
        try {
            EvaluationResult evaluationResult = OBJECT_MAPPER.readValue(json, EvaluationResult.class);
            return Success.of(evaluationResult);
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to deserialize JSON to EvaluationResult: " + e.getMessage(), e));
        }
    }
    
    /**
     * Deserialize an EvaluationResult from a file.
     * 
     * @param file The source file
     * @return Result containing reconstructed EvaluationResult or error
     */
    public static Result<EvaluationResult> fromJsonEvaluationResultFile(File file) {
        try {
            EvaluationResult evaluationResult = OBJECT_MAPPER.readValue(file, EvaluationResult.class);
            return Success.of(evaluationResult);
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to read EvaluationResult from file: " + e.getMessage(), e));
        }
    }
    
    /**
     * Deserialize an EvaluationResult from a Path.
     * 
     * @param path The source path
     * @return Result containing reconstructed EvaluationResult or error
     */
    public static Result<EvaluationResult> fromJsonEvaluationResultFile(Path path) {
        try {
            try (InputStream is = Files.newInputStream(path)) {
                EvaluationResult evaluationResult = OBJECT_MAPPER.readValue(is, EvaluationResult.class);
                return Success.of(evaluationResult);
            }
        } catch (Exception e) {
            return Failure.of(new RuntimeException("Failed to read EvaluationResult from path: " + e.getMessage(), e));
        }
    }
    
    // ===== VALIDATION AND ANALYSIS METHODS =====
    
    /**
     * Validate that a JSON string can be parsed as a CalculationNode.
     * 
     * @param json The JSON string to validate
     * @return Result indicating validation success or failure with details
     */
    public static Result<ValidationResult> validateJson(String json) {
        try {
            CalculationNode node = OBJECT_MAPPER.readValue(json, CalculationNode.class);
            
            ValidationResult validation = ValidationResult.builder()
                .valid(true)
                .nodeType(node.getClass().getSimpleName())
                .nodeName(node.name())
                .inputCount(node.inputs().size())
                .outputCount(node.outputs().size())
                .build();
                
            return Success.of(validation);
        } catch (Exception e) {
            ValidationResult validation = ValidationResult.builder()
                .valid(false)
                .errorMessage(e.getMessage())
                .build();
            return Success.of(validation);
        }
    }
    
    /**
     * Test round-trip serialization/deserialization.
     * 
     * @param node The node to test
     * @return Result indicating round-trip success or failure
     */
    public static Result<RoundTripResult> testRoundTrip(CalculationNode node) {
        try {
            // Serialize
            String json = OBJECT_MAPPER.writeValueAsString(node);
            
            // Deserialize
            CalculationNode reconstructed = OBJECT_MAPPER.readValue(json, CalculationNode.class);
            
            // Compare basic properties
            boolean nameMatch = node.name().equals(reconstructed.name());
            boolean typeMatch = node.getClass().equals(reconstructed.getClass());
            boolean inputCountMatch = node.inputs().size() == reconstructed.inputs().size();
            boolean outputCountMatch = node.outputs().size() == reconstructed.outputs().size();
            
            RoundTripResult result = RoundTripResult.builder()
                .successful(nameMatch && typeMatch && inputCountMatch && outputCountMatch)
                .originalNode(node)
                .reconstructedNode(reconstructed)
                .jsonLength(json.length())
                .nameMatch(nameMatch)
                .typeMatch(typeMatch)
                .inputCountMatch(inputCountMatch)
                .outputCountMatch(outputCountMatch)
                .build();
                
            return Success.of(result);
        } catch (Exception e) {
            RoundTripResult result = RoundTripResult.builder()
                .successful(false)
                .originalNode(node)
                .errorMessage(e.getMessage())
                .build();
            return Success.of(result);
        }
    }
    
    // ===== UTILITY METHODS =====
    
    /**
     * Get the configured ObjectMapper for advanced usage.
     * 
     * @return The configured ObjectMapper instance
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
    
    /**
     * Get statistics about registered types.
     * 
     * @return Statistics about the type registry
     */
    public static RegistryStatistics getRegistryStatistics() {
        return RegistryStatistics.builder()
            .registeredNodeTypes(NodeTypeRegistry.getRegisteredNodeTypes().size())
            .registeredResourceTypes(NodeTypeRegistry.getRegisteredResourceTypes().size())
            .nodeTypeNames(NodeTypeRegistry.getRegisteredNodeTypes())
            .resourceTypeNames(NodeTypeRegistry.getRegisteredResourceTypes())
            .build();
    }
    
    // ===== RESULT CLASSES =====
    
    /**
     * Result of JSON validation
     */
    public record ValidationResult(
        boolean valid,
        String nodeType,
        String nodeName,
        Integer inputCount,
        Integer outputCount,
        String errorMessage
    ) {
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private boolean valid;
            private String nodeType;
            private String nodeName;
            private Integer inputCount;
            private Integer outputCount;
            private String errorMessage;
            
            public Builder valid(boolean valid) {
                this.valid = valid;
                return this;
            }
            
            public Builder nodeType(String nodeType) {
                this.nodeType = nodeType;
                return this;
            }
            
            public Builder nodeName(String nodeName) {
                this.nodeName = nodeName;
                return this;
            }
            
            public Builder inputCount(Integer inputCount) {
                this.inputCount = inputCount;
                return this;
            }
            
            public Builder outputCount(Integer outputCount) {
                this.outputCount = outputCount;
                return this;
            }
            
            public Builder errorMessage(String errorMessage) {
                this.errorMessage = errorMessage;
                return this;
            }
            
            public ValidationResult build() {
                return new ValidationResult(valid, nodeType, nodeName, inputCount, outputCount, errorMessage);
            }
        }
    }
    
    /**
     * Result of round-trip testing
     */
    public record RoundTripResult(
        boolean successful,
        CalculationNode originalNode,
        CalculationNode reconstructedNode,
        Integer jsonLength,
        Boolean nameMatch,
        Boolean typeMatch,
        Boolean inputCountMatch,
        Boolean outputCountMatch,
        String errorMessage
    ) {
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private boolean successful;
            private CalculationNode originalNode;
            private CalculationNode reconstructedNode;
            private Integer jsonLength;
            private Boolean nameMatch;
            private Boolean typeMatch;
            private Boolean inputCountMatch;
            private Boolean outputCountMatch;
            private String errorMessage;
            
            public Builder successful(boolean successful) {
                this.successful = successful;
                return this;
            }
            
            public Builder originalNode(CalculationNode originalNode) {
                this.originalNode = originalNode;
                return this;
            }
            
            public Builder reconstructedNode(CalculationNode reconstructedNode) {
                this.reconstructedNode = reconstructedNode;
                return this;
            }
            
            public Builder jsonLength(Integer jsonLength) {
                this.jsonLength = jsonLength;
                return this;
            }
            
            public Builder nameMatch(Boolean nameMatch) {
                this.nameMatch = nameMatch;
                return this;
            }
            
            public Builder typeMatch(Boolean typeMatch) {
                this.typeMatch = typeMatch;
                return this;
            }
            
            public Builder inputCountMatch(Boolean inputCountMatch) {
                this.inputCountMatch = inputCountMatch;
                return this;
            }
            
            public Builder outputCountMatch(Boolean outputCountMatch) {
                this.outputCountMatch = outputCountMatch;
                return this;
            }
            
            public Builder errorMessage(String errorMessage) {
                this.errorMessage = errorMessage;
                return this;
            }
            
            public RoundTripResult build() {
                return new RoundTripResult(successful, originalNode, reconstructedNode, jsonLength,
                                         nameMatch, typeMatch, inputCountMatch, outputCountMatch, errorMessage);
            }
        }
    }
    
    /**
     * Registry statistics
     */
    public record RegistryStatistics(
        Integer registeredNodeTypes,
        Integer registeredResourceTypes,
        Set<String> nodeTypeNames,
        Set<String> resourceTypeNames
    ) {
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private Integer registeredNodeTypes;
            private Integer registeredResourceTypes;
            private Set<String> nodeTypeNames;
            private Set<String> resourceTypeNames;
            
            public Builder registeredNodeTypes(Integer registeredNodeTypes) {
                this.registeredNodeTypes = registeredNodeTypes;
                return this;
            }
            
            public Builder registeredResourceTypes(Integer registeredResourceTypes) {
                this.registeredResourceTypes = registeredResourceTypes;
                return this;
            }
            
            public Builder nodeTypeNames(Set<String> nodeTypeNames) {
                this.nodeTypeNames = nodeTypeNames;
                return this;
            }
            
            public Builder resourceTypeNames(Set<String> resourceTypeNames) {
                this.resourceTypeNames = resourceTypeNames;
                return this;
            }
            
            public RegistryStatistics build() {
                return new RegistryStatistics(registeredNodeTypes, registeredResourceTypes,
                                            nodeTypeNames, resourceTypeNames);
            }
        }
    }
}
