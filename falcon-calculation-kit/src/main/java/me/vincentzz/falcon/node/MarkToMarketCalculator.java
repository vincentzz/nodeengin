package me.vincentzz.falcon.node;

import me.vincentzz.falcon.attribute.MarkToMarket;
import me.vincentzz.falcon.attribute.MidPrice;
import me.vincentzz.falcon.ifo.FalconResourceId;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.model.Snapshot;
import me.vincentzz.graph.node.AtomicNode;
import me.vincentzz.lang.Result.Result;
import me.vincentzz.lang.Result.Success;
import me.vincentzz.lang.Result.Failure;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

public record MarkToMarketCalculator(String ifo, String source, String positionSource, String currency) implements AtomicNode {
    
    @Override
    public Map<String, Object> getConstructionParameters() {
        return Map.of(
            "ifo", ifo, 
            "source", source, 
            "positionSource", positionSource,
            "currency", currency
        );
    }

    @Override
    public Set<ResourceIdentifier> inputs() {
        return Set.of(FalconResourceId.of(ifo, source, MidPrice.class));
    }

    @Override
    public Set<ResourceIdentifier> outputs() {
        return Set.of(FalconResourceId.of(ifo, positionSource, MarkToMarket.class));
    }

    @Override
    public Set<ResourceIdentifier> resolveDependencies(Snapshot snapshot, Map<ResourceIdentifier, Result<Object>> inputs) {
        if (!inputs.containsKey(FalconResourceId.of(ifo, source, MidPrice.class))) {
            return Set.of(FalconResourceId.of(ifo, source, MidPrice.class));
        }
        return Set.of();
    }

    @Override
    public Map<ResourceIdentifier, Result<Object>> compute(Snapshot snapshot, Map<ResourceIdentifier, Result<Object>> dependencyValues) {
        Result<Object> midPriceResult = dependencyValues.get(FalconResourceId.of(ifo, source, MidPrice.class));
        
        if (midPriceResult.isFailure()) {
            return Map.of(
                FalconResourceId.of(ifo, positionSource, MarkToMarket.class),
                Failure.of(new RuntimeException("Failed to get mid price for MTM calculation"))
            );
        }
        
        MidPrice midPrice = (MidPrice) midPriceResult.get();
        
        // Simulate position size - in real system would come from position management
        BigDecimal positionSize = new BigDecimal("1000"); // 1000 shares
        BigDecimal mtmValue = midPrice.price().multiply(positionSize);
        
        MarkToMarket mtm = new MarkToMarket(mtmValue, currency, midPrice.time());
        
        return Map.of(
            FalconResourceId.of(ifo, positionSource, MarkToMarket.class),
            Success.of(mtm)
        );
    }

    @Override
    public String name() {
        return toString();
    }
}
