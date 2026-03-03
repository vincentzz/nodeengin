package me.vincentzz.visualnew;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import me.vincentzz.graph.CalculationEngine;
import me.vincentzz.graph.json.ConstructionalJsonUtil;
import me.vincentzz.graph.json.NodeTypeRegistry;
import me.vincentzz.graph.model.*;
import me.vincentzz.graph.node.AtomicNode;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.node.Flywire;
import me.vincentzz.graph.node.NodeGroup;
import me.vincentzz.graph.node.builder.NodeBuilder;
import me.vincentzz.graph.node.builder.NodeGroupBuilder;
import me.vincentzz.graph.scope.*;
import me.vincentzz.lang.PathUtils;
import me.vincentzz.visual.model.EditCanvasModel;
import me.vincentzz.visual.model.NodeViewModel;
import me.vincentzz.visual.util.ColorScheme;
import me.vincentzz.visual.view.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tab content for editing a CalculationNode graph.
 * IDE-like layout with unified side panel on the left and canvas on the right.
 */
public class EditModeTab extends Tab {

    private static final double SIDE_PANE_MIN_WIDTH = 280;

    private static final List<String> ATOMIC_NODE_CLASSES = List.of(
            "AskProvider", "BidProvider", "VolumeProvider",
            "MidSpreadCalculator", "VwapCalculator",
            "MarkToMarketCalculator", "HardcodeAttributeProvider"
    );

    private final TabManager tabManager;
    private final String fileName;

    // Core data
    private CalculationNode graph;
    private Snapshot snapshot;
    private Path requestedNodePath;
    private Optional<AdhocOverride> adhocOverride;
    private Set<ResourceIdentifier> requestedResources;
    private Path currentPath;

    // Original state for reset
    private final CalculationNode originalGraph;
    private final Snapshot originalSnapshot;
    private final Path originalRequestedNodePath;
    private final Optional<AdhocOverride> originalAdhocOverride;
    private final Set<ResourceIdentifier> originalRequestedResources;

    // NodeBuilder for mutations
    private NodeBuilder rootNodeBuilder;
    private NodeBuilder currentNodeBuilder;

    // Canvas
    private CalculationCanvas editCanvas;
    private EditCanvasModel editCanvasModel;

    // Side panel
    private VBox sidePaneContent;
    private ScrollPane sideScrollPane;
    private SplitPane mainHorizontalSplit;

    // Visibility state
    private Set<String> visibleNodeNames = new HashSet<>();
    private Set<String> selectedNodeNames = new HashSet<>();

    // Navigation
    private PathNavigationBar navigationBar;

    /**
     * Constructor for bare CalculationNode (e.g. drag-and-drop).
     */
    public EditModeTab(CalculationNode node, String fileName, TabManager tabManager) {
        this(node, fileName, tabManager,
                Snapshot.ofNow(),
                Path.of("/").resolve(node.name()),
                Optional.empty(),
                node.outputs(),
                Path.of("/").resolve(node.name()));
    }

    /**
     * Constructor with full evaluation context (from view mode Edit button).
     */
    public EditModeTab(CalculationNode graph, String fileName, TabManager tabManager,
                       Snapshot snapshot, Path requestedNodePath,
                       Optional<AdhocOverride> adhocOverride,
                       Set<ResourceIdentifier> requestedResources,
                       Path currentViewPath) {
        this.tabManager = tabManager;
        this.fileName = fileName;
        this.graph = graph;
        this.snapshot = snapshot;
        this.requestedNodePath = requestedNodePath;
        this.adhocOverride = adhocOverride;
        this.requestedResources = requestedResources;
        this.currentPath = currentViewPath;

        // Store originals for reset
        this.originalGraph = graph;
        this.originalSnapshot = snapshot;
        this.originalRequestedNodePath = requestedNodePath;
        this.originalAdhocOverride = adhocOverride;
        this.originalRequestedResources = requestedResources;

        setText(fileName + " [Edit]");
        setClosable(true);

        initializeNodeBuilder();
        initializeComponents();
        setupLayout();
        populateSidePane();
        styleComponents();

        setOnClosed(event -> shutdown());
    }

    private void initializeNodeBuilder() {
        this.rootNodeBuilder = NodeBuilder.fromNode(graph);
        this.currentNodeBuilder = rootNodeBuilder;

        String pathStr = PathUtils.toUnixString(currentPath);
        if (pathStr.startsWith("/root/")) {
            pathStr = pathStr.substring("/root/".length());
        } else if (pathStr.startsWith("/root") || pathStr.equals("/")) {
            pathStr = "";
        } else if (pathStr.startsWith("/")) {
            pathStr = pathStr.substring(1);
        }

        if (!pathStr.isEmpty()) {
            for (String segment : pathStr.split("/")) {
                if (!segment.isEmpty() && currentNodeBuilder instanceof NodeGroupBuilder ngb) {
                    NodeBuilder child = ngb.getChildBuilder(segment);
                    if (child != null) {
                        currentNodeBuilder = child;
                    }
                }
            }
        }
    }

    private void initializeComponents() {
        // Navigation bar
        navigationBar = new PathNavigationBar();
        navigationBar.setRightButtons(
                new String[]{"Reset", "Export", "Run Config", "\u25B6"},
                new Runnable[]{this::handleReset, this::handleExport, this::handleRunConfig, this::handleRun}
        );
        navigationBar.setOnSegmentClicked(this::handlePathSegmentClick);
        navigationBar.setPathSegments(getPathSegments(currentPath));

        // Edit canvas model
        editCanvasModel = new EditCanvasModel(currentNodeBuilder, currentPath);

        // Canvas
        editCanvas = new CalculationCanvas();
        editCanvas.setEditModel(editCanvasModel);
        editCanvas.setOnNodeDoubleClicked(this::handleNodeDoubleClick);
        editCanvas.setOnNodeClicked(this::handleNodeClick);
        editCanvas.setOnMultipleNodesSelected(this::handleMultipleNodesSelected);
        editCanvas.setOnStructuralChange(this::handleStructuralChange);
        editCanvas.setOnResetRequested(this::handleReset);
        editCanvas.setOnFlywireEditRequested(this::handleAddFlywire);
        editCanvas.setOnScopeEditRequested(this::handleEditScopeFromCanvas);

        // Initialize visibility
        initializeNodeVisibility();

        // Side panel
        sidePaneContent = new VBox(8);
        sidePaneContent.setPadding(new Insets(10));
        sidePaneContent.setMinWidth(SIDE_PANE_MIN_WIDTH);

        sideScrollPane = new ScrollPane(sidePaneContent);
        sideScrollPane.setFitToWidth(true);
        sideScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sideScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sideScrollPane.setMinWidth(SIDE_PANE_MIN_WIDTH);

        // Horizontal split: side panel (left) + canvas (right)
        mainHorizontalSplit = new SplitPane();
        mainHorizontalSplit.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        mainHorizontalSplit.getItems().addAll(sideScrollPane, editCanvas);
        mainHorizontalSplit.setDividerPositions(0.22);
    }

    private void setupLayout() {
        VBox mainLayout = new VBox();
        mainLayout.getChildren().addAll(navigationBar, mainHorizontalSplit);
        VBox.setVgrow(mainHorizontalSplit, Priority.ALWAYS);
        setContent(mainLayout);
    }

    // ===== Side pane population =====

    private void populateSidePane() {
        sidePaneContent.getChildren().clear();

        populateNodeGroupSection();
        populateAtomicWithInstancesSection();
        populateAvailableClassesSection();

        styleSidePane();
    }

    private void populateNodeGroupSection() {
        if (!(currentNodeBuilder instanceof NodeGroupBuilder ngb)) return;

        List<CalculationNode> nodeGroups = ngb.nodes().stream()
                .filter(n -> n instanceof NodeGroup)
                .sorted(Comparator.comparing(CalculationNode::name))
                .toList();

        if (nodeGroups.isEmpty()) return;

        Label sectionLabel = new Label("Node Groups");
        sectionLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        sectionLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        sidePaneContent.getChildren().add(sectionLabel);

        for (CalculationNode node : nodeGroups) {
            String nodeName = node.name();
            HBox row = new HBox(5);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            CheckBox eyeCheckBox = new CheckBox();
            eyeCheckBox.setSelected(visibleNodeNames.contains(nodeName));
            eyeCheckBox.setOnAction(e -> {
                if (eyeCheckBox.isSelected()) {
                    visibleNodeNames.add(nodeName);
                } else {
                    visibleNodeNames.remove(nodeName);
                }
                updateCanvasVisibility();
            });

            Label nameLabel = new Label(nodeName);
            nameLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
            nameLabel.setFont(Font.font("System", 12));
            HBox.setHgrow(nameLabel, Priority.ALWAYS);
            nameLabel.setMaxWidth(Double.MAX_VALUE);

            Button renameBtn = new Button("\u270E");
            renameBtn.setTooltip(new Tooltip("Rename"));
            renameBtn.setOnAction(e -> handleRenameNodeGroup(nodeName));
            styleSmallButton(renameBtn);

            Button deleteBtn = new Button("\u2715");
            deleteBtn.setTooltip(new Tooltip("Delete"));
            deleteBtn.setOnAction(e -> handleDeleteNode(nodeName));
            styleSmallButton(deleteBtn);

            row.getChildren().addAll(eyeCheckBox, nameLabel, renameBtn, deleteBtn);
            sidePaneContent.getChildren().add(row);
        }

        addSeparator();
    }

    private void populateAtomicWithInstancesSection() {
        if (!(currentNodeBuilder instanceof NodeGroupBuilder ngb)) return;

        // Group atomic node children by class name
        Map<String, List<CalculationNode>> instancesByClass = new LinkedHashMap<>();
        for (CalculationNode child : ngb.nodes()) {
            if (child instanceof AtomicNode) {
                String className = child.getClass().getSimpleName();
                instancesByClass.computeIfAbsent(className, k -> new ArrayList<>()).add(child);
            }
        }

        if (instancesByClass.isEmpty()) return;

        Label sectionLabel = new Label("Atomic Nodes");
        sectionLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        sectionLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        sidePaneContent.getChildren().add(sectionLabel);

        for (var entry : instancesByClass.entrySet()) {
            String className = entry.getKey();
            List<CalculationNode> instances = entry.getValue();

            // Build instance rows content
            VBox instanceContent = new VBox(3);
            instanceContent.setPadding(new Insets(2, 0, 2, 5));

            for (CalculationNode instance : instances) {
                String instanceName = instance.name();
                HBox instanceRow = new HBox(5);
                instanceRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                CheckBox eyeCheckBox = new CheckBox();
                eyeCheckBox.setSelected(visibleNodeNames.contains(instanceName));
                eyeCheckBox.setOnAction(e -> {
                    if (eyeCheckBox.isSelected()) {
                        visibleNodeNames.add(instanceName);
                    } else {
                        visibleNodeNames.remove(instanceName);
                    }
                    updateCanvasVisibility();
                });

                Label instanceLabel = new Label(instanceName);
                instanceLabel.setTextFill(ColorScheme.TEXT_SECONDARY);
                instanceLabel.setFont(Font.font("System", 11));
                HBox.setHgrow(instanceLabel, Priority.ALWAYS);
                instanceLabel.setMaxWidth(Double.MAX_VALUE);

                Button editBtn = new Button("\u270E");
                editBtn.setTooltip(new Tooltip("Edit parameters"));
                editBtn.setOnAction(e -> handleEditAtomicNode(instance));
                styleSmallButton(editBtn);

                Button deleteBtn = new Button("\u2715");
                deleteBtn.setTooltip(new Tooltip("Delete"));
                deleteBtn.setOnAction(e -> handleDeleteNode(instanceName));
                styleSmallButton(deleteBtn);

                instanceRow.getChildren().addAll(eyeCheckBox, instanceLabel, editBtn, deleteBtn);
                instanceContent.getChildren().add(instanceRow);
            }

            // Class header with eye-all toggle and create button
            CheckBox classEyeCheckBox = new CheckBox();
            boolean allVisible = instances.stream().allMatch(n -> visibleNodeNames.contains(n.name()));
            classEyeCheckBox.setSelected(allVisible);
            classEyeCheckBox.setOnAction(e -> {
                for (CalculationNode inst : instances) {
                    if (classEyeCheckBox.isSelected()) {
                        visibleNodeNames.add(inst.name());
                    } else {
                        visibleNodeNames.remove(inst.name());
                    }
                }
                updateCanvasVisibility();
                populateSidePane();
            });

            Label classLabel = new Label(className);
            classLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
            classLabel.setFont(Font.font("System", 12));
            HBox.setHgrow(classLabel, Priority.ALWAYS);
            classLabel.setMaxWidth(Double.MAX_VALUE);

            Button createBtn = new Button("+");
            createBtn.setTooltip(new Tooltip("Create " + className));
            createBtn.setOnAction(e -> handleCreateAtomicNode(className));
            styleSmallButton(createBtn);

            HBox headerGraphic = new HBox(5, classEyeCheckBox, classLabel, createBtn);
            headerGraphic.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            TitledPane titledPane = new TitledPane();
            titledPane.setGraphic(headerGraphic);
            titledPane.setText(null);
            titledPane.setContent(instanceContent);
            titledPane.setExpanded(false);
            titledPane.setAnimated(false);
            titledPane.setStyle(
                    "-fx-text-fill: " + toHexString(ColorScheme.TEXT_SECONDARY) + ";" +
                    "-fx-font-size: 11px;"
            );

            sidePaneContent.getChildren().add(titledPane);
        }

        addSeparator();
    }

    private void populateAvailableClassesSection() {
        if (!(currentNodeBuilder instanceof NodeGroupBuilder ngb)) return;

        // Determine which classes already have instances
        Set<String> classesWithInstances = ngb.nodes().stream()
                .filter(n -> n instanceof AtomicNode)
                .map(n -> n.getClass().getSimpleName())
                .collect(Collectors.toSet());

        List<String> availableClasses = ATOMIC_NODE_CLASSES.stream()
                .filter(c -> !classesWithInstances.contains(c))
                .toList();

        if (availableClasses.isEmpty()) return;

        Label sectionLabel = new Label("Available Classes");
        sectionLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        sectionLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        sidePaneContent.getChildren().add(sectionLabel);

        for (String className : availableClasses) {
            HBox row = new HBox(5);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            Label nameLabel = new Label(className);
            nameLabel.setTextFill(ColorScheme.TEXT_SECONDARY);
            nameLabel.setFont(Font.font("System", 11));
            HBox.setHgrow(nameLabel, Priority.ALWAYS);
            nameLabel.setMaxWidth(Double.MAX_VALUE);

            Button createBtn = new Button("+");
            createBtn.setTooltip(new Tooltip("Create " + className));
            createBtn.setOnAction(e -> handleCreateAtomicNode(className));
            styleSmallButton(createBtn);

            row.getChildren().addAll(nameLabel, createBtn);
            sidePaneContent.getChildren().add(row);
        }
    }

    private void addSeparator() {
        Separator separator = new Separator();
        separator.setPadding(new Insets(4, 0, 4, 0));
        sidePaneContent.getChildren().add(separator);
    }

    // ===== Canvas visibility =====

    private void initializeNodeVisibility() {
        visibleNodeNames.clear();
        editCanvasModel.setVisibleNodes(Set.of());
        var allNodes = editCanvasModel.getNodes();
        for (var node : allNodes) {
            visibleNodeNames.add(node.getDisplayName());
        }
        editCanvasModel.setVisibleNodes(visibleNodeNames);
    }

    private void updateCanvasVisibility() {
        editCanvasModel.setVisibleNodes(visibleNodeNames);

        var allNodes = editCanvasModel.getNodes();
        var allConnections = editCanvasModel.getConnections();

        List<NodeViewModel> visibleNodes = new ArrayList<>();
        for (var node : allNodes) {
            if (visibleNodeNames.contains(node.getDisplayName())) {
                visibleNodes.add(node);
            }
        }

        List<me.vincentzz.visual.model.ConnectionViewModel> visibleConnections = new ArrayList<>();
        for (var connection : allConnections) {
            String sourceName = connection.getSourcePath().getFileName() != null ?
                    connection.getSourcePath().getFileName().toString() :
                    PathUtils.toUnixString(connection.getSourcePath());
            String targetName = connection.getTargetPath().getFileName() != null ?
                    connection.getTargetPath().getFileName().toString() :
                    PathUtils.toUnixString(connection.getTargetPath());

            if (visibleNodeNames.contains(sourceName) && visibleNodeNames.contains(targetName)) {
                visibleConnections.add(connection);
            }
        }

        editCanvas.setNodes(visibleNodes);
        editCanvas.setConnections(visibleConnections);
        editCanvas.refresh();
    }

    // ===== Event handlers =====

    private void handleNodeClick(Path nodePath) {
        String nodeName = nodePath.getFileName() != null ?
                nodePath.getFileName().toString() : PathUtils.toUnixString(nodePath);

        NodeViewModel clickedNode = findNodeByName(nodeName);
        if (clickedNode == null) return;

        if (clickedNode.isSelected()) {
            clickedNode.setSelected(false);
            selectedNodeNames.remove(nodeName);
        } else {
            clickedNode.setSelected(true);
            selectedNodeNames.add(nodeName);
        }

        updateCanvasVisibility();
    }

    private void handleNodeDoubleClick(Path nodePath) {
        navigateToPath(nodePath);
    }

    private void handleMultipleNodesSelected(Set<Path> nodePaths) {
        selectedNodeNames.clear();
        for (Path path : nodePaths) {
            String nodeName = path.getFileName() != null ?
                    path.getFileName().toString() : PathUtils.toUnixString(path);
            selectedNodeNames.add(nodeName);
        }
    }

    private void handleStructuralChange() {
        try {
            NodeBuilder updatedNodeBuilder = editCanvasModel.getCurrentNodeBuilder();
            this.currentNodeBuilder = updatedNodeBuilder;

            editCanvasModel = new EditCanvasModel(currentNodeBuilder, currentPath);
            editCanvas.setEditModel(editCanvasModel);

            initializeNodeVisibility();
            populateSidePane();
        } catch (Exception e) {
            populateSidePane();
        }
    }

    private void handlePathSegmentClick(int segmentIndex) {
        try {
            var segments = getPathSegments(currentPath);
            if (segmentIndex == 0) {
                navigateToPath(Paths.get("/root"));
                return;
            }
            if (segmentIndex < segments.size()) {
                StringBuilder pathBuilder = new StringBuilder();
                for (int i = 0; i <= segmentIndex; i++) {
                    pathBuilder.append("/").append(segments.get(i));
                }
                navigateToPath(Paths.get(pathBuilder.toString()));
            }
        } catch (Exception e) {
            showErrorAlert("Navigation Error", "Failed to navigate: " + e.getMessage());
        }
    }

    private void navigateToPath(Path newPath) {
        try {
            NodeBuilder targetBuilder = rootNodeBuilder;
            Path targetPath = Paths.get("/root");

            String pathStr = PathUtils.toUnixString(newPath);
            if (pathStr.startsWith("/root/")) {
                pathStr = pathStr.substring("/root/".length());
            } else if (pathStr.startsWith("/root") || pathStr.equals("/")) {
                pathStr = "";
            } else if (pathStr.startsWith("/")) {
                pathStr = pathStr.substring(1);
            }

            if (!pathStr.isEmpty()) {
                for (String segment : pathStr.split("/")) {
                    if (!segment.isEmpty() && targetBuilder instanceof NodeGroupBuilder ngb) {
                        NodeBuilder child = ngb.getChildBuilder(segment);
                        if (child != null) {
                            targetBuilder = child;
                            targetPath = targetPath.resolve(segment);
                        }
                    }
                }
            }

            this.currentNodeBuilder = targetBuilder;
            this.currentPath = targetPath;

            editCanvasModel = new EditCanvasModel(currentNodeBuilder, currentPath);
            editCanvas.setEditModel(editCanvasModel);
            navigationBar.setPathSegments(getPathSegments(currentPath));
            initializeNodeVisibility();
            populateSidePane();
            updateCanvasVisibility();
        } catch (Exception e) {
            showErrorAlert("Navigation Error", "Failed to navigate to path: " + e.getMessage());
        }
    }

    private void handleCreateAtomicNode(String atomicNodeClass) {
        if (!(currentNodeBuilder instanceof NodeGroupBuilder ngb)) {
            showErrorAlert("Error", "Can only create nodes inside a NodeGroup.");
            return;
        }
        CreateAtomicNodeDialog dialog = new CreateAtomicNodeDialog(atomicNodeClass);
        dialog.showAndWait().ifPresent(result -> {
            try {
                Map<String, Object> constructionParams = new HashMap<>(result);
                CalculationNode newNode = NodeTypeRegistry.createNode(atomicNodeClass, constructionParams);
                ngb.addNode(newNode);
                refreshAfterStructuralChange();
            } catch (Exception e) {
                showErrorAlert("Node Creation Failed", "Failed to create " + atomicNodeClass + ": " + e.getMessage());
            }
        });
    }

    private void handleRenameNodeGroup(String oldName) {
        if (!(currentNodeBuilder instanceof NodeGroupBuilder ngb)) return;

        TextInputDialog dialog = new TextInputDialog(oldName);
        dialog.setTitle("Rename NodeGroup");
        dialog.setHeaderText("Rename NodeGroup: " + oldName);
        dialog.setContentText("New name:");

        dialog.showAndWait().ifPresent(newName -> {
            if (newName.isEmpty() || newName.equals(oldName)) return;
            try {
                NodeBuilder childBuilder = ngb.getChildBuilder(oldName);
                if (childBuilder == null) return;
                CalculationNode childNode = childBuilder.toNode();
                if (!(childNode instanceof NodeGroup ng)) return;

                NodeGroup renamed = new NodeGroup(newName, ng.nodes(), ng.flywires(), ng.exports());

                // Update parent flywires that reference the old name
                Set<Flywire> toRemove = new HashSet<>();
                Set<Flywire> toAdd = new HashSet<>();
                for (Flywire fw : ngb.flywires()) {
                    boolean srcMatch = pathEndsWith(fw.source().nodePath(), oldName);
                    boolean tgtMatch = pathEndsWith(fw.target().nodePath(), oldName);
                    if (srcMatch || tgtMatch) {
                        toRemove.add(fw);
                        ConnectionPoint newSrc = srcMatch
                                ? new ConnectionPoint(renamePath(fw.source().nodePath(), oldName, newName), fw.source().rid())
                                : fw.source();
                        ConnectionPoint newTgt = tgtMatch
                                ? new ConnectionPoint(renamePath(fw.target().nodePath(), oldName, newName), fw.target().rid())
                                : fw.target();
                        toAdd.add(new Flywire(newSrc, newTgt));
                    }
                }
                for (Flywire fw : toRemove) ngb.deleteFlywire(fw);
                ngb.deleteNode(oldName);
                ngb.addNode(renamed);
                for (Flywire fw : toAdd) {
                    try { ngb.addFlywire(fw); } catch (Exception ignored) {}
                }

                refreshAfterStructuralChange();
            } catch (Exception e) {
                showErrorAlert("Rename Failed", "Failed to rename NodeGroup: " + e.getMessage());
            }
        });
    }

    private void handleDeleteNode(String nodeName) {
        if (!(currentNodeBuilder instanceof NodeGroupBuilder ngb)) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Node");
        confirm.setHeaderText("Delete node: " + nodeName + "?");
        confirm.setContentText("This will also remove any flywires referencing this node.");

        confirm.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) return;
            try {
                // Remove flywires referencing the deleted node
                Set<Flywire> toRemove = new HashSet<>();
                for (Flywire fw : ngb.flywires()) {
                    if (pathEndsWith(fw.source().nodePath(), nodeName) ||
                        pathEndsWith(fw.target().nodePath(), nodeName)) {
                        toRemove.add(fw);
                    }
                }
                for (Flywire fw : toRemove) ngb.deleteFlywire(fw);

                ngb.deleteNode(nodeName);
                visibleNodeNames.remove(nodeName);
                selectedNodeNames.remove(nodeName);
                refreshAfterStructuralChange();
            } catch (Exception e) {
                showErrorAlert("Delete Failed", "Failed to delete node: " + e.getMessage());
            }
        });
    }

    private void handleEditAtomicNode(CalculationNode instance) {
        if (!(currentNodeBuilder instanceof NodeGroupBuilder ngb)) return;

        String className = instance.getClass().getSimpleName();
        CreateAtomicNodeDialog dialog = new CreateAtomicNodeDialog(className);
        dialog.prefillParameters(instance.getConstructionParameters());
        dialog.showAndWait().ifPresent(result -> {
            try {
                Map<String, Object> constructionParams = new HashMap<>(result);
                CalculationNode newNode = NodeTypeRegistry.createNode(className, constructionParams);
                ngb.deleteNode(instance.name());
                ngb.addNode(newNode);
                refreshAfterStructuralChange();
            } catch (Exception e) {
                showErrorAlert("Edit Failed", "Failed to edit " + className + ": " + e.getMessage());
            }
        });
    }

    private void handleAddFlywire() {
        if (!(currentNodeBuilder instanceof NodeGroupBuilder ngb)) {
            showErrorAlert("Error", "Can only add flywires inside a NodeGroup.");
            return;
        }
        CreateFlywireDialog dialog = new CreateFlywireDialog(ngb.nodes());
        dialog.showAndWait().ifPresent(flywire -> {
            try {
                ngb.addFlywire(flywire);
                refreshAfterStructuralChange();
            } catch (Exception e) {
                showErrorAlert("Flywire Creation Failed", "Incompatible types: " + e.getMessage());
            }
        });
    }

    private void handleEditScope() {
        if (!(currentNodeBuilder instanceof NodeGroupBuilder ngb)) {
            showErrorAlert("Error", "Can only edit scope of a NodeGroup.");
            return;
        }
        EditScopeDialog dialog = new EditScopeDialog(ngb.nodes(), ngb.getExports());
        dialog.showAndWait().ifPresent(newScope -> {
            ngb.setExports(newScope);
            refreshAfterStructuralChange();
        });
    }

    private void handleEditRegexScope() {
        if (!(currentNodeBuilder instanceof NodeGroupBuilder ngb)) {
            showErrorAlert("Error", "Can only edit scope of a NodeGroup.");
            return;
        }
        EditRegexScopeDialog dialog = new EditRegexScopeDialog(ngb.getExports());
        dialog.showAndWait().ifPresent(newScope -> {
            ngb.setExports(newScope);
            refreshAfterStructuralChange();
        });
    }

    private void handleEditScopeFromCanvas() {
        if (!(currentNodeBuilder instanceof NodeGroupBuilder)) {
            showErrorAlert("Error", "Can only edit scope of a NodeGroup.");
            return;
        }

        ChoiceDialog<String> choiceDialog = new ChoiceDialog<>("FullSet", "FullSet", "Regex");
        choiceDialog.setTitle("Edit Scope");
        choiceDialog.setHeaderText("Select scope edit mode");
        choiceDialog.setContentText("Mode:");

        choiceDialog.showAndWait().ifPresent(choice -> {
            if ("Regex".equals(choice)) {
                handleEditRegexScope();
            } else {
                handleEditScope();
            }
        });
    }

    private void handleReset() {
        try {
            this.graph = originalGraph;
            this.snapshot = originalSnapshot;
            this.requestedNodePath = originalRequestedNodePath;
            this.adhocOverride = originalAdhocOverride;
            this.requestedResources = originalRequestedResources;

            this.rootNodeBuilder = NodeBuilder.fromNode(originalGraph);
            this.currentNodeBuilder = rootNodeBuilder;

            // Re-navigate to current path
            String pathStr = PathUtils.toUnixString(currentPath);
            if (pathStr.startsWith("/root/")) {
                pathStr = pathStr.substring("/root/".length());
            } else if (pathStr.startsWith("/root") || pathStr.equals("/")) {
                pathStr = "";
            } else if (pathStr.startsWith("/")) {
                pathStr = pathStr.substring(1);
            }
            if (!pathStr.isEmpty()) {
                for (String segment : pathStr.split("/")) {
                    if (!segment.isEmpty() && currentNodeBuilder instanceof NodeGroupBuilder ngb) {
                        NodeBuilder child = ngb.getChildBuilder(segment);
                        if (child != null) {
                            currentNodeBuilder = child;
                        }
                    }
                }
            }

            editCanvasModel = new EditCanvasModel(currentNodeBuilder, currentPath);
            editCanvas.setEditModel(editCanvasModel);

            initializeNodeVisibility();
            populateSidePane();
            updateCanvasVisibility();
        } catch (Exception e) {
            showErrorAlert("Reset Failed", "Failed to reset to original state: " + e.getMessage());
        }
    }

    private void handleExport() {
        try {
            CalculationNode currentGraph = rootNodeBuilder.toNode();
            var jsonResult = ConstructionalJsonUtil.toJson(currentGraph);

            if (jsonResult.isFailure()) {
                showErrorAlert("Export Failed", "Failed to serialize graph to JSON: " + jsonResult.getException().getMessage());
                return;
            }

            String jsonContent = jsonResult.get();

            javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
            fileChooser.setTitle("Export Graph to JSON");
            fileChooser.setInitialFileName("exported-graph.json");
            fileChooser.getExtensionFilters().add(
                    new javafx.stage.FileChooser.ExtensionFilter("JSON Files", "*.json"));

            java.io.File file = fileChooser.showSaveDialog(
                    editCanvas.getScene() != null ? editCanvas.getScene().getWindow() : null);
            if (file != null) {
                java.nio.file.Files.writeString(file.toPath(), jsonContent);
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Export Successful");
                alert.setHeaderText(null);
                alert.setContentText("Graph exported successfully to: " + file.getAbsolutePath());
                alert.showAndWait();
            }
        } catch (Exception e) {
            showErrorAlert("Export Failed", "Failed to export graph: " + e.getMessage());
        }
    }

    private void handleRunConfig() {
        CalculationNode currentGraph = rootNodeBuilder.toNode();
        RunConfigDialog dialog = new RunConfigDialog(
                snapshot, requestedNodePath, requestedResources, adhocOverride, currentGraph);

        dialog.setOnApply(config -> applyRunConfig(config));

        dialog.showAndWait().ifPresent(config -> {
            applyRunConfig(config);
            if (config.action() == RunConfigDialog.Action.RUN) {
                handleRun();
            }
        });
    }

    private void applyRunConfig(RunConfigDialog.RunConfig config) {
        this.snapshot = config.snapshot();
        this.requestedNodePath = config.requestedNodePath();
        this.requestedResources = config.requestedResources();
        this.adhocOverride = config.adhocOverride();
    }

    private void handleRun() {
        try {
            CalculationNode modifiedGraph = rootNodeBuilder.toNode();

            CalculationEngine engine = new CalculationEngine(modifiedGraph);
            var newEvaluationResult = engine.evaluateForResult(
                    requestedNodePath, snapshot, requestedResources, adhocOverride);

            tabManager.openViewTab(new EvaluationBundle(modifiedGraph, newEvaluationResult), fileName);
        } catch (Exception e) {
            showErrorAlert("Run Failed", "Failed to evaluate modified graph: " + e.getMessage());
        }
    }

    private void refreshAfterStructuralChange() {
        editCanvasModel.updateNodeBuilder(currentNodeBuilder);
        editCanvas.setEditModel(editCanvasModel);
        initializeNodeVisibility();
        populateSidePane();
        updateCanvasVisibility();
    }

    // ===== Helpers =====

    private NodeViewModel findNodeByName(String nodeName) {
        return editCanvasModel.getNodes().stream()
                .filter(node -> node.getDisplayName().equals(nodeName))
                .findFirst()
                .orElse(null);
    }

    private boolean pathEndsWith(Path path, String name) {
        return path.getFileName() != null && path.getFileName().toString().equals(name);
    }

    private Path renamePath(Path path, String oldName, String newName) {
        if (path.getFileName() != null && path.getFileName().toString().equals(oldName)) {
            Path parent = path.getParent();
            return parent != null ? parent.resolve(newName) : Path.of(newName);
        }
        return path;
    }

    private List<String> getPathSegments(Path path) {
        List<String> segments = new ArrayList<>();
        String pathStr = PathUtils.toUnixString(path);
        if (pathStr.equals("/") || pathStr.equals("/root")) {
            segments.add("root");
        } else {
            segments.add("root");
            for (Path segment : path) {
                if (!segment.toString().equals("root")) {
                    segments.add(segment.toString());
                }
            }
        }
        return segments;
    }

    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void shutdown() {
        if (editCanvas != null) editCanvas.shutdown();
    }

    // ===== Styling =====

    private void styleComponents() {
        sidePaneContent.setStyle("-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_MEDIUM) + ";");
        styleScrollPane(sideScrollPane);
        styleSidePane();
    }

    private void styleSidePane() {
        styleLabelsInContainer(sidePaneContent);
        styleButtonsInContainer(sidePaneContent);
    }

    private void styleLabelsInContainer(javafx.scene.Parent container) {
        for (javafx.scene.Node node : container.getChildrenUnmodifiable()) {
            if (node instanceof Label label) {
                if (label.getTextFill() == null || label.getTextFill().equals(javafx.scene.paint.Color.BLACK)) {
                    label.setTextFill(ColorScheme.TEXT_PRIMARY);
                }
            } else if (node instanceof javafx.scene.Parent parent) {
                styleLabelsInContainer(parent);
            }
        }
    }

    private void styleButtonsInContainer(javafx.scene.Parent container) {
        for (javafx.scene.Node node : container.getChildrenUnmodifiable()) {
            if (node instanceof Button button) {
                if (button.getStyle() == null || button.getStyle().isEmpty()) {
                    styleSmallButton(button);
                }
            } else if (node instanceof javafx.scene.Parent parent) {
                styleButtonsInContainer(parent);
            }
        }
    }

    private void styleSmallButton(Button button) {
        button.setStyle(
                "-fx-background-color: " + toHexString(ColorScheme.NODE_BACKGROUND) + ";" +
                "-fx-text-fill: " + toHexString(ColorScheme.TEXT_PRIMARY) + ";" +
                "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";" +
                "-fx-border-width: 1px;" +
                "-fx-font-size: 11px;" +
                "-fx-padding: 2 6 2 6;" +
                "-fx-min-width: 24px;"
        );
    }

    private void styleScrollPane(ScrollPane scrollPane) {
        scrollPane.setStyle(
                "-fx-background: " + toHexString(ColorScheme.BACKGROUND_MEDIUM) + ";" +
                "-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_MEDIUM) + ";" +
                "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";" +
                "-fx-border-width: 1px;"
        );
    }

    private String toHexString(javafx.scene.paint.Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }
}
