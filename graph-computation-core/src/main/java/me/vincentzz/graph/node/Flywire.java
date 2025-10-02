package me.vincentzz.graph.node;

public record Flywire(ConnectionPoint source, ConnectionPoint target) {
    public Flywire {
        if (!target.rid().type().isAssignableFrom(source.rid().type())) {
            throw new RuntimeException("Incompatible type in flywire from '" + source + "' to '" + target + "'");
        }
    }

    public static Flywire of(ConnectionPoint source, ConnectionPoint target) {
        return new Flywire(source, target);
    }
}
