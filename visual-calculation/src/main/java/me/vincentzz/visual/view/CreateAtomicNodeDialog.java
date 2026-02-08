package me.vincentzz.visual.view;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import me.vincentzz.graph.json.NodeTypeRegistry;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.visual.util.ColorScheme;

import java.lang.reflect.RecordComponent;
import java.util.*;

/**
 * Dialog for creating new atomic nodes with construction parameters.
 */
public class CreateAtomicNodeDialog extends Dialog<Map<String, String>> {
    
    private GridPane parameterGrid;
    private Map<String, TextField> parameterFields = new HashMap<>();
    private String atomicNodeClass;
    
    public CreateAtomicNodeDialog(String atomicNodeClass) {
        this.atomicNodeClass = atomicNodeClass;
        
        initializeDialog();
        setupContent();
        setupButtonTypes();
        setupResultConverter();
        styleDialog();
    }
    
    private void initializeDialog() {
        setTitle("Create " + atomicNodeClass);
        setHeaderText("Enter construction parameters for " + atomicNodeClass);
        setResizable(true);
    }
    
    private void setupContent() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        
        Label instructionLabel = new Label("Specify the parameters needed to create this atomic node:");
        instructionLabel.setFont(Font.font("System", 12));
        instructionLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        
        parameterGrid = new GridPane();
        parameterGrid.setHgap(10);
        parameterGrid.setVgap(10);
        parameterGrid.setPadding(new Insets(10));
        
        // Add common parameters based on atomic node class
        addParametersForNodeClass(atomicNodeClass);
        
        content.getChildren().addAll(instructionLabel, parameterGrid);
        getDialogPane().setContent(content);
    }
    
    private void addParametersForNodeClass(String nodeClass) {
        // Derive constructor parameters dynamically from record components
        try {
            Class<? extends CalculationNode> clazz = NodeTypeRegistry.getNodeClass(nodeClass);
            if (clazz.isRecord()) {
                RecordComponent[] components = clazz.getRecordComponents();
                for (RecordComponent component : components) {
                    String paramName = component.getName();
                    String paramType = component.getType().getSimpleName();
                    addParameter(paramName, paramName + " (" + paramType + ")", "");
                }
                return;
            }
        } catch (Exception e) {
            // Fallback if reflection fails or type not registered
        }

        // Fallback for unregistered types
        addParameter("param1", "Parameter 1", "");
        addParameter("param2", "Parameter 2", "");
    }
    
    private void addParameter(String paramName, String labelText, String defaultValue) {
        int row = parameterGrid.getRowCount();
        
        Label paramLabel = new Label(labelText + ":");
        paramLabel.setFont(Font.font("System", FontWeight.BOLD, 11));
        paramLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        
        TextField paramField = new TextField(defaultValue);
        paramField.setPrefWidth(200);
        styleTextField(paramField);
        
        parameterFields.put(paramName, paramField);
        
        parameterGrid.add(paramLabel, 0, row);
        parameterGrid.add(paramField, 1, row);
    }
    
    private void setupButtonTypes() {
        ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        
        getDialogPane().getButtonTypes().addAll(createButtonType, cancelButtonType);
        
        // Style buttons
        Button createButton = (Button) getDialogPane().lookupButton(createButtonType);
        Button cancelButton = (Button) getDialogPane().lookupButton(cancelButtonType);
        
        styleButton(createButton);
        styleButton(cancelButton);
    }
    
    private void setupResultConverter() {
        setResultConverter(dialogButton -> {
            if (dialogButton.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                Map<String, String> result = new HashMap<>();
                for (Map.Entry<String, TextField> entry : parameterFields.entrySet()) {
                    result.put(entry.getKey(), entry.getValue().getText());
                }
                return result;
            }
            return null;
        });
    }
    
    private void styleDialog() {
        // Style the dialog pane
        DialogPane dialogPane = getDialogPane();
        dialogPane.setStyle(
            "-fx-background-color: " + toHexString(ColorScheme.NAV_BACKGROUND) + ";" +
            "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";"
        );
        
        // Style the parameter grid
        parameterGrid.setStyle(
            "-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_DARK) + ";" +
            "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";" +
            "-fx-border-width: 1px;"
        );
    }
    
    private void styleTextField(TextField textField) {
        textField.setStyle(
            "-fx-control-inner-background: " + toHexString(ColorScheme.BACKGROUND_DARK) + ";" +
            "-fx-text-fill: " + toHexString(ColorScheme.TEXT_PRIMARY) + ";" +
            "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";" +
            "-fx-border-width: 1px;" +
            "-fx-font-size: 11px;"
        );
    }
    
    private void styleButton(Button button) {
        button.setStyle(
            "-fx-background-color: " + toHexString(ColorScheme.NODE_BACKGROUND) + ";" +
            "-fx-text-fill: " + toHexString(ColorScheme.TEXT_PRIMARY) + ";" +
            "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";" +
            "-fx-border-width: 1px;" +
            "-fx-font-size: 11px;"
        );
    }
    
    private String toHexString(javafx.scene.paint.Color color) {
        return String.format("#%02X%02X%02X",
            (int) (color.getRed() * 255),
            (int) (color.getGreen() * 255),
            (int) (color.getBlue() * 255));
    }
}
