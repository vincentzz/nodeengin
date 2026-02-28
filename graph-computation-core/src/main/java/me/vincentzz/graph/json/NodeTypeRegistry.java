package me.vincentzz.graph.json;

import com.fasterxml.jackson.databind.JsonNode;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.graph.node.NodeGroup;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing CalculationNode, ResourceIdentifier, and typed value classes
 * for JSON serialization. Provides record-based construction from deserialized data.
 */
public class NodeTypeRegistry {

    private static final Map<String, Class<? extends CalculationNode>> NODE_TYPES = new ConcurrentHashMap<>();
    private static final Map<String, Class<? extends ResourceIdentifier>> RESOURCE_TYPES = new ConcurrentHashMap<>();
    private static final Map<String, Class<?>> VALUE_TYPES = new ConcurrentHashMap<>();
    private static final Map<String, Class<?>> CLASS_ALIASES = new ConcurrentHashMap<>();

    static {
        registerNodeType("NodeGroup", NodeGroup.class);
        registerClassAlias("String", String.class);
        registerClassAlias("Integer", Integer.class);
        registerClassAlias("Double", Double.class);
        registerClassAlias("Boolean", Boolean.class);
        registerClassAlias("Long", Long.class);
        registerClassAlias("Float", Float.class);

        // Try to register falcon types if on classpath
        tryRegister("me.vincentzz.falcon.node.AskProvider", "AskProvider", "node");
        tryRegister("me.vincentzz.falcon.node.BidProvider", "BidProvider", "node");
        tryRegister("me.vincentzz.falcon.node.MidSpreadCalculator", "MidSpreadCalculator", "node");
        tryRegister("me.vincentzz.falcon.node.HardcodeAttributeProvider", "HardcodeAttributeProvider", "node");
        tryRegister("me.vincentzz.falcon.node.VolumeProvider", "VolumeProvider", "node");
        tryRegister("me.vincentzz.falcon.node.VwapCalculator", "VwapCalculator", "node");
        tryRegister("me.vincentzz.falcon.node.MarkToMarketCalculator", "MarkToMarketCalculator", "node");
        tryRegister("me.vincentzz.falcon.ifo.FalconResourceId", "FalconResourceId", "resource");
        tryRegister("me.vincentzz.falcon.attribute.Ask", "Ask", "value");
        tryRegister("me.vincentzz.falcon.attribute.Bid", "Bid", "value");
        tryRegister("me.vincentzz.falcon.attribute.MidPrice", "MidPrice", "value");
        tryRegister("me.vincentzz.falcon.attribute.Spread", "Spread", "value");
        tryRegister("me.vincentzz.falcon.attribute.Volume", "Volume", "value");
        tryRegister("me.vincentzz.falcon.attribute.Vwap", "Vwap", "value");
        tryRegister("me.vincentzz.falcon.attribute.MarkToMarket", "MarkToMarket", "value");

        // Try to register test types
        tryRegister("me.vincentzz.graph.BasicResourceIdentifier", "BasicResourceIdentifier", "resource");
    }

    @SuppressWarnings("unchecked")
    private static void tryRegister(String className, String simpleName, String kind) {
        try {
            Class<?> clazz = Class.forName(className);
            switch (kind) {
                case "node" -> NODE_TYPES.put(simpleName, (Class<? extends CalculationNode>) clazz);
                case "resource" -> RESOURCE_TYPES.put(simpleName, (Class<? extends ResourceIdentifier>) clazz);
                case "value" -> VALUE_TYPES.put(simpleName, clazz);
            }
        } catch (ClassNotFoundException e) {
            // Not on classpath, skip
        }
    }

    public static void registerNodeType(String typeName, Class<? extends CalculationNode> nodeClass) {
        NODE_TYPES.put(typeName, nodeClass);
    }

    public static void registerResourceType(String typeName, Class<? extends ResourceIdentifier> resourceClass) {
        RESOURCE_TYPES.put(typeName, resourceClass);
    }

    public static void registerValueType(String typeName, Class<?> valueClass) {
        VALUE_TYPES.put(typeName, valueClass);
    }

    public static void registerClassAlias(String simpleName, Class<?> clazz) {
        CLASS_ALIASES.put(simpleName, clazz);
    }

    public static Class<? extends CalculationNode> getNodeClass(String typeName) {
        Class<? extends CalculationNode> clazz = NODE_TYPES.get(typeName);
        if (clazz == null) {
            throw new IllegalArgumentException("Unknown node type: " + typeName);
        }
        return clazz;
    }

    public static Class<? extends ResourceIdentifier> getResourceClass(String typeName) {
        Class<? extends ResourceIdentifier> clazz = RESOURCE_TYPES.get(typeName);
        if (clazz == null) {
            throw new IllegalArgumentException("Unknown resource type: " + typeName);
        }
        return clazz;
    }

    public static Class<?> getValueClass(String typeName) {
        return VALUE_TYPES.get(typeName);
    }

    public static boolean isResourceType(String typeName) {
        return RESOURCE_TYPES.containsKey(typeName);
    }

    /**
     * Resolve a simple class name to a Class object.
     * Checks aliases first, then value types, then tries Class.forName.
     */
    public static Class<?> resolveClass(String simpleName) {
        Class<?> clazz = CLASS_ALIASES.get(simpleName);
        if (clazz != null) return clazz;

        clazz = VALUE_TYPES.get(simpleName);
        if (clazz != null) return clazz;

        // Try common packages
        String[] packages = {
                "me.vincentzz.falcon.attribute.",
                "java.lang.",
                "java.math.",
                "java.time."
        };
        for (String pkg : packages) {
            try {
                return Class.forName(pkg + simpleName);
            } catch (ClassNotFoundException e) {
                // Try next
            }
        }

        // Try as fully qualified
        try {
            return Class.forName(simpleName);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Cannot resolve class: " + simpleName);
        }
    }

    /**
     * Create a CalculationNode from a type name and parameter map.
     * For NodeGroups, handled by ConstructionalJsonDeserializer directly.
     * For AtomicNodes, uses record constructor matching.
     */
    public static CalculationNode createNode(String typeName, Map<String, Object> parameters) {
        Class<? extends CalculationNode> nodeClass = getNodeClass(typeName);

        if (!nodeClass.isRecord()) {
            throw new IllegalArgumentException("AtomicNode type must be a record: " + typeName);
        }

        return (CalculationNode) constructRecord(nodeClass, parameters);
    }

    /**
     * Create a ResourceIdentifier from a type name and a JsonNode data object.
     */
    public static ResourceIdentifier createResourceFromData(String typeName,
                                                            Class<? extends ResourceIdentifier> resourceClass,
                                                            JsonNode dataNode) {
        if (!resourceClass.isRecord()) {
            throw new IllegalArgumentException("ResourceIdentifier must be a record: " + typeName);
        }

        RecordComponent[] components = resourceClass.getRecordComponents();
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            String fieldName = components[i].getName();
            Class<?> fieldType = components[i].getType();
            JsonNode fieldNode = dataNode.get(fieldName);
            args[i] = ResultJsonDeserializer.deserializeFieldValue(fieldNode, fieldType);
        }

        try {
            Constructor<?> constructor = resourceClass.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            return (ResourceIdentifier) constructor.newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create " + typeName, e);
        }
    }

    /**
     * Construct a record from a parameter map, matching by record component names.
     */
    private static Object constructRecord(Class<?> recordClass, Map<String, Object> parameters) {
        RecordComponent[] components = recordClass.getRecordComponents();
        Object[] args = new Object[components.length];

        for (int i = 0; i < components.length; i++) {
            String name = components[i].getName();
            Class<?> type = components[i].getType();
            Object value = parameters.get(name);
            args[i] = coerceValue(value, type);
        }

        try {
            Constructor<?> constructor = recordClass.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to construct record " + recordClass.getSimpleName(), e);
        }
    }

    /**
     * Coerce a deserialized value to the target type.
     */
    private static Object coerceValue(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isAssignableFrom(value.getClass())) return value;

        // Number coercions
        if (value instanceof Number number) {
            if (targetType == int.class || targetType == Integer.class) return number.intValue();
            if (targetType == long.class || targetType == Long.class) return number.longValue();
            if (targetType == double.class || targetType == Double.class) return number.doubleValue();
            if (targetType == float.class || targetType == Float.class) return number.floatValue();
            if (targetType == java.math.BigDecimal.class) return new java.math.BigDecimal(number.toString());
        }

        // String to various types
        if (value instanceof String str) {
            if (targetType == java.time.Instant.class) return java.time.Instant.parse(str);
            if (targetType == java.math.BigDecimal.class) return new java.math.BigDecimal(str);
            if (targetType == Class.class) return resolveClass(str);
        }

        return value;
    }

    public static Set<String> getRegisteredNodeTypes() {
        return new HashSet<>(NODE_TYPES.keySet());
    }

    public static Set<String> getRegisteredResourceTypes() {
        return new HashSet<>(RESOURCE_TYPES.keySet());
    }
}
