package me.vincentzz.falcon.attribute;

import java.math.BigDecimal;
import java.time.Instant;

public record MarkToMarket(BigDecimal mtm, String currency, Instant time) implements WithTiming {
}
