import diecup.Calculations;
import diecup.Game;
import strategies.*;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            startGame();
        } else if ("start".equals(args[0])) {
            startGame();
        } else if ("statistics".equals(args[0])) {
            getStatistics();
        }
    }

    public static void getStatistics() {
        int numberOfDice = 6;
        int sides = 6;
        Calculations calculations = new Calculations();

        List<Strategy> strategies = getAllStrategies(numberOfDice, sides);

        System.out.println("Running statistics for " + strategies.size() + " strategies...");

        for (Strategy strategy : strategies) {
            System.out.println("\nCalculating statistics for: " + strategy.getClass().getSimpleName());
            calculations.calculateAverageTurns(strategy);
        }

        System.out.println("\nAll statistics calculated! Check the simulation_results folder for output files.");
    }

    private static List<Strategy> getAllStrategies(int numberOfDice, int sides, Statistics statistics) {
        List<Strategy> strategies = new ArrayList<>();

        strategies.add(new AdvancedWeightedSelect(numberOfDice, sides, statistics));
        strategies.add(new WeightedSelect(numberOfDice, sides, statistics));
        strategies.add(new SelectHighest());
        strategies.add(new SelectMostCommon());
        strategies.add(new ProbabilisticSelect(numberOfDice, sides, 0.7, statistics));

        return strategies;
    }

    public static void startGame() {
        int numberOfDice = 6;
        int sides = 6;
        Statistics statistics = new Statistics(numberOfDice, sides);
        Game game = new Game(numberOfDice, sides, new AdvancedWeightedSelect(numberOfDice, sides, statistics), true, true);
        game.startGame();
    }
}
