import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.AbstractMap;
import java.util.Map;

import diecup.Statistics;
import strategies.ImprovedWeightedSelect;

public class ParameterTuner {
    private static final int POPULATION_SIZE = 500; // Size of the population for the genetic algorithm
    private static final int MAX_GENERATIONS = 50; // Maximum number of generations to run
    private static final int EVALUATIONS_PER_CONFIG = 10000; // Number of evaluations per configuration
    private static final int CONFIRMATION_EVALUATIONS = 20000; // More evaluations for confirmation
    private static final int ELITE_COUNT = 20; // Number of elite candidates to carry over to the next generation
    private static final int TOURNAMENT_SIZE = 3; // Size of the tournament for parent selection
    private static final double MUTATION_RATE = 0.8; // Probability of mutation per weight
    private static final double MUTATION_STRENGTH = 0.1; // Standard deviation for mutation strength
    private static final Random random = new Random();

    private static final int amountOfDice = 6; // Number of dice in the game
    private static final int sidesPerDie = 6; // Number of sides per die in the game

    // define weights to optimize
    private static final String[] WEIGHT_NAMES = {
            "opportunityWeight",
            "RarityWeight",
            "progressWeight",
            "rarityScalar",
            "collectionWeight",
            "collectionScalar",
            "completionWeight"
    };
    private static final int WEIGHT_COUNT = WEIGHT_NAMES.length;

    public static void main(String[] args) {
        System.out.println("Starting parameter optimization for ImprovedWeightedSelect...");

        Statistics statistics = new Statistics(amountOfDice, sidesPerDie);
        Optimizer optimizer = new Optimizer(statistics);

        Optimizer.ParameterSet bestParams = optimizer.optimize();

        System.out.println("=".repeat(60));
        System.out.println("OPTIMIZATION COMPLETE");
        System.out.println("=".repeat(60));
        bestParams.print();
        System.out.println("=".repeat(60));
    }

    static class Optimizer {
        private final Statistics statistics;
        private final List<ParameterSet> population;
        private double bestScore = Double.MAX_VALUE;
        private int stagnationCounter = 0;
        private double mutationStrength = MUTATION_STRENGTH;

        public Optimizer(Statistics statistics) {
            this.statistics = statistics;
            this.population = new ArrayList<>();
        }

        public ParameterSet optimize() {
            long startTime = System.currentTimeMillis();
            System.out.println("Initializing population...");

            initializePopulation();
            evaluatePopulation();

            System.out.printf("Initial best average turns: %.4f turns%n%n", bestScore);
            double previousBestScore = bestScore;

            for (int generation = 1; generation <= MAX_GENERATIONS; generation++) {
                evolvePopulation(generation);
                evaluatePopulation();

                boolean improved = bestScore < previousBestScore || generation == 1;
                printProgress(generation, startTime, improved);

                if (improved) {
                    stagnationCounter = 0;
                    mutationStrength = MUTATION_STRENGTH;
                } else {
                    stagnationCounter++;
                    if (stagnationCounter > 10) {
                        mutationStrength = Math.min(0.5, mutationStrength * 1.5);
                    }
                }

                if (stagnationCounter > 20) {
                    randomRestart(0.1); // Replace 10% of population with random
                    stagnationCounter = 0;
                    mutationStrength = MUTATION_STRENGTH;
                }

                previousBestScore = bestScore;
            }

            ParameterSet best = population.get(0);

            long totalTime = System.currentTimeMillis() - startTime;
            System.out.printf("Total optimization time: %s%n", formatTime(totalTime));

            return best;
        }

        private void initializePopulation() {
            for (int i = 0; i < POPULATION_SIZE; i++) {
                population.add(generateRandomParameterSet());
            }
        }

        private ParameterSet generateRandomParameterSet() {
            double[] w = new double[WEIGHT_COUNT];
            for (int i = 0; i < WEIGHT_COUNT; i++) {
                w[i] = random.nextDouble();
            }
            return new ParameterSet(w);
        }

        private void evaluatePopulation() {
            ExecutorService executor = Executors.newFixedThreadPool(
                    Runtime.getRuntime().availableProcessors());
            List<Future<Map.Entry<ParameterSet, Double>>> futures = new ArrayList<>();

            List<ParameterSet> shuffled = new ArrayList<>(population);
            java.util.Collections.shuffle(shuffled, random);

            for (ParameterSet params : shuffled) {
                if (params.avgTurns == 0) {
                    futures.add(executor.submit(() -> {
                        double score = evaluateParameterSet(
                                params, EVALUATIONS_PER_CONFIG);
                        return new AbstractMap.SimpleEntry<>(params, score);
                    }));
                }
            }

            for (Future<Map.Entry<ParameterSet, Double>> future : futures) {
                try {
                    Map.Entry<ParameterSet, Double> entry = future.get();
                    entry.getKey().avgTurns = entry.getValue();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            executor.shutdown();
            population.sort((a, b) -> Double.compare(a.avgTurns, b.avgTurns));
            
            // Confirm the best candidate if it shows improvement
            ParameterSet candidate = population.get(0);
            if (candidate.avgTurns < bestScore) {
                System.out.println("  Confirming promising candidate with additional evaluations...");
                double confirmedScore = evaluateParameterSet(candidate, CONFIRMATION_EVALUATIONS);
                candidate.avgTurns = confirmedScore;
                
                // Re-sort after confirmation
                population.sort((a, b) -> Double.compare(a.avgTurns, b.avgTurns));
                
                if (confirmedScore < bestScore && isStatisticallySignificant(candidate, bestScore)) {
                    bestScore = confirmedScore;
                    System.out.printf("  Confirmed statistically significant improvement: %.4f turns%n", confirmedScore);
                } else {
                    System.out.printf("  Improvement not confirmed or not significant. Original: %.4f, Confirmed: %.4f%n", 
                            bestScore, confirmedScore);
                }
            }
        }

        private double evaluateParameterSet(ParameterSet params, int runs) {
            ImprovedWeightedSelect strategy = new ImprovedWeightedSelect(
                    statistics,
                    params.weights[0],
                    params.weights[1],
                    params.weights[2],
                    params.weights[3],
                    params.weights[4],
                    params.weights[5],
                    params.weights[6]);

            double totalTurns = 0;
            double sumSquares = 0;
            for (int run = 0; run < runs; run++) {
                diecup.Game game = new diecup.Game(amountOfDice, sidesPerDie, strategy, false, false);
                game.startGame();
                double turns = game.getTurns();
                totalTurns += turns;
                sumSquares += turns * turns;
            }

            double mean = totalTurns / runs;
            double variance = (sumSquares / runs) - (mean * mean);
            params.standardError = Math.sqrt(variance / runs);
            
            return mean;
        }

        private ParameterSet selectParent() {
            ParameterSet best = null;
            for (int i = 0; i < TOURNAMENT_SIZE; i++) {
                ParameterSet candidate = population.get(random.nextInt(population.size()));
                if (best == null || candidate.avgTurns < best.avgTurns) {
                    best = candidate;
                }
            }
            return best;
        }

        private ParameterSet crossover(ParameterSet p1, ParameterSet p2) {
            double[] w = new double[WEIGHT_COUNT];
            int method = random.nextInt(3);
            if (method == 0) {
                double alpha = random.nextDouble();
                for (int i = 0; i < WEIGHT_COUNT; i++) {
                    w[i] = clamp(p1.weights[i] + alpha * (p2.weights[i] - p1.weights[i]));
                }
            } else if (method == 1) {
                for (int i = 0; i < WEIGHT_COUNT; i++) {
                    w[i] = clamp((p1.weights[i] + p2.weights[i]) / 2.0);
                }
            } else {
                for (int i = 0; i < WEIGHT_COUNT; i++) {
                    w[i] = clamp(random.nextBoolean() ? p1.weights[i] : p2.weights[i]);
                }
            }
            return new ParameterSet(w);
        }

        private void mutate(ParameterSet ps) {
            if (random.nextDouble() < MUTATION_RATE) {
                for (int i = 0; i < WEIGHT_COUNT; i++) {
                    ps.weights[i] = clamp(ps.weights[i] + random.nextGaussian() * mutationStrength);
                }
                ps.avgTurns = 0;
            }
        }
        
        private void evolvePopulation(int generation) {
            List<ParameterSet> newGeneration = new ArrayList<>();

            for (int i = 0; i < ELITE_COUNT; i++) {
                newGeneration.add(population.get(i).copy());
            }

            int diversityCount = (int) (POPULATION_SIZE * 0.05);
            for (int i = 0; i < diversityCount; i++) {
                newGeneration.add(generateRandomParameterSet());
            }

            while (newGeneration.size() < POPULATION_SIZE) {
                ParameterSet parent1 = selectParent();
                ParameterSet parent2 = selectParent();
                ParameterSet child = crossover(parent1, parent2);
                mutate(child);
                newGeneration.add(child);
            }

            population.clear();
            population.addAll(newGeneration);
        }

        private void randomRestart(double fraction) {
            int replaceCount = (int) (POPULATION_SIZE * fraction);
            for (int i = POPULATION_SIZE - replaceCount; i < POPULATION_SIZE; i++) {
                population.set(i, generateRandomParameterSet());
            }
        }

        private static double clamp(double v) {
            return Math.min(1.0, Math.max(0.0, v));
        }

        private boolean isStatisticallySignificant(ParameterSet candidate, double currentBest) {
            double difference = currentBest - candidate.avgTurns;
            double pooledSE = Math.sqrt(candidate.standardError * candidate.standardError + 
                                       candidate.standardError * candidate.standardError); // Assuming similar SE for current best
            double tStat = difference / pooledSE;
            
            // Simple threshold for statistical significance (approximately t > 1.96 for p < 0.05)
            return Math.abs(tStat) > 1.96 && difference > 0;
        }

        private void printProgress(int generation, long startTime, boolean improved) {
            long elapsed = System.currentTimeMillis() - startTime;
            double progress = (double) generation / MAX_GENERATIONS;
            long estimatedTotal = (long) (elapsed / progress);
            long remaining = estimatedTotal - elapsed;

            ParameterSet best = population.get(0);
            double confidenceInterval = best.standardError * 1.96; // 95% confidence interval

            System.out.printf("Generation %d/%d (%.1f%%) - Best average turns: %.4f turns (±%.4f) - Elapsed: %s - ETA: %s%n",
                    generation, MAX_GENERATIONS, progress * 100, bestScore, confidenceInterval,
                    formatTime(elapsed), formatTime(remaining));

            if (improved) {
                System.out.println("  *** IMPROVEMENT *** Current best parameters:");
                for (int i = 0; i < WEIGHT_COUNT; i++) {
                    System.out.printf("    %s=%.3f%n", WEIGHT_NAMES[i], best.weights[i]);
                }
                System.out.println();
            }
        }

        private String formatTime(long milliseconds) {
            long seconds = milliseconds / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;

            if (hours > 0) {
                return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
            } else if (minutes > 0) {
                return String.format("%dm %ds", minutes, seconds % 60);
            } else {
                return String.format("%ds", seconds);
            }
        }

        static class ParameterSet {
            double[] weights;
            double avgTurns = 0;
            double standardError = 0;

            ParameterSet(double[] weights) {
                this.weights = weights.clone();
            }

            ParameterSet copy() {
                ParameterSet c = new ParameterSet(this.weights);
                c.avgTurns = this.avgTurns;
                c.standardError = this.standardError;
                return c;
            }

            void print() {
                System.out.printf("Best average turns: %.4f turns (±%.4f)%n", avgTurns, standardError * 1.96);
                for (int i = 0; i < WEIGHT_COUNT; i++) {
                    System.out.printf("  %s=%.3f%n", WEIGHT_NAMES[i], weights[i]);
                }
            }
        }
    }
}
