package diecup;
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

import strategies.Strategy;
import java.util.Arrays;
import java.util.Scanner;

public class Game {
    private Strategy strategy;
    private Scoreboard scoreboard;
    private int turns;
    private int defaultNumberOfDice;
    private int defaultSides;
    private Logger logger;

    public Game(int numberOfDice, int sides, Strategy strategy, boolean verbose) {
        scoreboard = new Scoreboard();
        turns = 0;
        this.strategy = strategy;
        defaultNumberOfDice = numberOfDice;
        defaultSides = sides;
        logger = new Logger(verbose);
    }

    public void waitForUser() {
        Scanner scanner = new Scanner(System.in);
        scanner.nextLine();
    }

    public void startGame() {
        turns = 0;
        Scanner scanner = new Scanner(System.in);
        while (!scoreboard.isFull()) {
            turns++;
            logger.info("Runde " + turns, 2);
            waitForUser();

            playTurn();
        }
        logger.info("Fuldt point på " + turns + " runder", 1);
        scanner.close();
    }

    public void playTurn() {
        DieCup dieCup = new DieCup(defaultNumberOfDice, defaultSides);
        int selectedNumber = strategy.getSelectedNumber(dieCup.getValuesMap(), scoreboard);
        logger.info("Valgt nummer: " + selectedNumber);
        collectPoints(dieCup, selectedNumber);
    }

    public void collectPoints(DieCup dieCup, int selectedNumber) {

        logger.info("Terninger: " + Arrays.toString(dieCup.getDiceValues()), 1);

        if (selectedNumber == -1) {
            logger.info("Ingen mulige numre at vælge", 1);
            return;
        }

        boolean canMakeNumber = dieCup.getValuesMap().containsKey(selectedNumber);

        if (!canMakeNumber) {
            logger.info("Kan ikke lave " + selectedNumber, 1);
            return;
        }

        int points = dieCup.getValuesMap().getOrDefault(selectedNumber, 0);
        scoreboard.addPoints(selectedNumber, points);

        logger.info("Score: " + scoreboard.getPoints().toString());

        int amountOfDiceToRemove = calculateDiceToRemove(selectedNumber, points);
        int diceRemainingAfterCollection = dieCup.getAmountOfDice() - amountOfDiceToRemove;

        logger.info("Fjerner " + amountOfDiceToRemove + " terninger", 1);
        logger.info("Terninger tilbage: " + diceRemainingAfterCollection);

        boolean allDiceCollected = diceRemainingAfterCollection <= 0;
        boolean fullPointsReached = scoreboard.getPoints(selectedNumber) >= 5;
        boolean playFreeTurn = allDiceCollected || fullPointsReached;

        if (allDiceCollected) {
            logger.info("Alle terninger er fjernet", 1);
        }

        if (fullPointsReached) {
            logger.info("Fuldt antal point er nået for " + selectedNumber, 1);
        }

        if (scoreboard.isFull()) {
            logger.info("Færdig!", 2);
            return;
        }

        if (playFreeTurn) {
            // Start a new turn without incrementing turns as this is a free turn
            logger.info("Slår igen med alle terninger", 1);
            turns--;
        } else {
            // Recursively collect points until no more dice can be removed
            logger.info("Slår igen med " + diceRemainingAfterCollection + " terninger", 1);
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