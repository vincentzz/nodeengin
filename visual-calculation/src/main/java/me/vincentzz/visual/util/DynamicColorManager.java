package me.vincentzz.visual.util;

import javafx.scene.paint.Color;
import me.vincentzz.graph.model.EvaluationResult;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.falcon.ifo.FalconResourceId;
import me.vincentzz.lang.Result.Result;

import java.util.*;

/**
 * Dynamic color manager that analyzes EvaluationResult JSON content
 * and assigns colors to connection points based on the distinct attribute types found.
 * This replaces the hardcoded color scheme with a runtime-generated one.
 */
public class DynamicColorManager {
    
    private final Map<Class<?>, Color> attributeColorMap;
    private final Color defaultColor;
    
    public DynamicColorManager(EvaluationResult evaluationResult) {
        this.defaultColor = Color.web("#ffffff"); // White for unknown types
        this.attributeColorMap = new HashMap<>();
        
        // Analyze the evaluation result and build the dynamic color mapping
        analyzeAndBuildColorMapping(evaluationResult);
    }
    
    /**
     * Analyze the EvaluationResult to extract all distinct attribute types
     * and generate a color palette for them.
     */
    private void analyzeAndBuildColorMapping(EvaluationResult evaluationResult) {
        Set<Class<?>> distinctAttributeTypes = extractDistinctAttributeTypes(evaluationResult);
        
        // Generate colors for each distinct attribute type
        List<Color> colorPalette = generateColorPalette(distinctAttributeTypes.size());
        
        // Assign colors to attribute types
        List<Class<?>> sortedAttributes = new ArrayList<>(distinctAttributeTypes);
        sortedAttributes.sort(Comparator.comparing(Class::getSimpleName));
        
        for (int i = 0; i < sortedAttributes.size(); i++) {
            attributeColorMap.put(sortedAttributes.get(i), colorPalette.get(i));
        }
        
        System.out.println("Dynamic Color Mapping Generated:");
        for (Map.Entry<Class<?>, Color> entry : attributeColorMap.entrySet()) {
            System.out.printf("  %s -> %s%n", 
                entry.getKey().getSimpleName(), 
                colorToHex(entry.getValue()));
        }
    }
    
    /**
     * Extract all distinct attribute types from the EvaluationResult.
     */
    private Set<Class<?>> extractDistinctAttributeTypes(EvaluationResult evaluationResult) {
        Set<Class<?>> attributeTypes = new HashSet<>();
        
        // Extract from nodeEvaluationMap (inputs and outputs)
        for (me.vincentzz.graph.model.NodeEvaluation nodeEvaluation : evaluationResult.nodeEvaluationMap().values()) {
            // Extract from inputs
            for (ResourceIdentifier resourceId : nodeEvaluation.inputs().keySet()) {
                if (resourceId instanceof FalconResourceId falconId) {
                    attributeTypes.add(falconId.attribute());
                }
            }
            
            // Extract from outputs
            for (ResourceIdentifier resourceId : nodeEvaluation.outputs().keySet()) {
                if (resourceId instanceof FalconResourceId falconId) {
                    attributeTypes.add(falconId.attribute());
                }
            }
        }
        
        // Extract from results
        for (Map.Entry<ResourceIdentifier, Result<Object>> result : evaluationResult.results().entrySet()) {
            ResourceIdentifier resourceId = result.getKey();
            if (resourceId instanceof FalconResourceId falconId) {
                attributeTypes.add(falconId.attribute());
            }
        }
        
        // Extract from adhocOverride if present
        if (evaluationResult.adhocOverride().isPresent()) {
            var adhocOverride = evaluationResult.adhocOverride().get();
            
            // adhocInputs
            for (var input : adhocOverride.adhocInputs().entrySet()) {
                if (input.getKey().rid() instanceof FalconResourceId falconId) {
                    attributeTypes.add(falconId.attribute());
                }
            }
            
            // adhocOutputs
            for (var output : adhocOverride.adhocOutputs().entrySet()) {
                if (output.getKey().rid() instanceof FalconResourceId falconId) {
                    attributeTypes.add(falconId.attribute());
                }
            }
            
            // adhocFlywires
            for (var flywire : adhocOverride.adhocFlywires()) {
                if (flywire.source().rid() instanceof FalconResourceId falconId) {
                    attributeTypes.add(falconId.attribute());
                }
                if (flywire.target().rid() instanceof FalconResourceId falconId) {
                    attributeTypes.add(falconId.attribute());
                }
            }
        }
        
        return attributeTypes;
    }
    
    /**
     * Generate a visually distinct color palette for the given number of types.
     * Uses HSB color space to ensure good visual separation.
     */
    private List<Color> generateColorPalette(int numColors) {
        List<Color> colors = new ArrayList<>();
        
        if (numColors == 0) {
            return colors;
        }
        
        // Use predefined high-contrast colors for common financial types first
        List<Color> predefinedColors = Arrays.asList(
            Color.web("#44ff44"), // Green  
            Color.web("#4444ff"), // Blue
            Color.web("#ffaa00"), // Orange
            Color.web("#ff44ff"), // Magenta
            Color.web("#44ffff"), // Cyan
            Color.web("#ffff44"), // Yellow
            Color.web("#aa44ff"), // Purple
            Color.web("#ff8844"), // Light Orange
            Color.web("#44ff88"),  // Light Green
            Color.web("#ff4444")   // Red
        );
        
        // Use predefined colors first
        for (int i = 0; i < Math.min(numColors, predefinedColors.size()); i++) {
            colors.add(predefinedColors.get(i));
        }
        
        // Generate additional colors using HSB if needed
        for (int i = predefinedColors.size(); i < numColors; i++) {
            double hue = (360.0 * i / numColors) % 360.0;
            double saturation = 0.8 + (0.2 * (i % 2)); // Alternate between 0.8 and 1.0
            double brightness = 0.9;
            
            colors.add(Color.hsb(hue, saturation, brightness));
        }
        
        return colors;
    }
    
    /**
     * Get the color for a specific ResourceIdentifier.
     */
    public Color getConnectionPointColor(ResourceIdentifier resourceId) {
        if (resourceId instanceof FalconResourceId falconId) {
            return attributeColorMap.getOrDefault(falconId.attribute(), defaultColor);
        }
        
        // For non-FalconResourceId types, use the default color
        return defaultColor;
    }
    
    /**
     * Get the color for a specific attribute type.
     */
    public Color getConnectionPointColor(Class<?> attributeType) {
        return attributeColorMap.getOrDefault(attributeType, defaultColor);
    }
    
    /**
     * Get color mapping for debugging/display purposes.
     */
    public Map<Class<?>, Color> getAttributeColorMapping() {
        return new HashMap<>(attributeColorMap);
    }
    
    /**
     * Get the number of distinct attribute types found.
     */
    public int getDistinctAttributeCount() {
        return attributeColorMap.size();
    }
    
    /**
     * Convert Color to hex string for debugging.
     */
    private String colorToHex(Color color) {
        return String.format("#%02x%02x%02x", 
            (int) Math.round(color.getRed() * 255),
            (int) Math.round(color.getGreen() * 255),
            (int) Math.round(color.getBlue() * 255));
    }
    
    /**
     * Backward compatibility method for string-based attribute lookup.
     * This provides a fallback for cases where we only have attribute name as string.
     */
    public Color getConnectionPointColorByName(String attributeName) {
        if (attributeName == null) {
            return defaultColor;
        }
        
        // Try to find a matching attribute type by simple name
        for (Map.Entry<Class<?>, Color> entry : attributeColorMap.entrySet()) {
            if (entry.getKey().getSimpleName().equalsIgnoreCase(attributeName)) {
                return entry.getValue();
            }
        }
        
        // If no exact match, try partial matching
        String lowerAttr = attributeName.toLowerCase();
        for (Map.Entry<Class<?>, Color> entry : attributeColorMap.entrySet()) {
            String className = entry.getKey().getSimpleName().toLowerCase();
            if (lowerAttr.contains(className) || className.contains(lowerAttr)) {
                return entry.getValue();
            }
        }
        
        return defaultColor;
    }
}
