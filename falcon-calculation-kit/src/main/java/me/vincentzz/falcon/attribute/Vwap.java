package me.vincentzz.falcon.attribute;

import java.math.BigDecimal;
import java.time.Instant;

public record Vwap(BigDecimal price, Instant time) implements WithTiming {
}
