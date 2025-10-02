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
import me.vincentzz.graph.scope.Scope;
import me.vincentzz.graph.json.ConstructionalJsonUtil;
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
            
            // Navigate to current path to get the NodeBuilder we're editing
            this.currentNodeBuilder = navigateToNodeBuilder(rootNodeBuilder, currentPath);
            
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
        
        // Get all nodes and make them all visible by default
        var allNodes = editCanvasModel.getNodes();
        for (var node : allNodes) {
            visibleNodeNames.add(node.getDisplayName());
        }
        
        // Set visibility in model and let canvas handle state preservation
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
        
        nodeOperationsColumn.getChildren().addAll(
            visibilityLabel,
            instructionLabel,
            nodeScrollPane
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
            // TODO: Display scope information
            Label scopeInfo = new Label("Scope editing not yet implemented");
            scopeInfo.setTextFill(ColorScheme.TEXT_SECONDARY);
            scopeContainer.getChildren().add(scopeInfo);
        }
    }
    
    private void populateAtomicNodeOptions() {
        atomicNodeContainer.getChildren().clear();
        
        List<String> atomicNodeClasses = getAvailableAtomicNodeClasses();
        
        for (String atomicNodeClass : atomicNodeClasses) {
            HBox nodeRow = new HBox(10);
            nodeRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            
            Label nodeLabel = new Label(atomicNodeClass);
            nodeLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
            nodeLabel.setPrefWidth(200);
            
            Button createButton = new Button("Create");
            createButton.setOnAction(e -> handleCreateAtomicNode(atomicNodeClass));
            styleButton(createButton); // Apply styling immediately
            
            nodeRow.getChildren().addAll(nodeLabel, createButton);
            atomicNodeContainer.getChildren().add(nodeRow);
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
            
            System.out.println("DEBUG: Refreshed EditNodeWindow with updated NodeBuilder after structural change");
            
        } catch (Exception e) {
            System.err.println("ERROR: Failed to handle structural change: " + e.getMessage());
            e.printStackTrace();
            
            // Fallback: just refresh the UI without updating the model
            populateNodeVisibility();
            populateFlywires();
            populateScope();
        }
    }
    
    /**
     * Update the root NodeBuilder to reflect changes made at the current path.
     * This propagates structural changes back up to the root.
     * Enhanced to handle multi-layer grouping properly.
     */
    private void updateRootNodeBuilderFromCurrent() {
        try {
            System.out.println("DEBUG: Updating root NodeBuilder from current path: " + currentPath);
            
            // Convert current NodeBuilder to CalculationNode
            CalculationNode updatedCurrentNode = currentNodeBuilder.toNode();
            System.out.println("DEBUG: Converted current NodeBuilder to CalculationNode: " + updatedCurrentNode.name());
            
            // If we're at root, just update the root builder
            if (currentPath.toString().equals("/") || currentPath.toString().equals("/root")) {
                System.out.println("DEBUG: At root path, updating root builder directly");
                this.rootNodeBuilder = NodeBuilder.fromNode(updatedCurrentNode);
                return;
            }
            
            // For non-root paths, we need to properly reconstruct the hierarchy
            System.out.println("DEBUG: Non-root path, reconstructing hierarchy");
            
            // CRITICAL FIX: Use the current root NodeBuilder state, not the original graph
            // This ensures that previous changes are preserved
            CalculationNode currentRootNode = this.rootNodeBuilder.toNode();
            
            // Replace the node at currentPath with the updated node
            CalculationNode newRootNode = replaceNodeAtPath(currentRootNode, currentPath, updatedCurrentNode);
            
            // Update root builder with the new structure
            this.rootNodeBuilder = NodeBuilder.fromNode(newRootNode);
            
            System.out.println("DEBUG: Successfully updated root NodeBuilder");
            
        } catch (Exception e) {
            System.err.println("ERROR: Failed to update root NodeBuilder: " + e.getMessage());
            e.printStackTrace();
        }
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
        // Navigate into the node
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
        CreateAtomicNodeDialog dialog = new CreateAtomicNodeDialog(atomicNodeClass);
        dialog.showAndWait().ifPresent(result -> {
            // TODO: Implement atomic node creation with NodeBuilder
            System.out.println("Creating atomic node: " + atomicNodeClass + " with parameters: " + result);
            
            // Refresh visibility after adding new node
            populateNodeVisibility();
            updateCanvasVisibility();
        });
    }
    
    private void handleCreateNodeGroup() {
        if (selectedNodeNames.size() < 2) return;
        
        // TODO: Implement NodeGroup creation dialog
        TextInputDialog dialog = new TextInputDialog("NewGroup");
        dialog.setTitle("Create NodeGroup");
        dialog.setHeaderText("Create NodeGroup from selected nodes");
        dialog.setContentText("Enter group name:");
        
        dialog.showAndWait().ifPresent(groupName -> {
            // TODO: Implement NodeGroup creation with NodeBuilder
            System.out.println("Creating NodeGroup: " + groupName + " with nodes: " + selectedNodeNames);
            
            // Clear selection and refresh
            selectedNodeNames.clear();
            updateCreateGroupButton();
            populateNodeVisibility();
            updateCanvasVisibility();
        });
    }
    
    private void handleAddFlywire() {
        // TODO: Implement flywire creation dialog
        System.out.println("Add flywire not yet implemented");
    }
    
    private void handleRemoveFlywire(Flywire flywire) {
        if (currentNodeBuilder instanceof NodeGroupBuilder ngb) {
            // TODO: Implement flywire removal
            System.out.println("Remove flywire: " + formatFlywireShort(flywire));
            populateFlywires();
        }
    }
    
    private void handleEditScope() {
        // TODO: Implement scope editing dialog
        System.out.println("Edit scope not yet implemented");
    }
    
    private void navigateToPath(Path newPath) {
        try {
            // Update current path
            this.currentPath = newPath;
            
            // Navigate to the NodeBuilder for this path
            this.currentNodeBuilder = navigateToNodeBuilder(rootNodeBuilder, newPath);
            
            // Update edit canvas model
            editCanvasModel = new EditCanvasModel(currentNodeBuilder, currentPath);
            editCanvas.setEditModel(editCanvasModel);
            
            // Update navigation bar
            navigationBar.setPathSegments(getPathSegments(currentPath));
            
            // Refresh all UI components
            initializeNodeVisibility();
            populateNodeVisibility();
            populateFlywires();
            populateScope();
            
            // Update window title
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
