// Code editor for node editing
class CodeEditor {
    constructor() {
        this.modal = document.getElementById('codeEditorModal');
        this.currentNode = null;
        
        this.initElements();
        this.initEventListeners();
    }
    
    initElements() {
        // Get modal elements
        this.nodeNameInput = document.getElementById('nodeNameInput');
        this.codeTextarea = document.getElementById('codeEditor');
        this.inputPortsInput = document.getElementById('inputPorts');
        this.outputPortsInput = document.getElementById('outputPorts');
        
        // Get buttons
        this.saveBtn = document.getElementById('saveNodeBtn');
        this.cancelBtn = document.getElementById('cancelNodeBtn');
        this.closeBtn = this.modal.querySelector('.close');
    }
    
    initEventListeners() {
        // Save button
        this.saveBtn.addEventListener('click', () => this.saveNode());
        
        // Cancel button
        this.cancelBtn.addEventListener('click', () => this.closeEditor());
        
        // Close button
        this.closeBtn.addEventListener('click', () => this.closeEditor());
        
        // Click outside modal to close
        this.modal.addEventListener('click', (e) => {
            if (e.target === this.modal) {
                this.closeEditor();
            }
        });
        
        // ESC key to close
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && this.modal.style.display === 'block') {
                this.closeEditor();
            }
        });
        
        // Auto-resize textarea
        this.codeTextarea.addEventListener('input', () => {
            this.autoResizeTextarea();
        });
        
        // Add some helpful shortcuts to the code editor
        this.codeTextarea.addEventListener('keydown', (e) => {
            this.handleCodeEditorKeydown(e);
        });
    }
    
    openEditor(node) {
        this.currentNode = node;
        
        // Check if this is a group node
        if (node.isGroup && node.subgraph) {
            this.openGroupEditor(node);
            return;
        }
        
        // Check if this is a template-based node
        if (node.templateId && node.templateId !== 'default') {
            this.openParameterEditor(node);
            return;
        }
        
        // Legacy node - show full editor
        this.openFullEditor(node);
    }
    
    openParameterEditor(node) {
        const template = window.nodeManager.getNodeTemplate(node);
        if (!template || !template.parameters || template.parameters.length === 0) {
            alert('This node has no configurable parameters.');
            return;
        }
        
        // Create parameter editing modal
        this.createParameterEditModal(node, template);
    }
    
    createParameterEditModal(node, template) {
        // Create parameter edit modal
        const modal = document.createElement('div');
        modal.className = 'modal';
        modal.id = 'parameterEditModal';
        
        modal.innerHTML = `
            <div class="modal-content">
                <div class="modal-header">
                    <h3>Edit ${node.name}</h3>
                    <span class="close">&times;</span>
                </div>
                <div class="modal-body">
                    <div class="editor-section">
                        <label>Node Name:</label>
                        <input type="text" id="nodeNameEdit" value="${node.name}" placeholder="Enter node name">
                    </div>
                    <hr style="margin: 16px 0; border: none; border-top: 1px solid #e9ecef;">
                    <h4 style="margin: 0 0 12px 0; color: #495057;">Template: ${template.name}</h4>
                    <p style="margin: 0 0 16px 0; color: #6c757d; font-size: 14px;">${template.description}</p>
                    ${this.generateParameterEditForm(template, node.parameters)}
                </div>
                <div class="modal-footer">
                    <button id="saveParametersBtn" class="btn btn-primary">Save Changes</button>
                    <button id="cancelParametersBtn" class="btn btn-secondary">Cancel</button>
                </div>
            </div>
        `;
        
        document.body.appendChild(modal);
        modal.style.display = 'block';
        
        // Add event listeners
        const closeBtn = modal.querySelector('.close');
        const saveBtn = modal.querySelector('#saveParametersBtn');
        const cancelBtn = modal.querySelector('#cancelParametersBtn');
        
        const closeModal = () => {
            modal.remove();
        };
        
        closeBtn.addEventListener('click', closeModal);
        cancelBtn.addEventListener('click', closeModal);
        
        saveBtn.addEventListener('click', () => {
            const newName = document.getElementById('nodeNameEdit').value.trim();
            const newParameters = this.collectParametersFromEdit(template);
            
            if (!newName) {
                alert('Node name is required');
                return;
            }
            
            // Update node
            window.nodeManager.updateNode(node.id, {
                name: newName,
                parameters: newParameters
            });
            
            closeModal();
        });
        
        // Focus first input
        const firstInput = modal.querySelector('input');
        if (firstInput) {
            setTimeout(() => firstInput.focus(), 100);
        }
    }
    
    generateParameterEditForm(template, currentParameters) {
        return template.parameters.map(param => {
            const inputId = `edit_param_${param.name}`;
            const currentValue = currentParameters[param.name] !== undefined ? 
                currentParameters[param.name] : param.default;
            
            let inputHTML = '';
            switch (param.type) {
                case 'text':
                    inputHTML = `<input type="text" id="${inputId}" value="${currentValue || ''}" placeholder="${param.description || ''}">`;
                    break;
                case 'number':
                    inputHTML = `<input type="number" id="${inputId}" value="${currentValue || 0}" placeholder="${param.description || ''}">`;
                    break;
                case 'json':
                    const jsonValue = typeof currentValue === 'string' ? currentValue : JSON.stringify(currentValue, null, 2);
                    inputHTML = `<textarea id="${inputId}" rows="4" placeholder="${param.description || ''}">${jsonValue}</textarea>`;
                    break;
                default:
                    inputHTML = `<input type="text" id="${inputId}" value="${currentValue || ''}" placeholder="${param.description || ''}">`;
            }
            
            return `
                <div class="editor-section">
                    <label for="${inputId}">${param.label}:</label>
                    ${inputHTML}
                    ${param.description ? `<small style="color: #6c757d; font-size: 12px;">${param.description}</small>` : ''}
                </div>
            `;
        }).join('');
    }
    
    collectParametersFromEdit(template) {
        const parameters = {};
        
        template.parameters.forEach(param => {
            const inputEl = document.getElementById(`edit_param_${param.name}`);
            if (inputEl) {
                let value = inputEl.value;
                
                // Parse based on type
                switch (param.type) {
                    case 'number':
                        value = parseFloat(value) || 0;
                        break;
                    case 'json':
                        try {
                            value = JSON.parse(value);
                        } catch (e) {
                            alert(`Invalid JSON for ${param.label}: ${e.message}`);
                            throw e;
                        }
                        break;
                    default:
                        // Keep as string
                        break;
                }
                
                parameters[param.name] = value;
            }
        });
        
        return parameters;
    }
    
    openFullEditor(node) {
        // Get template data for legacy nodes
        const template = window.nodeManager.getNodeTemplate(node);
        const inputPorts = template ? template.inputPorts : [];
        const outputPorts = template ? template.outputPorts : ['result'];
        const code = window.nodeManager.getNodeCode(node);
        
        // Populate form with node data
        this.nodeNameInput.value = node.name;
        this.codeTextarea.value = code;
        this.inputPortsInput.value = inputPorts.join(', ');
        this.outputPortsInput.value = outputPorts.join(', ');
        
        // Show modal - move it outside group editor if editing subgraph node
        this.modal.style.display = 'block';
        
        // If editing a subgraph node, ensure modal is at body level
        if (this.editingSubgraphNode) {
            // Store original parent for restoration
            this.originalModalParent = this.modal.parentNode;
            
            // Move modal to body to escape stacking context
            document.body.appendChild(this.modal);
            this.modal.style.setProperty('z-index', '9999', 'important');
        }
        
        // Focus on name input
        setTimeout(() => {
            this.nodeNameInput.focus();
            this.nodeNameInput.select();
        }, 100);
        
        // Auto-resize textarea
        this.autoResizeTextarea();
    }
    
    openGroupEditor(groupNode) {
        // Check if group editor is already open
        const existingModal = document.getElementById('groupEditorModal');
        if (existingModal) {
            return; // Don't create duplicate modal
        }
        
        // Create a special modal for group editing
        this.createGroupModal(groupNode);
    }
    
    createGroupModal(groupNode) {
        // Double-check that modal doesn't exist
        const existingModal = document.getElementById('groupEditorModal');
        if (existingModal) {
            existingModal.remove(); // Remove any existing modal
        }
        
        // Create group editor modal HTML
        const groupModal = document.createElement('div');
        groupModal.className = 'modal';
        groupModal.id = 'groupEditorModal';
        groupModal.innerHTML = `
            <div class="modal-content" style="width: 90%; max-width: 1200px; height: 80vh;">
                <div class="modal-header">
                    <h3>Edit Group: ${groupNode.name}</h3>
                    <span class="close">&times;</span>
                </div>
                <div class="modal-body" style="display: flex; flex-direction: column; height: 100%;">
                    <div style="display: flex; gap: 20px; margin-bottom: 10px;">
                        <div style="flex: 1;">
                            <label>Group Name:</label>
                            <input type="text" id="groupNameInput" value="${groupNode.name}" style="width: 100%; padding: 8px; margin-top: 5px;">
                        </div>
                        <div style="flex: 1;">
                            <label>Input Ports:</label>
                            <input type="text" id="groupInputPorts" value="${groupNode.inputPorts.join(', ')}" style="width: 100%; padding: 8px; margin-top: 5px;">
                        </div>
                        <div style="flex: 1;">
                            <label>Output Ports:</label>
                            <input type="text" id="groupOutputPorts" value="${groupNode.outputPorts.join(', ')}" style="width: 100%; padding: 8px; margin-top: 5px;">
                        </div>
                    </div>
                    <div style="flex: 1; border: 1px solid #ccc; position: relative; background: #f5f5f5;">
                        <div id="groupNodeContainer" style="position: relative; width: 100%; height: 100%;"></div>
                        <canvas id="groupCanvas" style="position: absolute; top: 0; left: 0; width: 100%; height: 100%; pointer-events: none; z-index: 10;"></canvas>
                    </div>
                </div>
                <div class="modal-footer">
                    <button id="saveGroupBtn" class="btn btn-primary">Save Group</button>
                    <button id="cancelGroupBtn" class="btn btn-secondary">Cancel</button>
                </div>
            </div>
        `;
        
        document.body.appendChild(groupModal);
        
        // Show modal
        groupModal.style.display = 'block';
        
        // Render subgraph
        this.renderSubgraph(groupNode);
        
        // Add event listeners
        this.addGroupModalListeners(groupModal, groupNode);
    }
    
    renderSubgraph(groupNode) {
        const container = document.getElementById('groupNodeContainer');
        const canvas = document.getElementById('groupCanvas');
        
        // Clear container
        container.innerHTML = '';
        
        // Set canvas size
        const rect = container.getBoundingClientRect();
        canvas.width = rect.width;
        canvas.height = rect.height;
        
        // Create a mini canvas manager for the subgraph
        this.subgraphCanvas = canvas.getContext('2d');
        
        // Initialize subgraph management
        this.initSubgraphManagement(container, canvas, groupNode);
        
        // Render nodes from subgraph
        groupNode.subgraph.nodes.forEach(nodeData => {
            const nodeEl = this.createSubgraphNode(nodeData, container);
            // Position relative to center of group editor with scale
            const centerX = rect.width / 2;
            const centerY = rect.height / 2;
            nodeEl.style.transform = `translate(${centerX + nodeData.x}px, ${centerY + nodeData.y}px) scale(0.8)`;
        });
        
        // Draw connections after nodes are positioned and rendered
        requestAnimationFrame(() => {
            setTimeout(() => {
                this.drawSubgraphConnections(groupNode.subgraph.connections, container);
            }, 300);
        });
    }
    
    initSubgraphManagement(container, canvas, groupNode) {
        // Store subgraph state
        this.subgraphState = {
            selectedNodes: new Set(),
            dragging: null,
            groupNode: groupNode,
            container: container,
            canvas: canvas
        };
        
        // Create bound event handlers for proper cleanup
        this.subgraphMouseMoveHandler = (e) => this.handleSubgraphMouseMove(e);
        this.subgraphMouseUpHandler = (e) => this.handleSubgraphMouseUp(e);
        
        // Add mouse event listeners for subgraph interaction
        container.addEventListener('mousedown', (e) => this.handleSubgraphMouseDown(e));
        
        // Store references for cleanup
        this.subgraphState.mouseMoveHandler = this.subgraphMouseMoveHandler;
        this.subgraphState.mouseUpHandler = this.subgraphMouseUpHandler;
    }
    
    handleSubgraphMouseDown(e) {
        const nodeEl = e.target.closest('.subgraph-node');
        if (nodeEl) {
            const nodeId = nodeEl.getAttribute('data-node-id');
            
            // Node selection logic
            if (!e.ctrlKey && !e.metaKey) {
                // Single select - clear others and select this one
                this.subgraphState.selectedNodes.clear();
                document.querySelectorAll('.subgraph-node').forEach(el => {
                    el.classList.remove('selected');
                });
            }
            
            // Toggle selection or add to selection
            if (this.subgraphState.selectedNodes.has(nodeId)) {
                if (e.ctrlKey || e.metaKey) {
                    this.subgraphState.selectedNodes.delete(nodeId);
                    nodeEl.classList.remove('selected');
                }
            } else {
                this.subgraphState.selectedNodes.add(nodeId);
                nodeEl.classList.add('selected');
            }
            
            // Start dragging
            const rect = this.subgraphState.container.getBoundingClientRect();
            this.subgraphState.dragging = {
                startX: e.clientX - rect.left,
                startY: e.clientY - rect.top,
                selectedNodes: new Set(this.subgraphState.selectedNodes)
            };
            
            // Add document-level event listeners only when dragging starts
            document.addEventListener('mousemove', this.subgraphState.mouseMoveHandler);
            document.addEventListener('mouseup', this.subgraphState.mouseUpHandler);
            
            e.preventDefault();
        } else {
            // Clicked on empty space - deselect all
            this.subgraphState.selectedNodes.clear();
            document.querySelectorAll('.subgraph-node').forEach(el => {
                el.classList.remove('selected');
            });
        }
    }
    
    handleSubgraphMouseMove(e) {
        if (this.subgraphState.dragging) {
            const rect = this.subgraphState.container.getBoundingClientRect();
            const currentX = e.clientX - rect.left;
            const currentY = e.clientY - rect.top;
            
            const deltaX = currentX - this.subgraphState.dragging.startX;
            const deltaY = currentY - this.subgraphState.dragging.startY;
            
            // Get container dimensions for boundary checking
            const containerWidth = rect.width;
            const containerHeight = rect.height;
            
            // Move all selected nodes with boundary constraints
            this.subgraphState.dragging.selectedNodes.forEach(nodeId => {
                const nodeEl = document.querySelector(`[data-node-id="${nodeId}"]`);
                if (nodeEl && nodeEl.classList.contains('subgraph-node')) {
                    // Get current transform
                    const transform = nodeEl.style.transform;
                    const translateMatch = transform.match(/translate\(([^,]+),\s*([^)]+)\)/);
                    if (translateMatch) {
                        const currentX = parseFloat(translateMatch[1]);
                        const currentY = parseFloat(translateMatch[2]);
                        const proposedX = currentX + deltaX;
                        const proposedY = currentY + deltaY;
                        
                        // Get node dimensions (accounting for scale)
                        const nodeWidth = nodeEl.offsetWidth * 0.8;
                        const nodeHeight = nodeEl.offsetHeight * 0.8;
                        
                        // Apply boundary constraints
                        const minX = 0;
                        const maxX = containerWidth - nodeWidth;
                        const minY = 0;
                        const maxY = containerHeight - nodeHeight;
                        
                        const constrainedX = Math.max(minX, Math.min(maxX, proposedX));
                        const constrainedY = Math.max(minY, Math.min(maxY, proposedY));
                        
                        nodeEl.style.transform = `translate(${constrainedX}px, ${constrainedY}px) scale(0.8)`;
                    }
                }
            });
            
            this.subgraphState.dragging.startX = currentX;
            this.subgraphState.dragging.startY = currentY;
            
            // Redraw connections
            setTimeout(() => {
                this.drawSubgraphConnections(this.subgraphState.groupNode.subgraph.connections, this.subgraphState.container);
            }, 16);
        }
    }
    
    handleSubgraphMouseUp(e) {
        this.subgraphState.dragging = null;
        
        // Clean up document-level event listeners
        document.removeEventListener('mousemove', this.subgraphState.mouseMoveHandler);
        document.removeEventListener('mouseup', this.subgraphState.mouseUpHandler);
    }
    
    createSubgraphNode(nodeData, container) {
        const nodeEl = document.createElement('div');
        nodeEl.className = 'node not-evaluated subgraph-node';
        nodeEl.setAttribute('data-node-id', nodeData.id);
        nodeEl.style.width = '200px';
        
        // Generate the HTML manually to ensure all parts are included
        const inputPortsHTML = nodeData.inputPorts.map(port => 
            `<div class="port input-port" data-port="${port}">${port}</div>`
        ).join('');
        
        const outputPortsHTML = nodeData.outputPorts.map(port => 
            `<div class="port output-port" data-port="${port}">${port}</div>`
        ).join('');
        
        nodeEl.innerHTML = `
            <div class="node-spinner" data-node-id="${nodeData.id}"></div>
            <div class="node-header">
                <span class="node-title">${nodeData.name}</span>
                <div class="node-controls">
                    <button class="node-control evaluate-btn" title="Evaluate">▶️</button>
                    <button class="node-control edit-btn" title="Edit">✏️</button>
                    <button class="node-control duplicate-btn" title="Duplicate">📋</button>
                    <button class="node-control delete-btn" title="Delete">🗑️</button>
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
            <div class="node-result-area" data-node-id="${nodeData.id}">
                <div class="result-status">Not evaluated</div>
                <div class="result-content"></div>
            </div>
            <div class="node-resize-handle"></div>
        `;
        
        // Add event listeners to make buttons functional
        this.addSubgraphNodeEventListeners(nodeEl, nodeData);
        
        // Add subgraph-specific styling
        nodeEl.classList.add('subgraph-node');
        
        container.appendChild(nodeEl);
        return nodeEl;
    }
    
    addSubgraphNodeEventListeners(nodeEl, node) {
        // Add event listeners for subgraph node buttons with actual functionality
        const editBtn = nodeEl.querySelector('.edit-btn');
        if (editBtn) {
            editBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.editSubgraphNode(node);
            });
        }
        
        const duplicateBtn = nodeEl.querySelector('.duplicate-btn');
        if (duplicateBtn) {
            duplicateBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.updateSubgraphNodeStatus(nodeEl, 'Duplicated', 'Node copied');
            });
        }
        
        const evaluateBtn = nodeEl.querySelector('.evaluate-btn');
        if (evaluateBtn) {
            evaluateBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.evaluateSubgraphNodeWithDependencies(node.id);
            });
        }
        
        const deleteBtn = nodeEl.querySelector('.delete-btn');
        if (deleteBtn) {
            deleteBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                if (confirm(`Delete ${node.name} from subgraph?`)) {
                    this.updateSubgraphNodeStatus(nodeEl, 'Deleted', 'Node marked for deletion');
                    nodeEl.style.opacity = '0.5';
                }
            });
        }
        
        const ungroupBtn = nodeEl.querySelector('.ungroup-btn');
        if (ungroupBtn) {
            ungroupBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.updateSubgraphNodeStatus(nodeEl, 'Ungrouped', 'Node ungrouped');
            });
        }
        
        // Resize handle functionality
        const resizeHandle = nodeEl.querySelector('.node-resize-handle');
        if (resizeHandle) {
            resizeHandle.addEventListener('mousedown', (e) => {
                e.stopPropagation();
                this.startSubgraphNodeResize(e, nodeEl, node);
            });
        }
    }
    
    updateSubgraphNodeStatus(nodeEl, status, content, isEvaluating = false) {
        const statusEl = nodeEl.querySelector('.result-status');
        const contentEl = nodeEl.querySelector('.result-content');
        
        if (statusEl) {
            statusEl.textContent = status;
            statusEl.className = 'result-status';
            
            if (isEvaluating) {
                statusEl.classList.add('evaluating');
                nodeEl.classList.remove('not-evaluated', 'success', 'error');
                nodeEl.classList.add('evaluating');
            } else if (status === 'Success') {
                statusEl.classList.add('success');
                nodeEl.classList.remove('not-evaluated', 'evaluating', 'error');
                nodeEl.classList.add('success');
            } else {
                nodeEl.classList.remove('not-evaluated', 'evaluating', 'success', 'error');
            }
        }
        
        if (contentEl) {
            contentEl.textContent = content;
        }
    }
    
    drawSubgraphConnections(connections, container) {
        const canvas = document.getElementById('groupCanvas');
        const ctx = canvas.getContext('2d');
        
        console.log('Drawing subgraph connections:', connections);
        
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.strokeStyle = '#3498db';
        ctx.lineWidth = 2;
        
        connections.forEach((conn, index) => {
            console.log(`Connection ${index}:`, conn);
            
            const fromNode = container.querySelector(`[data-node-id="${conn.fromNode}"]`);
            const toNode = container.querySelector(`[data-node-id="${conn.toNode}"]`);
            
            console.log('Nodes found:', {
                fromNode: fromNode ? 'Found' : 'Not found',
                toNode: toNode ? 'Found' : 'Not found'
            });
            
            if (fromNode && toNode) {
                const fromPort = fromNode.querySelector(`.output-port[data-port="${conn.fromPort}"]`);
                const toPort = toNode.querySelector(`.input-port[data-port="${conn.toPort}"]`);
                
                console.log('Ports found:', {
                    fromPort: fromPort ? 'Found' : 'Not found',
                    toPort: toPort ? 'Found' : 'Not found'
                });
                
                if (fromPort && toPort) {
                    // Get the container bounds for coordinate calculation
                    const containerRect = container.getBoundingClientRect();
                    
                    // Get port positions relative to container
                    const fromPortRect = fromPort.getBoundingClientRect();
                    const toPortRect = toPort.getBoundingClientRect();
                    
                    // Calculate connection points
                    const fromX = (fromPortRect.right - containerRect.left);
                    const fromY = (fromPortRect.top + fromPortRect.height / 2 - containerRect.top);
                    const toX = (toPortRect.left - containerRect.left);
                    const toY = (toPortRect.top + toPortRect.height / 2 - containerRect.top);
                    
                    console.log('Connection coordinates:', {
                        fromX, fromY, toX, toY
                    });
                    
                    // Draw bezier curve
                    const controlOffset = Math.min(Math.abs(toX - fromX) / 2, 60);
                    ctx.beginPath();
                    ctx.moveTo(fromX, fromY);
                    ctx.bezierCurveTo(
                        fromX + controlOffset, fromY,
                        toX - controlOffset, toY,
                        toX, toY
                    );
                    ctx.stroke();
                    
                    console.log('Connection drawn successfully');
                } else {
                    console.log('Ports not found for connection:', conn);
                }
            } else {
                console.log('Nodes not found for connection:', conn);
            }
        });
    }
    
    addGroupModalListeners(modal, groupNode) {
        const closeBtn = modal.querySelector('.close');
        const saveBtn = modal.querySelector('#saveGroupBtn');
        const cancelBtn = modal.querySelector('#cancelGroupBtn');
        
        const closeModal = () => {
            // Clean up any remaining subgraph event listeners
            if (this.subgraphState) {
                document.removeEventListener('mousemove', this.subgraphState.mouseMoveHandler);
                document.removeEventListener('mouseup', this.subgraphState.mouseUpHandler);
                this.subgraphState = null;
            }
            modal.remove();
        };
        
        closeBtn.addEventListener('click', closeModal);
        cancelBtn.addEventListener('click', closeModal);
        
        saveBtn.addEventListener('click', () => {
            const name = document.getElementById('groupNameInput').value.trim();
            const inputPorts = this.parsePortsList(document.getElementById('groupInputPorts').value);
            const outputPorts = this.parsePortsList(document.getElementById('groupOutputPorts').value);
            
            if (!name) {
                alert('Group name is required');
                return;
            }
            
            if (outputPorts.length === 0) {
                alert('At least one output port is required');
                return;
            }
            
            // Update group node
            window.nodeManager.updateNode(groupNode.id, {
                name: name,
                inputPorts: inputPorts,
                outputPorts: outputPorts
            });
            
            closeModal();
        });
        
        // Click outside to close
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                closeModal();
            }
        });
    }
    
    closeEditor() {
        this.modal.style.display = 'none';
        this.modal.style.zIndex = ''; // Reset z-index to default
        
        // Restore modal to original parent if it was moved
        if (this.originalModalParent && this.modal.parentNode !== this.originalModalParent) {
            this.originalModalParent.appendChild(this.modal);
            this.originalModalParent = null;
        }
        
        this.currentNode = null;
        this.editingSubgraphNode = null; // Clear subgraph editing state
        
        // Clear form
        this.nodeNameInput.value = '';
        this.codeTextarea.value = '';
        this.inputPortsInput.value = '';
        this.outputPortsInput.value = '';
    }
    
    saveNode() {
        // Check if we're editing a subgraph node
        if (this.editingSubgraphNode) {
            this.saveSubgraphNode();
            return;
        }
        
        if (!this.currentNode) {
            console.error('No current node to save');
            return;
        }
        
        // Verify node still exists
        const nodeExists = window.nodeManager.getNode(this.currentNode.id);
        if (!nodeExists) {
            alert('Node no longer exists. Cannot save changes.');
            this.closeEditor();
            return;
        }
        
        try {
            // Get form values
            const name = this.nodeNameInput.value.trim();
            const code = this.codeTextarea.value;
            const inputPorts = this.parsePortsList(this.inputPortsInput.value);
            const outputPorts = this.parsePortsList(this.outputPortsInput.value);
            
            // Validate inputs
            if (!name) {
                alert('Node name is required');
                this.nodeNameInput.focus();
                return;
            }
            
            if (!code.trim()) {
                alert('Node code is required');
                this.codeTextarea.focus();
                return;
            }
            
            if (outputPorts.length === 0) {
                alert('At least one output port is required');
                this.outputPortsInput.focus();
                return;
            }
            
            // Validate code syntax (basic check)
            if (!this.validateCode(code)) {
                return; // Error already shown
            }
            
            // Check if ports changed and if so, validate connections
            const portsChanged = this.havePortsChanged(inputPorts, outputPorts);
            if (portsChanged && !this.validatePortChanges(inputPorts, outputPorts)) {
                return; // Error already shown
            }
            
            // Prepare updates
            const updates = {
                name: name,
                code: code,
                inputPorts: inputPorts,
                outputPorts: outputPorts
            };
            
            console.log('Saving node updates:', this.currentNode.id, updates);
            
            // Update node with error recovery
            const success = this.safeUpdateNode(updates);
            if (!success) {
                alert('Failed to update node. Please try again.');
                return;
            }
            
            // Remove invalid connections if ports changed
            if (portsChanged) {
                this.removeInvalidConnections();
            }
            
            this.closeEditor();
            
        } catch (error) {
            console.error('Save node error:', error);
            alert('Error saving node: ' + error.message);
            // Don't close editor on error, let user try again
        }
    }
    
    safeUpdateNode(updates) {
        try {
            // Verify node and element still exist before update
            const nodeExists = window.nodeManager.getNode(this.currentNode.id);
            if (!nodeExists || !nodeExists.element) {
                console.error('Node or element missing during update');
                return false;
            }
            
            // Perform the update
            window.nodeManager.updateNode(this.currentNode.id, updates);
            
            // Verify update was successful
            const updatedNode = window.nodeManager.getNode(this.currentNode.id);
            if (!updatedNode || !updatedNode.element) {
                console.error('Node disappeared after update');
                return false;
            }
            
            // Update our reference
            this.currentNode = updatedNode;
            
            return true;
            
        } catch (error) {
            console.error('Error in safeUpdateNode:', error);
            return false;
        }
    }
    
    parsePortsList(portsString) {
        return portsString
            .split(',')
            .map(port => port.trim())
            .filter(port => port.length > 0)
            .filter((port, index, arr) => arr.indexOf(port) === index); // Remove duplicates
    }
    
    validateCode(code) {
        try {
            // Try to create the function to check for syntax errors
            new Function('inputs', 'context', code);
            return true;
        } catch (error) {
            alert('Code syntax error: ' + error.message);
            this.codeTextarea.focus();
            return false;
        }
    }
    
    havePortsChanged(newInputPorts, newOutputPorts) {
        const currentInputPorts = this.currentNode.inputPorts;
        const currentOutputPorts = this.currentNode.outputPorts;
        
        return !this.arraysEqual(currentInputPorts, newInputPorts) ||
               !this.arraysEqual(currentOutputPorts, newOutputPorts);
    }
    
    arraysEqual(arr1, arr2) {
        if (arr1.length !== arr2.length) return false;
        return arr1.every((val, index) => val === arr2[index]);
    }
    
    validatePortChanges(newInputPorts, newOutputPorts) {
        const connections = window.canvasManager.getConnectionsForNode(this.currentNode.id);
        
        // Check for connections to ports that will be removed
        const removedInputPorts = this.currentNode.inputPorts.filter(port => !newInputPorts.includes(port));
        const removedOutputPorts = this.currentNode.outputPorts.filter(port => !newOutputPorts.includes(port));
        
        const affectedConnections = connections.filter(conn => {
            if (conn.toNode === this.currentNode.id && removedInputPorts.includes(conn.toPort)) {
                return true;
            }
            if (conn.fromNode === this.currentNode.id && removedOutputPorts.includes(conn.fromPort)) {
                return true;
            }
            return false;
        });
        
        if (affectedConnections.length > 0) {
            const message = `Changing ports will remove ${affectedConnections.length} connection(s). Continue?`;
            return confirm(message);
        }
        
        return true;
    }
    
    removeInvalidConnections() {
        const connections = window.canvasManager.getConnectionsForNode(this.currentNode.id);
        
        connections.forEach(conn => {
            let shouldRemove = false;
            
            if (conn.toNode === this.currentNode.id && !this.currentNode.inputPorts.includes(conn.toPort)) {
                shouldRemove = true;
            }
            
            if (conn.fromNode === this.currentNode.id && !this.currentNode.outputPorts.includes(conn.fromPort)) {
                shouldRemove = true;
            }
            
            if (shouldRemove) {
                window.canvasManager.removeConnection(conn);
                
                // Update port visual states
                const fromPortEl = window.canvasManager.getPortElement(conn.fromNode, conn.fromPort, 'output');
                const toPortEl = window.canvasManager.getPortElement(conn.toNode, conn.toPort, 'input');
                
                if (fromPortEl) fromPortEl.classList.remove('connected');
                if (toPortEl) toPortEl.classList.remove('connected');
            }
        });
    }
    
    autoResizeTextarea() {
        const textarea = this.codeTextarea;
        textarea.style.height = 'auto';
        const newHeight = Math.max(120, Math.min(400, textarea.scrollHeight));
        textarea.style.height = newHeight + 'px';
    }
    
    handleCodeEditorKeydown(e) {
        // Tab key for indentation
        if (e.key === 'Tab') {
            e.preventDefault();
            const start = this.codeTextarea.selectionStart;
            const end = this.codeTextarea.selectionEnd;
            
            // Insert tab character
            this.codeTextarea.value = this.codeTextarea.value.substring(0, start) + 
                                     '  ' + // 2 spaces instead of tab
                                     this.codeTextarea.value.substring(end);
            
            // Move cursor
            this.codeTextarea.selectionStart = this.codeTextarea.selectionEnd = start + 2;
        }
        
        // Auto-complete brackets
        const pairs = {
            '(': ')',
            '[': ']',
            '{': '}',
            '"': '"',
            "'": "'"
        };
        
        if (pairs[e.key]) {
            const start = this.codeTextarea.selectionStart;
            const end = this.codeTextarea.selectionEnd;
            
            if (start === end) { // No selection
                e.preventDefault();
                const before = this.codeTextarea.value.substring(0, start);
                const after = this.codeTextarea.value.substring(end);
                
                this.codeTextarea.value = before + e.key + pairs[e.key] + after;
                this.codeTextarea.selectionStart = this.codeTextarea.selectionEnd = start + 1;
            }
        }
        
        // Auto-indent on Enter
        if (e.key === 'Enter') {
            const start = this.codeTextarea.selectionStart;
            const lines = this.codeTextarea.value.substring(0, start).split('\n');
            const currentLine = lines[lines.length - 1];
            
            // Count leading spaces
            const indent = currentLine.match(/^ */)[0];
            
            // Add extra indent if line ends with {
            const extraIndent = currentLine.trim().endsWith('{') ? '  ' : '';
            
            setTimeout(() => {
                const newStart = this.codeTextarea.selectionStart;
                const before = this.codeTextarea.value.substring(0, newStart);
                const after = this.codeTextarea.value.substring(newStart);
                
                this.codeTextarea.value = before + indent + extraIndent + after;
                this.codeTextarea.selectionStart = this.codeTextarea.selectionEnd = newStart + indent.length + extraIndent.length;
            }, 0);
        }
    }
    
    insertTemplate(template) {
        const templates = {
            'basic': `function process(inputs) {\n  return {\n    result: inputs.data\n  };\n}`,
            'math': `function process(inputs) {\n  const result = inputs.value * 2;\n  return {\n    result: result\n  };\n}`,
            'filter': `function process(inputs) {\n  const filtered = inputs.array.filter(item => item > 0);\n  return {\n    result: filtered\n  };\n}`,
            'transform': `function process(inputs) {\n  const transformed = inputs.data.map(item => ({\n    ...item,\n    processed: true\n  }));\n  return {\n    result: transformed\n  };\n}`
        };
        
        if (templates[template]) {
            this.codeTextarea.value = templates[template];
            this.autoResizeTextarea();
        }
    }
    
    addHelpText() {
        const helpText = `
// Available context functions:
// context.log() - Console logging
// context.Math - Math utilities
// context.clone(obj) - Deep clone object
// context.merge(obj1, obj2) - Merge objects
// context.map(arr, fn) - Array mapping
// context.filter(arr, fn) - Array filtering
// context.reduce(arr, fn, initial) - Array reduction

function process(inputs) {
  // Your code here
  // Access inputs via inputs.portName
  // Return object with output port values
  
  return {
    result: inputs.data
  };
}`;
        
        this.codeTextarea.value = helpText;
        this.autoResizeTextarea();
    }
    
    async evaluateSubgraphNodeWithDependencies(targetNodeId) {
        if (!this.subgraphState || !this.subgraphState.groupNode) {
            console.error('No subgraph state available');
            return;
        }
        
        const groupNode = this.subgraphState.groupNode;
        const subgraphNodes = groupNode.subgraph.nodes;
        const subgraphConnections = groupNode.subgraph.connections;
        
        // Build dependency graph
        const dependencyGraph = this.buildSubgraphDependencyGraph(subgraphNodes, subgraphConnections);
        
        // Find evaluation order starting from target node
        const evaluationOrder = this.getEvaluationOrder(targetNodeId, dependencyGraph);
        
        console.log('Subgraph evaluation order:', evaluationOrder);
        
        // Store evaluation results
        this.subgraphResults = new Map();
        
        // Evaluate nodes in dependency order
        for (const nodeId of evaluationOrder) {
            await this.evaluateSubgraphNode(nodeId, subgraphNodes, subgraphConnections);
        }
    }
    
    buildSubgraphDependencyGraph(nodes, connections) {
        const graph = new Map();
        
        // Initialize graph with all nodes
        nodes.forEach(node => {
            graph.set(node.id, {
                dependencies: new Set(),
                dependents: new Set()
            });
        });
        
        // Build dependency relationships from connections
        connections.forEach(conn => {
            const fromNodeEntry = graph.get(conn.fromNode);
            const toNodeEntry = graph.get(conn.toNode);
            
            if (fromNodeEntry && toNodeEntry) {
                // toNode depends on fromNode
                toNodeEntry.dependencies.add(conn.fromNode);
                // fromNode has toNode as dependent
                fromNodeEntry.dependents.add(conn.toNode);
            }
        });
        
        return graph;
    }
    
    getEvaluationOrder(targetNodeId, dependencyGraph) {
        const visited = new Set();
        const order = [];
        
        const visit = (nodeId) => {
            if (visited.has(nodeId)) return;
            
            visited.add(nodeId);
            
            // Visit all dependencies first
            const nodeInfo = dependencyGraph.get(nodeId);
            if (nodeInfo) {
                nodeInfo.dependencies.forEach(depId => {
                    visit(depId);
                });
            }
            
            order.push(nodeId);
        };
        
        visit(targetNodeId);
        return order;
    }
    
    async evaluateSubgraphNode(nodeId, subgraphNodes, subgraphConnections) {
        const nodeData = subgraphNodes.find(n => n.id === nodeId);
        if (!nodeData) {
            console.error('Subgraph node not found:', nodeId);
            return;
        }
        
        const nodeEl = document.querySelector(`[data-node-id="${nodeId}"].subgraph-node`);
        if (!nodeEl) {
            console.error('Subgraph node element not found:', nodeId);
            return;
        }
        
        try {
            // Set evaluating state
            this.updateSubgraphNodeStatus(nodeEl, 'Evaluating...', 'Processing...', true);
            
            // Show spinner
            const spinner = nodeEl.querySelector('.node-spinner');
            if (spinner) spinner.classList.add('visible');
            
            // Collect inputs from dependencies
            const inputs = this.collectSubgraphInputs(nodeId, subgraphConnections);
            
            console.log(`Evaluating subgraph node ${nodeData.name} with inputs:`, inputs);
            
            // Use the same configurable delay as main canvas
            const delayMs = parseInt(document.getElementById('evaluationDelay').value) || 0;
            if (delayMs > 0) {
                await new Promise(resolve => setTimeout(resolve, delayMs));
            }
            
            // Execute the node's code
            const result = await this.executeSubgraphNodeCode(nodeData, inputs);
            
            // Store result
            this.subgraphResults.set(nodeId, result);
            
            // Hide spinner
            if (spinner) spinner.classList.remove('visible');
            
            // Update display with actual result
            const resultText = JSON.stringify(result, null, 2);
            this.updateSubgraphNodeStatus(nodeEl, 'Success', resultText);
            
            console.log(`Subgraph node ${nodeData.name} completed with result:`, result);
            
        } catch (error) {
            console.error(`Error evaluating subgraph node ${nodeData.name}:`, error);
            
            // Hide spinner
            const spinner = nodeEl.querySelector('.node-spinner');
            if (spinner) spinner.classList.remove('visible');
            
            // Show error
            this.updateSubgraphNodeStatus(nodeEl, 'Error', error.message);
            nodeEl.classList.remove('not-evaluated', 'evaluating', 'success');
            nodeEl.classList.add('error');
        }
    }
    
    collectSubgraphInputs(nodeId, connections) {
        const inputs = {};
        
        // Find all connections that feed into this node
        const inputConnections = connections.filter(conn => conn.toNode === nodeId);
        
        inputConnections.forEach(conn => {
            // Get the result from the source node
            const sourceResult = this.subgraphResults.get(conn.fromNode);
            if (sourceResult && sourceResult[conn.fromPort] !== undefined) {
                inputs[conn.toPort] = sourceResult[conn.fromPort];
            } else {
                console.warn(`No result for ${conn.fromNode}.${conn.fromPort}`);
                inputs[conn.toPort] = null;
            }
        });
        
        return inputs;
    }
    
    async executeSubgraphNodeCode(nodeData, inputs) {
        try {
            // Create execution context
            const context = {
                log: console.log,
                Math: Math,
                clone: (obj) => JSON.parse(JSON.stringify(obj)),
                merge: (obj1, obj2) => ({ ...obj1, ...obj2 }),
                map: (arr, fn) => arr.map(fn),
                filter: (arr, fn) => arr.filter(fn),
                reduce: (arr, fn, initial) => arr.reduce(fn, initial)
            };
            
            // Execute the code - handle both function definitions and direct code
            let result;
            
            try {
                // First try to execute as a function that returns something
                const func = new Function('inputs', 'context', `
                    ${nodeData.code}
                    
                    // Try to call process function if it exists
                    if (typeof process === 'function') {
                        return process(inputs, context);
                    } else {
                        // If no process function, try to evaluate the code directly
                        return (function() {
                            ${nodeData.code}
                        })();
                    }
                `);
                
                result = func(inputs, context);
            } catch (err) {
                // If that fails, try executing as direct function call
                const func = new Function('inputs', 'context', `return (${nodeData.code})(inputs, context);`);
                result = func(inputs, context);
            }
            
            // Handle different return types
            if (result === null || result === undefined) {
                return { result: null };
            }
            
            // If result is not an object, wrap it
            if (typeof result !== 'object') {
                return { result: result };
            }
            
            // If result is an array, wrap it
            if (Array.isArray(result)) {
                return { result: result };
            }
            
            // If it's already a proper object, return as-is
            return result;
            
        } catch (error) {
            console.error('Code execution error:', error);
            throw new Error(`Code execution failed: ${error.message}`);
        }
    }
    
    editSubgraphNode(nodeData) {
        // Create a temporary node object for editing
        const tempNode = {
            id: nodeData.id,
            name: nodeData.name,
            code: nodeData.code,
            inputPorts: [...nodeData.inputPorts],
            outputPorts: [...nodeData.outputPorts],
            isSubgraphNode: true,
            originalData: nodeData
        };
        
        // Store reference to subgraph for saving
        this.editingSubgraphNode = tempNode;
        
        // Populate form with subgraph node data
        this.nodeNameInput.value = tempNode.name;
        this.codeTextarea.value = tempNode.code;
        this.inputPortsInput.value = tempNode.inputPorts.join(', ');
        this.outputPortsInput.value = tempNode.outputPorts.join(', ');
        
        // Show modal - move it outside group editor since we're editing subgraph node
        this.modal.style.display = 'block';
        
        // Store original parent for restoration
        this.originalModalParent = this.modal.parentNode;
        
        // Move modal to body to escape stacking context
        document.body.appendChild(this.modal);
        this.modal.style.setProperty('z-index', '9999', 'important');
        
        // Focus on name input
        setTimeout(() => {
            this.nodeNameInput.focus();
            this.nodeNameInput.select();
        }, 100);
        
        // Auto-resize textarea
        this.autoResizeTextarea();
    }
    
    saveSubgraphNode() {
        if (!this.editingSubgraphNode) {
            console.error('No subgraph node being edited');
            return;
        }
        
        try {
            // Get form values
            const name = this.nodeNameInput.value.trim();
            const code = this.codeTextarea.value;
            const inputPorts = this.parsePortsList(this.inputPortsInput.value);
            const outputPorts = this.parsePortsList(this.outputPortsInput.value);
            
            // Validate inputs
            if (!name) {
                alert('Node name is required');
                this.nodeNameInput.focus();
                return;
            }
            
            if (!code.trim()) {
                alert('Node code is required');
                this.codeTextarea.focus();
                return;
            }
            
            if (outputPorts.length === 0) {
                alert('At least one output port is required');
                this.outputPortsInput.focus();
                return;
            }
            
            // Validate code syntax
            if (!this.validateCode(code)) {
                return;
            }
            
            // Update the subgraph node data
            const originalData = this.editingSubgraphNode.originalData;
            originalData.name = name;
            originalData.code = code;
            originalData.inputPorts = inputPorts;
            originalData.outputPorts = outputPorts;
            
            // Update the visual node in the subgraph
            const nodeEl = document.querySelector(`[data-node-id="${originalData.id}"].subgraph-node`);
            if (nodeEl) {
                // Update the title
                const titleEl = nodeEl.querySelector('.node-title');
                if (titleEl) {
                    titleEl.textContent = name;
                }
                
                // Regenerate ports HTML
                const inputPortsHTML = inputPorts.map(port => 
                    `<div class="port input-port" data-port="${port}">${port}</div>`
                ).join('');
                
                const outputPortsHTML = outputPorts.map(port => 
                    `<div class="port output-port" data-port="${port}">${port}</div>`
                ).join('');
                
                const inputPortsContainer = nodeEl.querySelector('.input-ports');
                const outputPortsContainer = nodeEl.querySelector('.output-ports');
                
                if (inputPortsContainer) {
                    inputPortsContainer.innerHTML = inputPortsHTML;
                }
                if (outputPortsContainer) {
                    outputPortsContainer.innerHTML = outputPortsHTML;
                }
                
                // Redraw connections after port changes
                setTimeout(() => {
                    this.drawSubgraphConnections(this.subgraphState.groupNode.subgraph.connections, this.subgraphState.container);
                }, 100);
            }
            
            // Also update the group node's subgraph data
            if (this.subgraphState && this.subgraphState.groupNode) {
                const groupNode = this.subgraphState.groupNode;
                const nodeIndex = groupNode.subgraph.nodes.findIndex(n => n.id === originalData.id);
                if (nodeIndex !== -1) {
                    groupNode.subgraph.nodes[nodeIndex] = { ...originalData };
                }
            }
            
            // Clear editing state
            this.editingSubgraphNode = null;
            
            // Close editor
            this.closeEditor();
            
            console.log('Subgraph node saved successfully:', originalData);
            
        } catch (error) {
            console.error('Save subgraph node error:', error);
            alert('Error saving subgraph node: ' + error.message);
        }
    }
    
    startSubgraphNodeResize(e, nodeEl, nodeData) {
        const startX = e.clientX;
        const startY = e.clientY;
        const startWidth = parseFloat(nodeEl.style.width) || 200;
        const startHeight = nodeEl.offsetHeight;
        
        const handleResize = (e) => {
            const deltaX = e.clientX - startX;
            const deltaY = e.clientY - startY;
            
            // Calculate new dimensions (accounting for 0.8 scale)
            const scale = 0.8;
            const newWidth = Math.max(150, Math.min(400, startWidth + deltaX / scale));
            const newHeight = Math.max(100, startHeight + deltaY / scale);
            
            // Update node dimensions
            nodeEl.style.width = `${newWidth}px`;
            nodeEl.style.height = `${newHeight}px`;
            
            // Redraw connections
            setTimeout(() => {
                this.drawSubgraphConnections(this.subgraphState.groupNode.subgraph.connections, this.subgraphState.container);
            }, 16);
        };
        
        const handleResizeEnd = () => {
            document.removeEventListener('mousemove', handleResize);
            document.removeEventListener('mouseup', handleResizeEnd);
        };
        
        document.addEventListener('mousemove', handleResize);
        document.addEventListener('mouseup', handleResizeEnd);
    }
}

// Global code editor instance
window.codeEditor = new CodeEditor();
