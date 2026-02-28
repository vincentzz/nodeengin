package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import me.vincentzz.graph.model.ResourceIdentifier;

import java.io.IOException;
import java.lang.reflect.RecordComponent;

/**
 * JSON serializer for ResourceIdentifier.
 * Produces: {"type": "FalconResourceId", "data": {"ifo": ..., "source": ..., "attribute": ...}}
 */
public class ResourceIdentifierJsonSerializer extends JsonSerializer<ResourceIdentifier> {

    @Override
    public void serialize(ResourceIdentifier rid, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("type", rid.getClass().getSimpleName());
        gen.writeObjectFieldStart("data");
        writeRecordFields(rid, gen);
        gen.writeEndObject();
        gen.writeEndObject();
    }

    static void writeRecordFields(Object obj, JsonGenerator gen) throws IOException {
        Class<?> clazz = obj.getClass();
        if (!clazz.isRecord()) {
            throw new IOException("Expected a record type but got: " + clazz.getName());
        }
        for (RecordComponent component : clazz.getRecordComponents()) {
            try {
                var accessor = component.getAccessor();
                accessor.setAccessible(true);
                Object value = accessor.invoke(obj);
                gen.writeFieldName(component.getName());
                writeFieldValue(value, gen);
            } catch (ReflectiveOperationException e) {
                throw new IOException("Failed to access record component: " + component.getName(), e);
            }
        }
    }

    static void writeFieldValue(Object value, JsonGenerator gen) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else if (value instanceof String s) {
            gen.writeString(s);
        } else if (value instanceof Integer i) {
            gen.writeNumber(i);
        } else if (value instanceof Long l) {
            gen.writeNumber(l);
        } else if (value instanceof Double d) {
            gen.writeNumber(d);
        } else if (value instanceof Float f) {
            gen.writeNumber(f);
        } else if (value instanceof Boolean b) {
            gen.writeBoolean(b);
        } else if (value instanceof java.math.BigDecimal bd) {
            gen.writeNumber(bd);
        } else if (value instanceof java.time.Instant instant) {
            gen.writeString(instant.toString());
        } else if (value instanceof Class<?> c) {
            gen.writeString(c.getSimpleName());
        } else {
            gen.writeString(value.toString());
        }
    }
}
