package strategies;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import diecup.Scoreboard;
import diecup.Statistics;

public class AdvancedWeightedSelect implements Strategy {
    private Map<Integer, Double> generalFrequencies = new HashMap<>();

    public AdvancedWeightedSelect(int numberOfDice, int sides) {
        Statistics statistics = new Statistics(numberOfDice, sides);
        this.generalFrequencies = statistics.getProbabilities();
    }

    public int getSelectedNumber(Map<Integer, Integer> values, Scoreboard scoreboard) {
        Map<Integer, Double> scores = new HashMap<>();

        for (Map.Entry<Integer, Integer> entry : values.entrySet()) {
            int value = entry.getKey();
            int countInThrow = entry.getValue();
            int pointsOnBoard = scoreboard.getPoints().getOrDefault(value, 0);
            int remainingPointsOnBoard = 5 - pointsOnBoard;
            
            // Consider "wasted" dice as points which can not be collected due to amount being higher than remaining score
            int collectablePoints = Math.min(countInThrow, remainingPointsOnBoard);

            // Only consider values that have less than 5 points on the scoreboard
            if (pointsOnBoard < 5) {
                Double frequency = generalFrequencies.get(value);
                if (frequency != null) {
                    double score = (1.0 / frequency) * collectablePoints;
                    scores.put(value, score);
                }
            }
        }

        // Select the key with the highest score, or return -1 if no valid values exist
        return scores.isEmpty() ? -1 : Collections.max(scores.entrySet(), Map.Entry.comparingByValue()).getKey();
    }
}
