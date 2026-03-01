package me.vincentzz.falcon.node;

import me.vincentzz.falcon.attribute.Ask;
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

public record AskProvider(String symbol, String source) implements AtomicNode {

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
        return Set.of(FalconRawTopic.of(symbol, source, Ask.class));
    }

    @Override
    public Set<ResourceIdentifier> resolveDependencies(Snapshot snapshot, Map<ResourceIdentifier, Result<Object>> inputs) {
        return Set.of(); // No dependencies - provides market ask price data
    }

    @Override
    public Map<ResourceIdentifier, Result<Object>> compute(Snapshot snapshot, Map<ResourceIdentifier, Result<Object>> dependencyValues) {
        // Simulate ask price data - in real system would fetch from market data provider
        BigDecimal askPriceValue = new BigDecimal("100.25"); // Slightly above bid
        Instant time = Instant.now();
        Ask askPrice = new Ask(askPriceValue, BigDecimal.valueOf(2), time);

        return Map.of(
            FalconRawTopic.of(symbol, source, Ask.class),
            Success.of(askPrice)
        );
    }

    @Override
    public String name() {
        return "ASK_"+symbol;
    }
}
