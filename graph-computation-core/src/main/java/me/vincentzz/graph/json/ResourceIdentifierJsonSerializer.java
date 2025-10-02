package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import me.vincentzz.graph.model.ResourceIdentifier;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Dedicated JSON serializer for ResourceIdentifier objects.
 * Uses reflection to serialize all fields in structured format.
 */
public class ResourceIdentifierJsonSerializer extends JsonSerializer<ResourceIdentifier> {
    
    @Override
    public void serialize(ResourceIdentifier rid, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("type", rid.getClass().getSimpleName());
        gen.writeObjectFieldStart("data");
        
        // Use reflection to serialize all fields
        serializeObjectFields(rid, gen);
        
        gen.writeEndObject();
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
