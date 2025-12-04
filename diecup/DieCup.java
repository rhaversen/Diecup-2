package diecup;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class DieCup {
    private final int[] diceValues;  // Store values directly instead of Die objects
    private Map<Integer, Integer> valuesMap;

    public DieCup(int numberOfDice, int sides) {
        diceValues = new int[numberOfDice];
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < numberOfDice; i++) {
            diceValues[i] = rng.nextInt(sides) + 1;
        }
        generateValuesMap();
    }

    public int[] getDiceValues() {
        return diceValues;
    }

    public int getAmountOfDice() {
        return diceValues.length;
    }

    public Map<Integer, Integer> getValuesMap() {
        return valuesMap;
    }

    private void generateValuesMap() {
        valuesMap = new HashMap<>();
        int n = diceValues.length;
        
        // Count single dice values
        for (int i = 0; i < n; i++) {
            int val = diceValues[i];
            valuesMap.merge(val, 1, Integer::sum);
        }
        
        // For pairs (sums > 6), use bitmask to track used dice instead of creating arrays
        for (int target = 7; target <= 12; target++) {
            int maxPairs = countPairsForTarget(target, 0, 0);
            if (maxPairs > 0) {
                valuesMap.merge(target, maxPairs, Integer::max);
            }
        }
    }
    
    // Count max pairs that sum to target, using bitmask for used dice
    private int countPairsForTarget(int target, int usedMask, int startIdx) {
        int n = diceValues.length;
        int maxPairs = 0;
        
        for (int i = startIdx; i < n; i++) {
            if ((usedMask & (1 << i)) != 0) continue;  // Die i already used
            
            for (int j = i + 1; j < n; j++) {
                if ((usedMask & (1 << j)) != 0) continue;  // Die j already used
                
                if (diceValues[i] + diceValues[j] == target) {
                    int newMask = usedMask | (1 << i) | (1 << j);
                    int pairs = 1 + countPairsForTarget(target, newMask, i + 1);
                    maxPairs = Math.max(maxPairs, pairs);
                }
            }
        }
        return maxPairs;
    }

    public int calculateDiceToRemove(int selectedNumber, int points) {
        return selectedNumber <= 6 ? points : points * 2;
    }
}
