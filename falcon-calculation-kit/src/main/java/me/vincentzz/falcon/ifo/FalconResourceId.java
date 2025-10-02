package me.vincentzz.falcon.ifo;

import me.vincentzz.graph.model.ResourceIdentifier;

public record FalconResourceId(String ifo, String source, Class<?> attribute)
        implements ResourceIdentifier {
    public static FalconResourceId of(String ifo, String source, Class<?> attribute) {
        return new FalconResourceId(ifo, source, attribute);
    }

    @Override
    public Class<?> type() {
        return attribute;
    }

    @Override
    public String toString() {
        return "FalconResourceId[" +
                "ifo='" + ifo + '\'' +
                ", source='" + source + '\'' +
                ", attribute=" + attribute.getSimpleName() +
                ']';
    }
}
