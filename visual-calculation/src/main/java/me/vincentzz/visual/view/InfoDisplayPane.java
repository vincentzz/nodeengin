package me.vincentzz.visual.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import me.vincentzz.graph.model.AdhocOverride;
import me.vincentzz.graph.model.EvaluationResult;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.node.Flywire;
import me.vincentzz.lang.PathUtils;
import me.vincentzz.lang.Result.Result;
import me.vincentzz.visual.util.ColorScheme;

import java.nio.file.Path;
import java.util.Map;

/**
 * Bottom panel that displays original request details and final result values from EvaluationResult.
 * Uses structured UI with TreeViews and styled rows instead of raw text.
 */
public class InfoDisplayPane extends VBox {

    private static final double PREFERRED_HEIGHT = 120.0;

    private EvaluationResult evaluationResult;

    // UI Components
    private VBox requestContent;
    private VBox resultContent;

    public InfoDisplayPane() {
        initializeComponents();
        setupLayout();
        styleComponents();
    }

    private void initializeComponents() {
        requestContent = new VBox(4);
        requestContent.setPadding(new Insets(4));

        resultContent = new VBox(4);
        resultContent.setPadding(new Insets(4));
    }

    private void setupLayout() {
        setPrefHeight(PREFERRED_HEIGHT);
        setPadding(new Insets(3));
        setSpacing(3);

        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(javafx.geometry.Orientation.HORIZONTAL);

        // Left side: Request
        VBox requestSection = new VBox(2);
        requestSection.setStyle("-fx-background-color: " + toHexString(ColorScheme.NAV_BACKGROUND) + ";");
        Label requestLabel = new Label("Request");
        requestLabel.setFont(Font.font("System", 10));
        requestLabel.getStyleClass().add("title");

        ScrollPane requestScroll = new ScrollPane(requestContent);
        requestScroll.setFitToWidth(true);
        requestScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        styleScrollPane(requestScroll);

        requestSection.getChildren().addAll(requestLabel, requestScroll);
        VBox.setVgrow(requestScroll, Priority.ALWAYS);

        // Right side: Result
        VBox resultSection = new VBox(2);
        resultSection.setStyle("-fx-background-color: " + toHexString(ColorScheme.NAV_BACKGROUND) + ";");
        Label resultLabel = new Label("Result");
        resultLabel.setFont(Font.font("System", 10));
        resultLabel.getStyleClass().add("title");

        ScrollPane resultScroll = new ScrollPane(resultContent);
        resultScroll.setFitToWidth(true);
        resultScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        styleScrollPane(resultScroll);

        resultSection.getChildren().addAll(resultLabel, resultScroll);
        VBox.setVgrow(resultScroll, Priority.ALWAYS);

        splitPane.getItems().addAll(requestSection, resultSection);
        splitPane.setDividerPositions(0.5);

        getChildren().add(splitPane);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
    }

    private void styleComponents() {
        setStyle("-fx-background-color: " + toHexString(ColorScheme.NAV_BACKGROUND) + ";");
        requestContent.setStyle("-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_DARK) + ";");
        resultContent.setStyle("-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_DARK) + ";");
    }

    /**
     * Update the display with new EvaluationResult data.
     */
    public void updateEvaluationResult(EvaluationResult evaluationResult) {
        this.evaluationResult = evaluationResult;
        updateDisplay();
    }

    private void updateDisplay() {
        requestContent.getChildren().clear();
        resultContent.getChildren().clear();

        if (evaluationResult == null) {
            requestContent.getChildren().add(createMutedLabel("No request loaded"));
            resultContent.getChildren().add(createMutedLabel("No results"));
            return;
        }

        buildRequestPanel();
        buildResultPanel();
    }

    // ===== Request Panel =====

    private void buildRequestPanel() {
        var request = evaluationResult.request();

        // Requested Node Path
        requestContent.getChildren().add(createSectionHeader("Requested Node Path"));
        Path requestedPath = request.path();
        HBox pathRow = createKeyValueRow("Path",
                requestedPath != null ? PathUtils.toUnixString(requestedPath) : "Unknown");
        requestContent.getChildren().add(pathRow);

        // Requested Resource IDs
        requestContent.getChildren().add(createSeparator());
        requestContent.getChildren().add(createSectionHeader(
                "Requested Resources (" + evaluationResult.results().size() + ")"));

        for (ResourceIdentifier rid : evaluationResult.results().keySet()) {
            requestContent.getChildren().add(createResourceIdRow(rid));
        }

        // Adhoc Override
        requestContent.getChildren().add(createSeparator());
        if (request.override().isEmpty()) {
            requestContent.getChildren().add(createSectionHeader("Adhoc Override"));
            requestContent.getChildren().add(createMutedLabel("  (none)"));
        } else {
            AdhocOverride override = request.override().get();
            buildAdhocSection(override);
        }
    }

    private void buildAdhocSection(AdhocOverride override) {
        // Adhoc Inputs
        requestContent.getChildren().add(createSectionHeader(
                "Adhoc Inputs (" + override.adhocInputs().size() + ")"));
        if (override.adhocInputs().isEmpty()) {
            requestContent.getChildren().add(createMutedLabel("  (empty)"));
        } else {
            for (var entry : override.adhocInputs().entrySet()) {
                requestContent.getChildren().add(
                        createConnectionPointResultRow(entry.getKey(), entry.getValue()));
            }
        }

        // Adhoc Outputs
        requestContent.getChildren().add(createSectionHeader(
                "Adhoc Outputs (" + override.adhocOutputs().size() + ")"));
        if (override.adhocOutputs().isEmpty()) {
            requestContent.getChildren().add(createMutedLabel("  (empty)"));
        } else {
            for (var entry : override.adhocOutputs().entrySet()) {
                requestContent.getChildren().add(
                        createConnectionPointResultRow(entry.getKey(), entry.getValue()));
            }
        }

        // Adhoc Flywires
        requestContent.getChildren().add(createSectionHeader(
                "Adhoc Flywires (" + override.adhocFlywires().size() + ")"));
        if (override.adhocFlywires().isEmpty()) {
            requestContent.getChildren().add(createMutedLabel("  (empty)"));
        } else {
            for (Flywire fw : override.adhocFlywires()) {
                requestContent.getChildren().add(createFlywireRow(fw));
            }
        }
    }

    // ===== Result Panel =====

    private void buildResultPanel() {
        if (evaluationResult.results().isEmpty()) {
            resultContent.getChildren().add(createMutedLabel("No final results"));
            return;
        }

        resultContent.getChildren().add(createSectionHeader(
                "Final Results (" + evaluationResult.results().size() + ")"));

        for (var entry : evaluationResult.results().entrySet()) {
            resultContent.getChildren().add(
                    createResultRow(entry.getKey(), entry.getValue()));
        }
    }

    // ===== UI Component Builders =====

    private Label createSectionHeader(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, 11));
        label.setTextFill(Color.web("#66CCFF"));
        label.setPadding(new Insets(4, 0, 2, 0));
        return label;
    }

    private Label createMutedLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", 10));
        label.setTextFill(ColorScheme.TEXT_MUTED);
        return label;
    }

    private HBox createKeyValueRow(String key, String value) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(1, 4, 1, 8));

        Label keyLabel = new Label(key + ":");
        keyLabel.setFont(Font.font("System", FontWeight.BOLD, 10));
        keyLabel.setTextFill(ColorScheme.TEXT_SECONDARY);
        keyLabel.setMinWidth(Region.USE_PREF_SIZE);

        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("Courier New", 10));
        valueLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        valueLabel.setWrapText(true);

        row.getChildren().addAll(keyLabel, valueLabel);
        return row;
    }

    private HBox createResourceIdRow(ResourceIdentifier rid) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(1, 4, 1, 8));

        Circle dot = new Circle(3, getResourceColor(rid));
        Label label = new Label(formatResourceId(rid));
        label.setFont(Font.font("Courier New", 10));
        label.setTextFill(ColorScheme.TEXT_PRIMARY);

        row.getChildren().addAll(dot, label);
        return row;
    }

    private VBox createResultRow(ResourceIdentifier rid, Result<Object> result) {
        VBox card = new VBox(2);
        card.setPadding(new Insets(4, 6, 4, 6));
        card.setStyle("-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_MEDIUM) + ";" +
                "-fx-background-radius: 3;");

        // Top row: colored dot + resource ID + status badge
        HBox topRow = new HBox(6);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Circle dot = new Circle(4, getResourceColor(rid));

        Label ridLabel = new Label(formatResourceId(rid));
        ridLabel.setFont(Font.font("System", FontWeight.BOLD, 10));
        ridLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        HBox.setHgrow(ridLabel, Priority.ALWAYS);

        Label badge = createStatusBadge(result);

        topRow.getChildren().addAll(dot, ridLabel, badge);

        // Bottom row: value
        Label valueLabel = new Label(formatResultValue(result));
        valueLabel.setFont(Font.font("Courier New", 10));
        valueLabel.setTextFill(result.isSuccess() ? ColorScheme.TEXT_SECONDARY : Color.web("#FF6666"));
        valueLabel.setWrapText(true);
        valueLabel.setPadding(new Insets(0, 0, 0, 14));

        card.getChildren().addAll(topRow, valueLabel);
        return card;
    }

    private VBox createConnectionPointResultRow(ConnectionPoint cp, Result<Object> result) {
        VBox card = new VBox(2);
        card.setPadding(new Insets(3, 6, 3, 8));
        card.setStyle("-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_MEDIUM) + ";" +
                "-fx-background-radius: 3;");

        // Connection point info
        HBox cpRow = new HBox(6);
        cpRow.setAlignment(Pos.CENTER_LEFT);

        Circle dot = new Circle(3, getResourceColor(cp.rid()));

        Label pathLabel = new Label(PathUtils.toUnixString(cp.nodePath()));
        pathLabel.setFont(Font.font("Courier New", FontWeight.BOLD, 10));
        pathLabel.setTextFill(ColorScheme.TEXT_PRIMARY);

        cpRow.getChildren().addAll(dot, pathLabel);

        // Resource ID
        Label ridLabel = new Label(formatResourceId(cp.rid()));
        ridLabel.setFont(Font.font("Courier New", 10));
        ridLabel.setTextFill(ColorScheme.TEXT_SECONDARY);
        ridLabel.setPadding(new Insets(0, 0, 0, 12));

        // Value
        HBox valueRow = new HBox(4);
        valueRow.setAlignment(Pos.CENTER_LEFT);
        valueRow.setPadding(new Insets(0, 0, 0, 12));

        Label badge = createStatusBadge(result);
        Label valueLabel = new Label(formatResultValue(result));
        valueLabel.setFont(Font.font("Courier New", 10));
        valueLabel.setTextFill(result.isSuccess() ? ColorScheme.TEXT_SECONDARY : Color.web("#FF6666"));
        valueLabel.setWrapText(true);

        valueRow.getChildren().addAll(badge, valueLabel);

        card.getChildren().addAll(cpRow, ridLabel, valueRow);
        return card;
    }

    private VBox createFlywireRow(Flywire fw) {
        VBox card = new VBox(2);
        card.setPadding(new Insets(3, 6, 3, 8));
        card.setStyle("-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_MEDIUM) + ";" +
                "-fx-background-radius: 3;");

        // Source connection point
        HBox sourceRow = new HBox(4);
        sourceRow.setAlignment(Pos.CENTER_LEFT);
        Label srcHeader = new Label("src:");
        srcHeader.setFont(Font.font("System", FontWeight.BOLD, 10));
        srcHeader.setTextFill(Color.web("#66CCFF"));
        srcHeader.setMinWidth(24);
        Circle srcDot = new Circle(3, getResourceColor(fw.source().rid()));
        Label srcLabel = new Label(PathUtils.toUnixString(fw.source().nodePath())
                + " : " + formatResourceId(fw.source().rid()));
        srcLabel.setFont(Font.font("Courier New", 10));
        srcLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        srcLabel.setWrapText(true);
        sourceRow.getChildren().addAll(srcHeader, srcDot, srcLabel);

        // Arrow
        Label arrow = new Label("       \u2192");
        arrow.setFont(Font.font("System", FontWeight.BOLD, 10));
        arrow.setTextFill(Color.web("#66CCFF"));

        // Target connection point
        HBox targetRow = new HBox(4);
        targetRow.setAlignment(Pos.CENTER_LEFT);
        Label tgtHeader = new Label("tgt:");
        tgtHeader.setFont(Font.font("System", FontWeight.BOLD, 10));
        tgtHeader.setTextFill(Color.web("#66CCFF"));
        tgtHeader.setMinWidth(24);
        Circle tgtDot = new Circle(3, getResourceColor(fw.target().rid()));
        Label tgtLabel = new Label(PathUtils.toUnixString(fw.target().nodePath())
                + " : " + formatResourceId(fw.target().rid()));
        tgtLabel.setFont(Font.font("Courier New", 10));
        tgtLabel.setTextFill(ColorScheme.TEXT_PRIMARY);
        tgtLabel.setWrapText(true);
        targetRow.getChildren().addAll(tgtHeader, tgtDot, tgtLabel);

        card.getChildren().addAll(sourceRow, arrow, targetRow);
        return card;
    }

    private Label createStatusBadge(Result<Object> result) {
        Label badge = new Label();
        badge.setFont(Font.font("System", FontWeight.BOLD, 9));
        badge.setPadding(new Insets(0, 4, 0, 4));
        badge.setMinWidth(Region.USE_PREF_SIZE);

        if (result.isSuccess()) {
            badge.setText("OK");
            badge.setTextFill(Color.web("#1A1A1A"));
            badge.setStyle("-fx-background-color: #44CC44; -fx-background-radius: 3;");
        } else {
            badge.setText("ERR");
            badge.setTextFill(Color.web("#FFFFFF"));
            badge.setStyle("-fx-background-color: #CC4444; -fx-background-radius: 3;");
        }
        return badge;
    }

    private Region createSeparator() {
        Region sep = new Region();
        sep.setPrefHeight(1);
        sep.setMaxHeight(1);
        sep.setStyle("-fx-background-color: " + toHexString(ColorScheme.NODE_BORDER) + ";");
        VBox.setMargin(sep, new Insets(2, 0, 2, 0));
        return sep;
    }

    // ===== Formatting Helpers =====

    private String formatResourceId(ResourceIdentifier rid) {
        String ridStr = rid.toString();

        if (ridStr.contains("FalconRawTopic")) {
            try {
                String symbol = ridStr.replaceAll(".*symbol='?([^',\\]]+)'?.*", "$1");
                String source = ridStr.replaceAll(".*source='?([^',\\]]+)'?.*", "$1");
                String attribute = ridStr.replaceAll(".*attribute=([^,\\]]+).*", "$1");

                if (attribute.startsWith("class ")) {
                    attribute = attribute.substring(6);
                    int lastDot = attribute.lastIndexOf('.');
                    if (lastDot >= 0) {
                        attribute = attribute.substring(lastDot + 1);
                    }
                }

                return symbol + " / " + source + " / " + attribute;
            } catch (Exception e) {
                return ridStr;
            }
        }

        return ridStr;
    }

    private String formatResultValue(Result<Object> result) {
        if (result == null) return "null";

        if (result.isSuccess()) {
            Object data = result.get();
            return toJsonString(data);
        } else {
            Exception ex = result.getException();
            return ex != null ? ex.getMessage() : "Unknown error";
        }
    }

    private String toJsonString(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return "\"" + s + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value.getClass().isRecord()) {
            StringBuilder sb = new StringBuilder("{ ");
            var components = value.getClass().getRecordComponents();
            for (int i = 0; i < components.length; i++) {
                try {
                    var accessor = components[i].getAccessor();
                    accessor.setAccessible(true);
                    Object fieldValue = accessor.invoke(value);
                    sb.append("\"").append(components[i].getName()).append("\": ");
                    sb.append(toJsonString(fieldValue));
                    if (i < components.length - 1) sb.append(", ");
                } catch (Exception e) {
                    sb.append("\"").append(components[i].getName()).append("\": \"error\"");
                }
            }
            sb.append(" }");
            return sb.toString();
        }
        if (value instanceof java.time.Instant instant) return "\"" + instant + "\"";
        if (value instanceof java.math.BigDecimal bd) return bd.toPlainString();
        if (value instanceof Class<?> c) return "\"" + c.getSimpleName() + "\"";
        return "\"" + value + "\"";
    }

    private Color getResourceColor(ResourceIdentifier rid) {
        String ridStr = rid.toString();
        if (ridStr.contains("Ask")) return Color.web("#44FF44");
        if (ridStr.contains("Bid")) return Color.web("#4444FF");
        if (ridStr.contains("MidPrice")) return Color.web("#FFAA00");
        if (ridStr.contains("Spread")) return Color.web("#FF44FF");
        if (ridStr.contains("Volume")) return Color.web("#44FFFF");
        if (ridStr.contains("Vwap")) return Color.web("#FF8844");
        if (ridStr.contains("MarkToMarket")) return Color.web("#FFFF44");
        return ColorScheme.TEXT_SECONDARY;
    }

    /**
     * Clear the display.
     */
    public void clear() {
        this.evaluationResult = null;
        updateDisplay();
    }

    private void styleScrollPane(ScrollPane sp) {
        sp.setStyle(
                "-fx-background: " + toHexString(ColorScheme.BACKGROUND_DARK) + ";" +
                "-fx-background-color: " + toHexString(ColorScheme.BACKGROUND_DARK) + ";"
        );
    }

    private String toHexString(javafx.scene.paint.Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }
}
