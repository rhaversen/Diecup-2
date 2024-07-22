import java.util.HashMap;
import java.util.Map;

public class DieCup {
    private Die[] dieList;

    public DieCup(int numberOfDice, int sides) {
        this.dieList = new Die[numberOfDice];
        for (int i = 0; i < numberOfDice; i++) {
            this.dieList[i] = new Die(sides);
        }
    }

    public void roll() {
        for (Die die : this.dieList) {
            die.roll();
        }
    }

    public Map<Integer, Integer> getValuesMap() {
        HashMap<Integer, Integer> valuesMap = new HashMap<>();
        int n = this.dieList.length;

        for (int i = 0; i < n; i++) {
            Die die1 = this.dieList[i];
            int faceValue1 = die1.getFaceValue();

            // Add all single die values to the map
            valuesMap.put(faceValue1, valuesMap.getOrDefault(faceValue1, 0) + 1);

            // Add all possible combinations of two dice to the map
            for (int j = i + 1; j < n; j++) {
                Die die2 = this.dieList[j];
                int faceValue2 = die2.getFaceValue();
                int sum = faceValue1 + faceValue2;
                
                valuesMap.put(sum, valuesMap.getOrDefault(sum, 0) + 1);
            }
        }

        return valuesMap;
    }
}
