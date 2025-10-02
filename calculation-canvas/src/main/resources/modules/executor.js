// Graph execution engine
class GraphExecutor {
    constructor() {
        this.executionResults = new Map();
        this.isExecuting = false;
        this.executionOrder = [];
        this.currentStep = 0;
    }
    
    async evaluateNodeWithDependencies(targetNodeId) {
        if (this.isExecuting) {
            this.showMessage('Execution already in progress', 'warning');
            return;
        }
        
        try {
            this.isExecuting = true;
            
            const targetNode = window.nodeManager.getNode(targetNodeId);
            if (!targetNode) {
                this.showError(`Node ${targetNodeId} not found`);
                return;
            }
            
            this.updateStatus(`Evaluating ${targetNode.name} and dependencies...`);
            
            // Get all dependencies for this node
            const dependencies = this.getNodeDependencies(targetNodeId);
            dependencies.push(targetNodeId); // Include the target node itself
            
            // Get execution order for just these nodes
            const executionOrder = this.getExecutionOrderForNodes(dependencies);
            
            if (executionOrder.length === 0) {
                this.showMessage('No nodes to execute', 'warning');
                return;
            }
            
            // Set nodes to pending state before evaluation
            executionOrder.forEach(nodeId => {
                this.updateNodeResult(nodeId, 'Pending', '', false, false);
            });
            
            // Clear previous results for these nodes only
            executionOrder.forEach(nodeId => {
                if (this.executionResults.has(nodeId)) {
                    this.executionResults.delete(nodeId);
                }
            });
            
            // Execute nodes in order
            this.currentStep = 0;
            for (const nodeId of executionOrder) {
                this.currentStep++;
                await this.executeNode(nodeId);
            }
            
            this.updateStatus(`Evaluation of ${targetNode.name} completed successfully`);
            this.addResult('Evaluation Summary', `Evaluated ${executionOrder.length} nodes successfully`, 'success');
            
        } catch (error) {
            this.showError('Evaluation failed: ' + error.message);
            console.error('Evaluation error:', error);
        } finally {
            this.isExecuting = false;
        }
    }

    async executeGraph() {
        if (this.isExecuting) {
            this.showMessage('Execution already in progress', 'warning');
            return;
        }
        
        try {
            this.isExecuting = true;
            this.updateStatus('Executing graph...');
            
            // Clear previous results
            this.executionResults.clear();
            this.clearResults();
            
            // Get execution order (topological sort)
            this.executionOrder = this.getExecutionOrder();
            
            if (this.executionOrder.length === 0) {
                this.showMessage('No nodes to execute', 'warning');
                return;
            }
            
            // Validate graph before execution
            const validation = this.validateGraph();
            if (validation.errors.length > 0) {
                this.showError('Graph validation failed: ' + validation.errors.join(', '));
                return;
            }
            
            // Show warnings if any
            if (validation.warnings.length > 0) {
                this.addResult('Warnings:', validation.warnings.join('\n'), 'warning');
            }
            
            // Execute nodes in order
            this.currentStep = 0;
            for (const nodeId of this.executionOrder) {
                this.currentStep++;
                await this.executeNode(nodeId);
            }
            
            this.updateStatus('Execution completed successfully');
            this.addResult('Execution Summary', `Executed ${this.executionOrder.length} nodes successfully`, 'success');
            
        } catch (error) {
            this.showError('Execution failed: ' + error.message);
            console.error('Execution error:', error);
        } finally {
            this.isExecuting = false;
        }
    }
    
    async executeNode(nodeId) {
        const node = window.nodeManager.getNode(nodeId);
        if (!node) {
            throw new Error(`Node ${nodeId} not found`);
        }
        
        // Check if this is a group node
        if (node.isGroup && node.subgraph) {
            return await this.executeGroupNode(node);
        }
        
        try {
            this.updateStatus(`Executing ${node.name} (${this.currentStep}/${this.executionOrder.length})`);
            
            // Update node to show it's evaluating (show "Evaluating..." text with breathing ember animation)
            this.updateNodeResult(nodeId, 'Evaluating...', '', false, true);
            
            // Delay to show the evaluating state and breathing animation (configurable)
            const delayMs = parseInt(document.getElementById('evaluationDelay').value) || 0;
            if (delayMs > 0) {
                await new Promise(resolve => setTimeout(resolve, delayMs));
            }
            
            // Get input data from connected ports
            const inputData = this.getNodeInputData(nodeId);
            
            // Execute the node's code
            const result = await this.executeNodeCode(node, inputData);
            
            // Store results
            this.executionResults.set(nodeId, {
                node: node,
                inputs: inputData,
                outputs: result,
                timestamp: new Date(),
                success: true
            });
            
            // Update node result area with success
            const resultText = `Result: ${JSON.stringify(result, null, 2)}`;
            this.updateNodeResult(nodeId, 'Success', resultText, false);
            
            // Propagate data to connected outputs
            this.propagateNodeOutputs(nodeId, result);
            
            // Add to results display
            this.addResult(
                `${node.name} (${nodeId})`,
                `Inputs: ${JSON.stringify(inputData, null, 2)}\nOutputs: ${JSON.stringify(result, null, 2)}`,
                'success'
            );
            
        } catch (error) {
            // Store error result
            this.executionResults.set(nodeId, {
                node: node,
                inputs: this.getNodeInputData(nodeId),
                outputs: null,
                error: error.message,
                timestamp: new Date(),
                success: false
            });
            
            // Update node result area with error
            this.updateNodeResult(nodeId, 'Failed', error.message, true);
            
            this.addResult(
                `${node.name} (${nodeId}) - ERROR`,
                `Error: ${error.message}`,
                'error'
            );
            
            throw error; // Re-throw to stop execution
        }
    }
    
    async executeGroupNode(groupNode) {
        try {
            this.updateStatus(`Executing group ${groupNode.name} (${this.currentStep}/${this.executionOrder.length})`);
            
            // Update group node to show it's evaluating
            this.updateNodeResult(groupNode.id, 'Evaluating...', '', false, true);
            
            // Delay to show the evaluating state
            const delayMs = parseInt(document.getElementById('evaluationDelay').value) || 0;
            if (delayMs > 0) {
                await new Promise(resolve => setTimeout(resolve, delayMs));
            }
            
            // Get input data from connected ports to group node
            const groupInputData = this.getNodeInputData(groupNode.id);
            
            // Create a mini executor for the subgraph
            const subgraphResults = new Map();
            
            // Create virtual nodes from subgraph data
            const virtualNodes = new Map();
            groupNode.subgraph.nodes.forEach(nodeData => {
                virtualNodes.set(nodeData.id, {
                    id: nodeData.id,
                    name: nodeData.name,
                    code: nodeData.code,
                    inputPorts: nodeData.inputPorts,
                    outputPorts: nodeData.outputPorts
                });
            });
            
            // Get execution order for subgraph nodes
            const subgraphOrder = this.getSubgraphExecutionOrder(
                groupNode.subgraph.nodes.map(n => n.id),
                groupNode.subgraph.connections
            );
            
            // Execute subgraph nodes in order
            for (const nodeId of subgraphOrder) {
                const virtualNode = virtualNodes.get(nodeId);
                
                // Get inputs for this virtual node
                const nodeInputs = this.getSubgraphNodeInputs(
                    nodeId, 
                    groupNode.subgraph.connections, 
                    subgraphResults,
                    groupInputData,
                    groupNode
                );
                
                // Execute the virtual node
                const nodeResult = await this.executeNodeCode(virtualNode, nodeInputs);
                
                // Store result
                subgraphResults.set(nodeId, {
                    node: virtualNode,
                    inputs: nodeInputs,
                    outputs: nodeResult,
                    success: true
                });
            }
            
            // Collect outputs from subgraph nodes that match group output ports
            const groupOutputs = {};
            groupNode.outputPorts.forEach(outputPort => {
                // Find which subgraph node provides this output
                let outputValue = null;
                
                for (const [nodeId, result] of subgraphResults) {
                    if (result.outputs && result.outputs[outputPort] !== undefined) {
                        outputValue = result.outputs[outputPort];
                        break; // Use first match
                    }
                }
                
                groupOutputs[outputPort] = outputValue;
            });
            
            // Store group node results
            this.executionResults.set(groupNode.id, {
                node: groupNode,
                inputs: groupInputData,
                outputs: groupOutputs,
                subgraphResults: subgraphResults, // Store subgraph execution details
                timestamp: new Date(),
                success: true
            });
            
            // Update group node result display
            const resultText = `Group Result: ${JSON.stringify(groupOutputs, null, 2)}\n\nSubgraph executed ${subgraphOrder.length} nodes`;
            this.updateNodeResult(groupNode.id, 'Success', resultText, false);
            
            // Propagate group outputs
            this.propagateNodeOutputs(groupNode.id, groupOutputs);
            
            // Add to results display
            this.addResult(
                `${groupNode.name} (Group)`,
                `Group Inputs: ${JSON.stringify(groupInputData, null, 2)}\nGroup Outputs: ${JSON.stringify(groupOutputs, null, 2)}\nSubgraph Nodes Executed: ${subgraphOrder.length}`,
                'success'
            );
            
            return groupOutputs;
            
        } catch (error) {
            // Store error result
            this.executionResults.set(groupNode.id, {
                node: groupNode,
                inputs: this.getNodeInputData(groupNode.id),
                outputs: null,
                error: error.message,
                timestamp: new Date(),
                success: false
            });
            
            // Update group node result area with error
            this.updateNodeResult(groupNode.id, 'Failed', error.message, true);
            
            this.addResult(
                `${groupNode.name} (Group) - ERROR`,
                `Error: ${error.message}`,
                'error'
            );
            
            throw error;
        }
    }
    
    getSubgraphExecutionOrder(nodeIds, connections) {
        // Topological sort for subgraph nodes
        const graph = {};
        
        // Build adjacency list from subgraph connections
        connections.forEach(conn => {
            if (!graph[conn.fromNode]) {
                graph[conn.fromNode] = [];
            }
            graph[conn.fromNode].push(conn.toNode);
        });
        
        const visited = new Set();
        const stack = [];
        
        const visit = (nodeId) => {
            if (visited.has(nodeId)) return;
            visited.add(nodeId);
            
            const neighbors = graph[nodeId] || [];
            neighbors.forEach(neighbor => visit(neighbor));
            
            stack.push(nodeId);
        };
        
        // Visit all subgraph nodes
        nodeIds.forEach(nodeId => visit(nodeId));
        
        // Return in reverse order (topological order)
        return stack.reverse();
    }
    
    getSubgraphNodeInputs(nodeId, connections, subgraphResults, groupInputData, groupNode) {
        const virtualNode = groupNode.subgraph.nodes.find(n => n.id === nodeId);
        const inputs = {};
        
        virtualNode.inputPorts.forEach(portName => {
            // Find connections to this port within the subgraph
            const internalConnections = connections.filter(conn => 
                conn.toNode === nodeId && conn.toPort === portName
            );
            
            if (internalConnections.length > 0) {
                // Get data from internal connection
                const conn = internalConnections[0];
                const sourceResult = subgraphResults.get(conn.fromNode);
                
                if (sourceResult && sourceResult.outputs) {
                    inputs[portName] = sourceResult.outputs[conn.fromPort];
                } else {
                    inputs[portName] = null;
                }
            } else {
                // Check if this is an external input (group input port)
                if (groupInputData.hasOwnProperty(portName)) {
                    inputs[portName] = groupInputData[portName];
                } else {
                    inputs[portName] = this.getDefaultValue(portName);
                }
            }
        });
        
        return inputs;
    }
    
    getNodeInputData(nodeId) {
        const node = window.nodeManager.getNode(nodeId);
        const template = window.nodeManager.getNodeTemplate(node);
        const inputPorts = template ? template.inputPorts : [];
        const inputData = {};
        
        // Get data from each input port
        inputPorts.forEach(portName => {
            const connections = this.getConnectionsForPort(nodeId, portName, 'input');
            
            if (connections.length > 0) {
                // Get data from the first connection (for simplicity)
                const connection = connections[0];
                const sourceResult = this.executionResults.get(connection.fromNode);
                
                if (sourceResult && sourceResult.success && sourceResult.outputs) {
                    inputData[portName] = sourceResult.outputs[connection.fromPort];
                } else {
                    inputData[portName] = null;
                }
            } else {
                // No connection - provide default value
                inputData[portName] = this.getDefaultValue(portName);
            }
        });
        
        return inputData;
    }
    
    getDefaultValue(portName) {
        // Provide some default values for common port names
        switch (portName.toLowerCase()) {
            case 'data':
                return { value: 1, name: 'default' };
            case 'number':
            case 'value':
                return 42;
            case 'text':
            case 'string':
                return 'hello';
            case 'array':
            case 'list':
                return [1, 2, 3];
            case 'config':
                return { enabled: true };
            default:
                return null;
        }
    }
    
    async executeNodeCode(node, inputData) {
        try {
            // Create a safe execution context
            const context = this.createExecutionContext(inputData);
            
            // Get code from template-based node or legacy node
            const nodeCode = window.nodeManager.getNodeCode(node);
            
            // Wrap the user code in a function and execute it
            const wrappedCode = this.wrapUserCode(nodeCode);
            const executeFunction = new Function('inputs', 'context', wrappedCode);
            
            // Execute with timeout
            const result = await this.executeWithTimeout(
                () => executeFunction(inputData, context),
                5000 // 5 second timeout
            );
            
            // Validate result
            if (typeof result !== 'object' || result === null) {
                throw new Error('Node must return an object with output values');
            }
            
            // Check that all output ports are present in result
            const template = window.nodeManager.getNodeTemplate(node);
            const outputPorts = template ? template.outputPorts : ['result'];
            
            const missingPorts = outputPorts.filter(port => !(port in result));
            if (missingPorts.length > 0) {
                console.warn(`Node ${node.name} missing output ports: ${missingPorts.join(', ')}`);
                // Fill missing ports with null
                missingPorts.forEach(port => {
                    result[port] = null;
                });
            }
            
            return result;
            
        } catch (error) {
            throw new Error(`Code execution failed: ${error.message}`);
        }
    }
    
    wrapUserCode(userCode) {
        // Extract the function body or wrap entire code
        const trimmedCode = userCode.trim();
        
        if (trimmedCode.includes('function process')) {
            // User provided a process function - call it
            return `
                ${trimmedCode}
                return process(inputs);
            `;
        } else {
            // Assume the entire code is the function body
            return `
                try {
                    ${trimmedCode}
                } catch (e) {
                    throw new Error('Runtime error: ' + e.message);
                }
            `;
        }
    }
    
    createExecutionContext(inputData) {
        return {
            // Utility functions available to user code
            log: (...args) => console.log('[Node]', ...args),
            
            // Math utilities
            Math: Math,
            
            // Data transformation utilities
            clone: (obj) => JSON.parse(JSON.stringify(obj)),
            merge: (obj1, obj2) => ({ ...obj1, ...obj2 }),
            
            // Array utilities
            map: (arr, fn) => arr.map(fn),
            filter: (arr, fn) => arr.filter(fn),
            reduce: (arr, fn, initial) => arr.reduce(fn, initial),
            
            // Object utilities
            keys: Object.keys,
            values: Object.values,
            entries: Object.entries,
        };
    }
    
    executeWithTimeout(fn, timeout) {
        return new Promise((resolve, reject) => {
            const timer = setTimeout(() => {
                reject(new Error(`Execution timeout after ${timeout}ms`));
            }, timeout);
            
            try {
                const result = fn();
                clearTimeout(timer);
                resolve(result);
            } catch (error) {
                clearTimeout(timer);
                reject(error);
            }
        });
    }
    
    propagateNodeOutputs(nodeId, outputs) {
        // For each output port, propagate data to connected inputs
        Object.keys(outputs).forEach(portName => {
            const connections = this.getConnectionsForPort(nodeId, portName, 'output');
            connections.forEach(connection => {
                connection.data = outputs[portName];
            });
        });
    }
    
    // Helper methods to work with canvas manager connections
    getConnectionsForPort(nodeId, portName, type) {
        const connections = window.canvasManager.getConnections();
        if (type === 'input') {
            return connections.filter(conn => 
                conn.toNode === nodeId && conn.toPort === portName
            );
        } else {
            return connections.filter(conn => 
                conn.fromNode === nodeId && conn.fromPort === portName
            );
        }
    }
    
    getNodeDependencies(targetNodeId) {
        // Find all nodes that the target node depends on (directly or indirectly)
        const connections = window.canvasManager.getConnections();
        const dependencies = new Set();
        const visited = new Set();
        
        const findDependencies = (nodeId) => {
            if (visited.has(nodeId)) return;
            visited.add(nodeId);
            
            // Find all nodes that provide input to this node
            const inputConnections = connections.filter(conn => conn.toNode === nodeId);
            
            inputConnections.forEach(conn => {
                dependencies.add(conn.fromNode);
                findDependencies(conn.fromNode); // Recursively find dependencies
            });
        };
        
        findDependencies(targetNodeId);
        return Array.from(dependencies);
    }
    
    getExecutionOrderForNodes(nodeIds) {
        // Topological sort for a specific set of nodes
        const connections = window.canvasManager.getConnections();
        const nodeSet = new Set(nodeIds);
        
        // Filter connections to only include connections between our target nodes
        const relevantConnections = connections.filter(conn => 
            nodeSet.has(conn.fromNode) && nodeSet.has(conn.toNode)
        );
        
        const graph = {};
        
        // Build adjacency list for only our target nodes
        relevantConnections.forEach(conn => {
            if (!graph[conn.fromNode]) {
                graph[conn.fromNode] = [];
            }
            graph[conn.fromNode].push(conn.toNode);
        });
        
        const visited = new Set();
        const stack = [];
        
        const visit = (nodeId) => {
            if (visited.has(nodeId)) return;
            visited.add(nodeId);
            
            const neighbors = graph[nodeId] || [];
            neighbors.forEach(neighbor => visit(neighbor));
            
            stack.push(nodeId);
        };
        
        // Visit all target nodes
        nodeIds.forEach(nodeId => visit(nodeId));
        
        // Return in reverse order (topological order)
        return stack.reverse();
    }

    getExecutionOrder() {
        // Topological sort to determine execution order
        const connections = window.canvasManager.getConnections();
        const graph = {};
        
        // Build adjacency list
        connections.forEach(conn => {
            if (!graph[conn.fromNode]) {
                graph[conn.fromNode] = [];
            }
            graph[conn.fromNode].push(conn.toNode);
        });
        
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
        connections.forEach(conn => {
            allNodes.add(conn.fromNode);
            allNodes.add(conn.toNode);
        });
        
        // Visit all nodes
        allNodes.forEach(nodeId => visit(nodeId));
        
        // Return in reverse order (topological order)
        return stack.reverse();
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
            const template = window.nodeManager.getNodeTemplate(node);
            const inputPorts = template ? template.inputPorts : [];
            
            inputPorts.forEach(port => {
                const connections = this.getConnectionsForPort(node.id, port, 'input');
                if (connections.length === 0) {
                    warnings.push(`${node.name}.${port} has no input connection`);
                }
            });
        });
        
        // Check for unused output ports
        window.nodeManager.getNodes().forEach(node => {
            const template = window.nodeManager.getNodeTemplate(node);
            const outputPorts = template ? template.outputPorts : ['result'];
            
            outputPorts.forEach(port => {
                const connections = this.getConnectionsForPort(node.id, port, 'output');
                if (connections.length === 0) {
                    warnings.push(`${node.name}.${port} has no output connections`);
                }
            });
        });
        
        return { errors, warnings };
    }
    
    hasGlobalCycles() {
        const connections = window.canvasManager.getConnections();
        const graph = {};
        
        // Build adjacency list
        connections.forEach(conn => {
            if (!graph[conn.fromNode]) {
                graph[conn.fromNode] = [];
            }
            graph[conn.fromNode].push(conn.toNode);
        });
        
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
    
    updateStatus(message) {
        // Status element removed from UI, just log for debugging
        console.log('Status:', message);
    }
    
    showMessage(message, type = 'info') {
        this.updateStatus(message);
        setTimeout(() => {
            this.updateStatus('Ready');
        }, 3000);
    }
    
    showError(message) {
        this.updateStatus(message);
        this.addResult('Error', message, 'error');
        setTimeout(() => {
            this.updateStatus('Ready');
        }, 5000);
    }
    
    updateNodeResult(nodeId, status, content, isError = false, isEvaluating = false) {
        const nodeEl = document.querySelector(`[data-node-id="${nodeId}"]`);
        const resultArea = nodeEl?.querySelector('.node-result-area');
        const spinner = nodeEl?.querySelector('.node-spinner');
        
        if (!nodeEl || !resultArea) return;
        
        const statusEl = resultArea.querySelector('.result-status');
        const contentEl = resultArea.querySelector('.result-content');
        
        // Update node outline color based on status
        nodeEl.classList.remove('not-evaluated', 'pending', 'evaluating', 'success', 'error');
        
        if (isEvaluating || status === 'Evaluating...') {
            // Show breathing ember animation for evaluating nodes (amber)
            nodeEl.classList.add('evaluating');
            if (spinner) spinner.classList.add('visible');
        } else {
            if (spinner) spinner.classList.remove('visible');
            if (isError || status === 'Failed') {
                // Red for failed nodes
                nodeEl.classList.add('error');
            } else if (status === 'Success') {
                // Green for successful nodes
                nodeEl.classList.add('success');
            } else if (status === 'Pending') {
                // Blue breathing animation for pending nodes
                nodeEl.classList.add('pending');
            } else {
                // Grey for not evaluated or other states
                nodeEl.classList.add('not-evaluated');
            }
        }
        
        if (statusEl) {
            statusEl.textContent = status;
            statusEl.className = 'result-status';
            if (status === 'Pending') {
                statusEl.classList.add('pending');
            } else if (status === 'Evaluating...') {
                statusEl.classList.add('evaluating');
            } else if (isError || status === 'Failed') {
                statusEl.classList.add('error');
            } else if (status === 'Success') {
                statusEl.classList.add('success');
            }
        }
        
        if (contentEl) {
            contentEl.textContent = content;
            contentEl.className = 'result-content';
            if (isError) {
                contentEl.classList.add('error');
            } else {
                contentEl.classList.add('success');
            }
        }
        
        // Force canvas redraw after node layout changes to recalculate port positions
        if (window.canvasManager) {
            // Always add a small delay for canvas redraw to ensure DOM layout is completely stable
            // This is separate from evaluation delay and ensures visual accuracy
            const scheduleRedraw = (delay) => {
                setTimeout(() => {
                    // Force a complete layout recalculation before getting coordinates
                    nodeEl.getBoundingClientRect();
                    document.getElementById('workspace').getBoundingClientRect();
                    window.canvasManager.redraw();
                }, delay);
            };
            
            // Multiple redraw attempts with progressive delays
            scheduleRedraw(0);     // Immediate
            scheduleRedraw(10);    // Small delay for layout settling
            scheduleRedraw(50);    // Medium delay for complex changes
            scheduleRedraw(100);   // Final redraw for complete stability
            
            // Also use requestAnimationFrame for frame-aligned redraw
            requestAnimationFrame(() => {
                nodeEl.getBoundingClientRect(); // Force layout recalc
                window.canvasManager.redraw();
                
                // And one more on the next frame
                requestAnimationFrame(() => {
                    nodeEl.getBoundingClientRect(); // Force layout recalc
                    window.canvasManager.redraw();
                });
            });
        }
    }
    
    clearNodeResult(nodeId) {
        this.updateNodeResult(nodeId, 'Not evaluated', '');
    }
    
    clearNodeResults(nodeIds) {
        // Clear visual output and reset node states for specified nodes
        nodeIds.forEach(nodeId => {
            this.clearNodeResult(nodeId);
        });
    }
    
    clearResults() {
        const resultsContent = document.getElementById('resultsContent');
        if (resultsContent) {
            resultsContent.innerHTML = '<p>Executing graph...</p>';
        }
        this.showResultsPanel();
    }
    
    showResultsPanel() {
        const resultsPanel = document.getElementById('resultsPanel');
        if (resultsPanel && !resultsPanel.classList.contains('visible')) {
            resultsPanel.classList.add('visible');
            // No canvas resize needed since panel is now an overlay
        }
    }
    
    hideResultsPanel() {
        const resultsPanel = document.getElementById('resultsPanel');
        if (resultsPanel && resultsPanel.classList.contains('visible')) {
            resultsPanel.classList.remove('visible');
            // No canvas resize needed since panel is now an overlay
        }
    }
    
    addResult(title, content, type = 'info') {
        const resultsContent = document.getElementById('resultsContent');
        const resultsPanel = document.getElementById('resultsPanel');
        if (!resultsContent) return;
        
        // Show results panel on first result
        if (!resultsPanel.classList.contains('visible')) {
            this.showResultsPanel();
        }
        
        // Clear initial message if this is the first result
        if (resultsContent.innerHTML.includes('Executing graph...') || 
            resultsContent.innerHTML.includes('Run the graph to see results...')) {
            resultsContent.innerHTML = '';
        }
        
        const resultEl = document.createElement('div');
        resultEl.className = `result-item result-${type}`;
        
        const titleEl = document.createElement('div');
        titleEl.style.fontWeight = 'bold';
        titleEl.style.marginBottom = '4px';
        titleEl.textContent = title;
        
        const contentEl = document.createElement('div');
        contentEl.style.whiteSpace = 'pre-wrap';
        contentEl.textContent = content;
        
        resultEl.appendChild(titleEl);
        resultEl.appendChild(contentEl);
        
        resultsContent.appendChild(resultEl);
        
        // Auto-scroll to bottom - use resultsContent which has the scrollable area
        if (resultsContent) {
            // Use requestAnimationFrame to ensure the new content is rendered before scrolling
            requestAnimationFrame(() => {
                resultsContent.scrollTop = resultsContent.scrollHeight;
            });
        }
    }
    
    getResults() {
        return this.executionResults;
    }
    
    getResult(nodeId) {
        return this.executionResults.get(nodeId);
    }
    
    clearAllResults() {
        this.executionResults.clear();
        const resultsContent = document.getElementById('resultsContent');
        if (resultsContent) {
            resultsContent.innerHTML = '<p>Run the graph to see results...</p>';
        }
        this.hideResultsPanel();
    }
    
    exportResults() {
        const results = {};
        this.executionResults.forEach((result, nodeId) => {
            results[nodeId] = {
                nodeName: result.node.name,
                inputs: result.inputs,
                outputs: result.outputs,
                success: result.success,
                error: result.error,
                timestamp: result.timestamp
            };
        });
        
        return JSON.stringify(results, null, 2);
    }
}

// Global executor instance
window.graphExecutor = new GraphExecutor();
