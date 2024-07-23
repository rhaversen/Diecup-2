import java.util.Map;
import java.util.Set;

public class SelectHighest implements StrategyInterface {
    public int getSelectedNumber(Map<Integer, Integer> values, Scoreboard scoreboard) {
        Set<Integer> valueKeys = values.keySet();
        int highestNumber = 0;
        for (int valueKey: valueKeys) {
            boolean lessThanFivePoints = scoreboard.getPoints().get(valueKey) < 5;
            if (lessThanFivePoints) {
                highestNumber = Math.max(values.get(valueKey), highestNumber);
            }
        }
        return highestNumber;
    }
}
