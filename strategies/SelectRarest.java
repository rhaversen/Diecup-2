package strategies;

import java.util.HashMap;
import java.util.Map;

import diecup.Scoreboard;
import diecup.Statistics;

public class SelectRarest implements Strategy {
    private Map<Integer, Double> generalFrequencies = new HashMap<>();

    public SelectRarest(Statistics statistics) {
        this.generalFrequencies = statistics.getGeneralFrequencies();
    }

    public int getSelectedNumber(Map<Integer, Integer> valueMap, Scoreboard scoreboard) {
        int rarest = -1;

        for (Map.Entry<Integer, Integer> entry : valueMap.entrySet()) {
            int sum = entry.getKey();
            if (scoreboard.getPoints().getOrDefault(sum, 0) < 5) {
                Double frequency = generalFrequencies.get(sum);
                if (frequency != null) {
                    if (rarest == -1 || frequency < generalFrequencies.get(rarest)) {
                        rarest = sum;
                    }
                }
            }
        }
        return rarest;
    }
}
