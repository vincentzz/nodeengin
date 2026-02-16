package me.vincentzz.graph.json;

import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.graph.node.NodeGroup;
import me.vincentzz.graph.node.Flywire;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.scope.Scope;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing CalculationNode and ResourceIdentifier types for JSON serialization.
 * Provides reflection-based construction capabilities.
 */
public class NodeTypeRegistry {
    
    private static final Map<String, Class<? extends CalculationNode>> NODE_TYPES = new ConcurrentHashMap<>();
    private static final Map<String, Class<? extends ResourceIdentifier>> RESOURCE_TYPES = new ConcurrentHashMap<>();
    
    static {
        // Register core types
        registerNodeType("NodeGroup", NodeGroup.class);
        
        // Register BasicResourceIdentifier - need to load it dynamically since it's in test package
        try {
            Class<?> basicResourceIdClass = Class.forName("me.vincentzz.graph.BasicResourceIdentifier");
            if (ResourceIdentifier.class.isAssignableFrom(basicResourceIdClass)) {
                @SuppressWarnings("unchecked")
                Class<? extends ResourceIdentifier> resourceClass = (Class<? extends ResourceIdentifier>) basicResourceIdClass;
                registerResourceType("BasicResourceIdentifier", resourceClass);
            }
        } catch (ClassNotFoundException e) {
            // BasicResourceIdentifier not available, will need to be registered manually
        }
        
        // More types will be registered by implementations
    }
    
    /**
     * Register a CalculationNode type for JSON deserialization.
     * 
     * @param typeName Simple class name or custom identifier
     * @param nodeClass The CalculationNode implementation class
     */
    public static void registerNodeType(String typeName, Class<? extends CalculationNode> nodeClass) {
        NODE_TYPES.put(typeName, nodeClass);
    }
    
    /**
     * Register a ResourceIdentifier type for JSON deserialization.
     * 
     * @param typeName Simple class name or custom identifier  
     * @param resourceClass The ResourceIdentifier implementation class
     */
    public static void registerResourceType(String typeName, Class<? extends ResourceIdentifier> resourceClass) {
        RESOURCE_TYPES.put(typeName, resourceClass);
    }
    
    /**
     * Get registered CalculationNode class by type name.
     * 
     * @param typeName The type identifier
     * @return The registered class
     * @throws IllegalArgumentException if type not found
     */
    public static Class<? extends CalculationNode> getNodeClass(String typeName) {
        Class<? extends CalculationNode> clazz = NODE_TYPES.get(typeName);
        if (clazz == null) {
            throw new IllegalArgumentException("Unknown node type: " + typeName);
        }
        return clazz;
    }
    
    /**
     * Get registered ResourceIdentifier class by type name.
     * 
     * @param typeName The type identifier
     * @return The registered class
     * @throws IllegalArgumentException if type not found
     */
    public static Class<? extends ResourceIdentifier> getResourceClass(String typeName) {
        Class<? extends ResourceIdentifier> clazz = RESOURCE_TYPES.get(typeName);
        if (clazz == null) {
            throw new IllegalArgumentException("Unknown resource type: " + typeName);
        }
        return clazz;
    }
    
    /**
     * Create a CalculationNode instance using reflection.
     * 
     * @param typeName The type identifier
     * @param parameters The construction parameters
     * @return The constructed node
     * @throws RuntimeException if construction fails
     */
    public static CalculationNode createNode(String typeName, Map<String, Object> parameters) {
        try {
            Class<? extends CalculationNode> nodeClass = getNodeClass(typeName);

            if (NodeGroup.class.equals(nodeClass)) {
                return createNodeGroup(parameters);
            } else {
                return createAtomicNode(nodeClass, parameters);
            }
        } catch (Exception e) {
            throw e;
        }
    }
    
    /**
     * Create a ResourceIdentifier instance using reflection.
     * 
     * @param typeName The type identifier
     * @param parameters The construction parameters (as Object[])
     * @return The constructed ResourceIdentifier
     * @throws RuntimeException if construction fails
     */
    public static ResourceIdentifier createResource(String typeName, Object[] parameters) {
        Class<? extends ResourceIdentifier> resourceClass = getResourceClass(typeName);
        
        try {
            // Find constructor that matches parameter count
            Constructor<?>[] constructors = resourceClass.getConstructors();
            for (Constructor<?> constructor : constructors) {
                if (constructor.getParameterCount() == parameters.length) {
                    return (ResourceIdentifier) constructor.newInstance(parameters);
                }
            }
            throw new IllegalArgumentException("No matching constructor found for " + typeName + " with " + parameters.length + " parameters");
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to create ResourceIdentifier of type " + typeName, e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private static NodeGroup createNodeGroup(Map<String, Object> parameters) {
        try {
            String name = (String) parameters.get("name");
            
            Object nodesObject = parameters.get("nodes");

            if (nodesObject instanceof List) {
                List<Map<String, Object>> nodesList = (List<Map<String, Object>>) parameters.get("nodes");
            } else {
                throw new RuntimeException("Expected 'nodes' to be a List but got " + (nodesObject != null ? nodesObject.getClass() : "null"));
            }
            
            List<Map<String, Object>> nodesList = (List<Map<String, Object>>) parameters.get("nodes");
            List<Map<String, Object>> flywiresList = (List<Map<String, Object>>) parameters.getOrDefault("flywires", List.of());
            Map<String, Object> exportsMap = (Map<String, Object>) parameters.get("exports");
            
            // Create nodes
            Set<CalculationNode> nodes = new HashSet<>();
            if (nodesList != null) {
                for (Map<String, Object> nodeSpec : nodesList) {
                    String nodeType = (String) nodeSpec.get("type");
                    
                    if ("NodeGroup".equals(nodeType)) {
                        // NodeGroups use "parameter" (singular)
                        Map<String, Object> nodeParameter = (Map<String, Object>) nodeSpec.get("parameter");
                        if (nodeParameter != null) {
                            CalculationNode node = createNode(nodeType, nodeParameter);
                            nodes.add(node);
                        }
                    } else {
                        // Atomic nodes use "parameters" (plural) - array of parameter sets
                        List<Map<String, Object>> nodeParameters = (List<Map<String, Object>>) nodeSpec.get("parameters");
                        if (nodeParameters != null) {
                            for (Map<String, Object> paramSet : nodeParameters) {
                                CalculationNode node = createNode(nodeType, paramSet);
                                nodes.add(node);
                            }
                        }
                    }
                }
            }
            
            // Create flywires
            Set<Flywire> flywires = new HashSet<>();
            if (flywiresList != null) {
                for (Map<String, Object> flywireSpec : flywiresList) {
                    Map<String, Object> sourceSpec = (Map<String, Object>) flywireSpec.get("source");
                    Map<String, Object> targetSpec = (Map<String, Object>) flywireSpec.get("target");
                    
                    ConnectionPoint source = createConnectionPoint(sourceSpec);
                    ConnectionPoint target = createConnectionPoint(targetSpec);
                    
                    flywires.add(new Flywire(source, target));
                }
            }
            
            // Create exports scope
            Scope<ConnectionPoint> exports = createScope(exportsMap);
            
            return new NodeGroup(name, nodes, flywires, exports);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create NodeGroup", e);
        }
    }
    
    private static CalculationNode createAtomicNode(Class<? extends CalculationNode> nodeClass, Map<String, Object> parameters) {
        try {
            // First, reconstruct complex objects from the parameters
            Map<String, Object> reconstructedParams = new HashMap<>();
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                Object reconstructed = reconstructComplexObject(value);
                reconstructedParams.put(key, reconstructed);
            }
            
            // For AtomicNode, we need to find the appropriate constructor
            Constructor<?>[] constructors = nodeClass.getConstructors();
            
            // Try to find a constructor that matches our parameters
            for (Constructor<?> constructor : constructors) {
                Class<?>[] paramTypes = constructor.getParameterTypes();
                Object[] args = new Object[paramTypes.length];

                // Enhanced parameter mapping with complex object support
                if (tryMapParametersEnhanced(reconstructedParams, paramTypes, args)) {
                    return (CalculationNode) constructor.newInstance(args);
                }
            }
            
            throw new IllegalArgumentException("No suitable constructor found for " + nodeClass.getSimpleName());
            
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to create AtomicNode of type " + nodeClass.getSimpleName(), e);
        }
    }
    
    private static boolean tryMapParameters(Map<String, Object> parameters, Class<?>[] paramTypes, Object[] args) {
        // Enhanced parameter mapping with type compatibility checking
        if (parameters.size() != paramTypes.length) {
            return false;
        }
        
        List<Object> values = new ArrayList<>(parameters.values());
        
        for (int i = 0; i < paramTypes.length; i++) {
            Object value = values.get(i);
            Class<?> paramType = paramTypes[i];
            
            // Handle type compatibility
            if (value == null) {
                args[i] = null;
            } else if (paramType.isAssignableFrom(value.getClass())) {
                args[i] = value;
            } else if (paramType == String.class && value instanceof String) {
                args[i] = value;
            } else if (paramType == double.class && value instanceof Number) {
                args[i] = ((Number) value).doubleValue();
            } else if (paramType == Double.class && value instanceof Number) {
                args[i] = ((Number) value).doubleValue();
            } else if (paramType == int.class && value instanceof Number) {
                args[i] = ((Number) value).intValue();
            } else if (paramType == Integer.class && value instanceof Number) {
                args[i] = ((Number) value).intValue();
            } else if (paramType == float.class && value instanceof Number) {
                args[i] = ((Number) value).floatValue();
            } else if (paramType == Float.class && value instanceof Number) {
                args[i] = ((Number) value).floatValue();
            } else if (paramType == long.class && value instanceof Number) {
                args[i] = ((Number) value).longValue();
            } else if (paramType == Long.class && value instanceof Number) {
                args[i] = ((Number) value).longValue();
            } else if (paramType == boolean.class && value instanceof Boolean) {
                args[i] = value;
            } else if (paramType == Boolean.class && value instanceof Boolean) {
                args[i] = value;
            } else {
                return false; // Type mismatch
            }
        }
        return true;
    }
    
    @SuppressWarnings("unchecked")
    private static ConnectionPoint createConnectionPoint(Map<String, Object> spec) {
        // Support both old format (node) and new format (nodePath) with filesystem-like path resolution
        String nodePath = (String) spec.get("nodePath");
        if (nodePath == null) {
            // Fallback to legacy "node" field for backward compatibility
            nodePath = (String) spec.get("node");
        }
        
        if (nodePath == null) {
            throw new IllegalArgumentException("ConnectionPoint missing both 'nodePath' and 'node' fields");
        }
        
        Map<String, Object> resourceSpec = (Map<String, Object>) spec.get("resourceId");
        
        String resourceType = (String) resourceSpec.get("type");
        
        // Handle both "parameters" (array format) and "data" (object format) 
        Object paramsOrData = resourceSpec.get("parameters");
        if (paramsOrData == null) {
            paramsOrData = resourceSpec.get("data");
        }
        
        ResourceIdentifier resourceId;
        if (paramsOrData instanceof List) {
            // Array format: parameters: [...]
            List<Object> resourceParams = (List<Object>) paramsOrData;
            resourceId = createResource(resourceType, resourceParams.toArray());
        } else if (paramsOrData instanceof Map) {
            // Object format: data: {...}
            Map<String, Object> dataMap = (Map<String, Object>) paramsOrData;
            resourceId = createResourceFromData(resourceType, dataMap);
        } else {
            throw new IllegalArgumentException("Invalid ResourceIdentifier format - expected parameters array or data object");
        }
        
        return new ConnectionPoint(Path.of(nodePath), resourceId);
    }
    
    /**
     * Create a ResourceIdentifier from a data map (object format).
     * Handles both BasicResourceIdentifier and FalconResourceId formats.
     */
    @SuppressWarnings("unchecked")
    private static ResourceIdentifier createResourceFromData(String resourceType, Map<String, Object> dataMap) {
        if ("BasicResourceIdentifier".equals(resourceType)) {
            // Handle BasicResourceIdentifier with name and type fields
            String name = (String) dataMap.get("name");
            Object typeValue = dataMap.get("type");
            
            // Convert type string to Class
            Class<?> typeClass;
            try {
                if (typeValue instanceof String) {
                    String typeStr = (String) typeValue;
                    if ("String".equals(typeStr)) {
                        typeClass = String.class;
                    } else if ("Integer".equals(typeStr)) {
                        typeClass = Integer.class;
                    } else if ("Double".equals(typeStr)) {
                        typeClass = Double.class;
                    } else if ("Boolean".equals(typeStr)) {
                        typeClass = Boolean.class;
                    } else {
                        // Try to load the class by name
                        typeClass = Class.forName(typeStr);
                    }
                } else if (typeValue instanceof Class) {
                    typeClass = (Class<?>) typeValue;
                } else {
                    throw new RuntimeException("Invalid type value for BasicResourceIdentifier: " + typeValue);
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Unknown type class: " + typeValue, e);
            }
            
            // Create BasicResourceIdentifier using constructor or factory method
            try {
                Class<?> basicResourceIdClass = getResourceClass(resourceType);
                
                // Handle record types specially - they have different constructor patterns
                if (basicResourceIdClass.isRecord()) {
                    // For records, use getDeclaredConstructors() to get the canonical constructor
                    Constructor<?>[] constructors = basicResourceIdClass.getDeclaredConstructors();
                    for (Constructor<?> constructor : constructors) {
                        Class<?>[] paramTypes = constructor.getParameterTypes();
                        if (paramTypes.length == 2 && 
                            paramTypes[0] == String.class && 
                            paramTypes[1] == Class.class) {
                            constructor.setAccessible(true); // Make it accessible for cross-package access
                            return (ResourceIdentifier) constructor.newInstance(name, typeClass);
                        }
                    }
                    throw new RuntimeException("No matching constructor found for record " + resourceType);
                } else {
                    // Regular class - use getConstructor
                    Constructor<?> constructor = basicResourceIdClass.getConstructor(String.class, Class.class);
                    return (ResourceIdentifier) constructor.newInstance(name, typeClass);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to create BasicResourceIdentifier from data map", e);
            }
        } else if ("FalconResourceId".equals(resourceType)) {
            String ifo = (String) dataMap.get("ifo");
            String source = (String) dataMap.get("source");
            String attribute = (String) dataMap.get("attribute");
            
            // Convert attribute string to Class
            Class<?> attributeClass;
            try {
                // Try with the full package name first
                if (attribute.contains(".")) {
                    attributeClass = Class.forName(attribute);
                } else {
                    // If it's just a simple class name, try with the falcon attribute package
                    attributeClass = Class.forName("me.vincentzz.falcon.attribute." + attribute);
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Unknown attribute class: " + attribute, e);
            }
            
            // Call FalconResourceId.of(ifo, source, attributeClass)
            try {
                Class<?> falconResourceIdClass = getResourceClass(resourceType);
                java.lang.reflect.Method ofMethod = falconResourceIdClass.getMethod("of", String.class, String.class, Class.class);
                return (ResourceIdentifier) ofMethod.invoke(null, ifo, source, attributeClass);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create FalconResourceId from data map", e);
            }
        } else {
            throw new IllegalArgumentException("Unsupported resource type for data format: " + resourceType);
        }
    }
    
    @SuppressWarnings("unchecked")
    private static Scope<ConnectionPoint> createScope(Map<String, Object> exportsMap) {
        if (exportsMap == null) {
            return me.vincentzz.graph.scope.Exclude.of(Set.of());
        }
        
        String scopeType = (String) exportsMap.get("type");
        List<Map<String, Object>> valuesList = (List<Map<String, Object>>) exportsMap.get("values");
        
        Set<ConnectionPoint> connectionPoints = new HashSet<>();
        if (valuesList != null) {
            for (Map<String, Object> valueSpec : valuesList) {
                String nodeName = (String) valueSpec.get("nodePath");
                Map<String, Object> resourceSpec = (Map<String, Object>) valueSpec.get("resourceId");
                
                String resourceType = (String) resourceSpec.get("type");
                
                // Handle both "parameters" (array format) and "data" (object format) 
                Object paramsOrData = resourceSpec.get("parameters");
                if (paramsOrData == null) {
                    paramsOrData = resourceSpec.get("data");
                }
                
                ResourceIdentifier resourceId;
                if (paramsOrData instanceof List) {
                    // Array format: parameters: [...]
                    List<Object> resourceParams = (List<Object>) paramsOrData;
                    resourceId = createResource(resourceType, resourceParams.toArray());
                } else if (paramsOrData instanceof Map) {
                    // Object format: data: {...}
                    Map<String, Object> dataMap = (Map<String, Object>) paramsOrData;
                    resourceId = createResourceFromData(resourceType, dataMap);
                } else {
                    throw new IllegalArgumentException("Invalid ResourceIdentifier format in scope - expected parameters array or data object");
                }
                
                connectionPoints.add(new ConnectionPoint(Path.of(nodeName), resourceId));
            }
        }
        
        return switch (scopeType) {
            case "Include" -> me.vincentzz.graph.scope.Include.of(connectionPoints);
            case "Exclude" -> me.vincentzz.graph.scope.Exclude.of(connectionPoints);
            default -> throw new IllegalArgumentException("Unknown scope type: " + scopeType);
        };
    }
    
    /**
     * Reconstruct complex objects from serialized JSON data.
     * 
     * @param value The serialized value
     * @return The reconstructed object
     */
    @SuppressWarnings("unchecked")
    private static Object reconstructComplexObject(Object value) {
        if (value == null) {
            return null;
        }
        
        if (value instanceof Map) {
            Map<String, Object> valueMap = (Map<String, Object>) value;
            
            // Check if it's a typed object (has "type" field)
            if (valueMap.containsKey("type")) {
                String type = (String) valueMap.get("type");
                
                if ("FalconResourceId".equals(type)) {
                    // Handle FalconResourceId reconstruction
                    Map<String, Object> data = (Map<String, Object>) valueMap.get("data");
                    return createResourceFromData(type, data);
                } else if (type.endsWith("Ask") || type.endsWith("Bid") || type.endsWith("Volume") || 
                          type.endsWith("Vwap") || type.endsWith("MarkToMarket") || type.endsWith("MidPrice") || type.endsWith("Spread")) {
                    // Handle attribute object reconstruction
                    return reconstructAttributeObject(type, valueMap);
                }
            }
        }
        
        // Return as-is for primitives and unrecognized objects
        return value;
    }
    
    /**
     * Reconstruct attribute objects (Ask, Bid, etc.) from JSON data.
     * 
     * @param type The attribute type
     * @param valueMap The serialized data
     * @return The reconstructed attribute object
     */
    @SuppressWarnings("unchecked")
    private static Object reconstructAttributeObject(String type, Map<String, Object> valueMap) {
        try {
            // Get the class for the attribute type
            Class<?> attributeClass = Class.forName("me.vincentzz.falcon.attribute." + type);
            
            // Get the data section
            Map<String, Object> data = (Map<String, Object>) valueMap.get("data");
            if (data == null) {
                throw new RuntimeException("Missing 'data' field for attribute type: " + type);
            }
            
            // Find a suitable constructor
            Constructor<?>[] constructors = attributeClass.getConstructors();
            for (Constructor<?> constructor : constructors) {
                Class<?>[] paramTypes = constructor.getParameterTypes();
                Object[] args = new Object[paramTypes.length];
                
                // Try to map the data to constructor parameters
                if (tryMapDataToConstructor(data, paramTypes, args)) {
                    return constructor.newInstance(args);
                }
            }
            
            throw new RuntimeException("No suitable constructor found for attribute type: " + type);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to reconstruct attribute object of type: " + type, e);
        }
    }
    
    /**
     * Try to map data fields to constructor parameters.
     * Enhanced to handle Ask record: (BigDecimal price, BigDecimal size, Instant time)
     * 
     * @param data The data map
     * @param paramTypes The constructor parameter types
     * @param args The arguments array to fill
     * @return true if mapping was successful
     */
    private static boolean tryMapDataToConstructor(Map<String, Object> data, Class<?>[] paramTypes, Object[] args) {
        // Handle Ask record specifically: need to match field names to constructor parameter positions
        if (paramTypes.length == 3 && data.containsKey("price") && data.containsKey("size") && data.containsKey("time")) {
            try {
                // For Ask(BigDecimal price, BigDecimal size, Instant time)
                Object priceValue = data.get("price");
                Object sizeValue = data.get("size");
                Object timeValue = data.get("time");

                // Convert price to BigDecimal
                if (paramTypes[0].getName().equals("java.math.BigDecimal")) {
                    if (priceValue instanceof Number) {
                        args[0] = new java.math.BigDecimal(priceValue.toString());
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }

                // Convert size to BigDecimal
                if (paramTypes[1].getName().equals("java.math.BigDecimal")) {
                    if (sizeValue instanceof Number) {
                        args[1] = new java.math.BigDecimal(sizeValue.toString());
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }

                // Convert time to Instant
                if (paramTypes[2].getName().equals("java.time.Instant")) {
                    if (timeValue instanceof String) {
                        args[2] = java.time.Instant.parse((String) timeValue);
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }

                return true;
                
            } catch (Exception e) {
                return false;
            }
        }
        
        // Fallback: use generic mapping for other types
        List<Object> values = new ArrayList<>(data.values());
        
        if (values.size() != paramTypes.length) {
            return false;
        }
        
        for (int i = 0; i < paramTypes.length; i++) {
            Object value = values.get(i);
            Class<?> paramType = paramTypes[i];
            
            if (value == null) {
                args[i] = null;
            } else if (paramType.isAssignableFrom(value.getClass())) {
                args[i] = value;
            } else if (paramType == double.class && value instanceof Number) {
                args[i] = ((Number) value).doubleValue();
            } else if (paramType == Double.class && value instanceof Number) {
                args[i] = ((Number) value).doubleValue();
            } else if (paramType == int.class && value instanceof Number) {
                args[i] = ((Number) value).intValue();
            } else if (paramType == Integer.class && value instanceof Number) {
                args[i] = ((Number) value).intValue();
            } else if (paramType == String.class && value instanceof String) {
                args[i] = value;
            } else if (paramType.getName().equals("java.time.Instant") && value instanceof String) {
                // Handle time-related fields - convert string to Instant
                try {
                    args[i] = java.time.Instant.parse((String) value);
                } catch (Exception e) {
                    return false;
                }
            } else if (paramType.getName().equals("java.math.BigDecimal") && value instanceof Number) {
                // Handle BigDecimal conversion
                try {
                    args[i] = new java.math.BigDecimal(value.toString());
                } catch (Exception e) {
                    return false;
                }
            } else {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Enhanced parameter mapping with complex object support.
     * Maps parameters by name for record constructors like HardcodeAttributeProvider(FalconResourceId rid, Object data).
     * 
     * @param parameters The reconstructed parameters
     * @param paramTypes The constructor parameter types
     * @param args The arguments array to fill
     * @return true if mapping was successful
     */
    private static boolean tryMapParametersEnhanced(Map<String, Object> parameters, Class<?>[] paramTypes, Object[] args) {
        if (parameters.size() != paramTypes.length) {
            return false;
        }

        // For HardcodeAttributeProvider(FalconResourceId rid, Object data), map by parameter names
        if (paramTypes.length == 2 && parameters.containsKey("rid") && parameters.containsKey("data")) {
            Object ridValue = parameters.get("rid");
            Object dataValue = parameters.get("data");

            // First parameter should be rid (FalconResourceId)
            if (paramTypes[0].getSimpleName().equals("FalconResourceId") && ridValue != null && ridValue.getClass().getSimpleName().equals("FalconResourceId")) {
                args[0] = ridValue;
            } else {
                return false;
            }

            // Second parameter should be data (Object)
            if (paramTypes[1] == Object.class) {
                args[1] = dataValue;
            } else {
                return false;
            }

            return true;
        }
        
        // Fallback: use original mapping by order for other constructors
        List<Object> values = new ArrayList<>(parameters.values());
        
        for (int i = 0; i < paramTypes.length; i++) {
            Object value = values.get(i);
            Class<?> paramType = paramTypes[i];

            if (value == null) {
                args[i] = null;
            } else if (paramType.isAssignableFrom(value.getClass())) {
                args[i] = value;
            } else if (paramType == String.class && value instanceof String) {
                args[i] = value;
            } else if (paramType == double.class && value instanceof Number) {
                args[i] = ((Number) value).doubleValue();
            } else if (paramType == Double.class && value instanceof Number) {
                args[i] = ((Number) value).doubleValue();
            } else if (paramType == int.class && value instanceof Number) {
                args[i] = ((Number) value).intValue();
            } else if (paramType == Integer.class && value instanceof Number) {
                args[i] = ((Number) value).intValue();
            } else if (paramType == float.class && value instanceof Number) {
                args[i] = ((Number) value).floatValue();
            } else if (paramType == Float.class && value instanceof Number) {
                args[i] = ((Number) value).floatValue();
            } else if (paramType == long.class && value instanceof Number) {
                args[i] = ((Number) value).longValue();
            } else if (paramType == Long.class && value instanceof Number) {
                args[i] = ((Number) value).longValue();
            } else if (paramType == boolean.class && value instanceof Boolean) {
                args[i] = value;
            } else if (paramType == Boolean.class && value instanceof Boolean) {
                args[i] = value;
            } else {
                return false; // Type mismatch
            }
        }
        return true;
    }
    
    /**
     * Get all registered node type names.
     * 
     * @return Set of registered type names
     */
    public static Set<String> getRegisteredNodeTypes() {
        return new HashSet<>(NODE_TYPES.keySet());
    }
    
    /**
     * Get all registered resource type names.
     * 
     * @return Set of registered type names
     */
    public static Set<String> getRegisteredResourceTypes() {
        return new HashSet<>(RESOURCE_TYPES.keySet());
    }
}
