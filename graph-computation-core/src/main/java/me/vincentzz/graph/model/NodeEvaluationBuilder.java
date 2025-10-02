package me.vincentzz.graph.model;

import me.vincentzz.graph.model.input.InputResult;
import me.vincentzz.graph.model.output.OutputResult;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class NodeEvaluationBuilder {
    private final Map<ResourceIdentifier, InputResult> inputs = new ConcurrentHashMap<>();
    private final Map<ResourceIdentifier, OutputResult> outputs = new ConcurrentHashMap<>();

    public NodeEvaluation toNodeEvaluation() {
        return new NodeEvaluation(Collections.unmodifiableMap(inputs), Collections.unmodifiableMap(outputs));
    }

    public Map<ResourceIdentifier, InputResult> inputs() {
        return Collections.unmodifiableMap(inputs);
    }

    public Map<ResourceIdentifier, OutputResult> outputs() {
        return outputs;
    }

    public void recordInput(ResourceIdentifier rid, InputResult inputResult) {
        inputs.putIfAbsent(rid, inputResult);
    }

    public void recordOutput(ResourceIdentifier rid, OutputResult outputResult) {
        outputs.putIfAbsent(rid, outputResult);
    }

    public Optional<OutputResult> getResult(ResourceIdentifier rid) {
        return Optional.ofNullable(outputs.get(rid));
    }
}
