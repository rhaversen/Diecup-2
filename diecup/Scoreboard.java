package diecup;

import java.util.HashMap;
import java.util.Map;

public class Scoreboard {
    private final int[] points;  // Use array instead of HashMap for speed
    private final int maxNumber;
    private int incompleteCount;  // Track how many numbers are incomplete

    public Scoreboard(int maxNumber) {
        this.maxNumber = maxNumber;
        this.points = new int[maxNumber + 1];  // Index 0 unused, 1-12 used
        this.incompleteCount = maxNumber;
        // Array is already zero-initialized
    }

    public Scoreboard() {
        this(12);
    }

    public boolean isComplete() {
        return incompleteCount == 0;
    }

    public void setPoints(int number, int points) {
        boolean wasComplete = this.points[number] >= 5;
        this.points[number] = points;
        boolean isComplete = points >= 5;
        
        if (wasComplete && !isComplete) incompleteCount++;
        else if (!wasComplete && isComplete) incompleteCount--;
    }

    public void addPoints(int number, int pointsToAdd) {
        int oldPoints = this.points[number];
        int newPoints = Math.min(oldPoints + pointsToAdd, 5);
        this.points[number] = newPoints;
        
        if (oldPoints < 5 && newPoints >= 5) {
            incompleteCount--;
        }
    }

    public int getPoints(int number) {
        return points[number];
    }

    public Map<Integer, Integer> getPoints() {
        // Return as Map for compatibility with existing code
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 1; i <= maxNumber; i++) {
            map.put(i, points[i]);
        }
        return map;
    }

    public void reset() {
        for (int i = 1; i <= maxNumber; i++) {
            points[i] = 0;
        }
        incompleteCount = maxNumber;
    }

    public Scoreboard copy() {
        Scoreboard newBoard = new Scoreboard(maxNumber);
        System.arraycopy(this.points, 0, newBoard.points, 0, points.length);
        newBoard.incompleteCount = this.incompleteCount;
        return newBoard;
    }
}
