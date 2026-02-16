package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import me.vincentzz.graph.node.CalculationNode;

import java.io.IOException;
import java.util.*;

/**
 * Constructional JSON deserializer for CalculationNodes.
 * Uses NodeTypeRegistry to reconstruct nodes from JSON.
 */
public class ConstructionalJsonDeserializer extends JsonDeserializer<CalculationNode> {
    
    @Override
    public CalculationNode deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        JsonNode node = p.getCodec().readTree(p);
        return parseCalculationNode(node);
    }
    
    private CalculationNode parseCalculationNode(JsonNode node) {
        String type = node.get("type").asText();

        if ("NodeGroup".equals(type)) {
            // Try "parameter" (singular) first - this is the correct format
            JsonNode parameterNode = node.get("parameter");
            if (parameterNode != null) {
                try {
                    Map<String, Object> parameters = parseNodeGroupParameters(parameterNode);
                    CalculationNode result = NodeTypeRegistry.createNode(type, parameters);
                    return result;
                } catch (Exception e) {
                    throw e;
                }
            }
            
            // Handle malformed "parameters" (plural) format for nested NodeGroups
            JsonNode parametersNode = node.get("parameters");
            if (parametersNode != null && parametersNode.isArray() && parametersNode.size() >= 1) {
                // This is an incorrect format where multiple NodeGroups are batched together
                // We can only handle the first one and warn about the issue
                JsonNode firstParameter = parametersNode.get(0);
                Map<String, Object> parameters = parseNodeGroupParameters(firstParameter);
                
                if (parametersNode.size() > 1) {
                    System.err.println("WARNING: Multiple NodeGroups found in 'parameters' array. Only processing the first one. This indicates a serialization issue.");
                }
                
                return NodeTypeRegistry.createNode(type, parameters);
            }
            
            throw new RuntimeException("Invalid NodeGroup format: missing 'parameter' field");
        } else {
            JsonNode parametersNode = node.get("parameters");
            try {
                Map<String, Object> parameters = parseParameterMap(parametersNode);
                CalculationNode result = NodeTypeRegistry.createNode(type, parameters);
                return result;
            } catch (Exception e) {
                throw e;
            }
        }
    }
    
    private Map<String, Object> parseNodeGroupParameters(JsonNode parameterNode) {
        Map<String, Object> parameters = new HashMap<>();
        
        // Parse name
        parameters.put("name", parameterNode.get("name").asText());
        
        // Parse nodes
        JsonNode nodesArray = parameterNode.get("nodes");
        if (nodesArray != null && nodesArray.isArray()) {
            List<Map<String, Object>> nodesList = new ArrayList<>();

            for (JsonNode nodeSpec : nodesArray) {
                String nodeType = nodeSpec.get("type").asText();
                
                if ("NodeGroup".equals(nodeType)) {
                    // Handle NodeGroup nodes - they use "parameter" (singular), not "parameters" (plural)
                    JsonNode nodeParameterSingular = nodeSpec.get("parameter");
                    if (nodeParameterSingular != null) {
                        Map<String, Object> nodeGroupInfo = new HashMap<>();
                        nodeGroupInfo.put("type", "NodeGroup");
                        nodeGroupInfo.put("parameter", parseNodeGroupParameters(nodeParameterSingular));
                        nodesList.add(nodeGroupInfo);
                    } else {
                        // Fallback: check for malformed "parameters" (plural) format
                        JsonNode nodeParametersArray = nodeSpec.get("parameters");
                        if (nodeParametersArray != null && nodeParametersArray.isArray()) {
                            // Malformed format: multiple NodeGroups batched together in parameters array
                            for (JsonNode parameterSet : nodeParametersArray) {
                                Map<String, Object> nodeGroupInfo = new HashMap<>();
                                nodeGroupInfo.put("type", "NodeGroup");
                                nodeGroupInfo.put("parameter", parseNodeGroupParameters(parameterSet));
                                nodesList.add(nodeGroupInfo);
                                System.err.println("WARNING: Found malformed NodeGroup in 'parameters' array during parsing. Fixed automatically but this indicates a serialization issue.");
                            }
                        } else {
                            throw new RuntimeException("NodeGroup missing parameter field(s)");
                        }
                    }
                } else {
                    // Handle atomic nodes normally
                    JsonNode nodeParametersArray = nodeSpec.get("parameters");
                    if (nodeParametersArray != null && nodeParametersArray.isArray()) {
                        List<Map<String, Object>> parametersList = new ArrayList<>();
                        for (JsonNode parameterSet : nodeParametersArray) {
                            parametersList.add(parseParameterMap(parameterSet));
                        }
                        Map<String, Object> nodeInfo = new HashMap<>();
                        nodeInfo.put("type", nodeType);
                        nodeInfo.put("parameters", parametersList);  // This should be a List
                        nodesList.add(nodeInfo);
                    } else {
                        throw new RuntimeException("Atomic node " + nodeType + " missing parameters field");
                    }
                }
            }
            
            parameters.put("nodes", nodesList);
        }
        
        // Parse flywires
        JsonNode flywiresArray = parameterNode.get("flywires");
        if (flywiresArray != null && flywiresArray.isArray()) {
            List<Map<String, Object>> flywiresList = new ArrayList<>();
            
            for (JsonNode flywireNode : flywiresArray) {
                Map<String, Object> flywireParams = new HashMap<>();
                flywireParams.put("source", parseConnectionPoint(flywireNode.get("source")));
                flywireParams.put("target", parseConnectionPoint(flywireNode.get("target")));
                flywiresList.add(flywireParams);
            }
            
            parameters.put("flywires", flywiresList);
        }
        
        // Parse exports
        JsonNode exportsNode = parameterNode.get("exports");
        if (exportsNode != null) {
            parameters.put("exports", parseScope(exportsNode));
        }
        
        return parameters;
    }
    
    private Map<String, Object> parseParameterMap(JsonNode parametersNode) {
        Map<String, Object> parameters = new HashMap<>();
        
        if (parametersNode != null && parametersNode.isObject()) {
            parametersNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode valueNode = entry.getValue();
                Object value = parseJsonValue(valueNode);
                parameters.put(key, value);
            });
        }
        
        return parameters;
    }
    
    private Object parseJsonValue(JsonNode valueNode) {
        if (valueNode.isTextual()) {
            return valueNode.asText();
        } else if (valueNode.isIntegralNumber()) {
            return valueNode.asInt();
        } else if (valueNode.isFloatingPointNumber()) {
            return valueNode.asDouble();
        } else if (valueNode.isBoolean()) {
            return valueNode.asBoolean();
        } else if (valueNode.isObject()) {
            // Check if it's a ResourceIdentifier
            if (valueNode.has("type") && valueNode.has("parameters")) {
                return parseResourceIdentifier(valueNode);
            } else {
                // Generic object parsing
                Map<String, Object> objectMap = new HashMap<>();
                valueNode.fields().forEachRemaining(entry -> {
                    objectMap.put(entry.getKey(), parseJsonValue(entry.getValue()));
                });
                return objectMap;
            }
        } else if (valueNode.isArray()) {
            List<Object> arrayList = new ArrayList<>();
            for (JsonNode element : valueNode) {
                arrayList.add(parseJsonValue(element));
            }
            return arrayList;
        } else {
            return valueNode.asText();
        }
    }
    
    private Map<String, Object> parseResourceIdentifier(JsonNode resourceNode) {
        Map<String, Object> resourceParams = new HashMap<>();
        resourceParams.put("type", resourceNode.get("type").asText());
        
        // Try "data" field first (new format from ConstructionalJsonSerializer)
        JsonNode dataNode = resourceNode.get("data");
        if (dataNode != null && dataNode.isObject()) {
            // Keep the data structure intact - don't flatten
            Map<String, Object> dataMap = new HashMap<>();
            dataNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode valueNode = entry.getValue();
                Object value = parseJsonValue(valueNode);
                dataMap.put(key, value);
            });
            resourceParams.put("data", dataMap);
        } else {
            // Fallback to legacy "parameters" array format
            JsonNode parametersArray = resourceNode.get("parameters");
            if (parametersArray != null && parametersArray.isArray()) {
                List<Object> paramsList = new ArrayList<>();
                for (JsonNode param : parametersArray) {
                    paramsList.add(parseJsonValue(param));
                }
                resourceParams.put("parameters", paramsList);
            }
        }
        
        return resourceParams;
    }
    
    private Map<String, Object> parseConnectionPoint(JsonNode connectionPointNode) {
        Map<String, Object> connectionPointParams = new HashMap<>();
        // Updated to use nodePath instead of nodeName for filesystem-like path resolution
        connectionPointParams.put("nodePath", connectionPointNode.get("nodePath").asText());
        connectionPointParams.put("resourceId", parseResourceIdentifier(connectionPointNode.get("resourceId")));
        return connectionPointParams;
    }
    
    private Map<String, Object> parseScope(JsonNode scopeNode) {
        Map<String, Object> scopeParams = new HashMap<>();
        scopeParams.put("type", scopeNode.get("type").asText());
        
        JsonNode valuesArray = scopeNode.get("values");
        if (valuesArray != null && valuesArray.isArray()) {
            List<Map<String, Object>> valuesList = new ArrayList<>();
            
            for (JsonNode valueNode : valuesArray) {
                Map<String, Object> valueParams = new HashMap<>();
                // Support both old format (nodeName) and new format (nodePath) for backward compatibility
                if (valueNode.has("nodePath")) {
                    valueParams.put("nodePath", valueNode.get("nodePath").asText());
                } else if (valueNode.has("nodeName")) {
                    // Legacy support - convert nodeName to nodePath
                    valueParams.put("nodePath", valueNode.get("nodeName").asText());
                } else {
                    throw new RuntimeException("ConnectionPoint missing both 'nodePath' and 'nodeName' fields");
                }
                valueParams.put("resourceId", parseResourceIdentifier(valueNode.get("resourceId")));
                valuesList.add(valueParams);
            }
            
            scopeParams.put("values", valuesList);
        }
        
        return scopeParams;
    }
}
