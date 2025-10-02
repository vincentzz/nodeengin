package me.vincentzz.graph.model.input;

import me.vincentzz.lang.Result.Result;

public record InputResult(InputContext inputContext, Result<Object> value) {
}
