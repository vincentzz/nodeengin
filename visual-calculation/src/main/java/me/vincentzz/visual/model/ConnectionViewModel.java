package me.vincentzz.visual.model;

import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.lang.PathUtils;

import java.nio.file.Path;

/**
 * View model for connections between nodes in the visualization.
 * Represents lines connecting output and input connection points.
 */
public class ConnectionViewModel {
    
    private final Path sourcePath;
    private final ResourceIdentifier sourceResource;
    private final Path targetPath;
    private final ResourceIdentifier targetResource;
    private final ConnectionType connectionType;
    
    // Visual properties
    private double sourceX;
    private double sourceY;
    private double targetX;
    private double targetY;
    private boolean hovered;
    
    public ConnectionViewModel(Path sourcePath,
                              ResourceIdentifier sourceResource,
                              Path targetPath,
                              ResourceIdentifier targetResource,
                              ConnectionType connectionType) {
        this.sourcePath = sourcePath;
        this.sourceResource = sourceResource;
        this.targetPath = targetPath;
        this.targetResource = targetResource;
        this.connectionType = connectionType;
        this.hovered = false;
    }
    
    /**
     * Update the visual coordinates of this connection based on node positions.
     */
    public void updateCoordinates(NodeViewModel sourceNode, NodeViewModel targetNode) {
        if (sourceNode != null) {
            NodeViewModel.ConnectionPointPosition sourcePoint = 
                sourceNode.getOutputConnectionPoint(sourceResource);
            if (sourcePoint != null) {
                this.sourceX = sourcePoint.x();
                this.sourceY = sourcePoint.y();
            }
        }
        
        if (targetNode != null) {
            NodeViewModel.ConnectionPointPosition targetPoint = 
                targetNode.getInputConnectionPoint(targetResource);
            if (targetPoint != null) {
                this.targetX = targetPoint.x();
                this.targetY = targetPoint.y();
            }
        }
    }
    
    /**
     * Check if the given point is near this connection line.
     */
    public boolean isNearPoint(double px, double py, double tolerance) {
        // Calculate distance from point to line segment
        double A = px - sourceX;
        double B = py - sourceY;
        double C = targetX - sourceX;
        double D = targetY - sourceY;
        
        double dot = A * C + B * D;
        double lenSq = C * C + D * D;
        
        if (lenSq == 0) {
            // Source and target are the same point
            return Math.sqrt(A * A + B * B) <= tolerance;
        }
        
        double param = dot / lenSq;
        
        double xx, yy;
        if (param < 0) {
            xx = sourceX;
            yy = sourceY;
        } else if (param > 1) {
            xx = targetX;
            yy = targetY;
        } else {
            xx = sourceX + param * C;
            yy = sourceY + param * D;
        }
        
        double dx = px - xx;
        double dy = py - yy;
        return Math.sqrt(dx * dx + dy * dy) <= tolerance;
    }
    
    /**
     * Get a description of this connection for tooltips.
     */
    public String getDescription() {
        return String.format("%s connection: %s -> %s\nResource: %s",
            connectionType.getDisplayName(),
            getNodeName(sourcePath),
            getNodeName(targetPath),
            getResourceDescription(sourceResource)
        );
    }
    
    private String getNodeName(Path path) {
        return path.getFileName() != null ?
               path.getFileName().toString() :
               PathUtils.toUnixString(path);
    }
    
    private String getResourceDescription(ResourceIdentifier resource) {
        // This would need to be implemented based on the specific ResourceIdentifier type
        return resource.toString();
    }
    
    // Getters and setters
    public Path getSourcePath() {
        return sourcePath;
    }
    
    public ResourceIdentifier getSourceResource() {
        return sourceResource;
    }
    
    public Path getTargetPath() {
        return targetPath;
    }
    
    public ResourceIdentifier getTargetResource() {
        return targetResource;
    }
    
    public ConnectionType getConnectionType() {
        return connectionType;
    }
    
    public double getSourceX() {
        return sourceX;
    }
    
    public void setSourceX(double sourceX) {
        this.sourceX = sourceX;
    }
    
    public double getSourceY() {
        return sourceY;
    }
    
    public void setSourceY(double sourceY) {
        this.sourceY = sourceY;
    }
    
    public double getTargetX() {
        return targetX;
    }
    
    public void setTargetX(double targetX) {
        this.targetX = targetX;
    }
    
    public double getTargetY() {
        return targetY;
    }
    
    public void setTargetY(double targetY) {
        this.targetY = targetY;
    }
    
    public boolean isHovered() {
        return hovered;
    }
    
    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }
    
    /**
     * Types of connections between nodes.
     */
    public enum ConnectionType {
        DIRECT("Direct"),           // Black lines for direct dependencies
        CONDITIONAL("Conditional"), // Green lines for conditional dependencies  
        FLYWIRE("Flywire");        // Blue lines for flywires
        
        private final String displayName;
        
        ConnectionType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}
