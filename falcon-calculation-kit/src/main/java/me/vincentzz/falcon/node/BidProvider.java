package me.vincentzz.falcon.node;

import me.vincentzz.falcon.attribute.Bid;
import me.vincentzz.falcon.rid.FalconRawTopic;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.model.Snapshot;
import me.vincentzz.graph.node.AtomicNode;
import me.vincentzz.lang.Result.Result;
import me.vincentzz.lang.Result.Success;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record BidProvider(String symbol, String source) implements AtomicNode {

    @Override
    public Map<String, Object> getConstructionParameters() {
        return Map.of("symbol", symbol, "source", source);
    }

    @Override
    public Set<ResourceIdentifier> inputs() {
        return Set.of();
    }

    @Override
    public Set<ResourceIdentifier> outputs() {
        return Set.of(FalconRawTopic.of(symbol, source, Bid.class));
    }

    @Override
    public Set<ResourceIdentifier> resolveDependencies(Snapshot snapshot, Map<ResourceIdentifier, Result<Object>> inputs) {
        return Set.of(); // No dependencies - provides market bid price data
    }

    @Override
    public Map<ResourceIdentifier, Result<Object>> compute(Snapshot snapshot, Map<ResourceIdentifier, Result<Object>> dependencyValues) {
        // Simulate bid price data - in real system would fetch from market data provider
        BigDecimal bidPriceValue = new BigDecimal("99.75"); // Slightly below ask
        Instant time = Instant.now();
        Bid bidPrice = new Bid(bidPriceValue, BigDecimal.valueOf(1), time);

        return Map.of(
            FalconRawTopic.of(symbol, source, Bid.class),
            Success.of(bidPrice)
        );
    }

    @Override
    public String name() {
        return "BID_"+symbol;
    }
}
