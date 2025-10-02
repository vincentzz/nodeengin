package me.vincentzz.falcon.demo;

import me.vincentzz.falcon.attribute.Ask;
import me.vincentzz.falcon.attribute.Bid;
import me.vincentzz.falcon.attribute.MidPrice;
import me.vincentzz.falcon.attribute.Spread;
import me.vincentzz.falcon.ifo.FalconResourceId;
import me.vincentzz.falcon.node.AskProvider;
import me.vincentzz.falcon.node.BidProvider;
import me.vincentzz.falcon.node.HardcodeAttributeProvider;
import me.vincentzz.falcon.node.MidSpreadCalculator;
import me.vincentzz.graph.model.AdhocOverride;
import me.vincentzz.graph.CalculationEngine;
import me.vincentzz.graph.model.EvaluationResult;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.model.Snapshot;
import me.vincentzz.graph.json.ConstructionalJsonUtil;
import me.vincentzz.graph.json.NodeTypeRegistry;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.node.Flywire;
import me.vincentzz.graph.node.NodeGroup;
import me.vincentzz.graph.node.builder.NodeBuilder;
import me.vincentzz.graph.node.builder.NodeGroupBuilder;
import me.vincentzz.graph.scope.Exclude;
import me.vincentzz.lang.Result.Result;
import me.vincentzz.lang.Result.Success;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test showcasing all CalculationEngine capabilities:
 * - Complex financial calculation pipelines
 * - Multiple provider conflict detection and resolution
 * - Export scope controls and resource visibility
 * - Hierarchical node structures
 * - Group-level static flywires
 * - Adhoc flywires with resource transformation
 */
public class ComprehensiveFinancialDemoTest {

    private CalculationEngine engine;
    private NodeGroup root;
    private FalconResourceId appleMidPriceId;
    private FalconResourceId googleMidPriceId;
    private FalconResourceId googleSpreadId;

    @BeforeEach
    void setUp() {
        // Register node types
        NodeTypeRegistry.registerNodeType("NodeGroup", NodeGroup.class);
        NodeTypeRegistry.registerNodeType("AskProvider", AskProvider.class);
        NodeTypeRegistry.registerNodeType("BidProvider", BidProvider.class);
        NodeTypeRegistry.registerNodeType("MidSpreadCalculator", MidSpreadCalculator.class);
        NodeTypeRegistry.registerNodeType("HardcodeAttributeProvider", HardcodeAttributeProvider.class);

        // Register resource types
        NodeTypeRegistry.registerResourceType("FalconResourceId", FalconResourceId.class);

        // Create test data
        AskProvider appleAsk = new AskProvider("APPLE", "Bloomberg");
        BidProvider appleBid = new BidProvider("APPLE", "Bloomberg");
        AskProvider googleAsk = new AskProvider("GOOGLE", "Bloomberg");
        BidProvider googleBid = new BidProvider("GOOGLE", "Bloomberg");
        MidSpreadCalculator appleMidCalculator = new MidSpreadCalculator("APPLE", "FALCON");
        MidSpreadCalculator googleMidCalculator = new MidSpreadCalculator("GOOGLE", "FALCON");
        HardcodeAttributeProvider hardcodedGoogleBid = new HardcodeAttributeProvider(
                FalconResourceId.of("GOOGLE", "HARDCODED", Bid.class),
                new Bid(BigDecimal.valueOf(80), BigDecimal.valueOf(1), Instant.now())
        );
        HardcodeAttributeProvider hardcodedAppleAsk = new HardcodeAttributeProvider(
                FalconResourceId.of("APPLE", "HARDCODED", Ask.class),
                new Ask(BigDecimal.valueOf(120), BigDecimal.valueOf(1), Instant.now())
        );

        NodeGroup rawGroup = NodeGroup.of("rawGroup", Set.of(appleAsk, appleBid, googleAsk, googleBid, hardcodedGoogleBid),
                Set.of(), Exclude.of(Set.of(ConnectionPoint.of(Path.of("hard"), FalconResourceId.of("GOOGLE", "HARDCODED", Bid.class)))));

        NodeGroup calGroup = NodeGroup.of("calGroup", Set.of(appleMidCalculator, googleMidCalculator),
                Set.of(Flywire.of(
                        ConnectionPoint.of(Path.of("/root/rawGroup/hard"), FalconResourceId.of("GOOGLE", "HARDCODED", Bid.class)),
                        ConnectionPoint.of(Path.of("MID_GOOGLE"), FalconResourceId.of("GOOGLE", "Bloomberg", Bid.class))
                )), Exclude.of(Set.of()));

        root = NodeGroup.of("root", Set.of(rawGroup, calGroup, hardcodedAppleAsk));

        engine = new CalculationEngine(root);

        appleMidPriceId = FalconResourceId.of("APPLE", "FALCON", MidPrice.class);
        googleMidPriceId = FalconResourceId.of("GOOGLE", "FALCON", MidPrice.class);
        googleSpreadId = FalconResourceId.of("GOOGLE", "FALCON", Spread.class);
    }

    @Test
    void builderTest() {
        NodeBuilder nb = NodeBuilder.fromNode(root);
        assertEquals(root, nb.toNode());

        ((NodeGroupBuilder)nb).addNode(new AskProvider("APPLE", "Bloomberg"));
        assertNotEquals(root, nb.toNode());
        CalculationNode newRoot = nb.toNode();
        assertEquals(4, ((NodeGroup)newRoot).nodes().size());
    }

    @Test
    void testEngin2() {
        CalculationEngine engine2 = new CalculationEngine(root);

        // Create flywire for more interesting evaluation context
        Flywire adhocFlywire = Flywire.of(
                ConnectionPoint.of(Path.of("/root/hard"), FalconResourceId.of("APPLE", "HARDCODED", Ask.class)),
                ConnectionPoint.of(Path.of("/root/calGroup"), FalconResourceId.of("APPLE", "Bloomberg", Ask.class))
        );

        AdhocOverride adhoc = new AdhocOverride(Map.of(), Map.of(
                ConnectionPoint.of(Path.of("/root/calGroup/MID_GOOGLE"), FalconResourceId.of("GOOGLE", "FALCON", Spread.class)),
                Success.of(new Spread(BigDecimal.ONE, Instant.now()))
        ), Set.of(adhocFlywire));

        for (int i = 0; i < 1000; i++) {
            Instant start = Instant.now();
            EvaluationResult evalResult = engine2.evaluateForResult(Snapshot.ofNow(), Set.of(appleMidPriceId, googleMidPriceId, googleSpreadId), Optional.of(adhoc));
            Instant end = Instant.now();
            System.out.println(evalResult);
            System.out.println(end.toEpochMilli() - start.toEpochMilli() + "ms. ");

            CalculationNode subGraph = evalResult.graph();
            CalculationEngine engine_new = new CalculationEngine(subGraph);
            EvaluationResult evalResult_new = engine_new.evaluateForResult(Snapshot.ofNow(), Set.of(appleMidPriceId, googleMidPriceId, googleSpreadId), Optional.of(adhoc));
            System.out.println(evalResult_new);

            assertEquals(evalResult.graph(), evalResult_new.graph());

            Result<String> jsonResult = ConstructionalJsonUtil.toJson(subGraph);
            System.out.println(jsonResult);
            Result<String> resultJson = ConstructionalJsonUtil.toJsonEvaluationResult(evalResult);
            System.out.println(resultJson);
            System.err.println("DEBUG TEST: resultJson.isSuccess() = " + resultJson.isSuccess());
            if (!resultJson.isSuccess()) {
                System.err.println("DEBUG TEST: resultJson failed with error: " + resultJson);
                throw new RuntimeException("JSON serialization failed!");
            }
            String jsonString = resultJson.get();
            System.err.println("DEBUG TEST: About to call fromJsonEvaluationResult with JSON length: " + jsonString.length());
            Result<EvaluationResult> parseFromJson = ConstructionalJsonUtil.fromJsonEvaluationResult(jsonString);
            System.err.println("DEBUG TEST: fromJsonEvaluationResult returned: " + parseFromJson.isSuccess());
            if (!parseFromJson.isSuccess()) {
                System.err.println("DEBUG TEST: parseFromJson failed with error: " + parseFromJson);
            }
            assertTrue(parseFromJson.isSuccess());

            assertEquals(evalResult.snapshot(), parseFromJson.get().snapshot());
            assertEquals(evalResult.requestedNodePath(), parseFromJson.get().requestedNodePath());
            assertEquals(evalResult.results(), parseFromJson.get().results());
            assertEquals(evalResult.adhocOverride(), parseFromJson.get().adhocOverride());
            assertEquals(evalResult.graph(), parseFromJson.get().graph());
            var oldEvaluationMap = evalResult.nodeEvaluationMap();
            var newEvaluationMap = parseFromJson.get().nodeEvaluationMap();
            assertEquals(oldEvaluationMap, newEvaluationMap);
            assertEquals(evalResult, parseFromJson.get());
            System.out.println(parseFromJson);

            Result<CalculationNode> parsedNode = ConstructionalJsonUtil.fromJson(jsonResult.get());
            assertEquals(subGraph, parsedNode.get());
            CalculationEngine engine_parsed = new CalculationEngine(parsedNode.get());
            EvaluationResult evalResult_parsed = engine_parsed.evaluateForResult(Snapshot.ofNow(), Set.of(appleMidPriceId, googleMidPriceId, googleSpreadId), Optional.of(adhoc));
            assertEquals(evalResult.graph(), evalResult_parsed.graph());
        }
    }
}
