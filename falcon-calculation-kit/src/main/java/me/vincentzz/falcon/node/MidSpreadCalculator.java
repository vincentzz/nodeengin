package me.vincentzz.falcon.node;

import me.vincentzz.falcon.attribute.Ask;
import me.vincentzz.falcon.attribute.Bid;
import me.vincentzz.falcon.attribute.MidPrice;
import me.vincentzz.falcon.attribute.Spread;
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

public record MidSpreadCalculator(String ifo, String source) implements AtomicNode {

    @Override
    public Map<String, Object> getConstructionParameters() {
        return Map.of("ifo", ifo, "source", source);
    }

    @Override
    public Set<ResourceIdentifier> inputs() {
        return Set.of(
            FalconResourceId.of(ifo, source, Ask.class)
        );
    }

    @Override
    public Set<ResourceIdentifier> outputs() {
        return Set.of(
                FalconResourceId.of(ifo, source, MidPrice.class),
                FalconResourceId.of(ifo, source, Spread.class)
        );
    }

    @Override
    public Set<ResourceIdentifier> resolveDependencies(Snapshot snapshot, Map<ResourceIdentifier, Result<Object>> inputs) {
        if (!inputs.containsKey(FalconResourceId.of(ifo, "Bloomberg", Bid.class))) {
            return Set.of(FalconResourceId.of(ifo, "Bloomberg", Bid.class));
        }
        if (!inputs.containsKey(FalconResourceId.of(ifo, "Bloomberg", Ask.class))) {
            return Set.of(FalconResourceId.of(ifo, "Bloomberg", Ask.class));
        }
        return Set.of();
    }

    @Override
    public Map<ResourceIdentifier, Result<Object>> compute(Snapshot snapshot, Map<ResourceIdentifier, Result<Object>> dependencyValues) {
        ResourceIdentifier bidPriceId = FalconResourceId.of(ifo, "Bloomberg", Bid.class);
        ResourceIdentifier askPriceId = FalconResourceId.of(ifo, "Bloomberg", Ask.class);

        Bid bidPrice = (Bid) dependencyValues.get(bidPriceId).get();
        Ask askPrice = (Ask) dependencyValues.get(askPriceId).get();
        
        BigDecimal midPriceValue = bidPrice.price().add(askPrice.price()).divide(BigDecimal.valueOf(2));
        BigDecimal spreadValue = askPrice.price().subtract(bidPrice.price());
        Instant time = bidPrice.time().isAfter(askPrice.time()) ? bidPrice.time() : askPrice.time();
        
        MidPrice midPrice = new MidPrice(midPriceValue, time);
        Spread spread = new Spread(spreadValue, time);
        
        return Map.of(
            FalconResourceId.of(ifo, source, MidPrice.class), Success.of(midPrice),
            FalconResourceId.of(ifo, source, Spread.class), Success.of(spread)
        );
    }

    @Override
    public String name() {
        return "MID_" + ifo;
    }
}
