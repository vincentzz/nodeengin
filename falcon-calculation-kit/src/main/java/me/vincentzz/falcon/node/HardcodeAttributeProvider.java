package me.vincentzz.falcon.node;

import me.vincentzz.falcon.ifo.FalconResourceId;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.model.Snapshot;
import me.vincentzz.graph.node.AtomicNode;
import me.vincentzz.lang.Result.Result;
import me.vincentzz.lang.Result.Success;

import java.util.Map;
import java.util.Set;

public record HardcodeAttributeProvider(FalconResourceId rid, Object data) implements AtomicNode {
    
    @Override
    public Map<String, Object> getConstructionParameters() {
        return Map.of("rid", rid, "data", data);
    }

    @Override
    public Set<ResourceIdentifier> inputs() {
        return Set.of();
    }

    @Override
    public Set<ResourceIdentifier> outputs() {
        return Set.of(rid);
    }

    @Override
    public Set<ResourceIdentifier> resolveDependencies(Snapshot snapshot, Map<ResourceIdentifier, Result<Object>> inputs) {
        return Set.of(); // No dependencies
    }

    @Override
    public Map<ResourceIdentifier, Result<Object>> compute(Snapshot snapshot, Map<ResourceIdentifier, Result<Object>> dependencyValues) {
        return Map.of(rid, Success.of(data));
    }

    @Override
    public String name() {
        return "hard";
    }
}
