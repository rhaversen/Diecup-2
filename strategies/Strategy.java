package strategies;

import java.util.Map;

import diecup.Scoreboard;

public interface Strategy {
    public int getSelectedNumber(Map<Integer, Integer> values, Scoreboard scoreboard);
}