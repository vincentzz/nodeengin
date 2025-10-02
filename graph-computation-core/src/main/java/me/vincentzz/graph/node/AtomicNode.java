package me.vincentzz.graph.node;

import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.model.Snapshot;
import me.vincentzz.lang.Result.Result;

import java.util.Map;
import java.util.Set;

public non-sealed interface AtomicNode extends CalculationNode {

    Set<ResourceIdentifier> resolveDependencies(Snapshot snapshot, Map<ResourceIdentifier, Result<Object>> inputs);

    Map<ResourceIdentifier, Result<Object>> compute(Snapshot snapshot, Map<ResourceIdentifier, Result<Object>> dependencyValues);
}