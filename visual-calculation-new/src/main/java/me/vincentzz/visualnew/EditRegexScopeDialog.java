package me.vincentzz.visualnew;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.scope.*;
import me.vincentzz.visual.util.ColorScheme;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Dialog for editing scope using RegExMatch patterns on ConnectionPoint fields.
 * Allows specifying regex patterns for nodePath and rid fields.
 */
public class EditRegexScopeDialog extends Dialog<Scope<ConnectionPoint>> {

    private ToggleGroup modeGroup;
    private RadioButton includeRadio;
    private RadioButton excludeRadio;

    private TextField nodePathField;
    private TextField ridField;

    // Dynamic rid sub-fields (populated from the current scope if available)
    private final Map<String, TextField> ridSubFields = new LinkedHashMap<>();

    private Label validationLabel;

    public EditRegexScopeDialog(Scope<ConnectionPoint> currentScope) {
        initializeDialog();
        setupContent(currentScope);
        setupButtonTypes();
        setupResultConverter();
        styleDialog();
    }

    private void initializeDialog() {
        setTitle("Edit Scope — Regex Match");
        setHeaderText("Define regex patterns to match ConnectionPoints.\nAll patterns must match (AND logic). Leave blank to match all.");
        setResizable(true);
    }

    private void setupContent(Scope<ConnectionPoint> currentScope) {
        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.setPrefWidth(500);

        // Mode selection
        Label modeLabel = new Label("Scope Mode:");
        modeLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        modeLabel.setTextFill(ColorScheme.TEXT_PRIMARY);

        modeGroup = new ToggleGroup();
        includeRadio = new RadioButton("Include (whitelist — matching items are exported)");
        includeRadio.setToggleGroup(modeGroup);
        includeRadio.setTextFill(ColorScheme.TEXT_PRIMARY);

        excludeRadio = new RadioButton("Exclude (blacklist — matching items are hidden)");
        excludeRadio.setToggleGroup(modeGroup);
        excludeRadio.setTextFill(ColorScheme.TEXT_PRIMARY);

        boolean isInclude = currentScope instanceof Include;
        includeRadio.setSelected(isInclude);
        excludeRadio.setSelected(!isInclude);

        // Extract existing RegExMatch patterns if present
        Map<String, String> existingPatterns = extractExistingPatterns(currentScope);

        // Regex fields
        Label fieldsLabel = new Label("Regex Patterns (Java regex syntax):");
        fieldsLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        fieldsLabel.setTextFill(ColorScheme.TEXT_PRIMARY);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        int row = 0;

        // nodePath field
        Label nodePathLabel = new Label("nodePath:");
        nodePathLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        nodePathField = new TextField(existingPatterns.getOrDefault("nodePath", ""));
        nodePathField.setPromptText("e.g. .*Provider.*");
        nodePathField.setPrefWidth(300);
        styleTextField(nodePathField);
        grid.add(nodePathLabel, 0, row);
        grid.add(nodePathField, 1, row);
        row++;

        // rid field (matches rid.toString())
        Label ridLabel = new Label("rid:");
        ridLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        ridField = new TextField(existingPatterns.getOrDefault("rid", ""));
        ridField.setPromptText("e.g. .*Ask.*");
        ridField.setPrefWidth(300);
        styleTextField(ridField);
        grid.add(ridLabel, 0, row);
        grid.add(ridField, 1, row);
        row++;

        // Separator for rid sub-fields
        Label subFieldsLabel = new Label("ResourceIdentifier sub-fields (if applicable):");
        subFieldsLabel.setFont(Font.font("System", FontWeight.BOLD, 11));
        subFieldsLabel.setTextFill(ColorScheme.TEXT_SECONDARY);
        grid.add(subFieldsLabel, 0, row, 2, 1);
        row++;

        // Common FalconRawTopic fields
        String[] ridSubFieldNames = {"symbol", "source", "attribute"};
        for (String fieldName : ridSubFieldNames) {
            Label fieldLabel = new Label("  rid." + fieldName + ":");
            fieldLabel.setTextFill(ColorScheme.TEXT_SECONDARY);
            TextField tf = new TextField(existingPatterns.getOrDefault(fieldName, ""));
            tf.setPromptText("e.g. .*");
            tf.setPrefWidth(300);
            styleTextField(tf);
            ridSubFields.put(fieldName, tf);
            grid.add(fieldLabel, 0, row);
            grid.add(tf, 1, row);
            row++;
        }

        // Validation label
        validationLabel = new Label("");
        validationLabel.setTextFill(javafx.scene.paint.Color.web("#FF6666"));
        validationLabel.setWrapText(true);

        // Help text
        Label helpLabel = new Label(
                "Patterns use Java regex syntax. " +
                "Only non-empty fields are included in the matcher. " +
                "All specified patterns must match for a ConnectionPoint to be in scope.");
        helpLabel.setTextFill(ColorScheme.TEXT_MUTED);
        helpLabel.setWrapText(true);
        helpLabel.setFont(Font.font("System", 11));

        content.getChildren().addAll(
                modeLabel, includeRadio, excludeRadio,
                new Separator(),
                fieldsLabel, grid,
                validationLabel,
                new Separator(),
                helpLabel
        );
        getDialogPane().setContent(content);
    }

    private Map<String, String> extractExistingPatterns(Scope<ConnectionPoint> scope) {
        ScopeSet<ConnectionPoint> ss;
        if (scope instanceof Include<ConnectionPoint> inc) {
            ss = inc.scopeSet();
        } else if (scope instanceof Exclude<ConnectionPoint> exc) {
            ss = exc.scopeSet();
        } else {
            return Map.of();
        }

        if (ss instanceof RegExMatch<ConnectionPoint> rem) {
            return rem.fieldMatcher();
        }
        return Map.of();
    }

    private void setupButtonTypes() {
        ButtonType applyType = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(applyType, cancelType);

        Button applyBtn = (Button) getDialogPane().lookupButton(applyType);
        Button cancelBtn = (Button) getDialogPane().lookupButton(cancelType);
        styleButton(applyBtn);
        styleButton(cancelBtn);

        // Validate on apply
        applyBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String error = validatePatterns();
            if (error != null) {
                validationLabel.setText(error);
                event.consume();
            }
        });
    }

    private String validatePatterns() {
        // Validate all non-empty fields are valid regex
        if (!nodePathField.getText().isBlank()) {
            try {
                Pattern.compile(nodePathField.getText().trim());
            } catch (PatternSyntaxException e) {
                return "Invalid regex for nodePath: " + e.getDescription();
            }
        }
        if (!ridField.getText().isBlank()) {
            try {
                Pattern.compile(ridField.getText().trim());
            } catch (PatternSyntaxException e) {
                return "Invalid regex for rid: " + e.getDescription();
            }
        }
        for (var entry : ridSubFields.entrySet()) {
            if (!entry.getValue().getText().isBlank()) {
                try {
                    Pattern.compile(entry.getValue().getText().trim());
                } catch (PatternSyntaxException e) {
                    return "Invalid regex for " + entry.getKey() + ": " + e.getDescription();
                }
            }
        }

        // At least one pattern must be specified
        boolean hasAny = !nodePathField.getText().isBlank() || !ridField.getText().isBlank();
        if (!hasAny) {
            for (TextField tf : ridSubFields.values()) {
                if (!tf.getText().isBlank()) {
                    hasAny = true;
                    break;
                }
            }
        }
        if (!hasAny) {
            return "At least one regex pattern must be specified.";
        }

        return null;
    }

    private void setupResultConverter() {
        setResultConverter(dialogButton -> {
            if (dialogButton.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                Map<String, String> fieldMatcher = new LinkedHashMap<>();

                if (!nodePathField.getText().isBlank()) {
                    fieldMatcher.put("nodePath", nodePathField.getText().trim());
                }
                if (!ridField.getText().isBlank()) {
                    fieldMatcher.put("rid", ridField.getText().trim());
                }
                for (var entry : ridSubFields.entrySet()) {
                    if (!entry.getValue().getText().isBlank()) {
                        fieldMatcher.put(entry.getKey(), entry.getValue().getText().trim());
                    }
                }

                RegExMatch<ConnectionPoint> regExMatch = new RegExMatch<>(fieldMatcher);

                if (includeRadio.isSelected()) {
                    return Include.of(regExMatch);
                } else {
                    return Exclude.of(regExMatch);
                }
            }
            return null;
        });
    }

    private void styleDialog() {
        DialogPane dialogPane = getDialogPane();
        dialogPane.setStyle(
                "-fx-background-color: " + toHexString(ColorScheme.NAV_BACKGROUND) + ";" +
                "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";"
        );
    }

    private void styleTextField(TextField textField) {
        textField.setStyle(
                "-fx-control-inner-background: " + toHexString(ColorScheme.BACKGROUND_DARK) + ";" +
                "-fx-text-fill: " + toHexString(ColorScheme.TEXT_PRIMARY) + ";" +
                "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";" +
                "-fx-border-width: 1px;" +
                "-fx-font-family: 'Courier New', monospace;" +
                "-fx-font-size: 12px;"
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
