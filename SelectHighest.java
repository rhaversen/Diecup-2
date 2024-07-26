import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SelectHighest implements StrategyInterface {
    public int getSelectedNumber(Map<Integer, Integer> values, Scoreboard scoreboard) {
        Set<Integer> scoreboardKeys = scoreboard.getPoints().keySet();
        for (int scoreboardKey : scoreboardKeys) {
            boolean lessThanFivePoints = scoreboard.getPoints().get(scoreboardKey) < 5;
            if (!lessThanFivePoints) {
                values.remove(scoreboardKey);
            }
        }
        int highestNumber = highestCommonNumber(values.keySet(), scoreboardKeys);
        System.out.println("Highest number: " + highestNumber);
        return highestNumber;
    }

    public static int highestCommonNumber(Set<Integer> set1, Set<Integer> set2) {
        Set<Integer> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        if (intersection.isEmpty()) {
            return -1; // No common number
        }
        return Collections.max(intersection);
    }

}
