package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.node.Flywire;
import me.vincentzz.graph.node.NodeGroup;
import me.vincentzz.graph.scope.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Constructional JSON deserializer for CalculationNodes (Graph Definition schema).
 *
 * Consumes the new format where:
 * - NodeGroup: {"type":"NodeGroup","name":"root","nodes":[...],"flywires":[...],"exports":{"exclude":[]}}
 * - AtomicNode: {"type":"MidSpreadCalculator","params":[{"symbol":"GOOGLE","source":"FALCON"}, ...]}
 */
public class ConstructionalJsonDeserializer extends JsonDeserializer<CalculationNode> {

    @Override
    public CalculationNode deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        return parseNode(node);
    }

    private CalculationNode parseNode(JsonNode node) {
        String type = node.get("type").asText();

        if ("NodeGroup".equals(type)) {
            return parseNodeGroup(node);
        } else {
            // AtomicNode with "params" batching — returns only the first instance
            // (top-level single atomic node is a batch of one)
            return parseAtomicNodeBatch(type, node).get(0);
        }
    }

    private NodeGroup parseNodeGroup(JsonNode node) {
        String name = node.get("name").asText();

        // Parse child nodes
        Set<CalculationNode> children = new HashSet<>();
        JsonNode nodesArray = node.get("nodes");
        if (nodesArray != null && nodesArray.isArray()) {
            for (JsonNode childNode : nodesArray) {
                String childType = childNode.get("type").asText();
                if ("NodeGroup".equals(childType)) {
                    children.add(parseNodeGroup(childNode));
                } else {
                    // AtomicNode batch — may produce multiple nodes
                    children.addAll(parseAtomicNodeBatch(childType, childNode));
                }
            }
        }

        // Parse flywires
        Set<Flywire> flywires = new HashSet<>();
        JsonNode flywiresArray = node.get("flywires");
        if (flywiresArray != null && flywiresArray.isArray()) {
            for (JsonNode flywireNode : flywiresArray) {
                ConnectionPoint source = ConnectionPointJsonDeserializer.deserializeFromNode(flywireNode.get("source"));
                ConnectionPoint target = ConnectionPointJsonDeserializer.deserializeFromNode(flywireNode.get("target"));
                flywires.add(new Flywire(source, target));
            }
        }

        // Parse exports (scope)
        Scope<ConnectionPoint> exports = parseScope(node.get("exports"));

        return new NodeGroup(name, children, flywires, exports);
    }

    private List<CalculationNode> parseAtomicNodeBatch(String typeName, JsonNode node) {
        List<CalculationNode> nodes = new ArrayList<>();

        JsonNode paramsArray = node.get("params");
        if (paramsArray != null && paramsArray.isArray()) {
            for (JsonNode paramSet : paramsArray) {
                Map<String, Object> params = parseConstructionParams(paramSet);
                CalculationNode created = NodeTypeRegistry.createNode(typeName, params);
                nodes.add(created);
            }
        }

        return nodes;
    }

    private Map<String, Object> parseConstructionParams(JsonNode paramNode) {
        Map<String, Object> params = new LinkedHashMap<>();
        paramNode.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode valueNode = entry.getValue();
            params.put(key, parseParamValue(valueNode));
        });
        return params;
    }

    private Object parseParamValue(JsonNode valueNode) {
        if (valueNode.isNull()) {
            return null;
        } else if (valueNode.isTextual()) {
            return valueNode.asText();
        } else if (valueNode.isIntegralNumber()) {
            return valueNode.asInt();
        } else if (valueNode.isFloatingPointNumber()) {
            return valueNode.asDouble();
        } else if (valueNode.isBoolean()) {
            return valueNode.asBoolean();
        } else if (valueNode.isObject()) {
            // Could be a ResourceIdentifier (has type+data) or a typed value
            if (valueNode.has("type") && valueNode.has("data")) {
                String type = valueNode.get("type").asText();
                // Check if it's a registered ResourceIdentifier type
                if (NodeTypeRegistry.isResourceType(type)) {
                    return ResourceIdentifierJsonDeserializer.deserializeFromNode(valueNode);
                }
                // Otherwise it's a typed value (like Ask, Bid etc.)
                return ResultJsonDeserializer.deserializeTypedValue(valueNode);
            }
            // Generic object — parse as map
            Map<String, Object> map = new LinkedHashMap<>();
            valueNode.fields().forEachRemaining(e -> map.put(e.getKey(), parseParamValue(e.getValue())));
            return map;
        } else if (valueNode.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode element : valueNode) {
                list.add(parseParamValue(element));
            }
            return list;
        }
        return valueNode.asText();
    }

    private Scope<ConnectionPoint> parseScope(JsonNode scopeNode) {
        if (scopeNode == null || scopeNode.isNull()) {
            return Exclude.of(Set.of());
        }
        if (scopeNode.has("include")) {
            return Include.of(parseScopeSet(scopeNode.get("include")));
        } else if (scopeNode.has("exclude")) {
            return Exclude.of(parseScopeSet(scopeNode.get("exclude")));
        }
        return Exclude.of(Set.of());
    }

    private ScopeSet<ConnectionPoint> parseScopeSet(JsonNode node) {
        String type = node.get("type").asText();
        if ("RegExMatch".equals(type)) {
            Map<String, String> matchers = new LinkedHashMap<>();
            node.get("fieldMatcher").fields().forEachRemaining(e ->
                matchers.put(e.getKey(), e.getValue().asText())
            );
            return new RegExMatch<>(matchers);
        }
        // Default: FullSet
        Set<ConnectionPoint> points = new HashSet<>();
        JsonNode elementsNode = node.get("elements");
        if (elementsNode != null && elementsNode.isArray()) {
            for (JsonNode cpNode : elementsNode) {
                points.add(ConnectionPointJsonDeserializer.deserializeFromNode(cpNode));
            }
        }
        return new FullSet<>(points);
    }
}
