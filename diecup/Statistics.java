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
    private double[] expectedTurnValue;                    // Expected total points in a turn starting with N dice
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
        calculateExpectedTurnValue(numberOfDice, sides);
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
     * Returns the expected total points collectible in a turn starting with N dice.
     * Index 0 = 0 dice (always 0), index 1 = 1 die, ..., index maxDice = maxDice dice.
     * This accounts for the fact that completing a slot or using all dice gives a free turn.
     */
    public double getExpectedTurnValue(int diceCount) {
        if (diceCount < 0) return 0;
        if (diceCount >= expectedTurnValue.length) return expectedTurnValue[expectedTurnValue.length - 1];
        return expectedTurnValue[diceCount];
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

    /**
     * Calculate expected points per turn for each starting dice count.
     * Uses Monte Carlo simulation with a greedy "pick highest expected value" strategy.
     * This gives us a baseline for "how valuable are N remaining dice?"
     */
    private void calculateExpectedTurnValue(int maxDice, int sides) {
        expectedTurnValue = new double[maxDice + 1];
        expectedTurnValue[0] = 0;  // 0 dice = 0 expected points
        
        int iterations = 20000;
        
        for (int startDice = 1; startDice <= maxDice; startDice++) {
            double totalPoints = 0;
            
            for (int i = 0; i < iterations; i++) {
                totalPoints += simulateTurn(startDice, sides, maxDice);
            }
            
            expectedTurnValue[startDice] = totalPoints / iterations;
        }
    }
    
    /**
     * Simulate a single turn starting with given dice count.
     * Uses a simple greedy strategy: always pick the option that gives most immediate points.
     * Returns total points collected in this turn.
     */
    private double simulateTurn(int startDice, int sides, int maxDice) {
        int currentDice = startDice;
        double pointsThisTurn = 0;
        
        // Simplified scoreboard: track points per number (1 to 2*sides)
        int[] points = new int[sides * 2 + 1];
        int maxPointsPerSlot = 5;
        
        while (currentDice > 0) {
            DieCup cup = new DieCup(currentDice, sides);
            Map<Integer, Integer> roll = cup.getValuesMap();
            
            // Find best option (greedy: most points we can collect)
            int bestNumber = -1;
            int bestCollectable = 0;
            int bestDiceUsed = 0;
            
            for (Map.Entry<Integer, Integer> entry : roll.entrySet()) {
                int number = entry.getKey();
                int available = entry.getValue();
                int currentPoints = points[number];
                int collectable = Math.min(available, maxPointsPerSlot - currentPoints);
                
                if (collectable > bestCollectable) {
                    bestCollectable = collectable;
                    bestNumber = number;
                    bestDiceUsed = (number <= sides) ? collectable : collectable * 2;
                }
            }
            
            if (bestNumber == -1 || bestCollectable == 0) {
                // Can't collect anything - turn ends
                break;
            }
            
            // Collect points
            pointsThisTurn += bestCollectable;
            points[bestNumber] += bestCollectable;
            
            // Check for free turn conditions
            boolean completedSlot = (points[bestNumber] == maxPointsPerSlot);
            boolean usedAllDice = (bestDiceUsed >= currentDice);
            
            if (completedSlot || usedAllDice) {
                // Free turn - reset to max dice
                currentDice = maxDice;
            } else {
                // Subtract used dice
                currentDice -= bestDiceUsed;
            }
        }
        
        return pointsThisTurn;
    }
}
