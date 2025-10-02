package me.vincentzz.visual.controller;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import me.vincentzz.graph.json.ConstructionalJsonUtil;
import me.vincentzz.visual.model.VisualizationModel;
import me.vincentzz.visual.view.CalculationCanvas;
import me.vincentzz.visual.view.InfoDisplayPane;
import me.vincentzz.visual.view.PathNavigationBar;
import me.vincentzz.visual.util.ColorScheme;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Main controller for the visual calculation application.
 * Coordinates between the model, view components, and user interactions.
 */
public class MainController {
    
    private VisualizationModel model;
    private PathNavigationBar navigationBar;
    private CalculationCanvas canvas;
    private InfoDisplayPane infoDisplayPane;
    private Label statusLabel;
    
    private BorderPane rootPane;
    
    public MainController() {
        initializeComponents();
    }
    
    private void initializeComponents() {
        // Create view components
        navigationBar = new PathNavigationBar();
        canvas = new CalculationCanvas();
        infoDisplayPane = new InfoDisplayPane();
        statusLabel = new Label("Ready - Open an EvaluationResult JSON file to begin");
        
        // Setup event handlers
        setupEventHandlers();
        
        // Create root layout
        rootPane = new BorderPane();
        setupLayout();
        
        // Style the status label
        statusLabel.setTextFill(ColorScheme.TEXT_SECONDARY);
        statusLabel.setStyle("-fx-padding: 5px; -fx-font-size: 12px;");
    }
    
    private void setupEventHandlers() {
        // Navigation bar events
        navigationBar.setOnPathChanged(this::handlePathNavigation);
        navigationBar.setOnSegmentClicked(this::handlePathSegmentClick);
        navigationBar.setRightButton("Edit", this::handleEditButtonClick);
        
        // Canvas events
        canvas.setOnNodeClicked(this::handleNodeClick);
        canvas.setOnNodeDoubleClicked(this::handleNodeDoubleClick);
        canvas.setOnNodeHovered(this::handleNodeHover);
        canvas.setOnEditCompleted(this::handleEditCompleted);
    }
    
    private void setupLayout() {
        // Top: Navigation bar
        rootPane.setTop(navigationBar);
        
        // Create a vertical split pane for canvas and info display
        SplitPane mainSplitPane = new SplitPane();
        mainSplitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        
        // Top part: Canvas
        mainSplitPane.getItems().add(canvas);
        
        // Bottom part: Info display and status bar
        VBox bottomPanel = new VBox();
        bottomPanel.getChildren().addAll(infoDisplayPane, statusLabel);
        bottomPanel.setStyle("-fx-background-color: " + toHexString(ColorScheme.NAV_BACKGROUND) + ";");
        
        // Set fixed height for status bar and let InfoDisplayPane take remaining space
        statusLabel.setPrefHeight(25);
        statusLabel.setMinHeight(25);
        statusLabel.setMaxHeight(25);
        VBox.setVgrow(infoDisplayPane, Priority.ALWAYS);
        VBox.setVgrow(statusLabel, Priority.NEVER);
        
        mainSplitPane.getItems().add(bottomPanel);
        
        // Set initial divider position (80% canvas, 20% info panel)
        mainSplitPane.setDividerPositions(0.8);
        
        // Center: Main split pane
        rootPane.setCenter(mainSplitPane);
        
        // Apply dark theme
        rootPane.setStyle("-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_DARK) + ";");
    }
    
    /**
     * Load an EvaluationResult from a JSON file.
     */
    public void loadEvaluationResult(File file) {
        setStatus("Loading file: " + file.getName() + "...");
        
        // Load asynchronously to avoid blocking the UI
        CompletableFuture.supplyAsync(() -> {
            try {
                String json = Files.readString(file.toPath());
                return ConstructionalJsonUtil.fromJsonEvaluationResult(json).get();
            } catch (Exception e) {
                throw new RuntimeException("Failed to load EvaluationResult from file", e);
            }
        }).thenAcceptAsync(evaluationResult -> {
            // Update UI on JavaFX Application Thread
            Platform.runLater(() -> {
                try {
                    setModel(new VisualizationModel(evaluationResult));
                    setStatus("Loaded: " + file.getName() + " - Showing " + 
                             model.getNodesForCurrentPath().size() + " nodes");
                } catch (Exception e) {
                    setStatus("Error processing file: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }).exceptionally(throwable -> {
            Platform.runLater(() -> {
                setStatus("Error loading file: " + throwable.getCause().getMessage());
                throwable.printStackTrace();
            });
            return null;
        });
    }
    
    /**
     * Set the visualization model and update all views.
     */
    private void setModel(VisualizationModel model) {
        this.model = model;
        
        // Update info display pane with the EvaluationResult
        if (model != null) {
            infoDisplayPane.updateEvaluationResult(model.getEvaluationResult());
        } else {
            infoDisplayPane.clear();
        }
        
        updateAllViews();
    }
    
    /**
     * Update all view components to reflect the current model state.
     */
    private void updateAllViews() {
        if (model == null) {
            return;
        }
        
        // Update navigation bar
        navigationBar.setPathSegments(model.getPathSegments());
        
        // Update canvas - set model reference first so it can access correct node information
        canvas.setModel(model);
        canvas.setCurrentPath(model.getCurrentPath());
        canvas.setNodes(model.getNodesForCurrentPath());
        canvas.setConnections(model.getConnectionsForCurrentPath());
        canvas.refresh();
    }
    
    // Event handlers
    private void handlePathNavigation(Path newPath) {
        if (model != null) {
            model.navigateToPath(newPath);
            updateAllViews();
            setStatus("Navigated to: " + newPath);
        }
    }
    
    private void handlePathSegmentClick(int segmentIndex) {
        if (model != null) {
            // Handle root navigation (home button)
            if (segmentIndex == 0) {
                model.navigateToPath(model.getEvaluationResult().requestedNodePath());
                updateAllViews();
                setStatus("Navigated to root");
                return;
            }
            
            // Build path up to the clicked segment
            var segments = model.getPathSegments();
            if (segmentIndex > 0 && segmentIndex < segments.size()) {
                // Build path by going up from current path
                Path currentPath = model.getCurrentPath();
                int stepsUp = segments.size() - segmentIndex;
                
                Path targetPath = currentPath;
                for (int i = 0; i < stepsUp; i++) {
                    if (targetPath.getParent() != null) {
                        targetPath = targetPath.getParent();
                    }
                }
                
                model.navigateToPath(targetPath);
                updateAllViews();
                setStatus("Navigated to: " + targetPath);
            }
        }
    }
    
    private void handleNodeClick(Path nodePath) {
        if (model != null) {
            var nodeViewModel = model.getNodeViewModel(nodePath);
            if (nodeViewModel != null) {
                setStatus("Selected node: " + nodeViewModel.getDisplayName());
                // Could implement node selection highlighting here
            }
        }
    }
    
    private void handleNodeDoubleClick(Path nodePath) {
        if (model != null && model.canNavigateToChild(nodePath.getFileName().toString())) {
            model.navigateToChild(nodePath.getFileName().toString());
            updateAllViews();
            setStatus("Navigated into: " + nodePath.getFileName());
        }
    }
    
    private void handleNodeHover(Path nodePath, String details) {
        if (nodePath != null) {
            setStatus("Hovering: " + details);
        } else {
            setStatus("Ready");
        }
    }
    
    /**
     * Handle edit button click to open the EditNodeWindow.
     */
    private void handleEditButtonClick() {
        if (model == null) {
            setStatus("No model loaded - cannot edit");
            return;
        }
        
        try {
            // Extract components from the evaluation result
            var evaluationResult = model.getEvaluationResult();
            var graph = evaluationResult.graph();
            var currentPath = model.getCurrentPath();
            var snapshot = evaluationResult.snapshot();
            var requestedNodePath = evaluationResult.requestedNodePath();
            var adhocOverride = evaluationResult.adhocOverride();
            
            // Extract requested resources from the results map (these were the originally requested resources)
            var requestedResources = evaluationResult.results().keySet();
            
            me.vincentzz.visual.view.EditNodeWindow editWindow = 
                new me.vincentzz.visual.view.EditNodeWindow(
                    graph, currentPath, snapshot, requestedNodePath, adhocOverride, requestedResources);
            
            // Set event handlers for the edit window
            editWindow.setOnRunCompleted(this::handleEditCompleted);
            editWindow.setOnCancel(() -> {
                setStatus("Edit cancelled");
            });
            
            editWindow.show();
            setStatus("Opened edit window for: " + model.getCurrentPath());
            
        } catch (Exception e) {
            setStatus("Failed to open edit window: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Handle the completion of an edit operation with a new EvaluationResult.
     */
    private void handleEditCompleted(me.vincentzz.graph.model.EvaluationResult newEvaluationResult) {
        if (newEvaluationResult != null) {
            try {
                // Create a new model with the updated evaluation result
                VisualizationModel newModel = new VisualizationModel(newEvaluationResult);
                
                // Preserve the current path if possible
                Path currentPath = model != null ? model.getCurrentPath() : newEvaluationResult.requestedNodePath();
                
                // Update the model
                setModel(newModel);
                
                // Navigate to the preserved path if it exists in the new model
                if (currentPath != null) {
                    try {
                        model.navigateToPath(currentPath);
                        updateAllViews();
                        setStatus("Edit completed - Updated graph with new results");
                    } catch (Exception e) {
                        // If navigation fails, go to root
                        model.navigateToPath(newEvaluationResult.requestedNodePath());
                        updateAllViews();
                        setStatus("Edit completed - Updated graph (navigated to root due to path change)");
                    }
                } else {
                    setStatus("Edit completed - Updated graph with new results");
                }
                
            } catch (Exception e) {
                setStatus("Error updating graph: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    private void setStatus(String message) {
        statusLabel.setText(message);
    }
    
    /**
     * Create the main layout for the application.
     */
    public BorderPane createLayout() {
        return rootPane;
    }
    
    /**
     * Shutdown the controller and clean up resources.
     */
    public void shutdown() {
        // Clean up any background tasks or resources
        if (canvas != null) {
            canvas.shutdown();
        }
        if (infoDisplayPane != null) {
            infoDisplayPane.clear();
        }
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
