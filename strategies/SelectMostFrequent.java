package strategies;

import java.util.Map;

import diecup.Scoreboard;

public class SelectMostFrequent implements Strategy {
    public int getSelectedNumber(Map<Integer, Integer> valueMap, Scoreboard scoreboard) {
        int bestSum = -1;
        int maxCount = 0; // Track the highest count found in valueMap

        for (Map.Entry<Integer, Integer> entry : valueMap.entrySet()) {
            int sum = entry.getKey();
            int count = entry.getValue();
            if (scoreboard.getPoints().getOrDefault(sum, 0) < 5 && count > maxCount) {
                maxCount = count;
                bestSum = sum;
            }
        }
        return bestSum;
    }
}
