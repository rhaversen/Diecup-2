// Game Rules:
// 1. The player has a scoreboard with numbers 1 to 12. To win, each number must be rolled 5 times.
// 2. The player rolls 5 dice. If the sum of any two dice matches a number on the scoreboard, the player can select those two dice and add 1 point to the corresponding number on the scoreboard.
// - For numbers 1 to 6, only a single die can be selected if it matches the number directly.
// 3. Remove the selected dice from play and roll the remaining dice.
// 4. If the selected number is rolled again, remove those dice from play and add another point to the corresponding number on the scoreboard.
// 5. Repeat the process until all dice are removed or no more dice can be removed. The player then ends their turn.
// 6. If the player manages to get a number on the scoreboard to 5 points, all die go back into the cup and the player can start again with 5 dice and a different number on the scoreboard.
// 7. if the player gets the same 7-12 number three times in a row, all die go back into the cup and the player can start again with 5 dice and a different or same number on the scoreboard.
// 7. The players final score will be how many turns it took to get all numbers 5 times.

import java.util.Arrays;

public class Game {
    private Strategy strategy;
    private Scoreboard scoreboard;
    private int turns;
    private int defaultNumberOfDice;
    private int defaultSides;

    public Game(int numberOfDice, int sides, Strategy strategy) {
        scoreboard = new Scoreboard();
        turns = 0;
        this.strategy = strategy;
        defaultNumberOfDice = numberOfDice;
        defaultSides = sides;
    }

    public void startGame() {
        turns = 0;
        while (!scoreboard.isFull()) {
            turns++;
            playTurn();
        }
    }

    public void playTurn() {
        DieCup dieCup = new DieCup(defaultNumberOfDice, defaultSides);

        int selectedNumber = strategy.getSelectedNumber(dieCup.getValuesMap(), scoreboard);

        collectPoints(dieCup, selectedNumber);
    }

    public void collectPoints(DieCup dieCup, int selectedNumber) {
        // Keep removing dice until no more dice can be removed, adding points to the
        // scoreboard meanwhile.
        // If all dice can be collected, or the scoreboard reaches 5 for the given
        // number, play another turn.
        if (selectedNumber == -1) {
            return;
        }

        boolean canMakeNumber = dieCup.getValuesMap().containsKey(selectedNumber);

        if (!canMakeNumber) {
            return;
        }

        int points = dieCup.getValuesMap().getOrDefault(selectedNumber, 0);
        scoreboard.addPoints(selectedNumber, points);

        int amountOfDiceToRemove = calculateDiceToRemove(selectedNumber, points);
        int diceRemainingAfterCollection = dieCup.getAmountOfDice() - amountOfDiceToRemove;

        boolean allDiceCollected = diceRemainingAfterCollection <= 0;
        boolean fullPointsReached = scoreboard.getPoints(selectedNumber) >= 5;

        if (allDiceCollected || fullPointsReached) {
            // Start a new turn without incrementing turns as this is a free turn
            playTurn();
        } else {
            // Recursively collect points until no more dice can be removed
            DieCup newDieCup = new DieCup(diceRemainingAfterCollection, defaultSides);
            collectPoints(newDieCup, selectedNumber);
        }
    }

    public int getTurns() {
        return this.turns;
    }

    private int calculateDiceToRemove(int selectedNumber, int points) {
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