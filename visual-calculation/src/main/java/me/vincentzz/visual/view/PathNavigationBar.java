package me.vincentzz.visual.view;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import me.vincentzz.visual.util.ColorScheme;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Navigation bar showing the current path with clickable breadcrumbs.
 * Styled similar to Unreal Engine's interface.
 */
public class PathNavigationBar extends HBox {
    
    private static final String SEPARATOR = " / ";
    
    private Consumer<Path> onPathChanged;
    private Consumer<Integer> onSegmentClicked;
    private Runnable onRightButtonClicked;
    private Button rightButton;
    private HBox rightButtonsContainer;
    
    public PathNavigationBar() {
        initializeComponent();
    }
    
    private void initializeComponent() {
        // Setup layout
        setSpacing(5);
        setPadding(new Insets(8, 12, 8, 12));
        setMinHeight(35);
        
        // Ensure all children are center-aligned vertically
        setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        
        // Apply styling
        setStyle(
            "-fx-background-color: " + toHexString(ColorScheme.NAV_BACKGROUND) + ";" +
            "-fx-border-color: " + toHexString(ColorScheme.NAV_BORDER) + ";" +
            "-fx-border-width: 0 0 1 0;"
        );
    }
    
    /**
     * Update the displayed path segments.
     */
    public void setPathSegments(List<String> segments) {
        getChildren().clear();
        
        if (segments.isEmpty()) {
            addLabel("No path", false);
            return;
        }
        
        // Add root indicator
        addSegmentButton("🏠", 0, true);
        
        // Add path segments
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0 || !segments.get(0).equals("root")) {
                addSeparator();
            }
            
            String segment = segments.get(i);
            boolean isLast = (i == segments.size() - 1);
            
            if (isLast) {
                addLabel(segment, true);
            } else {
                addSegmentButton(segment, i, false);
            }
        }
        
        // Add spacer to push content left
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getChildren().add(spacer);
        
        // Add right button if configured
        if (rightButton != null) {
            getChildren().add(rightButton);
        }
        
        // Add right buttons container if configured
        if (rightButtonsContainer != null) {
            getChildren().add(rightButtonsContainer);
        }
    }
    
    private void addSegmentButton(String text, int segmentIndex, boolean isRoot) {
        Button button = new Button(text);
        
        // Style the button with consistent font styling
        button.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: " + toHexString(ColorScheme.TEXT_PRIMARY) + ";" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: normal;" +
            "-fx-font-family: system;" +
            "-fx-padding: 4 8 4 8;" +
            "-fx-border-color: transparent;" +
            "-fx-cursor: hand;"
        );
        
        // Hover effect
        button.setOnMouseEntered(e -> button.setStyle(
            "-fx-background-color: " + toHexString(ColorScheme.NODE_HOVER) + ";" +
            "-fx-text-fill: " + toHexString(ColorScheme.TEXT_PRIMARY) + ";" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: normal;" +
            "-fx-font-family: system;" +
            "-fx-padding: 4 8 4 8;" +
            "-fx-border-color: transparent;" +
            "-fx-cursor: hand;"
        ));
        
        button.setOnMouseExited(e -> button.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: " + toHexString(ColorScheme.TEXT_PRIMARY) + ";" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: normal;" +
            "-fx-font-family: system;" +
            "-fx-padding: 4 8 4 8;" +
            "-fx-border-color: transparent;" +
            "-fx-cursor: hand;"
        ));
        
        // Click handler
        button.setOnAction(e -> {
            if (onSegmentClicked != null) {
                onSegmentClicked.accept(segmentIndex);
            }
        });
        
        getChildren().add(button);
    }
    
    private void addLabel(String text, boolean isActive) {
        Label label = new Label(text);
        
        String color = isActive ? 
            toHexString(ColorScheme.NAV_ACTIVE) : 
            toHexString(ColorScheme.TEXT_SECONDARY);
            
        label.setStyle(
            "-fx-text-fill: " + color + ";" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: normal;" +
            "-fx-font-family: system;" +
            "-fx-padding: 4 8 4 8;"
        );
        
        getChildren().add(label);
    }
    
    private void addSeparator() {
        Label separator = new Label(SEPARATOR);
        separator.setStyle(
            "-fx-text-fill: " + toHexString(ColorScheme.TEXT_MUTED) + ";" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: normal;" +
            "-fx-font-family: system;" +
            "-fx-padding: 4 0 4 0;"
        );
        getChildren().add(separator);
    }
    
    /**
     * Set callback for when path changes.
     */
    public void setOnPathChanged(Consumer<Path> onPathChanged) {
        this.onPathChanged = onPathChanged;
    }
    
    /**
     * Set callback for when a path segment is clicked.
     */
    public void setOnSegmentClicked(Consumer<Integer> onSegmentClicked) {
        this.onSegmentClicked = onSegmentClicked;
    }
    
    /**
     * Set the right button with text and callback.
     */
    public void setRightButton(String text, Runnable onClick) {
        this.onRightButtonClicked = onClick;
        
        if (text != null && !text.isEmpty()) {
            rightButton = new Button(text);
            
            // Style the button
            rightButton.setStyle(
                "-fx-background-color: " + toHexString(ColorScheme.NODE_BACKGROUND) + ";" +
                "-fx-text-fill: " + toHexString(ColorScheme.TEXT_PRIMARY) + ";" +
                "-fx-font-size: 12px;" +
                "-fx-padding: 6 12 6 12;" +
                "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";" +
                "-fx-border-width: 1px;" +
                "-fx-border-radius: 3px;" +
                "-fx-background-radius: 3px;" +
                "-fx-cursor: hand;"
            );
            
            // Hover effect
            rightButton.setOnMouseEntered(e -> rightButton.setStyle(
                "-fx-background-color: " + toHexString(ColorScheme.NODE_HOVER) + ";" +
                "-fx-text-fill: " + toHexString(ColorScheme.TEXT_PRIMARY) + ";" +
                "-fx-font-size: 12px;" +
                "-fx-padding: 6 12 6 12;" +
                "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";" +
                "-fx-border-width: 1px;" +
                "-fx-border-radius: 3px;" +
                "-fx-background-radius: 3px;" +
                "-fx-cursor: hand;"
            ));
            
            rightButton.setOnMouseExited(e -> rightButton.setStyle(
                "-fx-background-color: " + toHexString(ColorScheme.NODE_BACKGROUND) + ";" +
                "-fx-text-fill: " + toHexString(ColorScheme.TEXT_PRIMARY) + ";" +
                "-fx-font-size: 12px;" +
                "-fx-padding: 6 12 6 12;" +
                "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";" +
                "-fx-border-width: 1px;" +
                "-fx-border-radius: 3px;" +
                "-fx-background-radius: 3px;" +
                "-fx-cursor: hand;"
            ));
            
            // Click handler
            rightButton.setOnAction(e -> {
                if (onRightButtonClicked != null) {
                    onRightButtonClicked.run();
                }
            });
        } else {
            rightButton = null;
        }
        
        // Force refresh of path segments to add the button
        // This is a bit of a hack - in a real implementation, you'd want a cleaner way
        List<String> currentSegments = getCurrentSegments();
        if (currentSegments != null) {
            setPathSegments(currentSegments);
        }
    }
    
    /**
     * Set multiple right buttons with text and callbacks.
     */
    public void setRightButtons(String[] buttonTexts, Runnable[] onClicks) {
        if (buttonTexts == null || onClicks == null || buttonTexts.length != onClicks.length) {
            rightButtonsContainer = null;
            return;
        }
        
        rightButtonsContainer = new HBox(5);
        
        for (int i = 0; i < buttonTexts.length; i++) {
            String text = buttonTexts[i];
            Runnable onClick = onClicks[i];
            
            if (text != null && !text.isEmpty() && onClick != null) {
                Button button = createStyledButton(text, onClick);
                rightButtonsContainer.getChildren().add(button);
            }
        }
        
        // Clear single right button when using multiple buttons
        rightButton = null;
    }
    
    /**
     * Create a styled button with consistent appearance.
     */
    private Button createStyledButton(String text, Runnable onClick) {
        Button button = new Button(text);
        
        // Style the button
        button.setStyle(
            "-fx-background-color: " + toHexString(ColorScheme.NODE_BACKGROUND) + ";" +
            "-fx-text-fill: " + toHexString(ColorScheme.TEXT_PRIMARY) + ";" +
            "-fx-font-size: 12px;" +
            "-fx-padding: 6 12 6 12;" +
            "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";" +
            "-fx-border-width: 1px;" +
            "-fx-border-radius: 3px;" +
            "-fx-background-radius: 3px;" +
            "-fx-cursor: hand;"
        );
        
        // Hover effect
        button.setOnMouseEntered(e -> button.setStyle(
            "-fx-background-color: " + toHexString(ColorScheme.NODE_HOVER) + ";" +
            "-fx-text-fill: " + toHexString(ColorScheme.TEXT_PRIMARY) + ";" +
            "-fx-font-size: 12px;" +
            "-fx-padding: 6 12 6 12;" +
            "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";" +
            "-fx-border-width: 1px;" +
            "-fx-border-radius: 3px;" +
            "-fx-background-radius: 3px;" +
            "-fx-cursor: hand;"
        ));
        
        button.setOnMouseExited(e -> button.setStyle(
            "-fx-background-color: " + toHexString(ColorScheme.NODE_BACKGROUND) + ";" +
            "-fx-text-fill: " + toHexString(ColorScheme.TEXT_PRIMARY) + ";" +
            "-fx-font-size: 12px;" +
            "-fx-padding: 6 12 6 12;" +
            "-fx-border-color: " + toHexString(ColorScheme.NODE_BORDER) + ";" +
            "-fx-border-width: 1px;" +
            "-fx-border-radius: 3px;" +
            "-fx-background-radius: 3px;" +
            "-fx-cursor: hand;"
        ));
        
        // Click handler
        button.setOnAction(e -> {
            if (onClick != null) {
                onClick.run();
            }
        });
        
        return button;
    }
    
    /**
     * Remove the right button.
     */
    public void clearRightButton() {
        setRightButton(null, null);
    }
    
    /**
     * Clear all right buttons.
     */
    public void clearRightButtons() {
        rightButton = null;
        rightButtonsContainer = null;
    }
    
    /**
     * Get current segments (helper method).
     */
    private List<String> getCurrentSegments() {
        // This is a simplified implementation - you might need to store the current segments
        // or implement a more sophisticated way to track them
        return null; // For now, caller needs to call setPathSegments again
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
