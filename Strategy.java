import java.util.Map;

public interface Strategy {
    public int getSelectedNumber(Map<Integer, Integer> values, Scoreboard scoreboard);
}