package me.vincentzz.falcon.attribute;

import java.math.BigDecimal;
import java.time.Instant;

public record Spread(BigDecimal spread, Instant time) implements WithTiming{
}
