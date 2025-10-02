// Template management system
class TemplateManager {
    constructor() {
        this.templates = new Map();
        this.categories = new Map();
        this.dragState = null;
        this.dropIndicator = null;
        
        this.initializeDefaultTemplates();
        this.initEventListeners();
        this.createDropIndicator();
    }
    
    initEventListeners() {
        // Template panel collapse button
        const collapseBtn = document.getElementById('collapseTemplateBtn');
        if (collapseBtn) {
            collapseBtn.addEventListener('click', () => this.toggleTemplatePanel());
        }
        
        // Workspace drop events
        const workspace = document.getElementById('workspace');
        workspace.addEventListener('dragover', (e) => this.handleWorkspaceDragOver(e));
        workspace.addEventListener('drop', (e) => this.handleWorkspaceDrop(e));
        workspace.addEventListener('dragleave', (e) => this.handleWorkspaceDragLeave(e));
    }
    
    createDropIndicator() {
        this.dropIndicator = document.createElement('div');
        this.dropIndicator.className = 'drop-indicator';
        document.getElementById('workspace').appendChild(this.dropIndicator);
    }
    
    toggleTemplatePanel() {
        const panel = document.getElementById('templatePanel');
        panel.classList.toggle('collapsed');
    }
    
    defineTemplate(id, template) {
        // Validate template structure
        if (!template.name || !template.code || !template.outputPorts) {
            throw new Error('Template must have name, code, and outputPorts');
        }
        
        // Set defaults
        const fullTemplate = {
            id: id,
            name: template.name,
            description: template.description || '',
            icon: template.icon || '⚙️',
            category: template.category || 'General',
            code: template.code,
            inputPorts: template.inputPorts || [],
            outputPorts: template.outputPorts,
            parameters: template.parameters || [],
            color: template.color || '#3498db'
        };
        
        this.templates.set(id, fullTemplate);
        
        // Add to category
        if (!this.categories.has(fullTemplate.category)) {
            this.categories.set(fullTemplate.category, []);
        }
        this.categories.get(fullTemplate.category).push(fullTemplate);
        
        return fullTemplate;
    }
    
    initializeDefaultTemplates() {
        // Data Sources
        this.defineTemplate('data-source', {
            name: 'Data Source',
            description: 'Generates data with configurable values',
            icon: '📊',
            category: 'Data Sources',
            code: `function process(inputs) {
  return {
    data: {{{dataValue}}}
  };
}`,
            inputPorts: [],
            outputPorts: ['data'],
            parameters: [
                {
                    name: 'dataValue',
                    label: 'Data Value',
                    type: 'json',
                    default: '{"numbers": [1, 2, 3, 4, 5], "multiplier": 2}',
                    description: 'JSON data to output'
                }
            ]
        });
        
        this.defineTemplate('number-source', {
            name: 'Number Source',
            description: 'Outputs a configurable number',
            icon: '🔢',
            category: 'Data Sources',
            code: `function process(inputs) {
  return {
    value: {{{numberValue}}}
  };
}`,
            inputPorts: [],
            outputPorts: ['value'],
            parameters: [
                {
                    name: 'numberValue',
                    label: 'Number',
                    type: 'number',
                    default: 42,
                    description: 'Number to output'
                }
            ]
        });
        
        this.defineTemplate('text-source', {
            name: 'Text Source',
            description: 'Outputs configurable text',
            icon: '📝',
            category: 'Data Sources',
            code: `function process(inputs) {
  return {
    text: "{{{textValue}}}"
  };
}`,
            inputPorts: [],
            outputPorts: ['text'],
            parameters: [
                {
                    name: 'textValue',
                    label: 'Text',
                    type: 'text',
                    default: 'Hello World',
                    description: 'Text to output'
                }
            ]
        });
        
        // Math Operations
        this.defineTemplate('multiplier', {
            name: 'Multiplier',
            description: 'Multiplies input by a factor',
            icon: '✖️',
            category: 'Math',
            code: `function process(inputs) {
  const value = inputs.value || 0;
  const factor = {{{factor}}};
  return {
    result: value * factor
  };
}`,
            inputPorts: ['value'],
            outputPorts: ['result'],
            parameters: [
                {
                    name: 'factor',
                    label: 'Multiply Factor',
                    type: 'number',
                    default: 2,
                    description: 'Number to multiply by'
                }
            ]
        });
        
        this.defineTemplate('adder', {
            name: 'Adder',
            description: 'Adds a value to the input',
            icon: '➕',
            category: 'Math',
            code: `function process(inputs) {
  const value = inputs.value || 0;
  const addend = {{{addend}}};
  return {
    result: value + addend
  };
}`,
            inputPorts: ['value'],
            outputPorts: ['result'],
            parameters: [
                {
                    name: 'addend',
                    label: 'Add Value',
                    type: 'number',
                    default: 10,
                    description: 'Number to add'
                }
            ]
        });
        
        this.defineTemplate('math-expression', {
            name: 'Math Expression',
            description: 'Evaluates a custom math expression',
            icon: '🧮',
            category: 'Math',
            code: `function process(inputs) {
  const x = inputs.x || 0;
  const y = inputs.y || 0;
  const z = inputs.z || 0;
  
  // Expression: {{{expression}}}
  const result = {{{expression}}};
  
  return {
    result: result
  };
}`,
            inputPorts: ['x', 'y', 'z'],
            outputPorts: ['result'],
            parameters: [
                {
                    name: 'expression',
                    label: 'Math Expression',
                    type: 'text',
                    default: 'x + y + z',
                    description: 'Math expression using x, y, z variables'
                }
            ]
        });
        
        // Array Operations
        this.defineTemplate('array-mapper', {
            name: 'Array Mapper',
            description: 'Maps array elements using a function',
            icon: '🗂️',
            category: 'Array',
            code: `function process(inputs) {
  const array = inputs.array || [];
  const mapped = array.map(item => {
    // Map function: {{{mapFunction}}}
    return {{{mapFunction}}};
  });
  
  return {
    result: mapped
  };
}`,
            inputPorts: ['array'],
            outputPorts: ['result'],
            parameters: [
                {
                    name: 'mapFunction',
                    label: 'Map Function',
                    type: 'text',
                    default: 'item * 2',
                    description: 'Function to apply to each item (use "item" variable)'
                }
            ]
        });
        
        this.defineTemplate('array-filter', {
            name: 'Array Filter',
            description: 'Filters array elements using a condition',
            icon: '🔍',
            category: 'Array',
            code: `function process(inputs) {
  const array = inputs.array || [];
  const filtered = array.filter(item => {
    // Filter condition: {{{filterCondition}}}
    return {{{filterCondition}}};
  });
  
  return {
    result: filtered
  };
}`,
            inputPorts: ['array'],
            outputPorts: ['result'],
            parameters: [
                {
                    name: 'filterCondition',
                    label: 'Filter Condition',
                    type: 'text',
                    default: 'item > 0',
                    description: 'Condition to filter items (use "item" variable)'
                }
            ]
        });
        
        // Text Operations
        this.defineTemplate('text-formatter', {
            name: 'Text Formatter',
            description: 'Formats text with a template',
            icon: '📄',
            category: 'Text',
            code: `function process(inputs) {
  const value = inputs.value || '';
  const template = "{{{template}}}";
  const result = template.replace(/\\{value\\}/g, value);
  
  return {
    result: result
  };
}`,
            inputPorts: ['value'],
            outputPorts: ['result'],
            parameters: [
                {
                    name: 'template',
                    label: 'Text Template',
                    type: 'text',
                    default: 'The value is: {value}',
                    description: 'Template string (use {value} for input)'
                }
            ]
        });
        
        // Logic Operations
        this.defineTemplate('conditional', {
            name: 'Conditional',
            description: 'Returns different values based on condition',
            icon: '🔀',
            category: 'Logic',
            code: `function process(inputs) {
  const value = inputs.value;
  const condition = {{{condition}}};
  const trueValue = {{{trueValue}}};
  const falseValue = {{{falseValue}}};
  
  return {
    result: condition ? trueValue : falseValue
  };
}`,
            inputPorts: ['value'],
            outputPorts: ['result'],
            parameters: [
                {
                    name: 'condition',
                    label: 'Condition',
                    type: 'text',
                    default: 'value > 0',
                    description: 'Condition to evaluate (use "value" variable)'
                },
                {
                    name: 'trueValue',
                    label: 'True Value',
                    type: 'text',
                    default: '"positive"',
                    description: 'Value when condition is true'
                },
                {
                    name: 'falseValue',
                    label: 'False Value',
                    type: 'text',
                    default: '"negative or zero"',
                    description: 'Value when condition is false'
                }
            ]
        });
        
        // Utility Operations
        this.defineTemplate('delay', {
            name: 'Delay',
            description: 'Delays execution by specified milliseconds',
            icon: '⏰',
            category: 'Utility',
            code: `async function process(inputs) {
  const data = inputs.data;
  const delayMs = {{{delayMs}}};
  
  await new Promise(resolve => setTimeout(resolve, delayMs));
  
  return {
    result: data
  };
}`,
            inputPorts: ['data'],
            outputPorts: ['result'],
            parameters: [
                {
                    name: 'delayMs',
                    label: 'Delay (ms)',
                    type: 'number',
                    default: 1000,
                    description: 'Delay in milliseconds'
                }
            ]
        });
        
        this.defineTemplate('logger', {
            name: 'Logger',
            description: 'Logs input to console with custom message',
            icon: '📋',
            category: 'Utility',
            code: `function process(inputs) {
  const data = inputs.data;
  const message = "{{{logMessage}}}";
  
  console.log(message, data);
  
  return {
    result: data
  };
}`,
            inputPorts: ['data'],
            outputPorts: ['result'],
            parameters: [
                {
                    name: 'logMessage',
                    label: 'Log Message',
                    type: 'text',
                    default: 'Logger output:',
                    description: 'Message to display before data'
                }
            ]
        });
    }
    
    renderTemplates() {
        const container = document.getElementById('templateContainer');
        if (!container) return;
        
        container.innerHTML = '';
        
        // Add "New Template" button at the top
        const newTemplateBtn = document.createElement('button');
        newTemplateBtn.className = 'btn btn-primary';
        newTemplateBtn.style.cssText = 'width: 100%; margin-bottom: 16px; padding: 12px; font-weight: 600;';
        newTemplateBtn.innerHTML = '➕ Create New Template';
        newTemplateBtn.addEventListener('click', () => this.createNewTemplate());
        container.appendChild(newTemplateBtn);
        
        // Render by categories
        this.categories.forEach((templates, categoryName) => {
            const categoryDiv = document.createElement('div');
            categoryDiv.className = 'template-category';
            
            const headerDiv = document.createElement('div');
            headerDiv.className = 'template-category-header';
            headerDiv.textContent = categoryName;
            categoryDiv.appendChild(headerDiv);
            
            templates.forEach(template => {
                const templateEl = this.createTemplateElement(template);
                categoryDiv.appendChild(templateEl);
            });
            
            container.appendChild(categoryDiv);
        });
    }
    
    createTemplateElement(template) {
        const templateEl = document.createElement('div');
        templateEl.className = 'template-item';
        templateEl.draggable = true;
        templateEl.setAttribute('data-template-id', template.id);
        
        templateEl.innerHTML = `
            <div class="template-item-header">
                <span class="template-icon">${template.icon}</span>
                <span class="template-name">${template.name}</span>
            </div>
            <div class="template-description">${template.description}</div>
            <div class="template-ports">
                <div class="template-inputs">
                    <div class="template-port-label">Inputs:</div>
                    ${template.inputPorts.length > 0 ? template.inputPorts.join(', ') : 'none'}
                </div>
                <div class="template-outputs">
                    <div class="template-port-label">Outputs:</div>
                    ${template.outputPorts.join(', ')}
                </div>
            </div>
        `;
        
        // Add drag event listeners
        templateEl.addEventListener('dragstart', (e) => this.handleTemplateDragStart(e, template));
        templateEl.addEventListener('dragend', (e) => this.handleTemplateDragEnd(e));
        
        // Add double-click to edit template
        templateEl.addEventListener('dblclick', (e) => {
            e.preventDefault();
            e.stopPropagation();
            this.editTemplate(template);
        });
        
        // Add context menu for template actions
        templateEl.addEventListener('contextmenu', (e) => {
            e.preventDefault();
            this.showTemplateContextMenu(e, template);
        });
        
        return templateEl;
    }
    
    handleTemplateDragStart(e, template) {
        this.dragState = {
            template: template,
            startTime: Date.now()
        };
        
        e.target.classList.add('dragging');
        e.dataTransfer.effectAllowed = 'copy';
        e.dataTransfer.setData('text/plain', template.id);
        
        // Show drag feedback
        const workspace = document.getElementById('workspace');
        workspace.classList.add('drag-over');
    }
    
    handleTemplateDragEnd(e) {
        e.target.classList.remove('dragging');
        
        // Hide drag feedback
        const workspace = document.getElementById('workspace');
        workspace.classList.remove('drag-over');
        this.dropIndicator.classList.remove('visible');
        
        this.dragState = null;
    }
    
    handleWorkspaceDragOver(e) {
        if (!this.dragState) return;
        
        e.preventDefault();
        e.dataTransfer.dropEffect = 'copy';
        
        // Update drop indicator position
        const workspace = document.getElementById('workspace');
        const rect = workspace.getBoundingClientRect();
        const x = e.clientX - rect.left - 125; // Center the indicator
        const y = e.clientY - rect.top - 60;
        
        this.dropIndicator.style.left = `${Math.max(0, x)}px`;
        this.dropIndicator.style.top = `${Math.max(0, y)}px`;
        this.dropIndicator.classList.add('visible');
    }
    
    handleWorkspaceDragLeave(e) {
        // Only hide if leaving the workspace entirely
        if (e.target.id === 'workspace' && !e.relatedTarget?.closest('#workspace')) {
            this.dropIndicator.classList.remove('visible');
        }
    }
    
    handleWorkspaceDrop(e) {
        if (!this.dragState) return;
        
        e.preventDefault();
        this.dropIndicator.classList.remove('visible');
        
        // Calculate drop position
        const workspace = document.getElementById('workspace');
        const rect = workspace.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;
        
        // Create node from template
        this.createNodeFromTemplate(this.dragState.template, x, y);
    }
    
    createNodeFromTemplate(template, x, y) {
        // If template has parameters, show parameter dialog
        if (template.parameters && template.parameters.length > 0) {
            this.showParameterDialog(template, x, y);
        } else {
            // Create node directly
            this.instantiateTemplate(template, {}, x, y);
        }
    }
    
    showParameterDialog(template, x, y) {
        // Create parameter input modal
        const modal = document.createElement('div');
        modal.className = 'modal';
        modal.id = 'parameterModal';
        
        modal.innerHTML = `
            <div class="modal-content">
                <div class="modal-header">
                    <h3>Configure ${template.name}</h3>
                    <span class="close">&times;</span>
                </div>
                <div class="modal-body" id="parameterModalBody">
                    ${this.generateParameterForm(template)}
                </div>
                <div class="modal-footer">
                    <button id="createNodeBtn" class="btn btn-primary">Create Node</button>
                    <button id="cancelParameterBtn" class="btn btn-secondary">Cancel</button>
                </div>
            </div>
        `;
        
        document.body.appendChild(modal);
        modal.style.display = 'block';
        
        // Add event listeners
        const closeBtn = modal.querySelector('.close');
        const createBtn = modal.querySelector('#createNodeBtn');
        const cancelBtn = modal.querySelector('#cancelParameterBtn');
        
        const closeModal = () => {
            modal.remove();
        };
        
        closeBtn.addEventListener('click', closeModal);
        cancelBtn.addEventListener('click', closeModal);
        
        createBtn.addEventListener('click', () => {
            const parameters = this.collectParameters(template);
            this.instantiateTemplate(template, parameters, x, y);
            closeModal();
        });
        
        // Focus first input
        const firstInput = modal.querySelector('input, textarea');
        if (firstInput) {
            setTimeout(() => firstInput.focus(), 100);
        }
    }
    
    generateParameterForm(template) {
        return template.parameters.map(param => {
            const inputId = `param_${param.name}`;
            
            let inputHTML = '';
            switch (param.type) {
                case 'text':
                    inputHTML = `<input type="text" id="${inputId}" value="${param.default || ''}" placeholder="${param.description || ''}">`;
                    break;
                case 'number':
                    inputHTML = `<input type="number" id="${inputId}" value="${param.default || 0}" placeholder="${param.description || ''}">`;
                    break;
                case 'json':
                    inputHTML = `<textarea id="${inputId}" rows="4" placeholder="${param.description || ''}">${param.default || ''}</textarea>`;
                    break;
                default:
                    inputHTML = `<input type="text" id="${inputId}" value="${param.default || ''}" placeholder="${param.description || ''}">`;
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
    
    collectParameters(template) {
        const parameters = {};
        
        template.parameters.forEach(param => {
            const inputEl = document.getElementById(`param_${param.name}`);
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
                            value = param.default;
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
    
    instantiateTemplate(template, parameters, x, y) {
        // Use the new template-based node creation
        const node = window.nodeManager.createNodeFromTemplate(
            template.id, 
            parameters, 
            x - 125, 
            y - 60
        );
        
        console.log(`Created node from template: ${template.name}`, {
            template: template,
            parameters: parameters,
            node: node
        });
        
        return node;
    }
    
    getTemplate(id) {
        return this.templates.get(id);
    }
    
    getAllTemplates() {
        return Array.from(this.templates.values());
    }
    
    getTemplatesByCategory(category) {
        return this.categories.get(category) || [];
    }
    
    editTemplate(template) {
        // Create template editor modal
        const modal = document.createElement('div');
        modal.className = 'modal';
        modal.id = 'templateEditorModal';
        
        modal.innerHTML = `
            <div class="modal-content" style="max-width: 800px; max-height: 90vh;">
                <div class="modal-header">
                    <h3>Edit Template: ${template.name}</h3>
                    <span class="close">&times;</span>
                </div>
                <div class="modal-body" style="max-height: 70vh; overflow-y: auto;">
                    <div class="editor-section">
                        <label>Template Name:</label>
                        <input type="text" id="templateName" value="${template.name}" placeholder="Enter template name">
                    </div>
                    <div class="editor-section">
                        <label>Description:</label>
                        <input type="text" id="templateDescription" value="${template.description}" placeholder="Enter template description">
                    </div>
                    <div class="editor-section">
                        <label>Icon (emoji):</label>
                        <input type="text" id="templateIcon" value="${template.icon}" placeholder="Enter emoji icon">
                    </div>
                    <div class="editor-section">
                        <label>Category:</label>
                        <input type="text" id="templateCategory" value="${template.category}" placeholder="Enter category name">
                    </div>
                    <div class="editor-section">
                        <label>Template Code (use {{{parameterName}}} for parameters):</label>
                        <textarea id="templateCode" rows="12" placeholder="Enter template code">${template.code}</textarea>
                    </div>
                    <div class="editor-section">
                        <label>Input Ports (comma-separated, optional):</label>
                        <input type="text" id="templateInputPorts" value="${template.inputPorts.join(', ')}" placeholder="port1, port2 (leave empty for no inputs)">
                    </div>
                    <div class="editor-section">
                        <label>Output Ports (comma-separated):</label>
                        <input type="text" id="templateOutputPorts" value="${template.outputPorts.join(', ')}" placeholder="result, status">
                    </div>
                    <div class="editor-section">
                        <label>Parameters:</label>
                        <div id="parametersEditor">
                            ${this.generateParametersEditor(template.parameters)}
                        </div>
                        <button type="button" id="addParameterBtn" class="btn btn-secondary btn-small">Add Parameter</button>
                    </div>
                </div>
                <div class="modal-footer">
                    <button id="saveTemplateBtn" class="btn btn-primary">Save Template</button>
                    <button id="deleteTemplateBtn" class="btn btn-danger">Delete Template</button>
                    <button id="cancelTemplateBtn" class="btn btn-secondary">Cancel</button>
                </div>
            </div>
        `;
        
        document.body.appendChild(modal);
        modal.style.display = 'block';
        
        // Add event listeners
        this.addTemplateEditorListeners(modal, template);
        
        // Focus first input
        setTimeout(() => {
            document.getElementById('templateName').focus();
        }, 100);
    }
    
    addTemplateEditorListeners(modal, template) {
        const closeBtn = modal.querySelector('.close');
        const saveBtn = modal.querySelector('#saveTemplateBtn');
        const deleteBtn = modal.querySelector('#deleteTemplateBtn');
        const cancelBtn = modal.querySelector('#cancelTemplateBtn');
        const addParamBtn = modal.querySelector('#addParameterBtn');
        
        const closeModal = () => {
            modal.remove();
        };
        
        closeBtn.addEventListener('click', closeModal);
        cancelBtn.addEventListener('click', closeModal);
        
        addParamBtn.addEventListener('click', () => {
            this.addParameterRow();
        });
        
        deleteBtn.addEventListener('click', () => {
            if (confirm(`Are you sure you want to delete the template "${template.name}"?\n\nThis action cannot be undone.`)) {
                this.deleteTemplate(template.id);
                closeModal();
            }
        });
        
        saveBtn.addEventListener('click', () => {
            try {
                const updates = this.collectTemplateUpdates();
                this.updateTemplate(template.id, updates);
                closeModal();
            } catch (error) {
                alert('Error saving template: ' + error.message);
            }
        });
        
        // Click outside to close
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                closeModal();
            }
        });
    }
    
    generateParametersEditor(parameters) {
        return parameters.map((param, index) => `
            <div class="parameter-row" data-index="${index}">
                <div style="display: grid; grid-template-columns: 1fr 1fr 1fr auto; gap: 8px; margin-bottom: 8px; align-items: center;">
                    <input type="text" class="param-name" value="${param.name}" placeholder="Parameter name" style="font-size: 12px;">
                    <input type="text" class="param-label" value="${param.label}" placeholder="Display label" style="font-size: 12px;">
                    <select class="param-type" style="font-size: 12px;">
                        <option value="text" ${param.type === 'text' ? 'selected' : ''}>Text</option>
                        <option value="number" ${param.type === 'number' ? 'selected' : ''}>Number</option>
                        <option value="json" ${param.type === 'json' ? 'selected' : ''}>JSON</option>
                    </select>
                    <button type="button" class="btn btn-danger btn-small remove-param" style="padding: 2px 6px; font-size: 11px;">×</button>
                </div>
                <input type="text" class="param-default" value="${param.default || ''}" placeholder="Default value" style="width: 100%; margin-bottom: 4px; font-size: 12px;">
                <input type="text" class="param-description" value="${param.description || ''}" placeholder="Description (optional)" style="width: 100%; margin-bottom: 8px; font-size: 12px;">
            </div>
        `).join('');
    }
    
    addParameterRow() {
        const container = document.getElementById('parametersEditor');
        const newRow = document.createElement('div');
        newRow.className = 'parameter-row';
        newRow.innerHTML = `
            <div style="display: grid; grid-template-columns: 1fr 1fr 1fr auto; gap: 8px; margin-bottom: 8px; align-items: center;">
                <input type="text" class="param-name" placeholder="Parameter name" style="font-size: 12px;">
                <input type="text" class="param-label" placeholder="Display label" style="font-size: 12px;">
                <select class="param-type" style="font-size: 12px;">
                    <option value="text">Text</option>
                    <option value="number">Number</option>
                    <option value="json">JSON</option>
                </select>
                <button type="button" class="btn btn-danger btn-small remove-param" style="padding: 2px 6px; font-size: 11px;">×</button>
            </div>
            <input type="text" class="param-default" placeholder="Default value" style="width: 100%; margin-bottom: 4px; font-size: 12px;">
            <input type="text" class="param-description" placeholder="Description (optional)" style="width: 100%; margin-bottom: 8px; font-size: 12px;">
        `;
        
        container.appendChild(newRow);
        
        // Add remove listener
        const removeBtn = newRow.querySelector('.remove-param');
        removeBtn.addEventListener('click', () => {
            newRow.remove();
        });
        
        // Focus the name input
        newRow.querySelector('.param-name').focus();
    }
    
    collectTemplateUpdates() {
        const name = document.getElementById('templateName').value.trim();
        const description = document.getElementById('templateDescription').value.trim();
        const icon = document.getElementById('templateIcon').value.trim();
        const category = document.getElementById('templateCategory').value.trim();
        const code = document.getElementById('templateCode').value;
        const inputPorts = this.parsePortsList(document.getElementById('templateInputPorts').value);
        const outputPorts = this.parsePortsList(document.getElementById('templateOutputPorts').value);
        
        // Validate
        if (!name) throw new Error('Template name is required');
        if (!code.trim()) throw new Error('Template code is required');
        if (outputPorts.length === 0) throw new Error('At least one output port is required');
        
        // Collect parameters
        const parameters = [];
        const paramRows = document.querySelectorAll('.parameter-row');
        paramRows.forEach(row => {
            const name = row.querySelector('.param-name').value.trim();
            const label = row.querySelector('.param-label').value.trim();
            const type = row.querySelector('.param-type').value;
            const defaultValue = row.querySelector('.param-default').value;
            const description = row.querySelector('.param-description').value.trim();
            
            if (name) {
                parameters.push({
                    name: name,
                    label: label || name,
                    type: type,
                    default: defaultValue,
                    description: description
                });
            }
        });
        
        return {
            name,
            description,
            icon: icon || '⚙️',
            category: category || 'General',
            code,
            inputPorts,
            outputPorts,
            parameters
        };
    }
    
    parsePortsList(portsString) {
        return portsString
            .split(',')
            .map(port => port.trim())
            .filter(port => port.length > 0)
            .filter((port, index, arr) => arr.indexOf(port) === index); // Remove duplicates
    }
    
    updateTemplate(templateId, updates) {
        const template = this.templates.get(templateId);
        if (!template) {
            throw new Error('Template not found');
        }
        
        // Remove from old category
        const oldCategory = this.categories.get(template.category);
        if (oldCategory) {
            const index = oldCategory.findIndex(t => t.id === templateId);
            if (index !== -1) {
                oldCategory.splice(index, 1);
                if (oldCategory.length === 0) {
                    this.categories.delete(template.category);
                }
            }
        }
        
        // Update template
        Object.assign(template, updates);
        
        // Add to new category
        if (!this.categories.has(template.category)) {
            this.categories.set(template.category, []);
        }
        this.categories.get(template.category).push(template);
        
        // Re-render templates
        this.renderTemplates();
        
        console.log('Template updated:', template);
    }
    
    deleteTemplate(templateId) {
        const template = this.templates.get(templateId);
        if (!template) {
            return;
        }
        
        // Remove from category
        const category = this.categories.get(template.category);
        if (category) {
            const index = category.findIndex(t => t.id === templateId);
            if (index !== -1) {
                category.splice(index, 1);
                if (category.length === 0) {
                    this.categories.delete(template.category);
                }
            }
        }
        
        // Remove from templates
        this.templates.delete(templateId);
        
        // Re-render templates
        this.renderTemplates();
        
        console.log('Template deleted:', templateId);
    }
    
    showTemplateContextMenu(e, template) {
        // Remove existing context menu
        const existingMenu = document.getElementById('templateContextMenu');
        if (existingMenu) {
            existingMenu.remove();
        }
        
        // Create context menu
        const menu = document.createElement('div');
        menu.id = 'templateContextMenu';
        menu.style.cssText = `
            position: fixed;
            background: white;
            border: 1px solid #ccc;
            border-radius: 4px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
            z-index: 10000;
            min-width: 120px;
        `;
        
        menu.innerHTML = `
            <div class="context-menu-item" data-action="edit">Edit Template</div>
            <div class="context-menu-item" data-action="duplicate">Duplicate</div>
            <hr style="margin: 4px 0; border: none; border-top: 1px solid #eee;">
            <div class="context-menu-item" data-action="delete" style="color: #dc3545;">Delete</div>
        `;
        
        // Position menu
        menu.style.left = `${e.clientX}px`;
        menu.style.top = `${e.clientY}px`;
        
        document.body.appendChild(menu);
        
        // Add event listeners
        menu.addEventListener('click', (e) => {
            const action = e.target.getAttribute('data-action');
            if (action) {
                this.handleTemplateContextAction(action, template);
                menu.remove();
            }
        });
        
        // Close menu when clicking outside
        setTimeout(() => {
            document.addEventListener('click', function closeMenu() {
                menu.remove();
                document.removeEventListener('click', closeMenu);
            });
        }, 100);
    }
    
    handleTemplateContextAction(action, template) {
        switch (action) {
            case 'edit':
                this.editTemplate(template);
                break;
            case 'duplicate':
                this.duplicateTemplate(template);
                break;
            case 'delete':
                if (confirm(`Are you sure you want to delete the template "${template.name}"?\n\nThis action cannot be undone.`)) {
                    this.deleteTemplate(template.id);
                }
                break;
        }
    }
    
    duplicateTemplate(template) {
        const newId = template.id + '_copy_' + Date.now();
        const newTemplate = {
            ...template,
            id: newId,
            name: template.name + ' Copy'
        };
        
        this.templates.set(newId, newTemplate);
        
        // Add to category
        if (!this.categories.has(newTemplate.category)) {
            this.categories.set(newTemplate.category, []);
        }
        this.categories.get(newTemplate.category).push(newTemplate);
        
        this.renderTemplates();
        
        console.log('Template duplicated:', newTemplate);
    }
    
    createNewTemplate() {
        // Create a blank template object
        const blankTemplate = {
            id: null, // Will be generated when saved
            name: '',
            description: '',
            icon: '⚙️',
            category: 'Custom',
            code: `function process(inputs) {
  // Your custom logic here
  return {
    result: inputs.data
  };
}`,
            inputPorts: [],
            outputPorts: ['result'],
            parameters: []
        };
        
        // Create template editor modal for new template
        const modal = document.createElement('div');
        modal.className = 'modal';
        modal.id = 'newTemplateModal';
        
        modal.innerHTML = `
            <div class="modal-content" style="max-width: 800px; max-height: 90vh;">
                <div class="modal-header">
                    <h3>Create New Template</h3>
                    <span class="close">&times;</span>
                </div>
                <div class="modal-body" style="max-height: 70vh; overflow-y: auto;">
                    <div class="editor-section">
                        <label>Template Name:</label>
                        <input type="text" id="newTemplateName" value="" placeholder="Enter template name" autofocus>
                    </div>
                    <div class="editor-section">
                        <label>Description:</label>
                        <input type="text" id="newTemplateDescription" value="" placeholder="Enter template description">
                    </div>
                    <div class="editor-section">
                        <label>Icon (emoji):</label>
                        <input type="text" id="newTemplateIcon" value="⚙️" placeholder="Enter emoji icon">
                    </div>
                    <div class="editor-section">
                        <label>Category:</label>
                        <input type="text" id="newTemplateCategory" value="Custom" placeholder="Enter category name">
                    </div>
                    <div class="editor-section">
                        <label>Template Code (use {{{parameterName}}} for parameters):</label>
                        <textarea id="newTemplateCode" rows="12" placeholder="Enter template code">${blankTemplate.code}</textarea>
                    </div>
                    <div class="editor-section">
                        <label>Input Ports (comma-separated, optional):</label>
                        <input type="text" id="newTemplateInputPorts" value="" placeholder="data, config (leave empty for no inputs)">
                    </div>
                    <div class="editor-section">
                        <label>Output Ports (comma-separated):</label>
                        <input type="text" id="newTemplateOutputPorts" value="result" placeholder="result, status">
                    </div>
                    <div class="editor-section">
                        <label>Parameters:</label>
                        <div id="newParametersEditor">
                            <!-- Empty initially -->
                        </div>
                        <button type="button" id="newAddParameterBtn" class="btn btn-secondary btn-small">Add Parameter</button>
                    </div>
                </div>
                <div class="modal-footer">
                    <button id="createTemplateBtn" class="btn btn-primary">Create Template</button>
                    <button id="cancelNewTemplateBtn" class="btn btn-secondary">Cancel</button>
                </div>
            </div>
        `;
        
        document.body.appendChild(modal);
        modal.style.display = 'block';
        
        // Add event listeners for new template
        this.addNewTemplateEditorListeners(modal);
        
        // Focus first input
        setTimeout(() => {
            document.getElementById('newTemplateName').focus();
        }, 100);
    }
    
    addNewTemplateEditorListeners(modal) {
        const closeBtn = modal.querySelector('.close');
        const createBtn = modal.querySelector('#createTemplateBtn');
        const cancelBtn = modal.querySelector('#cancelNewTemplateBtn');
        const addParamBtn = modal.querySelector('#newAddParameterBtn');
        
        const closeModal = () => {
            modal.remove();
        };
        
        closeBtn.addEventListener('click', closeModal);
        cancelBtn.addEventListener('click', closeModal);
        
        addParamBtn.addEventListener('click', () => {
            this.addNewParameterRow();
        });
        
        createBtn.addEventListener('click', () => {
            try {
                const templateData = this.collectNewTemplateData();
                this.saveNewTemplate(templateData);
                closeModal();
            } catch (error) {
                alert('Error creating template: ' + error.message);
            }
        });
        
        // Click outside to close
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                closeModal();
            }
        });
    }
    
    addNewParameterRow() {
        const container = document.getElementById('newParametersEditor');
        const newRow = document.createElement('div');
        newRow.className = 'parameter-row';
        newRow.innerHTML = `
            <div style="display: grid; grid-template-columns: 1fr 1fr 1fr auto; gap: 8px; margin-bottom: 8px; align-items: center;">
                <input type="text" class="new-param-name" placeholder="Parameter name" style="font-size: 12px;">
                <input type="text" class="new-param-label" placeholder="Display label" style="font-size: 12px;">
                <select class="new-param-type" style="font-size: 12px;">
                    <option value="text">Text</option>
                    <option value="number">Number</option>
                    <option value="json">JSON</option>
                </select>
                <button type="button" class="btn btn-danger btn-small remove-new-param" style="padding: 2px 6px; font-size: 11px;">×</button>
            </div>
            <input type="text" class="new-param-default" placeholder="Default value" style="width: 100%; margin-bottom: 4px; font-size: 12px;">
            <input type="text" class="new-param-description" placeholder="Description (optional)" style="width: 100%; margin-bottom: 8px; font-size: 12px;">
        `;
        
        container.appendChild(newRow);
        
        // Add remove listener
        const removeBtn = newRow.querySelector('.remove-new-param');
        removeBtn.addEventListener('click', () => {
            newRow.remove();
        });
        
        // Focus the name input
        newRow.querySelector('.new-param-name').focus();
    }
    
    collectNewTemplateData() {
        const name = document.getElementById('newTemplateName').value.trim();
        const description = document.getElementById('newTemplateDescription').value.trim();
        const icon = document.getElementById('newTemplateIcon').value.trim();
        const category = document.getElementById('newTemplateCategory').value.trim();
        const code = document.getElementById('newTemplateCode').value;
        const inputPorts = this.parsePortsList(document.getElementById('newTemplateInputPorts').value);
        const outputPorts = this.parsePortsList(document.getElementById('newTemplateOutputPorts').value);
        
        // Validate
        if (!name) throw new Error('Template name is required');
        if (!code.trim()) throw new Error('Template code is required');
        if (outputPorts.length === 0) throw new Error('At least one output port is required');
        
        // Collect parameters
        const parameters = [];
        const paramRows = document.querySelectorAll('#newParametersEditor .parameter-row');
        paramRows.forEach(row => {
            const name = row.querySelector('.new-param-name').value.trim();
            const label = row.querySelector('.new-param-label').value.trim();
            const type = row.querySelector('.new-param-type').value;
            const defaultValue = row.querySelector('.new-param-default').value;
            const description = row.querySelector('.new-param-description').value.trim();
            
            if (name) {
                parameters.push({
                    name: name,
                    label: label || name,
                    type: type,
                    default: defaultValue,
                    description: description
                });
            }
        });
        
        return {
            name,
            description,
            icon: icon || '⚙️',
            category: category || 'Custom',
            code,
            inputPorts,
            outputPorts,
            parameters
        };
    }
    
    saveNewTemplate(templateData) {
        // Generate unique ID for new template
        const templateId = 'custom_' + templateData.name.toLowerCase().replace(/[^a-z0-9]/g, '_') + '_' + Date.now();
        
        // Create the template using defineTemplate
        const newTemplate = this.defineTemplate(templateId, templateData);
        
        // Re-render templates to show the new template
        this.renderTemplates();
        
        console.log('New template created:', newTemplate);
        
        // Show success message
        alert(`Template "${templateData.name}" created successfully!`);
    }
}

// Global template manager instance
window.templateManager = new TemplateManager();
