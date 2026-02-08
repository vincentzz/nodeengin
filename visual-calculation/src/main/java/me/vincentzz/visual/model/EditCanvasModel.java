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
 * Supports navigation into child NodeGroups via a builder stack.
 */
public class EditCanvasModel {

    private NodeBuilder currentNodeBuilder;
    private Path currentPath;
    private Set<String> visibleNodeNames = new HashSet<>();

    /** Stack of (builder, path) entries for navigating back to parent levels. */
    private final Deque<Map.Entry<NodeBuilder, Path>> builderStack = new ArrayDeque<>();

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
     * Navigate into a child NodeGroup.
     * Pushes current state onto the builder stack and descends into the child.
     *
     * @return true if navigation succeeded
     */
    public boolean navigateToChild(String childName) {
        if (currentNodeBuilder instanceof NodeGroupBuilder ngb) {
            NodeBuilder childBuilder = ngb.getChildBuilder(childName);
            if (childBuilder != null) {
                // Push current state
                builderStack.push(Map.entry(currentNodeBuilder, currentPath));
                // Descend
                this.currentNodeBuilder = childBuilder;
                this.currentPath = currentPath.resolve(childName);
                this.visibleNodeNames.clear();
                return true;
            }
        }
        return false;
    }

    /**
     * Navigate back to parent level by popping the builder stack.
     *
     * @return true if navigation succeeded (stack was non-empty)
     */
    public boolean navigateToParent() {
        if (!builderStack.isEmpty()) {
            var entry = builderStack.pop();
            this.currentNodeBuilder = entry.getKey();
            this.currentPath = entry.getValue();
            this.visibleNodeNames.clear();
            return true;
        }
        return false;
    }

    /**
     * Navigate all the way back to the root by popping the entire stack.
     */
    public void navigateToRoot() {
        while (!builderStack.isEmpty()) {
            var entry = builderStack.pop();
            this.currentNodeBuilder = entry.getKey();
            this.currentPath = entry.getValue();
        }
        this.visibleNodeNames.clear();
    }

    /**
     * @return true if we can navigate up (stack is non-empty)
     */
    public boolean canNavigateUp() {
        return !builderStack.isEmpty();
    }

    /**
     * @return the depth of the navigation stack
     */
    public int getStackDepth() {
        return builderStack.size();
    }
}
