package me.vincentzz.visual.model;

import me.vincentzz.graph.CalculationEngine;
import me.vincentzz.graph.model.EvaluationResult;
import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.graph.node.NodeGroup;
import me.vincentzz.lang.PathUtils;
import me.vincentzz.lang.Result.Result;
import me.vincentzz.visual.util.ColorScheme;
import me.vincentzz.visual.util.DynamicColorManager;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Model for visualization data extracted from EvaluationResult.
 * Manages navigation through the node hierarchy and provides data for rendering.
 */
public class VisualizationModel {
    
    private final EvaluationResult evaluationResult;
    private final CalculationEngine calculationEngine;
    private Path currentPath;
    private final Map<Path, NodeViewModel> nodeViewModels;
    private final List<ConnectionViewModel> connections;
    
    public VisualizationModel(EvaluationResult evaluationResult) {
        this.evaluationResult = evaluationResult;
        // Create CalculationEngine from the EvaluationResult's graph field
        this.calculationEngine = new CalculationEngine(evaluationResult.graph());
        this.currentPath = evaluationResult.requestedNodePath();
        this.nodeViewModels = new HashMap<>();
        this.connections = new ArrayList<>();
        
        // Initialize dynamic color manager for runtime color assignment
        initializeDynamicColorManager();
        
        buildModel();
    }
    
    public CalculationEngine getCalculationEngine() {
        return calculationEngine;
    }
    
    /**
     * Initialize the dynamic color manager based on the loaded EvaluationResult.
     * This analyzes the JSON content and sets up dynamic color assignment.
     */
    private void initializeDynamicColorManager() {
        DynamicColorManager colorManager = new DynamicColorManager(evaluationResult);
        ColorScheme.setDynamicColorManager(colorManager);
        
        System.out.println("Dynamic color manager initialized with " + 
                         colorManager.getDistinctAttributeCount() + " distinct attribute types");
    }
    
    private void buildModel() {
        // Build node view models from node evaluation map
        for (Map.Entry<Path, me.vincentzz.graph.model.NodeEvaluation> entry :
             evaluationResult.nodeEvaluationMap().entrySet()) {
            Path nodePath = entry.getKey();
            me.vincentzz.graph.model.NodeEvaluation nodeEvaluation = entry.getValue();
            
            // Extract results for this node from outputs
            Map<ResourceIdentifier, Result<Object>> nodeResults = new HashMap<>();
            nodeEvaluation.outputs().forEach((rid, outputResult) -> {
                nodeResults.put(rid, outputResult.value());
            });
            
            NodeViewModel nodeViewModel = new NodeViewModel(
                nodePath,
                getNodeName(nodePath),
                extractInputs(nodePath),
                extractOutputs(nodePath),
                nodeResults,
                isNodeGroup(nodePath)
            );
            
            nodeViewModels.put(nodePath, nodeViewModel);
        }
        
        // Build connections
        buildConnections();
    }
    
    private void buildConnections() {
        connections.clear();
        
        // Add direct dependency connections (black lines)
        buildDirectConnections();
        
        // Add conditional dependency connections (green lines)
        buildConditionalConnections();
        
        // Add flywire connections (blue lines)
        buildFlywireConnections();
    }
    
    private void buildDirectConnections() {
        // For each node, find its direct dependencies by looking at inputs
        // This is simplified - in a real implementation, you'd need dependency analysis
        for (NodeViewModel node : nodeViewModels.values()) {
            for (ResourceIdentifier input : node.getInputs()) {
                // Find nodes that provide this resource as output
                for (NodeViewModel provider : nodeViewModels.values()) {
                    if (provider != node && provider.getOutputs().contains(input)) {
                        connections.add(new ConnectionViewModel(
                            provider.getNodePath(),
                            input,
                            node.getNodePath(),
                            input,
                            ConnectionViewModel.ConnectionType.DIRECT
                        ));
                    }
                }
            }
        }
    }
    
    private void buildConditionalConnections() {
        // Extract conditional inputs from nodeEvaluationMap
        for (Map.Entry<Path, me.vincentzz.graph.model.NodeEvaluation> entry : 
             evaluationResult.nodeEvaluationMap().entrySet()) {
            Path targetPath = entry.getKey();
            me.vincentzz.graph.model.NodeEvaluation nodeEvaluation = entry.getValue();
            
            // Get inputs that are conditional (this is a simplified approach)
            // In the new model, we'll need to determine conditional vs direct inputs differently
            for (Map.Entry<ResourceIdentifier, me.vincentzz.graph.model.input.InputResult> inputEntry :
                 nodeEvaluation.inputs().entrySet()) {
                ResourceIdentifier resourceId = inputEntry.getKey();
                
                // Find the source of this input
                for (NodeViewModel provider : nodeViewModels.values()) {
                    if (provider.getOutputs().contains(resourceId)) {
                        connections.add(new ConnectionViewModel(
                            provider.getNodePath(),
                            resourceId,
                            targetPath,
                            resourceId,
                            ConnectionViewModel.ConnectionType.CONDITIONAL
                        ));
                    }
                }
            }
        }
    }
    
    private void buildFlywireConnections() {
        if (evaluationResult.adhocOverride().isPresent()) {
            evaluationResult.adhocOverride().get().adhocFlywires().forEach(flywire -> {
                connections.add(new ConnectionViewModel(
                    flywire.source().nodePath(),
                    flywire.source().rid(),
                    flywire.target().nodePath(),
                    flywire.target().rid(),
                    ConnectionViewModel.ConnectionType.FLYWIRE
                ));
            });
        }
    }
    
    private String getNodeName(Path nodePath) {
        return nodePath.getFileName() != null ? 
               nodePath.getFileName().toString() :
               PathUtils.toUnixString(nodePath);
    }
    
    private Set<ResourceIdentifier> extractInputs(Path nodePath) {
        Set<ResourceIdentifier> allInputs = new HashSet<>();
        
        // Use CalculationEngine to get domain object inputs
        try {
            CalculationNode node = calculationEngine.getNode(nodePath);
            if (node != null) {
                allInputs.addAll(node.inputs());
            }
        } catch (Exception e) {
            System.err.println("Failed to extract inputs from CalculationEngine for " + nodePath + ": " + e.getMessage());
        }
        
        // Add inputs from nodeEvaluationMap for completeness
        me.vincentzz.graph.model.NodeEvaluation nodeEvaluation = evaluationResult.nodeEvaluationMap().get(nodePath);
        if (nodeEvaluation != null) {
            allInputs.addAll(nodeEvaluation.inputs().keySet());
        }
        
        return allInputs;
    }
    /**
     * Calculate the inputs needed by a NodeGroup by analyzing its children.
     * A NodeGroup's inputs are resources needed by its children that come from outside the group.
     */
    private Set<ResourceIdentifier> calculateNodeGroupInputs(Path nodeGroupPath) {
        Set<ResourceIdentifier> groupInputs = new HashSet<>();
        Set<ResourceIdentifier> childrenProduced = new HashSet<>();
        Set<ResourceIdentifier> childrenConsumed = new HashSet<>();
        
        // Find all child nodes of this NodeGroup
        // Normalize path separators for cross-platform compatibility (Windows uses \, Mac uses /)
        String nodeGroupPathStr = PathUtils.toUnixString(nodeGroupPath);

        // Collect what children produce and consume from nodeEvaluationMap
        for (Map.Entry<Path, me.vincentzz.graph.model.NodeEvaluation> entry :
             evaluationResult.nodeEvaluationMap().entrySet()) {
            Path childPath = entry.getKey();
            String childPathStr = PathUtils.toUnixString(childPath);

            // Check if this is a direct child of the NodeGroup
            if (childPathStr.startsWith(nodeGroupPathStr + "/") &&
                !childPathStr.substring(nodeGroupPathStr.length() + 1).contains("/")) {
                
                me.vincentzz.graph.model.NodeEvaluation nodeEvaluation = entry.getValue();
                
                // This child produces these outputs
                childrenProduced.addAll(nodeEvaluation.outputs().keySet());
                
                // Check what this child consumes
                childrenConsumed.addAll(nodeEvaluation.inputs().keySet());
            }
        }
        
        // NodeGroup inputs = what children consume but don't produce within the group
        Set<ResourceIdentifier> externalInputs = new HashSet<>(childrenConsumed);
        externalInputs.removeAll(childrenProduced);
        groupInputs.addAll(externalInputs);
        
        return groupInputs;
    }
    
    private Set<ResourceIdentifier> extractOutputs(Path nodePath) {
        // Use CalculationEngine to get domain object outputs
        try {
            CalculationNode node = calculationEngine.getNode(nodePath);
            if (node != null) {
                return new HashSet<>(node.outputs());
            }
        } catch (Exception e) {
            System.err.println("Failed to extract outputs from CalculationEngine for " + nodePath + ": " + e.getMessage());
        }
        
        // Fallback: Get outputs from nodeEvaluationMap
        me.vincentzz.graph.model.NodeEvaluation nodeEvaluation = evaluationResult.nodeEvaluationMap().get(nodePath);
        
        return nodeEvaluation != null ? nodeEvaluation.outputs().keySet() : Set.of();
    }
    
    private boolean isNodeGroup(Path nodePath) {
        // Use CalculationEngine to check if this path corresponds to a NodeGroup
        try {
            CalculationNode node = calculationEngine.getNode(nodePath);
            return node instanceof NodeGroup;
        } catch (Exception e) {
            System.err.println("Failed to get node from CalculationEngine for " + nodePath + ": " + e.getMessage());
            return false;
        }
    }
    
    // Navigation methods
    public void navigateToPath(Path path) {
        this.currentPath = path;
    }
    
    public void navigateUp() {
        if (currentPath.getParent() != null) {
            currentPath = currentPath.getParent();
        }
    }
    
    public void navigateToChild(String childName) {
        Path childPath = currentPath.resolve(childName);
        if (nodeViewModels.containsKey(childPath)) {
            currentPath = childPath;
        }
    }
    
    // Getter methods for current view
    public List<NodeViewModel> getNodesForCurrentPath() {
        return nodeViewModels.values().stream()
            .filter(node -> isChildOfCurrentPath(node.getNodePath()))
            .collect(Collectors.toList());
    }
    
    public List<ConnectionViewModel> getConnectionsForCurrentPath() {
        Set<String> currentNodePaths = getNodesForCurrentPath().stream()
            .map(n -> PathUtils.toUnixString(n.getNodePath()))
            .collect(Collectors.toSet());

        return connections.stream()
            .filter(conn -> currentNodePaths.contains(PathUtils.toUnixString(conn.getSourcePath())) ||
                           currentNodePaths.contains(PathUtils.toUnixString(conn.getTargetPath())))
            .collect(Collectors.toList());
    }
    
    private boolean isChildOfCurrentPath(Path nodePath) {
        if (nodePath.getParent() == null) return false;
        String parentStr = PathUtils.toUnixString(nodePath.getParent());
        String currentStr = PathUtils.toUnixString(currentPath);
        return parentStr.equals(currentStr);
    }
    
    // Accessors
    public Path getCurrentPath() {
        return currentPath;
    }
    
    public EvaluationResult getEvaluationResult() {
        return evaluationResult;
    }
    
    public List<String> getPathSegments() {
        List<String> segments = new ArrayList<>();
        Path path = currentPath;
        
        while (path != null) {
            if (path.getFileName() != null) {
                segments.add(0, path.getFileName().toString());
            }
            path = path.getParent();
        }
        
        return segments;
    }
    
    public NodeViewModel getNodeViewModel(Path path) {
        return nodeViewModels.get(path);
    }
    
    public boolean canNavigateToChild(String childName) {
        Path childPath = currentPath.resolve(childName);
        NodeViewModel childNode = nodeViewModels.get(childPath);
        return childNode != null && childNode.isNodeGroup();
    }
    
}
