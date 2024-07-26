package strategies;
import java.util.*;

import diecup.Scoreboard;
import diecup.Statistics;

public class AdvancedWeightedSelect implements Strategy {
    private Map<Integer, Double> generalFrequencies = new HashMap<>();

    public AdvancedWeightedSelect(int numberOfDice, int sides) {
        Statistics statistics = new Statistics(numberOfDice, sides);
        this.generalFrequencies = statistics.getProbabilities();
    }

    public int getSelectedNumber(Map<Integer, Integer> valueMap, Scoreboard scoreboard) {
        int bestSum = -1;
        double bestValue = -Double.MAX_VALUE;

        for (Map.Entry<Integer, Integer> entry : valueMap.entrySet()) {
            int sum = entry.getKey();
            int count = entry.getValue();
            Integer pointsOnBoard = scoreboard.getPoints().getOrDefault(sum, 0);

            if (pointsOnBoard < 5) { // Ignore already completed sums
                double score = evaluateScore(sum, count, pointsOnBoard, valueMap.size(), scoreboard);
                if (score > bestValue) {
                    bestValue = score;
                    bestSum = sum;
                }
            }
        }

        return bestSum;
    }

    private double evaluateScore(int sum, int count, int pointsOnBoard, int diceLeft, Scoreboard scoreboard) {
        double completionPriority = (5 - pointsOnBoard) * 10; // More points if closer to completion
        double frequencyScore = count * 5; // Base score influenced by how many times the sum can be achieved
        double rarityBonus = (1.0 / generalFrequencies.getOrDefault(sum, 1.0)) * 2; // Rarer sums get a higher bonus

        // Predictive roll-over potential
        double predictiveScore = 0;
        if (pointsOnBoard + count <= 5) {
            predictiveScore = (count * generalFrequencies.getOrDefault(sum, 0.0)) * 5;
        }

        // Dynamic adjustment based on game stage
        double dynamicAdjustment = diceLeft > 15 ? frequencyScore : completionPriority; // More aggressive on frequency early on

        return completionPriority + frequencyScore + rarityBonus + predictiveScore + dynamicAdjustment;
    }
}
