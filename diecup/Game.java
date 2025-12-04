package diecup;

import strategies.Strategy;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;

public class Game {
    private Strategy strategy;
    private Scoreboard scoreboard;
    private int turns;
    private int numberOfDice;
    private int sidesPerDie;
    private boolean waitForUserInput;
    private Logger logger;
    private Random rng;  // Optional seeded random for reproducible games

    public Game(int numberOfDice, int sidesPerDie, Strategy strategy, boolean waitForUserInput, boolean verbose) {
        this(numberOfDice, sidesPerDie, strategy, waitForUserInput, verbose, null);
    }
    
    /**
     * Create a game with optional seeded randomness for reproducibility.
     * @param numberOfDice number of dice
     * @param sidesPerDie sides per die
     * @param strategy the strategy to use
     * @param waitForUserInput whether to wait for user input
     * @param verbose whether to log verbose output
     * @param rng Random instance for reproducible games, or null for default randomness
     */
    public Game(int numberOfDice, int sidesPerDie, Strategy strategy, boolean waitForUserInput, boolean verbose, Random rng) {
        scoreboard = new Scoreboard();
        turns = 0;
        this.strategy = strategy;
        this.numberOfDice = numberOfDice;
        this.sidesPerDie = sidesPerDie;
        this.waitForUserInput = waitForUserInput;
        this.rng = rng;
        logger = new Logger(verbose);
    }

    public void waitForUser() {
        if (waitForUserInput) {
            @SuppressWarnings("resource")
            Scanner scanner = new Scanner(System.in);
            scanner.nextLine();
        }
    }

    public void startGame() {
        turns = 0;
        scoreboard.reset();
        while (!scoreboard.isComplete()) {
            turns++;
            logger.info("Runde " + turns, 2);
            waitForUser();

            playTurn();
        }
        logger.info("Fuldt point på " + turns + " runder", 1);
    }

    public void playTurn() {
        DieCup dieCup = new DieCup(numberOfDice, sidesPerDie, rng);
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

        int amountOfDiceToRemove = dieCup.calculateDiceToRemove(selectedNumber, points);
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

        if (scoreboard.isComplete()) {
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
            DieCup newDieCup = new DieCup(diceRemainingAfterCollection, sidesPerDie, rng);
            collectPoints(newDieCup, selectedNumber);
        }
    }

    public int getTurns() {
        return this.turns;
    }
}