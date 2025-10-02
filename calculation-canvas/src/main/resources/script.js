// Main application script
class GraphApp {
    constructor() {
        this.initEventListeners();
        this.initializeApp();
    }
    
    initEventListeners() {
        // Toolbar buttons
        document.getElementById('addNodeBtn').addEventListener('click', () => this.addNode());
        document.getElementById('groupNodesBtn').addEventListener('click', () => this.groupSelectedNodes());
        document.getElementById('exportBtn').addEventListener('click', () => this.exportGraph());
        document.getElementById('importBtn').addEventListener('click', () => this.importGraph());
        
        // Results panel buttons
        document.getElementById('clearResultsBtn').addEventListener('click', () => this.clearResults());
        
        // Keyboard shortcuts
        document.addEventListener('keydown', (e) => this.handleKeyboardShortcuts(e));
        
        // Window resize
        window.addEventListener('resize', () => this.handleResize());
        
        // Context menu prevention (optional)
        document.addEventListener('contextmenu', (e) => {
            if (e.target.closest('#workspace')) {
                e.preventDefault();
            }
        });
    }
    
    initializeApp() {
        this.updateStatus('Graph Computation Engine ready');
        
        // Initialize canvas size
        window.canvasManager.resizeCanvas();
        
        // Initialize template system
        this.initializeTemplateSystem();
        
        // Create some example nodes for demonstration
        this.createExampleGraph();
    }
    
    initializeTemplateSystem() {
        // Render templates in the panel
        setTimeout(() => {
            window.templateManager.renderTemplates();
            console.log('Templates rendered, checking event listeners...');
        }, 100);
        
        // Update Add Node button to show template usage hint
        const addNodeBtn = document.getElementById('addNodeBtn');
        if (addNodeBtn) {
            addNodeBtn.textContent = 'Add Custom Node';
            addNodeBtn.title = 'Add a custom node with manual configuration (or drag templates from the right panel)';
        }
    }
    
    addNode() {
        // Get workspace dimensions for positioning
        const workspace = document.getElementById('workspace');
        const rect = workspace.getBoundingClientRect();
        
        // Ensure minimum workspace size
        const minWidth = Math.max(rect.width, 800);
        const minHeight = Math.max(rect.height, 600);
        
        // Better positioning to avoid overlaps
        const existingNodes = window.nodeManager.getNodes();
        let x, y;
        let attempts = 0;
        const maxAttempts = 20;
        
        do {
            x = Math.random() * (minWidth - 250) + 50;
            y = Math.random() * (minHeight - 200) + 50;
            attempts++;
        } while (attempts < maxAttempts && this.isPositionOccupied(x, y, existingNodes));
        
        // If we couldn't find a free position, use a systematic approach
        if (attempts >= maxAttempts) {
            const gridSize = 220; // Node width + margin
            const cols = Math.floor(minWidth / gridSize);
            const nodeCount = existingNodes.length;
            const col = nodeCount % cols;
            const row = Math.floor(nodeCount / cols);
            
            x = col * gridSize + 50;
            y = row * 150 + 50; // Node height + margin
        }
        
        console.log(`Creating node at position (${x}, ${y})`);
        const node = window.nodeManager.createNode(x, y);
        this.updateStatus(`Created ${node.name} at (${Math.round(x)}, ${Math.round(y)})`);
        
        // Verify node was created and is visible
        setTimeout(() => {
            if (node.element && node.element.parentNode) {
                console.log(`Node ${node.name} successfully created and added to DOM`);
                window.codeEditor.openEditor(node);
            } else {
                console.error(`Node ${node.name} creation failed - element not in DOM`);
                this.updateStatus(`Error: Failed to create ${node.name}`);
            }
        }, 100);
    }
    
    isPositionOccupied(x, y, existingNodes) {
        const minDistance = 180; // Minimum distance between nodes
        
        return existingNodes.some(node => {
            const dx = Math.abs(node.x - x);
            const dy = Math.abs(node.y - y);
            return dx < minDistance && dy < minDistance;
        });
    }
    
    async runGraph() {
        try {
            this.updateStatus('Starting execution...');
            await window.graphExecutor.executeGraph();
        } catch (error) {
            this.updateStatus('Execution failed: ' + error.message);
            console.error('Graph execution error:', error);
        }
    }
    
    clearResults() {
        window.graphExecutor.clearAllResults();
        this.updateStatus('Cleared execution results');
    }
    
    clearAll() {
        if (confirm('Clear all nodes and connections? This cannot be undone.')) {
            window.nodeManager.clearAll();
            window.graphExecutor.clearAllResults();
            this.updateStatus('Cleared all nodes and connections');
        }
    }
    
    handleKeyboardShortcuts(e) {
        // Only handle shortcuts when not in input fields
        if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') {
            return;
        }
        
        // Check for modifier keys
        const ctrl = e.ctrlKey || e.metaKey;
        const shift = e.shiftKey;
        
        switch (e.key) {
            case 'n':
            case 'N':
                if (ctrl) {
                    e.preventDefault();
                    this.addNode();
                }
                break;
                
            case 'r':
            case 'R':
                if (ctrl) {
                    e.preventDefault();
                    this.runGraph();
                }
                break;
                
            case 'Delete':
            case 'Backspace':
                // Handled by nodeManager
                break;
                
            case 'Escape':
                // Deselect all
                window.nodeManager.deselectAll();
                break;
                
            case 's':
            case 'S':
                if (ctrl) {
                    e.preventDefault();
                    this.exportGraph();
                }
                break;
                
            case 'o':
            case 'O':
                if (ctrl) {
                    e.preventDefault();
                    this.importGraph();
                }
                break;
                
            case 'a':
            case 'A':
                if (ctrl) {
                    e.preventDefault();
                    this.selectAll();
                }
                break;
                
            case '0':
                if (ctrl) {
                    e.preventDefault();
                    window.canvasManager.resetZoom();
                    this.updateStatus('Reset zoom to 100%');
                }
                break;
                
            case 'Home':
                e.preventDefault();
                window.canvasManager.resetZoom();
                this.updateStatus('Reset zoom to 100%');
                break;
        }
    }
    
    handleResize() {
        // Resize canvas when window resizes
        window.canvasManager.resizeCanvas();
    }
    
    updateStatus(message) {
        const statusEl = document.getElementById('status');
        if (statusEl) {
            statusEl.textContent = message;
        }
    }
    
    groupSelectedNodes() {
        const selectedNodes = window.nodeManager.getSelectedNodes();
        
        if (selectedNodes.length < 2) {
            alert('Please select at least 2 nodes to group.');
            return;
        }
        
        try {
            // Calculate group position (center of selected nodes)
            const avgX = selectedNodes.reduce((sum, node) => sum + node.x, 0) / selectedNodes.length;
            const avgY = selectedNodes.reduce((sum, node) => sum + node.y, 0) / selectedNodes.length;
            
            // Analyze connections
            const analysis = this.analyzeGroupConnections(selectedNodes);
            
            // Create group name
            const groupName = `Group (${selectedNodes.map(n => n.name).join(', ')})`;
            
            // Create the group node
            const groupNode = window.nodeManager.createNode(avgX, avgY);
            
            // Store the original nodes in the group node
            groupNode.isGroup = true;
            groupNode.subgraph = {
                nodes: selectedNodes.map(node => ({
                    id: node.id,
                    name: node.name,
                    x: node.x - avgX, // Relative position
                    y: node.y - avgY,
                    code: node.code,
                    inputPorts: [...node.inputPorts],
                    outputPorts: [...node.outputPorts]
                })),
                connections: analysis.internalConnections
            };
            
            // Update group node properties
            window.nodeManager.updateNode(groupNode.id, {
                name: groupName,
                code: `// Group node containing: ${selectedNodes.map(n => n.name).join(', ')}
function process(inputs) {
  // This is a group node - edit to see subgraph
  return inputs;
}`,
                inputPorts: analysis.externalInputs,
                outputPorts: analysis.allOutputs
            });
            
            // Remove original nodes and their connections
            selectedNodes.forEach(node => {
                window.nodeManager.deleteNodeWithoutConfirmation(node.id);
            });
            
            // Create new connections to/from group node
            this.reconnectGroupNode(groupNode, analysis);
            
            // Select the new group node
            window.nodeManager.selectNode(groupNode);
            
            this.updateStatus(`Created group "${groupName}" with ${selectedNodes.length} nodes`);
            
        } catch (error) {
            console.error('Failed to group nodes:', error);
            alert('Failed to group nodes: ' + error.message);
        }
    }
    
    analyzeGroupConnections(selectedNodes) {
        const selectedNodeIds = new Set(selectedNodes.map(n => n.id));
        const allConnections = window.canvasManager.getConnections();
        
        const externalInputs = new Set();
        const allOutputs = new Set();
        const internalConnections = [];
        const externalConnections = {
            incoming: [], // connections from outside to group
            outgoing: []  // connections from group to outside
        };
        
        // Track which input ports are connected internally
        const internallyConnectedInputs = new Set();
        
        // Analyze all connections
        allConnections.forEach(conn => {
            const fromSelected = selectedNodeIds.has(conn.fromNode);
            const toSelected = selectedNodeIds.has(conn.toNode);
            
            if (fromSelected && toSelected) {
                // Internal connection (within group)
                internalConnections.push({
                    fromNode: conn.fromNode,
                    fromPort: conn.fromPort,
                    toNode: conn.toNode,
                    toPort: conn.toPort
                });
                // Mark this input port as internally connected
                internallyConnectedInputs.add(`${conn.toNode}:${conn.toPort}`);
            } else if (!fromSelected && toSelected) {
                // External input (from outside to group)
                externalInputs.add(conn.toPort);
                externalConnections.incoming.push({
                    fromNode: conn.fromNode,
                    fromPort: conn.fromPort,
                    toPort: conn.toPort // This will be the group's input port
                });
            } else if (fromSelected && !toSelected) {
                // External output (from group to outside)
                allOutputs.add(conn.fromPort);
                externalConnections.outgoing.push({
                    fromPort: conn.fromPort, // This will be the group's output port
                    toNode: conn.toNode,
                    toPort: conn.toPort
                });
            }
        });
        
        // Check for unconnected input ports and add them as external inputs
        selectedNodes.forEach(node => {
            node.inputPorts.forEach(port => {
                const portKey = `${node.id}:${port}`;
                if (!internallyConnectedInputs.has(portKey)) {
                    // This input port is not connected internally, so it should be a group input
                    externalInputs.add(port);
                }
            });
            
            // Add all output ports from selected nodes
            node.outputPorts.forEach(port => {
                allOutputs.add(port);
            });
        });
        
        return {
            externalInputs: Array.from(externalInputs),
            allOutputs: Array.from(allOutputs),
            internalConnections,
            externalConnections
        };
    }
    
    reconnectGroupNode(groupNode, analysis) {
        // Create incoming connections (external -> group)
        analysis.externalConnections.incoming.forEach(conn => {
            window.nodeManager.createConnection(
                conn.fromNode, conn.fromPort,
                groupNode.id, conn.toPort
            );
        });
        
        // Create outgoing connections (group -> external)
        analysis.externalConnections.outgoing.forEach(conn => {
            window.nodeManager.createConnection(
                groupNode.id, conn.fromPort,
                conn.toNode, conn.toPort
            );
        });
    }
    
    selectAll() {
        // Select all nodes (not implemented yet, could be added)
        console.log('Select all not implemented');
    }
    
    exportGraph() {
        try {
            const graphData = this.serializeGraph();
            const blob = new Blob([graphData], { type: 'application/json' });
            const url = URL.createObjectURL(blob);
            
            const a = document.createElement('a');
            a.href = url;
            a.download = 'graph-' + new Date().toISOString().slice(0, 19).replace(/:/g, '-') + '.json';
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);
            
            this.updateStatus('Graph exported successfully');
        } catch (error) {
            alert('Export failed: ' + error.message);
        }
    }
    
    importGraph() {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.json';
        
        input.onchange = (e) => {
            const file = e.target.files[0];
            if (!file) return;
            
            const reader = new FileReader();
            reader.onload = (e) => {
                try {
                    const graphData = JSON.parse(e.target.result);
                    this.deserializeGraph(graphData);
                    this.updateStatus('Graph imported successfully');
                } catch (error) {
                    alert('Import failed: ' + error.message);
                }
            };
            reader.readAsText(file);
        };
        
        input.click();
    }
    
    serializeGraph() {
        const nodes = window.nodeManager.getNodes().map(node => {
            const serializedNode = {
                id: node.id,
                name: node.name,
                x: node.x,
                y: node.y,
                code: node.code,
                inputPorts: node.inputPorts,
                outputPorts: node.outputPorts
            };
            
            // Include group-specific properties if this is a group node
            if (node.isGroup && node.subgraph) {
                serializedNode.isGroup = true;
                serializedNode.subgraph = {
                    nodes: node.subgraph.nodes.map(subNode => ({
                        id: subNode.id,
                        name: subNode.name,
                        x: subNode.x,
                        y: subNode.y,
                        code: subNode.code,
                        inputPorts: [...subNode.inputPorts],
                        outputPorts: [...subNode.outputPorts]
                    })),
                    connections: node.subgraph.connections.map(conn => ({
                        fromNode: conn.fromNode,
                        fromPort: conn.fromPort,
                        toNode: conn.toNode,
                        toPort: conn.toPort
                    }))
                };
            }
            
            return serializedNode;
        });
        
        const connections = window.canvasManager.getConnections().map(conn => ({
            id: conn.id,
            fromNode: conn.fromNode,
            fromPort: conn.fromPort,
            toNode: conn.toNode,
            toPort: conn.toPort
        }));
        
        return JSON.stringify({
            version: '1.0',
            timestamp: new Date().toISOString(),
            nodes: nodes,
            connections: connections
        }, null, 2);
    }
    
    deserializeGraph(graphData) {
        // Clear current graph
        window.nodeManager.clearAll();
        
        // Import nodes
        const nodeMap = new Map();
        graphData.nodes.forEach(nodeData => {
            const node = window.nodeManager.createNode(nodeData.x, nodeData.y);
            
            // Update node with imported data
            window.nodeManager.updateNode(node.id, {
                name: nodeData.name,
                code: nodeData.code,
                inputPorts: nodeData.inputPorts,
                outputPorts: nodeData.outputPorts
            });
            
            // Restore group properties if this is a group node
            if (nodeData.isGroup && nodeData.subgraph) {
                node.isGroup = true;
                node.subgraph = {
                    nodes: nodeData.subgraph.nodes.map(subNode => ({
                        id: subNode.id,
                        name: subNode.name,
                        x: subNode.x,
                        y: subNode.y,
                        code: subNode.code,
                        inputPorts: [...subNode.inputPorts],
                        outputPorts: [...subNode.outputPorts]
                    })),
                    connections: nodeData.subgraph.connections.map(conn => ({
                        fromNode: conn.fromNode,
                        fromPort: conn.fromPort,
                        toNode: conn.toNode,
                        toPort: conn.toPort
                    }))
                };
                
                // Update the visual representation to show ungroup button
                window.nodeManager.updateGroupNodeUI(node);
            }
            
            nodeMap.set(nodeData.id, node.id);
        });
        
        // Import connections
        graphData.connections.forEach(connData => {
            const fromNodeId = nodeMap.get(connData.fromNode);
            const toNodeId = nodeMap.get(connData.toNode);
            
            if (fromNodeId && toNodeId) {
                window.nodeManager.createConnection(
                    fromNodeId, connData.fromPort,
                    toNodeId, connData.toPort
                );
            }
        });
    }
    
    createExampleGraph() {
        // Create a simple example graph for demonstration
        try {
            // Input node
            const inputNode = window.nodeManager.createNode(100, 150);
            window.nodeManager.updateNode(inputNode.id, {
                name: 'Data Source',
                code: `function process(inputs) {
  return {
    data: {
      numbers: [1, 2, 3, 4, 5],
      multiplier: 2
    }
  };
}`,
                inputPorts: ['trigger'],
                outputPorts: ['data']
            });
            
            // Processing node
            const processNode = window.nodeManager.createNode(350, 150);
            window.nodeManager.updateNode(processNode.id, {
                name: 'Multiplier',
                code: `function process(inputs) {
  const data = inputs.data;
  const multiplied = data.numbers.map(n => n * data.multiplier);
  
  return {
    result: {
      original: data.numbers,
      multiplied: multiplied,
      sum: multiplied.reduce((a, b) => a + b, 0)
    }
  };
}`,
                inputPorts: ['data'],
                outputPorts: ['result']
            });
            
            // Output node
            const outputNode = window.nodeManager.createNode(600, 150);
            window.nodeManager.updateNode(outputNode.id, {
                name: 'Result Display',
                code: `function process(inputs) {
  const data = inputs.data;
  const summary = \`Original: [\${data.original.join(', ')}]
Multiplied: [\${data.multiplied.join(', ')}]
Sum: \${data.sum}\`;
  
  return {
    display: summary,
    total: data.sum
  };
}`,
                inputPorts: ['data'],
                outputPorts: ['display', 'total']
            });
            
            // Create connections
            setTimeout(() => {
                window.nodeManager.createConnection(inputNode.id, 'data', processNode.id, 'data');
                window.nodeManager.createConnection(processNode.id, 'result', outputNode.id, 'data');
                
                this.updateStatus('Example graph created. Click "▶️" on nodes to evaluate!');
            }, 500);
            
        } catch (error) {
            console.warn('Failed to create example graph:', error);
        }
    }
    
    // Utility methods
    showNotification(message, type = 'info', duration = 3000) {
        // Simple notification system (could be enhanced)
        this.updateStatus(message);
        
        setTimeout(() => {
            this.updateStatus('Ready');
        }, duration);
    }
    
    getGraphStats() {
        return {
            nodes: window.nodeManager.getNodes().length,
            connections: window.canvasManager.getConnections().length,
            lastExecution: window.graphExecutor.getResults().size > 0 ? 'Completed' : 'Not run'
        };
    }
}

// Initialize the application when the page loads
document.addEventListener('DOMContentLoaded', () => {
    window.graphApp = new GraphApp();
});

// Global error handler
window.addEventListener('error', (e) => {
    console.error('Global error:', e.error);
    const statusEl = document.getElementById('status');
    if (statusEl) {
        statusEl.textContent = 'Error: ' + e.error.message;
    }
});

// Handle unhandled promise rejections
window.addEventListener('unhandledrejection', (e) => {
    console.error('Unhandled promise rejection:', e.reason);
    e.preventDefault(); // Prevent default browser behavior
});
