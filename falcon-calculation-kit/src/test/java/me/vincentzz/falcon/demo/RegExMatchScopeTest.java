package me.vincentzz.falcon.demo;

import me.vincentzz.falcon.attribute.Ask;
import me.vincentzz.falcon.attribute.Bid;
import me.vincentzz.falcon.attribute.MidPrice;
import me.vincentzz.falcon.attribute.Spread;
import me.vincentzz.falcon.rid.FalconRawTopic;
import me.vincentzz.falcon.node.AskProvider;
import me.vincentzz.falcon.node.BidProvider;
import me.vincentzz.falcon.node.MidSpreadCalculator;
import me.vincentzz.graph.CalculationEngine;
import me.vincentzz.graph.model.EvaluationBundle;
import me.vincentzz.graph.model.EvaluationResult;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.model.Snapshot;
import me.vincentzz.graph.json.ConstructionalJsonUtil;
import me.vincentzz.graph.json.NodeTypeRegistry;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.node.NodeGroup;
import me.vincentzz.graph.scope.Exclude;
import me.vincentzz.graph.scope.Include;
import me.vincentzz.graph.scope.RegExMatch;
import me.vincentzz.lang.Result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RegExMatch scope with CalculationEngine and JSON round-trip.
 */
class RegExMatchScopeTest {

    @BeforeEach
    void setUp() {
        NodeTypeRegistry.registerNodeType("NodeGroup", NodeGroup.class);
        NodeTypeRegistry.registerNodeType("AskProvider", AskProvider.class);
        NodeTypeRegistry.registerNodeType("BidProvider", BidProvider.class);
        NodeTypeRegistry.registerNodeType("MidSpreadCalculator", MidSpreadCalculator.class);
        NodeTypeRegistry.registerResourceType("FalconRawTopic", FalconRawTopic.class);
        NodeTypeRegistry.registerValueType("Ask", Ask.class);
        NodeTypeRegistry.registerValueType("Bid", Bid.class);
        NodeTypeRegistry.registerValueType("MidPrice", MidPrice.class);
        NodeTypeRegistry.registerValueType("Spread", Spread.class);
    }

    @Test
    void regexExcludeFiltersOutputsByRid() {
        AskProvider appleAsk = new AskProvider("APPLE", "Bloomberg");
        BidProvider appleBid = new BidProvider("APPLE", "Bloomberg");
        AskProvider googleAsk = new AskProvider("GOOGLE", "Bloomberg");
        BidProvider googleBid = new BidProvider("GOOGLE", "Bloomberg");

        // Exclude all GOOGLE resources via regex on rid's toString
        RegExMatch<ConnectionPoint> regEx = new RegExMatch<>(Map.of(
                "rid", (".*GOOGLE.*")
        ));
        NodeGroup rawGroup = NodeGroup.of("rawGroup",
                Set.of(appleAsk, appleBid, googleAsk, googleBid),
                Set.of(), Exclude.of(regEx));

        // rawGroup should only export APPLE resources (GOOGLE excluded)
        Set<ResourceIdentifier> outputs = rawGroup.outputs();
        for (ResourceIdentifier rid : outputs) {
            FalconRawTopic frid = (FalconRawTopic) rid;
            assertEquals("APPLE", frid.symbol(), "Only APPLE resources should be exported");
        }
        assertEquals(2, outputs.size(), "Should export APPLE Ask + APPLE Bid");
    }

    @Test
    void regexIncludeFiltersOutputsByNodePath() {
        AskProvider appleAsk = new AskProvider("APPLE", "Bloomberg");
        BidProvider appleBid = new BidProvider("APPLE", "Bloomberg");
        AskProvider googleAsk = new AskProvider("GOOGLE", "Bloomberg");

        // Include only nodes whose name starts with "ASK"
        RegExMatch<ConnectionPoint> regEx = new RegExMatch<>(Map.of(
                "nodePath", ("ASK_.*")
        ));
        NodeGroup group = NodeGroup.of("filtered",
                Set.of(appleAsk, appleBid, googleAsk),
                Set.of(), Include.of(regEx));

        // Only Ask providers' outputs should be visible (their node names start with "ASK_")
        Set<ResourceIdentifier> outputs = group.outputs();
        for (ResourceIdentifier rid : outputs) {
            FalconRawTopic frid = (FalconRawTopic) rid;
            assertEquals(Ask.class, frid.attribute(), "Only Ask outputs should be included");
        }
        assertEquals(2, outputs.size(), "Should include APPLE Ask + GOOGLE Ask");
    }

    @Test
    void regexScopeWorksWithEngine() {
        AskProvider appleAsk = new AskProvider("APPLE", "Bloomberg");
        BidProvider appleBid = new BidProvider("APPLE", "Bloomberg");
        AskProvider googleAsk = new AskProvider("GOOGLE", "Bloomberg");
        BidProvider googleBid = new BidProvider("GOOGLE", "Bloomberg");
        MidSpreadCalculator appleMid = new MidSpreadCalculator("APPLE", "FALCON");

        // Use regex to exclude GOOGLE from rawGroup exports
        RegExMatch<ConnectionPoint> regEx = new RegExMatch<>(Map.of(
                "rid", (".*GOOGLE.*")
        ));
        NodeGroup rawGroup = NodeGroup.of("rawGroup",
                Set.of(appleAsk, appleBid, googleAsk, googleBid),
                Set.of(), Exclude.of(regEx));

        NodeGroup calGroup = NodeGroup.of("calGroup",
                Set.of(appleMid), Set.of(), Exclude.of(Set.of()));

        NodeGroup root = NodeGroup.of("root", Set.of(rawGroup, calGroup));
        CalculationEngine engine = new CalculationEngine(root);

        FalconRawTopic appleMidPrice = FalconRawTopic.of("APPLE", "FALCON", MidPrice.class);
        FalconRawTopic appleSpread = FalconRawTopic.of("APPLE", "FALCON", Spread.class);

        // APPLE MidPrice should succeed (rawGroup exports APPLE Ask/Bid)
        Map<ResourceIdentifier, Result<Object>> results = engine.evaluate(
                Snapshot.ofNow(), Set.of(appleMidPrice, appleSpread));

        assertTrue(results.get(appleMidPrice).isSuccess(), "APPLE MidPrice should compute successfully");
        assertTrue(results.get(appleSpread).isSuccess(), "APPLE Spread should compute successfully");
    }

    @Test
    void regexScopeGraphJsonRoundTrip() {
        AskProvider appleAsk = new AskProvider("APPLE", "Bloomberg");
        BidProvider appleBid = new BidProvider("APPLE", "Bloomberg");
        AskProvider googleAsk = new AskProvider("GOOGLE", "Bloomberg");
        BidProvider googleBid = new BidProvider("GOOGLE", "Bloomberg");

        RegExMatch<ConnectionPoint> regEx = new RegExMatch<>(Map.of(
                "rid", (".*GOOGLE.*")
        ));
        NodeGroup rawGroup = NodeGroup.of("rawGroup",
                Set.of(appleAsk, appleBid, googleAsk, googleBid),
                Set.of(), Exclude.of(regEx));

        NodeGroup root = NodeGroup.of("root", Set.of(rawGroup));

        // Serialize
        Result<String> jsonResult = ConstructionalJsonUtil.toJson(root);
        assertTrue(jsonResult.isSuccess(), "Serialization should succeed");

        String json = jsonResult.get();
        assertTrue(json.contains("\"type\" : \"RegExMatch\""), "JSON should contain RegExMatch type");
        assertTrue(json.contains("\"fieldMatcher\""), "JSON should contain fieldMatcher");
        assertTrue(json.contains(".*GOOGLE.*"), "JSON should contain the regex pattern");

        // Deserialize
        Result<CalculationNode> parsed = ConstructionalJsonUtil.fromJson(json);
        assertTrue(parsed.isSuccess(), "Deserialization should succeed");
        assertEquals(root, parsed.get(), "Round-trip should preserve equality");

        // Verify deserialized graph behaves identically
        NodeGroup deserializedRoot = (NodeGroup) parsed.get();
        assertEquals(root.outputs(), deserializedRoot.outputs(), "Outputs should match after round-trip");
    }

    @Test
    void regexScopeEvaluationResultJsonRoundTrip() {
        AskProvider appleAsk = new AskProvider("APPLE", "Bloomberg");
        BidProvider appleBid = new BidProvider("APPLE", "Bloomberg");
        MidSpreadCalculator appleMid = new MidSpreadCalculator("APPLE", "FALCON");

        // Include only APPLE resources via regex
        RegExMatch<ConnectionPoint> regEx = new RegExMatch<>(Map.of(
                "rid", (".*APPLE.*")
        ));
        NodeGroup rawGroup = NodeGroup.of("rawGroup",
                Set.of(appleAsk, appleBid),
                Set.of(), Include.of(regEx));

        NodeGroup calGroup = NodeGroup.of("calGroup",
                Set.of(appleMid), Set.of(), Exclude.of(Set.of()));

        NodeGroup root = NodeGroup.of("root", Set.of(rawGroup, calGroup));
        CalculationEngine engine = new CalculationEngine(root);

        FalconRawTopic appleMidPrice = FalconRawTopic.of("APPLE", "FALCON", MidPrice.class);

        EvaluationResult evalResult = engine.evaluateForResult(
                Snapshot.ofNow(), Set.of(appleMidPrice), Optional.empty());

        // Round-trip the full EvaluationBundle through JSON
        EvaluationBundle bundle = new EvaluationBundle(engine.rootNode(), evalResult);
        Result<String> jsonResult = ConstructionalJsonUtil.toJsonEvaluationBundle(bundle);
        assertTrue(jsonResult.isSuccess(), "EvaluationBundle serialization should succeed");

        Result<EvaluationBundle> parsedBundle = ConstructionalJsonUtil.fromJsonEvaluationBundle(jsonResult.get());
        assertTrue(parsedBundle.isSuccess(), "EvaluationBundle deserialization should succeed");

        assertEquals(engine.rootNode(), parsedBundle.get().graph(), "Graph should match after round-trip");

        // Verify results have same keys and success status (BigDecimal precision may differ in JSON round-trip)
        EvaluationResult parsedResult = parsedBundle.get().evaluationResult();
        assertEquals(evalResult.results().keySet(), parsedResult.results().keySet(), "Result keys should match");
        for (var key : evalResult.results().keySet()) {
            assertEquals(evalResult.results().get(key).isSuccess(), parsedResult.results().get(key).isSuccess(),
                    "Result success status should match for " + key);
        }
    }
}
