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
    private final double remainingValueWeight;  // Weight for expected value of remaining dice after this choice
    private final double efficiencyWeight;       // Points per dice used (singles more efficient than pairs)
    private final double commitmentRiskWeight;   // Penalty for committing to hard-to-continue numbers (11, 12)
    private final double multiCollectThreshold;  // Minimum collectable to consider rare numbers (prevents bad commits)
    private final double continuationWeight;     // Probability of being able to continue after this pick

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
                getDefaultAllDiceBonusWeight(),
                getDefaultRemainingValueWeight(),
                getDefaultEfficiencyWeight(),
                getDefaultCommitmentRiskWeight(),
                getDefaultMultiCollectThreshold(),
                getDefaultContinuationWeight());
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
            double allDiceBonusWeight,
            double remainingValueWeight,
            double efficiencyWeight,
            double commitmentRiskWeight,
            double multiCollectThreshold,
            double continuationWeight) {
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
        this.remainingValueWeight = remainingValueWeight;
        this.efficiencyWeight = efficiencyWeight;
        this.commitmentRiskWeight = commitmentRiskWeight;
        this.multiCollectThreshold = multiCollectThreshold;
        this.continuationWeight = continuationWeight;
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
        double remainingValue = computeRemainingTurnValue(value, collectable, pointsOnBoard, maxPoints, totalDiceInRoll);
        double efficiencyValue = computeEfficiency(value, collectable);
        double commitmentRiskValue = computeCommitmentRisk(value);
        double multiCollectValue = computeMultiCollectBonus(value, collectable);
        double continuationValue = computeContinuationProbability(value, totalDiceInRoll, collectable);

        return rarityWeight * rarityValue
                + progressWeight * progressValue
                + opportunityWeight * opportunityValue
                + collectionWeight * collectionValue
                + completionWeight * completionBonus
                + catchUpWeight * catchUpValue
                + diceCostWeight * diceCostValue
                + varianceWeight * varianceValue
                + gameProgressWeight * gameProgressValue
                + allDiceBonusWeight * allDiceValue
                + remainingValueWeight * remainingValue
                + efficiencyWeight * efficiencyValue
                + commitmentRiskWeight * commitmentRiskValue
                + multiCollectThreshold * multiCollectValue
                + continuationWeight * continuationValue;
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
     * Compute the expected value of remaining dice after making this selection.
     * This is the KEY insight: choosing an option affects how many dice remain,
     * and more remaining dice = more expected points this turn.
     * 
     * Free turn triggers (completion or using all dice) reset to max dice.
     */
    private double computeRemainingTurnValue(int value, int collectable, int pointsOnBoard, 
                                              int maxPoints, int totalDiceInRoll) {
        int diceUsed = statistics.isPairNumber(value) ? collectable * 2 : collectable;
        boolean completesSlot = (pointsOnBoard + collectable >= maxPoints);
        boolean usesAllDice = (diceUsed >= totalDiceInRoll);
        
        int remainingDice;
        if (completesSlot || usesAllDice) {
            // Free turn - get all dice back
            remainingDice = statistics.getMaxDice();
        } else {
            remainingDice = totalDiceInRoll - diceUsed;
        }
        
        // Return expected points from remaining dice (pre-computed via simulation)
        return statistics.getExpectedTurnValue(remainingDice);
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

    /**
     * Efficiency: points per dice used.
     * Singles use 1 die per point, pairs use 2 dice per point.
     * Higher = more efficient use of dice.
     */
    private double computeEfficiency(int value, int collectable) {
        int diceUsed = statistics.isPairNumber(value) ? collectable * 2 : collectable;
        if (diceUsed == 0) return 0;
        return (double) collectable / diceUsed;  // 1.0 for singles, 0.5 for pairs
    }
    
    /**
     * Commitment risk: penalty for committing to numbers that are hard to continue.
     * 11 and 12 have very low probability of appearing again (41.8% and 26.3%).
     * Committing to them often means wasting a turn.
     * Returns negative value for risky numbers (so positive weight = avoid risk).
     */
    private double computeCommitmentRisk(int value) {
        // Expected points per turn if committed to this number (from theoretical analysis)
        // Lower expected = higher risk
        double[] expectedPointsPerTurn = {
            1.75, 1.75, 1.75, 1.75, 1.75, 1.75,  // Singles 1-6: ~1.75
            1.82, 1.58, 1.21, 0.94, 0.56, 0.31   // Pairs 7-12: decreasing
        };
        int idx = value - 1;
        if (idx < 0 || idx >= expectedPointsPerTurn.length) return 0;
        
        // Normalize: return negative for low expected (risky), positive for high expected (safe)
        // Baseline around 1.5, so 11 (0.56) and 12 (0.31) get strong negative
        return expectedPointsPerTurn[idx] - 1.5;
    }
    
    /**
     * Multi-collect bonus: reward for having multiple collectables of rare numbers.
     * The insight: only pick 11 or 12 when you have 2+ pairs available.
     * Returns bonus when collectable >= 2 for rare numbers.
     */
    private double computeMultiCollectBonus(int value, int collectable) {
        // Only applies to rare pair numbers (10, 11, 12)
        if (value < 10) return 0;
        
        // Bonus scales with how many we can collect
        // If collectable >= 2, it's a good opportunity for rare numbers
        if (collectable >= 2) {
            return collectable - 1;  // Bonus of 1 for 2, 2 for 3, etc.
        } else {
            // Penalty for picking rare number with only 1 collectable
            // This discourages bad commits to 11 or 12 with just 1 pair
            return -1.0;
        }
    }
    
    /**
     * Continuation probability: likelihood of being able to continue after this pick.
     * Based on remaining dice and the probability of rolling the target again.
     */
    private double computeContinuationProbability(int value, int totalDice, int collectable) {
        int diceUsed = statistics.isPairNumber(value) ? collectable * 2 : collectable;
        int remainingDice = totalDice - diceUsed;
        
        if (remainingDice <= 0) {
            // All dice used = free turn, probability is 1.0 (sort of)
            return 1.0;
        }
        
        // Probability of getting at least one match with remaining dice
        Double freq = statistics.getGeneralFrequencies().get(value);
        if (freq == null || freq == 0) return 0;
        
        // For singles: P(at least one) = 1 - (5/6)^remainingDice
        // For pairs: more complex, approximate using frequency
        if (!statistics.isPairNumber(value)) {
            return 1 - Math.pow(5.0/6.0, remainingDice);
        } else {
            // Approximate pair continuation probability
            // freq is already the probability per 6-dice roll, scale by remaining
            double scaledFreq = freq * remainingDice / 6.0;
            return Math.min(1.0, scaledFreq);
        }
    }

    public static double getDefaultOpportunityWeight() {
        return 4.691;
    }

    public static double getDefaultRarityWeight() {
        return 0.373;
    }

    public static double getDefaultProgressWeight() {
        return 0.871;
    }

    public static double getDefaultRarityScalar() {
        return 2.943;
    }

    public static double getDefaultCollectionWeight() {
        return 0.773;
    }

    public static double getDefaultCollectionScalar() {
        return 0.423;
    }

    public static double getDefaultCompletionWeight() {
        return 1.903;
    }

    public static double getDefaultCatchUpWeight() {
        return 5.418;
    }
    
    public static double getDefaultDiceCostWeight() {
        return 0.950;
    }
    
    public static double getDefaultVarianceWeight() {
        return 1.520;
    }
    
    public static double getDefaultGameProgressWeight() {
        return 2.469;
    }
    
    public static double getDefaultAllDiceBonusWeight() {
        return 8.166;
    }
    
    public static double getDefaultRemainingValueWeight() {
        return 2.273;
    }

    public static double getDefaultEfficiencyWeight() {
        return 0.962;
    }
    
    public static double getDefaultCommitmentRiskWeight() {
        return 0.590;
    }
    
    public static double getDefaultMultiCollectThreshold() {
        return 0.534;
    }
    
    public static double getDefaultContinuationWeight() {
        return 2.513;
    }
}
