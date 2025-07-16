package diecup;

import java.util.HashMap;
import java.util.Map;

public class Statistics {
    private Map<Integer, Double> probabilities;

    public Statistics(int numberOfDice, int sides) {
        calculateProbabilities(numberOfDice, sides);
    }

    /**
     * Returns the empirical probability of rolling each face value with the
     * configured number of dice and sides. The map contains an entry for each
     * face (1..sides) mapping to its estimated probability based on simulation.
     *
     * @return a map from face value to its estimated probability
     */
    public Map<Integer, Double> getProbabilities() {
        return probabilities;
    }

    private void calculateProbabilities(int numberOfDice, int sides) {
        int iterations = 10000;
        Map<Integer, Integer> result = new HashMap<>();

        for (int i = 0; i < iterations; i++) {
            DieCup dieCup = new DieCup(numberOfDice, sides);
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
