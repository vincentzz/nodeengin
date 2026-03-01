package me.vincentzz.falcon.node;

import me.vincentzz.falcon.attribute.MidPrice;
import me.vincentzz.falcon.attribute.Volume;
import me.vincentzz.falcon.attribute.Vwap;
import me.vincentzz.falcon.rid.FalconRawTopic;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.model.Snapshot;
import me.vincentzz.graph.node.AtomicNode;
import me.vincentzz.lang.Result.Result;
import me.vincentzz.lang.Result.Success;
import me.vincentzz.lang.Result.Failure;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record VwapCalculator(String symbol, String source) implements AtomicNode {

    @Override
    public Map<String, Object> getConstructionParameters() {
        return Map.of("symbol", symbol, "source", source);
    }

    @Override
    public Set<ResourceIdentifier> inputs() {
        return Set.of(
            FalconRawTopic.of(symbol, source, MidPrice.class),
            FalconRawTopic.of(symbol, source, Volume.class)
        );
    }

    @Override
    public Set<ResourceIdentifier> outputs() {
        return Set.of(FalconRawTopic.of(symbol, source, Vwap.class));
    }

    @Override
    public Set<ResourceIdentifier> resolveDependencies(Snapshot snapshot, Map<ResourceIdentifier, Result<Object>> inputs) {
        Set<ResourceIdentifier> missing = new java.util.HashSet<>();

        if (!inputs.containsKey(FalconRawTopic.of(symbol, source, MidPrice.class))) {
            missing.add(FalconRawTopic.of(symbol, source, MidPrice.class));
        }
        if (!inputs.containsKey(FalconRawTopic.of(symbol, source, Volume.class))) {
            missing.add(FalconRawTopic.of(symbol, source, Volume.class));
        }

        return missing;
    }

    @Override
    public Map<ResourceIdentifier, Result<Object>> compute(Snapshot snapshot, Map<ResourceIdentifier, Result<Object>> dependencyValues) {
        Result<Object> midPriceResult = dependencyValues.get(FalconRawTopic.of(symbol, source, MidPrice.class));
        Result<Object> volumeResult = dependencyValues.get(FalconRawTopic.of(symbol, source, Volume.class));

        if (midPriceResult.isFailure() || volumeResult.isFailure()) {
            return Map.of(
                FalconRawTopic.of(symbol, source, Vwap.class),
                Failure.of(new RuntimeException("Failed to get required inputs for VWAP calculation"))
            );
        }

        MidPrice midPrice = (MidPrice) midPriceResult.get();
        Volume volume = (Volume) volumeResult.get();

        // Simple VWAP calculation: price * volume / volume (in real system would accumulate over time window)
        // For demo purposes, we'll apply a small adjustment to show the calculation
        BigDecimal vwapPrice = midPrice.price()
            .multiply(volume.quantity())
            .divide(volume.quantity(), 6, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("0.998")); // Small discount for volume impact

        Instant latestTime = midPrice.time().isAfter(volume.time()) ? midPrice.time() : volume.time();
        Vwap vwap = new Vwap(vwapPrice, latestTime);

        return Map.of(
            FalconRawTopic.of(symbol, source, Vwap.class),
            Success.of(vwap)
        );
    }

    @Override
    public String name() {
        return toString();
    }
}
