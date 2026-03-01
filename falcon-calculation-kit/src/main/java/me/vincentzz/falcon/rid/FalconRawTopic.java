package me.vincentzz.falcon.rid;

import me.vincentzz.graph.model.ResourceIdentifier;

public record FalconRawTopic(String symbol, String source, Class<?> attribute)
        implements ResourceIdentifier {
    public static FalconRawTopic of(String symbol, String source, Class<?> attribute) {
        return new FalconRawTopic(symbol, source, attribute);
    }

    @Override
    public Class<?> type() {
        return attribute;
    }

    @Override
    public String toString() {
        return "FalconRawTopic[" +
                "symbol='" + symbol + '\'' +
                ", source='" + source + '\'' +
                ", attribute=" + attribute.getSimpleName() +
                ']';
    }
}
