package me.vincentzz.falcon.node;

import me.vincentzz.falcon.attribute.Ask;
import me.vincentzz.falcon.attribute.Bid;
import me.vincentzz.falcon.attribute.MidPrice;
import me.vincentzz.falcon.attribute.Spread;
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

public record MidSpreadCalculator(String symbol, String source) implements AtomicNode {

    @Override
    public Map<String, Object> getConstructionParameters() {
        return Map.of("symbol", symbol, "source", source);
    }

    @Override
    public Set<ResourceIdentifier> inputs() {
        return Set.of(
            FalconRawTopic.of(symbol, source, Ask.class)
        );
    }

    @Override
    public Set<ResourceIdentifier> outputs() {
        return Set.of(
                FalconRawTopic.of(symbol, source, MidPrice.class),
                FalconRawTopic.of(symbol, source, Spread.class)
        );
    }

    @Override
    public Set<ResourceIdentifier> resolveDependencies(Snapshot snapshot, Map<ResourceIdentifier, Result<Object>> inputs) {
        if (!inputs.containsKey(FalconRawTopic.of(symbol, "Bloomberg", Bid.class))) {
            return Set.of(FalconRawTopic.of(symbol, "Bloomberg", Bid.class));
        }
        if (!inputs.containsKey(FalconRawTopic.of(symbol, "Bloomberg", Ask.class))) {
            return Set.of(FalconRawTopic.of(symbol, "Bloomberg", Ask.class));
        }
        return Set.of();
    }

    @Override
    public Map<ResourceIdentifier, Result<Object>> compute(Snapshot snapshot, Map<ResourceIdentifier, Result<Object>> dependencyValues) {
        ResourceIdentifier bidPriceId = FalconRawTopic.of(symbol, "Bloomberg", Bid.class);
        ResourceIdentifier askPriceId = FalconRawTopic.of(symbol, "Bloomberg", Ask.class);

        Bid bidPrice = (Bid) dependencyValues.get(bidPriceId).get();
        Ask askPrice = (Ask) dependencyValues.get(askPriceId).get();

        BigDecimal midPriceValue = bidPrice.price().add(askPrice.price()).divide(BigDecimal.valueOf(2));
        BigDecimal spreadValue = askPrice.price().subtract(bidPrice.price());
        Instant time = bidPrice.time().isAfter(askPrice.time()) ? bidPrice.time() : askPrice.time();

        MidPrice midPrice = new MidPrice(midPriceValue, time);
        Spread spread = new Spread(spreadValue, time);

        return Map.of(
            FalconRawTopic.of(symbol, source, MidPrice.class), Success.of(midPrice),
            FalconRawTopic.of(symbol, source, Spread.class), Success.of(spread)
        );
    }

    @Override
    public String name() {
        return "MID_" + symbol;
    }
}
