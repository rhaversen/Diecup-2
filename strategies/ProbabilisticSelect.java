package strategies;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import diecup.Scoreboard;
import diecup.Statistics;

public class ProbabilisticSelect implements Strategy {
    private Map<Integer, Double> generalFrequencies = new HashMap<>();
    private double cutOff;
    private double wasteMultiplier;

    public ProbabilisticSelect(int numberOfDice, int sides, double cutOff, Statistics statistics) {
        this(numberOfDice, sides, cutOff, 0.95, 0.2, statistics);
    }

    public ProbabilisticSelect(int numberOfDice, int sides, double cutOff, double endgameThreshold, double wasteMultiplier, Statistics statistics) {
        this.generalFrequencies = statistics.getProbabilities();
        this.cutOff = cutOff;
        this.wasteMultiplier = wasteMultiplier;
    }

    public int getSelectedNumber(Map<Integer, Integer> values, Scoreboard scoreboard) {
        Map<Integer, Double> scores = new HashMap<>();
        
        for (Map.Entry<Integer, Integer> entry : values.entrySet()) {
            int value = entry.getKey();
            int countInThrow = entry.getValue();
            int pointsOnBoard = scoreboard.getPoints().getOrDefault(value, 0);
            int remainingPointsOnBoard = 5 - pointsOnBoard;

            if (pointsOnBoard >= 5) continue;

            int collectablePoints = Math.min(countInThrow, remainingPointsOnBoard);
            
            Double frequency = generalFrequencies.get(value);
            
            if (frequency != null) {
                double immediateValue = calculateImmediateValue(collectablePoints, countInThrow, wasteMultiplier);
                double rarityBonus = 1.0 / frequency;
                double completionBonus = calculateCompletionBonus(pointsOnBoard, collectablePoints);
                
                double finalScore = rarityBonus * immediateValue * completionBonus;
                
                if (finalScore >= this.cutOff) {
                    scores.put(value, finalScore);
                }
            }
        }

        if (scores.isEmpty()) {
            for (Map.Entry<Integer, Integer> entry : values.entrySet()) {
                int value = entry.getKey();
                int pointsOnBoard = scoreboard.getPoints().getOrDefault(value, 0);
                if (pointsOnBoard < 5) {
                    scores.put(value, (double) value);
                }
            }
        }

        int result = scores.isEmpty() ? -1 : Collections.max(scores.entrySet(), Map.Entry.comparingByValue()).getKey();
        return result;
    }
    
    private double calculateImmediateValue(int collectablePoints, int countInThrow, double wastePenalty) {
        int wastedDice = countInThrow - collectablePoints;
        double wasteMultiplier = wastedDice > 0 ? (1.0 - wastePenalty * wastedDice) : 1.0;
        return collectablePoints * wasteMultiplier;
    }
    
    private double calculateCompletionBonus(int pointsOnBoard, int collectablePoints) {
        int pointsAfterCollection = pointsOnBoard + collectablePoints;
        
        if (pointsAfterCollection >= 5) {
            return 3.0;
        } else if (pointsAfterCollection >= 4) {
            return 2.0;
        } else if (pointsAfterCollection >= 3) {
            return 1.5;
        }
        
        return 1.0;
    }
}
