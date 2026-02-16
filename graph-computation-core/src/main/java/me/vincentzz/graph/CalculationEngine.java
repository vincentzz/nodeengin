package me.vincentzz.graph;

import me.vincentzz.graph.model.*;
import me.vincentzz.graph.model.input.InputContext;
import me.vincentzz.graph.model.input.InputResult;
import me.vincentzz.graph.model.input.InputSourceType;
import me.vincentzz.graph.model.output.OutputContext;
import me.vincentzz.graph.model.output.OutputResult;
import me.vincentzz.graph.node.*;
import me.vincentzz.lang.PathUtils;
import me.vincentzz.lang.Result.Failure;
import me.vincentzz.lang.Result.Result;
import me.vincentzz.lang.collection.MapUtils;
import me.vincentzz.lang.tuple.Tuple;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static me.vincentzz.graph.model.output.OutputValueType.ByAdhoc;
import static me.vincentzz.graph.model.output.OutputValueType.ByEvaluation;

public final class CalculationEngine {
    private final CalculationNode rootNode;

    private final Map<Path, CalculationNode> pathToNodeIndex;
    private final Map<Path, Map<ResourceIdentifier, List<CalculationNode>>> scopedResourceProviderIndex;
    private final Map<Path, Map<ConnectionPoint, Flywire>> scopedFlywireByTargetIndex;

    private final Path rootNodePath;

    public CalculationNode getNode(Path path) {
        return Objects.requireNonNull(pathToNodeIndex.get(path));
    }

    public CalculationEngine(CalculationNode rootNode) {
        final Path rootPath = Path.of("/");
        this.rootNode = rootNode;
        this.rootNodePath = rootPath.resolve(rootNode.name());

        //build indexes
        final Map<Path, CalculationNode> nodeMap = new HashMap<>();
        buildPathIndex(nodeMap, rootPath, rootNode);
        this.pathToNodeIndex = Collections.unmodifiableMap(nodeMap);

        final Map<Path, Map<ResourceIdentifier, List<CalculationNode>>> providerMap = new HashMap<>();
        buildScopedProviderIndex(providerMap, rootPath, Set.of(rootNode));
        this.scopedResourceProviderIndex = Collections.unmodifiableMap(providerMap);

        final Map<Path, Map<ConnectionPoint, Flywire>> flywireMap = new HashMap<>();
        buildScopedFlywireIndex(flywireMap, rootPath, rootNode);
        this.scopedFlywireByTargetIndex = Collections.unmodifiableMap(flywireMap);
    }

    private void buildPathIndex(Map<Path, CalculationNode> map, Path currentPath, CalculationNode node) {
        Path nodePath = currentPath.resolve(node.name());
        map.put(nodePath, node);
        if (node instanceof NodeGroup group) {
            group.nodes().forEach(innerNode -> buildPathIndex(map, nodePath, innerNode));
        }
    }

    private void buildScopedProviderIndex(Map<Path, Map<ResourceIdentifier, List<CalculationNode>>> map, Path currentPath, Set<CalculationNode> nodes) {
        Map<ResourceIdentifier, List<CalculationNode>> currentLevelProviderMap = nodes.stream().flatMap(node -> node.outputs().stream().map(rid -> Tuple.of(rid, node)))
                .collect(Collectors.groupingBy(tuple -> tuple._1()))
                .entrySet().stream().collect(Collectors.toUnmodifiableMap(
                        e -> e.getKey(),
                        e -> e.getValue().stream().map(t -> t._2()).toList()));
        map.put(currentPath, currentLevelProviderMap);

        nodes.forEach(node -> {
            if (node instanceof NodeGroup gNode) {
                buildScopedProviderIndex(map, currentPath.resolve(gNode.name()), gNode.nodes());
            }
        });
    }

    private ConnectionPoint toAbsolutePath(Path base, ConnectionPoint point) {
        return point.nodePath().isAbsolute() ? point : ConnectionPoint.of(base.resolve(point.nodePath()), point.rid());
    }

    private void buildScopedFlywireIndex(Map<Path, Map<ConnectionPoint, Flywire>> map, Path currentPath, CalculationNode node) {
        if (node instanceof NodeGroup gNode) {
            Path path = currentPath.resolve(node.name());
            Map<ConnectionPoint, Flywire> flywireOfScope = gNode.flywires().stream()//.map(flywire -> Flywire.of(toAbsolutePath(path, flywire.source()), toAbsolutePath(path, flywire.target())))
                    .collect(Collectors.toUnmodifiableMap(f -> toAbsolutePath(path, f.target()), Function.identity()));
            map.put(path, flywireOfScope);
            gNode.nodes().forEach(innerNode -> {
                buildScopedFlywireIndex(map, path, innerNode);
            });
        }
    }

    public String name() {
        return rootNode.name();
    }

    public CalculationNode rootNode() {
        return rootNode;
    }

    public Path rootNodePath() {
        return rootNodePath;
    }

    public Map<ResourceIdentifier, Result<Object>> evaluate(Snapshot snapshot, Set<ResourceIdentifier> requestedResources) {
        return evaluate(rootNodePath(), snapshot, requestedResources);
    }

    public Map<ResourceIdentifier, Result<Object>> evaluate(Path path, Snapshot snapshot, Set<ResourceIdentifier> requestedResources) {
        return evaluateForResult(path, snapshot, requestedResources, Optional.empty()).results();
    }

    public Map<ResourceIdentifier, Result<Object>> evaluate(Snapshot snapshot, Set<ResourceIdentifier> requestedResources, Optional<AdhocOverride> adhocOverride) {
        return evaluateForResult(rootNodePath(), snapshot, requestedResources, adhocOverride).results();
    }

    public EvaluationResult evaluateForResult(Snapshot snapshot, Set<ResourceIdentifier> requestedResources) {
        return evaluateForResult(rootNodePath(), snapshot, requestedResources);
    }

    public EvaluationResult evaluateForResult(Path path, Snapshot snapshot, Set<ResourceIdentifier> requestedResources) {
        return evaluateForResult(path, snapshot, requestedResources, Optional.empty());
    }

    public EvaluationResult evaluateForResult(Snapshot snapshot, Set<ResourceIdentifier> requestedResources, Optional<AdhocOverride> adhocOverride) {
        return evaluateForResult(rootNodePath(), snapshot, requestedResources, adhocOverride);
    }

    public EvaluationResult evaluateForResult(Path path, Snapshot snapshot, Set<ResourceIdentifier> requestedResources, Optional<AdhocOverride> adhocOverride) {
        if (!path.startsWith(rootNodePath())) {
            throw new RuntimeException("cannot evaluate path '" + PathUtils.toUnixString(path) + "'");
        }
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(snapshot, "snapshot cannot be null");
        Objects.requireNonNull(requestedResources, "requestedResources cannot be null");
        Objects.requireNonNull(adhocOverride, "adhocOverride cannot be null");

        EvaluationContext context = new EvaluationContext(snapshot, path, adhocOverride);
        Result<Map<ResourceIdentifier, OutputResult>> tryResult = Result.Try(() -> evaluateWithContext(path, requestedResources, context, Collections.unmodifiableSet(new LinkedHashSet<>())));
        Map<ResourceIdentifier, OutputResult> resourceResultMap = flattenResourceResult(requestedResources, tryResult);

        return new EvaluationResult(
                snapshot,
                context.requestedNodePath(),
                adhocOverride,
                MapUtils.mapValue(resourceResultMap, (k, v) -> v.value()),
                context.nodeEvaluationMap(),
                rootNode
        );
    }

    private Map<ResourceIdentifier, OutputResult> flattenResourceResult(Set<ResourceIdentifier> requestedResources, Result<Map<ResourceIdentifier, OutputResult>> tryResult) {
        return tryResult.toOptional().orElseGet(() -> requestedResources.stream().collect(Collectors.toUnmodifiableMap(Function.identity(), i -> new OutputResult(new OutputContext(ByEvaluation), (Failure) tryResult))));
    }

    private Map<ResourceIdentifier, OutputResult> evaluateWithContext(Path path, Set<ResourceIdentifier> requestedResources, EvaluationContext context, Set<Path> evalStack) {
        CalculationNode node = getNode(path);
        return switch (node) {
            case AtomicNode aNode -> {
                if (evalStack.contains(path)) {
                    throw new RuntimeException("Cycle detected: " + PathUtils.toUnixString(path) + " already in evaluation stack: " + evalStack + ". This indicates a circular dependency in your calculation graph.");
                }
                Set<Path> newStack = new LinkedHashSet<>(evalStack);
                newStack.add(path);
                Map<ResourceIdentifier, OutputResult> cachedNodeOutputs = context.getCachedResults(path, requestedResources);
                Map<ResourceIdentifier, OutputResult> fullResult = cachedNodeOutputs.isEmpty() ? evaluateAtomicNode(path, aNode, context, Collections.unmodifiableSet(newStack)) : cachedNodeOutputs;
                yield fullResult.entrySet().stream()
                        .filter(e -> requestedResources.contains(e.getKey()))
                        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
            }
            case NodeGroup gNode -> {
                Map<ResourceIdentifier, OutputResult> alreadyCached = context.getCachedResults(path, requestedResources);
                Set<ResourceIdentifier> needEvalRids = requestedResources.stream().filter(rid -> !alreadyCached.containsKey(rid)).collect(Collectors.toUnmodifiableSet());
                Map<ResourceIdentifier, OutputResult> newEvalResult = evaluateNodeGroup(path, needEvalRids, context, evalStack);
                yield requestedResources.stream().collect(Collectors.toUnmodifiableMap(Function.identity(), rid -> alreadyCached.containsKey(rid) ? alreadyCached.get(rid) : newEvalResult.get(rid)));
            }
        };
    }

    private Map<ResourceIdentifier, OutputResult> evaluateAtomicNode(Path path, AtomicNode node, EvaluationContext context, Set<Path> evalStack) {
        Set<ResourceIdentifier> nodeOutputs = node.outputs();
        Map<ResourceIdentifier, OutputResult> adhocOutputs = nodeOutputs.stream()
                .collect(Collectors.toUnmodifiableMap(Function.identity(), oRid -> context.getAdhocOutput(path, oRid)))
                .entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toUnmodifiableMap(e -> e.getKey(), e -> new OutputResult(new OutputContext(ByAdhoc), e.getValue().get())));
        if (adhocOutputs.size() == nodeOutputs.size()) {
            context.cacheOutputs(path, adhocOutputs);
            return adhocOutputs;
        } else {
            Result<Map<ResourceIdentifier, OutputResult>> tryResult = Result.Try(() -> {
                Map<ResourceIdentifier, Result<Object>> paramMap = new HashMap<>();
                Set<ResourceIdentifier> nextStageDependencies = node.inputs();
                boolean isDirectInput = true;
                while (!nextStageDependencies.isEmpty()) {
                    boolean finalIsDirectInput = isDirectInput;
                    nextStageDependencies.forEach(dep -> {
                        Tuple<InputSourceType, Result<Object>> inputTuple = resolveValueForDependency(path, dep, context, evalStack);
                        paramMap.put(dep, inputTuple._2());
                        context.cacheInput(path, dep, new InputResult(new InputContext(inputTuple._1(), Optional.of(finalIsDirectInput)), inputTuple._2()));
                    });
                    nextStageDependencies = node.resolveDependencies(context.snapshot(), Map.copyOf(paramMap));
                    isDirectInput = false;
                }
                Map<ResourceIdentifier, Result<Object>> computeResult = node.compute(context.snapshot(), paramMap);
                return MapUtils.mapValue(computeResult, (k, v) -> new OutputResult(new OutputContext(ByEvaluation), v));
            });
            Map<ResourceIdentifier, OutputResult> result = flattenResourceResult(node.outputs(), tryResult);
            Map<ResourceIdentifier, OutputResult> withAdhocOverride;
            if (!adhocOutputs.isEmpty()) {
                withAdhocOverride = MapUtils.union(result, adhocOutputs);
            } else {
                withAdhocOverride = result;
            }
            context.cacheOutputs(path, withAdhocOverride);
            return withAdhocOverride;
        }
    }

    private Tuple<InputSourceType, Result<Object>> resolveValueForDependency(Path path, ResourceIdentifier rid, EvaluationContext context, Set<Path> evalStack) {
        Optional<Result<Object>> adhocInput = context.getAdhocInput(path, rid);
        if (adhocInput.isPresent()) {
            return Tuple.of(InputSourceType.ByAdhoc, adhocInput.get());
        } else {
            InputSourceType inputSourceType;
            Path parentPath = path.getParent();
            Optional<Flywire> adhocFlywireOp = context.getAdhocFlywire(path, rid);
            ConnectionPoint dependedConnectPoint;
            if (adhocFlywireOp.isPresent()) {
                inputSourceType = InputSourceType.ByAdhocFlywire;
                dependedConnectPoint = adhocFlywireOp.get().source();
            } else {
                Optional<Flywire> flywireOp = Optional.ofNullable(scopedFlywireByTargetIndex.get(parentPath)).flatMap(m -> Optional.ofNullable(m.get(ConnectionPoint.of(path, rid))));
                if (flywireOp.isPresent()) {
                    inputSourceType = InputSourceType.ByFlywire;
                    Flywire usedFlywire = flywireOp.get();
                    dependedConnectPoint = toAbsolutePath(parentPath, usedFlywire.source());
                } else {
                    Optional<List<CalculationNode>> providersOp = Optional.ofNullable(scopedResourceProviderIndex.get(parentPath)).flatMap(m -> Optional.ofNullable(m.get(rid)));
                    if (providersOp.isEmpty()) {
                        if (!parentPath.equals(rootNodePath) && parentPath.startsWith(rootNodePath)) {
                            Tuple<InputSourceType, Result<Object>> resolveFromParent = resolveValueForDependency(parentPath, rid, context, evalStack);
                            context.cacheInput(parentPath,rid, new InputResult(new InputContext(resolveFromParent._1(), Optional.empty()), resolveFromParent._2()));
                            return Tuple.of(InputSourceType.ByParentGroup, resolveFromParent._2());
                        } else {
                            return Tuple.of(InputSourceType.ByResolve, Failure.of(new RuntimeException("cannot find provider for " + rid)));
                        }
                    } else {
                        inputSourceType = InputSourceType.ByResolve;
                        List<CalculationNode> providers = providersOp.get();
                        if (providers.size() == 1) {
                            CalculationNode node = providers.get(0);
                            dependedConnectPoint = ConnectionPoint.of(parentPath.resolve(node.name()), rid);
                        } else {
                            return Tuple.of(InputSourceType.ByResolve, Failure.of(new RuntimeException("ambiguous with multiple providers for '" + rid + "', under path '" + PathUtils.toUnixString(path) + "'")));
                        }
                    }
                }
            }
            Result<Object> dependencyResult = evaluateWithContext(dependedConnectPoint.nodePath(), Set.of(dependedConnectPoint.rid()), context, evalStack).get(dependedConnectPoint.rid()).value();
            return Tuple.of(inputSourceType, dependencyResult);
        }

    }


    private Map<ResourceIdentifier, OutputResult> evaluateNodeGroup(Path path, Set<ResourceIdentifier> requestedResources, EvaluationContext context, Set<Path> evalStack) {
        Map<ResourceIdentifier, OutputResult> adhocOutputs = requestedResources.stream()
                .collect(Collectors.toUnmodifiableMap(Function.identity(), oRid -> context.getAdhocOutput(path, oRid)))
                .entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toUnmodifiableMap(e -> e.getKey(), e -> new OutputResult(new OutputContext(ByAdhoc), e.getValue().get())));

        if (adhocOutputs.size() == requestedResources.size()) {
            context.cacheOutputs(path, adhocOutputs);
            return adhocOutputs;
        } else {
            Set<ResourceIdentifier> remainingResources = requestedResources.stream().filter(rid -> !adhocOutputs.containsKey(rid)).collect(Collectors.toUnmodifiableSet());
            Map<ResourceIdentifier, OutputResult> evalResults = remainingResources.stream().collect(Collectors.toUnmodifiableMap(Function.identity(), rid -> {
                Optional<List<CalculationNode>> providersOp = Optional.ofNullable(scopedResourceProviderIndex.get(path)).flatMap(m -> Optional.ofNullable(m.get(rid)));
                if (providersOp.isEmpty()) {
                    return new OutputResult(new OutputContext(ByEvaluation), Failure.of(new RuntimeException("no resource provider in group '" + PathUtils.toUnixString(path) + "' for rid '" + rid + "'")));
                } else {
                    List<CalculationNode> providers = providersOp.get();
                    if (providers.size() == 1) {
                        CalculationNode providerNode = providers.get(0);
                        OutputResult dependencyResult = evaluateWithContext(path.resolve(providerNode.name()), Set.of(rid), context, evalStack).get(rid);
                        return new OutputResult(new OutputContext(ByEvaluation), dependencyResult.value());
                    } else {
                        return new OutputResult(new OutputContext(ByEvaluation), Failure.of(new RuntimeException("ambiguous with multiple providers for '" + rid + "', under path '" + PathUtils.toUnixString(path) + "'")));
                    }
                }
            }));
            Map<ResourceIdentifier, OutputResult> finalResult = MapUtils.union(evalResults, adhocOutputs);
            context.cacheOutputs(path, finalResult);
            return finalResult;
        }
    }

//    private CalculationNode getSubGraphFromEvaluationContext(EvaluationContext context) {
//        Set<Path> evaluatedPaths = context.nodeEvaluationCache().keySet();
//        // Build the sub-graph using NodeBuilder pattern
//        NodeBuilder rootBuilder = buildSubGraphBuilderRecursive(rootNodePath, evaluatedPaths, context);
//        return rootBuilder != null ? rootBuilder.toNode() : rootNode;
//    }
//
//    private NodeBuilder buildSubGraphBuilderRecursive(Path currentPath, Set<Path> evaluatedPaths, EvaluationContext context) {
//        CalculationNode originalNode = pathToNodeIndex.get(currentPath);
//
//        return switch (originalNode) {
//            case AtomicNode atomicNode -> evaluatedPaths.contains(currentPath) ? new AtomicNodeBuilder(atomicNode) : null;
//            case NodeGroup originalGroup -> {
//                // For NodeGroups, build a new group containing only evaluated children
//                List<NodeBuilder> includedChildBuilders = new ArrayList<>();
//
//                // Recursively check each child
//                for (CalculationNode child : originalGroup.nodes()) {
//                    Path childPath = currentPath.resolve(child.name());
//                    NodeBuilder childBuilder = buildSubGraphBuilderRecursive(childPath, evaluatedPaths, context);
//                    if (childBuilder != null) {
//                        includedChildBuilders.add(childBuilder);
//                    }
//                }
//
//                // Only create the group if it has evaluated children OR if it's on the path to evaluated nodes
//                if (includedChildBuilders.isEmpty() && !isOnPathToEvaluatedNodes(currentPath, evaluatedPaths)) {
//                    yield null;
//                }
//
//                // Build new NodeGroup with only the relevant children
//                NodeGroupBuilder builder = new NodeGroupBuilder(originalGroup);
//
//                // Add the evaluated children
//                for (NodeBuilder childBuilder : includedChildBuilders) {
//                    builder.addNode(childBuilder);
//                }
//
//                // Add only the flywires that were actually used in this scope
//                Set<Flywire> usedFlywires = context.getUsedFlywires(currentPath);
//                builder.addFlywires(usedFlywires);
//
//                yield builder;
//            }
//        };
//    }
//
//    /**
//     * Check if a path is on the route to any evaluated nodes.
//     * This ensures we include intermediate NodeGroups in the hierarchy.
//     */
//    private boolean isOnPathToEvaluatedNodes(Path currentPath, Set<Path> evaluatedPaths) {
//        return evaluatedPaths.stream().anyMatch(evalPath -> evalPath.startsWith(currentPath) && !evalPath.equals(currentPath));
//    }
}
