package me.vincentzz.visualnew;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import me.vincentzz.graph.model.EvaluationBundle;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.visual.util.ColorScheme;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

/**
 * Manages the TabPane, drag-and-drop file loading, and tab lifecycle.
 */
public class TabManager {

    private final TabPane tabPane;
    private Tab welcomeTab;

    public TabManager() {
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tabPane.setStyle("-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_DARK) + ";");

        // Listen for tab removal to show welcome tab when all user tabs are closed
        tabPane.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) change -> {
            while (change.next()) {
                if (change.wasRemoved()) {
                    // If only welcome tab remains or no tabs, ensure welcome is shown
                    if (tabPane.getTabs().isEmpty()) {
                        showWelcomeTab();
                    }
                }
            }
        });

        showWelcomeTab();
    }

    public TabPane getTabPane() {
        return tabPane;
    }

    /**
     * Setup drag-and-drop on the scene to accept JSON files.
     */
    public void setupDragAndDrop(Scene scene) {
        scene.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        scene.setOnDragDropped((DragEvent event) -> {
            var dragboard = event.getDragboard();
            boolean success = false;
            if (dragboard.hasFiles()) {
                for (File file : dragboard.getFiles()) {
                    if (file.getName().endsWith(".json")) {
                        openFile(file);
                        success = true;
                    }
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    /**
     * Open a JSON file asynchronously: detect its type and create the appropriate tab.
     */
    public void openFile(File file) {
        CompletableFuture.supplyAsync(() -> {
            try {
                String json = Files.readString(file.toPath());
                return JsonFileDetector.detect(json);
            } catch (Exception e) {
                return new JsonFileDetector.DetectionFailed("Failed to read file: " + e.getMessage());
            }
        }).thenAcceptAsync(result -> Platform.runLater(() -> {
            switch (result) {
                case JsonFileDetector.BundleDetected bd ->
                        openViewTab(bd.bundle(), file.getName());
                case JsonFileDetector.NodeDetected nd ->
                        openEditTab(nd.node(), file.getName());
                case JsonFileDetector.DetectionFailed df ->
                        openErrorTab(file.getName(), df.error());
            }
        })).exceptionally(throwable -> {
            Platform.runLater(() -> openErrorTab(file.getName(), throwable.getCause().getMessage()));
            return null;
        });
    }

    /**
     * Open a view mode tab for an EvaluationBundle.
     */
    public void openViewTab(EvaluationBundle bundle, String fileName) {
        removeWelcomeTab();
        ViewModeTab viewTab = new ViewModeTab(bundle, fileName, this);
        tabPane.getTabs().add(viewTab);
        tabPane.getSelectionModel().select(viewTab);
    }

    /**
     * Open an edit mode tab for a CalculationNode (no evaluation context).
     */
    public void openEditTab(CalculationNode node, String fileName) {
        removeWelcomeTab();
        EditModeTab editTab = new EditModeTab(node, fileName, this);
        tabPane.getTabs().add(editTab);
        tabPane.getSelectionModel().select(editTab);
    }

    /**
     * Open an edit mode tab with full evaluation context (from view mode Edit button).
     */
    public void openEditTab(CalculationNode graph, String fileName,
                            me.vincentzz.graph.model.Snapshot snapshot,
                            java.nio.file.Path requestedNodePath,
                            java.util.Optional<me.vincentzz.graph.model.AdhocOverride> adhocOverride,
                            java.util.Set<me.vincentzz.graph.model.ResourceIdentifier> requestedResources,
                            java.nio.file.Path currentViewPath) {
        removeWelcomeTab();
        EditModeTab editTab = new EditModeTab(graph, fileName, this,
                snapshot, requestedNodePath, adhocOverride, requestedResources, currentViewPath);
        tabPane.getTabs().add(editTab);
        tabPane.getSelectionModel().select(editTab);
    }

    /**
     * Replace an existing tab with a new view mode tab (used after edit→run).
     */
    public void replaceWithViewTab(Tab oldTab, EvaluationBundle bundle, String fileName) {
        int index = tabPane.getTabs().indexOf(oldTab);
        if (oldTab instanceof ViewModeTab vmt) {
            vmt.shutdown();
        } else if (oldTab instanceof EditModeTab emt) {
            emt.shutdown();
        }
        tabPane.getTabs().remove(oldTab);

        ViewModeTab viewTab = new ViewModeTab(bundle, fileName, this);
        if (index >= 0 && index <= tabPane.getTabs().size()) {
            tabPane.getTabs().add(index, viewTab);
        } else {
            tabPane.getTabs().add(viewTab);
        }
        tabPane.getSelectionModel().select(viewTab);
    }

    /**
     * Shutdown all tabs and clean up resources.
     */
    public void shutdownAll() {
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof ViewModeTab vmt) {
                vmt.shutdown();
            } else if (tab instanceof EditModeTab emt) {
                emt.shutdown();
            }
        }
    }

    private void showWelcomeTab() {
        if (welcomeTab != null && tabPane.getTabs().contains(welcomeTab)) {
            return;
        }
        welcomeTab = new Tab("Welcome");
        welcomeTab.setClosable(false);

        Label titleLabel = new Label("Falcon Visual Calculation");
        titleLabel.setStyle(
                "-fx-text-fill: " + toHexString(ColorScheme.TEXT_PRIMARY) + ";" +
                "-fx-font-size: 24px;" +
                "-fx-font-weight: bold;");

        Label instructionLabel = new Label("Drag and drop a JSON file here to open it");
        instructionLabel.setStyle(
                "-fx-text-fill: " + toHexString(ColorScheme.TEXT_SECONDARY) + ";" +
                "-fx-font-size: 14px;");

        Label detailLabel = new Label("EvaluationBundle JSON → View Mode  |  CalculationNode JSON → Edit Mode");
        detailLabel.setStyle(
                "-fx-text-fill: " + toHexString(ColorScheme.TEXT_MUTED) + ";" +
                "-fx-font-size: 12px;");

        VBox content = new VBox(15, titleLabel, instructionLabel, detailLabel);
        content.setAlignment(Pos.CENTER);
        content.setStyle("-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_DARK) + ";");

        welcomeTab.setContent(content);
        tabPane.getTabs().add(0, welcomeTab);
        tabPane.getSelectionModel().select(welcomeTab);
    }

    private void removeWelcomeTab() {
        if (welcomeTab != null) {
            tabPane.getTabs().remove(welcomeTab);
            welcomeTab = null;
        }
    }

    private void openErrorTab(String fileName, String error) {
        removeWelcomeTab();
        Tab errorTab = new Tab(fileName + " [Error]");
        errorTab.setClosable(true);

        Label errorLabel = new Label("Failed to load: " + fileName + "\n\n" + error);
        errorLabel.setStyle(
                "-fx-text-fill: #FF6666;" +
                "-fx-font-size: 13px;" +
                "-fx-padding: 20;");
        errorLabel.setWrapText(true);

        VBox content = new VBox(errorLabel);
        content.setAlignment(Pos.CENTER);
        content.setStyle("-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_DARK) + ";");

        errorTab.setContent(content);
        tabPane.getTabs().add(errorTab);
        tabPane.getSelectionModel().select(errorTab);
    }

    private String toHexString(javafx.scene.paint.Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }
}
