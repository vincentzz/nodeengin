package me.vincentzz.graph.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.node.NodeGroup;
import me.vincentzz.graph.scope.*;
import me.vincentzz.lang.PathUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Constructional JSON serializer for CalculationNodes (Graph Definition schema).
 *
 * NodeGroup format:
 * {"type":"NodeGroup","name":"root","nodes":[...],"flywires":[...],"exports":{"exclude":[]}}
 *
 * AtomicNode format (batched by type):
 * {"type":"MidSpreadCalculator","params":[{"symbol":"GOOGLE","source":"FALCON"}, ...]}
 */
public class ConstructionalJsonSerializer extends JsonSerializer<CalculationNode> {

    @Override
    public void serialize(CalculationNode node, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (node instanceof NodeGroup nodeGroup) {
            serializeNodeGroup(nodeGroup, gen, serializers);
        } else {
            // Single atomic node at top level — wrap in batch-of-one
            serializeAtomicNodeBatch(node.getClass().getSimpleName(), List.of(node), gen, serializers);
        }
    }

    private void serializeNodeGroup(NodeGroup group, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("type", "NodeGroup");
        gen.writeStringField("name", group.name());

        // Nodes — group AtomicNodes by type for batching, NodeGroups are individual
        gen.writeArrayFieldStart("nodes");

        // Separate NodeGroups from AtomicNodes
        Map<String, List<CalculationNode>> atomicByType = group.nodes().stream()
                .filter(n -> !(n instanceof NodeGroup))
                .collect(Collectors.groupingBy(n -> n.getClass().getSimpleName()));

        List<NodeGroup> childGroups = group.nodes().stream()
                .filter(n -> n instanceof NodeGroup)
                .map(n -> (NodeGroup) n)
                .toList();

        // Serialize each child NodeGroup individually
        for (NodeGroup childGroup : childGroups) {
            serializeNodeGroup(childGroup, gen, serializers);
        }

        // Serialize AtomicNodes batched by type
        for (Map.Entry<String, List<CalculationNode>> entry : atomicByType.entrySet()) {
            serializeAtomicNodeBatch(entry.getKey(), entry.getValue(), gen, serializers);
        }

        gen.writeEndArray();

        // Flywires
        gen.writeArrayFieldStart("flywires");
        for (var flywire : group.flywires()) {
            serializers.findValueSerializer(flywire.getClass())
                    .serialize(flywire, gen, serializers);
        }
        gen.writeEndArray();

        // Exports (scope) — {"exclude": [...]} or {"include": [...]}
        gen.writeFieldName("exports");
        serializeScope(group.exports(), gen, serializers);

        gen.writeEndObject();
    }

    private void serializeAtomicNodeBatch(String typeName, List<CalculationNode> nodes,
                                          JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("type", typeName);
        gen.writeArrayFieldStart("params");
        for (CalculationNode node : nodes) {
            gen.writeStartObject();
            Map<String, Object> params = node.getConstructionParameters();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                gen.writeFieldName(entry.getKey());
                writeConstructionValue(entry.getValue(), gen, serializers);
            }
            gen.writeEndObject();
        }
        gen.writeEndArray();
        gen.writeEndObject();
    }

    @SuppressWarnings("unchecked")
    private void writeConstructionValue(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else if (value instanceof ResourceIdentifier rid) {
            serializers.findValueSerializer(ResourceIdentifier.class)
                    .serialize(rid, gen, serializers);
        } else if (value.getClass().isRecord()) {
            // Complex typed value (e.g., Ask, Bid) — use type+data wrapper
            ResultJsonSerializer.writeTypedValue(value, gen);
        } else {
            ResourceIdentifierJsonSerializer.writeFieldValue(value, gen);
        }
    }

    private void serializeScope(Scope<ConnectionPoint> scope, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        String key;
        ScopeSet<ConnectionPoint> scopeSet;
        if (scope instanceof Include<ConnectionPoint> include) {
            key = "include";
            scopeSet = include.scopeSet();
        } else if (scope instanceof Exclude<ConnectionPoint> exclude) {
            key = "exclude";
            scopeSet = exclude.scopeSet();
        } else { return; }

        gen.writeFieldName(key);
        serializeScopeSet(scopeSet, gen, serializers);
        gen.writeEndObject();
    }

    private void serializeScopeSet(ScopeSet<ConnectionPoint> scopeSet, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        if (scopeSet instanceof FullSet<ConnectionPoint> fullSet) {
            gen.writeStringField("type", "FullSet");
            gen.writeArrayFieldStart("elements");
            for (ConnectionPoint cp : fullSet.elements()) {
                serializers.findValueSerializer(ConnectionPoint.class).serialize(cp, gen, serializers);
            }
            gen.writeEndArray();
        } else if (scopeSet instanceof RegExMatch<ConnectionPoint> regEx) {
            gen.writeStringField("type", "RegExMatch");
            gen.writeObjectFieldStart("fieldMatcher");
            for (var entry : regEx.fieldMatcher().entrySet()) {
                gen.writeStringField(entry.getKey(), entry.getValue());
            }
            gen.writeEndObject();
        }
        gen.writeEndObject();
    }
}
