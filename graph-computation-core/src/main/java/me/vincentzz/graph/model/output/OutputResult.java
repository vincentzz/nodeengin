package me.vincentzz.graph.model.output;

import me.vincentzz.lang.Result.Result;

public record OutputResult(OutputContext outputContext, Result<Object> value) {}
