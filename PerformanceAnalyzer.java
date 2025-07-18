import diecup.*;
import strategies.*;
import java.util.*;
import java.io.*;

public class PerformanceAnalyzer {

    public static void main(String[] args) {
        int numberOfGames = 10000;

        if (args.length > 0) {
            try {
                numberOfGames = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid number format. Using default: " + numberOfGames);
            }
        }

        System.out.println("Starting ImprovedWeightedSelect performance analysis with " + numberOfGames + " games...");
        analyzeWeightedSelectPerformance(numberOfGames);
    }

    public static void analyzeWeightedSelectPerformance(int numberOfGames) {
        System.out.println("Starting comprehensive ImprovedWeightedSelect analysis...");

        int numberOfDice = 6;
        int sides = 6;
        Statistics statistics = new Statistics(numberOfDice, sides);

        List<GameResult> results = new ArrayList<>();

        // Run games and collect data
        for (int i = 0; i < numberOfGames; i++) {
            if (i % 1000 == 0) {
                System.out.println("Analyzed " + i + " games...");
            }

            ImprovedWeightedSelect strategy = new ImprovedWeightedSelect(statistics);
            GameResult result = simulateGameWithAnalysis(numberOfDice, sides, strategy);
            results.add(result);
        }

        // Analyze results
        analyzeResults(results);
    }

    private static GameResult simulateGameWithAnalysis(int numberOfDice, int sides, Strategy strategy) {
        Scoreboard scoreboard = new Scoreboard();
        int turnCount = 0;
        List<Integer> turnLengths = new ArrayList<>();
        List<String> scoreboardStates = new ArrayList<>();
        List<Double> completionProgress = new ArrayList<>();
        List<Map<Integer, Integer>> detailedStates = new ArrayList<>();
        int maxTurnLength = 0;

        while (!scoreboard.isComplete()) {
            turnCount++;
            DieCup dieCup = new DieCup(numberOfDice, sides);
            int selectionCount = 0;

            double progressAtTurnStart = calculateCompletionProgress(scoreboard);
            completionProgress.add(progressAtTurnStart);
            detailedStates.add(new HashMap<>(scoreboard.getPoints()));

            while (true) {
                Map<Integer, Integer> values = dieCup.getValuesMap();

                if (values.isEmpty()) {
                    break;
                }

                int selectedNumber = strategy.getSelectedNumber(values, scoreboard);

                if (selectedNumber == -1) {
                    break;
                }

                selectionCount++;

                // Execute selection
                int occurrences = values.get(selectedNumber);
                int diceToRemove = dieCup.calculateDiceToRemove(selectedNumber, occurrences);
                int pointsBefore = scoreboard.getPoints(selectedNumber);
                int pointsToAdd = Math.min(occurrences, 5 - pointsBefore);

                scoreboard.addPoints(selectedNumber, pointsToAdd);

                if (scoreboard.isComplete()) {
                    break;
                }

                boolean allDiceUsed = dieCup.getAmountOfDice() == diceToRemove;
                dieCup = new DieCup(numberOfDice - diceToRemove, sides);

                if (scoreboard.getPoints(selectedNumber) >= 5 || allDiceUsed) {
                    dieCup = new DieCup(numberOfDice, sides);
                    break;
                }
            }

            turnLengths.add(selectionCount);
            maxTurnLength = Math.max(maxTurnLength, selectionCount);
            scoreboardStates.add(encodeScoreboardState(scoreboard));
        }

        return new GameResult(turnCount, turnLengths, scoreboardStates, maxTurnLength,
                scoreboard.getPoints(), completionProgress, detailedStates);
    }

    private static double calculateCompletionProgress(Scoreboard scoreboard) {
        Map<Integer, Integer> points = scoreboard.getPoints();
        int completed = 0;
        for (int i = 1; i <= 12; i++) {
            if (points.getOrDefault(i, 0) >= 5) {
                completed++;
            }
        }
        return completed / 12.0;
    }

    private static String encodeScoreboardState(Scoreboard scoreboard) {
        Map<Integer, Integer> points = scoreboard.getPoints();
        int[] counts = new int[6]; // [0-points, 1-point, 2-points, 3-points, 4-points, 5-points]

        for (int i = 1; i <= 12; i++) {
            int pts = points.getOrDefault(i, 0);
            if (pts >= 5)
                counts[5]++;
            else
                counts[pts]++;
        }

        return String.format("%d-%d-%d-%d-%d-%d", counts[0], counts[1], counts[2],
                counts[3], counts[4], counts[5]);
    }

    private static void analyzeResults(List<GameResult> results) {
        try {
            System.out.println("Generating analysis reports...");

            // Sort by game length for quartile analysis
            results.sort((a, b) -> Integer.compare(a.totalTurns, b.totalTurns));

            int total = results.size();
            int q1Index = total / 4;
            int q3Index = 3 * total / 4;

            List<GameResult> worstQuartile = results.subList(q3Index, total);
            List<GameResult> bestQuartile = results.subList(0, q1Index);

            generateTurnLengthReport(results, bestQuartile, worstQuartile);
            generateScoreboardAnalysis(results, bestQuartile, worstQuartile);
            generatePerformanceReport(results);
            generateLateGameAnalysis(results, bestQuartile, worstQuartile);

            System.out.println("Analysis complete! Check analysis_results/ for reports.");

        } catch (IOException e) {
            System.err.println("Error generating analysis: " + e.getMessage());
        }
    }

    private static void generateTurnLengthReport(List<GameResult> all, List<GameResult> best, List<GameResult> worst)
            throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("TURN LENGTH ANALYSIS\n");
        report.append("====================\n\n");

        // Turn length distribution
        Map<Integer, Integer> allTurnLengths = new HashMap<>();
        Map<Integer, Integer> bestTurnLengths = new HashMap<>();
        Map<Integer, Integer> worstTurnLengths = new HashMap<>();

        for (GameResult game : all) {
            for (int length : game.turnLengths) {
                allTurnLengths.merge(length, 1, Integer::sum);
            }
        }

        for (GameResult game : best) {
            for (int length : game.turnLengths) {
                bestTurnLengths.merge(length, 1, Integer::sum);
            }
        }

        for (GameResult game : worst) {
            for (int length : game.turnLengths) {
                worstTurnLengths.merge(length, 1, Integer::sum);
            }
        }

        report.append("Turn Length Distribution:\n");
        report.append("Length\tAll\tBest 25%\tWorst 25%\n");
        for (int i = 1; i <= 10; i++) {
            report.append(String.format("%d\t%d\t%d\t%d\n",
                    i, allTurnLengths.getOrDefault(i, 0),
                    bestTurnLengths.getOrDefault(i, 0),
                    worstTurnLengths.getOrDefault(i, 0)));
        }

        report.append("\nLong Turn Analysis (6+ selections):\n");
        int allLongTurns = allTurnLengths.entrySet().stream()
                .filter(e -> e.getKey() >= 6)
                .mapToInt(Map.Entry::getValue)
                .sum();
        int bestLongTurns = bestTurnLengths.entrySet().stream()
                .filter(e -> e.getKey() >= 6)
                .mapToInt(Map.Entry::getValue)
                .sum();
        int worstLongTurns = worstTurnLengths.entrySet().stream()
                .filter(e -> e.getKey() >= 6)
                .mapToInt(Map.Entry::getValue)
                .sum();

        report.append(String.format("All games: %d long turns\n", allLongTurns));
        report.append(String.format("Best quartile: %d long turns\n", bestLongTurns));
        report.append(String.format("Worst quartile: %d long turns\n", worstLongTurns));

        writeToFile("turn_length_analysis.txt", report.toString());
    }

    private static void generateScoreboardAnalysis(List<GameResult> all, List<GameResult> best, List<GameResult> worst)
            throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("SCOREBOARD STATE ANALYSIS\n");
        report.append("=========================\n\n");

        Map<String, Integer> allStates = new HashMap<>();
        Map<String, Integer> worstStates = new HashMap<>();

        for (GameResult game : all) {
            for (String state : game.scoreboardStates) {
                allStates.merge(state, 1, Integer::sum);
            }
        }

        for (GameResult game : worst) {
            for (String state : game.scoreboardStates) {
                worstStates.merge(state, 1, Integer::sum);
            }
        }

        report.append("Most Common Problematic States (format: 0pt-1pt-2pt-3pt-4pt-5pt):\n");
        worstStates.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(20)
                .forEach(entry -> {
                    report.append(String.format("%s: %d occurrences\n", entry.getKey(), entry.getValue()));
                });

        writeToFile("scoreboard_analysis.txt", report.toString());
    }

    private static void generatePerformanceReport(List<GameResult> results) throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("PERFORMANCE SUMMARY\n");
        report.append("===================\n\n");

        int total = results.size();
        List<Integer> gameLengths = results.stream().mapToInt(r -> r.totalTurns).sorted().boxed().toList();

        double average = gameLengths.stream().mapToInt(Integer::intValue).average().orElse(0);
        int median = gameLengths.get(total / 2);
        int q1 = gameLengths.get(total / 4);
        int q3 = gameLengths.get(3 * total / 4);
        int min = gameLengths.get(0);
        int max = gameLengths.get(total - 1);

        report.append(String.format("Games analyzed: %d\n", total));
        report.append(String.format("Average turns: %.2f\n", average));
        report.append(String.format("Median turns: %d\n", median));
        report.append(String.format("Q1: %d, Q3: %d\n", q1, q3));
        report.append(String.format("Range: %d - %d\n", min, max));

        // Identify problem games
        List<GameResult> problemGames = results.stream()
                .filter(r -> r.totalTurns >= 25)
                .sorted((a, b) -> Integer.compare(b.totalTurns, a.totalTurns))
                .limit(10)
                .toList();

        report.append(String.format("\nWorst 10 games (25+ turns):\n"));
        for (GameResult game : problemGames) {
            report.append(String.format("Game: %d turns, max turn length: %d, final state: %s\n",
                    game.totalTurns, game.maxTurnLength, game.scoreboardStates.get(game.scoreboardStates.size() - 1)));
        }

        writeToFile("performance_summary.txt", report.toString());
    }

    private static void generateLateGameAnalysis(List<GameResult> all, List<GameResult> best, List<GameResult> worst)
            throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("LATE GAME ANALYSIS\n");
        report.append("==================\n\n");

        analyzeProgressionSpeed(report, all, best, worst);
        analyzeLateGameTurnPatterns(report, all, best, worst);
        analyzeStuckPatterns(report, worst);

        writeToFile("late_game_analysis.txt", report.toString());
    }

    private static void analyzeProgressionSpeed(StringBuilder report, List<GameResult> all, List<GameResult> best,
            List<GameResult> worst) {
        report.append("COMPLETION SPEED ANALYSIS\n");
        report.append("Turn to reach completion thresholds:\n\n");

        double[] thresholds = { 0.5, 0.75, 0.9, 0.95 };
        String[] labels = { "50%", "75%", "90%", "95%" };

        for (int i = 0; i < thresholds.length; i++) {
            double threshold = thresholds[i];
            String label = labels[i];

            double avgAll = calculateAverageTurnToReachThreshold(all, threshold);
            double avgBest = calculateAverageTurnToReachThreshold(best, threshold);
            double avgWorst = calculateAverageTurnToReachThreshold(worst, threshold);

            report.append(String.format("%s completion - All: %.1f, Best: %.1f, Worst: %.1f\n",
                    label, avgAll, avgBest, avgWorst));
        }

        report.append("\n");
    }

    private static double calculateAverageTurnToReachThreshold(List<GameResult> games, double threshold) {
        return games.stream()
                .mapToInt(game -> findTurnToReachThreshold(game, threshold))
                .filter(turn -> turn != -1)
                .average()
                .orElse(-1);
    }

    private static int findTurnToReachThreshold(GameResult game, double threshold) {
        for (int i = 0; i < game.completionProgress.size(); i++) {
            if (game.completionProgress.get(i) >= threshold) {
                return i + 1;
            }
        }
        return -1;
    }

    private static void analyzeLateGameTurnPatterns(StringBuilder report, List<GameResult> all, List<GameResult> best,
            List<GameResult> worst) {
        report.append("LATE GAME TURN LENGTH PATTERNS\n");

        Map<String, List<Integer>> lateGameTurns = new HashMap<>();
        lateGameTurns.put("All", extractLateGameTurns(all));
        lateGameTurns.put("Best", extractLateGameTurns(best));
        lateGameTurns.put("Worst", extractLateGameTurns(worst));

        for (Map.Entry<String, List<Integer>> entry : lateGameTurns.entrySet()) {
            String category = entry.getKey();
            List<Integer> turns = entry.getValue();

            if (!turns.isEmpty()) {
                double avgLength = turns.stream().mapToInt(Integer::intValue).average().orElse(0);
                long longTurns = turns.stream().filter(t -> t >= 4).count();
                double longTurnRate = (double) longTurns / turns.size() * 100;

                report.append(String.format("%s quartile late game (90%% complete):\n", category));
                report.append(String.format("  Avg turn length: %.2f\n", avgLength));
                report.append(String.format("  Long turns (4+): %.1f%%\n", longTurnRate));
                report.append(String.format("  Total late turns: %d\n\n", turns.size()));
            }
        }
    }

    private static List<Integer> extractLateGameTurns(List<GameResult> games) {
        List<Integer> lateGameTurns = new ArrayList<>();

        for (GameResult game : games) {
            for (int i = 0; i < game.completionProgress.size(); i++) {
                if (game.completionProgress.get(i) >= 0.9 && i < game.turnLengths.size()) {
                    lateGameTurns.add(game.turnLengths.get(i));
                }
            }
        }

        return lateGameTurns;
    }

    private static void analyzeStuckPatterns(StringBuilder report, List<GameResult> worst) {
        report.append("STUCK PATTERN ANALYSIS (Worst Quartile)\n");

        Map<String, Integer> stuckStates = new HashMap<>();
        Map<String, List<Integer>> stuckTurnLengths = new HashMap<>();
        Map<Integer, Integer> incompleteValueCounts = new HashMap<>();
        Map<Integer, List<Integer>> incompleteValuePoints = new HashMap<>();

        for (GameResult game : worst) {
            for (int i = 0; i < game.completionProgress.size(); i++) {
                if (game.completionProgress.get(i) >= 0.9) {
                    String state = game.scoreboardStates.get(Math.min(i, game.scoreboardStates.size() - 1));
                    stuckStates.merge(state, 1, Integer::sum);

                    if (i < game.turnLengths.size()) {
                        stuckTurnLengths.computeIfAbsent(state, k -> new ArrayList<>())
                                .add(game.turnLengths.get(i));
                    }

                    if (i < game.detailedStates.size()) {
                        Map<Integer, Integer> detailed = game.detailedStates.get(i);
                        for (int value = 1; value <= 12; value++) {
                            int points = detailed.getOrDefault(value, 0);
                            if (points < 5) {
                                incompleteValueCounts.merge(value, 1, Integer::sum);
                                incompleteValuePoints.computeIfAbsent(value, k -> new ArrayList<>()).add(points);
                            }
                        }
                    }
                }
            }
        }

        report.append("Most problematic late-game states:\n");
        stuckStates.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(10)
                .forEach(entry -> {
                    String state = entry.getKey();
                    int occurrences = entry.getValue();
                    List<Integer> turns = stuckTurnLengths.get(state);
                    double avgTurnLength = turns != null
                            ? turns.stream().mapToInt(Integer::intValue).average().orElse(0)
                            : 0;

                    report.append(String.format("%s: %d times, avg turn length: %.1f\n",
                            state, occurrences, avgTurnLength));
                });

        report.append("\nMost frequently incomplete values in late game:\n");
        incompleteValueCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(12)
                .forEach(entry -> {
                    int value = entry.getKey();
                    int count = entry.getValue();
                    List<Integer> points = incompleteValuePoints.get(value);
                    double avgPoints = points.stream().mapToInt(Integer::intValue).average().orElse(0);

                    report.append(String.format("Value %d: incomplete %d times, avg points: %.1f\n",
                            value, count, avgPoints));
                });

        analyzeFinalValueStruggles(report, worst);
    }

    private static void analyzeFinalValueStruggles(StringBuilder report, List<GameResult> worst) {
        report.append("\nFINAL VALUE ANALYSIS\n");

        Map<Integer, Integer> finalValueStruggles = new HashMap<>();
        Map<Integer, List<Integer>> finalValueTurns = new HashMap<>();

        for (GameResult game : worst) {
            if (game.totalTurns >= 25) {
                Map<Integer, Integer> finalState = game.detailedStates.get(game.detailedStates.size() - 1);
                for (Map.Entry<Integer, Integer> entry : finalState.entrySet()) {
                    int value = entry.getKey();
                    int points = entry.getValue();
                    if (points < 5) {
                        finalValueStruggles.merge(value, 1, Integer::sum);
                        finalValueTurns.computeIfAbsent(value, k -> new ArrayList<>()).add(game.totalTurns);
                    }
                }
            }
        }

        report.append("Values most often incomplete in problem games (25+ turns):\n");
        finalValueStruggles.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .forEach(entry -> {
                    int value = entry.getKey();
                    int count = entry.getValue();
                    List<Integer> turns = finalValueTurns.get(value);
                    double avgTurns = turns.stream().mapToInt(Integer::intValue).average().orElse(0);

                    report.append(String.format("Value %d: incomplete in %d problem games, avg game length: %.1f\n",
                            value, count, avgTurns));
                });
    }

    private static void writeToFile(String filename, String content) throws IOException {
        new File("analysis_results").mkdirs();
        try (PrintWriter writer = new PrintWriter(new FileWriter("analysis_results/" + filename))) {
            writer.println(content);
        }
    }

    // Data class for storing game results
    private static class GameResult {
        int totalTurns;
        List<Integer> turnLengths;
        List<String> scoreboardStates;
        List<Double> completionProgress;
        List<Map<Integer, Integer>> detailedStates;
        int maxTurnLength;

        GameResult(int totalTurns, List<Integer> turnLengths, List<String> scoreboardStates,
                int maxTurnLength, Map<Integer, Integer> finalScoreboard,
                List<Double> completionProgress, List<Map<Integer, Integer>> detailedStates) {
            this.totalTurns = totalTurns;
            this.turnLengths = new ArrayList<>(turnLengths);
            this.scoreboardStates = new ArrayList<>(scoreboardStates);
            this.completionProgress = new ArrayList<>(completionProgress);
            this.detailedStates = new ArrayList<>();
            for (Map<Integer, Integer> state : detailedStates) {
                this.detailedStates.add(new HashMap<>(state));
            }
            this.maxTurnLength = maxTurnLength;
        }
    }
}
