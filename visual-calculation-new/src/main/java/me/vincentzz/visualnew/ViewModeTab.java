package me.vincentzz.visualnew;

import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import me.vincentzz.graph.model.EvaluationBundle;
import me.vincentzz.visual.model.VisualizationModel;
import me.vincentzz.visual.util.ColorScheme;
import me.vincentzz.visual.view.CalculationCanvas;
import me.vincentzz.visual.view.InfoDisplayPane;
import me.vincentzz.visual.view.PathNavigationBar;

import java.nio.file.Path;

/**
 * Tab content for viewing an EvaluationBundle.
 * Ports the MainController layout into a Tab.
 */
public class ViewModeTab extends Tab {

    private final TabManager tabManager;
    private final String fileName;

    private VisualizationModel model;
    private CalculationCanvas canvas;
    private PathNavigationBar navigationBar;
    private InfoDisplayPane infoDisplayPane;
    private Label statusLabel;

    public ViewModeTab(EvaluationBundle bundle, String fileName, TabManager tabManager) {
        this.tabManager = tabManager;
        this.fileName = fileName;

        setText(fileName);
        setClosable(true);

        initializeComponents();
        setContent(createLayout());
        loadBundle(bundle);

        setOnClosed(event -> shutdown());
    }

    private void initializeComponents() {
        navigationBar = new PathNavigationBar();
        canvas = new CalculationCanvas();
        infoDisplayPane = new InfoDisplayPane();
        statusLabel = new Label("Loading...");
        statusLabel.setTextFill(ColorScheme.TEXT_SECONDARY);
        statusLabel.setStyle("-fx-padding: 5px; -fx-font-size: 12px;");

        setupEventHandlers();
    }

    private void setupEventHandlers() {
        navigationBar.setOnPathChanged(this::handlePathNavigation);
        navigationBar.setOnSegmentClicked(this::handlePathSegmentClick);
        navigationBar.setRightButton("Edit", this::handleEditButtonClick);

        canvas.setOnNodeClicked(this::handleNodeClick);
        canvas.setOnNodeDoubleClicked(this::handleNodeDoubleClick);
        canvas.setOnNodeHovered(this::handleNodeHover);
    }

    private BorderPane createLayout() {
        BorderPane rootPane = new BorderPane();
        rootPane.setTop(navigationBar);

        SplitPane mainSplitPane = new SplitPane();
        mainSplitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        mainSplitPane.getItems().add(canvas);

        VBox bottomPanel = new VBox();
        bottomPanel.getChildren().addAll(infoDisplayPane, statusLabel);
        bottomPanel.setStyle("-fx-background-color: " + toHexString(ColorScheme.NAV_BACKGROUND) + ";");

        statusLabel.setPrefHeight(25);
        statusLabel.setMinHeight(25);
        statusLabel.setMaxHeight(25);
        VBox.setVgrow(infoDisplayPane, Priority.ALWAYS);
        VBox.setVgrow(statusLabel, Priority.NEVER);

        mainSplitPane.getItems().add(bottomPanel);
        mainSplitPane.setDividerPositions(0.8);

        rootPane.setCenter(mainSplitPane);
        rootPane.setStyle("-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_DARK) + ";");
        return rootPane;
    }

    private void loadBundle(EvaluationBundle bundle) {
        try {
            model = new VisualizationModel(bundle);
            infoDisplayPane.updateEvaluationResult(model.getEvaluationResult());
            updateAllViews();
            setStatus("Loaded: " + fileName + " - Showing " +
                    model.getNodesForCurrentPath().size() + " nodes");
        } catch (Exception e) {
            setStatus("Error processing file: " + e.getMessage());
        }
    }

    private void updateAllViews() {
        if (model == null) return;
        navigationBar.setPathSegments(model.getPathSegments());
        canvas.setModel(model);
        canvas.setCurrentPath(model.getCurrentPath());
        canvas.setNodes(model.getNodesForCurrentPath());
        canvas.setConnections(model.getConnectionsForCurrentPath());
        canvas.refresh();
    }

    // --- Event handlers ---

    private void handlePathNavigation(Path newPath) {
        if (model != null) {
            model.navigateToPath(newPath);
            updateAllViews();
            setStatus("Navigated to: " + newPath);
        }
    }

    private void handlePathSegmentClick(int segmentIndex) {
        if (model == null) return;
        if (segmentIndex == 0) {
            model.navigateToPath(model.getEvaluationResult().request().path());
            updateAllViews();
            setStatus("Navigated to root");
            return;
        }
        var segments = model.getPathSegments();
        if (segmentIndex > 0 && segmentIndex < segments.size()) {
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

    private void handleNodeClick(Path nodePath) {
        if (model != null) {
            var nodeViewModel = model.getNodeViewModel(nodePath);
            if (nodeViewModel != null) {
                setStatus("Selected node: " + nodeViewModel.getDisplayName());
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

    private void handleEditButtonClick() {
        if (model == null) {
            setStatus("No model loaded - cannot edit");
            return;
        }
        try {
            var evaluationResult = model.getEvaluationResult();
            tabManager.openEditTab(
                    model.getGraph(), fileName,
                    evaluationResult.request().snapshot(),
                    evaluationResult.request().path(),
                    evaluationResult.request().override(),
                    evaluationResult.request().rids(),
                    model.getCurrentPath());
        } catch (Exception e) {
            e.printStackTrace();
            setStatus("Error opening edit tab: " + e.getMessage());
        }
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    public void shutdown() {
        if (canvas != null) canvas.shutdown();
        if (infoDisplayPane != null) infoDisplayPane.clear();
    }

    private String toHexString(javafx.scene.paint.Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }
}
