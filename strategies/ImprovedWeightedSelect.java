package strategies;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import diecup.Scoreboard;
import diecup.Statistics;

public class ImprovedWeightedSelect implements Strategy {
    private Map<Integer, Double> generalFrequencies = new HashMap<>();
    private final double opportunityWeight;
    private final double rarityWeight;
    private final double progressWeight;
    private final double rarityScalar;
    private final double collectionWeight;
    private final double collectionScalar;
    private final double completionWeight;
    private final double catchUpWeight;

    // default constructor using tuned defaults
    public ImprovedWeightedSelect(Statistics statistics) {
        this(statistics,
                getDefaultOpportunityWeight(),
                getDefaultRarityWeight(),
                getDefaultProgressWeight(),
                getDefaultRarityScalar(),
                getDefaultCollectionWeight(),
                getDefaultCollectionScalar(),
                getDefaultCompletionWeight(),
                getDefaultCatchUpWeight());
    }

    public ImprovedWeightedSelect(Statistics statistics,
            double opportunityWeight,
            double rarityWeight,
            double progressWeight,
            double rarityScalar,
            double collectionWeight,
            double collectionScalar,
            double completionWeight,
            double catchUpWeight) {
        this.generalFrequencies = statistics.getGeneralFrequencies();
        this.opportunityWeight = opportunityWeight;
        this.rarityWeight = rarityWeight;
        this.progressWeight = progressWeight;
        this.rarityScalar = rarityScalar;
        this.collectionWeight = collectionWeight;
        this.collectionScalar = collectionScalar;
        this.completionWeight = completionWeight;
        this.catchUpWeight = catchUpWeight;
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
        double opportunityValue = computeOpportunityValue(frequency, collectable, maxPoints);
        double collectionValue = computeCollectionValue(collectable);
        double completionBonus = computeCompletionBonus(pointsOnBoard, collectable, maxPoints);
        double catchUpValue = computeCatchUpValue(pointsOnBoard, maxPoints);

        return rarityWeight * rarityValue
                + progressWeight * progressValue
                + opportunityWeight * opportunityValue
                + collectionWeight * collectionValue
                + completionWeight * completionBonus
                + catchUpWeight * catchUpValue;
    }

    private double computeRarityValue(double frequency, int collectable) {
        return collectable / Math.pow(frequency, 1 - rarityScalar);
    }

    private double computeProgressValue(int collectable, int pointsOnBoard, int maxPoints) {
        double progressBefore = (double) pointsOnBoard / maxPoints;
        double progressAfter = (double) Math.min(pointsOnBoard + collectable, maxPoints) / maxPoints;
        return progressAfter - progressBefore;
    }

    private double computeOpportunityValue(double frequency, int collectable, int maxPoints) {
        double expectedPointsPerTurn = frequency * maxPoints;
        return collectable - expectedPointsPerTurn;
    }

    private double computeCollectionValue(int collectable) {
        return Math.pow(collectable, collectionScalar);
    }

    private double computeCompletionBonus(int pointsOnBoard, int collectable, int maxPoints) {
        return (pointsOnBoard + collectable == maxPoints) ? 1.0 : 0.0;
    }

    private double computeCatchUpValue(int pointsOnBoard, int maxPoints) {
        return 1.0 - ((double) pointsOnBoard / maxPoints);
    }

    private static double getDefaultOpportunityWeight() {
        return 0.361;
    }

    private static double getDefaultRarityWeight() {
        return 0.724;
    }

    private static double getDefaultProgressWeight() {
        return 1.000;
    }

    private static double getDefaultRarityScalar() {
        return 0.017;
    }

    private static double getDefaultCollectionWeight() {
        return 0.798;
    }

    private static double getDefaultCollectionScalar() {
        return 0.000;
    }

    private static double getDefaultCompletionWeight() {
        return 0.302;
    }

    private static double getDefaultCatchUpWeight() {
        return 0.939;
    }
}
