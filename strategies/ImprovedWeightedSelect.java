package strategies;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import diecup.Scoreboard;
import diecup.Statistics;

public class ImprovedWeightedSelect implements Strategy {
    private Map<Integer, Double> generalFrequencies = new HashMap<>();
    private final double urgencyWeight;
    private final double rarityWeight;
    private final double progressWeight;

    // default constructor using tuned defaults
    public ImprovedWeightedSelect(Statistics statistics) {
        this(statistics,
                getDefaultUrgencyWeight(),
                getDefaultRarityWeight(),
                getDefaultprogressWeight());
    }

    public ImprovedWeightedSelect(Statistics statistics,
            double urgencyWeight,
            double rarityWeight,
            double progressWeight) {
        this.generalFrequencies = statistics.getGeneralFrequencies();
        this.urgencyWeight = urgencyWeight;
        this.rarityWeight = rarityWeight;
        this.progressWeight = progressWeight;
    }

    public int getSelectedNumber(Map<Integer, Integer> values, Scoreboard scoreboard) {
        int maxPoints = 5; // 5 is default max points pr slot (Game rule)
        Map<Integer, Double> scores = new HashMap<>();

        for (Map.Entry<Integer, Integer> entry : values.entrySet()) {
            int value = entry.getKey();
            int countInThrow = entry.getValue();
            int pointsOnBoard = scoreboard.getPoints().getOrDefault(value, 0);

            if (pointsOnBoard < maxPoints) {
                int collectable = Math.min(countInThrow, maxPoints - pointsOnBoard);
                if (collectable > 0) {
                    scores.put(value, calculateScore(value, collectable, pointsOnBoard, maxPoints));
                }
            }
        }

        return scores.isEmpty()
                ? -1
                : Collections.max(scores.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    private double calculateScore(int value, int collectable, int pointsOnBoard, int maxPoints) {
        Double frequency = generalFrequencies.get(value);
        if (frequency == null || frequency == 0) {
            return 0;
        }

        double rarityValue = computeRarityValue(frequency, collectable);
        double progressValue = computeProgressValue(collectable, pointsOnBoard, maxPoints);
        double urgencyValue = computeUrgencyValue(frequency, collectable, maxPoints);

        return rarityWeight * rarityValue
                + progressWeight * progressValue
                + urgencyWeight * urgencyValue;
    }

    private double computeRarityValue(double frequency, int collectable) {
        return collectable / frequency;
    }

    private double computeProgressValue(int collectable, int pointsOnBoard, int maxPoints) {
        double progressBefore = (double) pointsOnBoard / maxPoints;
        double progressAfter = (double) Math.min(pointsOnBoard + collectable, maxPoints) / maxPoints;
        return progressAfter - progressBefore;
    }

    private double computeUrgencyValue(double frequency, int collectable, int maxPoints) {
        double expectedPointsPerTurn = frequency * maxPoints;
        return collectable - expectedPointsPerTurn;
    }

    private static double getDefaultUrgencyWeight() {
        return 0.146;
    }

    private static double getDefaultRarityWeight() {
        return 0.582;
    }

    private static double getDefaultprogressWeight() {
        return 0.926;
    }
}
