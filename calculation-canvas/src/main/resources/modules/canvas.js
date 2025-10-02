// Canvas management for drawing connections
class CanvasManager {
    constructor() {
        this.canvas = document.getElementById('connectionCanvas');
        this.ctx = this.canvas.getContext('2d');
        this.connections = [];
        this.tempConnection = null;
        this.redrawScheduled = false;
        this.lastRedrawTime = 0;
        this.minRedrawInterval = 16; // ~60fps
        this.drawingEnabled = true; // Flag to control drawing during layout changes
        
        // Zoom and pan state
        this.zoom = 1.0;
        this.minZoom = 0.1;
        this.maxZoom = 3.0;
        this.panX = 0;
        this.panY = 0;
        this.isPanning = false;
        this.lastPanX = 0;
        this.lastPanY = 0;
        
        this.resizeCanvas();
        this.initZoomAndPan();
        
        // Handle canvas resize
        window.addEventListener('resize', () => this.resizeCanvas());
        
        // Handle workspace double-clicks for connection deletion (since nodes block canvas)
        const workspace = document.getElementById('workspace');
        workspace.addEventListener('dblclick', (e) => this.handleWorkspaceClick(e));
    }
    
    initZoomAndPan() {
        const workspace = document.getElementById('workspace');
        
        // Mouse wheel zoom
        workspace.addEventListener('wheel', (e) => {
            e.preventDefault();
            
            const rect = this.canvas.getBoundingClientRect();
            const mouseX = e.clientX - rect.left;
            const mouseY = e.clientY - rect.top;
            
            const zoomFactor = e.deltaY > 0 ? 0.9 : 1.1;
            const newZoom = Math.max(this.minZoom, Math.min(this.maxZoom, this.zoom * zoomFactor));
            
            if (newZoom !== this.zoom) {
                // Zoom toward mouse position
                const zoomRatio = newZoom / this.zoom;
                this.panX = mouseX - (mouseX - this.panX) * zoomRatio;
                this.panY = mouseY - (mouseY - this.panY) * zoomRatio;
                this.zoom = newZoom;
                
                this.applyTransformToWorkspace();
                this.redraw();
            }
        }, { passive: false });
        
        // Middle mouse button pan
        workspace.addEventListener('mousedown', (e) => {
            if (e.button === 1) { // Middle mouse button
                e.preventDefault();
                this.isPanning = true;
                this.lastPanX = e.clientX;
                this.lastPanY = e.clientY;
                workspace.style.cursor = 'move';
            }
        });
        
        workspace.addEventListener('mousemove', (e) => {
            if (this.isPanning) {
                const deltaX = e.clientX - this.lastPanX;
                const deltaY = e.clientY - this.lastPanY;
                
                this.panX += deltaX;
                this.panY += deltaY;
                
                this.lastPanX = e.clientX;
                this.lastPanY = e.clientY;
                
                this.applyTransformToWorkspace();
                this.redraw();
            }
        });
        
        workspace.addEventListener('mouseup', (e) => {
            if (e.button === 1) {
                this.isPanning = false;
                workspace.style.cursor = '';
            }
        });
        
        // Handle mouse leave to stop panning
        workspace.addEventListener('mouseleave', () => {
            this.isPanning = false;
            workspace.style.cursor = '';
        });
    }
    
    applyTransformToWorkspace() {
        const nodeContainer = document.getElementById('nodeContainer');
        if (nodeContainer) {
            nodeContainer.style.transform = `translate(${this.panX}px, ${this.panY}px) scale(${this.zoom})`;
            nodeContainer.style.transformOrigin = '0 0';
        }
    }
    
    resizeCanvas() {
        const workspace = document.getElementById('workspace');
        const newWidth = workspace.clientWidth;
        const newHeight = workspace.clientHeight;
        
        // Only resize if dimensions actually changed
        if (this.canvas.width !== newWidth || this.canvas.height !== newHeight) {
            this.canvas.width = newWidth;
            this.canvas.height = newHeight;
            
            // Force a complete redraw after resize
            requestAnimationFrame(() => {
                this.redraw();
            });
        }
    }
    
    addConnection(fromNode, fromPort, toNode, toPort) {
        const connection = {
            id: this.generateId(),
            fromNode,
            fromPort,
            toNode,
            toPort
        };
        
        this.connections.push(connection);
        this.redraw();
        return connection;
    }
    
    removeConnection(connection) {
        const index = this.connections.indexOf(connection);
        if (index > -1) {
            this.connections.splice(index, 1);
            this.redraw();
        }
    }
    
    setTempConnection(fromNode, fromPort, mouseX, mouseY) {
        this.tempConnection = {
            fromNode,
            fromPort,
            toX: mouseX,
            toY: mouseY
        };
        this.redraw();
    }
    
    updateTempConnection(mouseX, mouseY) {
        if (this.tempConnection) {
            this.tempConnection.toX = mouseX;
            this.tempConnection.toY = mouseY;
            this.redraw();
        }
    }
    
    clearTempConnection() {
        this.tempConnection = null;
        this.redraw();
    }
    
    getConnectionAt(x, y) {
        const tolerance = 15; // Increased tolerance for easier clicking
        
        for (let connection of this.connections) {
            const fromPos = this.getPortDrawPosition(connection.fromNode, connection.fromPort, 'output');
            const toPos = this.getPortDrawPosition(connection.toNode, connection.toPort, 'input');
            
            if (fromPos && toPos) {
                if (this.isPointNearBezier(x, y, fromPos.x, fromPos.y, toPos.x, toPos.y, tolerance)) {
                    return connection;
                }
            }
        }
        
        return null;
    }
    
    isPointNearBezier(px, py, x1, y1, x2, y2, tolerance) {
        // Simplified distance check for bezier curve
        const midX = (x1 + x2) / 2;
        const midY = (y1 + y2) / 2;
        
        // Check distance to line segments
        const d1 = this.distanceToLine(px, py, x1, y1, midX, midY);
        const d2 = this.distanceToLine(px, py, midX, midY, x2, y2);
        
        return Math.min(d1, d2) <= tolerance;
    }
    
    distanceToLine(px, py, x1, y1, x2, y2) {
        const dx = x2 - x1;
        const dy = y2 - y1;
        const length = Math.sqrt(dx * dx + dy * dy);
        
        if (length === 0) return Math.sqrt((px - x1) ** 2 + (py - y1) ** 2);
        
        const t = Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / (length * length)));
        const projX = x1 + t * dx;
        const projY = y1 + t * dy;
        
        return Math.sqrt((px - projX) ** 2 + (py - projY) ** 2);
    }
    
    handleWorkspaceClick(e) {
        // Skip if clicking on a node or port
        if (e.target.closest('.node') || e.target.closest('.port')) {
            return;
        }
        
        const rect = document.getElementById('workspace').getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;
        
        const connection = this.getConnectionAt(x, y);
        if (connection) {
            this.removeConnection(connection);
            
            // Update port states
            const fromPortEl = this.getPortElement(connection.fromNode, connection.fromPort, 'output');
            const toPortEl = this.getPortElement(connection.toNode, connection.toPort, 'input');
            
            if (fromPortEl) fromPortEl.classList.remove('connected');
            if (toPortEl) toPortEl.classList.remove('connected');
            
            // Show feedback message
            this.showConnectionDeleteMessage(connection);
        }
    }
    
    handleCanvasClick(e) {
        const rect = this.canvas.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;
        
        const connection = this.getConnectionAt(x, y);
        if (connection) {
            this.removeConnection(connection);
            
            // Update port states
            const fromPortEl = this.getPortElement(connection.fromNode, connection.fromPort, 'output');
            const toPortEl = this.getPortElement(connection.toNode, connection.toPort, 'input');
            
            if (fromPortEl) fromPortEl.classList.remove('connected');
            if (toPortEl) toPortEl.classList.remove('connected');
            
            // Show feedback message
            this.showConnectionDeleteMessage(connection);
        }
    }
    
    showConnectionDeleteMessage(connection) {
        const statusEl = document.getElementById('status');
        if (statusEl) {
            statusEl.textContent = `Deleted connection: ${connection.fromNode}.${connection.fromPort} → ${connection.toNode}.${connection.toPort}`;
            setTimeout(() => {
                statusEl.textContent = 'Ready';
            }, 2000);
        }
    }
    
    getPortElement(nodeId, portName, type) {
        const node = document.querySelector(`[data-node-id="${nodeId}"]`);
        if (!node) return null;
        
        const selector = type === 'input' ? '.input-port' : '.output-port';
        const ports = node.querySelectorAll(selector);
        
        for (let port of ports) {
            if (port.textContent.trim() === portName) {
                return port;
            }
        }
        return null;
    }
    
    redraw() {
        // Throttle redraw operations for performance
        const now = performance.now();
        if (this.redrawScheduled || (now - this.lastRedrawTime < this.minRedrawInterval)) {
            if (!this.redrawScheduled) {
                this.redrawScheduled = true;
                requestAnimationFrame(() => this.performRedraw());
            }
            return;
        }
        
        this.performRedraw();
    }
    
    performRedraw() {
        this.redrawScheduled = false;
        this.lastRedrawTime = performance.now();
        
        // Clear canvas efficiently
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        
        // Skip drawing if disabled (during layout changes)
        if (!this.drawingEnabled) {
            return;
        }
        
        // Use batch rendering for better performance
        this.ctx.save();
        
        // Draw existing connections
        this.connections.forEach(connection => {
            this.drawConnection(connection);
        });
        
        // Draw temporary connection
        if (this.tempConnection) {
            this.drawTempConnection(this.tempConnection);
        }
        
        this.ctx.restore();
    }
    
    // Temporarily disable drawing during layout changes
    disableDrawing() {
        this.drawingEnabled = false;
        // Clear canvas immediately
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
    }
    
    // Re-enable drawing and redraw immediately
    enableDrawing() {
        this.drawingEnabled = true;
        this.redraw();
    }
    
    drawConnection(connection) {
        const fromPos = this.getPortDrawPosition(connection.fromNode, connection.fromPort, 'output');
        const toPos = this.getPortDrawPosition(connection.toNode, connection.toPort, 'input');
        
        if (fromPos && toPos) {
            this.drawBezierCurve(fromPos.x, fromPos.y, toPos.x, toPos.y, '#3498db', 2);
        }
    }
    
    drawTempConnection(tempConnection) {
        const fromPos = this.getPortDrawPosition(tempConnection.fromNode, tempConnection.fromPort, 'output');
        
        if (fromPos) {
            this.drawBezierCurve(fromPos.x, fromPos.y, tempConnection.toX, tempConnection.toY, '#f39c12', 2, true);
        }
    }
    
    drawBezierCurve(x1, y1, x2, y2, color, width, dashed = false) {
        // Optimize rendering by batching style changes
        this.ctx.strokeStyle = color;
        this.ctx.lineWidth = width;
        
        if (dashed) {
            this.ctx.setLineDash([5, 5]);
        } else {
            this.ctx.setLineDash([]);
        }
        
        // Calculate control points for bezier curve
        const dx = Math.abs(x2 - x1);
        const controlOffset = Math.min(dx / 2, 100);
        
        const cp1x = x1 + controlOffset;
        const cp1y = y1;
        const cp2x = x2 - controlOffset;
        const cp2y = y2;
        
        this.ctx.beginPath();
        this.ctx.moveTo(x1, y1);
        this.ctx.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, x2, y2);
        this.ctx.stroke();
    }
    
    getPortPosition(nodeId, portName, type) {
        const nodeEl = document.querySelector(`[data-node-id="${nodeId}"]`);
        if (!nodeEl) return null;
        
        const portEl = this.getPortElement(nodeId, portName, type);
        if (!portEl) return null;
        
        const nodeRect = nodeEl.getBoundingClientRect();
        const portRect = portEl.getBoundingClientRect();
        const workspaceRect = document.getElementById('workspace').getBoundingClientRect();
        
        // Get raw position relative to workspace
        let x = type === 'output' 
            ? portRect.right - workspaceRect.left
            : portRect.left - workspaceRect.left;
        let y = portRect.top + portRect.height / 2 - workspaceRect.top;
        
        // Transform coordinates to account for zoom and pan (for connection detection)
        x = (x - this.panX) / this.zoom;
        y = (y - this.panY) / this.zoom;
        
        return { x, y };
    }
    
    getPortDrawPosition(nodeId, portName, type) {
        const nodeEl = document.querySelector(`[data-node-id="${nodeId}"]`);
        if (!nodeEl) return null;
        
        const portEl = this.getPortElement(nodeId, portName, type);
        if (!portEl) return null;
        
        // Force layout recalculation to ensure fresh coordinates
        const workspace = document.getElementById('workspace');
        workspace.getBoundingClientRect(); // Force reflow
        
        const portRect = portEl.getBoundingClientRect();
        
        // Calculate the absolute position of the CSS pseudo-element dot centers in viewport
        // Dots: 8px + 2px border = 12px total visual size, positioned with top: 50%, transform: translateY(-50%)
        const dotAbsoluteY = portRect.top + portRect.height / 2;
        
        let dotAbsoluteX;
        if (type === 'output') {
            // Output dot: right: -6px positions right edge of 8px element 6px right of port's right edge
            // Visual center accounting for 2px border: right edge + 6px - 6px (half of 12px total) = right edge + 0px
            dotAbsoluteX = portRect.right;
        } else {
            // Input dot: left: -6px positions left edge of 8px element 6px left of port's left edge  
            // Visual center accounting for 2px border: left edge - 6px + 6px (half of 12px total) = left edge + 0px
            dotAbsoluteX = portRect.left;
        }
        
        // Convert from viewport coordinates to canvas coordinates
        const canvasRect = this.canvas.getBoundingClientRect();
        const canvasX = dotAbsoluteX - canvasRect.left;
        const canvasY = dotAbsoluteY - canvasRect.top;
        
        return { x: canvasX, y: canvasY };
    }
    
    // Reset zoom and pan to default
    resetZoom() {
        this.zoom = 1.0;
        this.panX = 0;
        this.panY = 0;
        this.applyTransformToWorkspace();
        this.redraw();
    }
    
    // Get current zoom level
    getZoom() {
        return this.zoom;
    }
    
    // Get current pan position
    getPan() {
        return { x: this.panX, y: this.panY };
    }
    
    clearAll() {
        this.connections = [];
        this.tempConnection = null;
        this.redraw();
    }
    
    generateId() {
        return 'conn_' + Math.random().toString(36).substr(2, 9);
    }
    
    getConnections() {
        return this.connections;
    }
    
    getConnectionsForNode(nodeId) {
        return this.connections.filter(conn => 
            conn.fromNode === nodeId || conn.toNode === nodeId
        );
    }
}

// Global canvas manager instance
window.canvasManager = new CanvasManager();
