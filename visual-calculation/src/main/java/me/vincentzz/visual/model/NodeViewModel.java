package me.vincentzz.visual.model;

import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.lang.Result.Result;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * View model for a calculation node in the visualization.
 * Contains all data needed to render a node on the canvas.
 */
public class NodeViewModel {
    
    private final Path nodePath;
    private final String displayName;
    private final Set<ResourceIdentifier> inputs;
    private final Set<ResourceIdentifier> outputs;
    private final Map<ResourceIdentifier, Result<Object>> results;
    private final boolean isNodeGroup;
    
    // Visual properties
    private double x;
    private double y;
    private double width;
    private double height;
    private boolean selected;
    private boolean hovered;
    
    public NodeViewModel(Path nodePath, 
                        String displayName,
                        Set<ResourceIdentifier> inputs,
                        Set<ResourceIdentifier> outputs,
                        Map<ResourceIdentifier, Result<Object>> results,
                        boolean isNodeGroup) {
        this.nodePath = nodePath;
        this.displayName = displayName;
        this.inputs = inputs;
        this.outputs = outputs;
        this.results = results;
        this.isNodeGroup = isNodeGroup;
        
        // Default visual properties
        this.width = calculateWidth();
        this.height = calculateHeight();
        this.selected = false;
        this.hovered = false;
    }
    
    private double calculateWidth() {
        // Base width plus space for connection points
        double baseWidth = Math.max(120, displayName.length() * 8);
        int maxConnections = Math.max(inputs.size(), outputs.size());
        return Math.max(baseWidth, maxConnections * 25 + 40);
    }
    
    private double calculateHeight() {
        // Base height plus space for connection points
        double baseHeight = 60;
        int maxConnections = Math.max(inputs.size(), outputs.size());
        return Math.max(baseHeight, maxConnections * 25 + 40);
    }
    
    /**
     * Get the result for a specific resource identifier.
     */
    public Result<Object> getResult(ResourceIdentifier resourceId) {
        return results.get(resourceId);
    }
    
    /**
     * Check if this node has a result for the given resource identifier.
     */
    public boolean hasResult(ResourceIdentifier resourceId) {
        return results.containsKey(resourceId);
    }
    
    /**
     * Get a formatted display string for a resource identifier result.
     */
    public String getResultDisplayString(ResourceIdentifier resourceId) {
        Result<Object> result = results.get(resourceId);
        if (result == null) {
            return "No result";
        }
        
        return switch (result) {
            case me.vincentzz.lang.Result.Success<Object> success -> {
                Object data = success.get();
                if (data == null) {
                    yield "Success: null";
                }
                yield "Success: " + formatResultData(data);
            }
            case me.vincentzz.lang.Result.Failure<Object> failure -> 
                "Error: " + failure.getException();
        };
    }
    
    private String formatResultData(Object data) {
        // Format the result data for display
        if (data instanceof Number num) {
            return String.format("%.3f", num.doubleValue());
        } else if (data instanceof String str) {
            return str;
        } else {
            return data.toString();
        }
    }
    
    /**
     * Check if the given point is inside this node's bounds.
     */
    public boolean contains(double px, double py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }
    
    /**
     * Get the position of an input connection point.
     */
    public ConnectionPointPosition getInputConnectionPoint(ResourceIdentifier resourceId) {
        if (!inputs.contains(resourceId)) {
            return null;
        }
        
        int index = inputs.stream().toList().indexOf(resourceId);
        double pointY = y + 30 + (index * 25); // Distribute vertically on left side
        return new ConnectionPointPosition(x, pointY, resourceId);
    }
    
    /**
     * Get the position of an output connection point.
     */
    public ConnectionPointPosition getOutputConnectionPoint(ResourceIdentifier resourceId) {
        if (!outputs.contains(resourceId)) {
            return null;
        }
        
        int index = outputs.stream().toList().indexOf(resourceId);
        double pointY = y + 30 + (index * 25); // Distribute vertically on right side
        return new ConnectionPointPosition(x + width, pointY, resourceId);
    }
    
    // Getters and setters
    public Path getNodePath() {
        return nodePath;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public Set<ResourceIdentifier> getInputs() {
        return inputs;
    }
    
    public Set<ResourceIdentifier> getOutputs() {
        return outputs;
    }
    
    public Map<ResourceIdentifier, Result<Object>> getResults() {
        return results;
    }
    
    public boolean isNodeGroup() {
        return isNodeGroup;
    }
    
    public double getX() {
        return x;
    }
    
    public void setX(double x) {
        this.x = x;
    }
    
    public double getY() {
        return y;
    }
    
    public void setY(double y) {
        this.y = y;
    }
    
    public double getWidth() {
        return width;
    }
    
    public void setWidth(double width) {
        this.width = width;
    }
    
    public double getHeight() {
        return height;
    }
    
    public void setHeight(double height) {
        this.height = height;
    }
    
    public boolean isSelected() {
        return selected;
    }
    
    public void setSelected(boolean selected) {
        this.selected = selected;
    }
    
    public boolean isHovered() {
        return hovered;
    }
    
    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }
    
    /**
     * Record for connection point position.
     */
    public record ConnectionPointPosition(double x, double y, ResourceIdentifier resourceId) {}
}
