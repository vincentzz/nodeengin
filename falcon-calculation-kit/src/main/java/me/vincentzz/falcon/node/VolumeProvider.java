package me.vincentzz.falcon.node;

import me.vincentzz.falcon.attribute.Volume;
import me.vincentzz.falcon.ifo.FalconResourceId;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.model.Snapshot;
import me.vincentzz.graph.node.AtomicNode;
import me.vincentzz.lang.Result.Result;
import me.vincentzz.lang.Result.Success;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record VolumeProvider(String ifo, String source) implements AtomicNode {
    
    @Override
    public Map<String, Object> getConstructionParameters() {
        return Map.of("ifo", ifo, "source", source);
    }

    @Override
    public Set<ResourceIdentifier> inputs() {
        return Set.of();
    }

    @Override
    public Set<ResourceIdentifier> outputs() {
        return Set.of(FalconResourceId.of(ifo, source, Volume.class));
    }

    @Override
    public Set<ResourceIdentifier> resolveDependencies(Snapshot snapshot, Map<ResourceIdentifier, Result<Object>> inputs) {
        return Set.of(); // No dependencies - provides market volume data
    }

    @Override
    public Map<ResourceIdentifier, Result<Object>> compute(Snapshot snapshot, Map<ResourceIdentifier, Result<Object>> dependencyValues) {
        // Simulate volume data - in real system would fetch from market data provider
        BigDecimal volumeQuantity = new BigDecimal("150000"); // 150k shares
        Instant time = Instant.now();
        Volume volume = new Volume(volumeQuantity, time);
        
        return Map.of(
            FalconResourceId.of(ifo, source, Volume.class), 
            Success.of(volume)
        );
    }

    @Override
    public String name() {
        return toString();
    }
}
