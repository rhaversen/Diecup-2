package diecup;

import java.util.HashMap;
import java.util.Map;

public class Statistics {
    Map<Integer, Double> probabilities;

    public Statistics(int numberOfDice, int sides) {
        calculateProbabilities();
    }

    public Map<Integer, Double> getProbabilities() {
        return probabilities;
    }

    private void calculateProbabilities() {
        int iterations = 10000;
        Map<Integer, Integer> result = new HashMap<>();

        for (int i = 0; i < iterations; i++) {
            DieCup dieCup = new DieCup(6, 6);
            Map<Integer, Integer> valuesMap = dieCup.getValuesMap();

            // Add the valuesMap to the result
            for (Map.Entry<Integer, Integer> entry : valuesMap.entrySet()) {
                int key = entry.getKey();
                int preResult = result.getOrDefault(key, 0);
                int newValue = valuesMap.get(key);
                int newResult = preResult + newValue;
                result.put(key, newResult);
            }
        }

        // Create a new map to store normalized values
        Map<Integer, Double> normalizedMap = new HashMap<>();

        // Normalize each value
        for (Map.Entry<Integer, Integer> entry : result.entrySet()) {
            normalizedMap.put(entry.getKey(), entry.getValue() / (double) iterations);
        }
        probabilities = normalizedMap;
    }
}
