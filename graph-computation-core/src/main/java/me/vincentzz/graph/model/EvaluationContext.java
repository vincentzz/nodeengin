package me.vincentzz.graph.model;

import me.vincentzz.graph.model.input.InputResult;
import me.vincentzz.graph.model.output.OutputResult;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.node.Flywire;
import me.vincentzz.lang.Result.Result;
import me.vincentzz.lang.collection.MapUtils;
import me.vincentzz.lang.tuple.Tuple;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EvaluationContext {
    private final Snapshot snapshot;
    private final Path requestedNodePath;
    private final Optional<AdhocOverride> adhocOverride;
    private Map<ResourceIdentifier, Result<Object>> results;
    private final Map<ConnectionPoint, Flywire> adhocFlywireIndex;
    private final Map<Path, NodeEvaluationBuilder> nodeEvaluationMap = new ConcurrentHashMap<>();

    public Map<Path, NodeEvaluation> nodeEvaluationMap() {
        return MapUtils.mapValue(nodeEvaluationMap, (k, v) -> v.toNodeEvaluation());
    }

    public Path requestedNodePath() {
        return requestedNodePath;
    }

    public Optional<Result<Object>> getAdhocInput(Path nodePath, ResourceIdentifier rid) {
        return adhocOverride.flatMap(adhocOverride -> Optional.ofNullable(adhocOverride.adhocInputs().get(ConnectionPoint.of(nodePath, rid))));
    }

    public Optional<Result<Object>> getAdhocOutput(Path nodePath, ResourceIdentifier rid) {
        return adhocOverride.flatMap(adhocOverride -> Optional.ofNullable(adhocOverride.adhocOutputs().get(ConnectionPoint.of(nodePath, rid))));
    }

    public Optional<Flywire> getAdhocFlywire(Path nodePath, ResourceIdentifier rid) {
        return Optional.ofNullable(adhocFlywireIndex.get(ConnectionPoint.of(nodePath, rid)));
    }

    public Map<ResourceIdentifier, Result<Object>> results() {
        return results;
    }

    public void setResult(Map<ResourceIdentifier, Result<Object>> results) {
        this.results = results;
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public EvaluationContext(Snapshot snapshot, Path requestedNodePath, Optional<AdhocOverride> adhocOverride) {
        this.snapshot = snapshot;
        this.requestedNodePath = requestedNodePath;
        this.adhocOverride = adhocOverride;
        if (adhocOverride.isPresent()) {
            this.adhocFlywireIndex = adhocOverride.get().adhocFlywires().stream().collect(Collectors.toUnmodifiableMap(f -> f.target(), Function.identity()));
        } else {
            this.adhocFlywireIndex = Map.of();
        }
    }

    public Map<ResourceIdentifier, OutputResult> getCachedResults(Path nodePath, Set<ResourceIdentifier> requestedResources) {
        Optional<NodeEvaluationBuilder> nodeEvaluationOp = Optional.ofNullable(nodeEvaluationMap.get(nodePath));
        if (nodeEvaluationOp.isEmpty()) {
            return Map.of();
        } else {
            NodeEvaluationBuilder nodeEvaluation = nodeEvaluationOp.get();
            return requestedResources.stream().map(rid -> Tuple.of(rid, nodeEvaluation.getResult(rid)))
                    .filter(t -> t._2().isPresent())
                    .collect(Collectors.toUnmodifiableMap(t -> t._1(), t -> t._2().get()));
        }
    }

    public void cacheOutputs(Path nodePath, Map<ResourceIdentifier, OutputResult> outputs) {
        NodeEvaluationBuilder nodeEvaluation = nodeEvaluationMap.computeIfAbsent(nodePath, path -> new NodeEvaluationBuilder());
        outputs.forEach((rid, v) -> {
            nodeEvaluation.recordOutput(rid, v);
        });
    }

    public void cacheInput(Path nodePath, ResourceIdentifier rid, InputResult input) {
        NodeEvaluationBuilder nodeEvaluation = nodeEvaluationMap.computeIfAbsent(nodePath, path -> new NodeEvaluationBuilder());
        nodeEvaluation.recordInput(rid, input);
    }
}
