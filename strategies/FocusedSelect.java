package strategies;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import diecup.Scoreboard;
import diecup.Statistics;

public class FocusedSelect implements Strategy {
    private Map<Integer, Double> generalFrequencies = new HashMap<>();

    public FocusedSelect(int numberOfDice, int sides, Statistics statistics) {
        this.generalFrequencies = statistics.getProbabilities();
    }

    public int getSelectedNumber(Map<Integer, Integer> values, Scoreboard scoreboard) {
        Map<Integer, Double> scores = new HashMap<>();
        
        boolean isEndgame = isEndgamePhase(scoreboard);

        for (Map.Entry<Integer, Integer> entry : values.entrySet()) {
            int value = entry.getKey();
            int countInThrow = entry.getValue();
            int pointsOnBoard = scoreboard.getPoints().getOrDefault(value, 0);
            int remainingPointsOnBoard = 5 - pointsOnBoard;
            
            if (pointsOnBoard >= 5) continue;

            int collectablePoints = Math.min(countInThrow, remainingPointsOnBoard);
            
            Double frequency = generalFrequencies.get(value);
            if (frequency != null) {
                double baseScore = (1.0 / frequency) * collectablePoints;
                
                if (pointsOnBoard + collectablePoints >= 5) {
                    baseScore *= 2.5;
                } else if (pointsOnBoard >= 3) {
                    baseScore *= 1.8;
                }
                
                if (isEndgame && pointsOnBoard == 0) {
                    baseScore *= 0.2;
                }
                
                scores.put(value, baseScore);
            }
        }

        return scores.isEmpty() ? -1 : Collections.max(scores.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    private boolean isEndgamePhase(Scoreboard scoreboard) {
        int completedNumbers = 0;
        for (int i = 1; i <= 12; i++) {
            if (scoreboard.getPoints(i) >= 5) {
                completedNumbers++;
            }
        }
        return completedNumbers >= 10;
    }
}
