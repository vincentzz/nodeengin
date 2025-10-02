package me.vincentzz.graph.node;

import me.vincentzz.graph.model.ResourceIdentifier;

import java.nio.file.Path;

public record ConnectionPoint(Path nodePath, ResourceIdentifier rid) {
    public static ConnectionPoint of(Path nodePath, ResourceIdentifier rid) {
        return new ConnectionPoint(nodePath, rid);
    }
}
