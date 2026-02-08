package me.vincentzz.visual.view;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.node.Flywire;
import me.vincentzz.falcon.ifo.FalconResourceId;
import me.vincentzz.visual.util.ColorScheme;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dialog for creating a new Flywire connection between two nodes.
 * Target input supports both selecting from declared inputs (dropdown)
 * and free-text entry for non-direct dependencies.
 */
public class CreateFlywireDialog extends Dialog<Flywire> {

    private static final List<String> ATTRIBUTE_CLASSES = List.of(
        "Ask", "Bid", "MidPrice", "Spread", "Volume", "Vwap", "MarkToMarket"
    );
    private static final String ATTRIBUTE_PACKAGE = "me.vincentzz.falcon.attribute.";

    private final Map<String, CalculationNode> nodesByName;

    private ComboBox<String> sourceNodeCombo;
    private ComboBox<ResourceIdentifier> sourceRidCombo;
    private ComboBox<String> targetNodeCombo;

    // Target: dropdown mode
    private RadioButton targetFromInputsRadio;
    private ComboBox<ResourceIdentifier> targetRidCombo;

    // Target: custom mode
    private RadioButton targetCustomRadio;
    private TextField targetIfoField;
    private TextField targetSourceField;
    private ComboBox<String> targetAttributeCombo;

    public CreateFlywireDialog(Set<CalculationNode> childNodes) {
        this.nodesByName = childNodes.stream()
            .collect(Collectors.toMap(CalculationNode::name, n -> n));

        initializeDialog();
        setupContent();
        setupButtonTypes();
        setupResultConverter();
        styleDialog();
    }

    /**
     * Constructor with pre-filled source and target information.
     * Used when creating a flywire from a canvas double-click interaction.
     */
    public CreateFlywireDialog(Set<CalculationNode> childNodes,
                               String preSelectedSourceNode,
                               ResourceIdentifier preSelectedSourceResource,
                               String preSelectedTargetNode) {
        this(childNodes);

        if (preSelectedSourceNode != null) {
            sourceNodeCombo.setValue(preSelectedSourceNode);
            sourceNodeCombo.getOnAction().handle(null);
            if (preSelectedSourceResource != null) {
                sourceRidCombo.setValue(preSelectedSourceResource);
            }
        }

        if (preSelectedTargetNode != null) {
            targetNodeCombo.setValue(preSelectedTargetNode);
            targetNodeCombo.getOnAction().handle(null);

            // Pre-select matching declared input if it exists
            if (preSelectedSourceResource != null) {
                for (ResourceIdentifier rid : targetRidCombo.getItems()) {
                    if (rid.equals(preSelectedSourceResource)) {
                        targetRidCombo.setValue(rid);
                        break;
                    }
                }
            }
        }

        // Pre-fill custom ResourceId fields from source resource
        if (preSelectedSourceResource instanceof FalconResourceId frid) {
            targetIfoField.setText(frid.ifo());
            targetSourceField.setText(frid.source());
            String attrSimpleName = frid.attribute().getSimpleName();
            targetAttributeCombo.setValue(attrSimpleName);
            targetAttributeCombo.setDisable(true);
        }
    }

    private void initializeDialog() {
        setTitle("Add Flywire");
        setHeaderText("Create a connection between two nodes");
        setResizable(true);
    }

    private void setupContent() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        List<String> nodeNames = new ArrayList<>(nodesByName.keySet());
        Collections.sort(nodeNames);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        // === Source section ===
        Label srcHeader = new Label("Source");
        srcHeader.setFont(Font.font("System", FontWeight.BOLD, 12));
        srcHeader.setTextFill(ColorScheme.TEXT_PRIMARY);

        Label srcNodeLabel = new Label("Node:");
        srcNodeLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        sourceNodeCombo = new ComboBox<>(FXCollections.observableArrayList(nodeNames));

        Label srcRidLabel = new Label("Output:");
        srcRidLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        sourceRidCombo = new ComboBox<>();
        sourceRidCombo.setPrefWidth(300);

        sourceNodeCombo.setOnAction(e -> {
            String name = sourceNodeCombo.getValue();
            if (name != null && nodesByName.containsKey(name)) {
                CalculationNode node = nodesByName.get(name);
                sourceRidCombo.setItems(FXCollections.observableArrayList(node.outputs()));
            }
        });

        // === Target section ===
        Label tgtHeader = new Label("Target");
        tgtHeader.setFont(Font.font("System", FontWeight.BOLD, 12));
        tgtHeader.setTextFill(ColorScheme.TEXT_PRIMARY);

        Label tgtNodeLabel = new Label("Node:");
        tgtNodeLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        targetNodeCombo = new ComboBox<>(FXCollections.observableArrayList(nodeNames));

        // Radio buttons to switch between dropdown and custom input
        ToggleGroup targetToggle = new ToggleGroup();
        targetFromInputsRadio = new RadioButton("From declared inputs");
        targetFromInputsRadio.setToggleGroup(targetToggle);
        targetFromInputsRadio.setSelected(true);
        targetFromInputsRadio.setTextFill(ColorScheme.TEXT_PRIMARY);

        targetCustomRadio = new RadioButton("Custom ResourceId");
        targetCustomRadio.setToggleGroup(targetToggle);
        targetCustomRadio.setTextFill(ColorScheme.TEXT_PRIMARY);

        // Dropdown mode
        Label tgtRidLabel = new Label("Input:");
        tgtRidLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        targetRidCombo = new ComboBox<>();
        targetRidCombo.setPrefWidth(300);

        targetNodeCombo.setOnAction(e -> {
            String name = targetNodeCombo.getValue();
            if (name != null && nodesByName.containsKey(name)) {
                CalculationNode node = nodesByName.get(name);
                targetRidCombo.setItems(FXCollections.observableArrayList(node.inputs()));
            }
        });

        // Custom mode fields
        Label tgtIfoLabel = new Label("IFO:");
        tgtIfoLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        targetIfoField = new TextField();
        targetIfoField.setPromptText("e.g. GOOGLE");
        styleTextField(targetIfoField);

        Label tgtSourceLabel = new Label("Source:");
        tgtSourceLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        targetSourceField = new TextField();
        targetSourceField.setPromptText("e.g. FALCON");
        styleTextField(targetSourceField);

        Label tgtAttrLabel = new Label("Attribute:");
        tgtAttrLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        targetAttributeCombo = new ComboBox<>(FXCollections.observableArrayList(ATTRIBUTE_CLASSES));
        targetAttributeCombo.setEditable(true);
        targetAttributeCombo.setPrefWidth(300);

        // Toggle visibility
        setCustomFieldsEnabled(false);
        targetToggle.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            boolean custom = newVal == targetCustomRadio;
            setCustomFieldsEnabled(custom);
            targetRidCombo.setDisable(custom);
        });

        // Layout
        int row = 0;
        grid.add(srcHeader, 0, row, 2, 1); row++;
        grid.add(srcNodeLabel, 0, row);
        grid.add(sourceNodeCombo, 1, row); row++;
        grid.add(srcRidLabel, 0, row);
        grid.add(sourceRidCombo, 1, row); row++;

        grid.add(tgtHeader, 0, row, 2, 1); row++;
        grid.add(tgtNodeLabel, 0, row);
        grid.add(targetNodeCombo, 1, row); row++;
        grid.add(targetFromInputsRadio, 0, row, 2, 1); row++;
        grid.add(tgtRidLabel, 0, row);
        grid.add(targetRidCombo, 1, row); row++;

        // Separator
        Separator sep = new Separator();
        grid.add(sep, 0, row, 2, 1); row++;

        grid.add(targetCustomRadio, 0, row, 2, 1); row++;
        grid.add(tgtIfoLabel, 0, row);
        grid.add(targetIfoField, 1, row); row++;
        grid.add(tgtSourceLabel, 0, row);
        grid.add(targetSourceField, 1, row); row++;
        grid.add(tgtAttrLabel, 0, row);
        grid.add(targetAttributeCombo, 1, row);

        content.getChildren().add(grid);
        getDialogPane().setContent(content);
    }

    private void setCustomFieldsEnabled(boolean enabled) {
        targetIfoField.setDisable(!enabled);
        targetSourceField.setDisable(!enabled);
        targetAttributeCombo.setDisable(!enabled);
    }

    private void setupButtonTypes() {
        ButtonType createType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(createType, cancelType);

        Button createBtn = (Button) getDialogPane().lookupButton(createType);
        Button cancelBtn = (Button) getDialogPane().lookupButton(cancelType);
        styleButton(createBtn);
        styleButton(cancelBtn);
    }

    private void setupResultConverter() {
        setResultConverter(dialogButton -> {
            if (dialogButton.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                try {
                    String srcNode = sourceNodeCombo.getValue();
                    ResourceIdentifier srcRid = sourceRidCombo.getValue();
                    String tgtNode = targetNodeCombo.getValue();

                    if (srcNode == null || srcRid == null || tgtNode == null) {
                        return null;
                    }

                    ResourceIdentifier tgtRid;
                    if (targetFromInputsRadio.isSelected()) {
                        tgtRid = targetRidCombo.getValue();
                        if (tgtRid == null) return null;
                    } else {
                        // Build FalconResourceId from custom fields
                        String ifo = targetIfoField.getText();
                        String source = targetSourceField.getText();
                        String attrName = targetAttributeCombo.getValue();
                        if (ifo == null || ifo.isBlank() || source == null || source.isBlank()
                                || attrName == null || attrName.isBlank()) {
                            return null;
                        }
                        Class<?> attrClass = resolveAttributeClass(attrName);
                        tgtRid = FalconResourceId.of(ifo, source, attrClass);
                    }

                    ConnectionPoint sourcePoint = ConnectionPoint.of(Path.of(srcNode), srcRid);
                    ConnectionPoint targetPoint = ConnectionPoint.of(Path.of(tgtNode), tgtRid);
                    return Flywire.of(sourcePoint, targetPoint);
                } catch (Exception e) {
                    new Alert(Alert.AlertType.ERROR, "Failed to create flywire: " + e.getMessage())
                        .showAndWait();
                    return null;
                }
            }
            return null;
        });
    }

    private Class<?> resolveAttributeClass(String name) throws ClassNotFoundException {
        // Try the known attribute package first
        try {
            return Class.forName(ATTRIBUTE_PACKAGE + name);
        } catch (ClassNotFoundException e) {
            // Try as fully qualified class name
            return Class.forName(name);
        }
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
