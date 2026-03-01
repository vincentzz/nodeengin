package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.lang.PathUtils;

import java.io.IOException;

/**
 * JSON serializer for ConnectionPoint.
 * Produces: {"nodePath": "/root/node", "rid": {"type": "FalconRawTopic", "data": {...}}}
 */
public class ConnectionPointJsonSerializer extends JsonSerializer<ConnectionPoint> {

    @Override
    public void serialize(ConnectionPoint cp, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("nodePath", PathUtils.toUnixString(cp.nodePath()));
        gen.writeFieldName("rid");
        serializers.findValueSerializer(ResourceIdentifier.class)
                .serialize(cp.rid(), gen, serializers);
        gen.writeEndObject();
    }
}
