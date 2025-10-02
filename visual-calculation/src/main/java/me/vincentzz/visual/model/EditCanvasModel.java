package me.vincentzz.visual.model;

import me.vincentzz.graph.node.builder.NodeBuilder;
import me.vincentzz.graph.node.builder.NodeGroupBuilder;
import me.vincentzz.graph.node.CalculationNode;
import me.vincentzz.graph.node.NodeGroup;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Canvas model for edit mode - works with NodeBuilder instead of evaluation results.
 * Shows direct input connections only since evaluation results are not available.
 */
public class EditCanvasModel {
    
    private NodeBuilder currentNodeBuilder;
    private Path currentPath;
    private Set<String> visibleNodeNames = new HashSet<>();
    
    public EditCanvasModel(NodeBuilder nodeBuilder, Path currentPath) {
        this.currentNodeBuilder = nodeBuilder;
        this.currentPath = currentPath;
    }
    
    /**
     * Get all nodes at current path for display.
     */
    public List<NodeViewModel> getNodes() {
        List<NodeViewModel> nodes = new ArrayList<>();
        
        if (currentNodeBuilder instanceof NodeGroupBuilder ngb) {
            // Use public method to get child nodes
            Set<CalculationNode> childNodes = ngb.nodes();
            
            for (CalculationNode childNode : childNodes) {
                String nodeName = childNode.name();
                
                // Filter by visibility - only include visible nodes
                if (!visibleNodeNames.isEmpty() && !visibleNodeNames.contains(nodeName)) {
                    continue; // Skip this node if it's not visible
                }
                
                // Create NodeViewModel for edit mode (no evaluation results)
                Path nodePath = currentPath.resolve(nodeName);
                boolean isNodeGroup = childNode instanceof NodeGroup;
                
                // In edit mode, we don't have evaluation results, so pass empty map
                Map<me.vincentzz.graph.model.ResourceIdentifier, me.vincentzz.lang.Result.Result<Object>> emptyResults = Map.of();
                
                NodeViewModel nodeViewModel = new NodeViewModel(
                    nodePath,
                    nodeName,
                    childNode.inputs(),
                    childNode.outputs(),
                    emptyResults,
                    isNodeGroup
                );
                
                nodes.add(nodeViewModel);
            }
        }
        
        return nodes;
    }
    
    /**
     * Get connections between visible nodes (direct inputs only in edit mode).
     */
    public List<ConnectionViewModel> getConnections() {
        List<ConnectionViewModel> connections = new ArrayList<>();
        
        if (currentNodeBuilder instanceof NodeGroupBuilder ngb) {
            // Get flywires (internal connections)
            var flywires = ngb.flywires();
            
            for (var flywire : flywires) {
                Path sourcePath = flywire.source().nodePath();
                Path targetPath = flywire.target().nodePath();
                
                // Only show connections between visible nodes
                String sourceName = sourcePath.getFileName() != null ? 
                                   sourcePath.getFileName().toString() : sourcePath.toString();
                String targetName = targetPath.getFileName() != null ? 
                                   targetPath.getFileName().toString() : targetPath.toString();
                
                if (visibleNodeNames.contains(sourceName) && visibleNodeNames.contains(targetName)) {
                    ConnectionViewModel connection = new ConnectionViewModel(
                        sourcePath,
                        flywire.source().rid(),
                        targetPath,
                        flywire.target().rid(),
                        ConnectionViewModel.ConnectionType.FLYWIRE
                    );
                    connections.add(connection);
                }
            }
        }
        
        return connections;
    }
    
    /**
     * Get inputs for the current path (extracted from the NodeBuilder).
     */
    public List<me.vincentzz.graph.model.ResourceIdentifier> getPathInputs() {
        try {
            CalculationNode currentNode = currentNodeBuilder.toNode();
            return new ArrayList<>(currentNode.inputs());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    /**
     * Get outputs for the current path (extracted from the NodeBuilder).
     */
    public List<me.vincentzz.graph.model.ResourceIdentifier> getPathOutputs() {
        try {
            CalculationNode currentNode = currentNodeBuilder.toNode();
            return new ArrayList<>(currentNode.outputs());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    /**
     * Set which nodes should be visible in the canvas.
     */
    public void setVisibleNodes(Set<String> visibleNodeNames) {
        this.visibleNodeNames = new HashSet<>(visibleNodeNames);
    }
    
    /**
     * Get the current NodeBuilder being edited.
     */
    public NodeBuilder getCurrentNodeBuilder() {
        return currentNodeBuilder;
    }
    
    /**
     * Get the current path.
     */
    public Path getCurrentPath() {
        return currentPath;
    }
    
    /**
     * Update the current NodeBuilder (when modifications are made).
     */
    public void updateNodeBuilder(NodeBuilder newBuilder) {
        this.currentNodeBuilder = newBuilder;
    }
    
    /**
     * Navigate to a child path within the current NodeBuilder.
     * TODO: This needs to be implemented properly when NodeGroupBuilder provides access to child builders.
     */
    public boolean navigateToChild(String childName) {
        if (currentNodeBuilder instanceof NodeGroupBuilder ngb) {
            // We can't access the private nodeMap field directly
            // For now, we'll need to rebuild the NodeBuilder or add public accessor methods
            // This is a limitation of the current NodeBuilder API
            
            // Check if child exists by looking at the nodes
            Set<CalculationNode> nodes = ngb.nodes();
            boolean childExists = nodes.stream().anyMatch(node -> node.name().equals(childName));
            
            if (childExists) {
                // TODO: Implement proper navigation when NodeGroupBuilder API supports it
                this.currentPath = currentPath.resolve(childName);
                // For now, we can't actually change the currentNodeBuilder
                return true;
            }
        }
        return false;
    }
    
    /**
     * Navigate back to parent path.
     */
    public boolean navigateToParent() {
        if (currentPath.getParent() != null) {
            // TODO: Implement proper parent navigation
            // This would require keeping track of the parent NodeBuilder
            return false;
        }
        return false;
    }
}
