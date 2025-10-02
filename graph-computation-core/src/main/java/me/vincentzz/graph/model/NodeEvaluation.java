package me.vincentzz.graph.model;

import me.vincentzz.graph.model.input.InputResult;
import me.vincentzz.graph.model.output.OutputResult;

import java.util.Map;

public record NodeEvaluation(Map<ResourceIdentifier, InputResult> inputs,
                             Map<ResourceIdentifier, OutputResult> outputs) {}
