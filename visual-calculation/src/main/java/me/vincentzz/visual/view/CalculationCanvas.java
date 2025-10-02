package me.vincentzz.visual.view;

import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Duration;
import me.vincentzz.graph.model.EvaluationResult;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.model.input.InputSourceType;
import me.vincentzz.graph.model.output.OutputValueType;
import me.vincentzz.lang.Result.Failure;
import me.vincentzz.lang.Result.Result;
import me.vincentzz.lang.Result.Success;
import me.vincentzz.visual.model.ConnectionViewModel;
import me.vincentzz.visual.model.NodeViewModel;
import me.vincentzz.visual.model.VisualizationModel;
import me.vincentzz.visual.util.ColorScheme;

import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Canvas component for rendering calculation nodes and their connections.
 * Provides an Unreal 5-inspired interface for exploring node graphs.
 */
public class CalculationCanvas extends ScrollPane {
    
    private static final double MIN_CANVAS_WIDTH = 1000;
    private static final double MIN_CANVAS_HEIGHT = 800;
    private static final double NODE_SPACING_X = 250;
    private static final double NODE_SPACING_Y = 180;
    private static final double CONNECTION_POINT_RADIUS = 6;
    private static final double CONNECTION_LINE_WIDTH = 1.0;
    private static final double CANVAS_PADDING = 100;
    private static final double SIDE_PANEL_WIDTH = 200;
    private static final Duration TOOLTIP_DELAY = Duration.millis(500); // 500ms delay before showing tooltip
    private static final Duration CONNECTION_POINT_TOOLTIP_DELAY = Duration.millis(100); // 100ms delay for connection points
    
    // Floating info panel constants
    private static final double INFO_PANEL_WIDTH = 280;
    private static final double INFO_PANEL_MAX_HEIGHT = 200;
    private static final double INFO_PANEL_MARGIN = 20;
    private static final double INFO_PANEL_PADDING = 12;
    private static final double INFO_PANEL_LINE_HEIGHT = 16;
    
    private Canvas canvas;
    private Pane canvasContainer;
    private GraphicsContext gc;
    private Tooltip tooltip;
    private Timeline tooltipDelayTimeline;
    
    // Data
    private List<NodeViewModel> nodes = new ArrayList<>();
    private List<ConnectionViewModel> connections = new ArrayList<>();
    private Path currentPath;
    private VisualizationModel model; // Reference to get correct node information
    private me.vincentzz.visual.model.EditCanvasModel editModel; // For edit mode
    
    // State
    private NodeViewModel hoveredNode;
    private ResourceIdentifier hoveredPathInput;
    private ResourceIdentifier hoveredPathOutput;
    private NodeViewModel hoveredNodeConnectionPointNode; // Node containing the hovered connection point
    private ResourceIdentifier hoveredNodeConnectionPointResource; // Resource for the hovered connection point
    private boolean hoveredNodeConnectionPointIsInput; // Whether the hovered connection point is an input
    private Point2D lastMousePos;
    private NodeViewModel draggedNode;
    private Point2D dragOffset;
    private boolean isDragging = false;
    
    // Flywire dragging state
    private boolean isDraggingFlywire = false;
    private NodeViewModel flywireSourceNode;
    private ResourceIdentifier flywireSourceResource;
    private Point2D flywireStartPoint;
    private Point2D flywireCurrentPoint;
    
    // Selection state
    private Set<NodeViewModel> selectedNodes = new HashSet<>();
    
    // Event handlers
    private Consumer<Path> onNodeClicked;
    private Consumer<Path> onNodeDoubleClicked;
    private BiConsumer<Path, String> onNodeHovered;
    private Consumer<EvaluationResult> onEditCompleted;
    private Consumer<Set<Path>> onMultipleNodesSelected;
    private Runnable onStructuralChange; // New callback for structural changes
    
    /**
     * Helper class to store information about a node connection point.
     */
    private static class NodeConnectionPointInfo {
        final NodeViewModel node;
        final ResourceIdentifier resource;
        final boolean isInput;
        
        NodeConnectionPointInfo(NodeViewModel node, ResourceIdentifier resource, boolean isInput) {
            this.node = node;
            this.resource = resource;
            this.isInput = isInput;
        }
    }
    
    public CalculationCanvas() {
        initializeComponent();
        setupEventHandlers();
    }
    
    private void initializeComponent() {
        // Create canvas with initial size (will be resized dynamically)
        canvas = new Canvas(MIN_CANVAS_WIDTH, MIN_CANVAS_HEIGHT);
        gc = canvas.getGraphicsContext2D();
        
        // Create container for canvas
        canvasContainer = new Pane(canvas);
        canvasContainer.setStyle("-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_DARK) + ";");
        
        // Setup scroll pane
        setContent(canvasContainer);
        setFitToWidth(true);
        setFitToHeight(true);
        setPannable(true);
        
        // Listen to scroll pane size changes to update canvas
        viewportBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            updateCanvasSize();
        });
        
        // Create tooltip
        tooltip = new Tooltip();
        tooltip.setStyle(
            "-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_MEDIUM) + ";" +
            "-fx-text-fill: " + toHexString(ColorScheme.TEXT_PRIMARY) + ";" +
            "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";" +
            "-fx-font-size: 12px;"
        );
        
        // Initial render
        render();
    }
    
    private void setupEventHandlers() {
        canvas.setOnMouseMoved(this::handleMouseMove);
        canvas.setOnMouseClicked(this::handleMouseClick);
        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        canvas.setOnMouseReleased(this::handleMouseReleased);
        canvas.setOnMouseExited(this::handleMouseExit);
    }
    
    private void handleMouseMove(MouseEvent event) {
        lastMousePos = new Point2D(event.getX(), event.getY());
        
        // Clear all previous hover states first
        clearAllHoverStates();
        
        // Check for hover targets in priority order
        NodeConnectionPointInfo nodeConnectionPoint = findNodeConnectionPointAt(event.getX(), event.getY());
        NodeViewModel nodeAtMouse = findNodeAt(event.getX(), event.getY());
        ResourceIdentifier pathInputAtMouse = findPathInputAt(event.getX(), event.getY());
        ResourceIdentifier pathOutputAtMouse = findPathOutputAt(event.getX(), event.getY());
        
        // Apply hover state based on priority (connection points > nodes > path points)
        if (nodeConnectionPoint != null) {
            // Highest priority: individual node connection points
            setNodeConnectionPointHover(nodeConnectionPoint, event.getX(), event.getY());
        } else if (nodeAtMouse != null) {
            // Medium priority: general node hover
            setNodeHover(nodeAtMouse, event.getX(), event.getY());
        } else if (pathInputAtMouse != null) {
            // Lower priority: path input connection points
            setPathInputHover(pathInputAtMouse, event.getX(), event.getY());
        } else if (pathOutputAtMouse != null) {
            // Lower priority: path output connection points  
            setPathOutputHover(pathOutputAtMouse, event.getX(), event.getY());
        } else {
            // No hover target found
            hideTooltip();
        }
        
        // Update cursor
        boolean hasHover = (hoveredNodeConnectionPointNode != null || hoveredNode != null || 
                           hoveredPathInput != null || hoveredPathOutput != null);
        setCursor(hasHover ? Cursor.HAND : Cursor.DEFAULT);
        
        // Re-render if there's any hover state
        if (hasHover) {
            render();
        }
    }
    
    private void clearAllHoverStates() {
        if (hoveredNode != null) {
            hoveredNode.setHovered(false);
            hoveredNode = null;
        }
        hoveredNodeConnectionPointNode = null;
        hoveredNodeConnectionPointResource = null;
        hoveredNodeConnectionPointIsInput = false;
        hoveredPathInput = null;
        hoveredPathOutput = null;
    }
    
    private void setNodeConnectionPointHover(NodeConnectionPointInfo connectionPoint, double x, double y) {
        hoveredNodeConnectionPointNode = connectionPoint.node;
        hoveredNodeConnectionPointResource = connectionPoint.resource;
        hoveredNodeConnectionPointIsInput = connectionPoint.isInput;
        
        showNodeConnectionPointTooltip(
            hoveredNodeConnectionPointNode,
            hoveredNodeConnectionPointResource,
            hoveredNodeConnectionPointIsInput,
            x, y
        );
    }
    
    private void setNodeHover(NodeViewModel node, double x, double y) {
        hoveredNode = node;
        hoveredNode.setHovered(true);
        
        showNodeTooltip(hoveredNode, x, y);
        
        if (onNodeHovered != null) {
            onNodeHovered.accept(hoveredNode.getNodePath(), createNodeHoverText(hoveredNode));
        }
    }
    
    private void setPathInputHover(ResourceIdentifier pathInput, double x, double y) {
        hoveredPathInput = pathInput;
        showPathResourceTooltip(hoveredPathInput, x, y, true);
    }
    
    private void setPathOutputHover(ResourceIdentifier pathOutput, double x, double y) {
        hoveredPathOutput = pathOutput;
        showPathResourceTooltip(hoveredPathOutput, x, y, false);
    }
    
    private void handleMouseClick(MouseEvent event) {
        if (event.getClickCount() == 1) {
            // Check if click is on floating control panel first (only in edit mode)
            if (editModel != null && isClickOnControlPanel(event.getX(), event.getY())) {
                handleControlPanelClick(event.getX(), event.getY());
                return;
            }
            
            // Single click
            NodeViewModel clickedNode = findNodeAt(event.getX(), event.getY());
            if (clickedNode != null && onNodeClicked != null) {
                // Only handle selection in edit mode
                if (editModel != null) {
                    // Edit mode: Handle selection based on Ctrl key
                    if (event.isControlDown()) {
                        // Ctrl+click: toggle selection of this node
                        handleCtrlNodeClick(clickedNode);
                    } else {
                        // Regular click: clear all selections and select only this node
                        handleRegularNodeClick(clickedNode);
                    }
                    
                    // Re-render to show selection changes
                    render();
                }
                
                onNodeClicked.accept(clickedNode.getNodePath());
            } else {
                // Click on empty area - clear all selections (only in edit mode)
                if (editModel != null && !event.isControlDown()) {
                    clearAllNodeSelections();
                    render();
                }
            }
        } else if (event.getClickCount() == 2) {
            // Double click
            NodeViewModel clickedNode = findNodeAt(event.getX(), event.getY());
            if (clickedNode != null && onNodeDoubleClicked != null) {
                onNodeDoubleClicked.accept(clickedNode.getNodePath());
            }
        }
    }
    
    /**
     * Check if a click is within the edit buttons area.
     */
    private boolean isClickOnControlPanel(double x, double y) {
        if (editModel == null) {
            return false;
        }
        
        // Get the visible viewport bounds
        double viewportWidth = getViewportBounds().getWidth();
        double viewportHeight = getViewportBounds().getHeight();
        
        // Get current scroll position
        double scrollX = getHvalue() * Math.max(0, canvas.getWidth() - viewportWidth);
        double scrollY = getVvalue() * Math.max(0, canvas.getHeight() - viewportHeight);
        
        // Button dimensions
        double buttonWidth = 80;
        double buttonHeight = 30;
        double buttonSpacing = 10;
        double margin = 20;
        
        // Position buttons horizontally at top right (now 3 buttons)
        double startX = scrollX + viewportWidth - (buttonWidth * 3 + buttonSpacing * 2) - margin;
        double buttonY = scrollY + margin;
        
        // Check if click is within any button area
        double groupButtonX = startX;
        double ungroupButtonX = startX + buttonWidth + buttonSpacing;
        double resetButtonX = startX + (buttonWidth + buttonSpacing) * 2;
        
        // Group button area
        if (x >= groupButtonX && x <= groupButtonX + buttonWidth && 
            y >= buttonY && y <= buttonY + buttonHeight) {
            return true;
        }
        
        // Ungroup button area
        if (x >= ungroupButtonX && x <= ungroupButtonX + buttonWidth && 
            y >= buttonY && y <= buttonY + buttonHeight) {
            return true;
        }
        
        // Reset button area
        if (x >= resetButtonX && x <= resetButtonX + buttonWidth && 
            y >= buttonY && y <= buttonY + buttonHeight) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Handle clicks on the edit buttons.
     */
    private void handleControlPanelClick(double x, double y) {
        // Get button positions
        double viewportWidth = getViewportBounds().getWidth();
        double viewportHeight = getViewportBounds().getHeight();
        double scrollX = getHvalue() * Math.max(0, canvas.getWidth() - viewportWidth);
        double scrollY = getVvalue() * Math.max(0, canvas.getHeight() - viewportHeight);
        
        // Button dimensions
        double buttonWidth = 80;
        double buttonHeight = 30;
        double buttonSpacing = 10;
        double margin = 20;
        
        // Position buttons horizontally at top right (now 3 buttons)
        double startX = scrollX + viewportWidth - (buttonWidth * 3 + buttonSpacing * 2) - margin;
        double buttonY = scrollY + margin;
        
        double groupButtonX = startX;
        double ungroupButtonX = startX + buttonWidth + buttonSpacing;
        double resetButtonX = startX + (buttonWidth + buttonSpacing) * 2;
        
        // Get selection info
        List<NodeViewModel> selectedNodes = nodes.stream().filter(NodeViewModel::isSelected).collect(java.util.stream.Collectors.toList());
        int selectedCount = selectedNodes.size();
        boolean canGroup = selectedCount >= 1;
        boolean canUngroup = selectedCount == 1 && selectedNodes.get(0).isNodeGroup();
        
        // Check Group button click
        if (x >= groupButtonX && x <= groupButtonX + buttonWidth && 
            y >= buttonY && y <= buttonY + buttonHeight) {
            if (canGroup) {
                System.out.println("DEBUG: Group button clicked with " + selectedCount + " selected nodes");
                handleGroupNodes(selectedNodes);
            } else {
                System.out.println("DEBUG: Group button clicked but cannot group (selected: " + selectedCount + ")");
            }
            return;
        }
        
        // Check Ungroup button click
        if (x >= ungroupButtonX && x <= ungroupButtonX + buttonWidth && 
            y >= buttonY && y <= buttonY + buttonHeight) {
            if (canUngroup) {
                System.out.println("DEBUG: Ungroup button clicked");
                handleUngroupNode(selectedNodes.get(0));
            } else {
                System.out.println("DEBUG: Ungroup button clicked but cannot ungroup");
            }
            return;
        }
        
        // Check Reset button click
        if (x >= resetButtonX && x <= resetButtonX + buttonWidth && 
            y >= buttonY && y <= buttonY + buttonHeight) {
            System.out.println("DEBUG: Reset button clicked");
            handleResetChanges();
            return;
        }
    }
    
    /**
     * Handle grouping selected nodes into a new NodeGroup.
     */
    private void handleGroupNodes(List<NodeViewModel> selectedNodes) {
        if (selectedNodes.isEmpty()) {
            return;
        }
        
        // Prompt user for group name
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog("NewGroup");
        dialog.setTitle("Create NodeGroup");
        dialog.setHeaderText("Create NodeGroup from " + selectedNodes.size() + " selected node" + (selectedNodes.size() > 1 ? "s" : ""));
        dialog.setContentText("Enter group name:");
        
        dialog.showAndWait().ifPresent(groupName -> {
            try {
                // Get NodeGroupBuilder for current path
                me.vincentzz.graph.node.builder.NodeGroupBuilder currentGroupBuilder = 
                    (me.vincentzz.graph.node.builder.NodeGroupBuilder) editModel.getCurrentNodeBuilder();
                
                // Get node names of selected nodes
                List<String> selectedNodeNames = selectedNodes.stream()
                    .map(NodeViewModel::getDisplayName)
                    .collect(java.util.stream.Collectors.toList());
                
                System.out.println("DEBUG: Grouping nodes: " + selectedNodeNames + " into group: " + groupName);
                
                // Implement grouping using available NodeGroupBuilder methods
                createNodeGroup(currentGroupBuilder, groupName, selectedNodeNames);
                
                // Clear selections
                clearAllNodeSelections();
                
                // Refresh the canvas to show the new group
                refreshEditModel();
                
                // Notify EditNodeWindow to refresh visibility controls
                if (onStructuralChange != null) {
                    onStructuralChange.run();
                }
                
                System.out.println("DEBUG: Successfully created NodeGroup: " + groupName);
                
            } catch (Exception e) {
                System.err.println("ERROR: Failed to create NodeGroup: " + e.getMessage());
                e.printStackTrace();
                showErrorAlert("Group Creation Failed", "Failed to create NodeGroup: " + e.getMessage());
            }
        });
    }
    
    /**
     * Handle ungrouping a NodeGroup back into individual nodes.
     */
    private void handleUngroupNode(NodeViewModel nodeGroupToUngroup) {
        if (!nodeGroupToUngroup.isNodeGroup()) {
            return;
        }
        
        try {
            // Get NodeGroupBuilder for current path
            me.vincentzz.graph.node.builder.NodeGroupBuilder currentGroupBuilder = 
                (me.vincentzz.graph.node.builder.NodeGroupBuilder) editModel.getCurrentNodeBuilder();
            
            String nodeGroupName = nodeGroupToUngroup.getDisplayName();
            
            System.out.println("DEBUG: Ungrouping NodeGroup: " + nodeGroupName);
            
            // Implement ungrouping using available NodeGroupBuilder methods
            ungroupNode(currentGroupBuilder, nodeGroupName);
            
            // Clear selections
            clearAllNodeSelections();
            
            // Refresh the canvas to show the ungrouped nodes
            refreshEditModel();
            
            // Notify EditNodeWindow to refresh visibility controls
            if (onStructuralChange != null) {
                onStructuralChange.run();
            }
            
            System.out.println("DEBUG: Successfully ungrouped NodeGroup: " + nodeGroupName);
            
        } catch (Exception e) {
            System.err.println("ERROR: Failed to ungroup NodeGroup: " + e.getMessage());
            e.printStackTrace();
            showErrorAlert("Ungroup Failed", "Failed to ungroup NodeGroup: " + e.getMessage());
        }
    }
    
    /**
     * Create a new NodeGroup from selected nodes using correct NodeGroupBuilder API.
     * Fixed to use the actual available methods in NodeGroupBuilder.
     */
    private void createNodeGroup(me.vincentzz.graph.node.builder.NodeGroupBuilder parentBuilder, 
                                String groupName, List<String> nodeNames) {
        System.out.println("DEBUG: Creating NodeGroup '" + groupName + "' from nodes: " + nodeNames);
        
        // Get the current nodes to find the selected ones
        Set<me.vincentzz.graph.node.CalculationNode> allNodes = parentBuilder.nodes();
        Set<me.vincentzz.graph.node.CalculationNode> selectedNodes = new HashSet<>();
        
        // Find the selected nodes
        for (me.vincentzz.graph.node.CalculationNode node : allNodes) {
            if (nodeNames.contains(node.name())) {
                selectedNodes.add(node);
                System.out.println("DEBUG: Found selected node: " + node.name());
            }
        }
        
        if (selectedNodes.isEmpty()) {
            throw new RuntimeException("No nodes found to group");
        }
        
        System.out.println("DEBUG: Selected " + selectedNodes.size() + " nodes for grouping");
        
        // Get existing flywires to analyze connections
        Set<me.vincentzz.graph.node.Flywire> existingFlywires = parentBuilder.flywires();
        Set<me.vincentzz.graph.node.Flywire> internalFlywires = new HashSet<>();
        Set<me.vincentzz.graph.node.Flywire> externalFlywires = new HashSet<>();
        
        System.out.println("DEBUG: Analyzing " + existingFlywires.size() + " existing flywires");
        
        // Categorize flywires as internal (within group) or external (crossing group boundary)
        for (me.vincentzz.graph.node.Flywire flywire : existingFlywires) {
            String sourceName = getNodeNameFromPath(flywire.source().nodePath());
            String targetName = getNodeNameFromPath(flywire.target().nodePath());
            
            boolean sourceInGroup = nodeNames.contains(sourceName);
            boolean targetInGroup = nodeNames.contains(targetName);
            
            if (sourceInGroup && targetInGroup) {
                // Both source and target are in the group - internal flywire
                internalFlywires.add(flywire);
                System.out.println("DEBUG: Internal flywire: " + sourceName + " -> " + targetName);
            } else if (sourceInGroup || targetInGroup) {
                // One end is in the group, one is outside - external flywire (needs special handling)
                externalFlywires.add(flywire);
                System.out.println("DEBUG: External flywire: " + sourceName + " -> " + targetName);
            }
        }
        
        // Create default export scope: empty Exclude scope (excludes nothing = exports everything)
        // This is more intuitive than Include scope as it automatically exports all nested node outputs
        Set<me.vincentzz.graph.node.ConnectionPoint> emptyExcludeSet = new HashSet<>();
        me.vincentzz.graph.scope.Exclude<me.vincentzz.graph.node.ConnectionPoint> excludeScope = 
            new me.vincentzz.graph.scope.Exclude<>(emptyExcludeSet);
        
        System.out.println("DEBUG: Group will use empty Exclude scope (exports all nested node outputs)");
        
        // Create the new NodeGroup
        me.vincentzz.graph.node.NodeGroup newGroup = new me.vincentzz.graph.node.NodeGroup(
            groupName,
            selectedNodes,
            internalFlywires,
            excludeScope
        );
        
        System.out.println("DEBUG: Created NodeGroup with " + selectedNodes.size() + " nodes, " + 
                          internalFlywires.size() + " internal flywires");
        
        // Now update the parent builder using the correct API
        
        // 1. Remove internal flywires first (they're now inside the group)
        for (me.vincentzz.graph.node.Flywire flywire : internalFlywires) {
            parentBuilder.deleteFlywire(flywire);
            System.out.println("DEBUG: Removed internal flywire: " + 
                              getNodeNameFromPath(flywire.source().nodePath()) + " -> " + 
                              getNodeNameFromPath(flywire.target().nodePath()));
        }
        
        // 2. Update external flywires to point to the new group
        for (me.vincentzz.graph.node.Flywire flywire : externalFlywires) {
            String sourceName = getNodeNameFromPath(flywire.source().nodePath());
            String targetName = getNodeNameFromPath(flywire.target().nodePath());
            
            // Remove the old flywire
            parentBuilder.deleteFlywire(flywire);
            
            // Create new flywire pointing to/from the group
            me.vincentzz.graph.node.Flywire newFlywire;
            if (nodeNames.contains(sourceName)) {
                // Source is in the group, target is outside - create flywire from group to target
                newFlywire = new me.vincentzz.graph.node.Flywire(
                    new me.vincentzz.graph.node.ConnectionPoint(
                        java.nio.file.Paths.get(groupName), // Point to the group
                        flywire.source().rid()
                    ),
                    flywire.target() // Keep original target
                );
            } else {
                // Source is outside, target is in the group - create flywire from source to group
                newFlywire = new me.vincentzz.graph.node.Flywire(
                    flywire.source(), // Keep original source
                    new me.vincentzz.graph.node.ConnectionPoint(
                        java.nio.file.Paths.get(groupName), // Point to the group
                        flywire.target().rid()
                    )
                );
            }
            
            parentBuilder.addFlywire(newFlywire);
            System.out.println("DEBUG: Updated external flywire to use group: " + 
                              getNodeNameFromPath(newFlywire.source().nodePath()) + " -> " + 
                              getNodeNameFromPath(newFlywire.target().nodePath()));
        }
        
        // 3. Remove the selected nodes from the parent builder (AFTER updating flywires)
        // Use individual deleteNode calls since deleteNodes() doesn't exist
        for (String nodeName : nodeNames) {
            parentBuilder.deleteNode(nodeName);
            System.out.println("DEBUG: Removed node: " + nodeName);
        }
        
        // 4. Add the new group to the parent builder
        parentBuilder.addNode(newGroup);
        System.out.println("DEBUG: Added new NodeGroup '" + groupName + "' to parent");
        
        System.out.println("DEBUG: Successfully created NodeGroup '" + groupName + "' with proper connections");
    }
    
    /**
     * Extract node name from a path, handling both simple names and complex paths.
     */
    private String getNodeNameFromPath(java.nio.file.Path nodePath) {
        if (nodePath.getFileName() != null) {
            return nodePath.getFileName().toString();
        } else {
            String pathStr = nodePath.toString();
            if (pathStr.startsWith("/")) {
                pathStr = pathStr.substring(1);
            }
            return pathStr;
        }
    }
    
    /**
     * Ungroup a NodeGroup back into individual nodes using correct NodeGroupBuilder API.
     */
    private void ungroupNode(me.vincentzz.graph.node.builder.NodeGroupBuilder parentBuilder, 
                            String nodeGroupName) {
        System.out.println("DEBUG: Ungrouping NodeGroup: " + nodeGroupName);
        
        // Get the current nodes to find the NodeGroup to ungroup
        Set<me.vincentzz.graph.node.CalculationNode> allNodes = parentBuilder.nodes();
        me.vincentzz.graph.node.NodeGroup nodeGroupToUngroup = null;
        
        // Find the NodeGroup to ungroup
        for (me.vincentzz.graph.node.CalculationNode node : allNodes) {
            if (node.name().equals(nodeGroupName) && node instanceof me.vincentzz.graph.node.NodeGroup) {
                nodeGroupToUngroup = (me.vincentzz.graph.node.NodeGroup) node;
                break;
            }
        }
        
        if (nodeGroupToUngroup == null) {
            throw new RuntimeException("NodeGroup '" + nodeGroupName + "' not found");
        }
        
        System.out.println("DEBUG: Found NodeGroup to ungroup: " + nodeGroupName);
        
        // Get the child nodes and flywires from the NodeGroup
        Set<me.vincentzz.graph.node.CalculationNode> childNodes = nodeGroupToUngroup.nodes();
        Set<me.vincentzz.graph.node.Flywire> childFlywires = nodeGroupToUngroup.flywires();
        
        System.out.println("DEBUG: NodeGroup contains " + childNodes.size() + " nodes and " + 
                          childFlywires.size() + " flywires");
        
        // Remove the NodeGroup from the parent builder first
        parentBuilder.deleteNode(nodeGroupName);
        System.out.println("DEBUG: Removed NodeGroup from parent");
        
        // Add the child nodes to the parent builder using addNodes method
        parentBuilder.addNodes(childNodes);
        System.out.println("DEBUG: Added " + childNodes.size() + " child nodes to parent");
        
        // Add the child flywires to the parent builder using addFlywires method
        parentBuilder.addFlywires(childFlywires);
        System.out.println("DEBUG: Added " + childFlywires.size() + " child flywires to parent");
        
        System.out.println("DEBUG: Successfully ungrouped NodeGroup: " + nodeGroupName);
    }
    
    /**
     * Handle resetting all changes made to the graph back to the original state.
     */
    private void handleResetChanges() {
        try {
            // Show confirmation dialog
            javafx.scene.control.Alert confirmDialog = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
            confirmDialog.setTitle("Reset Changes");
            confirmDialog.setHeaderText("Reset all changes to original state?");
            confirmDialog.setContentText("This will discard all modifications made to the graph structure. This action cannot be undone.");
            
            confirmDialog.showAndWait().ifPresent(response -> {
                if (response == javafx.scene.control.ButtonType.OK) {
                    try {
                        // Reset the NodeBuilder to the original state
                        // We need to recreate the NodeBuilder from the original graph
                        if (editModel != null) {
                            // Get the original graph from the EditNodeWindow
                            // This requires coordination with EditNodeWindow to provide the original graph
                            resetToOriginalState();
                            
                            System.out.println("DEBUG: Successfully reset all changes to original state");
                        }
                    } catch (Exception e) {
                        System.err.println("ERROR: Failed to reset changes: " + e.getMessage());
                        e.printStackTrace();
                        showErrorAlert("Reset Failed", "Failed to reset changes: " + e.getMessage());
                    }
                }
            });
            
        } catch (Exception e) {
            System.err.println("ERROR: Failed to show reset confirmation: " + e.getMessage());
            e.printStackTrace();
            showErrorAlert("Reset Error", "Failed to show reset confirmation: " + e.getMessage());
        }
    }
    
    /**
     * Reset the edit model to the original state.
     * This method needs to be coordinated with EditNodeWindow to access the original graph.
     */
    private void resetToOriginalState() {
        // Find the EditNodeWindow that contains this canvas and call its reset method
        javafx.stage.Window window = getScene().getWindow();
        if (window instanceof me.vincentzz.visual.view.EditNodeWindow editWindow) {
            // Clear all selections first
            clearAllNodeSelections();
            
            // Call the EditNodeWindow's reset method to reload original parameters
            editWindow.resetToOriginalState();
            
            showInfoAlert("Reset Complete", "All changes have been reset to the original state.");
        } else {
            // Fallback: use structural change event
            if (onStructuralChange != null) {
                clearAllNodeSelections();
                onStructuralChange.run();
                showInfoAlert("Reset Complete", "All changes have been reset to the original state.");
            } else {
                throw new RuntimeException("Cannot reset: no EditNodeWindow or structural change handler available");
            }
        }
    }
    
    /**
     * Handle editing the export scope of the current NodeGroup.
     */
    private void handleEditScope() {
        // TODO: Implement scope editing dialog
        System.out.println("DEBUG: Edit scope functionality not yet implemented");
        showInfoAlert("Edit Scope", "Scope editing functionality will be implemented in a future update.");
    }
    
    /**
     * Refresh the edit model to reload nodes and connections after structural changes.
     */
    private void refreshEditModel() {
        if (editModel != null) {
            try {
                // Update the edit model with the modified NodeBuilder
                editModel.updateNodeBuilder(editModel.getCurrentNodeBuilder());
                
                // Update our nodes and connections from the refreshed model
                updateNodesFromEditModel();
                this.connections = new ArrayList<>(editModel.getConnections());
                
                // Re-render
                render();
                
            } catch (Exception e) {
                System.err.println("ERROR: Failed to refresh edit model: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Show an error alert dialog.
     */
    private void showErrorAlert(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Show an info alert dialog.
     */
    private void showInfoAlert(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Handle Ctrl+click on a node - toggle its selection without affecting others.
     */
    private void handleCtrlNodeClick(NodeViewModel clickedNode) {
        boolean wasSelected = clickedNode.isSelected();
        clickedNode.setSelected(!wasSelected);
        System.out.println("DEBUG: Ctrl+clicked node " + clickedNode.getDisplayName() + 
                          ", was selected: " + wasSelected + ", now selected: " + clickedNode.isSelected());
    }
    
    /**
     * Handle regular click on a node - clear all selections and select only this node.
     */
    private void handleRegularNodeClick(NodeViewModel clickedNode) {
        // Clear all selections first
        clearAllNodeSelections();
        // Select only the clicked node
        clickedNode.setSelected(true);
        System.out.println("DEBUG: Regular clicked node " + clickedNode.getDisplayName() + 
                          ", now selected: " + clickedNode.isSelected());
    }
    
    /**
     * Clear all node selections.
     */
    private void clearAllNodeSelections() {
        for (NodeViewModel node : nodes) {
            node.setSelected(false);
        }
    }
    
    private void handleMousePressed(MouseEvent event) {
        NodeViewModel nodeAtMouse = findNodeAt(event.getX(), event.getY());
        if (nodeAtMouse != null) {
            draggedNode = nodeAtMouse;
            dragOffset = new Point2D(event.getX() - nodeAtMouse.getX(), event.getY() - nodeAtMouse.getY());
            isDragging = false; // Will be set to true when actual dragging starts
            setPannable(false); // Disable scroll pane panning while dragging node
        }
    }
    
    private void handleMouseDragged(MouseEvent event) {
        if (draggedNode != null) {
            isDragging = true;
            
            // Update node position
            double newX = event.getX() - dragOffset.getX();
            double newY = event.getY() - dragOffset.getY();
            draggedNode.setX(newX);
            draggedNode.setY(newY);
            
            // Update connections that involve this node
            updateConnectionCoordinates();
            
            // Re-render
            render();
        }
    }
    
    private void handleMouseReleased(MouseEvent event) {
        if (draggedNode != null) {
            draggedNode = null;
            dragOffset = null;
            isDragging = false;
            setPannable(true); // Re-enable scroll pane panning
        }
    }
    
    private void handleMouseExit(MouseEvent event) {
        if (hoveredNode != null) {
            hoveredNode.setHovered(false);
            hoveredNode = null;
        }
        hideTooltip();
        setCursor(Cursor.DEFAULT);
        render();
    }
    
    private NodeViewModel findNodeAt(double x, double y) {
        for (NodeViewModel node : nodes) {
            if (node.contains(x, y)) {
                return node;
            }
        }
        return null;
    }
    
    private void showNodeTooltip(NodeViewModel node, double x, double y) {
        String tooltipText = createNodeTooltipText(node);
        showTooltipWithDelay(tooltipText, x, y);
    }
    
    private void hideTooltip() {
        cancelTooltipDelay();
        Platform.runLater(() -> tooltip.hide());
    }
    
    /**
     * Show tooltip with delay to prevent flickering.
     */
    private void showTooltipWithDelay(String tooltipText, double x, double y) {
        showTooltipWithDelay(tooltipText, x, y, TOOLTIP_DELAY);
    }
    
    /**
     * Show tooltip with custom delay to prevent flickering.
     */
    private void showTooltipWithDelay(String tooltipText, double x, double y, Duration delay) {
        cancelTooltipDelay();
        
        tooltipDelayTimeline = new Timeline(new KeyFrame(delay, e -> {
            tooltip.setText(tooltipText);
            Platform.runLater(() -> {
                tooltip.show(canvas, x + canvas.localToScreen(canvas.getBoundsInLocal()).getMinX() + 10,
                                   y + canvas.localToScreen(canvas.getBoundsInLocal()).getMinY() - 10);
            });
        }));
        
        tooltipDelayTimeline.play();
    }
    
    /**
     * Cancel any pending tooltip delay timer.
     */
    private void cancelTooltipDelay() {
        if (tooltipDelayTimeline != null) {
            tooltipDelayTimeline.stop();
            tooltipDelayTimeline = null;
        }
    }
    
    private String createNodeHoverText(NodeViewModel node) {
        return String.format("%s (%s)", 
                           node.getDisplayName(), 
                           node.isNodeGroup() ? "NodeGroup" : "AtomicNode");
    }
    
    private String createNodeTooltipText(NodeViewModel node) {
        StringBuilder sb = new StringBuilder();
        sb.append("Node: ").append(node.getDisplayName()).append("\n");
        sb.append("Type: ").append(node.isNodeGroup() ? "NodeGroup" : "AtomicNode").append("\n");
        sb.append("Path: ").append(node.getNodePath()).append("\n");
        
        return sb.toString();
    }
    
    private String getResourceName(ResourceIdentifier resourceId) {
        // This should be customized based on the actual ResourceIdentifier implementation
        return resourceId.toString();
    }
    
    /**
     * Set the nodes to display on the canvas with state preservation.
     */
    public void setNodes(List<NodeViewModel> newNodes) {
        // Preserve state from existing nodes before replacing
        Map<String, NodeViewModel> existingNodeMap = new HashMap<>();
        for (NodeViewModel existingNode : this.nodes) {
            existingNodeMap.put(existingNode.getDisplayName(), existingNode);
        }
        
        // Track nodes that need positioning
        List<NodeViewModel> nodesNeedingPositions = new ArrayList<>();
        
        // Apply state preservation to new nodes
        for (NodeViewModel newNode : newNodes) {
            NodeViewModel existingNode = existingNodeMap.get(newNode.getDisplayName());
            if (existingNode != null) {
                // Preserve selection state and position from existing node
                newNode.setSelected(existingNode.isSelected());
                newNode.setX(existingNode.getX());
                newNode.setY(existingNode.getY());
                newNode.setHovered(existingNode.isHovered());
            } else {
                // This node needs a position (newly visible or truly new)
                nodesNeedingPositions.add(newNode);
            }
        }
        
        this.nodes = new ArrayList<>(newNodes);
        
        // Assign intelligent positions to nodes that need them
        if (!nodesNeedingPositions.isEmpty()) {
            assignIntelligentPositions(nodesNeedingPositions);
        }
        
        render();
    }
    
    /**
     * Assign intelligent positions to nodes that don't have existing positions.
     * Places them in available space without overlapping existing positioned nodes.
     */
    private void assignIntelligentPositions(List<NodeViewModel> nodesNeedingPositions) {
        // Get existing positioned nodes to avoid overlapping
        List<NodeViewModel> positionedNodes = nodes.stream()
            .filter(node -> !nodesNeedingPositions.contains(node))
            .collect(java.util.stream.Collectors.toList());
        
        // Start positioning from a good base location
        double startX = SIDE_PANEL_WIDTH + 100;
        double startY = 100;
        double currentX = startX;
        double currentY = startY;
        
        for (NodeViewModel node : nodesNeedingPositions) {
            // Find next available position that doesn't overlap
            Point2D availablePosition = findAvailablePosition(currentX, currentY, positionedNodes);
            
            node.setX(availablePosition.getX());
            node.setY(availablePosition.getY());
            
            // Add this node to positioned nodes for next iteration
            positionedNodes.add(node);
            
            // Move to next position for the next node
            currentX = availablePosition.getX() + NODE_SPACING_X;
            
            // Wrap to next row if we're getting too wide
            if (currentX > canvas.getWidth() - SIDE_PANEL_WIDTH - 200) {
                currentX = startX;
                currentY += NODE_SPACING_Y;
            }
        }
    }
    
    /**
     * Find an available position that doesn't overlap with existing nodes.
     */
    private Point2D findAvailablePosition(double preferredX, double preferredY, List<NodeViewModel> existingNodes) {
        double testX = preferredX;
        double testY = preferredY;
        
        // Check if preferred position is available
        while (isPositionOccupied(testX, testY, existingNodes)) {
            // Try next position to the right
            testX += NODE_SPACING_X;
            
            // If we've gone too far right, wrap to next row
            if (testX > canvas.getWidth() - SIDE_PANEL_WIDTH - 200) {
                testX = SIDE_PANEL_WIDTH + 100;
                testY += NODE_SPACING_Y;
            }
            
            // Safety check to avoid infinite loop
            if (testY > canvas.getHeight() - 200) {
                // Just place it in the preferred position if we can't find space
                return new Point2D(preferredX, preferredY);
            }
        }
        
        return new Point2D(testX, testY);
    }
    
    /**
     * Check if a position is occupied by an existing node (with some buffer space).
     */
    private boolean isPositionOccupied(double x, double y, List<NodeViewModel> existingNodes) {
        double bufferSpace = 50; // Minimum space between nodes
        
        for (NodeViewModel existingNode : existingNodes) {
            double dx = Math.abs(x - existingNode.getX());
            double dy = Math.abs(y - existingNode.getY());
            
            // Check if too close to existing node
            if (dx < NODE_SPACING_X - bufferSpace && dy < NODE_SPACING_Y - bufferSpace) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Set the connections to display on the canvas.
     */
    public void setConnections(List<ConnectionViewModel> connections) {
        this.connections = new ArrayList<>(connections);
        updateConnectionCoordinates();
        render();
    }
    
    /**
     * Refresh the canvas rendering.
     */
    public void refresh() {
        updateConnectionCoordinates();
        render();
    }
    
    private void layoutNodes() {
        if (nodes.isEmpty()) {
            return;
        }
        
        // Use intelligent layout algorithm that considers connections and hierarchical structure
        intelligentLayoutNodes();
    }
    
    /**
     * Intelligent node layout algorithm that considers connections and hierarchy to minimize line crossings.
     */
    private void intelligentLayoutNodes() {
        // Step 1: Analyze node hierarchy and connectivity
        Map<NodeViewModel, Integer> inputCounts = new HashMap<>();
        Map<NodeViewModel, Integer> outputCounts = new HashMap<>();
        Map<NodeViewModel, Set<NodeViewModel>> nodeConnections = new HashMap<>();
        
        // Initialize counts and connections
        for (NodeViewModel node : nodes) {
            inputCounts.put(node, node.getInputs().size());
            outputCounts.put(node, node.getOutputs().size());
            nodeConnections.put(node, new HashSet<>());
        }
        
        // Build connection graph
        for (ConnectionViewModel connection : connections) {
            NodeViewModel sourceNode = findNodeByPath(connection.getSourcePath());
            NodeViewModel targetNode = findNodeByPath(connection.getTargetPath());
            
            if (sourceNode != null && targetNode != null) {
                nodeConnections.get(sourceNode).add(targetNode);
                nodeConnections.get(targetNode).add(sourceNode);
            }
        }
        
        // Step 2: Create hierarchical layers based on input/output patterns
        List<List<NodeViewModel>> layers = createHierarchicalLayers(inputCounts, outputCounts, nodeConnections);
        
        // Step 3: Position nodes within each layer to minimize crossings
        positionNodesInLayers(layers, nodeConnections);
    }
    
    /**
     * Create hierarchical layers based on node input/output patterns.
     */
    private List<List<NodeViewModel>> createHierarchicalLayers(
            Map<NodeViewModel, Integer> inputCounts,
            Map<NodeViewModel, Integer> outputCounts,
            Map<NodeViewModel, Set<NodeViewModel>> nodeConnections) {
        
        List<List<NodeViewModel>> layers = new ArrayList<>();
        Set<NodeViewModel> processedNodes = new HashSet<>();
        List<NodeViewModel> remainingNodes = new ArrayList<>(nodes);
        
        // Layer 0: Source nodes (nodes with no inputs or mostly outputs)
        List<NodeViewModel> sourceNodes = remainingNodes.stream()
                .filter(node -> inputCounts.get(node) == 0 || 
                               (outputCounts.get(node) > inputCounts.get(node) && inputCounts.get(node) <= 1))
                .collect(Collectors.toList());
        
        if (!sourceNodes.isEmpty()) {
            layers.add(new ArrayList<>(sourceNodes));
            processedNodes.addAll(sourceNodes);
            remainingNodes.removeAll(sourceNodes);
        }
        
        // Create intermediate layers based on dependency depth
        while (!remainingNodes.isEmpty()) {
            List<NodeViewModel> currentLayer = new ArrayList<>();
            
            // Find nodes whose dependencies are mostly satisfied by previous layers
            for (NodeViewModel node : new ArrayList<>(remainingNodes)) {
                int dependenciesSatisfied = 0;
                int totalDependencies = 0;
                
                // Count how many of this node's input dependencies are already positioned
                for (ConnectionViewModel connection : connections) {
                    NodeViewModel sourceNode = findNodeByPath(connection.getSourcePath());
                    NodeViewModel targetNode = findNodeByPath(connection.getTargetPath());
                    
                    if (targetNode == node && sourceNode != null) {
                        totalDependencies++;
                        if (processedNodes.contains(sourceNode)) {
                            dependenciesSatisfied++;
                        }
                    }
                }
                
                // Add to current layer if most dependencies are satisfied or no dependencies
                if (totalDependencies == 0 || dependenciesSatisfied >= totalDependencies * 0.6) {
                    currentLayer.add(node);
                }
            }
            
            // If no nodes were added, take nodes with fewest remaining dependencies
            if (currentLayer.isEmpty() && !remainingNodes.isEmpty()) {
                NodeViewModel nodeWithFewestDeps = remainingNodes.stream()
                        .min(Comparator.comparingInt(inputCounts::get))
                        .orElse(remainingNodes.get(0));
                currentLayer.add(nodeWithFewestDeps);
            }
            
            if (!currentLayer.isEmpty()) {
                layers.add(currentLayer);
                processedNodes.addAll(currentLayer);
                remainingNodes.removeAll(currentLayer);
            } else {
                // Fallback: add remaining nodes to avoid infinite loop
                layers.add(new ArrayList<>(remainingNodes));
                break;
            }
        }
        
        return layers;
    }
    
    /**
     * Position nodes within each layer to minimize connection crossings.
     */
    private void positionNodesInLayers(List<List<NodeViewModel>> layers, Map<NodeViewModel, Set<NodeViewModel>> nodeConnections) {
        double startX = SIDE_PANEL_WIDTH + 100;
        double startY = 100;
        double layerSpacingX = NODE_SPACING_X * 1.2;
        double maxLayerWidth = layers.stream()
                .mapToInt(List::size)
                .max()
                .orElse(1);
        
        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            List<NodeViewModel> layer = layers.get(layerIndex);
            
            // Sort nodes within layer to minimize crossings with previous layer
            if (layerIndex > 0) {
                List<NodeViewModel> previousLayer = layers.get(layerIndex - 1);
                sortLayerToMinimizeCrossings(layer, previousLayer, nodeConnections);
            }
            
            // Position nodes vertically within the layer
            double layerX = startX + layerIndex * layerSpacingX;
            double totalLayerHeight = layer.size() * NODE_SPACING_Y;
            double layerStartY = startY + (maxLayerWidth * NODE_SPACING_Y - totalLayerHeight) / 2;
            
            for (int nodeIndex = 0; nodeIndex < layer.size(); nodeIndex++) {
                NodeViewModel node = layer.get(nodeIndex);
                double nodeY = layerStartY + nodeIndex * NODE_SPACING_Y;
                
                node.setX(layerX);
                node.setY(nodeY);
            }
        }
        
        // Apply force-directed refinement to reduce crossings further
        applyForceDirectedRefinement(nodeConnections);
    }
    
    /**
     * Sort nodes within a layer to minimize crossings with the previous layer.
     */
    private void sortLayerToMinimizeCrossings(List<NodeViewModel> currentLayer, 
                                            List<NodeViewModel> previousLayer,
                                            Map<NodeViewModel, Set<NodeViewModel>> nodeConnections) {
        
        // Calculate connection weight for each node (average Y position of connected nodes in previous layer)
        Map<NodeViewModel, Double> connectionWeights = new HashMap<>();
        
        for (NodeViewModel node : currentLayer) {
            double totalWeight = 0;
            int connectionCount = 0;
            
            for (NodeViewModel prevNode : previousLayer) {
                if (nodeConnections.get(node).contains(prevNode) || nodeConnections.get(prevNode).contains(node)) {
                    totalWeight += prevNode.getY();
                    connectionCount++;
                }
            }
            
            // Use current position as fallback if no connections
            double averageWeight = connectionCount > 0 ? totalWeight / connectionCount : node.getY();
            connectionWeights.put(node, averageWeight);
        }
        
        // Sort by connection weight
        currentLayer.sort(Comparator.comparingDouble(connectionWeights::get));
    }
    
    /**
     * Apply force-directed refinement to further optimize node positions.
     */
    private void applyForceDirectedRefinement(Map<NodeViewModel, Set<NodeViewModel>> nodeConnections) {
        int iterations = 50;
        double temperature = 50.0;
        double cooling = 0.95;
        
        for (int i = 0; i < iterations; i++) {
            Map<NodeViewModel, Point2D> forces = new HashMap<>();
            
            // Initialize forces
            for (NodeViewModel node : nodes) {
                forces.put(node, new Point2D(0, 0));
            }
            
            // Calculate attraction forces between connected nodes
            for (NodeViewModel node1 : nodes) {
                for (NodeViewModel node2 : nodeConnections.get(node1)) {
                    if (nodes.contains(node2)) {
                        double dx = node2.getX() - node1.getX();
                        double dy = node2.getY() - node1.getY();
                        double distance = Math.sqrt(dx * dx + dy * dy);
                        
                        if (distance > 0) {
                            // Attraction force proportional to distance
                            double attractionStrength = Math.min(distance / NODE_SPACING_X, 1.0) * 0.5;
                            double fx = (dx / distance) * attractionStrength;
                            double fy = (dy / distance) * attractionStrength;
                            
                            Point2D currentForce1 = forces.get(node1);
                            Point2D currentForce2 = forces.get(node2);
                            
                            forces.put(node1, new Point2D(currentForce1.getX() + fx, currentForce1.getY() + fy));
                            forces.put(node2, new Point2D(currentForce2.getX() - fx, currentForce2.getY() - fy));
                        }
                    }
                }
            }
            
            // Calculate repulsion forces between all nodes
            for (int j = 0; j < nodes.size(); j++) {
                for (int k = j + 1; k < nodes.size(); k++) {
                    NodeViewModel node1 = nodes.get(j);
                    NodeViewModel node2 = nodes.get(k);
                    
                    double dx = node2.getX() - node1.getX();
                    double dy = node2.getY() - node1.getY();
                    double distance = Math.sqrt(dx * dx + dy * dy);
                    
                    if (distance > 0 && distance < NODE_SPACING_X * 2) {
                        // Repulsion force inversely proportional to distance
                        double repulsionStrength = (NODE_SPACING_X * 2 - distance) / (NODE_SPACING_X * 2) * 0.3;
                        double fx = (dx / distance) * repulsionStrength;
                        double fy = (dy / distance) * repulsionStrength;
                        
                        Point2D currentForce1 = forces.get(node1);
                        Point2D currentForce2 = forces.get(node2);
                        
                        forces.put(node1, new Point2D(currentForce1.getX() - fx, currentForce1.getY() - fy));
                        forces.put(node2, new Point2D(currentForce2.getX() + fx, currentForce2.getY() + fy));
                    }
                }
            }
            
            // Apply forces with temperature cooling
            for (NodeViewModel node : nodes) {
                Point2D force = forces.get(node);
                double magnitude = Math.sqrt(force.getX() * force.getX() + force.getY() * force.getY());
                
                if (magnitude > 0) {
                    double limitedMagnitude = Math.min(magnitude, temperature);
                    double fx = (force.getX() / magnitude) * limitedMagnitude;
                    double fy = (force.getY() / magnitude) * limitedMagnitude;
                    
                    // Constrain to reasonable bounds
                    double newX = Math.max(SIDE_PANEL_WIDTH + 50, 
                                          Math.min(node.getX() + fx, canvas.getWidth() - SIDE_PANEL_WIDTH - 200));
                    double newY = Math.max(50, 
                                          Math.min(node.getY() + fy, canvas.getHeight() - 200));
                    
                    node.setX(newX);
                    node.setY(newY);
                }
            }
            
            temperature *= cooling;
        }
    }
    
    /**
     * Render path-level input/output connection points on canvas sides.
     */
    private void renderPathConnectionPoints() {
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        
        // Get inputs and outputs for the CURRENT PATH
        List<ResourceIdentifier> pathInputs = getPathInputs();
        List<ResourceIdentifier> pathOutputs = getPathOutputs();
        
        // Remove duplicates while preserving order
        pathInputs = pathInputs.stream().distinct().toList();
        pathOutputs = pathOutputs.stream().distinct().toList();
        
        // Render left side inputs
        double leftTextX = 20; // Text starts from left edge
        double startY = 100;
        double ySpacing = 40;
        
        gc.setFill(ColorScheme.TEXT_PRIMARY);
        gc.fillText("INPUTS", leftTextX, startY - 30);
        
        // Calculate the maximum text width for alignment
        double maxInputTextWidth = 0;
        for (ResourceIdentifier input : pathInputs) {
            String label = getShortResourceName(input);
            Text labelText = new Text(label);
            labelText.setFont(gc.getFont());
            double labelWidth = labelText.getBoundsInLocal().getWidth();
            maxInputTextWidth = Math.max(maxInputTextWidth, labelWidth);
        }
        
        // Position all connection points at the same X coordinate
        double leftConnectionX = leftTextX + maxInputTextWidth + 15; // 15px gap from longest text
        
        for (int i = 0; i < pathInputs.size(); i++) {
            ResourceIdentifier input = pathInputs.get(i);
            double y = startY + i * ySpacing;
            
            // Render label first (on the left)
            String label = getShortResourceName(input);
            gc.setFill(ColorScheme.TEXT_SECONDARY);
            gc.fillText(label, leftTextX, y + 4);
            
            // Render connection point at aligned position
            Color pointColor = ColorScheme.getConnectionPointColor(getShortResourceName(input));
            renderConnectionPoint(leftConnectionX, y, pointColor, true);
        }
        
        // Render right side outputs  
        double rightTextEndX = canvas.getWidth() - 20; // Text ends at right edge
        
        gc.setFill(ColorScheme.TEXT_PRIMARY);
        gc.fillText("OUTPUTS", rightTextEndX - 50, startY - 30);
        
        // Calculate the maximum text width for alignment
        double maxOutputTextWidth = 0;
        for (ResourceIdentifier output : pathOutputs) {
            String label = getShortResourceName(output);
            Text labelText = new Text(label);
            labelText.setFont(gc.getFont());
            double labelWidth = labelText.getBoundsInLocal().getWidth();
            maxOutputTextWidth = Math.max(maxOutputTextWidth, labelWidth);
        }
        
        // Position all connection points at the same X coordinate
        double rightConnectionX = rightTextEndX - maxOutputTextWidth - 15; // 15px gap from longest text
        
        for (int i = 0; i < pathOutputs.size(); i++) {
            ResourceIdentifier output = pathOutputs.get(i);
            double y = startY + i * ySpacing;
            
            // Render label first (on the right, right-aligned)
            String label = getShortResourceName(output);
            Text labelText = new Text(label);
            labelText.setFont(gc.getFont());
            double labelWidth = labelText.getBoundsInLocal().getWidth();
            
            gc.setFill(ColorScheme.TEXT_SECONDARY);
            gc.fillText(label, rightTextEndX - labelWidth, y + 4);
            
            // Render connection point at aligned position
            Color pointColor = ColorScheme.getConnectionPointColor(getShortResourceName(output));
            renderConnectionPoint(rightConnectionX, y, pointColor, false);
        }
    }
    
    private void updateConnectionCoordinates() {
        for (ConnectionViewModel connection : connections) {
            NodeViewModel sourceNode = findNodeByPath(connection.getSourcePath());
            NodeViewModel targetNode = findNodeByPath(connection.getTargetPath());
            connection.updateCoordinates(sourceNode, targetNode);
        }
    }
    
    private NodeViewModel findNodeByPath(Path path) {
        return nodes.stream()
                   .filter(node -> node.getNodePath().equals(path))
                   .findFirst()
                   .orElse(null);
    }
    
    /**
     * Update canvas size to match viewport size for responsive behavior.
     */
    private void updateCanvasSize() {
        // Make canvas fill the viewport at minimum
        double viewportWidth = getViewportBounds().getWidth();
        double viewportHeight = getViewportBounds().getHeight();
        
        // Calculate required size based on content + side panels
        double nodeArea = calculateRequiredWidth() - SIDE_PANEL_WIDTH * 2;
        double requiredWidth = Math.max(viewportWidth, Math.max(MIN_CANVAS_WIDTH, nodeArea + SIDE_PANEL_WIDTH * 2));
        double requiredHeight = Math.max(viewportHeight, Math.max(MIN_CANVAS_HEIGHT, calculateRequiredHeight()));
        
        if (canvas.getWidth() != requiredWidth || canvas.getHeight() != requiredHeight) {
            canvas.setWidth(requiredWidth);
            canvas.setHeight(requiredHeight);
            Platform.runLater(this::render);
        }
    }
    
    private void render() {
        // Ensure canvas size is current
        updateCanvasSize();
        
        // Clear canvas
        gc.setFill(ColorScheme.BACKGROUND_DARK);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        
        // Render path-level connection points on canvas sides
        renderPathConnectionPoints();
        
        // Render connections first (behind nodes)
        renderConnections();
        
        // Render nodes (with their own connection points)
        renderNodes();
        
        // Render appropriate floating panels based on mode
        if (editModel != null) {
            // Edit mode: show both edit buttons (top right) and info panel (bottom right)
            renderEditButtons();
            renderFloatingInfoPanel();
        } else {
            // View mode: show info panel only
            renderFloatingInfoPanel();
        }
    }
    
    private double calculateRequiredWidth() {
        return nodes.stream()
                   .mapToDouble(node -> node.getX() + node.getWidth())
                   .max()
                   .orElse(MIN_CANVAS_WIDTH) + CANVAS_PADDING;
    }
    
    private double calculateRequiredHeight() {
        return nodes.stream()
                   .mapToDouble(node -> node.getY() + node.getHeight())
                   .max()
                   .orElse(MIN_CANVAS_HEIGHT) + CANVAS_PADDING;
    }
    
    private void renderConnections() {
        // Render hierarchical connections instead of direct node-to-node connections
        renderHierarchicalConnections();
    }
    
    private void renderHierarchicalConnections() {
        // For the current view level, we need to show:
        // 1. Connections from path-level inputs to child node inputs
        // 2. Connections from child node outputs to path-level outputs
        // 3. Internal connections between child nodes at the same level
        
        List<ResourceIdentifier> pathInputs = getPathInputs();
        List<ResourceIdentifier> pathOutputs = getPathOutputs();
        
        // 1. Connect path inputs to child node inputs that need them
        for (ResourceIdentifier pathInput : pathInputs) {
            Point2D pathInputPoint = getPathInputPoint(pathInput);
            if (pathInputPoint != null) {
                // Find child nodes that need this input
                for (NodeViewModel childNode : nodes) {
                    if (childNode.getInputs().contains(pathInput)) {
                        NodeViewModel.ConnectionPointPosition childInputPoint = 
                            childNode.getInputConnectionPoint(pathInput);
                        if (childInputPoint != null) {
                            // Find the actual connection to determine its type
                            Color connectionColor = findConnectionColor(null, pathInput, childNode.getNodePath(), pathInput);
                            renderHierarchicalConnection(
                                pathInputPoint.getX(), pathInputPoint.getY(),
                                childInputPoint.x(), childInputPoint.y(),
                                connectionColor
                            );
                        }
                    }
                }
            }
        }
        
        // 2. Connect child node outputs to path outputs
        for (ResourceIdentifier pathOutput : pathOutputs) {
            Point2D pathOutputPoint = getPathOutputPoint(pathOutput);
            if (pathOutputPoint != null) {
                // Find child nodes that produce this output
                for (NodeViewModel childNode : nodes) {
                    if (childNode.getOutputs().contains(pathOutput)) {
                        NodeViewModel.ConnectionPointPosition childOutputPoint = 
                            childNode.getOutputConnectionPoint(pathOutput);
                        if (childOutputPoint != null) {
                            // Find the actual connection to determine its type
                            Color connectionColor = findConnectionColor(childNode.getNodePath(), pathOutput, null, pathOutput);
                            renderHierarchicalConnection(
                                childOutputPoint.x(), childOutputPoint.y(),
                                pathOutputPoint.getX(), pathOutputPoint.getY(),
                                connectionColor
                            );
                        }
                    }
                }
            }
        }
        
        // 3. Internal connections between child nodes
        for (ConnectionViewModel connection : connections) {
            // Only draw connections between nodes that are both visible at current level
            NodeViewModel sourceNode = findNodeByPath(connection.getSourcePath());
            NodeViewModel targetNode = findNodeByPath(connection.getTargetPath());
            
            if (sourceNode != null && targetNode != null) {
                renderDirectConnection(connection, sourceNode, targetNode);
            }
        }
    }
    
    private void renderHierarchicalConnection(double x1, double y1, double x2, double y2, Color lineColor) {
        gc.setStroke(lineColor);
        gc.setLineWidth(CONNECTION_LINE_WIDTH);
        
        // Draw curved connection line using bezier curve
        drawCurvedConnection(x1, y1, x2, y2);
    }
    
    private void renderDirectConnection(ConnectionViewModel connection, NodeViewModel sourceNode, NodeViewModel targetNode) {
        Color lineColor = ColorScheme.getConnectionLineColor(
            mapConnectionType(connection.getConnectionType()));
        
        // Get actual connection points on the nodes
        NodeViewModel.ConnectionPointPosition sourcePoint = 
            sourceNode.getOutputConnectionPoint(connection.getSourceResource());
        NodeViewModel.ConnectionPointPosition targetPoint = 
            targetNode.getInputConnectionPoint(connection.getTargetResource());
        
        if (sourcePoint != null && targetPoint != null) {
            renderHierarchicalConnection(
                sourcePoint.x(), sourcePoint.y(),
                targetPoint.x(), targetPoint.y(),
                lineColor
            );
        }
    }
    
    /**
     * Draw a smooth curved connection between two points using a bezier curve.
     * This creates a more professional look and helps avoid overlapping with nodes.
     */
    private void drawCurvedConnection(double x1, double y1, double x2, double y2) {
        // Calculate control points for the bezier curve
        double distance = Math.abs(x2 - x1);
        double curveStrength = Math.max(50, Math.min(distance * 0.5, 200)); // Adaptive curve strength
        
        // Control points create a horizontal curve
        double cp1x = x1 + curveStrength;
        double cp1y = y1;
        double cp2x = x2 - curveStrength;
        double cp2y = y2;
        
        // For vertical connections, adjust control points to create vertical curves
        if (Math.abs(x2 - x1) < 50) {
            // Nearly vertical connection - use vertical curve
            double verticalCurveStrength = Math.max(30, Math.abs(y2 - y1) * 0.3);
            cp1x = x1;
            cp1y = y1 + (y2 > y1 ? verticalCurveStrength : -verticalCurveStrength);
            cp2x = x2;
            cp2y = y2 - (y2 > y1 ? verticalCurveStrength : -verticalCurveStrength);
        }
        
        // Create path for the bezier curve
        gc.beginPath();
        gc.moveTo(x1, y1);
        gc.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, x2, y2);
        gc.stroke();
    }
    
    private ColorScheme.ConnectionType mapConnectionType(ConnectionViewModel.ConnectionType type) {
        return switch (type) {
            case DIRECT -> ColorScheme.ConnectionType.DIRECT;
            case CONDITIONAL -> ColorScheme.ConnectionType.CONDITIONAL;
            case FLYWIRE -> ColorScheme.ConnectionType.FLYWIRE;
        };
    }
    
    /**
     * Find the appropriate connection color by looking up the connection type
     * from the actual ConnectionViewModel data and EvaluationResult using InputResult context.
     */
    private Color findConnectionColor(Path sourcePath, ResourceIdentifier sourceResource, 
                                    Path targetPath, ResourceIdentifier targetResource) {
        
        // Check if this is a flywire connection from adhoc overrides first
        if (sourcePath != null && sourceResource != null && targetPath != null && targetResource != null && model != null) {
            if (model.getEvaluationResult().adhocOverride().isPresent()) {
                var flywires = model.getEvaluationResult().adhocOverride().get().adhocFlywires();
                for (var flywire : flywires) {
                    if (flywire.source().nodePath().equals(sourcePath) && 
                        flywire.source().rid().equals(sourceResource) &&
                        flywire.target().nodePath().equals(targetPath) && 
                        flywire.target().rid().equals(targetResource)) {
                        return ColorScheme.getConnectionLineColor(ColorScheme.ConnectionType.FLYWIRE);
                    }
                }
            }
        }
        
        // Use InputResult context to determine connection type for target node - this is the primary method
        if (model != null && targetPath != null && targetResource != null) {
            var nodeEvaluation = model.getEvaluationResult().nodeEvaluationMap().get(targetPath);
            if (nodeEvaluation != null && nodeEvaluation.inputs().containsKey(targetResource)) {
                var inputResult = nodeEvaluation.inputs().get(targetResource);
                var inputContext = inputResult.inputContext();
                
                // Check if this is a flywire connection based on source type
                if (inputContext.sourceType() == me.vincentzz.graph.model.input.InputSourceType.ByFlywire ||
                    inputContext.sourceType() == me.vincentzz.graph.model.input.InputSourceType.ByAdhocFlywire) {
                    return ColorScheme.getConnectionLineColor(ColorScheme.ConnectionType.FLYWIRE);
                }
                
                // For hierarchical connections from path inputs to child nodes:
                // - Check if this is a path-to-node connection (sourcePath is null)
                // - Use more nuanced logic for ByParentGroup and ByResolve
                
                if (sourcePath == null) {
                    // This is a path input to child node connection
                    if (inputContext.sourceType() == me.vincentzz.graph.model.input.InputSourceType.ByParentGroup) {
                        // For ByParentGroup, use isDirectInput flag if available
                        if (inputContext.isDirectInput().isPresent()) {
                            boolean isDirect = inputContext.isDirectInput().get();
                            if (isDirect) {
                                return ColorScheme.getConnectionLineColor(ColorScheme.ConnectionType.DIRECT);
                            } else {
                                return ColorScheme.getConnectionLineColor(ColorScheme.ConnectionType.CONDITIONAL);
                            }
                        } else {
                            // Default for ByParentGroup without isDirectInput flag
                            return ColorScheme.getConnectionLineColor(ColorScheme.ConnectionType.DIRECT);
                        }
                    } else if (inputContext.sourceType() == me.vincentzz.graph.model.input.InputSourceType.ByResolve) {
                        // For nodeGroups' dependencies when isDirectInput is empty, default to DIRECT (black)
                        return ColorScheme.getConnectionLineColor(ColorScheme.ConnectionType.DIRECT);
                    }
                }
                
                // General logic for all connection types
                // Check if this is a direct or indirect input using isDirectInput flag
                if (inputContext.isDirectInput().isPresent()) {
                    boolean isDirect = inputContext.isDirectInput().get();
                    if (isDirect) {
                        return ColorScheme.getConnectionLineColor(ColorScheme.ConnectionType.DIRECT);
                    } else {
                        return ColorScheme.getConnectionLineColor(ColorScheme.ConnectionType.CONDITIONAL);
                    }
                }
                
                // Fallback based on source type if isDirectInput is not available
                // For nodeGroups' dependencies when isDirectInput is empty, default to DIRECT (black)
                return switch (inputContext.sourceType()) {
                    case ByParentGroup -> ColorScheme.getConnectionLineColor(ColorScheme.ConnectionType.DIRECT);
                    case ByResolve -> ColorScheme.getConnectionLineColor(ColorScheme.ConnectionType.DIRECT); // Default to black when isDirectInput is empty
                    case ByFlywire, ByAdhocFlywire -> ColorScheme.getConnectionLineColor(ColorScheme.ConnectionType.FLYWIRE);
                    case ByAdhoc -> ColorScheme.getConnectionLineColor(ColorScheme.ConnectionType.DIRECT);
                };
            }
        }
        
        // Look through existing connections to find a match (secondary method)
        for (ConnectionViewModel connection : connections) {
            boolean sourceMatches = (sourcePath == null) || connection.getSourcePath().equals(sourcePath);
            boolean targetMatches = (targetPath == null) || connection.getTargetPath().equals(targetPath);
            boolean sourceResourceMatches = connection.getSourceResource().equals(sourceResource);
            boolean targetResourceMatches = connection.getTargetResource().equals(targetResource);
            
            if (sourceMatches && targetMatches && sourceResourceMatches && targetResourceMatches) {
                return ColorScheme.getConnectionLineColor(mapConnectionType(connection.getConnectionType()));
            }
        }
        
        // Final fallback: check for any matching resource across all nodes
        if (model != null && targetResource != null) {
            for (var entry : model.getEvaluationResult().nodeEvaluationMap().entrySet()) {
                Path nodePath = entry.getKey();
                me.vincentzz.graph.model.NodeEvaluation nodeEvaluation = entry.getValue();
                
                if (nodeEvaluation.inputs().containsKey(targetResource)) {
                    var inputResult = nodeEvaluation.inputs().get(targetResource);
                    var inputContext = inputResult.inputContext();
                    
                    // Check for flywire connections
                    if (inputContext.sourceType() == me.vincentzz.graph.model.input.InputSourceType.ByFlywire ||
                        inputContext.sourceType() == me.vincentzz.graph.model.input.InputSourceType.ByAdhocFlywire) {
                        return ColorScheme.getConnectionLineColor(ColorScheme.ConnectionType.FLYWIRE);
                    }
                    
                    // Check directness
                    if (inputContext.isDirectInput().isPresent()) {
                        boolean isDirect = inputContext.isDirectInput().get();
                        return isDirect ? 
                            ColorScheme.getConnectionLineColor(ColorScheme.ConnectionType.DIRECT) :
                            ColorScheme.getConnectionLineColor(ColorScheme.ConnectionType.CONDITIONAL);
                    }
                    
                    // Use source type as final determination
                    if (inputContext.sourceType() == me.vincentzz.graph.model.input.InputSourceType.ByResolve) {
                        return ColorScheme.getConnectionLineColor(ColorScheme.ConnectionType.CONDITIONAL);
                    }
                }
            }
        }
        
        // Default to black (DIRECT) if no specific connection information found
        return ColorScheme.getConnectionLineColor(ColorScheme.ConnectionType.DIRECT);
    }
    
    private void renderNodes() {
        for (NodeViewModel node : nodes) {
            renderNode(node);
        }
    }
    
    private void renderNode(NodeViewModel node) {
        double x = node.getX();
        double y = node.getY();
        double width = node.getWidth();
        double height = node.getHeight();
        
        // Determine node colors
        Color backgroundColor = node.isHovered() ? 
            ColorScheme.NODE_HOVER : ColorScheme.NODE_BACKGROUND;
        Color borderColor = node.isSelected() ? 
            ColorScheme.NODE_SELECTED : ColorScheme.NODE_BORDER;
        
        // Debug output for selection
        if (node.isSelected()) {
            System.out.println("DEBUG: Rendering selected node " + node.getDisplayName() + 
                              " with border color: " + borderColor);
        }
        
        // Draw node background
        gc.setFill(backgroundColor);
        gc.fillRoundRect(x, y, width, height, 8, 8);
        
        // Draw node border with thicker line for selected nodes
        gc.setStroke(borderColor);
        gc.setLineWidth(node.isSelected() ? 4 : 2); // Make selected nodes more visible
        gc.strokeRoundRect(x, y, width, height, 8, 8);
        
        // Draw node title
        gc.setFill(ColorScheme.TEXT_PRIMARY);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        
        Text titleText = new Text(node.getDisplayName());
        titleText.setFont(gc.getFont());
        double titleWidth = titleText.getBoundsInLocal().getWidth();
        double titleX = x + (width - titleWidth) / 2;
        double titleY = y + 20;
        
        gc.fillText(node.getDisplayName(), titleX, titleY);
        
        // Draw connection points
        renderConnectionPoints(node);
    }
    
    private void renderConnectionPoints(NodeViewModel node) {
        gc.setFont(Font.font("Arial", 10));
        
        // Draw input connection points
        for (ResourceIdentifier input : node.getInputs()) {
            NodeViewModel.ConnectionPointPosition pos = node.getInputConnectionPoint(input);
            if (pos != null) {
                Color pointColor = ColorScheme.getConnectionPointColor(getShortResourceName(input));
                renderConnectionPoint(pos.x(), pos.y(), pointColor, true, node, input);
                
                // Draw input label
                gc.setFill(ColorScheme.TEXT_SECONDARY);
                gc.fillText(getShortResourceName(input), pos.x() + 15, pos.y() + 4);
            }
        }
        
        // Draw output connection points
        for (ResourceIdentifier output : node.getOutputs()) {
            NodeViewModel.ConnectionPointPosition pos = node.getOutputConnectionPoint(output);
            if (pos != null) {
                Color pointColor = ColorScheme.getConnectionPointColor(getShortResourceName(output));
                renderConnectionPoint(pos.x(), pos.y(), pointColor, false, node, output);
                
                // Draw output label
                gc.setFill(ColorScheme.TEXT_SECONDARY);
                String label = getShortResourceName(output);
                Text labelText = new Text(label);
                labelText.setFont(gc.getFont());
                double labelWidth = labelText.getBoundsInLocal().getWidth();
                gc.fillText(label, pos.x() - labelWidth - 15, pos.y() + 4);
            }
        }
    }
    
    private void renderConnectionPoint(double x, double y, Color color, boolean isInput) {
        renderConnectionPoint(x, y, color, isInput, null, null);
    }
    
    private void renderConnectionPoint(double x, double y, Color color, boolean isInput, 
                                     NodeViewModel node, ResourceIdentifier resource) {
        // Draw connection point circle
        gc.setFill(color);
        gc.fillOval(x - CONNECTION_POINT_RADIUS, y - CONNECTION_POINT_RADIUS, 
                   CONNECTION_POINT_RADIUS * 2, CONNECTION_POINT_RADIUS * 2);
        
        // Draw border
        gc.setStroke(ColorScheme.NODE_BORDER);
        gc.setLineWidth(1);
        gc.strokeOval(x - CONNECTION_POINT_RADIUS, y - CONNECTION_POINT_RADIUS,
                     CONNECTION_POINT_RADIUS * 2, CONNECTION_POINT_RADIUS * 2);
        
        // Draw status indicator if we have node and resource information
        if (node != null && resource != null) {
            renderConnectionPointStatusIndicator(x, y, isInput, node, resource);
        }
    }
    
    /**
     * Render a small vertical status bar next to the connection point to indicate success/failure.
     * Only shows status indicators in result viewing mode, not in edit mode.
     */
    private void renderConnectionPointStatusIndicator(double x, double y, boolean isInput, 
                                                    NodeViewModel node, ResourceIdentifier resource) {
        // Only show status indicators in result viewing mode (when we have evaluation results)
        // In edit mode (editModel != null), nodes are not evaluated yet, so no status indicators
        if (editModel != null) {
            return; // Skip status indicators in edit mode
        }
        
        // Get the status from evaluation results
        boolean isSuccess = getConnectionPointStatus(node, resource, isInput);
        
        // Status bar dimensions
        double statusBarWidth = 3;
        double statusBarHeight = 12;
        
        // Position the status bar
        double statusBarX;
        if (isInput) {
            // For input connection points, put status bar on the right of the dot
            statusBarX = x + CONNECTION_POINT_RADIUS + 2;
        } else {
            // For output connection points, put status bar on the left of the dot
            statusBarX = x - CONNECTION_POINT_RADIUS - statusBarWidth - 2;
        }
        double statusBarY = y - statusBarHeight / 2;
        
        // Choose color based on status
        Color statusColor = isSuccess ? Color.GREEN : Color.RED;
        
        // Draw the status bar
        gc.setFill(statusColor);
        gc.fillRect(statusBarX, statusBarY, statusBarWidth, statusBarHeight);
        
        // Draw a subtle border around the status bar
        gc.setStroke(statusColor.darker());
        gc.setLineWidth(0.5);
        gc.strokeRect(statusBarX, statusBarY, statusBarWidth, statusBarHeight);
    }
    
    /**
     * Get the success/failure status for a connection point.
     */
    private boolean getConnectionPointStatus(NodeViewModel node, ResourceIdentifier resource, boolean isInput) {
        if (model == null) {
            return true; // Default to success if no model
        }
        
        try {
            var nodeEvaluation = model.getEvaluationResult().nodeEvaluationMap().get(node.getNodePath());
            if (nodeEvaluation == null) {
                return true; // Default to success if no evaluation data
            }
            
            if (isInput) {
                // For input connection points, check if the input result is successful
                var inputResult = nodeEvaluation.inputs().get(resource);
                if (inputResult != null) {
                    return isResultSuccessful(inputResult.value());
                }
            } else {
                // For output connection points, check if the output result is successful
                var outputResult = nodeEvaluation.outputs().get(resource);
                if (outputResult != null) {
                    return isResultSuccessful(outputResult.value());
                }
            }
            
            // If no specific result found, default to success
            return true;
            
        } catch (Exception e) {
            // If any error occurs, default to success to avoid visual noise
            return true;
        }
    }
    
    /**
     * Check if a Result is successful (Success vs Failure).
     */
    private boolean isResultSuccessful(me.vincentzz.lang.Result.Result<Object> result) {
        if (result == null) {
            return false;
        }
        
        return result instanceof me.vincentzz.lang.Result.Success;
    }
    
    private String getShortResourceName(ResourceIdentifier resourceId) {
        String fullName = getResourceName(resourceId);
        
        // Handle FalconResourceId format: FalconResourceId[ifo=GOOGLE, source=FALCON, attribute=class me.vincentzz.falcon.attribute.Spread]
        if (fullName.contains("attribute=")) {
            int attributeStart = fullName.indexOf("attribute=") + "attribute=".length();
            int attributeEnd = fullName.indexOf("]", attributeStart);
            if (attributeEnd == -1) {
                attributeEnd = fullName.indexOf(",", attributeStart);
            }
            if (attributeEnd == -1) {
                attributeEnd = fullName.length();
            }
            
            String attributeValue = fullName.substring(attributeStart, attributeEnd).trim();
            
            // If the attribute value starts with "class ", extract the simple class name
            if (attributeValue.startsWith("class ")) {
                String className = attributeValue.substring("class ".length());
                // Get simple name (last part after the last dot)
                int lastDot = className.lastIndexOf('.');
                if (lastDot != -1) {
                    return className.substring(lastDot + 1);
                }
                return className;
            }
            
            return attributeValue;
        }
        
        // Fallback: Return last part after the last dot or colon, removing any trailing bracket
        String[] parts = fullName.split("[.:]");
        String lastPart = parts[parts.length - 1];
        
        // Remove trailing bracket if present
        if (lastPart.endsWith("]")) {
            lastPart = lastPart.substring(0, lastPart.length() - 1);
        }
        
        return lastPart;
    }
    
    /**
     * Get the list of inputs for the current path level, ordered to minimize connection crossings.
     */
    private List<ResourceIdentifier> getPathInputs() {
        List<ResourceIdentifier> pathInputs = new ArrayList<>();
        
        // In edit mode, use EditCanvasModel for path inputs
        if (editModel != null) {
            pathInputs.addAll(editModel.getPathInputs());
            return optimizeConnectionPointOrder(pathInputs, true);
        }
        
        // In view mode, use VisualizationModel
        if (model == null || currentPath == null) {
            return optimizeConnectionPointOrder(pathInputs, true);
        }
        
        try {
            String currentPathStr = currentPath.toString();
            var graph = model.getEvaluationResult().graph();
            
            // Extract actual inputs by calling inputs() on CalculationNode objects
            var inputs = extractInputsFromCalculationNode(graph, currentPathStr);
            
            if (inputs != null && !inputs.isEmpty()) {
                pathInputs.addAll(inputs);
            }
            
        } catch (Exception e) {
            // Fallback: Try to get the node from the model
            NodeViewModel currentPathNode = model.getNodeViewModel(currentPath);
            if (currentPathNode != null) {
                pathInputs.addAll(currentPathNode.getInputs());
            }
        }
        
        return optimizeConnectionPointOrder(pathInputs, true);
    }
    
    /**
     * Get the list of outputs for the current path level, ordered to minimize connection crossings.
     */
    private List<ResourceIdentifier> getPathOutputs() {
        List<ResourceIdentifier> pathOutputs = new ArrayList<>();
        
        // In edit mode, use EditCanvasModel for path outputs
        if (editModel != null) {
            pathOutputs.addAll(editModel.getPathOutputs());
            return optimizeConnectionPointOrder(pathOutputs, false);
        }
        
        // In view mode, use VisualizationModel
        if (model == null || currentPath == null) {
            return optimizeConnectionPointOrder(pathOutputs, false);
        }
        
        try {
            String currentPathStr = currentPath.toString();
            var graph = model.getEvaluationResult().graph();
            
            // Extract actual outputs by calling outputs() on CalculationNode objects
            var outputs = extractOutputsFromCalculationNode(graph, currentPathStr);
            
            if (outputs != null && !outputs.isEmpty()) {
                pathOutputs.addAll(outputs);
            }
            
        } catch (Exception e) {
            // Fallback: Try to get the node from the model
            NodeViewModel currentPathNode = model.getNodeViewModel(currentPath);
            if (currentPathNode != null) {
                pathOutputs.addAll(currentPathNode.getOutputs());
            }
        }
        
        return optimizeConnectionPointOrder(pathOutputs, false);
    }
    
    /**
     * Optimize the order of connection points to minimize line crossings.
     * Orders connection points based on the actual Y-coordinates of connection points on sub-node rectangles.
     */
    private List<ResourceIdentifier> optimizeConnectionPointOrder(List<ResourceIdentifier> resourceIds, boolean isInput) {
        if (resourceIds.isEmpty()) {
            return new ArrayList<>(resourceIds);
        }
        
        // Create a list of resources with their connection weights (actual connection point Y positions)
        List<ResourceWithWeight> resourcesWithWeights = new ArrayList<>();
        
        for (ResourceIdentifier resourceId : resourceIds) {
            double totalY = 0;
            int connectionPointCount = 0;
            
            // Find all nodes that have connection points for this resource
            for (NodeViewModel node : nodes) {
                NodeViewModel.ConnectionPointPosition connectionPoint = null;
                
                if (isInput && node.getInputs().contains(resourceId)) {
                    // Get the actual input connection point position
                    connectionPoint = node.getInputConnectionPoint(resourceId);
                } else if (!isInput && node.getOutputs().contains(resourceId)) {
                    // Get the actual output connection point position
                    connectionPoint = node.getOutputConnectionPoint(resourceId);
                }
                
                if (connectionPoint != null) {
                    totalY += connectionPoint.y(); // Use actual connection point Y coordinate
                    connectionPointCount++;
                }
            }
            
            // If no connection points found, use a default ordering based on resource name
            double averageY = connectionPointCount > 0 ? totalY / connectionPointCount : getDefaultResourceOrder(resourceId);
            resourcesWithWeights.add(new ResourceWithWeight(resourceId, averageY));
        }
        
        // Sort by average connection point Y position to minimize crossings
        resourcesWithWeights.sort((a, b) -> Double.compare(a.weight, b.weight));
        
        // Extract the ordered resource identifiers
        return resourcesWithWeights.stream()
                .map(rw -> rw.resource)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Get a default ordering value for resources that don't have visible connection points.
     * This ensures consistent ordering even when some resources aren't connected to visible nodes.
     */
    private double getDefaultResourceOrder(ResourceIdentifier resourceId) {
        // Use a hash-based ordering to ensure consistent results
        String resourceName = getShortResourceName(resourceId);
        
        // Create a deterministic ordering based on resource name and type
        // Common financial calculation order: Bid < Ask < Mid < Spread < Volume
        return switch (resourceName.toLowerCase()) {
            case "bid" -> 100;
            case "ask" -> 200;
            case "midprice", "mid" -> 300;
            case "spread" -> 400;
            case "volume" -> 500;
            case "vwap" -> 600;
            case "marktomarket", "mtm" -> 700;
            default -> 800 + resourceName.hashCode() % 1000; // Deterministic fallback
        };
    }
    
    /**
     * Helper class to store a resource with its connection weight for ordering.
     */
    private static class ResourceWithWeight {
        final ResourceIdentifier resource;
        final double weight;
        
        ResourceWithWeight(ResourceIdentifier resource, double weight) {
            this.resource = resource;
            this.weight = weight;
        }
    }
    
    /**
     * Get the position of a path input connection point.
     */
    private Point2D getPathInputPoint(ResourceIdentifier input) {
        List<ResourceIdentifier> pathInputs = getPathInputs();
        int index = pathInputs.indexOf(input);
        
        if (index >= 0) {
            double leftTextX = 20;
            double startY = 100;
            double ySpacing = 40;
            double y = startY + index * ySpacing;
            
            // Calculate the maximum text width for alignment
            double maxInputTextWidth = 0;
            for (ResourceIdentifier pathInput : pathInputs) {
                String label = getShortResourceName(pathInput);
                Text labelText = new Text(label);
                labelText.setFont(Font.font("Arial", FontWeight.BOLD, 12));
                double labelWidth = labelText.getBoundsInLocal().getWidth();
                maxInputTextWidth = Math.max(maxInputTextWidth, labelWidth);
            }
            
            // Position connection point at aligned position
            double leftConnectionX = leftTextX + maxInputTextWidth + 15; // 15px gap from longest text
            
            return new Point2D(leftConnectionX, y);
        }
        
        return null;
    }
    
    /**
     * Get the position of a path output connection point.
     */
    private Point2D getPathOutputPoint(ResourceIdentifier output) {
        List<ResourceIdentifier> pathOutputs = getPathOutputs();
        int index = pathOutputs.indexOf(output);
        
        if (index >= 0) {
            double rightTextEndX = canvas.getWidth() - 20;
            double startY = 100;
            double ySpacing = 40;
            double y = startY + index * ySpacing;
            
            // Calculate the maximum text width for alignment
            double maxOutputTextWidth = 0;
            for (ResourceIdentifier pathOutput : pathOutputs) {
                String label = getShortResourceName(pathOutput);
                Text labelText = new Text(label);
                labelText.setFont(Font.font("Arial", FontWeight.BOLD, 12));
                double labelWidth = labelText.getBoundsInLocal().getWidth();
                maxOutputTextWidth = Math.max(maxOutputTextWidth, labelWidth);
            }
            
            // Position connection point at aligned position
            double rightConnectionX = rightTextEndX - maxOutputTextWidth - 15; // 15px gap from longest text
            
            return new Point2D(rightConnectionX, y);
        }
        
        return null;
    }
    
    // Event handler setters
    public void setOnNodeClicked(Consumer<Path> onNodeClicked) {
        this.onNodeClicked = onNodeClicked;
    }
    
    public void setOnNodeDoubleClicked(Consumer<Path> onNodeDoubleClicked) {
        this.onNodeDoubleClicked = onNodeDoubleClicked;
    }
    
    public void setOnNodeHovered(BiConsumer<Path, String> onNodeHovered) {
        this.onNodeHovered = onNodeHovered;
    }
    
    public void setOnEditCompleted(Consumer<EvaluationResult> onEditCompleted) {
        this.onEditCompleted = onEditCompleted;
    }
    
    public void setOnMultipleNodesSelected(Consumer<Set<Path>> onMultipleNodesSelected) {
        this.onMultipleNodesSelected = onMultipleNodesSelected;
    }
    
    public void setOnStructuralChange(Runnable onStructuralChange) {
        this.onStructuralChange = onStructuralChange;
    }
    
    /**
     * Set edit model for edit mode.
     */
    public void setEditModel(me.vincentzz.visual.model.EditCanvasModel editModel) {
        this.editModel = editModel;
        if (editModel != null) {
            // Update nodes and connections from edit model, preserving existing state
            updateNodesFromEditModel();
            this.connections = new ArrayList<>(editModel.getConnections());
            this.currentPath = editModel.getCurrentPath();
            
            // Layout and render
            layoutNodes();
            render();
        }
    }
    
    /**
     * Update nodes from edit model while preserving existing selection state and positions.
     */
    private void updateNodesFromEditModel() {
        var newNodes = editModel.getNodes();
        
        // Create a map of existing nodes by display name for quick lookup
        Map<String, NodeViewModel> existingNodeMap = new HashMap<>();
        for (NodeViewModel existingNode : nodes) {
            existingNodeMap.put(existingNode.getDisplayName(), existingNode);
        }
        
        // Update nodes list, preserving state from existing nodes
        List<NodeViewModel> updatedNodes = new ArrayList<>();
        for (NodeViewModel newNode : newNodes) {
            NodeViewModel existingNode = existingNodeMap.get(newNode.getDisplayName());
            if (existingNode != null) {
                // Preserve selection state and position from existing node
                newNode.setSelected(existingNode.isSelected());
                newNode.setX(existingNode.getX());
                newNode.setY(existingNode.getY());
                newNode.setHovered(existingNode.isHovered());
            }
            updatedNodes.add(newNode);
        }
        
        this.nodes = updatedNodes;
    }
    
    /**
     * Cleanup resources when shutting down.
     */
    public void shutdown() {
        hideTooltip();
    }
    
    /**
     * Get the current path as a string for comparison.
     */
    private String getCurrentPathString() {
        return currentPath != null ? currentPath.toString() : "/root";
    }
    
    /**
     * Set the current path being displayed.
     */
    public void setCurrentPath(Path currentPath) {
        this.currentPath = currentPath;
        render();
    }
    
    /**
     * Set the visualization model reference for accessing correct node information.
     */
    public void setModel(VisualizationModel model) {
        this.model = model;
    }
    
    /**
     * Find path input ResourceIdentifier at given coordinates.
     */
    private ResourceIdentifier findPathInputAt(double x, double y) {
        List<ResourceIdentifier> pathInputs = getPathInputs();
        
        for (ResourceIdentifier input : pathInputs) {
            Point2D inputPoint = getPathInputPoint(input);
            if (inputPoint != null) {
                // Check connection point dot
                double distance = Math.sqrt(Math.pow(x - inputPoint.getX(), 2) + Math.pow(y - inputPoint.getY(), 2));
                if (distance <= CONNECTION_POINT_RADIUS + 5) { // 5px tolerance for easier hovering
                    return input;
                }
                
                // Check input label area (text is at leftTextX, y + 4)
                double leftTextX = 20;
                double startY = 100;
                double ySpacing = 40;
                int index = pathInputs.indexOf(input);
                double labelY = startY + index * ySpacing + 4;
                
                String label = getShortResourceName(input);
                Text labelText = new Text(label);
                labelText.setFont(Font.font("Arial", FontWeight.BOLD, 12));
                double labelWidth = labelText.getBoundsInLocal().getWidth();
                double labelHeight = labelText.getBoundsInLocal().getHeight();
                
                // Check if mouse is within label bounds
                if (x >= leftTextX && x <= leftTextX + labelWidth && 
                    y >= labelY - labelHeight && y <= labelY + 4) {
                    return input;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Find path output ResourceIdentifier at given coordinates.
     */
    private ResourceIdentifier findPathOutputAt(double x, double y) {
        List<ResourceIdentifier> pathOutputs = getPathOutputs();
        
        for (ResourceIdentifier output : pathOutputs) {
            Point2D outputPoint = getPathOutputPoint(output);
            if (outputPoint != null) {
                // Check connection point dot
                double distance = Math.sqrt(Math.pow(x - outputPoint.getX(), 2) + Math.pow(y - outputPoint.getY(), 2));
                if (distance <= CONNECTION_POINT_RADIUS + 5) { // 5px tolerance for easier hovering
                    return output;
                }
                
                // Check output label area (text is right-aligned at rightTextEndX - labelWidth, y + 4)
                double rightTextEndX = canvas.getWidth() - 20;
                double startY = 100;
                double ySpacing = 40;
                int index = pathOutputs.indexOf(output);
                double labelY = startY + index * ySpacing + 4;
                
                String label = getShortResourceName(output);
                Text labelText = new Text(label);
                labelText.setFont(Font.font("Arial", FontWeight.BOLD, 12));
                double labelWidth = labelText.getBoundsInLocal().getWidth();
                double labelHeight = labelText.getBoundsInLocal().getHeight();
                
                double labelX = rightTextEndX - labelWidth;
                
                // Check if mouse is within label bounds
                if (x >= labelX && x <= labelX + labelWidth && 
                    y >= labelY - labelHeight && y <= labelY + 4) {
                    return output;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Show detailed ResourceIdentifier tooltip for path connection points.
     */
    private void showPathResourceTooltip(ResourceIdentifier resourceId, double x, double y, boolean isInput) {
        StringBuilder tooltipText = new StringBuilder();
        tooltipText.append(isInput ? "PATH INPUT" : "PATH OUTPUT").append("\n");
        tooltipText.append("Resource: ").append(resourceId.toString()).append("\n");
        
        // Try to extract detailed information from FalconResourceId format
        String resourceString = resourceId.toString();
        if (resourceString.contains("FalconResourceId[")) {
            // Parse FalconResourceId format
            if (resourceString.contains("ifo=")) {
                String ifo = extractValue(resourceString, "ifo=");
                if (ifo != null) {
                    tooltipText.append("IFO: ").append(ifo).append("\n");
                }
            }
            
            if (resourceString.contains("source=")) {
                String source = extractValue(resourceString, "source=");
                if (source != null) {
                    tooltipText.append("Source: ").append(source).append("\n");
                }
            }
            
            if (resourceString.contains("attribute=")) {
                String attribute = extractValue(resourceString, "attribute=");
                if (attribute != null) {
                    // Clean up class attribute format
                    if (attribute.startsWith("class ")) {
                        String className = attribute.substring("class ".length());
                        int lastDot = className.lastIndexOf('.');
                        if (lastDot != -1) {
                            attribute = className.substring(lastDot + 1);
                        } else {
                            attribute = className;
                        }
                    }
                    tooltipText.append("Attribute: ").append(attribute).append("\n");
                }
            }
        }
        
        // Add value information for both inputs and outputs
        if (isInput) {
            String inputValue = getPathInputValueForTooltip(resourceId);
            tooltipText.append("Input Value: ").append(inputValue).append("\n");
        } else {
            String outputValue = getPathOutputValue(resourceId);
            tooltipText.append("Output Value: ").append(outputValue).append("\n");
        }
        
        // Add connection type information
        tooltipText.append("Type: ").append(isInput ? "External Input" : "Available Output");
        
        // Use 100ms delay for connection point tooltips
        showTooltipWithDelay(tooltipText.toString(), x, y, CONNECTION_POINT_TOOLTIP_DELAY);
    }
    
    /**
     * Extract value from FalconResourceId string format.
     */
    private String extractValue(String resourceString, String key) {
        int keyStart = resourceString.indexOf(key);
        if (keyStart == -1) {
            return null;
        }
        
        int valueStart = keyStart + key.length();
        int valueEnd = resourceString.indexOf(",", valueStart);
        if (valueEnd == -1) {
            valueEnd = resourceString.indexOf("]", valueStart);
        }
        if (valueEnd == -1) {
            valueEnd = resourceString.length();
        }
        
        return resourceString.substring(valueStart, valueEnd).trim();
    }
    
    /**
     * Get path input value for display in tooltips.
     */
    private String getPathInputValueForTooltip(ResourceIdentifier resourceId) {
        if (model == null) {
            return "No model available";
        }
        
        // Get detailed input information from nodeEvaluationMap for current path
        if (currentPath != null) {
            var nodeEvaluation = model.getEvaluationResult().nodeEvaluationMap().get(currentPath);
            if (nodeEvaluation != null && nodeEvaluation.inputs().containsKey(resourceId)) {
                var inputResult = nodeEvaluation.inputs().get(resourceId);
                
                // Extract comprehensive context from InputResult
                String valueStr = formatResultValue(inputResult.value());
                String sourceType = formatInputSourceType(inputResult.inputContext().sourceType());
                
                // Check if input is direct or indirect
                String directnessInfo = "";
                if (inputResult.inputContext().isDirectInput().isPresent()) {
                    boolean isDirect = inputResult.inputContext().isDirectInput().get();
                    directnessInfo = isDirect ? " (direct input)" : " (indirect input)";
                }
                
                // Provide detailed source information
                String sourceInfo = getDetailedInputSourceInfo(inputResult.inputContext().sourceType(), resourceId);
                
                return valueStr + directnessInfo + " from " + sourceType + 
                       (sourceInfo != null ? " - " + sourceInfo : "");
            }
        }
        
        // Check if there's an adhoc override for this resource
        String adhocValue = getAdhocOutputValue(resourceId);
        if (adhocValue != null) {
            return adhocValue + " (adhoc override)";
        }
        
        // Look for dependency value with detailed context
        String dependencyValue = getDependencyValueWithContext(resourceId);
        if (dependencyValue != null) {
            return dependencyValue;
        }
        
        return "No value available";
    }
    
    
    /**
     * Show detailed tooltip for node connection points.
     */
    private void showNodeConnectionPointTooltip(NodeViewModel node, ResourceIdentifier resource, boolean isInput, double x, double y) {
        StringBuilder tooltipText = new StringBuilder();
        tooltipText.append("ResourceId : ").append(resource.toString()).append("\n");
        if (model != null ) {
            var nodeEvaluation = model.getEvaluationResult().nodeEvaluationMap().get(node.getNodePath());
            // Add value information for both inputs and outputs
            if (isInput) {
                var inputResult = nodeEvaluation.inputs().get(resource);
                Optional<Boolean> isDirect = inputResult.inputContext().isDirectInput();
                isDirect.ifPresent(d -> tooltipText.append("IsDirect   : ").append(d).append("\n"));

                InputSourceType st = inputResult.inputContext().sourceType();
                tooltipText.append("Tracing    : ").append(st).append("\n");

                var result = inputResult.value();
                switch (result) {
                    case Success r -> tooltipText.append("Value      : ").append(r.get()).append("\n");
                    case Failure e ->
                            tooltipText.append("Error      : ").append(e.getException().getMessage()).append("\n");
                }
            } else {
                if (node.hasResult(resource)) {
                    var outputResult = nodeEvaluation.outputs().get(resource);
                    OutputValueType vt = outputResult.outputContext().resultType();
                    tooltipText.append("Tracing    : ").append(vt).append("\n");
                    Result<Object> result = outputResult.value();
                    switch (result) {
                        case Success r -> tooltipText.append("Value      : ").append(r.get()).append("\n");
                        case Failure e ->
                                tooltipText.append("Error      : ").append(e.getException().getMessage()).append("\n");
                    }
                } else {
                    tooltipText.append("Output Value: ").append("Not computed").append("\n");
                }
            }
        }
        // Use 100ms delay for connection point tooltips
        showTooltipWithDelay(tooltipText.toString(), x, y, CONNECTION_POINT_TOOLTIP_DELAY);
    }
    
    /**
     * Find node connection point at given coordinates.
     */
    private NodeConnectionPointInfo findNodeConnectionPointAt(double x, double y) {
        for (NodeViewModel node : nodes) {
            // Check input connection points (both dot and label)
            for (ResourceIdentifier input : node.getInputs()) {
                NodeViewModel.ConnectionPointPosition pos = node.getInputConnectionPoint(input);
                if (pos != null) {
                    // Check connection point dot
                    double distance = Math.sqrt(Math.pow(x - pos.x(), 2) + Math.pow(y - pos.y(), 2));
                    if (distance <= CONNECTION_POINT_RADIUS + 5) { // 5px tolerance
                        return new NodeConnectionPointInfo(node, input, true);
                    }
                    
                    // Check input label area (text is at pos.x() + 15, pos.y() + 4)
                    String label = getShortResourceName(input);
                    Text labelText = new Text(label);
                    labelText.setFont(Font.font("Arial", 10));
                    double labelWidth = labelText.getBoundsInLocal().getWidth();
                    double labelHeight = labelText.getBoundsInLocal().getHeight();
                    
                    double labelX = pos.x() + 15;
                    double labelY = pos.y() + 4;
                    
                    // Check if mouse is within label bounds
                    if (x >= labelX && x <= labelX + labelWidth && 
                        y >= labelY - labelHeight && y <= labelY + 4) {
                        return new NodeConnectionPointInfo(node, input, true);
                    }
                }
            }
            
            // Check output connection points (both dot and label)
            for (ResourceIdentifier output : node.getOutputs()) {
                NodeViewModel.ConnectionPointPosition pos = node.getOutputConnectionPoint(output);
                if (pos != null) {
                    // Check connection point dot
                    double distance = Math.sqrt(Math.pow(x - pos.x(), 2) + Math.pow(y - pos.y(), 2));
                    if (distance <= CONNECTION_POINT_RADIUS + 5) { // 5px tolerance
                        return new NodeConnectionPointInfo(node, output, false);
                    }
                    
                    // Check output label area (text is at pos.x() - labelWidth - 15, pos.y() + 4)
                    String label = getShortResourceName(output);
                    Text labelText = new Text(label);
                    labelText.setFont(Font.font("Arial", 10));
                    double labelWidth = labelText.getBoundsInLocal().getWidth();
                    double labelHeight = labelText.getBoundsInLocal().getHeight();
                    
                    double labelX = pos.x() - labelWidth - 15;
                    double labelY = pos.y() + 4;
                    
                    // Check if mouse is within label bounds
                    if (x >= labelX && x <= labelX + labelWidth && 
                        y >= labelY - labelHeight && y <= labelY + 4) {
                        return new NodeConnectionPointInfo(node, output, false);
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Get the actual input value for a node's input connection point.
     * Uses detailed InputResult context to show source type and directness.
     */
    private String getInputValue(NodeViewModel node, ResourceIdentifier resource) {
        if (model == null) {
            return "No model available";
        }
        
        // Get detailed input information from nodeEvaluationMap
        var nodeEvaluation = model.getEvaluationResult().nodeEvaluationMap().get(node.getNodePath());
        if (nodeEvaluation != null && nodeEvaluation.inputs().containsKey(resource)) {
            var inputResult = nodeEvaluation.inputs().get(resource);
            
            // Extract detailed context from InputResult
            String valueStr = formatResultValue(inputResult.value());
            String sourceType = inputResult.inputContext().sourceType().toString();
            
            // Check if input is direct or indirect
            String directnessInfo = "";
            if (inputResult.inputContext().isDirectInput().isPresent()) {
                boolean isDirect = inputResult.inputContext().isDirectInput().get();
                directnessInfo = isDirect ? " (direct)" : " (indirect)";
            }
            
            // Format source type for better readability
            String readableSourceType = formatInputSourceType(inputResult.inputContext().sourceType());
            
            return valueStr + directnessInfo + " via " + readableSourceType;
        }
        
        // Fallback: Check adhoc overrides
        String adhocValue = getAdhocOutputValue(resource);
        if (adhocValue != null) {
            return adhocValue + " (adhoc override)";
        }
        
        // Final fallback: try to find the value from dependency nodes
        String dependencyValue = getDependencyValue(resource);
        if (dependencyValue != null) {
            return dependencyValue + " (from dependency)";
        }
        
        return "No value available";
    }
    
    /**
     * Get adhoc output value for a resource.
     */
    private String getAdhocOutputValue(ResourceIdentifier resource) {
        if (model == null || model.getEvaluationResult().adhocOverride().isEmpty()) {
            return null;
        }
        
        var adhocOverride = model.getEvaluationResult().adhocOverride().get();
        for (var entry : adhocOverride.adhocOutputs().entrySet()) {
            if (entry.getKey().rid().equals(resource)) {
                return formatResultValue(entry.getValue());
            }
        }
        
        return null;
    }
    
    /**
     * Get direct input value for a node and resource.
     */
    private String getDirectInputValue(java.nio.file.Path nodePath, ResourceIdentifier resource) {
        if (model == null) {
            return null;
        }
        
        // In the new API, we need to use nodeEvaluationMap to access input data
        var nodeEvaluation = model.getEvaluationResult().nodeEvaluationMap().get(nodePath);
        if (nodeEvaluation != null && nodeEvaluation.inputs().containsKey(resource)) {
            return formatResultValue(nodeEvaluation.inputs().get(resource).value());
        }
        
        return null;
    }
    
    /**
     * Get conditional input value for a node and resource.
     */
    private String getConditionalInputValue(java.nio.file.Path nodePath, ResourceIdentifier resource) {
        if (model == null) {
            return null;
        }
        
        // In the new API, we only have nodeEvaluationMap - the distinction between direct and conditional 
        // is handled internally. Check if this resource is available as an input.
        var nodeEvaluation = model.getEvaluationResult().nodeEvaluationMap().get(nodePath);
        if (nodeEvaluation != null && nodeEvaluation.inputs().containsKey(resource)) {
            return formatResultValue(nodeEvaluation.inputs().get(resource).value());
        }
        
        return null;
    }
    
    /**
     * Get value from a dependency node that produces this resource.
     */
    private String getDependencyValue(ResourceIdentifier resource) {
        if (model == null) {
            return null;
        }
        
        // Look through all node evaluations to find one that produces this resource
        for (var entry : model.getEvaluationResult().nodeEvaluationMap().entrySet()) {
            Path nodePath = entry.getKey();
            var nodeEvaluation = entry.getValue();
            
            // Check if this node has the resource as an output
            if (nodeEvaluation.outputs().containsKey(resource)) {
                var outputResult = nodeEvaluation.outputs().get(resource);
                return formatResultValue(outputResult.value()) + " (from " + getNodeDisplayName(nodePath) + ")";
            }
        }
        
        // Also check in the top-level results map
        var results = model.getEvaluationResult().results();
        if (results.containsKey(resource)) {
            return formatResultValue(results.get(resource));
        }
        
        return null;
    }
    
    /**
     * Format a Result value for display.
     */
    private String formatResultValue(me.vincentzz.lang.Result.Result<Object> result) {
        if (result == null) {
            return "null";
        }
        
        return switch (result) {
            case me.vincentzz.lang.Result.Success<Object> success -> {
                Object data = success.get();
                if (data == null) {
                    yield "Success: null";
                }
                yield "Success: " + formatDataValue(data);
            }
            case me.vincentzz.lang.Result.Failure<Object> failure -> 
                "Error: " + failure.getException();
        };
    }
    
    /**
     * Format the actual data value for display.
     */
    private String formatDataValue(Object data) {
        if (data instanceof Number num) {
            return String.format("%.3f", num.doubleValue());
        } else if (data instanceof String str) {
            return str;
        } else {
            return data.toString();
        }
    }
    
    /**
     * Get display name for a node path.
     */
    private String getNodeDisplayName(java.nio.file.Path nodePath) {
        return nodePath.getFileName() != null ? 
               nodePath.getFileName().toString() : 
               nodePath.toString();
    }
    
    /**
     * Format InputSourceType for better readability.
     */
    private String formatInputSourceType(me.vincentzz.graph.model.input.InputSourceType sourceType) {
        return switch (sourceType) {
            case ByParentGroup -> "Parent Group";
            case ByResolve -> "Resolution";
            case ByFlywire -> "Flywire";
            case ByAdhocFlywire -> "Adhoc Flywire";
            case ByAdhoc -> "Adhoc Override";
        };
    }
    
    /**
     * Format OutputValueType for better readability.
     */
    private String formatOutputValueType(me.vincentzz.graph.model.output.OutputValueType outputType) {
        return switch (outputType) {
            case ByEvaluation -> "Evaluation";
            case ByAdhoc -> "Adhoc Override";
        };
    }
    
    /**
     * Get detailed input source information.
     */
    private String getDetailedInputSourceInfo(me.vincentzz.graph.model.input.InputSourceType sourceType, ResourceIdentifier resourceId) {
        return switch (sourceType) {
            case ByParentGroup -> "Inherited from parent node group";
            case ByResolve -> "Resolved through dependency graph";
            case ByFlywire -> "Connected via flywire";
            case ByAdhocFlywire -> "Connected via adhoc flywire";
            case ByAdhoc -> "Set via adhoc override";
        };
    }
    
    /**
     * Get detailed output source information.
     */
    private String getDetailedOutputSourceInfo(me.vincentzz.graph.model.output.OutputValueType outputType, ResourceIdentifier resourceId) {
        return switch (outputType) {
            case ByEvaluation -> "Computed through node evaluation";
            case ByAdhoc -> "Set via adhoc override";
        };
    }
    
    /**
     * Get value from a dependency node with detailed context information.
     */
    private String getDependencyValueWithContext(ResourceIdentifier resource) {
        if (model == null) {
            return null;
        }
        
        // Look through all node evaluations to find one that produces this resource
        for (var entry : model.getEvaluationResult().nodeEvaluationMap().entrySet()) {
            Path nodePath = entry.getKey();
            var nodeEvaluation = entry.getValue();
            
            // Check if this node has the resource as an output
            if (nodeEvaluation.outputs().containsKey(resource)) {
                var outputResult = nodeEvaluation.outputs().get(resource);
                String valueStr = formatResultValue(outputResult.value());
                String outputType = formatOutputValueType(outputResult.outputContext().resultType());
                String sourceInfo = getDetailedOutputSourceInfo(outputResult.outputContext().resultType(), resource);
                
                return valueStr + " via " + outputType + " from " + getNodeDisplayName(nodePath) + 
                       (sourceInfo != null ? " - " + sourceInfo : "");
            }
        }
        
        // Also check in the top-level results map
        var results = model.getEvaluationResult().results();
        if (results.containsKey(resource)) {
            return formatResultValue(results.get(resource)) + " (top-level result)";
        }
        
        return null;
    }
    
    /**
     * Render actual buttons horizontally at top right for edit operations.
     */
    private void renderEditButtons() {
        // Only show buttons in edit mode
        if (editModel == null) {
            return;
        }
        
        // Get the visible viewport bounds
        double viewportWidth = getViewportBounds().getWidth();
        double viewportHeight = getViewportBounds().getHeight();
        
        // Get current scroll position
        double scrollX = getHvalue() * Math.max(0, canvas.getWidth() - viewportWidth);
        double scrollY = getVvalue() * Math.max(0, canvas.getHeight() - viewportHeight);
        
        // Button dimensions
        double buttonWidth = 80;
        double buttonHeight = 30;
        double buttonSpacing = 10;
        double margin = 20;
        
        // Get selection info
        List<NodeViewModel> selectedNodes = nodes.stream().filter(NodeViewModel::isSelected).collect(java.util.stream.Collectors.toList());
        int selectedCount = selectedNodes.size();
        boolean canGroup = selectedCount >= 1;
        boolean canUngroup = selectedCount == 1 && selectedNodes.get(0).isNodeGroup();
        
        // Position buttons horizontally at top right (now 3 buttons)
        double startX = scrollX + viewportWidth - (buttonWidth * 3 + buttonSpacing * 2) - margin;
        double buttonY = scrollY + margin;
        
        // Draw Group button
        double groupButtonX = startX;
        Color groupBgColor = canGroup ? ColorScheme.NODE_BACKGROUND : ColorScheme.BACKGROUND_MEDIUM;
        Color groupTextColor = canGroup ? ColorScheme.TEXT_PRIMARY : ColorScheme.TEXT_SECONDARY;
        
        gc.setFill(groupBgColor);
        gc.fillRoundRect(groupButtonX, buttonY, buttonWidth, buttonHeight, 5, 5);
        gc.setStroke(ColorScheme.NODE_BORDER);
        gc.setLineWidth(1);
        gc.strokeRoundRect(groupButtonX, buttonY, buttonWidth, buttonHeight, 5, 5);
        
        gc.setFont(Font.font("Arial", FontWeight.NORMAL, 11));
        gc.setFill(groupTextColor);
        gc.fillText("Group", groupButtonX + 25, buttonY + 20);
        
        // Draw Ungroup button
        double ungroupButtonX = startX + buttonWidth + buttonSpacing;
        Color ungroupBgColor = canUngroup ? ColorScheme.NODE_BACKGROUND : ColorScheme.BACKGROUND_MEDIUM;
        Color ungroupTextColor = canUngroup ? ColorScheme.TEXT_PRIMARY : ColorScheme.TEXT_SECONDARY;
        
        gc.setFill(ungroupBgColor);
        gc.fillRoundRect(ungroupButtonX, buttonY, buttonWidth, buttonHeight, 5, 5);
        gc.setStroke(ColorScheme.NODE_BORDER);
        gc.setLineWidth(1);
        gc.strokeRoundRect(ungroupButtonX, buttonY, buttonWidth, buttonHeight, 5, 5);
        
        gc.setFont(Font.font("Arial", FontWeight.NORMAL, 11));
        gc.setFill(ungroupTextColor);
        gc.fillText("Ungroup", ungroupButtonX + 20, buttonY + 20);
        
        // Draw Reset button
        double resetButtonX = startX + (buttonWidth + buttonSpacing) * 2;
        Color resetBgColor = ColorScheme.NODE_BACKGROUND; // Always enabled
        Color resetTextColor = ColorScheme.TEXT_PRIMARY;
        
        gc.setFill(resetBgColor);
        gc.fillRoundRect(resetButtonX, buttonY, buttonWidth, buttonHeight, 5, 5);
        gc.setStroke(ColorScheme.NODE_BORDER);
        gc.setLineWidth(1);
        gc.strokeRoundRect(resetButtonX, buttonY, buttonWidth, buttonHeight, 5, 5);
        
        gc.setFont(Font.font("Arial", FontWeight.NORMAL, 11));
        gc.setFill(resetTextColor);
        gc.fillText("Reset", resetButtonX + 25, buttonY + 20);
    }
    
    /**
     * Render floating info panel in bottom right corner showing flywire and scope info.
     */
    private void renderFloatingInfoPanel() {
        if (model == null || currentPath == null) {
            return;
        }
        
        // Get flywire and scope information for current path
        List<String> flywireInfo = getFlywireInfo();
        List<String> scopeInfo = getScopeInfo();
        
        // Always show at least basic info
        if (flywireInfo.isEmpty() && scopeInfo.isEmpty()) {
            scopeInfo = new ArrayList<>();
            scopeInfo.add("Path: " + getNodeDisplayName(currentPath));
            
            // Check if current path is a NodeGroup
            boolean isNodeGroup = false;
            if (model != null) {
                NodeViewModel currentPathNode = model.getNodeViewModel(currentPath);
                isNodeGroup = currentPathNode != null && currentPathNode.isNodeGroup();
            }
            scopeInfo.add("Type: " + (isNodeGroup ? "NodeGroup" : "AtomicNode"));
        }
        
        // Calculate panel dimensions
        double maxTextWidth = calculateMaxTextWidth(flywireInfo, scopeInfo);
        double panelWidth = Math.min(maxTextWidth + INFO_PANEL_PADDING * 2, INFO_PANEL_WIDTH);
        
        // Calculate total lines
        int totalLines = 0;
        if (!flywireInfo.isEmpty()) {
            totalLines += 1 + flywireInfo.size(); // "FLYWIRES:" + lines
        }
        if (!scopeInfo.isEmpty()) {
            totalLines += 1 + scopeInfo.size(); // "SCOPE:" + lines
        }
        if (!flywireInfo.isEmpty() && !scopeInfo.isEmpty()) {
            totalLines += 1; // Empty line between sections
        }
        
        double panelHeight = Math.min(totalLines * INFO_PANEL_LINE_HEIGHT + INFO_PANEL_PADDING * 2, INFO_PANEL_MAX_HEIGHT);
        
        // Position panel in bottom right corner of the visible viewport, not the full canvas
        // Get the visible viewport bounds
        double viewportWidth = getViewportBounds().getWidth();
        double viewportHeight = getViewportBounds().getHeight();
        
        // Get current scroll position
        double scrollX = getHvalue() * Math.max(0, canvas.getWidth() - viewportWidth);
        double scrollY = getVvalue() * Math.max(0, canvas.getHeight() - viewportHeight);
        
        // Position panel in the bottom right of the visible area
        double panelX = scrollX + viewportWidth - panelWidth - INFO_PANEL_MARGIN;
        double panelY = scrollY + viewportHeight - panelHeight - INFO_PANEL_MARGIN;
        
        // Ensure panel doesn't go outside canvas bounds
        panelX = Math.max(INFO_PANEL_MARGIN, Math.min(panelX, canvas.getWidth() - panelWidth - INFO_PANEL_MARGIN));
        panelY = Math.max(INFO_PANEL_MARGIN, Math.min(panelY, canvas.getHeight() - panelHeight - INFO_PANEL_MARGIN));
        
        // Draw panel background
        gc.setFill(ColorScheme.BACKGROUND_MEDIUM.deriveColor(0, 0, 0, 0.9)); // Semi-transparent
        gc.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 6, 6);
        
        // Draw panel border
        gc.setStroke(ColorScheme.NODE_BORDER);
        gc.setLineWidth(1);
        gc.strokeRoundRect(panelX, panelY, panelWidth, panelHeight, 6, 6);
        
        // Draw content
        double textX = panelX + INFO_PANEL_PADDING;
        double textY = panelY + INFO_PANEL_PADDING + 12; // 12 for font baseline
        
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        gc.setFill(ColorScheme.TEXT_PRIMARY);
        
        // Draw flywire info
        if (!flywireInfo.isEmpty()) {
            gc.fillText("FLYWIRES:", textX, textY);
            textY += INFO_PANEL_LINE_HEIGHT;
            
            gc.setFont(Font.font("Arial", 9));
            gc.setFill(ColorScheme.TEXT_SECONDARY);
            
            for (String flywire : flywireInfo) {
                gc.fillText("  " + flywire, textX, textY);
                textY += INFO_PANEL_LINE_HEIGHT;
            }
            
            if (!scopeInfo.isEmpty()) {
                textY += INFO_PANEL_LINE_HEIGHT; // Empty line
            }
        }
        
        // Draw scope info
        if (!scopeInfo.isEmpty()) {
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 10));
            gc.setFill(ColorScheme.TEXT_PRIMARY);
            gc.fillText("SCOPE:", textX, textY);
            textY += INFO_PANEL_LINE_HEIGHT;
            
            gc.setFont(Font.font("Arial", 9));
            gc.setFill(ColorScheme.TEXT_SECONDARY);
            
            for (String scope : scopeInfo) {
                gc.fillText("  " + scope, textX, textY);
                textY += INFO_PANEL_LINE_HEIGHT;
            }
        }
    }
    
    /**
     * Get flywire information for the current node group directly from NodeGroup's flywires() method.
     */
    private List<String> getFlywireInfo() {
        List<String> flywireInfo = new ArrayList<>();
        
        if (model == null) {
            return flywireInfo;
        }
        
        try {
            String currentPathStr = currentPath.toString();
            var graph = model.getEvaluationResult().graph();
            
            // Extract actual flywire information by calling flywires() on NodeGroup objects
            var flywires = extractFlywireFromNodeGroup(graph, currentPathStr);
            
            if (flywires != null && !flywires.isEmpty()) {
                for (var flywire : flywires) {
                    // Create detailed flywire information
                    flywireInfo.add("SOURCE:");
                    flywireInfo.add("  Path: " + flywire.source().nodePath().toString());
                    flywireInfo.add("  Resource: " + formatResourceDetails(flywire.source().rid()));
                    
                    flywireInfo.add("TARGET:");
                    flywireInfo.add("  Path: " + flywire.target().nodePath().toString());
                    flywireInfo.add("  Resource: " + formatResourceDetails(flywire.target().rid()));
                    
                    // Add separator between flywires if there are more
                    flywireInfo.add("---");
                }
                
                // Remove trailing separator if present
                if (!flywireInfo.isEmpty() && flywireInfo.get(flywireInfo.size() - 1).equals("---")) {
                    flywireInfo.remove(flywireInfo.size() - 1);
                }
            }
            
        } catch (Exception e) {
            // Fallback: show error info
            flywireInfo.add("Error extracting flywires: " + e.getMessage());
        }
        
        return flywireInfo;
    }
    
    /**
     * Extract flywires from NodeGroup by calling flywires() method recursively.
     */
    private java.util.Set<me.vincentzz.graph.node.Flywire> extractFlywireFromNodeGroup(Object computedSubGraph, String targetPath) {
        if (!(computedSubGraph instanceof me.vincentzz.graph.node.CalculationNode)) {
            return null;
        }
        
        var calculationNode = (me.vincentzz.graph.node.CalculationNode) computedSubGraph;
        
        // If this is a NodeGroup, check if it matches our target path
        if (calculationNode instanceof me.vincentzz.graph.node.NodeGroup) {
            var nodeGroup = (me.vincentzz.graph.node.NodeGroup) calculationNode;
            
            // Check if this is the target NodeGroup
            if (matchesTargetPath(nodeGroup, targetPath)) {
                return nodeGroup.flywires();
            }
            
            // Recursively search in child nodes and collect all flywires
            java.util.Set<me.vincentzz.graph.node.Flywire> allFlywires = new java.util.HashSet<>();
            
            // Add flywires from this NodeGroup
            allFlywires.addAll(nodeGroup.flywires());
            
            // Recursively search in child nodes
            for (var childNode : nodeGroup.nodes()) {
                var childFlywires = extractFlywireFromNodeGroup(childNode, targetPath);
                if (childFlywires != null) {
                    allFlywires.addAll(childFlywires);
                }
            }
            
            // Filter flywires that are relevant to the target path
            if (!allFlywires.isEmpty()) {
                return allFlywires.stream()
                    .filter(flywire -> isFlywireRelevantToPath(flywire, targetPath))
                    .collect(java.util.stream.Collectors.toSet());
            }
        }
        
        return null;
    }
    
    /**
     * Check if a flywire is relevant to the target path (involves the path or its children).
     */
    private boolean isFlywireRelevantToPath(me.vincentzz.graph.node.Flywire flywire, String targetPath) {
        String sourcePath = flywire.source().nodePath().toString();
        String targetFlywirePath = flywire.target().nodePath().toString();
        
        // Include flywires that start or end at current path or its children
        return sourcePath.startsWith(targetPath) || targetFlywirePath.startsWith(targetPath) ||
               targetPath.startsWith(sourcePath) || targetPath.startsWith(targetFlywirePath);
    }
    
    /**
     * Get scope information for the current node group directly from NodeGroup's actual scope configuration.
     */
    private List<String> getScopeInfo() {
        List<String> scopeInfo = new ArrayList<>();
        
        if (model == null) {
            return scopeInfo;
        }
        
        try {
            String currentPathStr = currentPath.toString();
            var graph = model.getEvaluationResult().graph();
            
            // Extract actual scope information by calling exports() on NodeGroup objects
            var scope = extractScopeFromNodeGroup(graph, currentPathStr);
            
            if (scope != null) {
                // Determine scope type and resources
                if (scope instanceof me.vincentzz.graph.scope.Include) {
                    @SuppressWarnings("unchecked")
                    var includeScope = (me.vincentzz.graph.scope.Include<me.vincentzz.graph.node.ConnectionPoint>) scope;
                    var resources = includeScope.resources();
                    
                    scopeInfo.add("Type: Include");
                    if (!resources.isEmpty()) {
                        scopeInfo.add("Resources: " + resources.size() + " included");
                        
                        // Show first few scope entries
                        int displayCount = Math.min(resources.size(), 4);
                        int count = 0;
                        for (var connectionPoint : resources) {
                            if (count >= displayCount) break;
                            String resourceInfo = formatConnectionPointInfo(connectionPoint);
                            scopeInfo.add("  " + resourceInfo);
                            count++;
                        }
                        
                        if (resources.size() > displayCount) {
                            scopeInfo.add("  ... and " + (resources.size() - displayCount) + " more");
                        }
                    } else {
                        scopeInfo.add("Resources: Empty include scope");
                        scopeInfo.add("  (No outputs exported)");
                    }
                    
                } else if (scope instanceof me.vincentzz.graph.scope.Exclude) {
                    @SuppressWarnings("unchecked")
                    var excludeScope = (me.vincentzz.graph.scope.Exclude<me.vincentzz.graph.node.ConnectionPoint>) scope;
                    var resources = excludeScope.resources();
                    
                    scopeInfo.add("Type: Exclude");
                    if (!resources.isEmpty()) {
                        scopeInfo.add("Resources: " + resources.size() + " excluded");
                        
                        // Show first few scope entries
                        int displayCount = Math.min(resources.size(), 4);
                        int count = 0;
                        for (var connectionPoint : resources) {
                            if (count >= displayCount) break;
                            String resourceInfo = formatConnectionPointInfo(connectionPoint);
                            scopeInfo.add("  " + resourceInfo);
                            count++;
                        }
                        
                        if (resources.size() > displayCount) {
                            scopeInfo.add("  ... and " + (resources.size() - displayCount) + " more");
                        }
                    } else {
                        scopeInfo.add("Resources: Empty exclude scope");
                        scopeInfo.add("  (All outputs exported)");
                    }
                }
            } else {
                // Fallback to showing basic information
                NodeViewModel currentPathNode = model.getNodeViewModel(currentPath);
                if (currentPathNode != null && currentPathNode.isNodeGroup()) {
                    scopeInfo.add("Type: NodeGroup");
                    
                    List<ResourceIdentifier> pathOutputs = getPathOutputs();
                    if (!pathOutputs.isEmpty()) {
                        scopeInfo.add("Exports: " + pathOutputs.size() + " resources");
                        for (ResourceIdentifier output : pathOutputs.stream().limit(3).toList()) {
                            scopeInfo.add("  " + getShortResourceName(output));
                        }
                        if (pathOutputs.size() > 3) {
                            scopeInfo.add("  ... and " + (pathOutputs.size() - 3) + " more");
                        }
                    }
                } else {
                    scopeInfo.add("Type: AtomicNode");
                    scopeInfo.add("Scope: N/A (atomic nodes don't have export scope)");
                }
            }
            
        } catch (Exception e) {
            // Fallback: show minimal scope info
            scopeInfo.add("Scope: " + getNodeDisplayName(currentPath));
            scopeInfo.add("Error: " + e.getMessage());
        }
        
        return scopeInfo;
    }
    
    /**
     * Get path output value for display in tooltips.
     * Uses detailed OutputResult context to show comprehensive output information.
     */
    private String getPathOutputValue(ResourceIdentifier resourceId) {
        if (model == null) {
            return "No model available";
        }
        
        // Check if current path has evaluation results for this resource
        if (currentPath != null) {
            var nodeEvaluationMap = model.getEvaluationResult().nodeEvaluationMap();
            var currentPathEvaluation = nodeEvaluationMap.get(currentPath);
            if (currentPathEvaluation != null && currentPathEvaluation.outputs().containsKey(resourceId)) {
                var outputResult = currentPathEvaluation.outputs().get(resourceId);
                
                // Extract detailed context from OutputResult
                String valueStr = formatResultValue(outputResult.value());
                String resultType = formatOutputValueType(outputResult.outputContext().resultType());
                
                // Provide detailed output source information
                String sourceInfo = getDetailedOutputSourceInfo(outputResult.outputContext().resultType(), resourceId);
                
                return valueStr + " via " + resultType + 
                       (sourceInfo != null ? " - " + sourceInfo : "");
            }
        }
        
        // Look through child nodes to find the value with detailed context
        String dependencyValue = getDependencyValueWithContext(resourceId);
        if (dependencyValue != null) {
            return dependencyValue;
        }
        
        return "Not computed";
    }
    
    /**
     * Extract scope from NodeGroup by calling exports() method recursively.
     */
    private me.vincentzz.graph.scope.Scope<me.vincentzz.graph.node.ConnectionPoint> extractScopeFromNodeGroup(Object computedSubGraph, String targetPath) {
        if (!(computedSubGraph instanceof me.vincentzz.graph.node.CalculationNode)) {
            return null;
        }
        
        var calculationNode = (me.vincentzz.graph.node.CalculationNode) computedSubGraph;
        
        // If this is a NodeGroup, check if it matches our target path
        if (calculationNode instanceof me.vincentzz.graph.node.NodeGroup) {
            var nodeGroup = (me.vincentzz.graph.node.NodeGroup) calculationNode;
            
            // Check if this is the target NodeGroup
            if (matchesTargetPath(nodeGroup, targetPath)) {
                return nodeGroup.exports();
            }
            
            // Recursively search in child nodes
            for (var childNode : nodeGroup.nodes()) {
                var result = extractScopeFromNodeGroup(childNode, targetPath);
                if (result != null) {
                    return result;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Extract inputs from CalculationNode by calling inputs() method recursively,
     * and comprehensively include all conditional dependencies.
     */
    private java.util.Set<ResourceIdentifier> extractInputsFromCalculationNode(Object computedSubGraph, String targetPath) {
        if (!(computedSubGraph instanceof me.vincentzz.graph.node.CalculationNode)) {
            return null;
        }
        
        var calculationNode = (me.vincentzz.graph.node.CalculationNode) computedSubGraph;
        
        // Check if this node matches our target path
        if (matchesTargetPath(calculationNode, targetPath)) {
            java.util.Set<ResourceIdentifier> allInputs = new java.util.HashSet<>();
            
            // First, get domain object inputs if available
            try {
                java.util.Set<ResourceIdentifier> domainInputs = calculationNode.inputs();
                if (domainInputs != null && !domainInputs.isEmpty()) {
                    allInputs.addAll(domainInputs);
                }
            } catch (Exception e) {
                System.err.println("Failed to extract inputs from domain object for " + targetPath + ": " + e.getMessage());
            }
            
            // Always add inputs from nodeEvaluationMap to ensure comprehensive coverage
            if (model != null) {
                try {
                    java.nio.file.Path pathAsPath = java.nio.file.Paths.get(targetPath);
                    
                    // Add ALL inputs from nodeEvaluationMap (using new API)
                    var nodeEvaluation = model.getEvaluationResult().nodeEvaluationMap().get(pathAsPath);
                    if (nodeEvaluation != null && nodeEvaluation.inputs() != null) {
                        allInputs.addAll(nodeEvaluation.inputs().keySet());
                    }
                    
                } catch (Exception e) {
                    System.err.println("Failed to extract evaluation inputs for " + targetPath + ": " + e.getMessage());
                }
            }
            
            return allInputs;
        }
        
        // If this is a NodeGroup, recursively search in child nodes
        if (calculationNode instanceof me.vincentzz.graph.node.NodeGroup) {
            var nodeGroup = (me.vincentzz.graph.node.NodeGroup) calculationNode;
            
            for (var childNode : nodeGroup.nodes()) {
                var result = extractInputsFromCalculationNode(childNode, targetPath);
                if (result != null) {
                    return result;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Extract outputs from CalculationNode by calling outputs() method recursively.
     */
    private java.util.Set<ResourceIdentifier> extractOutputsFromCalculationNode(Object computedSubGraph, String targetPath) {
        if (!(computedSubGraph instanceof me.vincentzz.graph.node.CalculationNode)) {
            return null;
        }
        
        var calculationNode = (me.vincentzz.graph.node.CalculationNode) computedSubGraph;
        
        // Check if this node matches our target path
        if (matchesTargetPath(calculationNode, targetPath)) {
            return calculationNode.outputs();
        }
        
        // If this is a NodeGroup, recursively search in child nodes
        if (calculationNode instanceof me.vincentzz.graph.node.NodeGroup) {
            var nodeGroup = (me.vincentzz.graph.node.NodeGroup) calculationNode;
            
            for (var childNode : nodeGroup.nodes()) {
                var result = extractOutputsFromCalculationNode(childNode, targetPath);
                if (result != null) {
                    return result;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Check if a CalculationNode matches the target path.
     */
    private boolean matchesTargetPath(me.vincentzz.graph.node.CalculationNode calculationNode, String targetPath) {
        // For NodeGroup, get the name
        if (calculationNode instanceof me.vincentzz.graph.node.NodeGroup) {
            var nodeGroup = (me.vincentzz.graph.node.NodeGroup) calculationNode;
            String nodeName = nodeGroup.name();
            
            // Simple path matching - could be enhanced for more complex path resolution
            if ("/root".equals(targetPath) && "root".equals(nodeName)) {
                return true;
            }
            
            return targetPath.endsWith("/" + nodeName) || targetPath.equals("/" + nodeName);
        }
        
        // For AtomicNode, try to extract name from toString or use a different approach
        // This is a simplified approach - you might need to enhance based on actual AtomicNode structure
        String nodeString = calculationNode.toString();
        return nodeString.contains(targetPath) || targetPath.equals("/root");
    }
    
    /**
     * Check if a NodeGroup matches the target path.
     */
    private boolean matchesTargetPath(me.vincentzz.graph.node.NodeGroup nodeGroup, String targetPath) {
        String nodeName = nodeGroup.name();
        
        // Simple path matching - could be enhanced for more complex path resolution
        if ("/root".equals(targetPath) && "root".equals(nodeName)) {
            return true;
        }
        
        return targetPath.endsWith("/" + nodeName) || targetPath.equals("/" + nodeName);
    }
    
    /**
     * Format connection point information for display.
     */
    private String formatConnectionPointInfo(me.vincentzz.graph.node.ConnectionPoint connectionPoint) {
        String nodePath = connectionPoint.nodePath().toString();
        String resourceInfo = formatResourceDetails(connectionPoint.rid());
        return nodePath + " → " + resourceInfo;
    }
    
    /**
     * Calculate the maximum text width needed for the info panel.
     */
    private double calculateMaxTextWidth(List<String> flywireInfo, List<String> scopeInfo) {
        double maxWidth = 0;
        
        // Check section headers
        Font headerFont = Font.font("Arial", FontWeight.BOLD, 10);
        if (!flywireInfo.isEmpty()) {
            Text headerText = new Text("FLYWIRES:");
            headerText.setFont(headerFont);
            maxWidth = Math.max(maxWidth, headerText.getBoundsInLocal().getWidth());
        }
        if (!scopeInfo.isEmpty()) {
            Text headerText = new Text("SCOPE:");
            headerText.setFont(headerFont);
            maxWidth = Math.max(maxWidth, headerText.getBoundsInLocal().getWidth());
        }
        
        // Check content lines
        Font contentFont = Font.font("Arial", 9);
        for (String line : flywireInfo) {
            Text lineText = new Text("  " + line);
            lineText.setFont(contentFont);
            maxWidth = Math.max(maxWidth, lineText.getBoundsInLocal().getWidth());
        }
        for (String line : scopeInfo) {
            Text lineText = new Text("  " + line);
            lineText.setFont(contentFont);
            maxWidth = Math.max(maxWidth, lineText.getBoundsInLocal().getWidth());
        }
        
        return maxWidth;
    }
    
    /**
     * Format resource details for comprehensive display in floating panel.
     */
    private String formatResourceDetails(ResourceIdentifier resourceId) {
        String resourceString = resourceId.toString();
        
        // Handle FalconResourceId format comprehensively
        if (resourceString.contains("FalconResourceId[")) {
            StringBuilder details = new StringBuilder();
            
            // Extract IFO
            String ifo = extractValue(resourceString, "ifo=");
            if (ifo != null) {
                details.append("IFO=").append(ifo);
            }
            
            // Extract Source
            String source = extractValue(resourceString, "source=");
            if (source != null) {
                if (details.length() > 0) details.append(", ");
                details.append("Source=").append(source);
            }
            
            // Extract Attribute
            String attribute = extractValue(resourceString, "attribute=");
            if (attribute != null) {
                if (details.length() > 0) details.append(", ");
                
                // Clean up class attribute format
                if (attribute.startsWith("class ")) {
                    String className = attribute.substring("class ".length());
                    int lastDot = className.lastIndexOf('.');
                    if (lastDot != -1) {
                        attribute = className.substring(lastDot + 1);
                    } else {
                        attribute = className;
                    }
                }
                details.append("Attr=").append(attribute);
            }
            
            return details.toString();
        }
        
        // Fallback for other resource types
        return resourceString;
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
