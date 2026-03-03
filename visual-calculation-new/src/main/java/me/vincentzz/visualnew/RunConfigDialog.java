package me.vincentzz.visualnew;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.collections.FXCollections;
import me.vincentzz.graph.json.ConstructionalJsonUtil;
import me.vincentzz.graph.json.NodeTypeRegistry;
import me.vincentzz.graph.model.AdhocOverride;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.falcon.rid.FalconRawTopic;
import me.vincentzz.graph.model.Snapshot;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.node.Flywire;
import me.vincentzz.graph.node.NodeGroup;
import me.vincentzz.lang.PathUtils;
import me.vincentzz.lang.Result.Result;
import me.vincentzz.lang.Result.Success;
import me.vincentzz.visual.util.ColorScheme;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Dialog for editing the evaluation request configuration before running.
 * Uses intuitive UI controls instead of raw JSON editing.
 */
public class RunConfigDialog extends Dialog<RunConfigDialog.RunConfig> {

    public enum Action { RUN, OK, APPLY, CANCEL }

    public record RunConfig(
            Action action,
            Snapshot snapshot,
            Path requestedNodePath,
            Set<ResourceIdentifier> requestedResources,
            Optional<AdhocOverride> adhocOverride
    ) {}

    private static final DateTimeFormatter DT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    // Snapshot controls
    private CheckBox logicalNowCheckBox;
    private TextField logicalTimestampField;
    private CheckBox physicalNowCheckBox;
    private TextField physicalTimestampField;

    // Path
    private TextField pathField;

    // Resources — checkbox list
    private final Map<ResourceIdentifier, CheckBox> resourceCheckBoxes = new LinkedHashMap<>();

    // Resources — container for dynamic rebuild
    private VBox resourceCheckboxContainer;
    private Label pathStatusLabel;

    // Adhoc override — structured lists
    private VBox adhocInputsList;
    private VBox adhocOutputsList;
    private VBox adhocFlywiresList;
    private final List<AdhocEntry> adhocInputEntries = new ArrayList<>();
    private final List<AdhocEntry> adhocOutputEntries = new ArrayList<>();
    private final List<FlywireEntry> adhocFlywireEntries = new ArrayList<>();

    private Label validationLabel;

    private record AdhocEntry(ConnectionPoint connectionPoint, String valueText) {}
    private record FlywireEntry(ConnectionPoint source, ConnectionPoint target) {}

    private final Snapshot currentSnapshot;
    private final Path currentPath;
    private final Set<ResourceIdentifier> currentResources;
    private final Optional<AdhocOverride> currentOverride;
    private final CalculationNode graph;
    private final Map<Path, CalculationNode> pathToNodeIndex;

    public RunConfigDialog(Snapshot snapshot, Path requestedNodePath,
                           Set<ResourceIdentifier> requestedResources,
                           Optional<AdhocOverride> adhocOverride,
                           CalculationNode graph) {
        this.currentSnapshot = snapshot;
        this.currentPath = requestedNodePath;
        this.currentResources = requestedResources;
        this.currentOverride = adhocOverride;
        this.graph = graph;
        this.pathToNodeIndex = buildPathIndex(graph);

        setTitle("Run Configuration");
        setHeaderText(null);
        setResizable(true);

        setupContent();
        setupButtons();
        setupResultConverter();
        styleDialog();

        getDialogPane().setPrefWidth(720);
        getDialogPane().setPrefHeight(750);
    }

    private void setupContent() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(14));
        root.setStyle("-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_DARK) + ";");

        // === Snapshot ===
        root.getChildren().add(createSectionHeader("Snapshot"));
        root.getChildren().add(buildSnapshotSection());
        root.getChildren().add(createSeparator());

        // === Requested Node Path ===
        root.getChildren().add(createSectionHeader("Requested Node Path"));
        root.getChildren().add(createMutedLabel("Leave empty to use root path: " + PathUtils.toUnixString(getRootNodePath())));
        pathField = new TextField(PathUtils.toUnixString(currentPath));
        pathField.setPromptText(PathUtils.toUnixString(getRootNodePath()));
        pathField.setFont(Font.font("Courier New", 12));
        styleTextField(pathField);
        root.getChildren().add(pathField);

        pathStatusLabel = new Label("");
        pathStatusLabel.setFont(Font.font("System", 10));
        pathStatusLabel.setWrapText(true);
        root.getChildren().add(pathStatusLabel);
        updatePathStatus(pathField.getText());

        // Listen for path changes to refresh resources
        pathField.textProperty().addListener((obs, oldVal, newVal) -> {
            updatePathStatus(newVal);
            refreshResourceCheckboxes(newVal);
        });

        root.getChildren().add(createSeparator());

        // === Requested Resources ===
        root.getChildren().add(createSectionHeader("Requested Resources"));
        root.getChildren().add(createMutedLabel("Select which outputs to request from the graph:"));
        resourceCheckboxContainer = new VBox();
        root.getChildren().add(resourceCheckboxContainer);
        buildResourcesInto(resourceCheckboxContainer, resolveOutputsForPath(pathField.getText()));
        root.getChildren().add(createSeparator());

        // === Adhoc Override ===
        root.getChildren().add(createSectionHeader("Adhoc Override"));

        // --- Adhoc Inputs ---
        root.getChildren().add(buildAdhocSubSection("Adhoc Inputs", true));
        root.getChildren().add(createSeparator());

        // --- Adhoc Outputs ---
        root.getChildren().add(buildAdhocSubSection("Adhoc Outputs", false));
        root.getChildren().add(createSeparator());

        // --- Adhoc Flywires ---
        root.getChildren().add(buildAdhocFlywireSection());

        // Validation
        validationLabel = new Label("");
        validationLabel.setTextFill(Color.web("#FF6666"));
        validationLabel.setWrapText(true);
        root.getChildren().add(validationLabel);

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle(
                "-fx-background: " + toHexString(ColorScheme.BACKGROUND_DARK) + ";" +
                "-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_DARK) + ";"
        );
        getDialogPane().setContent(scrollPane);
    }

    // ===== Snapshot Section =====

    private VBox buildSnapshotSection() {
        VBox box = new VBox(6);
        box.setPadding(new Insets(0, 0, 0, 8));

        // Logical Timestamp
        HBox logicalRow = new HBox(8);
        logicalRow.setAlignment(Pos.CENTER_LEFT);

        Label logicalLabel = new Label("Logical Timestamp:");
        logicalLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        logicalLabel.setFont(Font.font("System", FontWeight.BOLD, 11));
        logicalLabel.setMinWidth(130);

        logicalNowCheckBox = new CheckBox("Now (empty)");
        logicalNowCheckBox.setTextFill(ColorScheme.TEXT_SECONDARY);
        logicalNowCheckBox.setFont(Font.font("System", 10));

        logicalTimestampField = new TextField();
        logicalTimestampField.setPromptText("yyyy-MM-dd HH:mm:ss");
        logicalTimestampField.setPrefWidth(200);
        styleTextField(logicalTimestampField);

        if (currentSnapshot.logicalTimestamp().isEmpty()) {
            logicalNowCheckBox.setSelected(true);
            logicalTimestampField.setDisable(true);
        } else {
            logicalNowCheckBox.setSelected(false);
            logicalTimestampField.setText(DT_FORMAT.format(currentSnapshot.logicalTimestamp().get()));
        }

        logicalNowCheckBox.setOnAction(e -> {
            logicalTimestampField.setDisable(logicalNowCheckBox.isSelected());
            if (logicalNowCheckBox.isSelected()) logicalTimestampField.clear();
        });

        logicalRow.getChildren().addAll(logicalLabel, logicalTimestampField, logicalNowCheckBox);

        // Physical Timestamp
        HBox physicalRow = new HBox(8);
        physicalRow.setAlignment(Pos.CENTER_LEFT);

        Label physicalLabel = new Label("Physical Timestamp:");
        physicalLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        physicalLabel.setFont(Font.font("System", FontWeight.BOLD, 11));
        physicalLabel.setMinWidth(130);

        physicalNowCheckBox = new CheckBox("Now (empty)");
        physicalNowCheckBox.setTextFill(ColorScheme.TEXT_SECONDARY);
        physicalNowCheckBox.setFont(Font.font("System", 10));

        physicalTimestampField = new TextField();
        physicalTimestampField.setPromptText("yyyy-MM-dd HH:mm:ss");
        physicalTimestampField.setPrefWidth(200);
        styleTextField(physicalTimestampField);

        if (currentSnapshot.physicalTimestamp().isEmpty()) {
            physicalNowCheckBox.setSelected(true);
            physicalTimestampField.setDisable(true);
        } else {
            physicalNowCheckBox.setSelected(false);
            physicalTimestampField.setText(DT_FORMAT.format(currentSnapshot.physicalTimestamp().get()));
        }

        physicalNowCheckBox.setOnAction(e -> {
            physicalTimestampField.setDisable(physicalNowCheckBox.isSelected());
            if (physicalNowCheckBox.isSelected()) physicalTimestampField.clear();
        });

        physicalRow.getChildren().addAll(physicalLabel, physicalTimestampField, physicalNowCheckBox);

        box.getChildren().addAll(logicalRow, physicalRow);
        return box;
    }

    // ===== Resources Section =====

    private void buildResourcesInto(VBox container, Set<ResourceIdentifier> outputs) {
        container.getChildren().clear();
        resourceCheckBoxes.clear();

        VBox box = new VBox(4);

        // Select All / Deselect All buttons
        HBox buttonRow = new HBox(8);
        buttonRow.setPadding(new Insets(0, 0, 4, 0));

        Button selectAllBtn = new Button("Select All");
        selectAllBtn.setOnAction(e -> resourceCheckBoxes.values().forEach(cb -> cb.setSelected(true)));
        styleSmallButton(selectAllBtn);

        Button deselectAllBtn = new Button("Deselect All");
        deselectAllBtn.setOnAction(e -> resourceCheckBoxes.values().forEach(cb -> cb.setSelected(false)));
        styleSmallButton(deselectAllBtn);

        buttonRow.getChildren().addAll(selectAllBtn, deselectAllBtn);
        box.getChildren().add(buttonRow);

        if (outputs.isEmpty()) {
            Label emptyLabel = createMutedLabel("  No outputs available at this path.");
            box.getChildren().add(emptyLabel);
            container.getChildren().add(box);
            return;
        }

        // Build sorted list of outputs
        List<ResourceIdentifier> sortedOutputs = new ArrayList<>(outputs);
        sortedOutputs.sort(Comparator.comparing(this::formatResourceId));

        VBox checkboxList = new VBox(3);
        checkboxList.setPadding(new Insets(0, 0, 0, 4));

        for (ResourceIdentifier rid : sortedOutputs) {
            HBox row = new HBox(6);
            row.setAlignment(Pos.CENTER_LEFT);

            CheckBox cb = new CheckBox();
            cb.setSelected(currentResources != null && currentResources.contains(rid));

            Circle dot = new Circle(3, getResourceColor(rid));

            Label label = new Label(formatResourceId(rid));
            label.setFont(Font.font("Courier New", 11));
            label.setTextFill(ColorScheme.TEXT_PRIMARY);

            row.getChildren().addAll(cb, dot, label);
            checkboxList.getChildren().add(row);
            resourceCheckBoxes.put(rid, cb);
        }

        ScrollPane scrollPane = new ScrollPane(checkboxList);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(150);
        scrollPane.setMaxHeight(200);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle(
                "-fx-background: " + toHexString(ColorScheme.BACKGROUND_MEDIUM) + ";" +
                "-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_MEDIUM) + ";"
        );

        box.getChildren().add(scrollPane);
        container.getChildren().add(box);
    }

    private void refreshResourceCheckboxes(String pathText) {
        Set<ResourceIdentifier> outputs = resolveOutputsForPath(pathText);
        buildResourcesInto(resourceCheckboxContainer, outputs);
    }

    private void updatePathStatus(String pathText) {
        if (pathText == null || pathText.isBlank()) {
            pathStatusLabel.setText("Using root: " + PathUtils.toUnixString(getRootNodePath()));
            pathStatusLabel.setTextFill(Color.web("#44CC44"));
            return;
        }
        try {
            Path path = Paths.get(pathText.trim());
            CalculationNode node = pathToNodeIndex.get(path);
            if (node != null) {
                String type = (node instanceof NodeGroup) ? "NodeGroup" : "AtomicNode";
                pathStatusLabel.setText("Valid: " + node.name() + " (" + type + ", " + node.outputs().size() + " outputs)");
                pathStatusLabel.setTextFill(Color.web("#44CC44"));
            } else {
                pathStatusLabel.setText("Path not found in graph. Available: " + formatAvailablePaths());
                pathStatusLabel.setTextFill(Color.web("#FFAA00"));
            }
        } catch (Exception e) {
            pathStatusLabel.setText("Invalid path syntax");
            pathStatusLabel.setTextFill(Color.web("#FF6666"));
        }
    }

    private String formatAvailablePaths() {
        return pathToNodeIndex.keySet().stream()
                .map(PathUtils::toUnixString)
                .sorted()
                .limit(8)
                .collect(java.util.stream.Collectors.joining(", "))
                + (pathToNodeIndex.size() > 8 ? " ..." : "");
    }

    // ===== Adhoc Override Sections =====

    private VBox buildAdhocSubSection(String title, boolean isInput) {
        VBox section = new VBox(4);

        VBox list = isInput ? (adhocInputsList = new VBox(3)) : (adhocOutputsList = new VBox(3));
        List<AdhocEntry> entries = isInput ? adhocInputEntries : adhocOutputEntries;

        // Pre-populate from current override
        if (currentOverride.isPresent()) {
            var map = isInput ? currentOverride.get().adhocInputs() : currentOverride.get().adhocOutputs();
            for (var entry : map.entrySet()) {
                String valueStr = entry.getValue().isSuccess()
                        ? serializeValue(entry.getValue().get()) : "ERROR";
                entries.add(new AdhocEntry(entry.getKey(), valueStr));
            }
        }

        HBox headerRow = new HBox(8);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.getChildren().add(createSubSectionHeader(title));

        Button addBtn = new Button("+ Add");
        styleSmallButton(addBtn);
        addBtn.setOnAction(e -> showAdhocEntryDialog(isInput, -1));
        headerRow.getChildren().add(addBtn);
        section.getChildren().add(headerRow);

        refreshAdhocEntryList(list, entries, isInput);
        section.getChildren().add(list);
        return section;
    }

    private VBox buildAdhocFlywireSection() {
        VBox section = new VBox(4);

        adhocFlywiresList = new VBox(3);

        // Pre-populate from current override
        if (currentOverride.isPresent()) {
            for (Flywire fw : currentOverride.get().adhocFlywires()) {
                adhocFlywireEntries.add(new FlywireEntry(fw.source(), fw.target()));
            }
        }

        HBox headerRow = new HBox(8);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.getChildren().add(createSubSectionHeader("Adhoc Flywires"));

        Button addBtn = new Button("+ Add");
        styleSmallButton(addBtn);
        addBtn.setOnAction(e -> showFlywireDialog(-1));
        headerRow.getChildren().add(addBtn);
        section.getChildren().add(headerRow);

        refreshFlywireList();
        section.getChildren().add(adhocFlywiresList);
        return section;
    }

    private void refreshAdhocEntryList(VBox list, List<AdhocEntry> entries, boolean isInput) {
        list.getChildren().clear();
        if (entries.isEmpty()) {
            list.getChildren().add(createMutedLabel("  (none)"));
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            AdhocEntry entry = entries.get(i);
            int idx = i;

            HBox row = new HBox(6);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(2, 4, 2, 8));
            row.setStyle("-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_MEDIUM) + ";" +
                    "-fx-background-radius: 3;");

            Circle dot = new Circle(3, getResourceColor(entry.connectionPoint().rid()));

            Label cpLabel = new Label(PathUtils.toUnixString(entry.connectionPoint().nodePath())
                    + " : " + formatResourceId(entry.connectionPoint().rid()));
            cpLabel.setFont(Font.font("Courier New", 10));
            cpLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
            HBox.setHgrow(cpLabel, Priority.ALWAYS);

            String valuePreview = entry.valueText().replaceAll("\\s+", " ");
            if (valuePreview.length() > 60) valuePreview = valuePreview.substring(0, 57) + "...";
            Label valueLabel = new Label("= " + valuePreview);
            valueLabel.setFont(Font.font("Courier New", 10));
            valueLabel.setTextFill(Color.web("#44CC44"));
            valueLabel.setMaxWidth(250);

            Button editBtn = new Button("e");
            editBtn.setStyle(
                    "-fx-background-color: #4488CC; -fx-text-fill: white;" +
                    "-fx-font-size: 9px; -fx-padding: 0 4 0 4; -fx-background-radius: 3;");
            editBtn.setOnAction(e -> showAdhocEntryDialog(isInput, idx));

            Button removeBtn = new Button("x");
            removeBtn.setStyle(
                    "-fx-background-color: #CC4444; -fx-text-fill: white;" +
                    "-fx-font-size: 9px; -fx-padding: 0 4 0 4; -fx-background-radius: 3;");
            removeBtn.setOnAction(e -> {
                entries.remove(idx);
                refreshAdhocEntryList(list, entries, isInput);
            });

            row.getChildren().addAll(dot, cpLabel, valueLabel, editBtn, removeBtn);
            list.getChildren().add(row);
        }
    }

    private void refreshFlywireList() {
        adhocFlywiresList.getChildren().clear();
        if (adhocFlywireEntries.isEmpty()) {
            adhocFlywiresList.getChildren().add(createMutedLabel("  (none)"));
            return;
        }
        for (int i = 0; i < adhocFlywireEntries.size(); i++) {
            FlywireEntry entry = adhocFlywireEntries.get(i);
            int idx = i;

            HBox row = new HBox(4);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(2, 4, 2, 8));
            row.setStyle("-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_MEDIUM) + ";" +
                    "-fx-background-radius: 3;");

            Circle srcDot = new Circle(3, getResourceColor(entry.source().rid()));
            Label srcLabel = new Label(PathUtils.toUnixString(entry.source().nodePath())
                    + " : " + formatResourceId(entry.source().rid()));
            srcLabel.setFont(Font.font("Courier New", 10));
            srcLabel.setTextFill(ColorScheme.TEXT_PRIMARY);

            Label arrow = new Label(" → ");
            arrow.setFont(Font.font("System", FontWeight.BOLD, 10));
            arrow.setTextFill(Color.web("#66CCFF"));

            Circle tgtDot = new Circle(3, getResourceColor(entry.target().rid()));
            Label tgtLabel = new Label(PathUtils.toUnixString(entry.target().nodePath())
                    + " : " + formatResourceId(entry.target().rid()));
            tgtLabel.setFont(Font.font("Courier New", 10));
            tgtLabel.setTextFill(ColorScheme.TEXT_PRIMARY);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button editBtn = new Button("e");
            editBtn.setStyle(
                    "-fx-background-color: #4488CC; -fx-text-fill: white;" +
                    "-fx-font-size: 9px; -fx-padding: 0 4 0 4; -fx-background-radius: 3;");
            editBtn.setOnAction(e -> showFlywireDialog(idx));

            Button removeBtn = new Button("x");
            removeBtn.setStyle(
                    "-fx-background-color: #CC4444; -fx-text-fill: white;" +
                    "-fx-font-size: 9px; -fx-padding: 0 4 0 4; -fx-background-radius: 3;");
            removeBtn.setOnAction(e -> {
                adhocFlywireEntries.remove(idx);
                refreshFlywireList();
            });

            row.getChildren().addAll(srcDot, srcLabel, arrow, tgtDot, tgtLabel, spacer, editBtn, removeBtn);
            adhocFlywiresList.getChildren().add(row);
        }
    }

    // ===== Adhoc Entry Dialog (Add / Edit) =====

    private void showAdhocEntryDialog(boolean isInput, int editIndex) {
        List<AdhocEntry> entries = isInput ? adhocInputEntries : adhocOutputEntries;
        VBox list = isInput ? adhocInputsList : adhocOutputsList;
        boolean isEdit = editIndex >= 0;
        AdhocEntry existing = isEdit ? entries.get(editIndex) : null;

        Dialog<AdhocEntry> dialog = new Dialog<>();
        dialog.setTitle((isEdit ? "Edit" : "Add") + " Adhoc " + (isInput ? "Input" : "Output"));
        dialog.setHeaderText(null);
        dialog.setResizable(true);

        VBox content = new VBox(8);
        content.setPadding(new Insets(12));
        content.setStyle("-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_DARK) + ";");

        // Node Path combo
        content.getChildren().add(createSubSectionHeader("Node Path"));
        List<String> pathStrings = pathToNodeIndex.keySet().stream()
                .map(PathUtils::toUnixString).sorted().toList();
        ComboBox<String> pathCombo = new ComboBox<>(FXCollections.observableArrayList(pathStrings));
        pathCombo.setEditable(true);
        pathCombo.setMaxWidth(Double.MAX_VALUE);
        styleComboBox(pathCombo);
        content.getChildren().add(pathCombo);

        // Resource — combo for selection, or manual entry for inputs
        content.getChildren().add(createSubSectionHeader("Resource"));

        ComboBox<ResourceIdentifier> ridCombo = new ComboBox<>();
        ridCombo.setMaxWidth(Double.MAX_VALUE);
        ridCombo.setCellFactory(lv -> new ResourceIdListCell());
        ridCombo.setButtonCell(new ResourceIdListCell());
        styleComboBox(ridCombo);
        content.getChildren().add(ridCombo);

        // Manual entry fields (for inputs — conditional dependencies)
        VBox manualEntryBox = new VBox(4);
        manualEntryBox.setPadding(new Insets(4, 0, 0, 0));
        CheckBox manualCheckBox = new CheckBox("Manual entry (for conditional dependencies)");
        manualCheckBox.setTextFill(ColorScheme.TEXT_SECONDARY);
        manualCheckBox.setFont(Font.font("System", 10));

        HBox manualFields = new HBox(6);
        manualFields.setAlignment(Pos.CENTER_LEFT);
        TextField symbolField = new TextField();
        symbolField.setPromptText("symbol");
        HBox.setHgrow(symbolField, Priority.ALWAYS);
        styleTextField(symbolField);
        TextField sourceField = new TextField();
        sourceField.setPromptText("source");
        HBox.setHgrow(sourceField, Priority.ALWAYS);
        styleTextField(sourceField);
        ComboBox<String> attrCombo = new ComboBox<>(FXCollections.observableArrayList(
                collectKnownAttributeTypes()));
        attrCombo.setEditable(true);
        attrCombo.setPromptText("attribute");
        HBox.setHgrow(attrCombo, Priority.ALWAYS);
        attrCombo.setMaxWidth(Double.MAX_VALUE);
        styleComboBox(attrCombo);
        manualFields.getChildren().addAll(
                createMiniLabel("symbol"), symbolField,
                createMiniLabel("source"), sourceField,
                createMiniLabel("attr"), attrCombo);
        manualFields.setVisible(false);
        manualFields.setManaged(false);

        manualCheckBox.setOnAction(e -> {
            boolean manual = manualCheckBox.isSelected();
            manualFields.setVisible(manual);
            manualFields.setManaged(manual);
            ridCombo.setDisable(manual);
        });

        if (isInput) {
            manualEntryBox.getChildren().addAll(manualCheckBox, manualFields);
            content.getChildren().add(manualEntryBox);
        }

        // Update resource list when path changes
        pathCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            ridCombo.getItems().clear();
            if (newVal != null && !newVal.isBlank()) {
                try {
                    Path p = Paths.get(newVal.trim());
                    CalculationNode node = pathToNodeIndex.get(p);
                    if (node != null) {
                        Set<ResourceIdentifier> rids = isInput ? getAllResourcesForNode(node) : node.outputs();
                        List<ResourceIdentifier> sorted = new ArrayList<>(rids);
                        sorted.sort(Comparator.comparing(this::formatResourceId));
                        ridCombo.setItems(FXCollections.observableArrayList(sorted));
                    }
                } catch (Exception ignored) {}
            }
        });

        // Value (JSON)
        Label valueHeader = createSubSectionHeader("Value (JSON)");
        content.getChildren().add(valueHeader);

        Label typeHintLabel = new Label("");
        typeHintLabel.setFont(Font.font("System", 10));
        typeHintLabel.setTextFill(ColorScheme.TEXT_MUTED);
        content.getChildren().add(typeHintLabel);

        TextArea valueArea = new TextArea();
        valueArea.setPrefRowCount(6);
        valueArea.setWrapText(true);
        valueArea.setFont(Font.font("Courier New", 11));
        valueArea.setStyle(
                "-fx-control-inner-background: " + toHexString(ColorScheme.BACKGROUND_DARK) + ";" +
                "-fx-text-fill: " + toHexString(ColorScheme.TEXT_PRIMARY) + ";" +
                "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";" +
                "-fx-border-width: 1px;" +
                "-fx-font-family: 'Courier New', monospace;" +
                "-fx-font-size: 11px;"
        );
        content.getChildren().add(valueArea);

        // Generate JSON template when resource is selected
        ridCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && valueArea.getText().isBlank()) {
                Class<?> type = newVal.type();
                typeHintLabel.setText("Type: " + type.getSimpleName());
                valueArea.setText(generateJsonTemplate(type));
            } else if (newVal != null) {
                typeHintLabel.setText("Type: " + newVal.type().getSimpleName());
            }
        });

        // Update type hint and template when manual attribute type changes
        attrCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (manualCheckBox.isSelected() && newVal != null && !newVal.isBlank()) {
                Class<?> attrClass = NodeTypeRegistry.getValueClass(newVal.trim());
                if (attrClass != null) {
                    typeHintLabel.setText("Type: " + attrClass.getSimpleName());
                    if (valueArea.getText().isBlank()) {
                        valueArea.setText(generateJsonTemplate(attrClass));
                    }
                }
            }
        });

        // Pre-populate for edit mode
        if (existing != null) {
            pathCombo.setValue(PathUtils.toUnixString(existing.connectionPoint().nodePath()));
            javafx.application.Platform.runLater(() -> {
                ridCombo.setValue(existing.connectionPoint().rid());
                // Set value after rid is set so template doesn't overwrite
                javafx.application.Platform.runLater(() -> valueArea.setText(existing.valueText()));
            });
        }

        Label errorLabel = new Label("");
        errorLabel.setTextFill(Color.web("#FF6666"));
        errorLabel.setFont(Font.font("System", 10));
        errorLabel.setWrapText(true);
        content.getChildren().add(errorLabel);

        DialogPane dp = dialog.getDialogPane();
        dp.setContent(content);
        dp.setStyle("-fx-background-color: " + toHexString(ColorScheme.NAV_BACKGROUND) + ";" +
                "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";");
        dp.setPrefWidth(580);
        dp.setPrefHeight(520);

        ButtonType confirmType = new ButtonType(isEdit ? "Save" : "Add", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dp.getButtonTypes().addAll(confirmType, cancelType);
        styleButton((Button) dp.lookupButton(confirmType));
        styleButton((Button) dp.lookupButton(cancelType));

        ((Button) dp.lookupButton(confirmType)).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (pathCombo.getValue() == null || pathCombo.getValue().isBlank()) {
                errorLabel.setText("Please select a node path.");
                event.consume();
            } else if (isInput && manualCheckBox.isSelected()) {
                // Manual entry validation
                if (symbolField.getText().isBlank() || sourceField.getText().isBlank()) {
                    errorLabel.setText("Please fill in symbol and source fields.");
                    event.consume();
                } else if (attrCombo.getValue() == null || attrCombo.getValue().isBlank()) {
                    errorLabel.setText("Please select or enter an attribute type.");
                    event.consume();
                } else {
                    Class<?> attrClass = NodeTypeRegistry.getValueClass(attrCombo.getValue().trim());
                    if (attrClass == null) {
                        errorLabel.setText("Unknown attribute type: " + attrCombo.getValue().trim());
                        event.consume();
                    } else if (valueArea.getText().isBlank()) {
                        errorLabel.setText("Please enter a JSON value.");
                        event.consume();
                    } else {
                        try {
                            parseValueAsType(valueArea.getText().trim(), attrClass);
                        } catch (Exception ex) {
                            errorLabel.setText(ex.getMessage());
                            event.consume();
                        }
                    }
                }
            } else if (ridCombo.getValue() == null) {
                errorLabel.setText("Please select a resource.");
                event.consume();
            } else if (valueArea.getText().isBlank()) {
                errorLabel.setText("Please enter a JSON value.");
                event.consume();
            } else {
                // Validate JSON against the attribute type
                try {
                    parseValueAsType(valueArea.getText().trim(), ridCombo.getValue().type());
                } catch (Exception ex) {
                    errorLabel.setText(ex.getMessage());
                    event.consume();
                }
            }
        });

        dialog.setResultConverter(btn -> {
            if (btn.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                Path nodePath = Paths.get(pathCombo.getValue().trim());
                ResourceIdentifier rid;
                if (isInput && manualCheckBox.isSelected()) {
                    Class<?> attrClass = NodeTypeRegistry.getValueClass(attrCombo.getValue().trim());
                    rid = FalconRawTopic.of(symbolField.getText().trim(), sourceField.getText().trim(), attrClass);
                } else {
                    rid = ridCombo.getValue();
                }
                return new AdhocEntry(ConnectionPoint.of(nodePath, rid), valueArea.getText().trim());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(entry -> {
            if (isEdit) {
                entries.set(editIndex, entry);
            } else {
                entries.add(entry);
            }
            refreshAdhocEntryList(list, entries, isInput);
        });
    }

    private void showFlywireDialog(int editIndex) {
        boolean isEdit = editIndex >= 0;
        FlywireEntry existing = isEdit ? adhocFlywireEntries.get(editIndex) : null;

        Dialog<FlywireEntry> dialog = new Dialog<>();
        dialog.setTitle((isEdit ? "Edit" : "Add") + " Adhoc Flywire");
        dialog.setHeaderText(null);
        dialog.setResizable(true);

        VBox content = new VBox(8);
        content.setPadding(new Insets(12));
        content.setStyle("-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_DARK) + ";");

        List<String> pathStrings = pathToNodeIndex.keySet().stream()
                .map(PathUtils::toUnixString).sorted().toList();

        // === Source ===
        content.getChildren().add(createSectionHeader("Source"));

        content.getChildren().add(createSubSectionHeader("Node Path"));
        ComboBox<String> srcPathCombo = new ComboBox<>(FXCollections.observableArrayList(pathStrings));
        srcPathCombo.setEditable(true);
        srcPathCombo.setMaxWidth(Double.MAX_VALUE);
        styleComboBox(srcPathCombo);
        content.getChildren().add(srcPathCombo);

        content.getChildren().add(createSubSectionHeader("Resource (output)"));
        ComboBox<ResourceIdentifier> srcRidCombo = new ComboBox<>();
        srcRidCombo.setMaxWidth(Double.MAX_VALUE);
        srcRidCombo.setCellFactory(lv -> new ResourceIdListCell());
        srcRidCombo.setButtonCell(new ResourceIdListCell());
        styleComboBox(srcRidCombo);
        content.getChildren().add(srcRidCombo);

        srcPathCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            srcRidCombo.getItems().clear();
            if (newVal != null && !newVal.isBlank()) {
                try {
                    CalculationNode node = pathToNodeIndex.get(Paths.get(newVal.trim()));
                    if (node != null) {
                        List<ResourceIdentifier> sorted = new ArrayList<>(node.outputs());
                        sorted.sort(Comparator.comparing(this::formatResourceId));
                        srcRidCombo.setItems(FXCollections.observableArrayList(sorted));
                    }
                } catch (Exception ignored) {}
            }
        });

        content.getChildren().add(createSeparator());

        // === Target ===
        content.getChildren().add(createSectionHeader("Target"));

        content.getChildren().add(createSubSectionHeader("Node Path"));
        ComboBox<String> tgtPathCombo = new ComboBox<>(FXCollections.observableArrayList(pathStrings));
        tgtPathCombo.setEditable(true);
        tgtPathCombo.setMaxWidth(Double.MAX_VALUE);
        styleComboBox(tgtPathCombo);
        content.getChildren().add(tgtPathCombo);

        content.getChildren().add(createSubSectionHeader("Resource (input)"));
        ComboBox<ResourceIdentifier> tgtRidCombo = new ComboBox<>();
        tgtRidCombo.setMaxWidth(Double.MAX_VALUE);
        tgtRidCombo.setCellFactory(lv -> new ResourceIdListCell());
        tgtRidCombo.setButtonCell(new ResourceIdListCell());
        styleComboBox(tgtRidCombo);
        content.getChildren().add(tgtRidCombo);

        // Manual entry for target resource (conditional dependencies)
        VBox tgtManualBox = new VBox(4);
        tgtManualBox.setPadding(new Insets(4, 0, 0, 0));
        CheckBox tgtManualCheckBox = new CheckBox("Manual entry (for conditional dependencies)");
        tgtManualCheckBox.setTextFill(ColorScheme.TEXT_SECONDARY);
        tgtManualCheckBox.setFont(Font.font("System", 10));

        HBox tgtManualFields = new HBox(6);
        tgtManualFields.setAlignment(Pos.CENTER_LEFT);
        TextField tgtSymbolField = new TextField();
        tgtSymbolField.setPromptText("symbol");
        HBox.setHgrow(tgtSymbolField, Priority.ALWAYS);
        styleTextField(tgtSymbolField);
        TextField tgtSourceField = new TextField();
        tgtSourceField.setPromptText("source");
        HBox.setHgrow(tgtSourceField, Priority.ALWAYS);
        styleTextField(tgtSourceField);
        ComboBox<String> tgtAttrCombo = new ComboBox<>(FXCollections.observableArrayList(
                collectKnownAttributeTypes()));
        tgtAttrCombo.setEditable(true);
        tgtAttrCombo.setPromptText("attribute");
        HBox.setHgrow(tgtAttrCombo, Priority.ALWAYS);
        tgtAttrCombo.setMaxWidth(Double.MAX_VALUE);
        styleComboBox(tgtAttrCombo);
        tgtManualFields.getChildren().addAll(
                createMiniLabel("symbol"), tgtSymbolField,
                createMiniLabel("source"), tgtSourceField,
                createMiniLabel("attr"), tgtAttrCombo);
        tgtManualFields.setVisible(false);
        tgtManualFields.setManaged(false);

        tgtManualCheckBox.setOnAction(e -> {
            boolean manual = tgtManualCheckBox.isSelected();
            tgtManualFields.setVisible(manual);
            tgtManualFields.setManaged(manual);
            tgtRidCombo.setDisable(manual);
        });

        tgtManualBox.getChildren().addAll(tgtManualCheckBox, tgtManualFields);
        content.getChildren().add(tgtManualBox);

        tgtPathCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            tgtRidCombo.getItems().clear();
            if (newVal != null && !newVal.isBlank()) {
                try {
                    CalculationNode node = pathToNodeIndex.get(Paths.get(newVal.trim()));
                    if (node != null) {
                        List<ResourceIdentifier> sorted = new ArrayList<>(getAllResourcesForNode(node));
                        sorted.sort(Comparator.comparing(this::formatResourceId));
                        tgtRidCombo.setItems(FXCollections.observableArrayList(sorted));
                    }
                } catch (Exception ignored) {}
            }
        });

        // Pre-populate for edit mode
        if (existing != null) {
            srcPathCombo.setValue(PathUtils.toUnixString(existing.source().nodePath()));
            tgtPathCombo.setValue(PathUtils.toUnixString(existing.target().nodePath()));
            javafx.application.Platform.runLater(() -> {
                srcRidCombo.setValue(existing.source().rid());
                tgtRidCombo.setValue(existing.target().rid());
            });
        }

        Label errorLabel = new Label("");
        errorLabel.setTextFill(Color.web("#FF6666"));
        errorLabel.setFont(Font.font("System", 10));
        content.getChildren().add(errorLabel);

        DialogPane dp = dialog.getDialogPane();
        dp.setContent(content);
        dp.setStyle("-fx-background-color: " + toHexString(ColorScheme.NAV_BACKGROUND) + ";" +
                "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";");
        dp.setPrefWidth(580);
        dp.setPrefHeight(550);

        ButtonType confirmType = new ButtonType(isEdit ? "Save" : "Add", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dp.getButtonTypes().addAll(confirmType, cancelType);
        styleButton((Button) dp.lookupButton(confirmType));
        styleButton((Button) dp.lookupButton(cancelType));

        ((Button) dp.lookupButton(confirmType)).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (srcPathCombo.getValue() == null || srcRidCombo.getValue() == null) {
                errorLabel.setText("Please select source path and resource.");
                event.consume();
            } else if (tgtPathCombo.getValue() == null || tgtPathCombo.getValue().isBlank()) {
                errorLabel.setText("Please select a target node path.");
                event.consume();
            } else if (tgtManualCheckBox.isSelected()) {
                // Manual target resource validation
                if (tgtSymbolField.getText().isBlank() || tgtSourceField.getText().isBlank()) {
                    errorLabel.setText("Please fill in target symbol and source fields.");
                    event.consume();
                } else if (tgtAttrCombo.getValue() == null || tgtAttrCombo.getValue().isBlank()) {
                    errorLabel.setText("Please select or enter a target attribute type.");
                    event.consume();
                } else {
                    Class<?> attrClass = NodeTypeRegistry.getValueClass(tgtAttrCombo.getValue().trim());
                    if (attrClass == null) {
                        errorLabel.setText("Unknown attribute type: " + tgtAttrCombo.getValue().trim());
                        event.consume();
                    } else {
                        // Validate type compatibility
                        try {
                            ResourceIdentifier tgtRid = FalconRawTopic.of(
                                    tgtSymbolField.getText().trim(), tgtSourceField.getText().trim(), attrClass);
                            ConnectionPoint src = ConnectionPoint.of(
                                    Paths.get(srcPathCombo.getValue().trim()), srcRidCombo.getValue());
                            ConnectionPoint tgt = ConnectionPoint.of(
                                    Paths.get(tgtPathCombo.getValue().trim()), tgtRid);
                            new Flywire(src, tgt); // type check
                        } catch (Exception ex) {
                            errorLabel.setText("Type mismatch: " + ex.getMessage());
                            event.consume();
                        }
                    }
                }
            } else if (tgtRidCombo.getValue() == null) {
                errorLabel.setText("Please select a target resource.");
                event.consume();
            } else {
                // Validate type compatibility
                try {
                    ConnectionPoint src = ConnectionPoint.of(
                            Paths.get(srcPathCombo.getValue().trim()), srcRidCombo.getValue());
                    ConnectionPoint tgt = ConnectionPoint.of(
                            Paths.get(tgtPathCombo.getValue().trim()), tgtRidCombo.getValue());
                    new Flywire(src, tgt); // type check
                } catch (Exception ex) {
                    errorLabel.setText("Type mismatch: " + ex.getMessage());
                    event.consume();
                }
            }
        });

        dialog.setResultConverter(btn -> {
            if (btn.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                ConnectionPoint src = ConnectionPoint.of(
                        Paths.get(srcPathCombo.getValue().trim()), srcRidCombo.getValue());
                ResourceIdentifier tgtRid;
                if (tgtManualCheckBox.isSelected()) {
                    Class<?> attrClass = NodeTypeRegistry.getValueClass(tgtAttrCombo.getValue().trim());
                    tgtRid = FalconRawTopic.of(tgtSymbolField.getText().trim(), tgtSourceField.getText().trim(), attrClass);
                } else {
                    tgtRid = tgtRidCombo.getValue();
                }
                ConnectionPoint tgt = ConnectionPoint.of(
                        Paths.get(tgtPathCombo.getValue().trim()), tgtRid);
                return new FlywireEntry(src, tgt);
            }
            return null;
        });

        dialog.showAndWait().ifPresent(entry -> {
            if (isEdit) {
                adhocFlywireEntries.set(editIndex, entry);
            } else {
                adhocFlywireEntries.add(entry);
            }
            refreshFlywireList();
        });
    }

    // Custom cell for displaying ResourceIdentifier in ComboBox
    private class ResourceIdListCell extends ListCell<ResourceIdentifier> {
        @Override
        protected void updateItem(ResourceIdentifier item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                HBox row = new HBox(4);
                row.setAlignment(Pos.CENTER_LEFT);
                Circle dot = new Circle(3, getResourceColor(item));
                Label label = new Label(formatResourceId(item));
                label.setFont(Font.font("Courier New", 11));
                label.setTextFill(ColorScheme.TEXT_PRIMARY);
                row.getChildren().addAll(dot, label);
                setGraphic(row);
                setText(null);
                setStyle("-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_DARK) + ";");
            }
        }
    }

    // ===== Buttons =====

    private Action selectedAction = Action.CANCEL;

    private void setupButtons() {
        // Use CANCEL_CLOSE for all so we control closing manually
        ButtonType runType = new ButtonType("\u25B6", ButtonBar.ButtonData.OTHER);
        ButtonType okType = new ButtonType("OK", ButtonBar.ButtonData.OTHER);
        ButtonType applyType = new ButtonType("Apply", ButtonBar.ButtonData.OTHER);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(runType, okType, applyType, cancelType);

        Button runBtn = (Button) getDialogPane().lookupButton(runType);
        Button okBtn = (Button) getDialogPane().lookupButton(okType);
        Button applyBtn = (Button) getDialogPane().lookupButton(applyType);
        Button cancelBtn = (Button) getDialogPane().lookupButton(cancelType);
        styleButton(runBtn);
        styleButton(okBtn);
        styleButton(applyBtn);
        styleButton(cancelBtn);

        // Run: validate → set action → close
        runBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            String error = validate();
            if (error != null) {
                validationLabel.setText(error);
            } else {
                validationLabel.setText("");
                selectedAction = Action.RUN;
                setResult(buildRunConfig(Action.RUN));
                close();
            }
        });

        // OK: validate → set action → close
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            String error = validate();
            if (error != null) {
                validationLabel.setText(error);
            } else {
                validationLabel.setText("");
                selectedAction = Action.OK;
                setResult(buildRunConfig(Action.OK));
                close();
            }
        });

        // Apply: validate → set action → keep dialog open
        applyBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            String error = validate();
            if (error != null) {
                validationLabel.setText(error);
            } else {
                validationLabel.setText("");
                selectedAction = Action.APPLY;
                if (onApply != null) {
                    onApply.accept(buildRunConfig(Action.APPLY));
                }
            }
        });

        // Cancel: just close, return null
    }

    private java.util.function.Consumer<RunConfig> onApply;

    public void setOnApply(java.util.function.Consumer<RunConfig> onApply) {
        this.onApply = onApply;
    }

    private String validate() {
        // Path is optional — blank means root path
        String pathText = pathField.getText().trim();
        if (!pathText.isEmpty()) {
            try {
                Path path = Paths.get(pathText);
                if (pathToNodeIndex.get(path) == null) {
                    return "Path not found in graph: " + pathText;
                }
            } catch (Exception e) {
                return "Invalid path: " + e.getMessage();
            }
        }

        // Validate logical timestamp
        if (!logicalNowCheckBox.isSelected() && !logicalTimestampField.getText().isBlank()) {
            try {
                parseTimestamp(logicalTimestampField.getText().trim());
            } catch (Exception e) {
                return "Invalid Logical Timestamp: " + e.getMessage();
            }
        }

        // Validate physical timestamp
        if (!physicalNowCheckBox.isSelected() && !physicalTimestampField.getText().isBlank()) {
            try {
                parseTimestamp(physicalTimestampField.getText().trim());
            } catch (Exception e) {
                return "Invalid Physical Timestamp: " + e.getMessage();
            }
        }

        // At least one resource must be selected
        boolean anySelected = resourceCheckBoxes.values().stream().anyMatch(CheckBox::isSelected);
        if (!anySelected) {
            return "At least one requested resource must be selected.";
        }

        return null;
    }

    private Instant parseTimestamp(String text) {
        try {
            // Try ISO instant first
            return Instant.parse(text);
        } catch (Exception e1) {
            try {
                // Try local date-time format
                var ldt = java.time.LocalDateTime.parse(text, DT_FORMAT);
                return ldt.atZone(ZoneId.systemDefault()).toInstant();
            } catch (Exception e2) {
                throw new IllegalArgumentException(
                        "Use format 'yyyy-MM-dd HH:mm:ss' or ISO instant (e.g. 2025-01-01T00:00:00Z)");
            }
        }
    }

    private RunConfig buildRunConfig(Action action) {
        try {
            // Build Snapshot
            Optional<Instant> logical = Optional.empty();
            if (!logicalNowCheckBox.isSelected() && !logicalTimestampField.getText().isBlank()) {
                logical = Optional.of(parseTimestamp(logicalTimestampField.getText().trim()));
            }
            Optional<Instant> physical = Optional.empty();
            if (!physicalNowCheckBox.isSelected() && !physicalTimestampField.getText().isBlank()) {
                physical = Optional.of(parseTimestamp(physicalTimestampField.getText().trim()));
            }
            Snapshot snap = new Snapshot(logical, physical);

            // Path — empty means root
            String pathText = pathField.getText().trim();
            Path path = pathText.isEmpty() ? getRootNodePath() : Paths.get(pathText);

            // Resources from checkboxes
            Set<ResourceIdentifier> resources = new LinkedHashSet<>();
            for (var entry : resourceCheckBoxes.entrySet()) {
                if (entry.getValue().isSelected()) {
                    resources.add(entry.getKey());
                }
            }

            // Adhoc override from structured entries
            Optional<AdhocOverride> override = Optional.empty();
            if (!adhocInputEntries.isEmpty() || !adhocOutputEntries.isEmpty() || !adhocFlywireEntries.isEmpty()) {
                Map<ConnectionPoint, Result<Object>> inputs = new LinkedHashMap<>();
                for (AdhocEntry ae : adhocInputEntries) {
                    inputs.put(ae.connectionPoint(), Success.of(parseValueAsType(ae.valueText(), ae.connectionPoint().rid().type())));
                }
                Map<ConnectionPoint, Result<Object>> outputs = new LinkedHashMap<>();
                for (AdhocEntry ae : adhocOutputEntries) {
                    outputs.put(ae.connectionPoint(), Success.of(parseValueAsType(ae.valueText(), ae.connectionPoint().rid().type())));
                }
                Set<Flywire> flywires = new LinkedHashSet<>();
                for (FlywireEntry fe : adhocFlywireEntries) {
                    flywires.add(Flywire.of(fe.source(), fe.target()));
                }
                override = Optional.of(new AdhocOverride(inputs, outputs, flywires));
            }

            return new RunConfig(action, snap, path, resources, override);
        } catch (Exception e) {
            return null;
        }
    }

    private void setupResultConverter() {
        setResultConverter(dialogButton -> {
            // Run/OK/Apply set their own result via setResult(); Cancel returns null
            return null;
        });
    }

    // ===== Path Index =====

    private static Map<Path, CalculationNode> buildPathIndex(CalculationNode root) {
        Map<Path, CalculationNode> map = new LinkedHashMap<>();
        buildPathIndexRecursive(map, Path.of("/"), root);
        return Collections.unmodifiableMap(map);
    }

    private static void buildPathIndexRecursive(Map<Path, CalculationNode> map, Path currentPath, CalculationNode node) {
        Path nodePath = currentPath.resolve(node.name());
        map.put(nodePath, node);
        if (node instanceof NodeGroup group) {
            group.nodes().forEach(innerNode -> buildPathIndexRecursive(map, nodePath, innerNode));
        }
    }

    private Path getRootNodePath() {
        return Path.of("/").resolve(graph.name());
    }

    private Set<ResourceIdentifier> resolveOutputsForPath(String pathText) {
        if (pathText == null || pathText.isBlank()) {
            return graph.outputs();
        }
        try {
            Path path = Paths.get(pathText.trim());
            CalculationNode node = pathToNodeIndex.get(path);
            if (node != null) {
                return node.outputs();
            }
        } catch (Exception ignored) {
        }
        return Set.of();
    }

    // ===== UI Builders =====

    private Label createSectionHeader(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, 12));
        label.setTextFill(Color.web("#66CCFF"));
        label.setPadding(new Insets(4, 0, 2, 0));
        return label;
    }

    private Label createSubSectionHeader(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, 11));
        label.setTextFill(Color.web("#66CCFF"));
        label.setPadding(new Insets(2, 0, 1, 4));
        return label;
    }

    private Label createMutedLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", 10));
        label.setTextFill(ColorScheme.TEXT_MUTED);
        return label;
    }

    private Label createMiniLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", 9));
        label.setTextFill(ColorScheme.TEXT_MUTED);
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    private List<String> collectKnownAttributeTypes() {
        // Gather all attribute type names seen in the graph's resources
        Set<String> types = new TreeSet<>();
        for (CalculationNode node : pathToNodeIndex.values()) {
            for (ResourceIdentifier rid : node.outputs()) {
                if (rid.type() != null) {
                    types.add(rid.type().getSimpleName());
                }
            }
            for (ResourceIdentifier rid : node.inputs()) {
                if (rid.type() != null) {
                    types.add(rid.type().getSimpleName());
                }
            }
        }
        return new ArrayList<>(types);
    }

    private Set<ResourceIdentifier> getAllResourcesForNode(CalculationNode node) {
        Set<ResourceIdentifier> all = new LinkedHashSet<>();
        all.addAll(node.inputs());
        all.addAll(node.outputs());
        return all;
    }

    private Region createSeparator() {
        Region sep = new Region();
        sep.setPrefHeight(1);
        sep.setMaxHeight(1);
        sep.setStyle("-fx-background-color: " + toHexString(ColorScheme.NODE_BORDER) + ";");
        VBox.setMargin(sep, new Insets(2, 0, 2, 0));
        return sep;
    }

    // ===== Value Parsing =====

    private Object parseValueAsType(String json, Class<?> type) {
        if (json == null || json.isBlank()) return null;
        try {
            ObjectMapper mapper = ConstructionalJsonUtil.getObjectMapper();
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON for type " + type.getSimpleName() + ": " + e.getMessage());
        }
    }

    private String serializeValue(Object value) {
        return toJsonString(value, true);
    }

    private String toJsonString(Object value, boolean pretty) {
        if (value == null) return "null";
        if (value instanceof String s) return "\"" + s + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value.getClass().isRecord()) {
            var components = value.getClass().getRecordComponents();
            String indent = pretty ? "  " : "";
            String sep = pretty ? ",\n" : ", ";
            String open = pretty ? "{\n" : "{ ";
            String close = pretty ? "\n}" : " }";
            StringBuilder sb = new StringBuilder(open);
            for (int i = 0; i < components.length; i++) {
                try {
                    var accessor = components[i].getAccessor();
                    accessor.setAccessible(true);
                    Object fieldValue = accessor.invoke(value);
                    sb.append(indent).append("\"").append(components[i].getName()).append("\": ");
                    sb.append(toJsonString(fieldValue, false));
                    if (i < components.length - 1) sb.append(sep);
                } catch (Exception e) {
                    sb.append(indent).append("\"").append(components[i].getName()).append("\": \"error\"");
                }
            }
            sb.append(close);
            return sb.toString();
        }
        if (value instanceof java.time.Instant instant) return "\"" + instant + "\"";
        if (value instanceof java.math.BigDecimal bd) return bd.toPlainString();
        if (value instanceof Class<?> c) return "\"" + c.getSimpleName() + "\"";
        return "\"" + value + "\"";
    }

    private String generateJsonTemplate(Class<?> type) {
        if (type.isRecord()) {
            var components = type.getRecordComponents();
            StringBuilder sb = new StringBuilder("{\n");
            for (int i = 0; i < components.length; i++) {
                var comp = components[i];
                sb.append("  \"").append(comp.getName()).append("\": ");
                sb.append(getDefaultValueForType(comp.getType()));
                if (i < components.length - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("}");
            return sb.toString();
        }
        return "{}";
    }

    private String getDefaultValueForType(Class<?> type) {
        if (type == String.class) return "\"\"";
        if (type == java.math.BigDecimal.class || type == double.class || type == Double.class) return "0.0";
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class) return "0";
        if (type == boolean.class || type == Boolean.class) return "false";
        if (type == java.time.Instant.class) return "\"" + java.time.Instant.now().toString() + "\"";
        return "null";
    }

    // ===== Formatting =====

    private String formatResourceId(ResourceIdentifier rid) {
        String ridStr = rid.toString();
        if (ridStr.contains("FalconRawTopic")) {
            try {
                String symbol = ridStr.replaceAll(".*symbol='?([^',\\]]+)'?.*", "$1");
                String source = ridStr.replaceAll(".*source='?([^',\\]]+)'?.*", "$1");
                String attribute = ridStr.replaceAll(".*attribute=([^,\\]]+).*", "$1");
                if (attribute.startsWith("class ")) {
                    attribute = attribute.substring(6);
                    int lastDot = attribute.lastIndexOf('.');
                    if (lastDot >= 0) attribute = attribute.substring(lastDot + 1);
                }
                return symbol + " / " + source + " / " + attribute;
            } catch (Exception e) {
                return ridStr;
            }
        }
        return ridStr;
    }


    private Color getResourceColor(ResourceIdentifier rid) {
        String ridStr = rid.toString();
        if (ridStr.contains("Ask")) return Color.web("#44FF44");
        if (ridStr.contains("Bid")) return Color.web("#4444FF");
        if (ridStr.contains("MidPrice")) return Color.web("#FFAA00");
        if (ridStr.contains("Spread")) return Color.web("#FF44FF");
        if (ridStr.contains("Volume")) return Color.web("#44FFFF");
        if (ridStr.contains("Vwap")) return Color.web("#FF8844");
        if (ridStr.contains("MarkToMarket")) return Color.web("#FFFF44");
        return ColorScheme.TEXT_SECONDARY;
    }

    // ===== Styling =====

    private void styleDialog() {
        DialogPane dp = getDialogPane();
        dp.setStyle(
                "-fx-background-color: " + toHexString(ColorScheme.NAV_BACKGROUND) + ";" +
                "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";"
        );
    }

    private void styleTextField(TextField tf) {
        tf.setStyle(
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

    private void styleComboBox(ComboBox<?> cb) {
        cb.setStyle(
                "-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_DARK) + ";" +
                "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";" +
                "-fx-border-width: 1px;" +
                "-fx-font-family: 'Courier New', monospace;" +
                "-fx-font-size: 11px;"
        );
    }

    private void styleSmallButton(Button button) {
        button.setStyle(
                "-fx-background-color: " + toHexString(ColorScheme.NODE_BACKGROUND) + ";" +
                "-fx-text-fill: " + toHexString(ColorScheme.TEXT_SECONDARY) + ";" +
                "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";" +
                "-fx-border-width: 1px;" +
                "-fx-font-size: 10px;" +
                "-fx-padding: 2 8 2 8;"
        );
    }

    private String toHexString(javafx.scene.paint.Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }
}
