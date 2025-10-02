package me.vincentzz.visual.util;

import javafx.scene.paint.Color;
import me.vincentzz.graph.model.ResourceIdentifier;

/**
 * Unreal 5-inspired color scheme for the visual calculation interface.
 * Provides consistent colors throughout the application.
 */
public class ColorScheme {
    
    // Background colors
    public static final Color BACKGROUND_DARK = Color.web("#2d2d2d");
    public static final Color BACKGROUND_MEDIUM = Color.web("#3e3e3e");
    public static final Color BACKGROUND_LIGHT = Color.web("#4a4a4a");
    
    // Node colors
    public static final Color NODE_BACKGROUND = Color.web("#404040");
    public static final Color NODE_BORDER = Color.web("#606060");
    public static final Color NODE_SELECTED = Color.web("#0078d4");
    public static final Color NODE_HOVER = Color.web("#555555");
    
    // Connection point default color for backward compatibility
    public static final Color CONNECTION_DEFAULT = Color.web("#ffffff");   // White for other attributes
    
    // Dynamic color manager instance (set when EvaluationResult is loaded)
    private static DynamicColorManager dynamicColorManager = null;
    
    // Connection line colors
    public static final Color CONNECTION_DIRECT = Color.BLACK;             // Black for direct dependencies
    public static final Color CONNECTION_CONDITIONAL = Color.web("#32cd32"); // Green for conditional dependencies
    public static final Color CONNECTION_FLYWIRE = Color.web("#1e90ff");   // Blue for flywires
    
    // Text colors
    public static final Color TEXT_PRIMARY = Color.web("#ffffff");
    public static final Color TEXT_SECONDARY = Color.web("#b0b0b0");
    public static final Color TEXT_MUTED = Color.web("#808080");
    
    // Navigation colors
    public static final Color NAV_BACKGROUND = Color.web("#363636");
    public static final Color NAV_BORDER = Color.web("#505050");
    public static final Color NAV_ACTIVE = Color.web("#0078d4");
    
    // Status colors
    public static final Color SUCCESS = Color.web("#32cd32");
    public static final Color ERROR = Color.web("#ff4444");
    public static final Color WARNING = Color.web("#ffaa00");
    
    /**
     * Set the dynamic color manager for runtime color assignment.
     */
    public static void setDynamicColorManager(DynamicColorManager colorManager) {
        dynamicColorManager = colorManager;
    }
    
    /**
     * Get connection point color based on attribute type.
     * Uses dynamic color assignment if available, otherwise falls back to default.
     */
    public static Color getConnectionPointColor(String attribute) {
        if (dynamicColorManager != null) {
            return dynamicColorManager.getConnectionPointColorByName(attribute);
        }
        
        // Fallback to default color if no dynamic color manager is set
        return CONNECTION_DEFAULT;
    }
    
    /**
     * Get connection point color for a ResourceIdentifier.
     * Uses dynamic color assignment if available, otherwise falls back to default.
     */
    public static Color getConnectionPointColor(ResourceIdentifier resourceId) {
        if (dynamicColorManager != null) {
            return dynamicColorManager.getConnectionPointColor(resourceId);
        }
        
        // Fallback to default color if no dynamic color manager is set
        return CONNECTION_DEFAULT;
    }
    
    /**
     * Get connection line color based on connection type.
     */
    public static Color getConnectionLineColor(ConnectionType type) {
        return switch (type) {
            case DIRECT -> CONNECTION_DIRECT;
            case CONDITIONAL -> CONNECTION_CONDITIONAL;
            case FLYWIRE -> CONNECTION_FLYWIRE;
        };
    }
    
    /**
     * Types of connections between nodes.
     */
    public enum ConnectionType {
        DIRECT,      // Direct dependencies (black lines)
        CONDITIONAL, // Conditional dependencies (green lines)
        FLYWIRE      // Flywires (blue lines)
    }
}
