// Node management system
class NodeManager {
    constructor() {
        this.nodes = new Map();
        this.selectedNodes = new Set();
        this.dragging = null;
        this.nodeCounter = 0;
        this.connectionState = null; // For tracking connection creation
        this.rectangleSelection = null; // For tracking rectangle selection
        
        this.initEventListeners();
        this.createSelectionRectangle();
    }
    
    initEventListeners() {
        const container = document.getElementById('nodeContainer');
        
        // Mouse events for dragging and selection
        container.addEventListener('mousedown', (e) => this.handleMouseDown(e));
        document.addEventListener('mousemove', (e) => this.handleMouseMove(e));
        document.addEventListener('mouseup', (e) => this.handleMouseUp(e));
        
        // Keyboard events
        document.addEventListener('keydown', (e) => this.handleKeyDown(e));
    }
    
    createSelectionRectangle() {
        const selectionRect = document.createElement('div');
        selectionRect.className = 'selection-rectangle';
        selectionRect.id = 'selectionRectangle';
        document.getElementById('workspace').appendChild(selectionRect);
        this.selectionRect = selectionRect;
    }
    
    createNode(x = 100, y = 100, templateId = null, parameters = {}) {
        const nodeId = 'node_' + (++this.nodeCounter);
        const node = {
            id: nodeId,
            name: `Node ${this.nodeCounter}`,
            x: x,
            y: y,
            width: 250,
            height: null, // Auto height
            templateId: templateId || 'default', // Reference to template
            parameters: parameters || {}, // Template parameters
            element: null
        };
        
        this.nodes.set(nodeId, node);
        this.createNodeElement(node);
        return node;
    }
    
    createNodeElement(node) {
        const nodeEl = document.createElement('div');
        nodeEl.className = 'node not-evaluated';
        nodeEl.setAttribute('data-node-id', node.id);
        nodeEl.style.transform = `translate(${node.x}px, ${node.y}px)`;
        nodeEl.style.width = `${node.width}px`;
        if (node.height) {
            nodeEl.style.height = `${node.height}px`;
        }
        
        nodeEl.innerHTML = this.generateNodeHTML(node);
        
        // Add event listeners to the node element
        this.addNodeEventListeners(nodeEl, node);
        
        document.getElementById('nodeContainer').appendChild(nodeEl);
        node.element = nodeEl;
        
        return nodeEl;
    }
    
    generateNodeHTML(node) {
        // Get ports from template
        const template = this.getNodeTemplate(node);
        const inputPorts = template ? template.inputPorts : [];
        const outputPorts = template ? template.outputPorts : ['result'];
        
        const inputPortsHTML = inputPorts.map(port => 
            `<div class="port input-port" data-port="${port}">${port}</div>`
        ).join('');
        
        const outputPortsHTML = outputPorts.map(port => 
            `<div class="port output-port" data-port="${port}">${port}</div>`
        ).join('');
        
        // Different controls for group nodes vs regular nodes
        const controlsHTML = node.isGroup ? `
            <button class="node-control evaluate-btn" title="Evaluate">▶️</button>
            <button class="node-control edit-btn" title="Edit">✏️</button>
            <button class="node-control ungroup-btn" title="Ungroup">📤</button>
            <button class="node-control delete-btn" title="Delete">🗑️</button>
        ` : `
            <button class="node-control evaluate-btn" title="Evaluate">▶️</button>
            <button class="node-control edit-btn" title="Edit">✏️</button>
            <button class="node-control duplicate-btn" title="Duplicate">📋</button>
            <button class="node-control delete-btn" title="Delete">🗑️</button>
        `;
        
        return `
            <div class="node-spinner" data-node-id="${node.id}"></div>
            <div class="node-header">
                <span class="node-title">${node.name}</span>
                <div class="node-controls">
                    ${controlsHTML}
                </div>
            </div>
            <div class="node-body">
                <div class="node-ports">
                    <div class="input-ports">
                        ${inputPortsHTML}
                    </div>
                    <div class="output-ports">
                        ${outputPortsHTML}
                    </div>
                </div>
            </div>
            <div class="node-result-area" data-node-id="${node.id}">
                <div class="result-status">Not evaluated</div>
                <div class="result-content"></div>
            </div>
            <div class="node-resize-handle"></div>
        `;
    }
    
    addNodeEventListeners(nodeEl, node) {
        // Edit button
        const editBtn = nodeEl.querySelector('.edit-btn');
        if (editBtn) {
            editBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.editNode(node);
            });
        }
        
        // Duplicate button (only for regular nodes)
        const duplicateBtn = nodeEl.querySelector('.duplicate-btn');
        if (duplicateBtn) {
            duplicateBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.duplicateNode(node);
            });
        }
        
        // Evaluate button
        const evaluateBtn = nodeEl.querySelector('.evaluate-btn');
        if (evaluateBtn) {
            evaluateBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.evaluateNode(node);
            });
        }
        
        // Delete button
        const deleteBtn = nodeEl.querySelector('.delete-btn');
        if (deleteBtn) {
            deleteBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.deleteNode(node.id);
            });
        }
        
        // Ungroup button (only for group nodes)
        const ungroupBtn = nodeEl.querySelector('.ungroup-btn');
        if (ungroupBtn) {
            ungroupBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.ungroupNode(node);
            });
        }
        
        // Port click events for connections
        const ports = nodeEl.querySelectorAll('.port');
        ports.forEach(port => {
            port.addEventListener('mousedown', (e) => {
                e.stopPropagation();
                this.handlePortMouseDown(e, node, port);
            });
        });
        
        // Node selection is handled by the main handleMouseDown method
        // No need for a separate event listener here
        
        // Double click to edit
        nodeEl.addEventListener('dblclick', (e) => {
            e.stopPropagation();
            this.editNode(node);
        });
        
        // Resize handle
        const resizeHandle = nodeEl.querySelector('.node-resize-handle');
        if (resizeHandle) {
            resizeHandle.addEventListener('mousedown', (e) => {
                e.stopPropagation();
                this.startResize(e, node);
            });
        }
    }
    
    startResize(e, node) {
        const startX = e.clientX;
        const startY = e.clientY;
        const startWidth = node.width;
        const startHeight = node.element.offsetHeight;
        
        // Store initial height in node data
        node.height = startHeight;
        
        const handleResize = (e) => {
            const deltaX = e.clientX - startX;
            const deltaY = e.clientY - startY;
            
            // Calculate new dimensions
            const newWidth = Math.max(180, Math.min(500, startWidth + deltaX));
            
            // Calculate minimum height needed for all content by getting actual element heights
            const header = node.element.querySelector('.node-header');
            const body = node.element.querySelector('.node-body');
            const resultArea = node.element.querySelector('.node-result-area');
            const resultStatus = node.element.querySelector('.result-status');
            
            // Get actual heights of all elements
            const headerHeight = header ? header.offsetHeight : 0;
            const bodyHeight = body ? body.offsetHeight : 0;
            const resultStatusHeight = resultStatus ? resultStatus.offsetHeight : 0;
            
            // Add padding and margins from CSS with extra buffer
            const nodePadding = 8; // Border and internal spacing
            const resultAreaMinContentHeight = 50; // Minimum height for result content area
            const resultAreaPadding = 20; // Padding within result area + buffer
            const extraBuffer = 10; // Additional safety buffer
            
            // Calculate total minimum height ensuring result area stays within bounds
            const minHeight = headerHeight + bodyHeight + resultStatusHeight + resultAreaMinContentHeight + resultAreaPadding + nodePadding + extraBuffer;
            
            const newHeight = Math.max(minHeight, startHeight + deltaY);
            
            // Update node data
            node.width = newWidth;
            node.height = newHeight;
            
            // Apply new dimensions
            node.element.style.width = `${newWidth}px`;
            node.element.style.height = `${newHeight}px`;
            
            // Redraw connections
            window.canvasManager.redraw();
        };
        
        const handleResizeEnd = () => {
            document.removeEventListener('mousemove', handleResize);
            document.removeEventListener('mouseup', handleResizeEnd);
        };
        
        document.addEventListener('mousemove', handleResize);
        document.addEventListener('mouseup', handleResizeEnd);
    }
    
    handlePortMouseDown(e, node, portEl) {
        const portName = portEl.getAttribute('data-port');
        const isOutput = portEl.classList.contains('output-port');
        
        if (isOutput) {
            // Start connection from output port
            this.connectionState = {
                fromNode: node.id,
                fromPort: portName,
                connecting: true
            };
            
            portEl.classList.add('connecting');
            
            // Throttle mouse movement for better performance
            let lastMoveTime = 0;
            const moveThrottle = 8; // ~120fps for smooth connection drawing
            
            // Track mouse for temp connection
            const handleMouseMove = (e) => {
                const now = performance.now();
                if (now - lastMoveTime < moveThrottle) return;
                lastMoveTime = now;
                
                const workspace = document.getElementById('workspace');
                const rect = workspace.getBoundingClientRect();
                
                // Simple mouse coordinates relative to workspace
                const x = e.clientX - rect.left;
                const y = e.clientY - rect.top;
                
                window.canvasManager.updateTempConnection(x, y);
            };
            
            const handleMouseUp = (e) => {
                document.removeEventListener('mousemove', handleMouseMove);
                document.removeEventListener('mouseup', handleMouseUp);
                
                // Check if we're over an input port
                const targetEl = document.elementFromPoint(e.clientX, e.clientY);
                if (targetEl && targetEl.classList.contains('input-port')) {
                    const targetNodeEl = targetEl.closest('.node');
                    const targetNodeId = targetNodeEl.getAttribute('data-node-id');
                    const targetPortName = targetEl.getAttribute('data-port');
                    
                    if (targetNodeId !== node.id) {
                        this.createConnection(node.id, portName, targetNodeId, targetPortName);
                    }
                }
                
                portEl.classList.remove('connecting');
                window.canvasManager.clearTempConnection();
                this.connectionState = null;
            };
            
            // Initialize temp connection
            const workspace = document.getElementById('workspace');
            const rect = workspace.getBoundingClientRect();
            const x = e.clientX - rect.left;
            const y = e.clientY - rect.top;
            window.canvasManager.setTempConnection(node.id, portName, x, y);
            
            document.addEventListener('mousemove', handleMouseMove);
            document.addEventListener('mouseup', handleMouseUp);
        }
    }
    
    createConnection(fromNodeId, fromPort, toNodeId, toPort) {
        // Check if connection already exists
        const connections = window.canvasManager.getConnections();
        const exists = connections.some(conn => 
            conn.fromNode === fromNodeId && conn.fromPort === fromPort &&
            conn.toNode === toNodeId && conn.toPort === toPort
        );
        
        if (exists) return;
        
        // Check for cycles (basic check)
        if (this.wouldCreateCycle(fromNodeId, toNodeId)) {
            this.showMessage('Connection would create a cycle!', 'error');
            return;
        }
        
        // Create the connection
        const connection = window.canvasManager.addConnection(fromNodeId, fromPort, toNodeId, toPort);
        
        // Update port visual states
        const fromPortEl = this.getPortElement(fromNodeId, fromPort, 'output');
        const toPortEl = this.getPortElement(toNodeId, toPort, 'input');
        
        if (fromPortEl) fromPortEl.classList.add('connected');
        if (toPortEl) toPortEl.classList.add('connected');
        
        this.showMessage(`Connected ${fromNodeId}.${fromPort} → ${toNodeId}.${toPort}`, 'success');
    }
    
    wouldCreateCycle(fromNodeId, toNodeId) {
        // Simple cycle detection - check if toNode can reach fromNode
        const visited = new Set();
        const stack = [toNodeId];
        
        while (stack.length > 0) {
            const current = stack.pop();
            if (current === fromNodeId) return true;
            if (visited.has(current)) continue;
            
            visited.add(current);
            
            // Find all nodes that current outputs to
            const connections = window.canvasManager.getConnections();
            connections.forEach(conn => {
                if (conn.fromNode === current) {
                    stack.push(conn.toNode);
                }
            });
        }
        
        return false;
    }
    
    getPortElement(nodeId, portName, type) {
        const nodeEl = document.querySelector(`[data-node-id="${nodeId}"]`);
        if (!nodeEl) return null;
        
        const selector = type === 'input' ? '.input-port' : '.output-port';
        const ports = nodeEl.querySelectorAll(selector);
        
        for (let port of ports) {
            if (port.getAttribute('data-port') === portName) {
                return port;
            }
        }
        return null;
    }
    
    handleMouseDown(e) {
        if (e.target.classList.contains('port') || e.target.classList.contains('node-resize-handle')) {
            return;
        }
        
        const nodeEl = e.target.closest('.node');
        if (nodeEl) {
            const nodeId = nodeEl.getAttribute('data-node-id');
            const node = this.nodes.get(nodeId);
            
            if (node) {
                const isCtrlCmd = e.ctrlKey || e.metaKey;
                
                if (isCtrlCmd) {
                    // Ctrl/Cmd+click: Toggle selection
                    if (this.isNodeSelected(node)) {
                        // Remove from selection
                        console.log(`Ctrl+clicked on selected node "${node.name}" - removing from selection`);
                        this.selectedNodes.delete(node);
                        node.element.classList.remove('selected');
                    } else {
                        // Add to selection
                        console.log(`Ctrl+clicked on unselected node "${node.name}" - adding to selection`);
                        this.selectedNodes.add(node);
                        node.element.classList.add('selected');
                    }
                } else {
                    // Regular click
                    if (this.isNodeSelected(node)) {
                        // Clicking on selected node - keep current selection, just start dragging
                        console.log(`Clicked on selected node "${node.name}" - keeping selection`);
                    } else {
                        // Clicking on unselected node - select it and deselect others
                        console.log(`Clicked on unselected node "${node.name}" - selecting it`);
                        this.deselectAll();
                        this.selectedNodes.add(node);
                        node.element.classList.add('selected');
                    }
                }
                
                // Get current zoom and pan for coordinate transformation
                const zoom = window.canvasManager.getZoom();
                const pan = window.canvasManager.getPan();
                
                // Calculate offset accounting for zoom and pan
                const transformedMouseX = (e.clientX - pan.x) / zoom;
                const transformedMouseY = (e.clientY - pan.y) / zoom;
                
                // Start dragging - store offsets for all selected nodes
                this.dragging = {
                    startMouseX: transformedMouseX,
                    startMouseY: transformedMouseY,
                    nodeOffsets: new Map()
                };
                
                // Calculate offset for each selected node
                this.selectedNodes.forEach(selectedNode => {
                    this.dragging.nodeOffsets.set(selectedNode, {
                        startX: selectedNode.x,
                        startY: selectedNode.y
                    });
                });
            }
        } else {
            // Clicking on empty space - deselect all and start rectangle selection
            console.log('Clicked on empty space - deselecting all and starting rectangle selection');
            this.deselectAll();
            
            // Get workspace position for rectangle
            const workspace = document.getElementById('workspace');
            const rect = workspace.getBoundingClientRect();
            
            const startX = e.clientX - rect.left;
            const startY = e.clientY - rect.top;
            
            this.rectangleSelection = {
                startX: startX,
                startY: startY
            };
            
            // Show selection rectangle
            this.selectionRect.style.display = 'block';
            this.updateSelectionRectangle(startX, startY);
        }
    }
    
    handleMouseMove(e) {
        if (this.dragging) {
            // Throttle node movement for better performance
            const now = performance.now();
            if (!this.dragging.lastMoveTime) this.dragging.lastMoveTime = 0;
            
            const moveThrottle = 8; // ~120fps for smooth dragging
            if (now - this.dragging.lastMoveTime < moveThrottle) return;
            this.dragging.lastMoveTime = now;
            
            // Get current zoom and pan for coordinate transformation
            const zoom = window.canvasManager.getZoom();
            const pan = window.canvasManager.getPan();
            
            // Transform mouse coordinates to account for zoom and pan
            const transformedMouseX = (e.clientX - pan.x) / zoom;
            const transformedMouseY = (e.clientY - pan.y) / zoom;
            
            // Calculate mouse movement delta
            const deltaX = transformedMouseX - this.dragging.startMouseX;
            const deltaY = transformedMouseY - this.dragging.startMouseY;
            
            // Move all selected nodes by the same delta
            this.dragging.nodeOffsets.forEach((offset, node) => {
                const newX = offset.startX + deltaX;
                const newY = offset.startY + deltaY;
                this.moveNode(node, newX, newY);
            });
        } else if (this.rectangleSelection) {
            // Update rectangle selection
            const workspace = document.getElementById('workspace');
            const rect = workspace.getBoundingClientRect();
            const currentX = e.clientX - rect.left;
            const currentY = e.clientY - rect.top;
            
            this.updateSelectionRectangle(currentX, currentY);
        }
    }
    
    handleMouseUp(e) {
        if (this.rectangleSelection) {
            // Complete rectangle selection
            this.completeRectangleSelection();
            this.rectangleSelection = null;
            this.selectionRect.style.display = 'none';
        }
        
        this.dragging = null;
    }
    
    updateSelectionRectangle(currentX, currentY) {
        if (!this.rectangleSelection) return;
        
        const startX = this.rectangleSelection.startX;
        const startY = this.rectangleSelection.startY;
        
        // Calculate rectangle bounds
        const left = Math.min(startX, currentX);
        const top = Math.min(startY, currentY);
        const width = Math.abs(currentX - startX);
        const height = Math.abs(currentY - startY);
        
        // Update rectangle visual
        this.selectionRect.style.left = `${left}px`;
        this.selectionRect.style.top = `${top}px`;
        this.selectionRect.style.width = `${width}px`;
        this.selectionRect.style.height = `${height}px`;
    }
    
    completeRectangleSelection() {
        if (!this.rectangleSelection) return;
        
        console.log('=== RECTANGLE SELECTION COMPLETE ===');
        
        // Get rectangle bounds from the style properties
        const left = parseFloat(this.selectionRect.style.left);
        const top = parseFloat(this.selectionRect.style.top);
        const width = parseFloat(this.selectionRect.style.width);
        const height = parseFloat(this.selectionRect.style.height);
        
        const selectionLeft = left;
        const selectionTop = top;
        const selectionRight = left + width;
        const selectionBottom = top + height;
        
        console.log('Rectangle bounds:', {
            left: selectionLeft,
            top: selectionTop, 
            right: selectionRight,
            bottom: selectionBottom,
            width: width,
            height: height
        });
        
        const nodesInRectangle = [];
        
        // Find nodes within the rectangle
        this.nodes.forEach(node => {
            // Get the actual rendered position and size of the node
            const nodeEl = node.element;
            const nodeRect = nodeEl.getBoundingClientRect();
            const workspace = document.getElementById('workspace');
            const workspaceRect = workspace.getBoundingClientRect();
            
            // Convert to workspace coordinates
            const nodeLeft = nodeRect.left - workspaceRect.left;
            const nodeTop = nodeRect.top - workspaceRect.top;
            const nodeRight = nodeLeft + nodeRect.width;
            const nodeBottom = nodeTop + nodeRect.height;
            
            console.log(`Node "${node.name}" bounds:`, {
                left: nodeLeft,
                top: nodeTop,
                right: nodeRight,
                bottom: nodeBottom
            });
            
            // Check if node overlaps with selection rectangle
            const overlaps = !(nodeRight < selectionLeft || 
                             nodeLeft > selectionRight || 
                             nodeBottom < selectionTop || 
                             nodeTop > selectionBottom);
            
            console.log(`Node "${node.name}" overlaps: ${overlaps}`);
            
            if (overlaps) {
                nodesInRectangle.push(node.name);
                // For rectangle selection, always add nodes (don't toggle)
                this.selectedNodes.add(node);
                node.element.classList.add('selected');
            }
        });
        
        console.log('Nodes found in rectangle:', nodesInRectangle);
        console.log('Total selected nodes after rectangle selection:', Array.from(this.selectedNodes).map(n => n.name));
        console.log('=== END RECTANGLE SELECTION ===');
    }
    
    handleKeyDown(e) {
        if (e.key === 'Delete' && this.selectedNodes.size > 0) {
            // Delete all selected nodes
            const nodesToDelete = Array.from(this.selectedNodes);
            
            if (nodesToDelete.length === 1) {
                this.deleteNode(nodesToDelete[0].id);
            } else {
                // Multiple nodes - ask for confirmation
                const nodeNames = nodesToDelete.map(node => node.name).join(', ');
                if (confirm(`Are you sure you want to delete ${nodesToDelete.length} nodes (${nodeNames})?\n\nThis action cannot be undone.`)) {
                    nodesToDelete.forEach(node => {
                        this.deleteNodeWithoutConfirmation(node.id);
                    });
                    this.deselectAll();
                }
            }
        }
    }
    
    moveNode(node, x, y) {
        // Allow negative coordinates for flexible node positioning
        node.x = x;
        node.y = y;
        
        // Use transform for better performance than changing left/top
        node.element.style.transform = `translate(${node.x}px, ${node.y}px)`;
        
        // Redraw connections (now throttled)
        window.canvasManager.redraw();
    }
    
    selectNode(node, addToSelection = false) {
        if (!addToSelection) {
            this.deselectAll();
        }
        
        if (this.selectedNodes.has(node)) {
            // If node is already selected and we're adding to selection, deselect it
            if (addToSelection) {
                this.selectedNodes.delete(node);
                node.element.classList.remove('selected');
            }
        } else {
            // Add node to selection
            this.selectedNodes.add(node);
            node.element.classList.add('selected');
        }
    }
    
    deselectAll() {
        const selectedNodeNames = Array.from(this.selectedNodes).map(n => n.name);
        console.log('Deselecting all nodes:', selectedNodeNames);
        
        this.selectedNodes.forEach(node => {
            node.element.classList.remove('selected');
        });
        this.selectedNodes.clear();
        
        console.log('All nodes deselected');
    }
    
    getSelectedNodes() {
        return Array.from(this.selectedNodes);
    }
    
    isNodeSelected(node) {
        return this.selectedNodes.has(node);
    }
    
    duplicateNode(node) {
        // Create a new node ID
        const newNodeId = 'node_' + (++this.nodeCounter);
        
        // Create duplicate with offset position and "Copy" appended to name
        const duplicateNode = {
            id: newNodeId,
            name: node.name + ' Copy',
            x: node.x + 30, // Offset to the right
            y: node.y + 30, // Offset down
            code: node.code, // Copy the code exactly
            inputPorts: [...node.inputPorts], // Copy array
            outputPorts: [...node.outputPorts], // Copy array
            element: null
        };
        
        // Add to nodes map
        this.nodes.set(newNodeId, duplicateNode);
        
        // Create the visual element
        this.createNodeElement(duplicateNode);
        
        // Select the new node
        this.selectNode(duplicateNode);
        
        // Show feedback message
        this.showMessage(`Duplicated ${node.name} as ${duplicateNode.name}`, 'success');
        
        return duplicateNode;
    }
    
    evaluateNode(node) {
        // Evaluate this node and all its dependencies
        window.graphExecutor.evaluateNodeWithDependencies(node.id);
    }
    
    editNode(node) {
        window.codeEditor.openEditor(node);
    }
    
    updateNode(nodeId, updates) {
        const node = this.nodes.get(nodeId);
        if (!node) {
            console.error('Node not found for update:', nodeId);
            return;
        }
        
        try {
            // Store element reference and position
            const element = node.element;
            const currentTransform = element.style.transform;
            
            // Update node data
            Object.assign(node, updates);
            
            // Regenerate HTML safely
            const newHTML = this.generateNodeHTML(node);
            element.innerHTML = newHTML;
            
            // Restore position
            element.style.transform = currentTransform;
            
            // Re-attach event listeners
            this.addNodeEventListeners(element, node);
            
            // Update connections if ports changed
            requestAnimationFrame(() => {
                window.canvasManager.redraw();
            });
            
            console.log('Node updated successfully:', nodeId, node.name);
            
        } catch (error) {
            console.error('Error updating node:', nodeId, error);
            // Try to restore node if update failed
            this.showMessage(`Error updating node: ${error.message}`, 'error');
        }
    }
    
    updateGroupNodeUI(node) {
        // Update the visual representation of a group node to show the ungroup button
        if (!node.isGroup || !node.element) {
            return;
        }
        
        try {
            // Store element reference and position
            const element = node.element;
            const currentTransform = element.style.transform;
            
            // Regenerate HTML to include group-specific controls
            const newHTML = this.generateNodeHTML(node);
            element.innerHTML = newHTML;
            
            // Restore position
            element.style.transform = currentTransform;
            
            // Re-attach event listeners
            this.addNodeEventListeners(element, node);
            
            console.log('Group node UI updated successfully:', node.id, node.name);
            
        } catch (error) {
            console.error('Error updating group node UI:', node.id, error);
        }
    }
    
    deleteNode(nodeId) {
        const node = this.nodes.get(nodeId);
        if (!node) return;
        
        // Get connection count for confirmation message
        const connections = window.canvasManager.getConnectionsForNode(nodeId);
        const connectionCount = connections.length;
        
        // Create confirmation message
        let confirmMessage = `Are you sure you want to delete "${node.name}"?`;
        if (connectionCount > 0) {
            confirmMessage += `\n\nThis will also remove ${connectionCount} connection(s).`;
        }
        confirmMessage += '\n\nThis action cannot be undone.';
        
        // Show confirmation dialog
        if (!confirm(confirmMessage)) {
            return; // User cancelled deletion
        }
        
        this.deleteNodeWithoutConfirmation(nodeId);
    }
    
    deleteNodeWithoutConfirmation(nodeId) {
        const node = this.nodes.get(nodeId);
        if (!node) return;
        
        // Remove all connections for this node
        const connections = window.canvasManager.getConnectionsForNode(nodeId);
        connections.forEach(conn => {
            window.canvasManager.removeConnection(conn);
        });
        
        // Remove node element
        if (node.element) {
            node.element.remove();
        }
        
        // Remove from nodes map
        this.nodes.delete(nodeId);
        
        // Remove from selection if selected
        this.selectedNodes.delete(node);
        
        this.showMessage(`Deleted ${node.name}`, 'success');
    }
    
    ungroupNode(groupNode) {
        if (!groupNode.isGroup || !groupNode.subgraph) {
            alert('This is not a group node');
            return;
        }
        
        try {
            // Get external connections to the group node
            const groupConnections = window.canvasManager.getConnectionsForNode(groupNode.id);
            const externalInputConnections = groupConnections.filter(conn => conn.toNode === groupNode.id);
            const externalOutputConnections = groupConnections.filter(conn => conn.fromNode === groupNode.id);
            
            // Calculate absolute positions for restored nodes
            const groupX = groupNode.x;
            const groupY = groupNode.y;
            
            // Map to store old node IDs to new node IDs
            const nodeIdMap = new Map();
            const restoredNodes = [];
            
            // Recreate original nodes
            groupNode.subgraph.nodes.forEach(nodeData => {
                const newNodeId = 'node_' + (++this.nodeCounter);
                
                // Calculate absolute position
                const absoluteX = groupX + nodeData.x;
                const absoluteY = groupY + nodeData.y;
                
                const restoredNode = {
                    id: newNodeId,
                    name: nodeData.name,
                    x: absoluteX,
                    y: absoluteY,
                    width: 250,
                    height: null,
                    code: nodeData.code,
                    inputPorts: [...nodeData.inputPorts],
                    outputPorts: [...nodeData.outputPorts],
                    element: null
                };
                
                // Add to nodes map and create element
                this.nodes.set(newNodeId, restoredNode);
                this.createNodeElement(restoredNode);
                
                // Store mapping
                nodeIdMap.set(nodeData.id, newNodeId);
                restoredNodes.push(restoredNode);
            });
            
            // Recreate internal connections
            groupNode.subgraph.connections.forEach(conn => {
                const newFromNodeId = nodeIdMap.get(conn.fromNode);
                const newToNodeId = nodeIdMap.get(conn.toNode);
                
                if (newFromNodeId && newToNodeId) {
                    this.createConnection(newFromNodeId, conn.fromPort, newToNodeId, conn.toPort);
                }
            });
            
            // Recreate external input connections
            externalInputConnections.forEach(conn => {
                // Find which restored node should receive this input
                const targetPort = conn.toPort;
                
                // Find the restored node that has this input port
                const targetNode = restoredNodes.find(node => 
                    node.inputPorts.includes(targetPort)
                );
                
                if (targetNode) {
                    this.createConnection(conn.fromNode, conn.fromPort, targetNode.id, targetPort);
                }
            });
            
            // Recreate external output connections
            externalOutputConnections.forEach(conn => {
                // Find which restored node should provide this output
                const sourcePort = conn.fromPort;
                
                // Find the restored node that has this output port
                const sourceNode = restoredNodes.find(node => 
                    node.outputPorts.includes(sourcePort)
                );
                
                if (sourceNode) {
                    this.createConnection(sourceNode.id, sourcePort, conn.toNode, conn.toPort);
                }
            });
            
            // Remove the group node
            this.deleteNodeWithoutConfirmation(groupNode.id);
            
            // Select the restored nodes
            this.deselectAll();
            restoredNodes.forEach(node => {
                this.selectedNodes.add(node);
                node.element.classList.add('selected');
            });
            
            this.showMessage(`Ungrouped "${groupNode.name}" into ${restoredNodes.length} nodes`, 'success');
            
        } catch (error) {
            console.error('Failed to ungroup node:', error);
            alert('Failed to ungroup node: ' + error.message);
        }
    }
    
    clearAll() {
        this.nodes.forEach(node => {
            if (node.element) {
                node.element.remove();
            }
        });
        
        this.nodes.clear();
        this.selectedNodes.clear();
        this.nodeCounter = 0;
        
        window.canvasManager.clearAll();
    }
    
    showMessage(message, type = 'info') {
        // Status element removed from UI, just log for debugging
        console.log('Message:', message);
    }
    
    getNodes() {
        return Array.from(this.nodes.values());
    }
    
    getNode(nodeId) {
        return this.nodes.get(nodeId);
    }
    
    getNodeTemplate(node) {
        // Get template from template manager
        if (window.templateManager && node.templateId) {
            return window.templateManager.getTemplate(node.templateId);
        }
        
        // Return default template for legacy nodes
        return {
            id: 'default',
            name: 'Default Template',
            code: '// Write your processing code here\nfunction process(inputs) {\n  return {\n    result: 42\n  };\n}',
            inputPorts: [],
            outputPorts: ['result'],
            parameters: []
        };
    }
    
    getNodeCode(node) {
        // Get code from template and replace parameters
        const template = this.getNodeTemplate(node);
        if (!template) return 'function process(inputs) { return { result: 42 }; }';
        
        let code = template.code;
        
        // Replace parameter placeholders
        Object.entries(node.parameters).forEach(([name, value]) => {
            const placeholder = `{{{${name}}}}`;
            const replacement = typeof value === 'string' ? value : JSON.stringify(value);
            code = code.replace(new RegExp(placeholder.replace(/[{}]/g, '\\$&'), 'g'), replacement);
        });
        
        return code;
    }
    
    createNodeFromTemplate(templateId, parameters, x, y, name) {
        const template = window.templateManager.getTemplate(templateId);
        if (!template) {
            console.error('Template not found:', templateId);
            return null;
        }
        
        const node = this.createNode(x, y, templateId, parameters);
        
        // Update with template-based name
        this.updateNode(node.id, {
            name: name || this.generateNodeName(template, parameters)
        });
        
        return node;
    }
    
    generateNodeName(template, parameters) {
        let nodeName = template.name;
        if (Object.keys(parameters).length > 0) {
            const paramSummary = Object.entries(parameters)
                .map(([name, value]) => {
                    if (typeof value === 'string' && value.length > 20) {
                        return `${name}: "${value.substring(0, 20)}..."`;
                    }
                    return `${name}: ${JSON.stringify(value)}`;
                })
                .join(', ');
            nodeName = `${template.name} (${paramSummary})`;
        }
        return nodeName;
    }
}

// Global node manager instance
window.nodeManager = new NodeManager();
