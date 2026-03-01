package me.vincentzz.graph.model;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

public record EvaluationRequest(
        Set<ResourceIdentifier> rids,
        Snapshot snapshot,
        Path path,
        Optional<AdhocOverride> override
) {}
