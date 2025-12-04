package strategies;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import diecup.Scoreboard;
import diecup.Statistics;

public class ImprovedWeightedSelect implements Strategy {
    private final Statistics statistics;
    private final double opportunityWeight;
    private final double rarityWeight;
    private final double progressWeight;
    private final double rarityScalar;
    private final double collectionWeight;
    private final double collectionScalar;
    private final double completionWeight;      // Bonus for completing a slot (triggers free turn)
    private final double catchUpWeight;
    private final double diceCostWeight;        // Weight for dice cost (pair numbers cost 2 dice, singles cost 1)
    private final double varianceWeight;        // Weight for preferring low/high variance numbers
    private final double gameProgressWeight;    // Adjusts strategy based on how many slots are complete
    private final double allDiceBonusWeight;    // Weight for using all remaining dice this roll

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
                getDefaultCatchUpWeight(),
                getDefaultDiceCostWeight(),
                getDefaultVarianceWeight(),
                getDefaultGameProgressWeight(),
                getDefaultAllDiceBonusWeight());
    }

    public ImprovedWeightedSelect(Statistics statistics,
            double opportunityWeight,
            double rarityWeight,
            double progressWeight,
            double rarityScalar,
            double collectionWeight,
            double collectionScalar,
            double completionWeight,
            double catchUpWeight,
            double diceCostWeight,
            double varianceWeight,
            double gameProgressWeight,
            double allDiceBonusWeight) {
        this.statistics = statistics;
        this.opportunityWeight = opportunityWeight;
        this.rarityWeight = rarityWeight;
        this.progressWeight = progressWeight;
        this.rarityScalar = rarityScalar;
        this.collectionWeight = collectionWeight;
        this.collectionScalar = collectionScalar;
        this.completionWeight = completionWeight;
        this.catchUpWeight = catchUpWeight;
        this.diceCostWeight = diceCostWeight;
        this.varianceWeight = varianceWeight;
        this.gameProgressWeight = gameProgressWeight;
        this.allDiceBonusWeight = allDiceBonusWeight;
    }

    public int getSelectedNumber(Map<Integer, Integer> values, Scoreboard scoreboard) {
        int maxPoints = 5; // 5 is default max points pr slot (Game rule)
        Map<Integer, Double> scores = new HashMap<>();
        
        // Calculate game progress (0.0 = start, 1.0 = almost done)
        double gameProgress = calculateGameProgress(scoreboard, maxPoints);
        
        // Calculate total dice in this roll (for all-dice bonus calculation)
        int totalDiceInRoll = calculateTotalDice(values);

        for (Map.Entry<Integer, Integer> entry : values.entrySet()) {
            int value = entry.getKey();
            int countInThrow = entry.getValue();
            int pointsOnBoard = scoreboard.getPoints().getOrDefault(value, 0);

            if (pointsOnBoard < maxPoints) {
                int collectable = Math.min(countInThrow, maxPoints - pointsOnBoard);
                if (collectable > 0) {
                    scores.put(value, calculateScore(value, collectable, pointsOnBoard, maxPoints, 
                                                     gameProgress, totalDiceInRoll));
                }
            }
        }

        return scores.isEmpty()
                ? -1
                : Collections.max(scores.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    private double calculateScore(int value, int collectable, int pointsOnBoard, int maxPoints,
                                   double gameProgress, int totalDiceInRoll) {
        Double frequency = statistics.getGeneralFrequencies().get(value);
        if (frequency == null || frequency == 0) {
            return 0;
        }

        double rarityValue = computeRarityValue(frequency, collectable);
        double progressValue = computeProgressValue(collectable, pointsOnBoard, maxPoints);
        double opportunityValue = computeOpportunityValue(frequency, collectable, maxPoints);
        double collectionValue = computeCollectionValue(collectable);
        double completionBonus = computeCompletionBonus(pointsOnBoard, collectable, maxPoints);
        double catchUpValue = computeCatchUpValue(pointsOnBoard, maxPoints);
        double diceCostValue = computeDiceCost(value, collectable);
        double varianceValue = computeVarianceValue(value);
        double gameProgressValue = computeGameProgressAdjustment(gameProgress, pointsOnBoard, maxPoints);
        double allDiceValue = computeAllDiceBonus(value, collectable, totalDiceInRoll);

        return rarityWeight * rarityValue
                + progressWeight * progressValue
                + opportunityWeight * opportunityValue
                + collectionWeight * collectionValue
                + completionWeight * completionBonus
                + catchUpWeight * catchUpValue
                + diceCostWeight * diceCostValue
                + varianceWeight * varianceValue
                + gameProgressWeight * gameProgressValue
                + allDiceBonusWeight * allDiceValue;
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

    /**
     * Dice cost: how many dice are consumed for this selection.
     * Negative value = more dice used (pair numbers use 2 per point, singles use 1).
     * Positive weight means prefer efficient (fewer dice), negative means prefer using more dice.
     */
    private double computeDiceCost(int value, int collectable) {
        int diceUsed = statistics.isPairNumber(value) ? collectable * 2 : collectable;
        // Return negative of dice used so positive weight = prefer fewer dice
        return -diceUsed;
    }
    
    /**
     * Variance value: positive means prefer high variance (risky), negative means prefer low variance (safe).
     * Based on statistics about how variable the point collection is for each number.
     */
    private double computeVarianceValue(int value) {
        Map<Integer, Double> variances = statistics.getVarianceByNumber();
        Double variance = variances.get(value);
        if (variance == null) return 0.0;
        
        // Normalize variance (higher variance -> higher absolute value)
        // The sign of varianceWeight determines if we prefer or avoid variance
        return variance;
    }
    
    /**
     * Game progress adjustment: may want different strategies early vs late game.
     * Returns higher value for low-progress slots when game is near end.
     */
    private double computeGameProgressAdjustment(double gameProgress, int pointsOnBoard, int maxPoints) {
        // Late game: prioritize slots that are further behind
        double slotProgress = (double) pointsOnBoard / maxPoints;
        return gameProgress * (1.0 - slotProgress);
    }
    
    /**
     * Bonus if selecting this number would use all dice in the roll (triggers free turn).
     */
    private double computeAllDiceBonus(int value, int collectable, int totalDiceInRoll) {
        int diceUsed = statistics.isPairNumber(value) ? collectable * 2 : collectable;
        if (diceUsed >= totalDiceInRoll) {
            return 1.0;
        }
        return 0.0;
    }
    
    /**
     * Calculate overall game progress (0.0 = start, 1.0 = near completion).
     */
    private double calculateGameProgress(Scoreboard scoreboard, int maxPoints) {
        Map<Integer, Integer> points = scoreboard.getPoints();
        int totalPoints = 0;
        int maxTotalPoints = points.size() * maxPoints;
        
        for (int p : points.values()) {
            totalPoints += p;
        }
        
        return (double) totalPoints / maxTotalPoints;
    }
    
    /**
     * Calculate total dice in the current roll.
     */
    private int calculateTotalDice(Map<Integer, Integer> values) {
        int total = 0;
        for (Map.Entry<Integer, Integer> entry : values.entrySet()) {
            int value = entry.getKey();
            int count = entry.getValue();
            // For single dice (1-6), count is the number of dice
            // For pairs (7-12), count is pairs, so multiply by 2
            if (value <= statistics.getSides()) {
                total += count;
            }
            // Don't double-count dice used in pairs - they're the same dice
        }
        return total;
    }

    public static double getDefaultOpportunityWeight() {
        return 0.661;
    }

    public static double getDefaultRarityWeight() {
        return 0.468;
    }

    public static double getDefaultProgressWeight() {
        return 0.376;
    }

    public static double getDefaultRarityScalar() {
        return 0.164;
    }

    public static double getDefaultCollectionWeight() {
        return 0.536;
    }

    public static double getDefaultCollectionScalar() {
        return 0.148;
    }

    public static double getDefaultCompletionWeight() {
        return 0.707;
    }

    public static double getDefaultCatchUpWeight() {
        return 0.676;
    }
    
    public static double getDefaultDiceCostWeight() {
        return -0.15;
    }
    
    public static double getDefaultVarianceWeight() {
        return 0.178;
    }
    
    public static double getDefaultGameProgressWeight() {
        return 0.761;
    }
    
    public static double getDefaultAllDiceBonusWeight() {
        return 2.707;
    }
}
