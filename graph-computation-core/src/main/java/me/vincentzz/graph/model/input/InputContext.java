package me.vincentzz.graph.model.input;

import java.util.Optional;

public record InputContext(
        InputSourceType sourceType,
        Optional<Boolean> isDirectInput
) {
}
