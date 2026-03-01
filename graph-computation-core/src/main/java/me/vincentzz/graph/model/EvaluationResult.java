package me.vincentzz.graph.model;

import me.vincentzz.lang.Result.Result;

import java.nio.file.Path;
import java.util.Map;

public record EvaluationResult(
        EvaluationRequest request,
        Map<ResourceIdentifier, Result<Object>> results,
        Map<Path, NodeEvaluation> evaluations
) {}
