package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.vincentzz.graph.model.ResourceIdentifier;

import java.io.IOException;
import java.util.Map;

/**
 * JSON deserializer for ResourceIdentifier.
 * Handles the type/data format used in EvaluationResult JSON files.
 */
public class ResourceIdentifierJsonDeserializer extends JsonDeserializer<ResourceIdentifier> {
    
    @Override
    public ResourceIdentifier deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        JsonNode node = p.getCodec().readTree(p);
        
        if (node.has("type") && node.has("data")) {
            String type = node.get("type").asText();

            if ("FalconResourceId".equals(type)) {
                JsonNode dataNode = node.get("data");
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> dataMap = mapper.convertValue(dataNode, Map.class);
                
                String ifo = (String) dataMap.get("ifo");
                String source = (String) dataMap.get("source");
                String attribute = (String) dataMap.get("attribute");
                
                try {
                    Class<?> attributeClass = Class.forName("me.vincentzz.falcon.attribute." + attribute);
                    Class<?> falconResourceIdClass = Class.forName("me.vincentzz.falcon.ifo.FalconResourceId");
                    java.lang.reflect.Method ofMethod = falconResourceIdClass.getMethod("of", String.class, String.class, Class.class);
                    ResourceIdentifier result = (ResourceIdentifier) ofMethod.invoke(null, ifo, source, attributeClass);
                    return result;
                } catch (Exception e) {
                    throw new IllegalArgumentException("Unable to create FalconResourceId", e);
                }
            } else {
                throw new IllegalArgumentException("Unsupported ResourceIdentifier type: " + type);
            }
        } else {
            throw new IllegalArgumentException("Invalid ResourceIdentifier JSON: must have 'type' and 'data' fields");
        }
    }
}
