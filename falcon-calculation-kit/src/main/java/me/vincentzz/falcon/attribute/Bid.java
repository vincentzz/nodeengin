package me.vincentzz.falcon.attribute;

import java.math.BigDecimal;
import java.time.Instant;

public record Bid(BigDecimal price, BigDecimal size, Instant time) implements WithTiming {
}
