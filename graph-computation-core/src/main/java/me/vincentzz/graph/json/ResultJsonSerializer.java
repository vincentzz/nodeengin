package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import me.vincentzz.lang.Result.Result;
import me.vincentzz.lang.Result.Success;
import me.vincentzz.lang.Result.Failure;

import java.io.IOException;

/**
 * JSON serializer for Result<T> types.
 * Serializes Success and Failure variants with type information.
 */
public class ResultJsonSerializer extends JsonSerializer<Result<?>> {
    
    @Override
    public void serialize(Result<?> result, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        if (result instanceof Success<?> success) {
            gen.writeFieldName("success");
            writeJsonValue(success.get(), gen, serializers);
        } else if (result instanceof Failure<?> failure) {
            gen.writeFieldName("failure");
            // For failure, just write the error message as a string
            Exception exception = failure.getException();
            gen.writeString(exception != null ? exception.getMessage() : "Unknown error");
        }
        
        gen.writeEndObject();
    }
    
    @SuppressWarnings("unchecked")
    private void writeJsonValue(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
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
        } else if (value instanceof java.math.BigDecimal) {
            gen.writeNumber((java.math.BigDecimal) value);
        } else if (value instanceof java.time.Instant) {
            gen.writeString(value.toString());
        } else if (value instanceof java.time.LocalDateTime) {
            gen.writeString(value.toString());
        } else if (value instanceof java.time.LocalDate) {
            gen.writeString(value.toString());
        } else if (value instanceof java.util.UUID) {
            gen.writeString(value.toString());
        } else {
            // For complex objects, serialize with type information for proper reconstruction
            gen.writeStartObject();
            gen.writeStringField("type", value.getClass().getSimpleName());
            gen.writeFieldName("data");
            
            try {
                // First try to extract data manually using reflection for records
                gen.writeStartObject();
                boolean foundFields = false;
                
                // Check if this is a Java record by looking for record components
                try {
                    java.lang.reflect.RecordComponent[] recordComponents = value.getClass().getRecordComponents();
                    if (recordComponents != null && recordComponents.length > 0) {
                        // This is a Java record
                        for (java.lang.reflect.RecordComponent component : recordComponents) {
                            try {
                                java.lang.reflect.Method accessor = component.getAccessor();
                                Object fieldValue = accessor.invoke(value);
                                gen.writeFieldName(component.getName());
                                writeJsonValue(fieldValue, gen, serializers);
                                foundFields = true;
                            } catch (Exception e) {
                                // Skip this component if it fails
                            }
                        }
                    }
                } catch (Exception e) {
                    // Not a record or reflection failed, try traditional method approach
                }
                
                // If no record components found, try traditional methods
                if (!foundFields) {
                    java.lang.reflect.Method[] methods = value.getClass().getDeclaredMethods();
                    for (java.lang.reflect.Method method : methods) {
                        // Look for accessor methods (no parameters, returns something, not static)
                        if (method.getParameterCount() == 0 && 
                            method.getReturnType() != void.class &&
                            !java.lang.reflect.Modifier.isStatic(method.getModifiers()) &&
                            !method.getName().equals("toString") &&
                            !method.getName().equals("hashCode") &&
                            !method.getName().equals("getClass") &&
                            !method.getName().equals("equals")) {
                            
                            try {
                                Object fieldValue = method.invoke(value);
                                gen.writeFieldName(method.getName());
                                writeJsonValue(fieldValue, gen, serializers);
                                foundFields = true;
                            } catch (Exception ignored) {
                                // Skip fields that can't be accessed
                            }
                        }
                    }
                }
                
                // If still no fields found, fall back to string representation
                if (!foundFields) {
                    gen.writeStringField("value", value.toString());
                }
                
                gen.writeEndObject();
            } catch (Exception fallbackException) {
                // Ultimate fallback: serialize as string representation
                gen.writeString(value.toString());
            }
            
            gen.writeEndObject();
        }
    }
}
