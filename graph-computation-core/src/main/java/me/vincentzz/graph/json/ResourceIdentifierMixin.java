package me.vincentzz.graph.json;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Jackson mixin to force use of custom ResourceIdentifierJsonSerializer
 * for FalconResourceId and other ResourceIdentifier implementations.
 * This bypasses Jackson's built-in record serialization.
 */
@JsonSerialize(using = ResourceIdentifierJsonSerializer.class)
public interface ResourceIdentifierMixin {
    // Empty mixin interface to attach serialization annotations
}
