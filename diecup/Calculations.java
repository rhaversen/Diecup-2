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

import strategies.Strategy;

public class Calculations {
    public void calculateAverageTurns(Strategy strategy) {
        int simulationCount = 10000;
        int numberOfDice = 6;
        int sides = 6;
        List<Integer> turns = new ArrayList<>();

        for (int i = 0; i < simulationCount; i++) {
            System.out.println("Iteration number: " + i);
            Game game = new Game(numberOfDice, sides, strategy, false, false);
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
        String strategyName = strategy.getClass().getSimpleName();
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
        System.out.println("Average turns: " + averageTurns);
    }

}
