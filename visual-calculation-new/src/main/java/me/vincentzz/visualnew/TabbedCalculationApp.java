package me.vincentzz.visualnew;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * Multi-tab JavaFX application for visualizing EvaluationBundle and editing CalculationNode graphs.
 * Opens empty by default. Drag-and-drop JSON files to open new tabs.
 */
public class TabbedCalculationApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        NodeTypeRegistrar.registerAll();

        TabManager tabManager = new TabManager();

        BorderPane root = new BorderPane(tabManager.getTabPane());
        root.setStyle("-fx-background-color: #1E1E1E;");

        Scene scene = new Scene(root, 1400, 900);
        scene.getStylesheets().add(
                getClass().getResource("/styles.css").toExternalForm());
        scene.getStylesheets().add(
                getClass().getResource("/tab-styles.css").toExternalForm());

        tabManager.setupDragAndDrop(scene);

        primaryStage.setTitle("Falcon Visual Calculation");
        primaryStage.setScene(scene);
        primaryStage.setMaximized(true);
        primaryStage.setOnCloseRequest(e -> tabManager.shutdownAll());
        primaryStage.show();
    }

    public static void main(String[] args) {
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.text", "t2k");
        launch(args);
    }
}
