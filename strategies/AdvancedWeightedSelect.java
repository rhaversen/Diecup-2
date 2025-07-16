package strategies;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import diecup.Scoreboard;
import diecup.Statistics;

public class AdvancedWeightedSelect implements Strategy {
    private Statistics statistics;
    private final double wastePenalty;
    private final double continuationWeight;
    private final double rarityWeight;
    private final double synergyWeight;
    private final double clusteringWeight;
    private final double endgameWeight;
    private final double blockingWeight;
    private final double decayWeight;
    private final double turnLengthWeight;
    private final double decayFactor;
    private final double[] turnLengthThresholds;
    private final double[] turnLengthPenalties;
    private final int[] riskThresholds;
    private final double[] riskFactors;
    private int currentTurnDepth = 0;

    public AdvancedWeightedSelect(int numberOfDice, int sides, Statistics statistics) {
        this(statistics, getDefaultWastePenalty(), getDefaultContinuationWeight(), getDefaultRarityWeight(),
                getDefaultSynergyWeight(), getDefaultClusteringWeight(), getDefaultEndgameWeight(),
                getDefaultBlockingWeight(), getDefaultDecayWeight(), getDefaultTurnLengthWeight(),
                getDefaultDecayFactor(), getDefaultTurnLengthThresholds(), getDefaultTurnLengthPenalties(),
                getDefaultRiskThresholds(), getDefaultRiskFactors());
    }

    public AdvancedWeightedSelect(Statistics statistics, double wastePenalty, double continuationWeight,
            double rarityWeight, double synergyWeight, double clusteringWeight,
            double endgameWeight, double blockingWeight, double decayWeight,
            double turnLengthWeight, double decayFactor, double[] turnLengthThresholds,
            double[] turnLengthPenalties, int[] riskThresholds, double[] riskFactors) {
        this.statistics = statistics;
        this.wastePenalty = wastePenalty;
        this.continuationWeight = continuationWeight;
        this.rarityWeight = rarityWeight;
        this.synergyWeight = synergyWeight;
        this.clusteringWeight = clusteringWeight;
        this.endgameWeight = endgameWeight;
        this.blockingWeight = blockingWeight;
        this.decayWeight = decayWeight;
        this.turnLengthWeight = turnLengthWeight;
        this.decayFactor = decayFactor;
        this.turnLengthThresholds = turnLengthThresholds;
        this.turnLengthPenalties = turnLengthPenalties;
        this.riskThresholds = riskThresholds;
        this.riskFactors = riskFactors;
    }

    private static double getDefaultWastePenalty() {
        return 0.349;
    }

    private static double getDefaultContinuationWeight() {
        return 0.239;
    }

    private static double getDefaultRarityWeight() {
        return 1.486;
    }

    private static double getDefaultSynergyWeight() {
        return -0.070;
    }

    private static double getDefaultClusteringWeight() {
        return 1.595;
    }

    private static double getDefaultEndgameWeight() {
        return 0.851;
    }

    private static double getDefaultBlockingWeight() {
        return -0.003;
    }

    private static double getDefaultDecayWeight() {
        return 0.651;
    }

    private static double getDefaultTurnLengthWeight() {
        return -1.506;
    }

    private static double getDefaultDecayFactor() {
        return 1.063;
    }

    private static double[] getDefaultTurnLengthThresholds() {
        return new double[] { -0.847, 1.248, 2.612 };
    }

    private static double[] getDefaultTurnLengthPenalties() {
        return new double[] { -1.720, -1.099, 2.228 };
    }

    private static int[] getDefaultRiskThresholds() {
        return new int[] { 3, 3, 6 };
    }

    private static double[] getDefaultRiskFactors() {
        return new double[] { -1.844, -0.980, 0.742 };
    }

    public int getSelectedNumber(Map<Integer, Integer> values, Scoreboard scoreboard) {
        Map<Integer, Double> scores = new HashMap<>();

        for (Map.Entry<Integer, Integer> entry : values.entrySet()) {
            int value = entry.getKey();
            int countInThrow = entry.getValue();
            int pointsOnBoard = scoreboard.getPoints().getOrDefault(value, 0);
            int remainingPointsOnBoard = 5 - pointsOnBoard;

            if (pointsOnBoard >= 5) {
                continue;
            }

            int collectablePoints = Math.min(countInThrow, remainingPointsOnBoard);
            int wastedPoints = countInThrow - collectablePoints;

            double rarityWeightValue = statistics.getRarityWeight(value);
            double wasteValue = wastedPoints * wastePenalty;
            double baseValue = collectablePoints * rarityWeightValue * rarityWeight;

            int estimatedRemainingDice = estimateRemainingDice(values, value, countInThrow);
            double continuationProb = statistics.getContinuationProbability(estimatedRemainingDice, value);
            double continuationValue = continuationProb * continuationWeight;

            double synergyValue = calculateSynergyBonus(values, value, countInThrow);
            double clusteringValue = statistics.getCompletionClusteringValue(scoreboard, value) * clusteringWeight;
            double endgameValue = statistics.getEndgameProgressionWeight(scoreboard) * endgameWeight;
            double blockingCost = statistics.calculateBlockingCost(values, scoreboard, value) * blockingWeight;

            double decayValue = statistics.calculateExpectedValueDecay(estimatedRemainingDice, value, currentTurnDepth,
                    decayFactor, riskThresholds, riskFactors) * decayWeight;
            double turnLengthPenalty = statistics.calculateTurnLengthPenalty(currentTurnDepth + 1, turnLengthThresholds,
                    turnLengthPenalties) * turnLengthWeight;

            double totalScore = (baseValue + continuationValue + synergyValue + decayValue) * clusteringValue
                    * endgameValue * turnLengthPenalty - wasteValue - blockingCost;
            scores.put(value, totalScore);
        }

        currentTurnDepth++;
        return scores.isEmpty() ? -1 : Collections.max(scores.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    public void resetTurnDepth() {
        currentTurnDepth = 0;
    }

    private int estimateRemainingDice(Map<Integer, Integer> values, int selectedValue, int selectedCount) {
        int totalDice = values.values().stream().mapToInt(Integer::intValue).sum();

        if (selectedValue <= 6) {
            return totalDice - selectedCount;
        } else {
            return totalDice - (selectedCount * 2);
        }
    }

    private double calculateSynergyBonus(Map<Integer, Integer> values, int selectedValue, int selectedCount) {
        double synergyBonus = 0.0;

        int estimatedRemainingDice = estimateRemainingDice(values, selectedValue, selectedCount);

        for (Map.Entry<Integer, Integer> entry : values.entrySet()) {
            int diceValue = entry.getKey();
            int diceCount = entry.getValue();

            if (selectedValue <= 6 && diceValue == selectedValue) {
                continue;
            }

            if (selectedValue > 6) {
                boolean usedForSelection = false;
                for (int otherDie = 1; otherDie <= 6; otherDie++) {
                    if (diceValue + otherDie == selectedValue && values.containsKey(otherDie)) {
                        usedForSelection = true;
                        break;
                    }
                }
                if (usedForSelection) {
                    continue;
                }
            }

            double diceSynergy = statistics.getSynergyWeight(diceValue);
            synergyBonus += diceSynergy * diceCount * synergyWeight;
        }

        return synergyBonus * (estimatedRemainingDice / 6.0);
    }
}
