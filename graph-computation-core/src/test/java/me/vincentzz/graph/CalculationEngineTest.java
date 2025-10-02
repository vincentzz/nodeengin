package me.vincentzz.graph;

import me.vincentzz.graph.model.ResourceIdentifier;
import me.vincentzz.graph.model.Snapshot;
import me.vincentzz.graph.node.AtomicNode;
import me.vincentzz.graph.node.ConnectionPoint;
import me.vincentzz.graph.node.NodeGroup;
import me.vincentzz.graph.scope.Include;
import me.vincentzz.lang.Result.Result;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Test to verify the TODO implementations in CalculationEngine and EvaluationContext
 */
public class CalculationEngineTest {
    
    public static void main(String[] args) {
        System.out.println("=== CalculationEngine TODO Implementation Test ===\n");
        
        try {
            testBasicEvaluation();
            testPathResolution();
            testEvaluationContext();
            System.out.println("✅ All tests passed!");
        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testBasicEvaluation() {
        System.out.println("🔧 Testing Basic Evaluation...");
        
        // Create a simple test setup
        TestDataNode dataNode = new TestDataNode("DataNode", 42.0);
        TestComputeNode computeNode = new TestComputeNode("ComputeNode");
        
        // Only export the "result" resource to avoid multiple provider conflicts
        Set<ConnectionPoint> exportedConnections = Set.of(
            new ConnectionPoint(Path.of("ComputeNode"), new TestResourceId("result"))
        );
        NodeGroup rootGroup = NodeGroup.of("TestSystem", Set.of(dataNode, computeNode), 
                                         Set.of(), Include.of(exportedConnections));
        CalculationEngine engine = new CalculationEngine(rootGroup);
        
        // Test evaluation
        Snapshot snapshot = Snapshot.ofNow();
        Set<ResourceIdentifier> requestedResources = Set.of(new TestResourceId("result"));
        
        Map<ResourceIdentifier, Result<Object>> results = engine.evaluate(snapshot, requestedResources);
        
        if (results.containsKey(new TestResourceId("result"))) {
            Result<Object> result = results.get(new TestResourceId("result"));
            if (result.isSuccess()) {
                System.out.println("   ✅ Basic evaluation successful: " + result.get());
            } else {
                throw new RuntimeException("Evaluation failed: " + result.getException().getMessage());
            }
        } else {
            throw new RuntimeException("Expected result not found");
        }
    }
    
    private static void testPathResolution() {
        System.out.println("🔍 Testing Path Resolution...");
        
        TestDataNode dataNode = new TestDataNode("DataNode", 10.0);
        NodeGroup subGroup = NodeGroup.of("SubGroup", Set.of(dataNode));
        NodeGroup rootGroup = NodeGroup.of("RootSystem", Set.of(subGroup));
        
        CalculationEngine engine = new CalculationEngine(rootGroup);
        
        // Test path-based evaluation
        Snapshot snapshot = Snapshot.ofNow();
        Set<ResourceIdentifier> requestedResources = Set.of(new TestResourceId("data"));
        
        Map<ResourceIdentifier, Result<Object>> results = engine.evaluate(Path.of("RootSystem/SubGroup"), snapshot, requestedResources);
        
        if (results.containsKey(new TestResourceId("data"))) {
            Result<Object> result = results.get(new TestResourceId("data"));
            if (result.isSuccess()) {
                System.out.println("   ✅ Path resolution successful: " + result.get());
            } else {
                throw new RuntimeException("Path evaluation failed: " + result.getException().getMessage());
            }
        } else {
            throw new RuntimeException("Expected data not found in path evaluation");
        }
    }
    
    private static void testEvaluationContext() {
        System.out.println("🏗️ Testing EvaluationContext...");
        
        TestDataNode dataNode = new TestDataNode("DataNode", 100.0);
        TestComputeNode computeNode = new TestComputeNode("ComputeNode");
        
        // Only export the "result" resource to avoid multiple provider conflicts
        Set<ConnectionPoint> exportedConnections = Set.of(
            new ConnectionPoint(Path.of("ComputeNode"), new TestResourceId("result"))
        );
        NodeGroup rootGroup = NodeGroup.of("TestSystem", Set.of(dataNode, computeNode), 
                                         Set.of(), Include.of(exportedConnections));
        
        CalculationEngine engine = new CalculationEngine(rootGroup);
        
        // Evaluate and get context
        Snapshot snapshot = Snapshot.ofNow();
        Set<ResourceIdentifier> requestedResources = Set.of(new TestResourceId("result"));
        
        Map<ResourceIdentifier, Result<Object>> results = engine.evaluate(snapshot, requestedResources);
        
        // Since we can't directly access the context from the engine, 
        // we'll verify that the evaluation worked correctly
        if (results.containsKey(new TestResourceId("result"))) {
            Result<Object> result = results.get(new TestResourceId("result"));
            if (result.isSuccess()) {
                double value = (Double) result.get();
                double expected = 100.0 * 2.0; // DataNode * ComputeNode multiplier
                if (Math.abs(value - expected) < 0.001) {
                    System.out.println("   ✅ EvaluationContext working correctly: " + value);
                } else {
                    throw new RuntimeException("Unexpected result: " + value + ", expected: " + expected);
                }
            } else {
                throw new RuntimeException("Context evaluation failed: " + result.getException().getMessage());
            }
        } else {
            throw new RuntimeException("Expected result not found in context evaluation");
        }
    }
    
    // Test implementations
    private static class TestDataNode implements AtomicNode {
        private final String name;
        private final double value;
        
        public TestDataNode(String name, double value) {
            this.name = name;
            this.value = value;
        }
        
        @Override
        public String name() {
            return name;
        }
        
        @Override
        public Set<ResourceIdentifier> inputs() {
            return Set.of();
        }
        
        @Override
        public Set<ResourceIdentifier> outputs() {
            return Set.of(new TestResourceId("data"));
        }
        
        @Override
        public Set<ResourceIdentifier> resolveDependencies(Snapshot snapshot, Map<ResourceIdentifier, Result<Object>> inputs) {
            return Set.of();
        }
        
        @Override
        public Map<ResourceIdentifier, Result<Object>> compute(Snapshot snapshot, Map<ResourceIdentifier, Result<Object>> dependencyValues) {
            Map<ResourceIdentifier, Result<Object>> results = new HashMap<>();
            results.put(new TestResourceId("data"), me.vincentzz.lang.Result.Success.of(value));
            return results;
        }
        
        @Override
        public Map<String, Object> getConstructionParameters() {
            return Map.of("name", name, "value", value);
        }
    }
    
    private static class TestComputeNode implements AtomicNode {
        private final String name;
        
        public TestComputeNode(String name) {
            this.name = name;
        }
        
        @Override
        public String name() {
            return name;
        }
        
        @Override
        public Set<ResourceIdentifier> inputs() {
            return Set.of(new TestResourceId("data"));
        }
        
        @Override
        public Set<ResourceIdentifier> outputs() {
            return Set.of(new TestResourceId("result"));
        }
        
        @Override
        public Set<ResourceIdentifier> resolveDependencies(Snapshot snapshot, Map<ResourceIdentifier, Result<Object>> inputs) {
            if (!inputs.containsKey(new TestResourceId("data"))) {
                return Set.of(new TestResourceId("data"));
            }
            return Set.of();
        }
        
        @Override
        public Map<ResourceIdentifier, Result<Object>> compute(Snapshot snapshot, Map<ResourceIdentifier, Result<Object>> dependencyValues) {
            Map<ResourceIdentifier, Result<Object>> results = new HashMap<>();
            
            Result<Object> dataResult = dependencyValues.get(new TestResourceId("data"));
            if (dataResult != null && dataResult.isSuccess()) {
                double dataValue = (Double) dataResult.get();
                double result = dataValue * 2.0;
                results.put(new TestResourceId("result"), me.vincentzz.lang.Result.Success.of(result));
            } else {
                results.put(new TestResourceId("result"), 
                           me.vincentzz.lang.Result.Failure.of(new RuntimeException("Missing data dependency")));
            }
            
            return results;
        }
        
        @Override
        public Map<String, Object> getConstructionParameters() {
            return Map.of("name", name);
        }
    }
    
    private static class TestResourceId implements ResourceIdentifier {
        private final String name;
        
        public TestResourceId(String name) {
            this.name = name;
        }
        
        @Override
        public Class<?> type() {
            return String.class;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TestResourceId that = (TestResourceId) obj;
            return name.equals(that.name);
        }
        
        @Override
        public int hashCode() {
            return name.hashCode();
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
}
