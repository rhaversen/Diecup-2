package strategies;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import diecup.Scoreboard;
import diecup.Statistics;

public class WeightedSelect implements Strategy {
    private Map<Integer, Double> generalFrequencies = new HashMap<>();

    public WeightedSelect(int numberOfDice, int sides) {
        Statistics statistics = new Statistics(numberOfDice, sides);
        this.generalFrequencies = statistics.getProbabilities();
    }

    public int getSelectedNumber(Map<Integer, Integer> values, Scoreboard scoreboard) {
        Map<Integer, Double> scores = new HashMap<>();

        for (Map.Entry<Integer, Integer> entry : values.entrySet()) {
            int value = entry.getKey();
            int countInThrow = entry.getValue();
            Integer pointsOnBoard = scoreboard.getPoints().get(value);

            // Only consider values that have less than 5 points on the scoreboard
            if (pointsOnBoard == null || pointsOnBoard < 5) {
                Double frequency = generalFrequencies.get(value);
                if (frequency != null) {
                    double score = (1.0 / frequency) * countInThrow;
                    scores.put(value, score);
                }
            }
        }

        // Select the key with the highest score, or return -1 if no valid values exist
        return scores.isEmpty() ? -1 : Collections.max(scores.entrySet(), Map.Entry.comparingByValue()).getKey();
    }
}
