package diecup;

import java.util.HashMap;
import java.util.Map;

public class Scoreboard {
    private Map<Integer, Integer> points;

    public Scoreboard(int maxNumber) {
        // Initialize the scoreboard with all numbers from 1 to 12
        // and set the value to 0
        this.points = new HashMap<>();
        for (int i = 1; i <= maxNumber; i++) {
            this.points.put(i, 0);
        }
    }

    public Scoreboard() {
        this(12);
    }

    public boolean isComplete() {
        for (int value : this.points.values()) {
            if (value < 5) {
                return false;
            }
        }
        return true;
    }

    public void setPoints(int number, int points) {
        this.points.put(number, points);
    }

    public void addPoints(int number, int points) {
        this.points.put(number, Math.min(this.points.get(number) + points, 5));
    }

    public int getPoints(int number) {
        return points.getOrDefault(number, 0);
    }

    public Map<Integer, Integer> getPoints() {
        return points;
    }

    public void reset() {
        for (int key : this.points.keySet()) {
            this.points.put(key, 0);
        }
    }

    public Scoreboard copy() {
        Scoreboard newBoard = new Scoreboard();
        for (Map.Entry<Integer, Integer> entry : this.points.entrySet()) {
            newBoard.setPoints(entry.getKey(), entry.getValue());
        }
        return newBoard;
    }
}
