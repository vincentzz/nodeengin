package me.vincentzz.visual.view;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.scope.Exclude;
import me.vincentzz.graph.scope.Include;
import me.vincentzz.graph.scope.Scope;
import me.vincentzz.visual.util.ColorScheme;

import java.nio.file.Path;
import java.util.*;

/**
 * Dialog for editing the scope (exports) of a NodeGroup.
 */
public class EditScopeDialog extends Dialog<Scope<ConnectionPoint>> {

    private ToggleGroup modeGroup;
    private RadioButton includeRadio;
    private RadioButton excludeRadio;
    private final Map<ConnectionPoint, CheckBox> checkBoxMap = new LinkedHashMap<>();

    public EditScopeDialog(Set<CalculationNode> childNodes, Scope<ConnectionPoint> currentScope) {
        initializeDialog();
        setupContent(childNodes, currentScope);
        setupButtonTypes();
        setupResultConverter();
        styleDialog();
    }

    private void initializeDialog() {
        setTitle("Edit Scope");
        setHeaderText("Configure which outputs are exported from this group");
        setResizable(true);
    }

    private void setupContent(Set<CalculationNode> childNodes, Scope<ConnectionPoint> currentScope) {
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        // Mode selection
        Label modeLabel = new Label("Scope Mode:");
        modeLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        modeLabel.setTextFill(ColorScheme.TEXT_PRIMARY);

        modeGroup = new ToggleGroup();
        includeRadio = new RadioButton("Include (whitelist — only checked items are exported)");
        includeRadio.setToggleGroup(modeGroup);
        includeRadio.setTextFill(ColorScheme.TEXT_PRIMARY);

        excludeRadio = new RadioButton("Exclude (blacklist — checked items are hidden)");
        excludeRadio.setToggleGroup(modeGroup);
        excludeRadio.setTextFill(ColorScheme.TEXT_PRIMARY);

        boolean isInclude = currentScope instanceof Include;
        includeRadio.setSelected(isInclude);
        excludeRadio.setSelected(!isInclude);

        // Determine currently-scoped ConnectionPoints
        Set<ConnectionPoint> scopedPoints = isInclude
            ? ((Include<ConnectionPoint>) currentScope).resources()
            : ((Exclude<ConnectionPoint>) currentScope).resources();

        // Connection points list
        Label cpLabel = new Label("Connection Points:");
        cpLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        cpLabel.setTextFill(ColorScheme.TEXT_PRIMARY);

        VBox cpContainer = new VBox(4);
        // Build all possible ConnectionPoints from child node outputs
        List<ConnectionPoint> allPoints = new ArrayList<>();
        for (CalculationNode child : childNodes) {
            for (ResourceIdentifier rid : child.outputs()) {
                allPoints.add(ConnectionPoint.of(Path.of(child.name()), rid));
            }
        }
        allPoints.sort(Comparator.comparing(cp -> cp.nodePath().toString() + cp.rid().toString()));

        for (ConnectionPoint cp : allPoints) {
            CheckBox cb = new CheckBox(cp.nodePath() + " : " + cp.rid());
            cb.setTextFill(ColorScheme.TEXT_PRIMARY);
            cb.setSelected(scopedPoints.contains(cp));
            checkBoxMap.put(cp, cb);
            cpContainer.getChildren().add(cb);
        }

        ScrollPane scrollPane = new ScrollPane(cpContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(300);
        scrollPane.setStyle(
            "-fx-background: " + toHexString(ColorScheme.BACKGROUND_MEDIUM) + ";" +
            "-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_MEDIUM) + ";"
        );

        content.getChildren().addAll(
            modeLabel, includeRadio, excludeRadio,
            cpLabel, scrollPane
        );
        getDialogPane().setContent(content);
    }

    private void setupButtonTypes() {
        ButtonType applyType = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(applyType, cancelType);

        Button applyBtn = (Button) getDialogPane().lookupButton(applyType);
        Button cancelBtn = (Button) getDialogPane().lookupButton(cancelType);
        styleButton(applyBtn);
        styleButton(cancelBtn);
    }

    private void setupResultConverter() {
        setResultConverter(dialogButton -> {
            if (dialogButton.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                Set<ConnectionPoint> selected = new HashSet<>();
                for (var entry : checkBoxMap.entrySet()) {
                    if (entry.getValue().isSelected()) {
                        selected.add(entry.getKey());
                    }
                }
                if (includeRadio.isSelected()) {
                    return Include.of(selected);
                } else {
                    return Exclude.of(selected);
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
