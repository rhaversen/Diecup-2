package diecup;

import java.util.HashMap;
import java.util.Map;

public class Statistics {
    private Map<Integer, Double> generalFrequencies;
    
    // New statistics for enhanced strategy
    private Map<Integer, Double> completionProbabilities;  // P(getting at least 1 point for number N)
    private Map<Integer, Double> expectedDiceUsed;         // Expected dice consumed when picking N
    private Map<Integer, Map<Integer, Double>> frequenciesByDiceCount;  // Frequencies for different dice counts
    private Map<Integer, Double> varianceByNumber;         // Variance in points collected per number
    private final int sides;
    private final int maxDice;

    public Statistics(int numberOfDice, int sides) {
        this.sides = sides;
        this.maxDice = numberOfDice;
        calculateGeneralFrequencies(numberOfDice, sides);
        calculateCompletionProbabilities(numberOfDice, sides);
        calculateExpectedDiceUsed(numberOfDice, sides);
        calculateFrequenciesByDiceCount(numberOfDice, sides);
        calculateVarianceByNumber(numberOfDice, sides);
    }

    /**
     * Returns the empirical probability of rolling each face value with the
     * configured number of dice and sides. The map contains an entry for each
     * face (1..sides) mapping to its estimated probability based on simulation.
     *
     * @return a map from face value to its estimated probability
     */
    public Map<Integer, Double> getGeneralFrequencies() {
        return generalFrequencies;
    }

    /**
     * Returns the probability of getting at least 1 point for each number (1-12).
     * Useful for determining how likely it is to make progress on a given number.
     */
    public Map<Integer, Double> getCompletionProbabilities() {
        return completionProbabilities;
    }

    /**
     * Returns the expected number of dice used when selecting each number.
     * Numbers 7-12 use 2 dice per point (pairs), 1-6 use 1 die per point.
     */
    public Map<Integer, Double> getExpectedDiceUsed() {
        return expectedDiceUsed;
    }

    /**
     * Returns frequency maps for different dice counts.
     * Key is the number of dice, value is a map of number -> expected points.
     */
    public Map<Integer, Map<Integer, Double>> getFrequenciesByDiceCount() {
        return frequenciesByDiceCount;
    }

    /**
     * Returns the variance in points collected for each number.
     * Higher variance means more unpredictable outcomes.
     */
    public Map<Integer, Double> getVarianceByNumber() {
        return varianceByNumber;
    }

    /**
     * Returns whether a number requires pairs (7-12) or single dice (1-6).
     */
    public boolean isPairNumber(int number) {
        return number > sides;
    }

    /**
     * Returns the number of sides per die.
     */
    public int getSides() {
        return sides;
    }

    /**
     * Returns the maximum number of dice.
     */
    public int getMaxDice() {
        return maxDice;
    }

    private void calculateGeneralFrequencies(int numberOfDice, int sides) {
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
        generalFrequencies = normalizedMap;
    }

    private void calculateCompletionProbabilities(int numberOfDice, int sides) {
        int iterations = 10000;
        Map<Integer, Integer> hitCount = new HashMap<>();

        for (int i = 0; i < iterations; i++) {
            DieCup dieCup = new DieCup(numberOfDice, sides);
            Map<Integer, Integer> valuesMap = dieCup.getValuesMap();

            for (Map.Entry<Integer, Integer> entry : valuesMap.entrySet()) {
                if (entry.getValue() > 0) {
                    hitCount.merge(entry.getKey(), 1, Integer::sum);
                }
            }
        }

        completionProbabilities = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : hitCount.entrySet()) {
            completionProbabilities.put(entry.getKey(), entry.getValue() / (double) iterations);
        }
    }

    private void calculateExpectedDiceUsed(int numberOfDice, int sides) {
        int iterations = 10000;
        Map<Integer, Integer> totalDiceUsed = new HashMap<>();
        Map<Integer, Integer> hitCount = new HashMap<>();

        for (int i = 0; i < iterations; i++) {
            DieCup dieCup = new DieCup(numberOfDice, sides);
            Map<Integer, Integer> valuesMap = dieCup.getValuesMap();

            for (Map.Entry<Integer, Integer> entry : valuesMap.entrySet()) {
                int number = entry.getKey();
                int points = entry.getValue();
                if (points > 0) {
                    // Numbers 1-6: 1 die per point; 7-12: 2 dice per point
                    int diceUsed = (number <= sides) ? points : points * 2;
                    totalDiceUsed.merge(number, diceUsed, Integer::sum);
                    hitCount.merge(number, 1, Integer::sum);
                }
            }
        }

        expectedDiceUsed = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : totalDiceUsed.entrySet()) {
            int count = hitCount.getOrDefault(entry.getKey(), 1);
            expectedDiceUsed.put(entry.getKey(), entry.getValue() / (double) count);
        }
    }

    private void calculateFrequenciesByDiceCount(int maxDice, int sides) {
        frequenciesByDiceCount = new HashMap<>();
        int iterations = 5000;

        for (int diceCount = 1; diceCount <= maxDice; diceCount++) {
            Map<Integer, Integer> result = new HashMap<>();

            for (int i = 0; i < iterations; i++) {
                DieCup dieCup = new DieCup(diceCount, sides);
                Map<Integer, Integer> valuesMap = dieCup.getValuesMap();

                for (Map.Entry<Integer, Integer> entry : valuesMap.entrySet()) {
                    result.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
            }

            Map<Integer, Double> normalizedMap = new HashMap<>();
            for (Map.Entry<Integer, Integer> entry : result.entrySet()) {
                normalizedMap.put(entry.getKey(), entry.getValue() / (double) iterations);
            }
            frequenciesByDiceCount.put(diceCount, normalizedMap);
        }
    }

    private void calculateVarianceByNumber(int numberOfDice, int sides) {
        int iterations = 10000;
        Map<Integer, Double> sumPoints = new HashMap<>();
        Map<Integer, Double> sumSquaredPoints = new HashMap<>();

        for (int i = 0; i < iterations; i++) {
            DieCup dieCup = new DieCup(numberOfDice, sides);
            Map<Integer, Integer> valuesMap = dieCup.getValuesMap();

            // For each possible number (1-12), record what we got (0 if not present)
            for (int num = 1; num <= sides * 2; num++) {
                double points = valuesMap.getOrDefault(num, 0);
                sumPoints.merge(num, points, Double::sum);
                sumSquaredPoints.merge(num, points * points, Double::sum);
            }
        }

        varianceByNumber = new HashMap<>();
        for (int num = 1; num <= sides * 2; num++) {
            double mean = sumPoints.getOrDefault(num, 0.0) / iterations;
            double meanSquared = sumSquaredPoints.getOrDefault(num, 0.0) / iterations;
            double variance = meanSquared - (mean * mean);
            varianceByNumber.put(num, variance);
        }
    }
}
