package diecup;

import java.util.HashMap;
import java.util.Map;

public class Statistics {
    Map<Integer, Double> probabilities;
    // First entry is each target number, second entry is the probability to get a
    // amount of the target number
    Map<Integer, Map<Integer, Double>> averageCollections;

    public Statistics(int numberOfDice, int sides) {
        calculateProbabilities(numberOfDice, sides);
        calculateAverageCollections(numberOfDice, sides);
    }

    public Map<Integer, Double> getProbabilities() {
        return probabilities;
    }

    public Map<Integer, Map<Integer, Double>> getAverageCollections() {
        return averageCollections;
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

    private void calculateAverageCollections(int numberOfDice, int sides) {
        int iterations = 10000;

        Map<Integer, Map<Integer, Double>> result = new HashMap<>();

        // For each number, run the simulation and tally amount collected
        for (int target = 1; target <= 12; target++) {
            Map<Integer, Double> tally = new HashMap<>();

            for (int i = 0; i < iterations; i++) {
                int diceLeft = numberOfDice;
                int timesCollected = 0;

                while (diceLeft > 0) {
                    DieCup dieCup = new DieCup(diceLeft, sides);

                    boolean canMakeNumber = dieCup.getValuesMap().containsKey(target);

                    if (!canMakeNumber) {
                        break;
                    }

                    int occurrences = dieCup.getValuesMap().get(target);
                    int diceToRemove = dieCup.calculateDiceToRemove(target, occurrences);

                    diceLeft -= diceToRemove;
                    timesCollected += occurrences;
                }

                tally.put(timesCollected, 1 + tally.getOrDefault(timesCollected, 0.0));
            }

            // Divide all tally values by number of iterations
            for (Map.Entry<Integer, Double> entry : tally.entrySet()) {
                int key = entry.getKey();
                double value = entry.getValue();
                tally.put(key, value / iterations);
            }

            result.put(target, tally);
        }
        averageCollections = result;
    }
}
