package me.vincentzz.graph.model;

import me.vincentzz.graph.node.CalculationNode;

public record EvaluationBundle(
        CalculationNode graph,
        EvaluationResult evaluationResult
) {}
