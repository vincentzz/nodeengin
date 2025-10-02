# Visual Calculation Module

A JavaFX-based visualization tool for EvaluationResult JSON files, featuring an Unreal 5-inspired interface for exploring calculation graphs.

## Features

- **Hierarchical Navigation**: Navigate through node paths using breadcrumb-style navigation bar
- **Interactive Canvas**: Visualize calculation nodes as rectangular blocks with connection points
- **Connection Visualization**: 
  - Black lines for direct dependencies
  - Green lines for conditional dependencies  
  - Blue lines for flywires
- **Connection Points**: Color-coded by resource type (Gold for prices, Green for spreads, Blue for volumes)
- **Interactive Features**:
  - Hover tooltips showing node details and calculation results
  - Click to select nodes
  - Double-click to navigate into NodeGroups
  - Pan and zoom capabilities
- **Dark Theme**: Unreal 5-inspired dark interface

## Usage

### Running the Application

```bash
# Using Maven JavaFX plugin
mvn javafx:run -pl visual-calculation

# Or run the demo class directly
mvn exec:java -pl visual-calculation -Dexec.mainClass="me.vincentzz.visual.VisualCalculationDemo"
```

### Loading Data

1. Launch the application
2. A file chooser will appear on startup
3. Select an EvaluationResult JSON file
4. The visualization will load and display the node graph

### Navigation

- **Path Navigation**: Click on breadcrumb segments to navigate up the hierarchy
- **Node Interaction**: 
  - Single click to select a node
  - Double click to navigate into NodeGroups
  - Hover for detailed tooltips
- **Canvas**: Pan by dragging, use scroll wheel to zoom

### Sample Data

A sample EvaluationResult JSON file is included at:
`visual-calculation/src/main/resources/sample-evaluation-result.json`

## Architecture

### Key Components

- **VisualCalculationApplication**: Main JavaFX application entry point
- **MainController**: Coordinates between model and view components
- **VisualizationModel**: Processes EvaluationResult data for visualization
- **CalculationCanvas**: Custom Canvas component for rendering nodes and connections
- **PathNavigationBar**: Breadcrumb navigation component

### Data Flow

1. EvaluationResult JSON → VisualizationModel
2. VisualizationModel → NodeViewModel + ConnectionViewModel
3. ViewModels → CalculationCanvas rendering
4. User interactions → MainController → Model updates

### Styling

The application uses a comprehensive CSS stylesheet (`styles.css`) that provides:
- Unreal 5-inspired dark theme
- Consistent component styling
- Hover and focus effects
- Professional appearance

## Dependencies

- JavaFX 21+ (for UI framework)
- Jackson (for JSON processing) 
- graph-computation-core (for EvaluationResult models)
- falcon-calculation-kit (for domain-specific types)

## Requirements

- Java 21+
- JavaFX runtime
- Maven 3.6+

## Building

```bash
# Build the module
mvn clean compile -pl visual-calculation

# Build with dependencies
mvn clean package -pl visual-calculation

# Build entire project
mvn clean package
