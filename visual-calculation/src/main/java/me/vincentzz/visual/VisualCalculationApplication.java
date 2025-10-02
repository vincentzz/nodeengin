package me.vincentzz.visual;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import me.vincentzz.falcon.ifo.FalconResourceId;
import me.vincentzz.falcon.node.AskProvider;
import me.vincentzz.falcon.node.BidProvider;
import me.vincentzz.falcon.node.HardcodeAttributeProvider;
import me.vincentzz.falcon.node.MidSpreadCalculator;
import me.vincentzz.graph.json.NodeTypeRegistry;
import me.vincentzz.graph.node.NodeGroup;
import me.vincentzz.visual.controller.MainController;
import me.vincentzz.visual.util.ColorScheme;

import java.io.File;

/**
 * Main JavaFX application for visualizing EvaluationResult JSON files.
 * Provides an Unreal 5-inspired interface for exploring calculation graphs.
 */
public class VisualCalculationApplication extends Application {
    
    private static final String TITLE = "Falcon Visual Calculation";
    private static final double MIN_WIDTH = 1200;
    private static final double MIN_HEIGHT = 800;
    
    private MainController mainController;
    
    @Override
    public void start(Stage primaryStage) {
        try {
            // Initialize the main controller
            mainController = new MainController();
            
            // Create the root layout
            BorderPane root = mainController.createLayout();
            
            // Create and configure the scene
            Scene scene = new Scene(root, MIN_WIDTH, MIN_HEIGHT);
            scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
            
            // Configure the primary stage
            primaryStage.setTitle(TITLE);
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(MIN_WIDTH);
            primaryStage.setMinHeight(MIN_HEIGHT);
            primaryStage.setMaximized(true); // Maximize to fill the screen
            
            // Setup window close handler
            primaryStage.setOnCloseRequest(event -> {
                mainController.shutdown();
            });
            
            // Show file chooser on startup
            showFileChooser(primaryStage);
            
            primaryStage.show();
            
        } catch (Exception e) {
            showErrorDialog("Application Startup Error", "Failed to start the application", e);
            e.printStackTrace();
        }
    }
    
    private void showFileChooser(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open EvaluationResult JSON File");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("JSON Files", "*.json"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        
        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile != null) {
            try {
                // Register node types
                NodeTypeRegistry.registerNodeType("NodeGroup", NodeGroup.class);
                NodeTypeRegistry.registerNodeType("AskProvider", AskProvider.class);
                NodeTypeRegistry.registerNodeType("BidProvider", BidProvider.class);
                NodeTypeRegistry.registerNodeType("MidSpreadCalculator", MidSpreadCalculator.class);
                NodeTypeRegistry.registerNodeType("HardcodeAttributeProvider", HardcodeAttributeProvider.class);

                // Register resource types
                NodeTypeRegistry.registerResourceType("FalconResourceId", FalconResourceId.class);
                mainController.loadEvaluationResult(selectedFile);
            } catch (Exception e) {
                showErrorDialog("File Load Error", "Failed to load the selected file", e);
                e.printStackTrace();
            }
        }
    }
    
    private void showErrorDialog(String title, String message, Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(message);
        alert.setContentText(e.getMessage());
        alert.showAndWait();
    }
    
    @Override
    public void stop() {
        if (mainController != null) {
            mainController.shutdown();
        }
    }
    
    public static void main(String[] args) {
        // Set system properties for better rendering
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.text", "t2k");
        
        launch(args);
    }
}
