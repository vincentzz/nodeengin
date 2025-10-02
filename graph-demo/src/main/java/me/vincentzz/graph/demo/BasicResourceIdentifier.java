package me.vincentzz.graph.demo;

import me.vincentzz.graph.ResourceIdentifier;

import java.math.BigDecimal;

public record BasicResourceIdentifier(String rid, Class<?> clazz) implements ResourceIdentifier {
    @Override
    public Class<?> type() {
        return clazz;
    }
}
