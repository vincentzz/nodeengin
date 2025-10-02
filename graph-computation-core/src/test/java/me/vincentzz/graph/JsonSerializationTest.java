package me.vincentzz.graph;

import me.vincentzz.graph.json.ConstructionalJsonUtil;
import me.vincentzz.graph.json.NodeTypeRegistry;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.node.Flywire;
import me.vincentzz.graph.node.NodeGroup;
import me.vincentzz.graph.scope.Exclude;
import me.vincentzz.lang.Result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test JSON serialization round-trip to verify the "nodePath" field fix
 */
public class JsonSerializationTest {
    
    @BeforeEach
    void setUp() {
        // Register node types
        NodeTypeRegistry.registerNodeType("NodeGroup", NodeGroup.class);
        NodeTypeRegistry.registerResourceType("BasicResourceIdentifier", BasicResourceIdentifier.class);
    }

    @Test
    void testJsonRoundTripWithFlywires() {
        // Create a simple test scenario with flywires that contain ConnectionPoints
        BasicResourceIdentifier testResource = new BasicResourceIdentifier("test", String.class);
        
        // Create a NodeGroup with flywires to test the serialization
        Flywire testFlywire = Flywire.of(
                ConnectionPoint.of(Path.of("/source/node"), testResource),
                ConnectionPoint.of(Path.of("/target/node"), testResource)
        );
        
        NodeGroup testGroup = NodeGroup.of("testGroup", Set.of(), Set.of(testFlywire), 
                Exclude.of(Set.of(ConnectionPoint.of(Path.of("/test"), testResource))));

        // Test serialization to JSON
        Result<String> jsonResult = ConstructionalJsonUtil.toJson(testGroup);
        assertTrue(jsonResult.isSuccess(), "JSON serialization should succeed");
        
        String json = jsonResult.get();
        System.out.println("Serialized JSON: " + json);
        
        // Verify the JSON contains "nodePath" fields (not "node" fields)
        assertTrue(json.contains("\"nodePath\""), "JSON should contain 'nodePath' fields");
        assertFalse(json.contains("\"node\":"), "JSON should not contain old 'node' field format");

        // Test deserialization from JSON
        Result<CalculationNode> nodeResult = ConstructionalJsonUtil.fromJson(json);
        assertTrue(nodeResult.isSuccess(), "JSON deserialization should succeed without 'missing nodePath' error");
        
        CalculationNode reconstructedNode = nodeResult.get();
        
        // Verify the round-trip worked
        assertEquals(testGroup, reconstructedNode, "Round-trip serialization should preserve object equality");
    }
}
