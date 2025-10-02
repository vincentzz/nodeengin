package me.vincentzz.graph.model;

import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.lang.Result.Result;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public record EvaluationResult(
        Snapshot snapshot,
        Path requestedNodePath,
        Optional<AdhocOverride> adhocOverride,
        Map<ResourceIdentifier, Result<Object>> results,
        Map<Path, NodeEvaluation> nodeEvaluationMap,
        CalculationNode graph
) {}
