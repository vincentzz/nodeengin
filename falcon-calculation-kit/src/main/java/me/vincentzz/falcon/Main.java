package me.vincentzz.falcon;

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
import me.vincentzz.graph.model.Snapshot;
import me.vincentzz.graph.json.ConstructionalJsonUtil;
import me.vincentzz.graph.json.NodeTypeRegistry;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.node.Flywire;
import me.vincentzz.graph.node.NodeGroup;
import me.vincentzz.graph.scope.Exclude;
import me.vincentzz.lang.Result.Result;
import me.vincentzz.lang.Result.Success;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


/**
 * Comprehensive test showcasing all CalculationEngine capabilities:
 * - Complex financial calculation pipelines
 * - Multiple provider conflict detection and resolution
 * - Export scope controls and resource visibility
 * - Hierarchical node structures
 * - Group-level static flywires
 * - Adhoc flywires with resource transformation
 */
public class Main {

    public static void main(String[] args) {
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

        CalculationEngine engine;
        NodeGroup root;
        FalconResourceId appleMidPriceId;
        FalconResourceId googleMidPriceId;
        FalconResourceId googleSpreadId;

        root = NodeGroup.of("root", Set.of(rawGroup, calGroup, hardcodedAppleAsk));

        engine = new CalculationEngine(root);
        appleMidPriceId = FalconResourceId.of("APPLE", "FALCON", MidPrice.class);
        googleMidPriceId = FalconResourceId.of("GOOGLE", "FALCON", MidPrice.class);
        googleSpreadId = FalconResourceId.of("GOOGLE", "FALCON", Spread.class);

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

        for (int i = 0; i < 100; i++) {
            Instant start = Instant.now();
            EvaluationResult evalResult = engine2.evaluateForResult(Snapshot.ofNow(), Set.of(appleMidPriceId, googleMidPriceId, googleSpreadId), Optional.of(adhoc));

            CalculationNode subGraph = evalResult.graph();
            CalculationEngine engine_new = new CalculationEngine(subGraph);
            EvaluationResult evalResult_new = engine_new.evaluateForResult(Snapshot.ofNow(), Set.of(appleMidPriceId, googleMidPriceId, googleSpreadId), Optional.of(adhoc));
            System.out.println(evalResult_new);

            System.out.println("evalResult == evalResult_new ? :" + evalResult.graph().equals(evalResult_new.graph()));

            Result<String> jsonResult = ConstructionalJsonUtil.toJson(subGraph);
            System.out.println(jsonResult);

            Result<CalculationNode> parsedNode = ConstructionalJsonUtil.fromJson(jsonResult.get());
            System.out.println("subGraph == parsedNode ? :" + subGraph.equals(parsedNode.get()));
            CalculationEngine engine_parsed = new CalculationEngine(parsedNode.get());
            EvaluationResult evalResult_parsed = engine_parsed.evaluateForResult(Snapshot.ofNow(), Set.of(appleMidPriceId, googleMidPriceId, googleSpreadId), Optional.of(adhoc));

            System.out.println("evalResult.graph == evalResult_parsed.gubGraph ? :" + evalResult.graph().equals(evalResult_parsed.graph()));

            Instant end = Instant.now();
            System.out.println(evalResult);
            System.out.println(end.toEpochMilli() - start.toEpochMilli() + "ms. ");
        }
    }
}
