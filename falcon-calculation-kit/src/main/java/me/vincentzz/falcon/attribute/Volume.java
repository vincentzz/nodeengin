package me.vincentzz.falcon.attribute;

import java.math.BigDecimal;
import java.time.Instant;

public record Volume(BigDecimal quantity, Instant time) implements WithTiming {
}
