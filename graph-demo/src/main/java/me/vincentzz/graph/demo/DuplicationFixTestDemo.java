package me.vincentzz.graph.demo;

import me.vincentzz.falcon.ifo.FalconResourceId;
import me.vincentzz.falcon.attribute.Bid;
import me.vincentzz.graph.json.ConstructionalJsonUtil;

/**
 * Test to verify that FalconResourceId JSON serialization works correctly.
 */
public class DuplicationFixTestDemo {

    public static void main(String[] args) {
        System.out.println("=== Testing FalconResourceId Serialization ===\n");

        try {
            // Create a FalconResourceId
            FalconResourceId rid = FalconResourceId.of("GOOGLE", "HARDCODED", Bid.class);

            // Use the standard ObjectMapper from ConstructionalJsonUtil
            String json = ConstructionalJsonUtil.getObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(rid);
            System.out.println("Serialized FalconResourceId JSON:");
            System.out.println(json);
            
            // Check for duplications
            String[] lines = json.split("\n");
            boolean foundDuplication = false;
            
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.contains("\"ifo\"") || trimmed.contains("\"source\"") || trimmed.contains("\"attribute\"")) {
                    // Count occurrences of each field in the entire JSON
                    String ifoPattern = "\"ifo\"";
                    String sourcePattern = "\"source\"";
                    String attributePattern = "\"attribute\"";
                    
                    long ifoOccurrences = countOccurrences(json, ifoPattern);
                    long sourceOccurrences = countOccurrences(json, sourcePattern);
                    long attributeOccurrences = countOccurrences(json, attributePattern);
                    
                    System.out.println("\nField occurrence counts:");
                    System.out.println("\"ifo\": " + ifoOccurrences);
                    System.out.println("\"source\": " + sourceOccurrences);
                    System.out.println("\"attribute\": " + attributeOccurrences);
                    
                    if (ifoOccurrences > 1 || sourceOccurrences > 1 || attributeOccurrences > 1) {
                        foundDuplication = true;
                        System.out.println("\n❌ DUPLICATION DETECTED!");
                    } else {
                        System.out.println("\n✅ NO DUPLICATION FOUND!");
                    }
                    break;
                }
            }
            
            if (!foundDuplication && !json.contains("\"ifo\"")) {
                System.out.println("\n⚠️  No fields found - possible serialization issue");
            }
            
            // Test if we can still create another ResourceIdentifier with same data
            System.out.println("\n--- Testing Manual Creation ---");
            FalconResourceId rid2 = FalconResourceId.of("GOOGLE", "HARDCODED", Bid.class);
            
            // Verify equality
            if (rid.equals(rid2)) {
                System.out.println("✅ ResourceIdentifier equality works correctly!");
            } else {
                System.out.println("❌ ResourceIdentifier equality failed!");
                System.out.println("Original: " + rid);
                System.out.println("Second: " + rid2);
            }
            
        } catch (Exception e) {
            System.err.println("❌ Test failed with exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static long countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}
