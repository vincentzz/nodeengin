package me.vincentzz.visual.view;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import me.vincentzz.falcon.attribute.MidPrice;
import me.vincentzz.falcon.attribute.Spread;
import me.vincentzz.falcon.ifo.FalconResourceId;
import me.vincentzz.graph.CalculationEngine;
import me.vincentzz.graph.model.AdhocOverride;
import me.vincentzz.graph.model.EvaluationResult;
import me.vincentzz.graph.model.Snapshot;
import me.vincentzz.graph.node.builder.NodeBuilder;
import me.vincentzz.graph.node.builder.NodeGroupBuilder;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.node.Flywire;
import me.vincentzz.graph.node.NodeGroup;
import me.vincentzz.graph.scope.Exclude;
import me.vincentzz.graph.scope.Include;
import me.vincentzz.graph.scope.Scope;
import me.vincentzz.graph.json.ConstructionalJsonUtil;
import me.vincentzz.graph.json.NodeTypeRegistry;
import me.vincentzz.visual.model.EditCanvasModel;
import me.vincentzz.visual.model.NodeViewModel;
import me.vincentzz.visual.util.ColorScheme;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/**
 * Edit window for modifying graph nodes using NodeBuilder.
 * Takes graph data for display and provides editing capabilities.
 */
public class EditNodeWindow extends Stage {
    
    private static final double WINDOW_WIDTH = 1200;
    private static final double WINDOW_HEIGHT = 800;
    private static final double COLUMN_MIN_WIDTH = 350;
    
    // Core data for editing
    private CalculationNode graph;
    private Path currentPath;
    private Snapshot snapshot;
    private Path requestedNodePath;
    private Optional<AdhocOverride> adhocOverride;
    private Set<me.vincentzz.graph.model.ResourceIdentifier> requestedResources;
    
    // Original state for reset functionality
    private final CalculationNode originalGraph;
    private final Snapshot originalSnapshot;
    private final Path originalRequestedNodePath;
    private final Optional<AdhocOverride> originalAdhocOverride;
    private final Set<me.vincentzz.graph.model.ResourceIdentifier> originalRequestedResources;
    
    // NodeBuilder for mutations
    private NodeBuilder rootNodeBuilder;
    private NodeBuilder currentNodeBuilder;
    
    // UI Components
    private SplitPane canvasControlsSplit;
    
    // Canvas for displaying the graph (edit mode)
    private CalculationCanvas editCanvas;
    private EditCanvasModel editCanvasModel;
    
    // Column 1: Request (editable)
    private VBox requestColumn;
    private TextArea snapshotArea;
    private TextArea requestPathArea;
    private TextArea requestedResourcesArea;
    private TextArea adhocOverrideArea;
    
    // Column 2: Node visibility and operations
    private VBox nodeOperationsColumn;
    private VBox nodeCheckboxContainer;
    private VBox flywireContainer;
    private VBox scopeContainer;
    private Map<String, CheckBox> nodeVisibilityMap = new HashMap<>();
    private Set<String> visibleNodeNames = new HashSet<>();
    private Set<String> selectedNodeNames = new HashSet<>();
    
    // Column 3: Atomic node creation
    private VBox atomicNodeColumn;
    private VBox atomicNodeContainer;
    
    // Top navigation bar
    private PathNavigationBar navigationBar;
    private Button createGroupButton;
    
    // Events
    private Consumer<EvaluationResult> onRunCompleted;
    private Runnable onCancel;
    
    /**
     * Create edit window with separate graph data components.
     */
    public EditNodeWindow(CalculationNode graph, Path currentPath, Snapshot snapshot, 
                         Path requestedNodePath, Optional<AdhocOverride> adhocOverride,
                         Set<me.vincentzz.graph.model.ResourceIdentifier> requestedResources) {
        this.graph = graph;
        this.currentPath = currentPath;
        this.snapshot = snapshot;
        this.requestedNodePath = requestedNodePath;
        this.adhocOverride = adhocOverride;
        this.requestedResources = requestedResources;
        
        // Store original state for reset functionality
        this.originalGraph = graph;
        this.originalSnapshot = snapshot;
        this.originalRequestedNodePath = requestedNodePath;
        this.originalAdhocOverride = adhocOverride;
        this.originalRequestedResources = requestedResources;
        
        // Initialize NodeBuilder for editing
        initializeNodeBuilder();
        
        initializeWindow();
        initializeComponents();
        setupLayout();
        populateData();
        styleComponents();
    }
    
    private void initializeNodeBuilder() {
        try {
            // Create root NodeBuilder from the graph
            this.rootNodeBuilder = NodeBuilder.fromNode(graph);

            // Navigate to current path using real child builder references (not copies)
            this.currentNodeBuilder = rootNodeBuilder;
            String pathStr = currentPath.toString();
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
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize NodeBuilder", e);
        }
    }
    
    private NodeBuilder navigateToNodeBuilder(NodeBuilder builder, Path targetPath) {
        if (targetPath.toString().equals("/") || targetPath.toString().equals("/root")) {
            return builder;
        }
        
        // Navigate through the path segments to find the target NodeBuilder
        NodeBuilder currentBuilder = builder;
        
        // Split the path into segments (skip root)
        String pathStr = targetPath.toString();
        if (pathStr.startsWith("/root/")) {
            pathStr = pathStr.substring("/root/".length());
        } else if (pathStr.startsWith("/")) {
            pathStr = pathStr.substring(1);
        }
        
        if (pathStr.isEmpty()) {
            return currentBuilder;
        }
        
        String[] segments = pathStr.split("/");
        
        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            
            System.out.println("DEBUG: Navigating to segment '" + segment + "' from current builder");
            
            // Find the child NodeBuilder for this segment
            NodeBuilder childBuilder = findChildNodeBuilder(currentBuilder, segment);
            if (childBuilder == null) {
                System.err.println("ERROR: Could not navigate to segment '" + segment + "' in path " + targetPath);
                System.err.println("Available children: " + getAvailableChildNames(currentBuilder));
                // Return current builder as fallback instead of null
                return currentBuilder;
            }
            
            System.out.println("DEBUG: Successfully navigated to segment '" + segment + "'");
            currentBuilder = childBuilder;
        }
        
        return currentBuilder;
    }
    
    /**
     * Find a child NodeBuilder by name within the given NodeBuilder.
     * Enhanced to handle multi-layer grouping properly.
     */
    private NodeBuilder findChildNodeBuilder(NodeBuilder parentBuilder, String childName) {
        if (!(parentBuilder instanceof NodeGroupBuilder ngb)) {
            System.err.println("DEBUG: Parent builder is not a NodeGroupBuilder, cannot navigate to child '" + childName + "'");
            return null; // Can't navigate into non-NodeGroup builders
        }
        
        try {
            System.out.println("DEBUG: Looking for child '" + childName + "' in NodeGroupBuilder");
            
            // Get the child nodes from the NodeGroupBuilder
            Set<CalculationNode> childNodes = ngb.nodes();
            System.out.println("DEBUG: Found " + childNodes.size() + " child nodes");
            
            // Find the child node with the matching name
            for (CalculationNode childNode : childNodes) {
                String nodeName = childNode.name();
                System.out.println("DEBUG: Checking child node: " + nodeName);
                
                if (nodeName.equals(childName)) {
                    System.out.println("DEBUG: Found matching child node: " + nodeName);
                    
                    // Convert the child node back to a NodeBuilder
                    NodeBuilder childBuilder = NodeBuilder.fromNode(childNode);
                    System.out.println("DEBUG: Successfully created NodeBuilder for child: " + nodeName);
                    return childBuilder;
                }
            }
            
            System.err.println("DEBUG: Child '" + childName + "' not found among available children: " + 
                              childNodes.stream().map(CalculationNode::name).collect(java.util.stream.Collectors.toList()));
            
        } catch (Exception e) {
            System.err.println("ERROR: Exception while finding child NodeBuilder for '" + childName + "': " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * Get available child names for debugging.
     */
    private java.util.List<String> getAvailableChildNames(NodeBuilder parentBuilder) {
        if (!(parentBuilder instanceof NodeGroupBuilder ngb)) {
            return java.util.List.of("(not a NodeGroupBuilder)");
        }
        
        try {
            Set<CalculationNode> childNodes = ngb.nodes();
            return childNodes.stream()
                           .map(CalculationNode::name)
                           .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            return java.util.List.of("(error getting children: " + e.getMessage() + ")");
        }
    }
    
    private void initializeWindow() {
        setTitle("Edit Node: " + currentPath);
        setMaximized(true); // Maximize to fill the screen
        initModality(Modality.APPLICATION_MODAL);
        setResizable(true);
    }
    
    private void initializeComponents() {
        // Create navigation bar with Export and Run buttons on the right
        navigationBar = new PathNavigationBar();
        navigationBar.setRightButtons(
            new String[]{"Export", "Run"}, 
            new Runnable[]{this::handleExport, this::handleRun}
        );
        
        // Set up navigation bar event handlers
        navigationBar.setOnSegmentClicked(this::handlePathSegmentClick);
        
        // Set path segments based on current path
        navigationBar.setPathSegments(getPathSegments(currentPath));
        
        // Create edit canvas model for editing (no evaluation results)
        editCanvasModel = new EditCanvasModel(currentNodeBuilder, currentPath);
        
        // Create edit canvas
        editCanvas = new CalculationCanvas();
        editCanvas.setEditModel(editCanvasModel);
        
        // Set up canvas event handlers
        editCanvas.setOnNodeDoubleClicked(this::handleNodeDoubleClick);
        editCanvas.setOnNodeClicked(this::handleNodeClick);
        editCanvas.setOnMultipleNodesSelected(this::handleMultipleNodesSelected);
        editCanvas.setOnStructuralChange(this::handleStructuralChange);
        
        // Initialize visibility - show all nodes by default in edit mode
        initializeNodeVisibility();
        
        // Column 1: Request (editable JSON)
        requestColumn = new VBox(10);
        requestColumn.setPadding(new Insets(10));
        requestColumn.setMinWidth(COLUMN_MIN_WIDTH);
        
        snapshotArea = new TextArea();
        requestPathArea = new TextArea();
        requestedResourcesArea = new TextArea();
        adhocOverrideArea = new TextArea();
        
        // Column 2: Node operations, flywires, scope
        nodeOperationsColumn = new VBox(10);
        nodeOperationsColumn.setPadding(new Insets(10));
        nodeOperationsColumn.setMinWidth(COLUMN_MIN_WIDTH);
        
        nodeCheckboxContainer = new VBox(5);
        flywireContainer = new VBox(5);
        scopeContainer = new VBox(5);
        
        createGroupButton = new Button("Create NodeGroup from Selected");
        createGroupButton.setOnAction(e -> handleCreateNodeGroup());
        createGroupButton.setDisable(true);
        
        // Column 3: Atomic node creation
        atomicNodeColumn = new VBox(10);
        atomicNodeColumn.setPadding(new Insets(10));
        atomicNodeColumn.setMinWidth(COLUMN_MIN_WIDTH);
        
        atomicNodeContainer = new VBox(5);
        
        // Controls split pane (for the 3 columns at bottom)
        SplitPane controlsPane = new SplitPane();
        controlsPane.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        controlsPane.getItems().addAll(requestColumn, nodeOperationsColumn, atomicNodeColumn);
        controlsPane.setDividerPositions(0.33, 0.66);
        
        // Main split pane (canvas on top, controls on bottom)
        canvasControlsSplit = new SplitPane();
        canvasControlsSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        canvasControlsSplit.getItems().addAll(editCanvas, controlsPane);
        canvasControlsSplit.setDividerPositions(0.75); // 75% canvas, 25% bottom panes
    }
    
    /**
     * Initialize node visibility - show all nodes by default in edit mode.
     */
    private void initializeNodeVisibility() {
        visibleNodeNames.clear();

        // Clear the model's filter first so getNodes() returns ALL nodes (unfiltered)
        editCanvasModel.setVisibleNodes(Set.of());

        // Get all nodes and make them all visible by default
        var allNodes = editCanvasModel.getNodes();
        for (var node : allNodes) {
            visibleNodeNames.add(node.getDisplayName());
        }

        // Set visibility in model
        editCanvasModel.setVisibleNodes(visibleNodeNames);
    }
    
    /**
     * Update canvas visibility by filtering nodes and connections.
     */
    private void updateCanvasVisibility() {
        System.out.println("DEBUG: Updating canvas visibility. Visible nodes: " + visibleNodeNames);
        
        // Get all nodes from model
        var allNodes = editCanvasModel.getNodes();
        var allConnections = editCanvasModel.getConnections();
        
        // Filter nodes based on visibility
        List<me.vincentzz.visual.model.NodeViewModel> visibleNodes = new ArrayList<>();
        for (var node : allNodes) {
            if (visibleNodeNames.contains(node.getDisplayName())) {
                visibleNodes.add(node);
            }
        }
        
        // Filter connections to only show those between visible nodes
        List<me.vincentzz.visual.model.ConnectionViewModel> visibleConnections = new ArrayList<>();
        for (var connection : allConnections) {
            String sourceName = connection.getSourcePath().getFileName() != null ? 
                               connection.getSourcePath().getFileName().toString() : 
                               connection.getSourcePath().toString();
            String targetName = connection.getTargetPath().getFileName() != null ? 
                               connection.getTargetPath().getFileName().toString() : 
                               connection.getTargetPath().toString();
            
            if (visibleNodeNames.contains(sourceName) && visibleNodeNames.contains(targetName)) {
                visibleConnections.add(connection);
            }
        }
        
        // Update canvas with filtered nodes and connections
        editCanvas.setNodes(visibleNodes);
        editCanvas.setConnections(visibleConnections);
        editCanvas.refresh();
        
        System.out.println("DEBUG: Applied visibility filter. Showing " + visibleNodes.size() + " out of " + allNodes.size() + " nodes");
    }
    
    private List<String> getPathSegments(Path path) {
        List<String> segments = new ArrayList<>();
        if (path.toString().equals("/") || path.toString().equals("/root")) {
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
    
    private void setupLayout() {
        setupRequestColumn();
        setupNodeOperationsColumn();
        setupAtomicNodeColumn();
        
        // Main layout
        VBox mainLayout = new VBox();
        mainLayout.getChildren().addAll(navigationBar, canvasControlsSplit);
        VBox.setVgrow(canvasControlsSplit, Priority.ALWAYS);
        
        Scene scene = new Scene(mainLayout);
        setScene(scene);
    }
    
    private void setupRequestColumn() {
        Label requestLabel = new Label("Request Parameters (Editable JSON)");
        requestLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        requestLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        
        // Snapshot section
        Label snapshotLabel = new Label("Snapshot:");
        snapshotLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        snapshotLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        
        snapshotArea.setPrefRowCount(6);
        snapshotArea.setEditable(true);
        snapshotArea.setWrapText(true);
        
        // Request path section
        Label pathLabel = new Label("Requested Node Path:");
        pathLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        pathLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        
        requestPathArea.setPrefRowCount(2);
        requestPathArea.setEditable(true);
        requestPathArea.setWrapText(true);
        
        // Requested resources section
        Label resourcesLabel = new Label("Requested Resources:");
        resourcesLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        resourcesLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        
        requestedResourcesArea.setPrefRowCount(4);
        requestedResourcesArea.setEditable(true);
        requestedResourcesArea.setWrapText(true);
        
        // Adhoc override section
        Label adhocLabel = new Label("Adhoc Override:");
        adhocLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        adhocLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        
        adhocOverrideArea.setPrefRowCount(10);
        adhocOverrideArea.setEditable(true);
        adhocOverrideArea.setWrapText(true);
        
        requestColumn.getChildren().addAll(
            requestLabel,
            snapshotLabel, snapshotArea,
            pathLabel, requestPathArea,
            resourcesLabel, requestedResourcesArea,
            adhocLabel, adhocOverrideArea
        );
        
        VBox.setVgrow(adhocOverrideArea, Priority.ALWAYS);
    }
    
    private void setupNodeOperationsColumn() {
        Label visibilityLabel = new Label("Node Visibility");
        visibilityLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        visibilityLabel.setTextFill(ColorScheme.TEXT_PRIMARY);

        Label instructionLabel = new Label("Check/uncheck to show/hide nodes on canvas:");
        instructionLabel.setFont(Font.font("System", 11));
        instructionLabel.setTextFill(ColorScheme.TEXT_SECONDARY);
        instructionLabel.setWrapText(true);

        ScrollPane nodeScrollPane = new ScrollPane(nodeCheckboxContainer);
        nodeScrollPane.setFitToWidth(true);
        nodeScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        nodeScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        styleScrollPane(nodeScrollPane);

        // Create Group button
        createGroupButton.setMaxWidth(Double.MAX_VALUE);

        // Flywires section
        Label flywireLabel = new Label("Flywires");
        flywireLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        flywireLabel.setTextFill(ColorScheme.TEXT_PRIMARY);

        Button addFlywireButton = new Button("Add Flywire");
        addFlywireButton.setOnAction(e -> handleAddFlywire());
        addFlywireButton.setMaxWidth(Double.MAX_VALUE);

        ScrollPane flywireScrollPane = new ScrollPane(flywireContainer);
        flywireScrollPane.setFitToWidth(true);
        flywireScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        flywireScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        flywireScrollPane.setPrefHeight(120);
        styleScrollPane(flywireScrollPane);

        // Scope section
        Label scopeLabel = new Label("Scope (Exports)");
        scopeLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        scopeLabel.setTextFill(ColorScheme.TEXT_PRIMARY);

        Button editScopeButton = new Button("Edit Scope");
        editScopeButton.setOnAction(e -> handleEditScope());
        editScopeButton.setMaxWidth(Double.MAX_VALUE);

        ScrollPane scopeScrollPane = new ScrollPane(scopeContainer);
        scopeScrollPane.setFitToWidth(true);
        scopeScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scopeScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scopeScrollPane.setPrefHeight(80);
        styleScrollPane(scopeScrollPane);

        nodeOperationsColumn.getChildren().addAll(
            visibilityLabel,
            instructionLabel,
            nodeScrollPane,
            createGroupButton,
            flywireLabel,
            addFlywireButton,
            flywireScrollPane,
            scopeLabel,
            editScopeButton,
            scopeScrollPane
        );

        // Set the scroll pane to fill remaining space
        VBox.setVgrow(nodeScrollPane, Priority.ALWAYS);
    }
    
    private void setupAtomicNodeColumn() {
        Label atomicLabel = new Label("Create Atomic Nodes");
        atomicLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        atomicLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        
        Label descLabel = new Label("Available atomic node classes:");
        descLabel.setFont(Font.font("System", 11));
        descLabel.setTextFill(ColorScheme.TEXT_SECONDARY);
        descLabel.setWrapText(true);
        
        ScrollPane scrollPane = new ScrollPane(atomicNodeContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        styleScrollPane(scrollPane); // Add missing scroll pane styling
        
        atomicNodeColumn.getChildren().addAll(atomicLabel, descLabel, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
    }
    
    private void populateData() {
        populateRequestData();
        populateNodeVisibility();
        populateFlywires();
        populateScope();
        populateAtomicNodeOptions();
    }
    
    private void populateRequestData() {
        try {
            // Populate with raw JSON data (using the ObjectMapper directly)
            var objectMapper = ConstructionalJsonUtil.getObjectMapper();
            snapshotArea.setText(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot));
            requestPathArea.setText(requestedNodePath.toString());
            
            // Populate requested resources as JSON array
            if (requestedResources != null && !requestedResources.isEmpty()) {
                requestedResourcesArea.setText(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestedResources));
            } else {
                requestedResourcesArea.setText("[]");
            }
            
            if (adhocOverride.isPresent()) {
                adhocOverrideArea.setText(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(adhocOverride.get()));
            } else {
                adhocOverrideArea.setText("{}");
            }
        } catch (Exception e) {
            snapshotArea.setText("Error loading snapshot: " + e.getMessage());
            requestPathArea.setText(requestedNodePath.toString());
            requestedResourcesArea.setText("[]");
            adhocOverrideArea.setText("{}");
        }
    }
    
    private void populateNodeVisibility() {
        populateNodeVisibilityCheckboxes();
    }
    
    /**
     * Populate visibility checkboxes based on model nodes.
     */
    private void populateNodeVisibilityCheckboxes() {
        nodeCheckboxContainer.getChildren().clear();
        nodeVisibilityMap.clear();
        
        // Get nodes from model
        var nodes = editCanvasModel.getNodes();
        for (var node : nodes) {
            String nodeName = node.getDisplayName();
            CheckBox checkBox = new CheckBox(nodeName);
            
            // Check if this node is currently visible
            checkBox.setSelected(visibleNodeNames.contains(nodeName));
            checkBox.setTextFill(ColorScheme.TEXT_PRIMARY);
            
            // Add node type indicator
            String nodeType = node.isNodeGroup() ? " (NodeGroup)" : " (AtomicNode)";
            checkBox.setText(nodeName + nodeType);
            
            // Add event handler for visibility changes
            checkBox.setOnAction(e -> {
                if (checkBox.isSelected()) {
                    visibleNodeNames.add(nodeName);
                } else {
                    visibleNodeNames.remove(nodeName);
                }
                updateCanvasVisibility();
            });
            
            // Add selection handler for multi-select
            checkBox.setOnMouseClicked(e -> {
                if (e.isControlDown()) {
                    if (selectedNodeNames.contains(nodeName)) {
                        selectedNodeNames.remove(nodeName);
                    } else {
                        selectedNodeNames.add(nodeName);
                    }
                    updateCreateGroupButton();
                }
            });
            
            nodeVisibilityMap.put(nodeName, checkBox);
            nodeCheckboxContainer.getChildren().add(checkBox);
        }
    }
    
    private void populateFlywires() {
        flywireContainer.getChildren().clear();
        
        if (currentNodeBuilder instanceof NodeGroupBuilder ngb) {
            var flywires = ngb.flywires();
            for (Flywire flywire : flywires) {
                HBox flywireRow = createFlywireRow(flywire);
                flywireContainer.getChildren().add(flywireRow);
            }
        }
    }
    
    private HBox createFlywireRow(Flywire flywire) {
        HBox row = new HBox(5);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        
        Label flywireLabel = new Label(formatFlywireShort(flywire));
        flywireLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        flywireLabel.setPrefWidth(250);
        
        Button removeButton = new Button("Remove");
        removeButton.setOnAction(e -> handleRemoveFlywire(flywire));
        styleButton(removeButton); // Apply styling immediately
        
        row.getChildren().addAll(flywireLabel, removeButton);
        return row;
    }
    
    private void populateScope() {
        scopeContainer.getChildren().clear();

        if (currentNodeBuilder instanceof NodeGroupBuilder ngb) {
            Scope<ConnectionPoint> scope = ngb.getExports();
            String modeText;
            Set<ConnectionPoint> points;
            if (scope instanceof Include<ConnectionPoint> inc) {
                modeText = "Include";
                points = inc.resources();
            } else if (scope instanceof Exclude<ConnectionPoint> exc) {
                modeText = "Exclude";
                points = exc.resources();
            } else {
                modeText = "Unknown";
                points = Set.of();
            }

            Label modeLabel = new Label("Mode: " + modeText + " (" + points.size() + " entries)");
            modeLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
            scopeContainer.getChildren().add(modeLabel);

            for (ConnectionPoint cp : points) {
                Label cpLabel = new Label("  " + cp.nodePath() + " : " + cp.rid());
                cpLabel.setTextFill(ColorScheme.TEXT_SECONDARY);
                scopeContainer.getChildren().add(cpLabel);
            }
        }
    }
    
    private void populateAtomicNodeOptions() {
        atomicNodeContainer.getChildren().clear();

        List<String> atomicNodeClasses = getAvailableAtomicNodeClasses();

        // Collect existing instances grouped by class simple name
        Map<String, List<CalculationNode>> instancesByClass = new LinkedHashMap<>();
        if (currentNodeBuilder instanceof NodeGroupBuilder ngb) {
            for (CalculationNode child : ngb.nodes()) {
                String className = child.getClass().getSimpleName();
                instancesByClass.computeIfAbsent(className, k -> new ArrayList<>()).add(child);
            }
        }

        for (String atomicNodeClass : atomicNodeClasses) {
            VBox classSection = new VBox(3);

            HBox nodeRow = new HBox(10);
            nodeRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            Label nodeLabel = new Label(atomicNodeClass);
            nodeLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
            nodeLabel.setPrefWidth(200);

            Button createButton = new Button("Create");
            createButton.setOnAction(e -> handleCreateAtomicNode(atomicNodeClass));
            styleButton(createButton);

            nodeRow.getChildren().addAll(nodeLabel, createButton);
            classSection.getChildren().add(nodeRow);

            // Add foldable tree of existing instances
            List<CalculationNode> instances = instancesByClass.get(atomicNodeClass);
            if (instances != null && !instances.isEmpty()) {
                VBox instanceList = new VBox(2);
                instanceList.setPadding(new Insets(2, 0, 2, 10));
                for (CalculationNode instance : instances) {
                    Label instanceLabel = new Label("  " + instance.name());
                    instanceLabel.setTextFill(ColorScheme.TEXT_SECONDARY);
                    instanceLabel.setFont(Font.font("System", 11));
                    instanceList.getChildren().add(instanceLabel);
                }
                TitledPane titledPane = new TitledPane(
                    instances.size() + " instance" + (instances.size() > 1 ? "s" : ""),
                    instanceList
                );
                titledPane.setExpanded(false);
                titledPane.setAnimated(false);
                titledPane.setStyle(
                    "-fx-text-fill: " + toHexString(ColorScheme.TEXT_SECONDARY) + ";" +
                    "-fx-font-size: 11px;"
                );
                classSection.getChildren().add(titledPane);
            }

            atomicNodeContainer.getChildren().add(classSection);
        }
    }
    
    private List<String> getAvailableAtomicNodeClasses() {
        return Arrays.asList(
            "AskProvider",
            "BidProvider", 
            "VolumeProvider",
            "MidSpreadCalculator",
            "VwapCalculator",
            "MarkToMarketCalculator",
            "HardcodeAttributeProvider"
        );
    }
    
    // Event handlers
    
    private void handleNodeClick(Path nodePath) {
        String nodeName = nodePath.getFileName() != null ? 
                         nodePath.getFileName().toString() : nodePath.toString();
        
        // Find the clicked node
        NodeViewModel clickedNode = findNodeByName(nodeName);
        if (clickedNode == null) return;
        
        // Handle selection based on whether Ctrl is held
        // Note: We'll need to modify CalculationCanvas to pass mouse event info
        // For now, implement toggle behavior
        
        if (clickedNode.isSelected()) {
            // Deselect the node
            clickedNode.setSelected(false);
            selectedNodeNames.remove(nodeName);
        } else {
            // Select the node
            clickedNode.setSelected(true);
            selectedNodeNames.add(nodeName);
        }
        
        updateCreateGroupButton();
        updateCanvasVisibility(); // Refresh to show selection highlighting
    }
    
    /**
     * Handle node click with mouse event information for Ctrl+click support.
     */
    private void handleNodeClickWithEvent(Path nodePath, boolean isCtrlDown) {
        String nodeName = nodePath.getFileName() != null ? 
                         nodePath.getFileName().toString() : nodePath.toString();
        
        // Find the clicked node
        NodeViewModel clickedNode = findNodeByName(nodeName);
        if (clickedNode == null) return;
        
        if (isCtrlDown) {
            // Ctrl+click: toggle selection of this node without affecting others
            if (clickedNode.isSelected()) {
                clickedNode.setSelected(false);
                selectedNodeNames.remove(nodeName);
            } else {
                clickedNode.setSelected(true);
                selectedNodeNames.add(nodeName);
            }
        } else {
            // Regular click: clear all selections and select only this node
            clearAllSelections();
            clickedNode.setSelected(true);
            selectedNodeNames.add(nodeName);
        }
        
        updateCreateGroupButton();
        updateCanvasVisibility(); // Refresh to show selection highlighting
    }
    
    /**
     * Clear all node selections.
     */
    private void clearAllSelections() {
        selectedNodeNames.clear();
        // Let canvas handle clearing selections internally
    }
    
    /**
     * Find a node by its display name.
     */
    private NodeViewModel findNodeByName(String nodeName) {
        // Get nodes from model
        var nodes = editCanvasModel.getNodes();
        return nodes.stream()
                   .filter(node -> node.getDisplayName().equals(nodeName))
                   .findFirst()
                   .orElse(null);
    }
    
    /**
     * Handle structural changes from the canvas (like grouping/ungrouping).
     * This refreshes the visibility controls to reflect the new node structure.
     */
    private void handleStructuralChange() {
        try {
            // Get the updated NodeBuilder from the canvas's edit model
            NodeBuilder updatedNodeBuilder = editCanvasModel.getCurrentNodeBuilder();
            
            // Update our current NodeBuilder reference
            this.currentNodeBuilder = updatedNodeBuilder;
            
            // Update the root NodeBuilder to reflect changes
            // We need to rebuild the path to the current node with the updated structure
            updateRootNodeBuilderFromCurrent();
            
            // Recreate the EditCanvasModel with the updated NodeBuilder
            editCanvasModel = new EditCanvasModel(currentNodeBuilder, currentPath);
            editCanvas.setEditModel(editCanvasModel);
            
            // Refresh node visibility controls to reflect structural changes
            initializeNodeVisibility(); // Reset visibility to show all nodes
            populateNodeVisibility();
            
            // Also refresh other components that might be affected
            populateFlywires();
            populateScope();
            populateAtomicNodeOptions();

            System.out.println("DEBUG: Refreshed EditNodeWindow with updated NodeBuilder after structural change");

        } catch (Exception e) {
            System.err.println("ERROR: Failed to handle structural change: " + e.getMessage());
            e.printStackTrace();

            // Fallback: just refresh the UI without updating the model
            populateNodeVisibility();
            populateFlywires();
            populateScope();
            populateAtomicNodeOptions();
        }
    }
    
    /**
     * Update the root NodeBuilder to reflect changes made at the current path.
     * This propagates structural changes back up to the root.
     * Enhanced to handle multi-layer grouping properly.
     */
    private void updateRootNodeBuilderFromCurrent() {
        // No-op: All mutations go through getChildBuilder() references which are
        // live pointers into the mutable builder tree. rootNodeBuilder always
        // reflects the latest state without needing to be recreated.
        // Recreating via NodeBuilder.fromNode() would create a DISCONNECTED copy
        // that breaks referential integrity with currentNodeBuilder and the
        // EditCanvasModel's builder stack.
    }
    
    /**
     * Rebuild the root NodeBuilder from an updated node at the current path.
     * This method properly handles multi-layer grouping by reconstructing the entire hierarchy.
     */
    private NodeBuilder rebuildRootFromUpdatedNode(CalculationNode updatedCurrentNode) {
        try {
            // If we're at root, the updated node IS the root
            if (currentPath.toString().equals("/") || currentPath.toString().equals("/root")) {
                return NodeBuilder.fromNode(updatedCurrentNode);
            }
            
            // For nested paths, we need to rebuild the hierarchy
            // Start with the original root and navigate down, replacing the current node
            CalculationNode originalRoot = NodeBuilder.fromNode(originalGraph).toNode();
            
            // Replace the node at currentPath with the updated node
            CalculationNode newRoot = replaceNodeAtPath(originalRoot, currentPath, updatedCurrentNode);
            
            return NodeBuilder.fromNode(newRoot);
            
        } catch (Exception e) {
            System.err.println("ERROR: Failed to rebuild root from updated node: " + e.getMessage());
            e.printStackTrace();
            
            // Fallback: just use the updated current node as root
            return NodeBuilder.fromNode(updatedCurrentNode);
        }
    }
    
    /**
     * Replace a node at a specific path within the graph structure.
     * Enhanced to handle multi-layer grouping properly.
     */
    private CalculationNode replaceNodeAtPath(CalculationNode rootNode, Path targetPath, CalculationNode replacementNode) {
        System.out.println("DEBUG: Replacing node at path: " + targetPath + " with: " + replacementNode.name());
        
        // If we're at root, return the replacement node
        if (targetPath.toString().equals("/") || targetPath.toString().equals("/root")) {
            System.out.println("DEBUG: Target path is root, returning replacement node");
            return replacementNode;
        }
        
        // For non-root paths, we need to traverse and reconstruct the hierarchy
        if (!(rootNode instanceof NodeGroup)) {
            System.err.println("ERROR: Root node is not a NodeGroup, cannot replace nested node");
            return replacementNode; // Fallback
        }
        
        NodeGroup rootGroup = (NodeGroup) rootNode;
        
        // Parse the target path to get segments
        String pathStr = targetPath.toString();
        if (pathStr.startsWith("/root/")) {
            pathStr = pathStr.substring("/root/".length());
        } else if (pathStr.startsWith("/")) {
            pathStr = pathStr.substring(1);
        }
        
        if (pathStr.isEmpty()) {
            return replacementNode;
        }
        
        String[] segments = pathStr.split("/");
        
        // If there's only one segment, replace directly in the root group
        if (segments.length == 1) {
            String targetNodeName = segments[0];
            System.out.println("DEBUG: Replacing direct child '" + targetNodeName + "' in root group");
            
            return replaceChildInNodeGroup(rootGroup, targetNodeName, replacementNode);
        }
        
        // For multiple segments, we need to navigate deeper
        System.out.println("DEBUG: Multi-segment path, navigating deeper: " + java.util.Arrays.toString(segments));
        
        // Navigate to the parent of the target node and replace the child
        String firstSegment = segments[0];
        String[] remainingSegments = java.util.Arrays.copyOfRange(segments, 1, segments.length);
        Path remainingPath = java.nio.file.Paths.get("/" + String.join("/", remainingSegments));
        
        // Find the first child and recursively replace within it
        Set<CalculationNode> updatedChildren = new HashSet<>();
        boolean foundChild = false;
        
        for (CalculationNode child : rootGroup.nodes()) {
            if (child.name().equals(firstSegment)) {
                System.out.println("DEBUG: Found child '" + firstSegment + "', recursively replacing within it");
                CalculationNode updatedChild = replaceNodeAtPath(child, remainingPath, replacementNode);
                updatedChildren.add(updatedChild);
                foundChild = true;
            } else {
                updatedChildren.add(child);
            }
        }
        
        if (!foundChild) {
            System.err.println("ERROR: Could not find child '" + firstSegment + "' in root group");
            return rootNode; // Return original if we can't find the path
        }
        
        // Create new NodeGroup with updated children
        return new NodeGroup(
            rootGroup.name(),
            updatedChildren,
            rootGroup.flywires(),
            rootGroup.exports()
        );
    }
    
    /**
     * Replace a specific child in a NodeGroup.
     */
    private CalculationNode replaceChildInNodeGroup(NodeGroup parentGroup, String childName, CalculationNode replacementChild) {
        Set<CalculationNode> updatedChildren = new HashSet<>();
        boolean foundChild = false;
        
        for (CalculationNode child : parentGroup.nodes()) {
            if (child.name().equals(childName)) {
                System.out.println("DEBUG: Replacing child '" + childName + "' with '" + replacementChild.name() + "'");
                updatedChildren.add(replacementChild);
                foundChild = true;
            } else {
                updatedChildren.add(child);
            }
        }
        
        if (!foundChild) {
            System.err.println("ERROR: Could not find child '" + childName + "' to replace");
            // Add the replacement child anyway
            updatedChildren.add(replacementChild);
        }
        
        // Create new NodeGroup with updated children
        return new NodeGroup(
            parentGroup.name(),
            updatedChildren,
            parentGroup.flywires(),
            parentGroup.exports()
        );
    }
    
    private void handleNodeDoubleClick(Path nodePath) {
        // nodePath is a full path (e.g. /root/childName). Use navigateToPath
        // which walks from rootNodeBuilder — works reliably after any edits.
        navigateToPath(nodePath);
    }
    
    private void handleMultipleNodesSelected(Set<Path> nodePaths) {
        selectedNodeNames.clear();
        for (Path path : nodePaths) {
            String nodeName = path.getFileName() != null ? 
                             path.getFileName().toString() : path.toString();
            selectedNodeNames.add(nodeName);
        }
        updateCreateGroupButton();
    }
    
    private void updateCreateGroupButton() {
        createGroupButton.setDisable(selectedNodeNames.size() < 2);
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
                e.printStackTrace();
            }
        });
    }
    
    private void handleCreateNodeGroup() {
        if (selectedNodeNames.size() < 2) return;
        if (!(currentNodeBuilder instanceof NodeGroupBuilder ngb)) return;

        TextInputDialog dialog = new TextInputDialog("NewGroup");
        dialog.setTitle("Create NodeGroup");
        dialog.setHeaderText("Create NodeGroup from selected nodes");
        dialog.setContentText("Enter group name:");

        dialog.showAndWait().ifPresent(groupName -> {
            try {
                // Collect the child nodes that were selected
                Set<CalculationNode> selectedNodes = new HashSet<>();
                for (CalculationNode node : ngb.nodes()) {
                    if (selectedNodeNames.contains(node.name())) {
                        selectedNodes.add(node);
                    }
                }

                Set<String> selectedNames = selectedNodes.stream()
                    .map(CalculationNode::name)
                    .collect(java.util.stream.Collectors.toSet());

                // Partition flywires: internal (both endpoints in selection) vs cross-boundary
                Set<Flywire> internalFlywires = new HashSet<>();
                Set<Flywire> crossBoundaryFlywires = new HashSet<>();
                for (Flywire fw : ngb.flywires()) {
                    String srcName = fw.source().nodePath().getFileName() != null
                        ? fw.source().nodePath().getFileName().toString()
                        : fw.source().nodePath().toString();
                    String tgtName = fw.target().nodePath().getFileName() != null
                        ? fw.target().nodePath().getFileName().toString()
                        : fw.target().nodePath().toString();
                    boolean srcIn = selectedNames.contains(srcName);
                    boolean tgtIn = selectedNames.contains(tgtName);
                    if (srcIn && tgtIn) {
                        internalFlywires.add(fw);
                    } else if (srcIn || tgtIn) {
                        crossBoundaryFlywires.add(fw);
                    }
                }

                // Remove selected nodes and their flywires from parent
                ngb.deleteNodes(selectedNames);
                for (Flywire fw : internalFlywires) {
                    ngb.deleteFlywire(fw);
                }
                for (Flywire fw : crossBoundaryFlywires) {
                    ngb.deleteFlywire(fw);
                }

                // Create the new NodeGroup with the selected nodes and internal flywires
                NodeGroup newGroup = new NodeGroup(
                    groupName,
                    selectedNodes,
                    internalFlywires,
                    Exclude.of(Set.of())
                );
                ngb.addNode(newGroup);

                if (!crossBoundaryFlywires.isEmpty()) {
                    showErrorAlert("Warning",
                        crossBoundaryFlywires.size() + " cross-boundary flywire(s) were removed during grouping. " +
                        "You may need to recreate them manually.");
                }

                selectedNodeNames.clear();
                updateCreateGroupButton();
                refreshAfterStructuralChange();
            } catch (Exception e) {
                showErrorAlert("Group Creation Failed", "Failed to create NodeGroup: " + e.getMessage());
                e.printStackTrace();
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
                showErrorAlert("Flywire Creation Failed",
                    "Incompatible types: " + e.getMessage());
            }
        });
    }
    
    private void handleRemoveFlywire(Flywire flywire) {
        if (currentNodeBuilder instanceof NodeGroupBuilder ngb) {
            ngb.deleteFlywire(flywire);
            refreshAfterStructuralChange();
        }
    }

    private void refreshAfterStructuralChange() {
        updateRootNodeBuilderFromCurrent();
        // Update the model in-place to preserve the navigation stack
        editCanvasModel.updateNodeBuilder(currentNodeBuilder);
        editCanvas.setEditModel(editCanvasModel);
        initializeNodeVisibility();
        populateNodeVisibility();
        populateFlywires();
        populateScope();
        populateAtomicNodeOptions();
        updateCanvasVisibility();
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
    
    private void navigateToPath(Path newPath) {
        try {
            // Walk from rootNodeBuilder using getChildBuilder() to reach the target.
            // This avoids relying on the model's builder stack which can become stale
            // after structural changes.
            NodeBuilder targetBuilder = rootNodeBuilder;
            Path targetPath = java.nio.file.Paths.get("/root");

            String pathStr = newPath.toString();
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

            // Update our fields
            this.currentNodeBuilder = targetBuilder;
            this.currentPath = targetPath;

            // Recreate model fresh at the target level (clean stack)
            editCanvasModel = new EditCanvasModel(currentNodeBuilder, currentPath);

            // Refresh canvas
            editCanvas.setEditModel(editCanvasModel);
            navigationBar.setPathSegments(getPathSegments(currentPath));
            initializeNodeVisibility();
            populateNodeVisibility();
            populateFlywires();
            populateScope();
            populateAtomicNodeOptions();
            updateCanvasVisibility();
            setTitle("Edit Node: " + currentPath);

        } catch (Exception e) {
            showErrorAlert("Navigation Error", "Failed to navigate to path: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handlePathSegmentClick(int segmentIndex) {
        try {
            var segments = getPathSegments(currentPath);
            
            // Handle root navigation (home button)
            if (segmentIndex == 0) {
                navigateToPath(java.nio.file.Paths.get("/root"));
                return;
            }
            
            // Build path up to the clicked segment
            if (segmentIndex < segments.size()) {
                // Build target path from segments
                StringBuilder pathBuilder = new StringBuilder();
                for (int i = 0; i <= segmentIndex; i++) {
                    if (i == 0) {
                        pathBuilder.append("/").append(segments.get(i));
                    } else {
                        pathBuilder.append("/").append(segments.get(i));
                    }
                }
                
                Path targetPath = java.nio.file.Paths.get(pathBuilder.toString());
                navigateToPath(targetPath);
            }
        } catch (Exception e) {
            showErrorAlert("Navigation Error", "Failed to navigate to segment: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleExport() {
        try {
            // Convert current NodeBuilder state to CalculationNode
            CalculationNode currentGraph = rootNodeBuilder.toNode();
            
            // Convert to JSON using ConstructionalJsonUtil
            var jsonResult = ConstructionalJsonUtil.toJson(currentGraph);
            
            if (jsonResult instanceof me.vincentzz.lang.Result.Failure<?> failure) {
                showErrorAlert("Export Failed", "Failed to serialize graph to JSON: " + failure.getException().getMessage());
                return;
            }
            
            String jsonContent = ((me.vincentzz.lang.Result.Success<String>) jsonResult).get();
            
            // Show file chooser to save the JSON
            javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
            fileChooser.setTitle("Export Graph to JSON");
            fileChooser.setInitialFileName("exported-graph.json");
            fileChooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("JSON Files", "*.json")
            );
            
            java.io.File file = fileChooser.showSaveDialog(this);
            if (file != null) {
                // Write JSON content to file
                java.nio.file.Files.writeString(file.toPath(), jsonContent);
                
                // Show success message
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Export Successful");
                alert.setHeaderText(null);
                alert.setContentText("Graph exported successfully to: " + file.getAbsolutePath());
                alert.showAndWait();
                
                System.out.println("DEBUG: Successfully exported graph to: " + file.getAbsolutePath());
            }
            
        } catch (Exception e) {
            showErrorAlert("Export Failed", "Failed to export graph to JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleRun() {
        try {
            // Convert NodeBuilder back to CalculationNode
            CalculationNode modifiedGraph = rootNodeBuilder.toNode();
            
            // Parse modified request data using ObjectMapper
            var objectMapper = ConstructionalJsonUtil.getObjectMapper();
            Snapshot modifiedSnapshot = objectMapper.readValue(snapshotArea.getText(), Snapshot.class);
            Path modifiedRequestPath = java.nio.file.Paths.get(requestPathArea.getText().trim());
            Optional<AdhocOverride> modifiedAdhocOverride = Optional.empty();
            
            if (!adhocOverrideArea.getText().trim().equals("{}")) {
                modifiedAdhocOverride = Optional.of(objectMapper.readValue(adhocOverrideArea.getText(), AdhocOverride.class));
            }
            
            // Parse requested resources from the text area
            Set<me.vincentzz.graph.model.ResourceIdentifier> modifiedRequestedResources = Set.of();
            String requestedResourcesText = requestedResourcesArea.getText().trim();
            if (!requestedResourcesText.equals("[]") && !requestedResourcesText.isEmpty()) {
                try {
                    // Parse as JSON array of ResourceIdentifiers
                    var typeRef = new com.fasterxml.jackson.core.type.TypeReference<Set<me.vincentzz.graph.model.ResourceIdentifier>>() {};
                    modifiedRequestedResources = objectMapper.readValue(requestedResourcesText, typeRef);
                    System.out.println("DEBUG: Parsed " + modifiedRequestedResources.size() + " requested resources from text area");
                } catch (Exception e) {
                    System.err.println("ERROR: Failed to parse requested resources, using empty set: " + e.getMessage());
                    modifiedRequestedResources = Set.of();
                }
            }
            
            // Create evaluation engine and evaluate
            CalculationEngine engine = new CalculationEngine(modifiedGraph);
            
            // Evaluate with modified parameters including the parsed requested resources
            var newEvaluationResult = engine.evaluateForResult(modifiedRequestPath, modifiedSnapshot, modifiedRequestedResources, modifiedAdhocOverride);
            
            // Notify completion
            if (onRunCompleted != null) {
                onRunCompleted.accept(newEvaluationResult);
            }
            
            close();
            
        } catch (Exception e) {
            showErrorAlert("Run Failed", "Failed to evaluate modified graph: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void styleComponents() {
        requestColumn.setStyle("-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_MEDIUM) + ";");
        nodeOperationsColumn.setStyle("-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_MEDIUM) + ";");
        atomicNodeColumn.setStyle("-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_MEDIUM) + ";");
        
        styleTextArea(snapshotArea);
        styleTextArea(requestPathArea);
        styleTextArea(requestedResourcesArea);
        styleTextArea(adhocOverrideArea);
        
        styleButton(createGroupButton);
        
        // Style all labels in node operations column
        styleLabelsInContainer(nodeOperationsColumn);
        styleLabelsInContainer(atomicNodeColumn);
        
        // Style checkboxes in node operations column for better visibility
        for (CheckBox checkBox : nodeVisibilityMap.values()) {
            checkBox.setStyle(
                "-fx-text-fill: " + toHexString(ColorScheme.TEXT_PRIMARY) + ";" +
                "-fx-font-size: 12px;"
            );
        }
        
        // Style buttons in atomic node column
        styleButtonsInContainer(atomicNodeContainer);
        
        // Style flywire labels and buttons
        styleLabelsInContainer(flywireContainer);
        styleButtonsInContainer(flywireContainer);
        
        // Style scope labels
        styleLabelsInContainer(scopeContainer);
    }
    
    /**
     * Style all Labels in a container recursively.
     */
    private void styleLabelsInContainer(javafx.scene.Parent container) {
        for (javafx.scene.Node node : container.getChildrenUnmodifiable()) {
            if (node instanceof Label label) {
                label.setTextFill(ColorScheme.TEXT_PRIMARY);
            } else if (node instanceof javafx.scene.Parent parent) {
                styleLabelsInContainer(parent);
            }
        }
    }
    
    /**
     * Style all Buttons in a container recursively.
     */
    private void styleButtonsInContainer(javafx.scene.Parent container) {
        for (javafx.scene.Node node : container.getChildrenUnmodifiable()) {
            if (node instanceof Button button) {
                styleButton(button);
            } else if (node instanceof javafx.scene.Parent parent) {
                styleButtonsInContainer(parent);
            }
        }
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
    
    private void styleButton(Button button) {
        button.setStyle(
            "-fx-background-color: " + toHexString(ColorScheme.NODE_BACKGROUND) + ";" +
            "-fx-text-fill: " + toHexString(ColorScheme.TEXT_PRIMARY) + ";" +
            "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";" +
            "-fx-border-width: 1px;" +
            "-fx-font-size: 12px;"
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
    
    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private String formatFlywireShort(Flywire flywire) {
        return flywire.source().nodePath() + " -> " + flywire.target().nodePath();
    }
    
    private String toHexString(javafx.scene.paint.Color color) {
        return String.format("#%02X%02X%02X",
            (int) (color.getRed() * 255),
            (int) (color.getGreen() * 255),
            (int) (color.getBlue() * 255));
    }
    
    /**
     * Reset all changes to the original state when the window was first opened.
     * This reloads the original parameters without complex revert operations.
     */
    public void resetToOriginalState() {
        try {
            // Reset to original parameters
            this.graph = originalGraph;
            this.snapshot = originalSnapshot;
            this.requestedNodePath = originalRequestedNodePath;
            this.adhocOverride = originalAdhocOverride;
            
            // Reinitialize NodeBuilder from original graph
            this.rootNodeBuilder = NodeBuilder.fromNode(originalGraph);
            this.currentNodeBuilder = navigateToNodeBuilder(rootNodeBuilder, currentPath);
            
            // Recreate edit canvas model with original data
            editCanvasModel = new EditCanvasModel(currentNodeBuilder, currentPath);
            editCanvas.setEditModel(editCanvasModel);
            
            // Repopulate all UI components with original data
            populateData();
            
            // Reset visibility to show all nodes
            initializeNodeVisibility();
            populateNodeVisibility();
            updateCanvasVisibility();
            
            System.out.println("DEBUG: Successfully reset EditNodeWindow to original state");
            
        } catch (Exception e) {
            System.err.println("ERROR: Failed to reset to original state: " + e.getMessage());
            e.printStackTrace();
            showErrorAlert("Reset Failed", "Failed to reset to original state: " + e.getMessage());
        }
    }
    
    // Event setters
    public void setOnRunCompleted(Consumer<EvaluationResult> onRunCompleted) {
        this.onRunCompleted = onRunCompleted;
    }
    
    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }
}
