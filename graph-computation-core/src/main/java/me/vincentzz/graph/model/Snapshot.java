package me.vincentzz.graph.model;

import java.time.Instant;
import java.util.Optional;

public record Snapshot(Optional<Instant> logicalTimestamp, Optional<Instant> physicalTimestamp) {
    public static Snapshot ofLogical(Instant lts) {
        return new Snapshot(Optional.of(lts), Optional.empty());
    }

    public static Snapshot ofPhysical(Instant pts) {
        return new Snapshot(Optional.empty(), Optional.of(pts));
    }

    public static Snapshot ofNow() {
        return new Snapshot(Optional.empty(), Optional.empty());
    }

    public static Snapshot of(Instant lts, Instant pts) {
        return new Snapshot(Optional.of(lts), Optional.of(pts));
    }
}
