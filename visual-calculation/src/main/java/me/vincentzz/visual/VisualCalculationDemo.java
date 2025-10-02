package me.vincentzz.visual;

import javafx.application.Application;

/**
 * Demo class to launch the Visual Calculation application.
 * This provides an easy way to test the application with sample data.
 */
public class VisualCalculationDemo {

    public static void main(String[] args) {
        System.out.println("Starting Falcon Visual Calculation Demo...");
        System.out.println("This will open a JavaFX window where you can:");
        System.out.println("1. Load EvaluationResult JSON files");
        System.out.println("2. Navigate through the node hierarchy");
        System.out.println("3. Visualize calculation graphs with Unreal 5-style interface");
        System.out.println("4. View connection points with different colors for different resource types");
        System.out.println("5. See connections: Black=direct, Green=conditional, Blue=flywires");
        System.out.println();
        System.out.println("Sample file available at: visual-calculation/src/main/resources/sample-evaluation-result.json");
        System.out.println();

        // Launch the JavaFX application
        Application.launch(VisualCalculationApplication.class, args);
    }
}
