package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.graph.node.NodeGroup;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Constructional JSON serializer for CalculationNodes.
 * Outputs JSON format suitable for reconstruction using NodeTypeRegistry.
 * Uses getConstructionParameters() method for bi-directional serialization.
 */
public class ConstructionalJsonSerializer extends JsonSerializer<CalculationNode> {
    
    private SerializerProvider serializerProvider;
    
    @Override
    public void serialize(CalculationNode node, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        this.serializerProvider = serializers; // Store for nested serialization
        
        gen.writeStartObject();
        
        // Write type (class name)
        gen.writeStringField("type", node.getClass().getSimpleName());
        
        // Write parameters based on node type
        if (node instanceof NodeGroup) {
            // NodeGroup uses "parameter" (singular)
            gen.writeObjectFieldStart("parameter");
            serializeNodeGroupParameter((NodeGroup) node, gen);
            gen.writeEndObject();
        } else {
            // AtomicNode uses "parameters" (plural) as 2D array  
            gen.writeArrayFieldStart("parameters");
            serializeAtomicNodeParameters(node, gen);
            gen.writeEndArray();
        }
        
        gen.writeEndObject();
    }
    
    @SuppressWarnings("unchecked")
    private void serializeNodeGroupParameter(NodeGroup nodeGroup, JsonGenerator gen) throws IOException {
        // Write name directly
        gen.writeStringField("name", nodeGroup.name());
        
        // Write nodes array
        gen.writeArrayFieldStart("nodes");
        
        // Process each child node individually
        // NodeGroups cannot be batched and must each be their own entry
        // AtomicNodes of the same type can be batched together
        Set<CalculationNode> childNodes = nodeGroup.nodes();
        
        // Separate NodeGroups from AtomicNodes
        // Use class name check as backup since instanceof might fail for nested nodes
        List<NodeGroup> childNodeGroups = childNodes.stream()
            .filter(node -> node instanceof NodeGroup || "NodeGroup".equals(node.getClass().getSimpleName()))
            .map(node -> (NodeGroup) node)
            .collect(Collectors.toList());
            
        Map<String, List<CalculationNode>> atomicNodesByType = childNodes.stream()
            .filter(node -> !(node instanceof NodeGroup) && !"NodeGroup".equals(node.getClass().getSimpleName()))
            .collect(Collectors.groupingBy(node -> node.getClass().getSimpleName()));
        
        // First, serialize each NodeGroup individually (cannot be batched)
        for (NodeGroup childNodeGroup : childNodeGroups) {
            gen.writeStartObject();
            gen.writeStringField("type", "NodeGroup");
            gen.writeObjectFieldStart("parameter");
            serializeNodeGroupParameter(childNodeGroup, gen);
            gen.writeEndObject();
            gen.writeEndObject();
        }
        
        // Then, serialize AtomicNodes by type (can be batched)  
        for (Map.Entry<String, List<CalculationNode>> entry : atomicNodesByType.entrySet()) {
            String nodeType = entry.getKey();
            List<CalculationNode> nodesOfType = entry.getValue();
            
            gen.writeStartObject();
            gen.writeStringField("type", nodeType);
            gen.writeArrayFieldStart("parameters");
            for (CalculationNode childNode : nodesOfType) {
                // Each child node contributes one parameter array
                serializeNodeConstructionParameters(childNode, gen);
            }
            gen.writeEndArray();
            gen.writeEndObject();
        }
        
        gen.writeEndArray();
        
        // Write flywires
        gen.writeArrayFieldStart("flywires");
        for (var flywire : nodeGroup.flywires()) {
            gen.writeStartObject();
            
            gen.writeObjectFieldStart("source");
            gen.writeStringField("nodePath", flywire.source().nodePath().toString());
            gen.writeFieldName("resourceId");
            writeJsonValue(flywire.source().rid(), gen);
            gen.writeEndObject();
            
            gen.writeObjectFieldStart("target");
            gen.writeStringField("nodePath", flywire.target().nodePath().toString());
            gen.writeFieldName("resourceId");
            writeJsonValue(flywire.target().rid(), gen);
            gen.writeEndObject();
            
            gen.writeEndObject();
        }
        gen.writeEndArray();
        
        // Write exports (scope)
        if (nodeGroup.exports() != null) {
            gen.writeObjectFieldStart("exports");
            gen.writeStringField("type", nodeGroup.exports().getClass().getSimpleName());
            
            gen.writeArrayFieldStart("values");
            // Serialize scope values using reflection if available
            try {
                Method getValuesMethod = nodeGroup.exports().getClass().getMethod("resources");
                Object values = getValuesMethod.invoke(nodeGroup.exports());
                if (values instanceof Collection<?> collection) {
                    for (Object value : collection) {
                        serializeScopeValue(value, gen);
                    }
                }
            } catch (Exception e) {
                // If reflection fails, just create empty array
            }
            gen.writeEndArray();
            
            gen.writeEndObject();
        }
    }
    
    private void serializeAtomicNodeParameters(CalculationNode node, JsonGenerator gen) throws IOException {
        // For atomic nodes, write a single parameter array (representing this instance)
        serializeNodeConstructionParameters(node, gen);
    }
    
    private void serializeNodeConstructionParameters(CalculationNode node, JsonGenerator gen) throws IOException {
        // For atomic nodes, serialize as structured object instead of using getConstructionParameters()
        gen.writeStartObject();
        serializeObjectFields(node, gen);
        gen.writeEndObject();
    }
    
    private void serializeResourceIdentifier(ResourceIdentifier rid, JsonGenerator gen) throws IOException {
        gen.writeStringField("type", rid.getClass().getSimpleName());
        gen.writeObjectFieldStart("data");
        
        // Use reflection to serialize all fields
        serializeObjectFields(rid, gen);
        
        gen.writeEndObject();
    }
    
    private void serializeScopeValue(Object scopeValue, JsonGenerator gen) throws IOException {
        gen.writeStartObject();
        
        try {
            // Try to extract nodePath and rid using reflection (ConnectionPoint methods)
            Method getNodePathMethod = scopeValue.getClass().getMethod("nodePath");
            Method getRidMethod = scopeValue.getClass().getMethod("rid");
            
            Object nodePathObj = getNodePathMethod.invoke(scopeValue);
            ResourceIdentifier resourceIdentifier = (ResourceIdentifier) getRidMethod.invoke(scopeValue);
            
            // Convert Path to String
            String nodePath = nodePathObj.toString();
            
            gen.writeStringField("nodePath", nodePath);
            
            gen.writeObjectFieldStart("resourceId");
            // Serialize ResourceIdentifier manually to avoid object nesting issues
            gen.writeStringField("type", resourceIdentifier.getClass().getSimpleName());
            gen.writeObjectFieldStart("data");
            serializeObjectFields(resourceIdentifier, gen);
            gen.writeEndObject();
            gen.writeEndObject();
            
        } catch (Exception e) {
            // Fallback: write as string
            gen.writeStringField("value", scopeValue.toString());
        }
        
        gen.writeEndObject();
    }
    
    @SuppressWarnings("unchecked")
    private void writeJsonValue(Object value, JsonGenerator gen) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else if (value instanceof String) {
            gen.writeString((String) value);
        } else if (value instanceof Integer) {
            gen.writeNumber((Integer) value);
        } else if (value instanceof Double) {
            gen.writeNumber((Double) value);
        } else if (value instanceof Float) {
            gen.writeNumber((Float) value);
        } else if (value instanceof Long) {
            gen.writeNumber((Long) value);
        } else if (value instanceof Boolean) {
            gen.writeBoolean((Boolean) value);
        } else if (value instanceof Class<?>) {
            // Handle Class objects - serialize just the simple name
            gen.writeString(((Class<?>) value).getSimpleName());
        } else if (value instanceof java.math.BigDecimal) {
            // Handle BigDecimal - serialize as number
            gen.writeNumber((java.math.BigDecimal) value);
        } else if (value instanceof java.time.Instant) {
            // Handle Instant - serialize as ISO string
            gen.writeString(value.toString());
        } else if (value instanceof java.time.LocalDateTime) {
            // Handle LocalDateTime - serialize as ISO string
            gen.writeString(value.toString());
        } else if (value instanceof java.time.LocalDate) {
            // Handle LocalDate - serialize as ISO string
            gen.writeString(value.toString());
        } else if (value instanceof java.util.UUID) {
            // Handle UUID - serialize as string
            gen.writeString(value.toString());
        } else if (value instanceof CalculationNode) {
            // Use the registered CalculationNode serializer (this same serializer) for nested nodes
            if (serializerProvider != null) {
                try {
                    JsonSerializer<Object> serializer = serializerProvider.findValueSerializer(value.getClass());
                    serializer.serialize(value, gen, serializerProvider);
                    return;
                } catch (Exception e) {
                    // Fall back to custom serialization if delegation fails
                    System.err.println("DEBUG: Failed to delegate CalculationNode serialization: " + e.getMessage());
                }
            }
            // Fallback - should not reach here normally
            System.err.println("DEBUG: Fallback serialization for CalculationNode: " + value.getClass().getSimpleName());
            gen.writeString("ERROR: Failed to serialize CalculationNode: " + value.toString());
        } else if (value instanceof ResourceIdentifier) {
            // Use the registered ResourceIdentifier serializer instead of custom logic
            if (serializerProvider != null) {
                try {
                    JsonSerializer<Object> serializer = serializerProvider.findValueSerializer(ResourceIdentifier.class);
                    serializer.serialize(value, gen, serializerProvider);
                    return;
                } catch (Exception e) {
                    System.err.println("DEBUG: Failed to delegate ResourceIdentifier serialization: " + e.getMessage());
                    e.printStackTrace();
                    // Fall back to custom serialization if delegation fails
                }
            }
            // Fallback to custom serialization using ResourceIdentifier format
            gen.writeStartObject();
            gen.writeStringField("type", value.getClass().getSimpleName());
            gen.writeObjectFieldStart("data");
            serializeObjectFields(value, gen);
            gen.writeEndObject();
            gen.writeEndObject();
        } else if (value instanceof Map) {
            // Serialize maps as JSON objects
            Map<String, Object> map = (Map<String, Object>) value;
            gen.writeStartObject();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                gen.writeFieldName(entry.getKey());
                writeJsonValue(entry.getValue(), gen);
            }
            gen.writeEndObject();
        } else if (value instanceof List) {
            // Serialize lists as JSON arrays
            List<?> list = (List<?>) value;
            gen.writeStartArray();
            for (Object item : list) {
                writeJsonValue(item, gen);
            }
            gen.writeEndArray();
        } else if (value instanceof Collection) {
            // Serialize other collections as JSON arrays
            Collection<?> collection = (Collection<?>) value;
            gen.writeStartArray();
            for (Object item : collection) {
                writeJsonValue(item, gen);
            }
            gen.writeEndArray();
        } else if (value instanceof Set) {
            // Serialize sets as JSON arrays (special case for Set<CalculationNode>)
            Set<?> set = (Set<?>) value;
            gen.writeStartArray();
            for (Object item : set) {
                writeJsonValue(item, gen);
            }
            gen.writeEndArray();
        } else {
            // For complex objects, serialize with type and data structure
            gen.writeStartObject();
            gen.writeStringField("type", value.getClass().getSimpleName());
            gen.writeObjectFieldStart("data");
            serializeObjectFields(value, gen);
            gen.writeEndObject();
            gen.writeEndObject();
        }
    }
    
    private void serializeObjectFields(Object obj, JsonGenerator gen) throws IOException {
        try {
            Class<?> clazz = obj.getClass();
            
            // For records, prefer record components to avoid field duplication
            if (clazz.isRecord()) {
                java.lang.reflect.RecordComponent[] components = clazz.getRecordComponents();
                for (java.lang.reflect.RecordComponent component : components) {
                    try {
                        Method accessor = component.getAccessor();
                        Object value = accessor.invoke(obj);
                        gen.writeFieldName(component.getName());
                        writeJsonValue(value, gen);
                    } catch (Exception e) {
                        // Skip if we can't access
                    }
                }
            } else {
                // For regular classes, use field reflection with getters
                java.lang.reflect.Field[] fields = clazz.getDeclaredFields();
                for (java.lang.reflect.Field field : fields) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        continue; // Skip static fields
                    }
                    
                    try {
                        // Try to find a getter method
                        String fieldName = field.getName();
                        String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                        
                        try {
                            Method getter = clazz.getMethod(getterName);
                            Object fieldValue = getter.invoke(obj);
                            gen.writeFieldName(fieldName);
                            writeJsonValue(fieldValue, gen);
                        } catch (NoSuchMethodException e) {
                            // Try direct field access if no getter
                            field.setAccessible(true);
                            Object fieldValue = field.get(obj);
                            gen.writeFieldName(fieldName);
                            writeJsonValue(fieldValue, gen);
                        }
                    } catch (Exception e) {
                        // Skip this field if we can't access it
                    }
                }
            }
        } catch (Exception e) {
            // If all else fails, just write empty object
        }
    }
}
