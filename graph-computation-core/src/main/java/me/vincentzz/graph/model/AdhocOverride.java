package me.vincentzz.graph.model;

import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.node.Flywire;
import me.vincentzz.lang.Result.Result;

import java.util.Map;
import java.util.Set;

public record AdhocOverride(
        Map<ConnectionPoint, Result<Object>> adhocInputs,
        Map<ConnectionPoint, Result<Object>> adhocOutputs,
        Set<Flywire> adhocFlywires) {
}
