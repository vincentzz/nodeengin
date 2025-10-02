package me.vincentzz.falcon.attribute;

import java.math.BigDecimal;
import java.time.Instant;

public record MidPrice(BigDecimal price, Instant time) implements WithTiming{
}
