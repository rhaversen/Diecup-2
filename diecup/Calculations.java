package diecup;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import strategies.Strategy;

public class Calculations {
    private Logger logger = new Logger(true);
    private static final long DEFAULT_SEED = 42;  // Fixed seed for reproducibility

    public void calculateAverageTurns(Strategy strategy) {
        calculateAverageTurns(strategy, DEFAULT_SEED);
    }

    public void calculateAverageTurns(Strategy strategy, long seed) {
        int simulationCount = 100000;
        int numberOfDice = 6;
        int sides = 6;
        List<Integer> turns = new ArrayList<>();

        // Generate seeds for each game (derived from master seed for reproducibility)
        Random masterRandom = new Random(seed);
        long[] gameSeeds = new long[simulationCount];
        for (int i = 0; i < simulationCount; i++) {
            gameSeeds[i] = masterRandom.nextLong();
        }

        long startTime = System.currentTimeMillis();
        long lastProgressUpdate = startTime;

        logger.log("Starting simulation with " + simulationCount + " iterations using "
                + strategy.getClass().getSimpleName() + " (seed=" + seed + ")");

        for (int i = 0; i < simulationCount; i++) {
            long currentTime = System.currentTimeMillis();

            // Show progress every 500ms
            if (currentTime - lastProgressUpdate >= 500 && i > 0) {
                long elapsedTime = currentTime - startTime;
                double avgTimePerSimulation = (double) elapsedTime / i;
                long remainingSimulations = simulationCount - i;
                long estimatedTimeRemaining = (long) (remainingSimulations * avgTimePerSimulation);

                String progressMsg = String.format("Progress: %d/%d (%.1f%%) - ETA: %d seconds",
                        i, simulationCount, (100.0 * i / simulationCount), estimatedTimeRemaining / 1000);
                logger.log(progressMsg);
                lastProgressUpdate = currentTime;
            }

            Random gameRng = new Random(gameSeeds[i]);
            Game game = new Game(numberOfDice, sides, strategy, false, false, gameRng);
            game.startGame();
            turns.add(game.getTurns());
        }

        // Create directory for output if it doesn't exist
        String outputDir = "simulation_results";
        try {
            Files.createDirectories(Paths.get(outputDir));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Generate filename based on strategy and timestamp
        String strategyName;
        try {
            String toStringResult = strategy.toString();
            // Check if toString() returns a custom name or just the default
            // Object.toString()
            if (toStringResult.contains("@")) {
                // Default Object.toString() format, use class name instead
                strategyName = strategy.getClass().getSimpleName();
            } else {
                strategyName = toStringResult;
            }
        } catch (Exception e) {
            // Fallback to class name if toString() fails
            strategyName = strategy.getClass().getSimpleName();
        }
        String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("%s/%s_%s.txt", outputDir, strategyName, dateTime);

        // Write turns to a file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            for (Integer turn : turns) {
                writer.write(turn.toString());
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Print average turns
        double averageTurns = turns.stream().mapToInt(Integer::intValue).average().orElse(0);
        logger.log("Simulation completed!");
        logger.log("Average turns: " + averageTurns);
    }

}
