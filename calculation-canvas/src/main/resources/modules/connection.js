// Connection management system
class ConnectionManager {
    constructor() {
        this.connections = [];
        this.tempConnection = null;
    }
    
    createConnection(fromNodeId, fromPort, toNodeId, toPort) {
        // Validate connection
        if (!this.isValidConnection(fromNodeId, fromPort, toNodeId, toPort)) {
            return null;
        }
        
        // Check if connection already exists
        if (this.connectionExists(fromNodeId, fromPort, toNodeId, toPort)) {
            return null;
        }
        
        const connection = {
            id: this.generateId(),
            fromNode: fromNodeId,
            fromPort: fromPort,
            toNode: toNodeId,
            toPort: toPort,
            data: null // Last data passed through this connection
        };
        
        this.connections.push(connection);
        return connection;
    }
    
    isValidConnection(fromNodeId, fromPort, toNodeId, toPort) {
        // Can't connect to same node
        if (fromNodeId === toNodeId) {
            return false;
        }
        
        // Check if nodes exist
        const fromNode = window.nodeManager.getNode(fromNodeId);
        const toNode = window.nodeManager.getNode(toNodeId);
        
        if (!fromNode || !toNode) {
            return false;
        }
        
        // Check if ports exist
        if (!fromNode.outputPorts.includes(fromPort) || !toNode.inputPorts.includes(toPort)) {
            return false;
        }
        
        // Check for cycles
        if (this.wouldCreateCycle(fromNodeId, toNodeId)) {
            return false;
        }
        
        return true;
    }
    
    connectionExists(fromNodeId, fromPort, toNodeId, toPort) {
        return this.connections.some(conn => 
            conn.fromNode === fromNodeId && 
            conn.fromPort === fromPort &&
            conn.toNode === toNodeId && 
            conn.toPort === toPort
        );
    }
    
    wouldCreateCycle(fromNodeId, toNodeId) {
        // Build adjacency list of current connections
        const graph = this.buildDependencyGraph();
        
        // Add the potential new connection
        if (!graph[fromNodeId]) graph[fromNodeId] = [];
        graph[fromNodeId].push(toNodeId);
        
        // Check for cycles using DFS
        const visited = new Set();
        const recursionStack = new Set();
        
        const hasCycle = (node) => {
            if (recursionStack.has(node)) return true;
            if (visited.has(node)) return false;
            
            visited.add(node);
            recursionStack.add(node);
            
            const neighbors = graph[node] || [];
            for (let neighbor of neighbors) {
                if (hasCycle(neighbor)) return true;
            }
            
            recursionStack.delete(node);
            return false;
        };
        
        // Check all nodes for cycles
        for (let nodeId of Object.keys(graph)) {
            if (!visited.has(nodeId)) {
                if (hasCycle(nodeId)) return true;
            }
        }
        
        return false;
    }
    
    buildDependencyGraph() {
        const graph = {};
        
        this.connections.forEach(conn => {
            if (!graph[conn.fromNode]) {
                graph[conn.fromNode] = [];
            }
            graph[conn.fromNode].push(conn.toNode);
        });
        
        return graph;
    }
    
    removeConnection(connectionId) {
        const index = this.connections.findIndex(conn => conn.id === connectionId);
        if (index > -1) {
            this.connections.splice(index, 1);
            return true;
        }
        return false;
    }
    
    removeConnectionsForNode(nodeId) {
        const toRemove = this.connections.filter(conn => 
            conn.fromNode === nodeId || conn.toNode === nodeId
        );
        
        toRemove.forEach(conn => this.removeConnection(conn.id));
        return toRemove.length;
    }
    
    getConnectionsFromNode(nodeId) {
        return this.connections.filter(conn => conn.fromNode === nodeId);
    }
    
    getConnectionsToNode(nodeId) {
        return this.connections.filter(conn => conn.toNode === nodeId);
    }
    
    getConnectionsForPort(nodeId, portName, type) {
        if (type === 'input') {
            return this.connections.filter(conn => 
                conn.toNode === nodeId && conn.toPort === portName
            );
        } else {
            return this.connections.filter(conn => 
                conn.fromNode === nodeId && conn.fromPort === portName
            );
        }
    }
    
    clearAll() {
        this.connections = [];
        this.tempConnection = null;
    }
    
    generateId() {
        return 'conn_' + Math.random().toString(36).substr(2, 9);
    }
    
    getExecutionOrder() {
        // Topological sort to determine execution order
        const graph = this.buildDependencyGraph();
        const visited = new Set();
        const stack = [];
        
        const visit = (nodeId) => {
            if (visited.has(nodeId)) return;
            visited.add(nodeId);
            
            const neighbors = graph[nodeId] || [];
            neighbors.forEach(neighbor => visit(neighbor));
            
            stack.push(nodeId);
        };
        
        // Get all nodes
        const allNodes = new Set();
        window.nodeManager.getNodes().forEach(node => allNodes.add(node.id));
        this.connections.forEach(conn => {
            allNodes.add(conn.fromNode);
            allNodes.add(conn.toNode);
        });
        
        // Visit all nodes
        allNodes.forEach(nodeId => visit(nodeId));
        
        // Return in reverse order (topological order)
        return stack.reverse();
    }
    
    propagateData(fromNodeId, fromPort, data) {
        const connections = this.getConnectionsFromNode(fromNodeId).filter(
            conn => conn.fromPort === fromPort
        );
        
        connections.forEach(connection => {
            connection.data = data;
        });
        
        return connections;
    }
    
    getInputData(nodeId) {
        const inputData = {};
        const node = window.nodeManager.getNode(nodeId);
        
        if (!node) return inputData;
        
        // Get data from all input connections
        node.inputPorts.forEach(portName => {
            const connections = this.getConnectionsForPort(nodeId, portName, 'input');
            
            if (connections.length > 0) {
                // For now, take the first connection's data
                // In a more complex system, you might merge or handle multiple inputs differently
                const connection = connections[0];
                inputData[portName] = connection.data;
            } else {
                inputData[portName] = null;
            }
        });
        
        return inputData;
    }
    
    validateGraph() {
        const errors = [];
        const warnings = [];
        
        // Check for cycles
        if (this.hasGlobalCycles()) {
            errors.push('Graph contains cycles');
        }
        
        // Check for disconnected input ports
        window.nodeManager.getNodes().forEach(node => {
            node.inputPorts.forEach(port => {
                const connections = this.getConnectionsForPort(node.id, port, 'input');
                if (connections.length === 0) {
                    warnings.push(`${node.name}.${port} has no input connection`);
                }
            });
        });
        
        // Check for unused output ports
        window.nodeManager.getNodes().forEach(node => {
            node.outputPorts.forEach(port => {
                const connections = this.getConnectionsForPort(node.id, port, 'output');
                if (connections.length === 0) {
                    warnings.push(`${node.name}.${port} has no output connections`);
                }
            });
        });
        
        return { errors, warnings };
    }
    
    hasGlobalCycles() {
        const graph = this.buildDependencyGraph();
        const visited = new Set();
        const recursionStack = new Set();
        
        const hasCycle = (node) => {
            if (recursionStack.has(node)) return true;
            if (visited.has(node)) return false;
            
            visited.add(node);
            recursionStack.add(node);
            
            const neighbors = graph[node] || [];
            for (let neighbor of neighbors) {
                if (hasCycle(neighbor)) return true;
            }
            
            recursionStack.delete(node);
            return false;
        };
        
        for (let nodeId of Object.keys(graph)) {
            if (!visited.has(nodeId)) {
                if (hasCycle(nodeId)) return true;
            }
        }
        
        return false;
    }
    
    getStats() {
        return {
            totalConnections: this.connections.length,
            totalNodes: window.nodeManager.getNodes().length,
            executionOrder: this.getExecutionOrder(),
            validation: this.validateGraph()
        };
    }
}

// Global connection manager instance
window.connectionManager = new ConnectionManager();
