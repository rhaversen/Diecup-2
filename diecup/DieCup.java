package diecup;

import java.util.HashMap;
import java.util.Map;

public class DieCup {
    private Die[] dieList;
    private Map<Integer, Integer> valuesMap;

    public DieCup(int numberOfDice, int sides) {
        dieList = new Die[numberOfDice];
        for (int i = 0; i < numberOfDice; i++) {
            dieList[i] = new Die(sides);
        }
        generateValuesMap();
    }

    public int[] getDiceValues() {
        int[] dice = new int[dieList.length];
        for (int i = 0; i < dieList.length; i++) {
            dice[i] = dieList[i].getFaceValue();
        }
        return dice;
    }

    public int getAmountOfDice() {
        return dieList.length;
    }

    public Map<Integer, Integer> getValuesMap() {
        return valuesMap;
    }

    private void generateValuesMap() {
        Map<Integer, Integer> singleDieValuesMap = new HashMap<>();
        Map<Integer, Integer> pairDieValuesMap = new HashMap<>();

        // Add all values for single dice
        for (int i = 0; i < dieList.length; i++) {
            int faceValue = dieList[i].getFaceValue();
            singleDieValuesMap.put(faceValue, singleDieValuesMap.getOrDefault(faceValue, 0) + 1);
        }

        // Select the first initial pair of dice that make the sum
        for (int i = 0; i < dieList.length; i++) {
            // The second die should be greater than the first die
            for (int j = i + 1; j < dieList.length; j++) {

                // Create new dielist of non-selcted dice
                Die[] newDieList = new Die[dieList.length - 2];
                int index = 0;
                for (int k = 0; k < dieList.length; k++) {
                    if (k != i && k != j) {
                        newDieList[index++] = dieList[k];
                    }
                }

                if (dieList[i].getFaceValue() + dieList[j].getFaceValue() > 6) {
                    // Count the pairs for given target
                    int pairs = 1 + countSumPairs(newDieList, dieList[i].getFaceValue() + dieList[j].getFaceValue());

                    // Add it to the map if it is bigger
                    if (pairs > pairDieValuesMap.getOrDefault(dieList[i].getFaceValue() + dieList[j].getFaceValue(),
                            0)) {
                        pairDieValuesMap.put(dieList[i].getFaceValue() + dieList[j].getFaceValue(), pairs);
                    }
                }

            }
        }

        // Merge the two maps, overwriting the single die values with the pair die
        // values
        Map<Integer, Integer> localValuesMap = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : singleDieValuesMap.entrySet()) {
            localValuesMap.put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Integer, Integer> entry : pairDieValuesMap.entrySet()) {
            if (entry.getValue() > localValuesMap.getOrDefault(entry.getKey(), 0)) {
                localValuesMap.put(entry.getKey(), entry.getValue());
            }
        }

        valuesMap = localValuesMap;
    }

    private int countSumPairs(Die[] dieList, int target) {
        int maxCount = 0;

        // If there are no die pairs left, return the count immediately
        if (dieList.length <= 1) {
            return 0;
        }

        // If the target is 6 or less, return the count immediately
        if (target <= 6) {
            return 0;
        }

        for (int i = 0; i < dieList.length; i++) {
            // The second die should be greater than the first die
            for (int j = i + 1; j < dieList.length; j++) {

                // Create new dielist of non selcted dice
                Die[] newDieList = new Die[dieList.length - 2];
                int index = 0;
                for (int k = 0; k < dieList.length; k++) {
                    if (k != i && k != j) {
                        newDieList[index++] = dieList[k];
                    }
                }

                // If the sum of the two dice is equal to the target, count remaining pairs
                if (dieList[i].getFaceValue() + dieList[j].getFaceValue() == target) {
                    int remainingPairs = countSumPairs(newDieList,
                            dieList[i].getFaceValue() + dieList[j].getFaceValue());
                    maxCount = Math.max(maxCount, 1 + remainingPairs);
                }
            }
        }
        return maxCount;
    }

    public int calculateDiceToRemove(int selectedNumber, int points) {
        // The amount of dice to remove per point
        int dicePerPoint = 0;
        if (selectedNumber <= 6) {
            dicePerPoint = 1;
        } else {
            dicePerPoint = 2;
        }

        // The amount of dice to remove
        return points * dicePerPoint;
    }

}
