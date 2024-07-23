import java.util.HashMap;
import java.util.Map;

public class DieCup {
    private Die[] dieList;

    public DieCup(int numberOfDice, int sides) {
        dieList = new Die[numberOfDice];
        for (int i = 0; i < numberOfDice; i++) {
            dieList[i] = new Die(sides);
        }
    }

    public void roll() {
        for (Die die : dieList) {
            die.roll();
        }
    }

    public Map<Integer, Integer> getValuesMap() {
        HashMap<Integer, Integer> valuesMap = new HashMap<>();
        int n = dieList.length;

        for (int i = 0; i < n; i++) {
            Die die1 = dieList[i];
            int faceValue1 = die1.getFaceValue();

            // Add all single die values to the map
            valuesMap.put(faceValue1, valuesMap.getOrDefault(faceValue1, 0) + 1);

            // Add all possible combinations of two dice to the map
            // Only add with dice after i'th to avoid double counting
            for (int j = i + 1; j < n; j++) {
                Die die2 = dieList[j];
                int faceValue2 = die2.getFaceValue();
                int sum = faceValue1 + faceValue2;
                
                valuesMap.put(sum, valuesMap.getOrDefault(sum, 0) + 1);
            }
        }

        return valuesMap;
    }

    public void removeDice(int amount) {
        if (amount > dieList.length) return;
        if (amount == dieList.length) dieList = new Die[0];

        for (int i = dieList.length - amount; i < dieList.length; i++) {
            dieList[i] = null;
        }
    }
}
