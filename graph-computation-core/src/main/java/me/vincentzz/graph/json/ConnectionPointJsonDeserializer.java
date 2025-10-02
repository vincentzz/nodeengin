package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.node.ConnectionPoint;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * JSON deserializer for ConnectionPoint.
 * Deserializes ConnectionPoint from structured object with nodePath and resourceId.
 */
public class ConnectionPointJsonDeserializer extends JsonDeserializer<ConnectionPoint> {
    
    @Override
    public ConnectionPoint deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        JsonNode node = p.getCodec().readTree(p);
        
        // Deserialize nodePath from string
        String nodePathString = node.get("nodePath").asText();
        Path nodePath = Paths.get(nodePathString);
        
        // Deserialize resourceId
        JsonNode resourceIdNode = node.get("resourceId");
        ResourceIdentifier resourceId = deserializeResourceIdentifier(resourceIdNode, p, ctxt);
        
        return ConnectionPoint.of(nodePath, resourceId);
    }
    
    private ResourceIdentifier deserializeResourceIdentifier(JsonNode node, JsonParser p, DeserializationContext ctxt) throws IOException {
        try {
            // Try to use registered ResourceIdentifier deserializer
            JsonParser nodeParser = node.traverse(p.getCodec());
            nodeParser.nextToken();
            return ctxt.readValue(nodeParser, ResourceIdentifier.class);
        } catch (Exception e) {
            // Fallback: try to handle simple type/data format
            if (node.has("type") && node.has("data")) {
                String type = node.get("type").asText();
                
                // Try to get the resource type from registry
                try {
                    Class<? extends ResourceIdentifier> resourceClass = NodeTypeRegistry.getResourceClass(type);
                    if (resourceClass != null) {
                        JsonNode dataNode = node.get("data");
                        JsonParser dataParser = dataNode.traverse(p.getCodec());
                        dataParser.nextToken();
                        return ctxt.readValue(dataParser, resourceClass);
                    }
                } catch (Exception ex) {
                    // Continue to fallback
                }
                
                // Handle FalconResourceId specifically using reflection
                System.err.println("DEBUG CONN: Trying to deserialize ResourceIdentifier: " + type);
                try {
                    JsonNode dataNode = node.get("data");
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> dataMap = mapper.convertValue(dataNode, Map.class);
                    System.err.println("DEBUG CONN: Data map: " + dataMap);
                    
                    if ("FalconResourceId".equals(type)) {
                        // Handle FalconResourceId specifically - use the of() method
                        String ifo = (String) dataMap.get("ifo");
                        String source = (String) dataMap.get("source");
                        String attribute = (String) dataMap.get("attribute");
                        
                        System.err.println("DEBUG CONN: ifo=" + ifo + ", source=" + source + ", attribute=" + attribute);
                        
                        try {
                            Class<?> attributeClass = Class.forName("me.vincentzz.falcon.attribute." + attribute);
                            Class<?> falconResourceIdClass = Class.forName("me.vincentzz.falcon.ifo.FalconResourceId");
                            java.lang.reflect.Method ofMethod = falconResourceIdClass.getMethod("of", String.class, String.class, Class.class);
                            ResourceIdentifier result = (ResourceIdentifier) ofMethod.invoke(null, ifo, source, attributeClass);
                            System.err.println("DEBUG CONN: Successfully created FalconResourceId: " + result);
                            return result;
                        } catch (Exception reflectionEx) {
                            System.err.println("DEBUG CONN: Failed to create FalconResourceId: " + reflectionEx.getMessage());
                            throw new IllegalArgumentException("Unable to create FalconResourceId", reflectionEx);
                        }
                    } else {
                        throw new IllegalArgumentException("Unsupported ResourceIdentifier type: " + type);
                    }
                } catch (Exception ex) {
                    System.err.println("DEBUG CONN: Failed to create ResourceIdentifier: " + ex.getMessage());
                    ex.printStackTrace();
                    throw new IllegalArgumentException("Unable to deserialize ResourceIdentifier of type: " + type, ex);
                }
            }
            
            throw new IllegalArgumentException("Unable to deserialize ResourceIdentifier from: " + node.toString());
        }
    }
}
