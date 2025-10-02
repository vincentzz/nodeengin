package me.vincentzz.falcon.demo;

import me.vincentzz.falcon.attribute.*;
import me.vincentzz.falcon.ifo.FalconResourceId;
import me.vincentzz.falcon.node.*;
import me.vincentzz.graph.CalculationEngine;
import me.vincentzz.graph.json.NodeTypeRegistry;
import me.vincentzz.graph.node.NodeGroup;
import me.vincentzz.graph.scope.Exclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * Comprehensive demo showcasing all CalculationEngine capabilities:
 * - Complex financial calculation pipelines
 * - Multiple provider conflict detection and resolution
 * - Export scope controls and resource visibility
 * - Hierarchical node structures
 * - Performance testing with larger graphs
 */
public class ComprehensiveFinancialDemo {
    static {
        // Register node types
        NodeTypeRegistry.registerNodeType("NodeGroup", NodeGroup.class);
        NodeTypeRegistry.registerNodeType("AskProvider", AskProvider.class);
        NodeTypeRegistry.registerNodeType("BidProvider", BidProvider.class);
        NodeTypeRegistry.registerNodeType("MidSpreadCalculator", MidSpreadCalculator.class);
        NodeTypeRegistry.registerNodeType("HardcodeAttributeProvider", HardcodeAttributeProvider.class);

        // Register resource types
        NodeTypeRegistry.registerResourceType("FalconResourceId", FalconResourceId.class);
    }
    
    public static void main(String[] args) {
        System.out.println("=== Comprehensive Financial CalculationEngine Demo ===\n");

        AskProvider appleAsk = new AskProvider("APPLE", "Bloomberg");
        BidProvider appleBid = new BidProvider("APPLE", "Bloomberg");
        AskProvider googleAsk = new AskProvider("GOOGLE", "Bloomberg");
        BidProvider googleBid = new BidProvider("GOOGLE", "Bloomberg");
        MidSpreadCalculator appleMidCalculator = new MidSpreadCalculator("APPLE", "FALCON");
        MidSpreadCalculator googleMidCalculator = new MidSpreadCalculator("GOOGLE", "FALCON");
        HardcodeAttributeProvider hardcodedAppleAsk = new HardcodeAttributeProvider(FalconResourceId.of("APPLE", "HARDCODED", Ask.class),
                new Ask(BigDecimal.valueOf(120), BigDecimal.valueOf(1), Instant.now()));

        NodeGroup rawGroup = NodeGroup.of("rawGroup", Set.of(appleAsk, appleBid, googleAsk, googleBid));
        NodeGroup calGroup = NodeGroup.of("calGroup", Set.of(appleMidCalculator, googleMidCalculator));
        NodeGroup root = NodeGroup.of("root", Set.of(rawGroup, calGroup, hardcodedAppleAsk), Set.of(
//                Flywire.of(
//                        ConnectionPoint.of("hard", FalconResourceId.of("APPLE", "HARDCODED", Ask.class)),
//                        ConnectionPoint.of("calGroup", FalconResourceId.of("APPLE","Bloomberg", Ask.class))
//                )
        ), Exclude.of(Set.of()));

        CalculationEngine engine = new CalculationEngine(root);
//        var a = engine.evaluate(Snapshot.ofNow(), Set.of(FalconResourceId.of("APPLE", "FALCON", MidPrice.class)),
//                Set.of(
//                        Flywire.of(
//                                ConnectionPoint.of(Path.of("hard"), FalconResourceId.of("APPLE", "HARDCODED", Ask.class)),
//                                ConnectionPoint.of(Path.of("calGroup"), FalconResourceId.of("APPLE","Bloomberg", Ask.class))
//                        )
//                )
//        );
//        System.out.println(a);
    }

}
