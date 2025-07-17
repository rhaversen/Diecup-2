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
    private static final int POPULATION_SIZE = 100;
    private static final int MAX_GENERATIONS = 100;
    private static final int EVALUATIONS_PER_CONFIG = 10000;
    private static final int ELITE_COUNT = 10;
    private static final int TOURNAMENT_SIZE = 3;
    private static final double MUTATION_RATE = 0.8;
    private static final double MUTATION_STRENGTH = 0.1;
    private static final Random random = new Random();
    
    // define weights to optimize
    private static final String[] WEIGHT_NAMES = {
        "UrgencyWeight", "RarityWeight"
    };
    private static final int WEIGHT_COUNT = WEIGHT_NAMES.length;

    public static void main(String[] args) {
        System.out.println("Starting parameter optimization for ImprovedWeightedSelect...");
        
        Statistics statistics = new Statistics(6, 6);
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
                evolvePopulation();
                evaluatePopulation();
                
                boolean improved = bestScore < previousBestScore || generation == 1;
                printProgress(generation, startTime, improved);
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
                w[i] = random.nextDouble(); // initial weights in [0,1]
            }
            return new ParameterSet(w);
        }

        private void evaluatePopulation() {
            ExecutorService executor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
            );
            List<Future<Map.Entry<ParameterSet, Double>>> futures = new ArrayList<>();

            for (ParameterSet params : population) {
                if (params.avgTurns == 0) {
                    futures.add(executor.submit(() -> {
                        double score = evaluateParameterSet(
                            params, EVALUATIONS_PER_CONFIG
                        );
                        return new AbstractMap.SimpleEntry<>(params, score);
                    }));
                }
            }

            for (Future<Map.Entry<ParameterSet, Double>> future : futures) {
                try {
                    Map.Entry<ParameterSet, Double> entry = future.get();
                    entry.getKey().avgTurns = entry.getValue();
                    if (entry.getValue() < bestScore) {
                        bestScore = entry.getValue();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            executor.shutdown();
            population.sort((a, b) -> Double.compare(a.avgTurns, b.avgTurns));
        }

        private double evaluateParameterSet(ParameterSet params, int runs) {
            ImprovedWeightedSelect strategy = new ImprovedWeightedSelect(
                statistics,
                params.weights[0], params.weights[1]
            );

            double totalTurns = 0;
            for (int run = 0; run < runs; run++) {
                diecup.Game game = new diecup.Game(6, 6, strategy, false, false);
                game.startGame();
                totalTurns += game.getTurns();
            }

            return totalTurns / runs;
        }

        private void evolvePopulation() {
            List<ParameterSet> newGeneration = new ArrayList<>();
            
            // Keep elite
            for (int i = 0; i < ELITE_COUNT; i++) {
                newGeneration.add(population.get(i).copy());
            }
            
            // Generate offspring
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
            double alpha = random.nextDouble();
            double[] w = new double[WEIGHT_COUNT];
            for (int i = 0; i < WEIGHT_COUNT; i++) {
                w[i] = clamp(p1.weights[i] + alpha * (p2.weights[i] - p1.weights[i]));
            }
            return new ParameterSet(w);
        }

        private void mutate(ParameterSet ps) {
            if (random.nextDouble() < MUTATION_RATE) {
                for (int i = 0; i < WEIGHT_COUNT; i++) {
                    ps.weights[i] = clamp(ps.weights[i] + random.nextGaussian() * MUTATION_STRENGTH);
                }
                ps.avgTurns = 0;
            }
        }

        private static double clamp(double v) {
            return Math.min(1.0, Math.max(0.0, v));
        }

        private void printProgress(int generation, long startTime, boolean improved) {
            long elapsed = System.currentTimeMillis() - startTime;
            double progress = (double) generation / MAX_GENERATIONS;
            long estimatedTotal = (long) (elapsed / progress);
            long remaining = estimatedTotal - elapsed;
            
            System.out.printf("Generation %d/%d (%.1f%%) - Best average turns: %.4f turns - Elapsed: %s - ETA: %s%n", 
                generation, MAX_GENERATIONS, progress * 100, bestScore, 
                formatTime(elapsed), formatTime(remaining));
                
            if (improved) {
                ParameterSet best = population.get(0);
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

            ParameterSet(double[] weights) {
                this.weights = weights.clone();
            }

            ParameterSet copy() {
                ParameterSet c = new ParameterSet(this.weights);
                c.avgTurns = this.avgTurns;
                return c;
            }

            void print() {
                System.out.printf("Best average turns: %.4f turns%n", avgTurns);
                for (int i = 0; i < WEIGHT_COUNT; i++) {
                    System.out.printf("  %s=%.3f%n", WEIGHT_NAMES[i], weights[i]);
                }
            }
        }
    }
}
