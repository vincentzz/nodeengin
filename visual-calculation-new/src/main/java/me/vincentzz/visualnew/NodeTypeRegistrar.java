package me.vincentzz.visualnew;

import me.vincentzz.falcon.node.*;
import me.vincentzz.falcon.rid.FalconRawTopic;
import me.vincentzz.graph.json.NodeTypeRegistry;
import me.vincentzz.graph.node.NodeGroup;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe, idempotent registration of all known node/resource/value types.
 * Called once at startup before any JSON deserialization.
 */
public class NodeTypeRegistrar {

    private static final AtomicBoolean registered = new AtomicBoolean(false);

    public static void registerAll() {
        if (registered.compareAndSet(false, true)) {
            // Node types
            NodeTypeRegistry.registerNodeType("NodeGroup", NodeGroup.class);
            NodeTypeRegistry.registerNodeType("AskProvider", AskProvider.class);
            NodeTypeRegistry.registerNodeType("BidProvider", BidProvider.class);
            NodeTypeRegistry.registerNodeType("MidSpreadCalculator", MidSpreadCalculator.class);
            NodeTypeRegistry.registerNodeType("HardcodeAttributeProvider", HardcodeAttributeProvider.class);
            NodeTypeRegistry.registerNodeType("VolumeProvider", VolumeProvider.class);
            NodeTypeRegistry.registerNodeType("VwapCalculator", VwapCalculator.class);
            NodeTypeRegistry.registerNodeType("MarkToMarketCalculator", MarkToMarketCalculator.class);

            // Resource types
            NodeTypeRegistry.registerResourceType("FalconRawTopic", FalconRawTopic.class);

            // Value types (attribute classes used in FalconRawTopic.type)
            NodeTypeRegistry.registerValueType("Ask", me.vincentzz.falcon.attribute.Ask.class);
            NodeTypeRegistry.registerValueType("Bid", me.vincentzz.falcon.attribute.Bid.class);
            NodeTypeRegistry.registerValueType("MidPrice", me.vincentzz.falcon.attribute.MidPrice.class);
            NodeTypeRegistry.registerValueType("Spread", me.vincentzz.falcon.attribute.Spread.class);
            NodeTypeRegistry.registerValueType("Volume", me.vincentzz.falcon.attribute.Volume.class);
            NodeTypeRegistry.registerValueType("Vwap", me.vincentzz.falcon.attribute.Vwap.class);
            NodeTypeRegistry.registerValueType("MarkToMarket", me.vincentzz.falcon.attribute.MarkToMarket.class);
        }
    }
}
