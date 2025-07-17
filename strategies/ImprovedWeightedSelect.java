package strategies;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import diecup.Scoreboard;
import diecup.Statistics;

public class ImprovedWeightedSelect implements Strategy {
    private Map<Integer, Double> generalFrequencies = new HashMap<>();
    private final double urgencyWeight;
    private final double futureWeight;
    private final double rarityWeight;

    // default constructor using tuned defaults
    public ImprovedWeightedSelect(Statistics statistics) {
        this(statistics,
                getDefaultUrgencyWeight(),
                getDefaultFutureWeight(),
                getDefaultRarityWeight());
    }

    public ImprovedWeightedSelect(Statistics statistics,
            double urgencyWeight,
            double futureWeight,
            double rarityWeight) {
        this.generalFrequencies = statistics.getProbabilities();
        this.urgencyWeight = urgencyWeight;
        this.futureWeight = futureWeight;
        this.rarityWeight = rarityWeight;
    }

    public int getSelectedNumber(Map<Integer, Integer> values, Scoreboard scoreboard) {
        int maxPoints = 5; // default max points, can be adjusted as needed
        int totalSlots = 12; // default total slots, can be adjusted as needed

        // calculate average points across all slots
        int totalPoints = 0;
        for (int pts : scoreboard.getPoints().values()) {
            totalPoints += pts;
        }
        double averagePoints = totalPoints / (double) totalSlots;

        // compute how many dice are in this roll
        int currentDice = 0;
        for (int cnt : values.values()) {
            currentDice += cnt;
        }

        Map<Integer, Double> scores = new HashMap<>();

        for (Map.Entry<Integer, Integer> entry : values.entrySet()) {
            int value = entry.getKey();
            int countInThrow = entry.getValue();
            int pointsOnBoard = scoreboard.getPoints().getOrDefault(value, 0);
            int remaining = maxPoints - pointsOnBoard;
            int collectable = Math.min(countInThrow, remaining);

            if (pointsOnBoard < maxPoints) {
                Double frequency = generalFrequencies.get(value);
                if (frequency != null) {
                    double score = (1.0 / Math.pow(frequency, rarityWeight)) * collectable;
                    // boost when many points are still missing
                    double missingFactor = 1 + (remaining / (double) maxPoints);
                    score *= missingFactor;

                    double deficit = averagePoints - pointsOnBoard;
                    double urgencyFactor = deficit > 0
                            ? 1 + (deficit / maxPoints) * urgencyWeight
                            : 1;
                    score *= urgencyFactor;

                    int diceAfter = currentDice - collectable;
                    double futurePotential = 1 - Math.pow(1 - frequency, Math.max(diceAfter, 0));
                    score *= 1 + futurePotential * futureWeight;

                    scores.put(value, score);
                }
            }
        }

        return scores.isEmpty()
                ? -1
                : Collections.max(scores.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    private static double getDefaultUrgencyWeight() {
        return -0.426;
    }

    private static double getDefaultFutureWeight() {
        return 4.125;
    }

    private static double getDefaultRarityWeight() {
        return 0.919;
    }
}
