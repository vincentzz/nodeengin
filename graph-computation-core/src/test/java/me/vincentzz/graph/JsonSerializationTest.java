package me.vincentzz.graph;

import me.vincentzz.graph.json.ConstructionalJsonUtil;
import me.vincentzz.graph.json.NodeTypeRegistry;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.node.Flywire;
import me.vincentzz.graph.node.NodeGroup;
import me.vincentzz.graph.scope.Exclude;
import me.vincentzz.graph.scope.Include;
import me.vincentzz.graph.scope.RegExMatch;
import me.vincentzz.lang.Result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test JSON serialization round-trip for the new format.
 */
public class JsonSerializationTest {

    @BeforeEach
    void setUp() {
        NodeTypeRegistry.registerNodeType("NodeGroup", NodeGroup.class);
        NodeTypeRegistry.registerResourceType("BasicResourceIdentifier", BasicResourceIdentifier.class);
    }

    @Test
    void testJsonRoundTripWithFlywires() {
        BasicResourceIdentifier testResource = new BasicResourceIdentifier("test", String.class);

        Flywire testFlywire = Flywire.of(
                ConnectionPoint.of(Path.of("/source/node"), testResource),
                ConnectionPoint.of(Path.of("/target/node"), testResource)
        );

        NodeGroup testGroup = NodeGroup.of("testGroup", Set.of(), Set.of(testFlywire),
                Exclude.of(Set.of(ConnectionPoint.of(Path.of("/test"), testResource))));

        Result<String> jsonResult = ConstructionalJsonUtil.toJson(testGroup);
        assertTrue(jsonResult.isSuccess(), "JSON serialization should succeed");

        String json = jsonResult.get();

        // Verify new format: "rid" field, not "resourceId"
        assertTrue(json.contains("\"rid\""), "JSON should contain 'rid' fields");
        // Verify new format: "nodePath" field
        assertTrue(json.contains("\"nodePath\""), "JSON should contain 'nodePath' fields");
        // Verify new scope format: {"exclude": [...]} not {"type": "Exclude", "values": [...]}
        assertTrue(json.contains("\"exclude\""), "JSON should contain 'exclude' scope key");
        assertFalse(json.contains("\"values\""), "JSON should not contain old 'values' scope field");
        // Verify new format: type+data wrapper for ResourceIdentifier
        assertTrue(json.contains("\"type\" : \"BasicResourceIdentifier\""), "JSON should contain type field for ResourceIdentifier");
        assertTrue(json.contains("\"data\""), "JSON should contain data field for ResourceIdentifier");

        // Test round-trip deserialization
        Result<CalculationNode> nodeResult = ConstructionalJsonUtil.fromJson(json);
        assertTrue(nodeResult.isSuccess(), "JSON deserialization should succeed");

        CalculationNode reconstructedNode = nodeResult.get();
        assertEquals(testGroup, reconstructedNode, "Round-trip serialization should preserve object equality");
    }

    @Test
    void testEmptyNodeGroupRoundTrip() {
        NodeGroup emptyGroup = NodeGroup.of("empty", Set.of());

        Result<String> jsonResult = ConstructionalJsonUtil.toJson(emptyGroup);
        assertTrue(jsonResult.isSuccess());

        String json = jsonResult.get();
        assertTrue(json.contains("\"type\" : \"NodeGroup\""));
        assertTrue(json.contains("\"name\" : \"empty\""));

        Result<CalculationNode> nodeResult = ConstructionalJsonUtil.fromJson(json);
        assertTrue(nodeResult.isSuccess());
        assertEquals(emptyGroup, nodeResult.get());
    }

    @Test
    void testNestedNodeGroupRoundTrip() {
        NodeGroup inner = NodeGroup.of("inner", Set.of());
        NodeGroup outer = NodeGroup.of("outer", Set.of(inner));

        Result<String> jsonResult = ConstructionalJsonUtil.toJson(outer);
        assertTrue(jsonResult.isSuccess());

        Result<CalculationNode> nodeResult = ConstructionalJsonUtil.fromJson(jsonResult.get());
        assertTrue(nodeResult.isSuccess());
        assertEquals(outer, nodeResult.get());
    }

    @Test
    void testRegExMatchExcludeRoundTrip() {
        RegExMatch<ConnectionPoint> regEx = new RegExMatch<>(Map.of(
                "nodePath", "^Provider.*",
                "rid", ".*APPLE.*"
        ));
        NodeGroup group = NodeGroup.of("regexGroup", Set.of(), Set.of(), Exclude.of(regEx));

        Result<String> jsonResult = ConstructionalJsonUtil.toJson(group);
        assertTrue(jsonResult.isSuccess());

        String json = jsonResult.get();
        assertTrue(json.contains("\"type\" : \"RegExMatch\""), "JSON should contain RegExMatch type");
        assertTrue(json.contains("\"fieldMatcher\""), "JSON should contain fieldMatcher");
        assertTrue(json.contains("\"nodePath\""), "JSON should contain nodePath matcher key");
        assertTrue(json.contains("^Provider.*"), "JSON should contain nodePath regex pattern");
        assertTrue(json.contains(".*APPLE.*"), "JSON should contain rid regex pattern");

        Result<CalculationNode> nodeResult = ConstructionalJsonUtil.fromJson(json);
        assertTrue(nodeResult.isSuccess(), "Deserialization should succeed");

        NodeGroup deserialized = (NodeGroup) nodeResult.get();
        assertEquals("regexGroup", deserialized.name());

        // Verify the scope is an Exclude with RegExMatch
        var exports = deserialized.exports();
        assertInstanceOf(Exclude.class, exports);
        Exclude<ConnectionPoint> exclude = (Exclude<ConnectionPoint>) exports;
        assertInstanceOf(RegExMatch.class, exclude.scopeSet());

        // Verify the regex patterns match correctly after round-trip
        BasicResourceIdentifier appleRid = new BasicResourceIdentifier("APPLE_ASK", String.class);
        BasicResourceIdentifier googleRid = new BasicResourceIdentifier("GOOGLE_BID", String.class);
        ConnectionPoint matchCp = ConnectionPoint.of(Path.of("ProviderA"), appleRid);
        ConnectionPoint noMatchCp = ConnectionPoint.of(Path.of("ProviderA"), googleRid);

        // Exclude scope: things matching regex are excluded (isInScope returns false)
        assertFalse(exports.isInScope(matchCp), "APPLE on Provider should be excluded");
        assertTrue(exports.isInScope(noMatchCp), "GOOGLE on Provider should not be excluded (rid doesn't match)");
    }

    @Test
    void testRegExMatchIncludeRoundTrip() {
        RegExMatch<ConnectionPoint> regEx = new RegExMatch<>(Map.of(
                "nodePath", "Calculator.*"
        ));
        NodeGroup group = NodeGroup.of("includeRegex", Set.of(), Set.of(), Include.of(regEx));

        Result<String> jsonResult = ConstructionalJsonUtil.toJson(group);
        assertTrue(jsonResult.isSuccess());

        String json = jsonResult.get();
        assertTrue(json.contains("\"include\""), "JSON should use include key");
        assertTrue(json.contains("\"type\" : \"RegExMatch\""));

        Result<CalculationNode> nodeResult = ConstructionalJsonUtil.fromJson(json);
        assertTrue(nodeResult.isSuccess());

        NodeGroup deserialized = (NodeGroup) nodeResult.get();
        var exports = deserialized.exports();
        assertInstanceOf(Include.class, exports);
        Include<ConnectionPoint> include = (Include<ConnectionPoint>) exports;
        assertInstanceOf(RegExMatch.class, include.scopeSet());

        BasicResourceIdentifier rid = new BasicResourceIdentifier("test", String.class);
        assertTrue(exports.isInScope(ConnectionPoint.of(Path.of("CalculatorNode"), rid)));
        assertFalse(exports.isInScope(ConnectionPoint.of(Path.of("ProviderNode"), rid)));
    }

    @Test
    void testFullSetScopeFormat() {
        // Verify FullSet format has type+elements wrapper (not flat array)
        BasicResourceIdentifier testResource = new BasicResourceIdentifier("test", String.class);
        NodeGroup group = NodeGroup.of("fullSetGroup", Set.of(), Set.of(),
                Exclude.of(Set.of(ConnectionPoint.of(Path.of("/node"), testResource))));

        Result<String> jsonResult = ConstructionalJsonUtil.toJson(group);
        assertTrue(jsonResult.isSuccess());

        String json = jsonResult.get();
        assertTrue(json.contains("\"type\" : \"FullSet\""), "JSON should contain FullSet type");
        assertTrue(json.contains("\"elements\""), "JSON should contain elements array");
    }
}
