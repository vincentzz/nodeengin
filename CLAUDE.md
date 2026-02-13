# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build entire project (from root)
mvn clean package

# Build a single module (with dependencies)
mvn clean compile -pl visual-calculation -am

# Run all tests
mvn test

# Run tests for a single module
mvn test -pl graph-computation-core

# Run a single test class
mvn test -pl graph-computation-core -Dtest=CalculationEngineTest

# Run a single test method
mvn test -pl graph-computation-core -Dtest=CalculationEngineTest#testMethodName

# Run the visual-calculation JavaFX app
mvn javafx:run -pl visual-calculation
```

**Note:** `graph-computation-core` and `graph-demo` have standalone pom.xml files (no `<parent>` reference). They must be installed locally (`mvn install -pl graph-computation-core`) before dependent modules can resolve them in a fresh environment.

## Project Overview

A hierarchical dependency-graph calculation engine with a JavaFX visualization tool, built for financial computations. Java 21, Maven multi-module.

## Module Dependency Chain

```
graph-computation-core  (standalone pom, core engine)
        ↓
falcon-calculation-kit  (financial domain nodes)
        ↓
visual-calculation      (JavaFX graph visualizer)

graph-demo              (standalone pom, demo/test harness)
calculation-canvas      (minimal, mostly empty)
```

## Architecture

### Core Abstractions (`graph-computation-core`)

- **`CalculationNode`** — sealed interface: `AtomicNode | NodeGroup`. Nodes declare `inputs()`, `outputs()` as sets of `ResourceIdentifier`.
- **`AtomicNode`** — leaf computation node. Implements `compute(Snapshot, dependencies) → Map<ResourceIdentifier, Result<Object>>` and `resolveDependencies()` for multi-stage dependency discovery.
- **`NodeGroup`** — hierarchical container of `CalculationNode`s. Has no compute logic itself; all evaluation is driven by `CalculationEngine`. Controls output visibility via `Scope<ConnectionPoint>` (Include/Exclude).
- **`ResourceIdentifier`** — interface with a single `type()` method. Identity is based on the implementing class's equality (e.g., `FalconResourceId` is a record with `ifo`, `source`, `attribute`).
- **`Flywire`** — cross-hierarchy connection between `ConnectionPoint`s (nodePath + ResourceIdentifier). Type-checked at construction.
- **`CalculationEngine`** — takes a root `CalculationNode`, builds three indexes (path→node, scoped providers, scoped flywires), then evaluates requested resources against a `Snapshot`. Handles dependency resolution, caching, cycle detection, and adhoc overrides.
- **`Result<T>`** — sealed `Success | Failure` monad used throughout for error propagation. Created via `Result.Try(() -> ...)`.
- **`EvaluationResult`** — immutable record containing snapshot, results map, per-node evaluation traces (`NodeEvaluation`), and the full graph. Serializable to/from JSON.

### Dependency Resolution Order in CalculationEngine

1. Check adhoc overrides
2. Check flywires (adhoc flywires first, then graph-defined)
3. Resolve from sibling nodes in same scope
4. Recurse to parent group scope
5. Fail if unresolvable

### JSON Serialization (`graph-computation-core` json package)

Uses custom Jackson serializers/deserializers for all core types. `NodeTypeRegistry` provides reflection-based polymorphic deserialization — node types and resource types must be registered before deserializing:

```java
NodeTypeRegistry.registerNodeType("AskProvider", AskProvider.class);
NodeTypeRegistry.registerResourceType("FalconResourceId", FalconResourceId.class);
```

### Financial Domain (`falcon-calculation-kit`)

- **`FalconResourceId(ifo, source, attribute)`** — three-part resource identifier (instrument, data source, attribute class).
- Attribute marker classes: `Ask`, `Bid`, `MidPrice`, `Spread`, `Volume`, `Vwap`, `MarkToMarket`.
- Node implementations are records implementing `AtomicNode` (e.g., `AskProvider`, `MidSpreadCalculator`). Each must implement `getConstructionParameters()` for JSON round-tripping.

### Visual Calculation (`visual-calculation`)

JavaFX app (dark Unreal 5-inspired theme). Loads `EvaluationResult` JSON → `VisualizationModel` → `NodeViewModel`/`ConnectionViewModel` → `CalculationCanvas` rendering. `PathNavigationBar` provides breadcrumb navigation through the node hierarchy. `MainController` orchestrates model↔view interaction.

## Conventions

- Nodes are typically Java records.
- `java.nio.file.Path` is used for node addressing within the graph (not filesystem paths). Root is `Path.of("/")`.
- Tests use JUnit 5 + AssertJ. Financial demo tests are in `falcon-calculation-kit`.
- Functional utilities (`TFunction`, `TSupplier`, `TRunnable`) are checked-exception-friendly variants of `java.util.function` interfaces.
