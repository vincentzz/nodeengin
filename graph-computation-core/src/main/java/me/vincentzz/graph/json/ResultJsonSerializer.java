package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import me.vincentzz.lang.Result.Failure;
import me.vincentzz.lang.Result.Result;
import me.vincentzz.lang.Result.Success;

import java.io.IOException;
import java.lang.reflect.RecordComponent;

/**
 * JSON serializer for Result&lt;T&gt;.
 * Produces: {"success": {"type": "Ask", "data": {...}}} or {"failure": "error message"}
 */
public class ResultJsonSerializer extends JsonSerializer<Result<?>> {

    @Override
    public void serialize(Result<?> result, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        if (result instanceof Success<?> success) {
            gen.writeFieldName("success");
            writeTypedValue(success.get(), gen);
        } else if (result instanceof Failure<?> failure) {
            gen.writeStringField("failure", failure.getException() != null
                    ? failure.getException().getMessage() : "Unknown error");
        }
        gen.writeEndObject();
    }

    /**
     * Write a value with type+data wrapper for complex objects,
     * or as a plain value for primitives.
     */
    static void writeTypedValue(Object value, JsonGenerator gen) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            ResourceIdentifierJsonSerializer.writeFieldValue(value, gen);
        } else if (value.getClass().isRecord()) {
            gen.writeStartObject();
            gen.writeStringField("type", value.getClass().getSimpleName());
            gen.writeObjectFieldStart("data");
            writeRecordDataFields(value, gen);
            gen.writeEndObject();
            gen.writeEndObject();
        } else {
            ResourceIdentifierJsonSerializer.writeFieldValue(value, gen);
        }
    }

    private static void writeRecordDataFields(Object obj, JsonGenerator gen) throws IOException {
        for (RecordComponent component : obj.getClass().getRecordComponents()) {
            try {
                var accessor = component.getAccessor();
                accessor.setAccessible(true);
                Object fieldValue = accessor.invoke(obj);
                gen.writeFieldName(component.getName());
                ResourceIdentifierJsonSerializer.writeFieldValue(fieldValue, gen);
            } catch (ReflectiveOperationException e) {
                throw new IOException("Failed to access record component: " + component.getName(), e);
            }
        }
    }
}
