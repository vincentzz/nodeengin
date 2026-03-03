package me.vincentzz.visualnew;

import me.vincentzz.graph.json.ConstructionalJsonUtil;
import me.vincentzz.graph.model.EvaluationBundle;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.lang.Result.Result;

/**
 * Detects the type of a JSON string: EvaluationBundle or CalculationNode.
 * Tries EvaluationBundle first, then falls back to CalculationNode.
 */
public class JsonFileDetector {

    public sealed interface DetectionResult
            permits BundleDetected, NodeDetected, DetectionFailed {}

    public record BundleDetected(EvaluationBundle bundle) implements DetectionResult {}
    public record NodeDetected(CalculationNode node) implements DetectionResult {}
    public record DetectionFailed(String error) implements DetectionResult {}

    public static DetectionResult detect(String json) {
        // Try EvaluationBundle first
        Result<EvaluationBundle> bundleResult = ConstructionalJsonUtil.fromJsonEvaluationBundle(json);
        if (bundleResult.isSuccess()) {
            return new BundleDetected(bundleResult.get());
        }

        // Try CalculationNode
        Result<CalculationNode> nodeResult = ConstructionalJsonUtil.fromJson(json);
        if (nodeResult.isSuccess()) {
            return new NodeDetected(nodeResult.get());
        }

        return new DetectionFailed("Not a valid EvaluationBundle or CalculationNode JSON");
    }
}
