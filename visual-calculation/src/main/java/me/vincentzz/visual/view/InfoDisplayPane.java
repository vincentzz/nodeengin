package me.vincentzz.visual.view;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import me.vincentzz.graph.model.AdhocOverride;
import me.vincentzz.graph.model.EvaluationResult;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.node.Flywire;
import me.vincentzz.lang.Result.Result;
import me.vincentzz.visual.util.ColorScheme;

import java.nio.file.Path;
import java.util.Map;

/**
 * Bottom panel that displays original request details and final result values from EvaluationResult.
 */
public class InfoDisplayPane extends VBox {
    
    private static final double PREFERRED_HEIGHT = 120.0;
    private static final int MAX_DISPLAY_ITEMS = 20; // Limit displayed items to avoid UI clutter
    
    private EvaluationResult evaluationResult;
    
    // UI Components
    private Label requestPathLabel;
    private TextArea adhocOverrideArea;
    private TextArea finalResultsArea;
    
    public InfoDisplayPane() {
        initializeComponents();
        setupLayout();
        styleComponents();
    }
    
    private void initializeComponents() {
        // Request path section
        requestPathLabel = new Label("No request loaded");
        
        // Adhoc overrides section
        adhocOverrideArea = new TextArea();
        adhocOverrideArea.setEditable(false);
        adhocOverrideArea.setWrapText(true);
        // Remove setPrefRowCount to allow full expansion
        
        // Final results section
        finalResultsArea = new TextArea();
        finalResultsArea.setEditable(false);
        finalResultsArea.setWrapText(true);
        // Remove setPrefRowCount to allow full expansion
    }
    
    private void setupLayout() {
        setPrefHeight(PREFERRED_HEIGHT);
        // Remove setMaxHeight to allow vertical resizing
        setPadding(new Insets(3));
        setSpacing(3);
        
        // Create split pane for adhoc overrides and final results
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        
        // Left side: Request
        VBox adhocSection = new VBox(2);
        adhocSection.setStyle("-fx-background-color: " + toHexString(ColorScheme.NAV_BACKGROUND) + ";");
        Label adhocLabel = new Label("Request");
        adhocLabel.setFont(Font.font("System", 10));
        adhocLabel.getStyleClass().add("title"); // Use CSS class for cyan color and bold styling
        adhocSection.getChildren().addAll(adhocLabel, adhocOverrideArea);
        VBox.setVgrow(adhocOverrideArea, Priority.ALWAYS);
        // Set the section to grow within the SplitPane
        HBox.setHgrow(adhocSection, Priority.ALWAYS);
        
        // Right side: Result
        VBox resultsSection = new VBox(2);
        resultsSection.setStyle("-fx-background-color: " + toHexString(ColorScheme.NAV_BACKGROUND) + ";");
        Label resultsLabel = new Label("Result");
        resultsLabel.setFont(Font.font("System", 10));
        resultsLabel.getStyleClass().add("title"); // Use CSS class for cyan color and bold styling
        resultsSection.getChildren().addAll(resultsLabel, finalResultsArea);
        VBox.setVgrow(finalResultsArea, Priority.ALWAYS);
        // Set the section to grow within the SplitPane
        HBox.setHgrow(resultsSection, Priority.ALWAYS);
        
        splitPane.getItems().addAll(adhocSection, resultsSection);
        splitPane.setDividerPositions(0.5);
        
        // Add split pane directly (no separate request path section)
        getChildren().add(splitPane);
        
        // Ensure the split pane fills the parent completely
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        HBox.setHgrow(splitPane, Priority.ALWAYS);
    }
    
    private VBox createRequestPathSection() {
        VBox section = new VBox(5);
        Label pathLabel = new Label("Requested Node Path:");
        pathLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        section.getChildren().addAll(pathLabel, requestPathLabel);
        return section;
    }
    
    private void styleComponents() {
        // Apply dark theme styling
        setStyle("-fx-background-color: " + toHexString(ColorScheme.NAV_BACKGROUND) + ";");
        
        requestPathLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        requestPathLabel.setFont(Font.font("System", 11));
        
        styleTextArea(adhocOverrideArea);
        styleTextArea(finalResultsArea);
    }
    
    private void styleTextArea(TextArea textArea) {
        textArea.setStyle(
            "-fx-control-inner-background: " + toHexString(ColorScheme.BACKGROUND_DARK) + ";" +
            "-fx-text-fill: " + toHexString(ColorScheme.TEXT_PRIMARY) + ";" +
            "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";" +
            "-fx-border-width: 1px;" +
            "-fx-font-family: 'Courier New', monospace;" +
            "-fx-font-size: 10px;"
        );
    }
    
    /**
     * Update the display with new EvaluationResult data.
     */
    public void updateEvaluationResult(EvaluationResult evaluationResult) {
        this.evaluationResult = evaluationResult;
        updateDisplay();
    }
    
    private void updateDisplay() {
        if (evaluationResult == null) {
            requestPathLabel.setText("No request loaded");
            adhocOverrideArea.setText("No adhoc overrides");
            finalResultsArea.setText("No results");
            return;
        }
        
        updateRequestPath();
        updateAdhocOverrides();
        updateFinalResults();
    }
    
    private void updateRequestPath() {
        Path requestedPath = evaluationResult.requestedNodePath();
        requestPathLabel.setText(requestedPath != null ? requestedPath.toString() : "Unknown");
    }
    
    private void updateAdhocOverrides() {
        StringBuilder sb = new StringBuilder();
        
        // Add requested node path at the top of the left pane
        Path requestedPath = evaluationResult.requestedNodePath();
        sb.append("Requested Node Path: ").append(requestedPath != null ? requestedPath.toString() : "Unknown").append("\n\n");
        
        // Add requested resource IDs section
        sb.append("Requested Resource IDs:\n");
        if (evaluationResult.results().isEmpty()) {
            sb.append("  (empty)\n\n");
        } else {
            int count = 0;
            for (Map.Entry<ResourceIdentifier, Result<Object>> entry : evaluationResult.results().entrySet()) {
                if (count >= MAX_DISPLAY_ITEMS) {
                    sb.append("... and ").append(evaluationResult.results().size() - MAX_DISPLAY_ITEMS).append(" more\n");
                    break;
                }
                sb.append("  ").append(formatResourceIdentifierFull(entry.getKey())).append("\n");
                count++;
            }
            sb.append("\n");
        }
        
        if (evaluationResult.adhocOverride().isEmpty()) {
            sb.append("Adhoc Inputs:\n  (empty)\n\n");
            sb.append("Adhoc Outputs:\n  (empty)\n\n");
            sb.append("Adhoc Flywires:\n  (empty)");
        } else {
            AdhocOverride override = evaluationResult.adhocOverride().get();
            
            // Adhoc Inputs - always show section
            sb.append("Adhoc Inputs:\n");
            if (override.adhocInputs().isEmpty()) {
                sb.append("  (empty)\n\n");
            } else {
                int count = 0;
                for (Map.Entry<ConnectionPoint, Result<Object>> entry : override.adhocInputs().entrySet()) {
                    if (count >= MAX_DISPLAY_ITEMS) {
                        sb.append("... and ").append(override.adhocInputs().size() - MAX_DISPLAY_ITEMS).append(" more\n");
                        break;
                    }
                    sb.append("  ").append(formatConnectionPoint(entry.getKey()))
                      .append("\n    Value: ").append(formatResult(entry.getValue())).append("\n\n");
                    count++;
                }
            }
            
            // Adhoc Outputs - always show section
            sb.append("Adhoc Outputs:\n");
            if (override.adhocOutputs().isEmpty()) {
                sb.append("  (empty)\n\n");
            } else {
                int count = 0;
                for (Map.Entry<ConnectionPoint, Result<Object>> entry : override.adhocOutputs().entrySet()) {
                    if (count >= MAX_DISPLAY_ITEMS) {
                        sb.append("... and ").append(override.adhocOutputs().size() - MAX_DISPLAY_ITEMS).append(" more\n");
                        break;
                    }
                    sb.append("  ").append(formatConnectionPoint(entry.getKey()))
                      .append("\n    Value: ").append(formatResult(entry.getValue())).append("\n\n");
                    count++;
                }
            }
            
            // Adhoc Flywires - always show section
            sb.append("Adhoc Flywires:\n");
            if (override.adhocFlywires().isEmpty()) {
                sb.append("  (empty)");
            } else {
                int count = 0;
                for (Flywire flywire : override.adhocFlywires()) {
                    if (count >= MAX_DISPLAY_ITEMS) {
                        sb.append("... and ").append(override.adhocFlywires().size() - MAX_DISPLAY_ITEMS).append(" more\n");
                        break;
                    }
                    // Properly indent all lines of flywire output
                    String flywireText = formatFlywire(flywire);
                    String[] lines = flywireText.split("\n");
                    for (String line : lines) {
                        sb.append("  ").append(line).append("\n");
                    }
                    sb.append("\n");
                    count++;
                }
            }
        }
        
        adhocOverrideArea.setText(sb.toString());
    }
    
    private void updateFinalResults() {
        StringBuilder sb = new StringBuilder();
        
        if (evaluationResult.results().isEmpty()) {
            sb.append("No final results");
        } else {
            sb.append("Final Results (").append(evaluationResult.results().size()).append(" items):\n\n");
            
            int count = 0;
            for (Map.Entry<ResourceIdentifier, Result<Object>> entry : evaluationResult.results().entrySet()) {
                if (count >= MAX_DISPLAY_ITEMS) {
                    sb.append("... and ").append(evaluationResult.results().size() - MAX_DISPLAY_ITEMS).append(" more items\n");
                    break;
                }
                
                sb.append(formatResourceIdentifierFull(entry.getKey()))
                  .append(":\n  ")
                  .append(formatResult(entry.getValue()))
                  .append("\n\n");
                count++;
            }
        }
        
        finalResultsArea.setText(sb.toString());
    }
    
    private String formatConnectionPoint(ConnectionPoint cp) {
        return String.format("NodePath: %s\n    ResourceId: %s", 
            cp.nodePath(),
            formatResourceIdentifierFull(cp.rid()));
    }
    
    private String formatFlywire(Flywire flywire) {
        // Create aligned multiline display for source and target
        StringBuilder sb = new StringBuilder();
        
        // Format source - ensure consistent 9-character width for label
        sb.append("Source:  ").append(formatConnectionPointInline(flywire.source())).append("\n");
        sb.append("         ").append(formatResourceIdentifierFull(flywire.source().rid())).append("\n");
        
        // Format target - ensure consistent 9-character width for label  
        sb.append("Target:  ").append(formatConnectionPointInline(flywire.target())).append("\n");
        sb.append("         ").append(formatResourceIdentifierFull(flywire.target().rid()));
        
        return sb.toString();
    }
    
    private String formatConnectionPointInline(ConnectionPoint cp) {
        // Format connection point on a single line for flywire display
        return cp.nodePath().toString();
    }
    
    private String formatResourceIdentifier(ResourceIdentifier rid) {
        // Try to extract meaningful information from the resource identifier
        String ridStr = rid.toString();
        
        // Look for common patterns and simplify display
        if (ridStr.contains("FalconResourceId")) {
            // Extract ifo, source, attribute from FalconResourceId
            String simplified = ridStr
                .replaceAll(".*ifo=([^,\\]]+).*", "$1")
                .replaceAll(".*source=([^,\\]]+).*", "$1") 
                .replaceAll(".*attribute=([^,\\]]+).*", "$1");
            
            if (!simplified.equals(ridStr)) {
                return simplified;
            }
        }
        
        return ridStr.length() > 60 ? ridStr.substring(0, 57) + "..." : ridStr;
    }
    
    private String formatResourceIdentifierFull(ResourceIdentifier rid) {
        // Return full ResourceIdentifier information without truncation
        String ridStr = rid.toString();
        
        // Parse FalconResourceId for detailed display
        if (ridStr.contains("FalconResourceId")) {
            try {
                // Extract structured information
                String ifo = ridStr.replaceAll(".*ifo=([^,\\]]+).*", "$1");
                String source = ridStr.replaceAll(".*source=([^,\\]]+).*", "$1");
                String attribute = ridStr.replaceAll(".*attribute=([^,\\]]+).*", "$1");
                
                // Clean up attribute class name
                if (attribute.startsWith("class ")) {
                    attribute = attribute.substring(6); // Remove "class " prefix
                    // Extract just the class name (after last dot)
                    int lastDot = attribute.lastIndexOf('.');
                    if (lastDot >= 0) {
                        attribute = attribute.substring(lastDot + 1);
                    }
                }
                
                return String.format("FalconResourceId[ifo=%s, source=%s, attribute=%s]", ifo, source, attribute);
            } catch (Exception e) {
                // Fall back to original string if parsing fails
                return ridStr;
            }
        }
        
        return ridStr;
    }
    
    private String formatResult(Result<Object> result) {
        if (result == null) {
            return "null";
        }
        
        if (result.isSuccess()) {
            Object data = result.get();
            if (data == null) {
                return "null";
            }
            String dataStr = data.toString();
            return dataStr.length() > 100 ? dataStr.substring(0, 97) + "..." : dataStr;
        } else {
            Exception ex = result.getException();
            return "Error: " + (ex != null ? ex.getMessage() : "Unknown error");
        }
    }
    
    /**
     * Clear the display.
     */
    public void clear() {
        this.evaluationResult = null;
        updateDisplay();
    }
    
    /**
     * Convert JavaFX Color to hex string for CSS.
     */
    private String toHexString(javafx.scene.paint.Color color) {
        return String.format("#%02X%02X%02X",
            (int) (color.getRed() * 255),
            (int) (color.getGreen() * 255),
            (int) (color.getBlue() * 255));
    }
}
