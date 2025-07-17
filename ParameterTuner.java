import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import diecup.Statistics;
import strategies.ImprovedWeightedSelect;

public class ParameterTuner {
    private static final int POPULATION_SIZE = 100;
    private static final int MAX_GENERATIONS = 500;
    private static final int RUNS_PER_CONFIG = 200;
    private static final int EVALUATIONS_PER_CONFIG = 5;
    private static final int FINAL_RUNS = 500;
    private static final int FINAL_EVALUATIONS = 10;
    private static final int ELITE_COUNT = 10;
    private static final int TOURNAMENT_SIZE = 3;
    private static final double MUTATION_RATE = 0.8;
    private static final double MUTATION_STRENGTH = 0.1;
    private static final Random random = new Random();

    public static void main(String[] args) {
        System.out.println("Starting simple parameter optimization for ImprovedWeightedSelect...");
        
        Statistics statistics = new Statistics(6, 6);
        SimpleOptimizer optimizer = new SimpleOptimizer(statistics);
        
        SimpleOptimizer.ParameterSet bestParams = optimizer.optimize();
        
        System.out.println("=".repeat(60));
        System.out.println("OPTIMIZATION COMPLETE");
        System.out.println("=".repeat(60));
        bestParams.print();
        System.out.println("=".repeat(60));
    }

    static class SimpleOptimizer {
        private final Statistics statistics;
        private final List<ParameterSet> population;
        private double bestScore = Double.MAX_VALUE;

        public SimpleOptimizer(Statistics statistics) {
            this.statistics = statistics;
            this.population = new ArrayList<>();
        }

        public ParameterSet optimize() {
            long startTime = System.currentTimeMillis();
            System.out.println("Initializing population...");
            
            initializePopulation();
            evaluatePopulation();
            
            System.out.printf("Initial best Q3: %.4f turns%n%n", bestScore);
            double previousBestScore = bestScore;
            
            for (int generation = 1; generation <= MAX_GENERATIONS; generation++) {
                evolvePopulation();
                evaluatePopulation();
                
                boolean improved = bestScore < previousBestScore || generation == 1;
                printProgress(generation, startTime, improved);
                previousBestScore = bestScore;
            }
            
            ParameterSet best = population.get(0);
            System.out.printf("%nFinal evaluation with %d runs x %d evaluations...%n", FINAL_RUNS, FINAL_EVALUATIONS);
            best.q3Score = evaluateParameterSetAverage(best, FINAL_RUNS, FINAL_EVALUATIONS);
            
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
            return new ParameterSet(
                random.nextDouble() * 5.0,
                random.nextDouble() * 5.0
            );
        }

        private void evaluatePopulation() {
            for (ParameterSet params : population) {
                if (params.q3Score == 0) {
                    params.q3Score = evaluateParameterSetAverage(params, RUNS_PER_CONFIG, EVALUATIONS_PER_CONFIG);
                    if (params.q3Score < bestScore) {
                        bestScore = params.q3Score;
                    }
                }
            }
            population.sort((a, b) -> Double.compare(a.q3Score, b.q3Score));
        }

        private double evaluateParameterSetAverage(ParameterSet params, int runsPerEvaluation, int evaluations) {
            double totalQ3 = 0.0;
            
            for (int eval = 0; eval < evaluations; eval++) {
                double q3 = evaluateParameterSetSingle(params, runsPerEvaluation);
                totalQ3 += q3;
            }
            
            return totalQ3 / evaluations;
        }

        private double evaluateParameterSetSingle(ParameterSet params, int runs) {
            ImprovedWeightedSelect strategy =
                new ImprovedWeightedSelect(statistics, params.urgencyWeight, params.futureWeight);
            
            List<Double> results = new ArrayList<>();
            for (int run = 0; run < runs; run++) {
                diecup.Game game = new diecup.Game(6, 6, strategy, false, false);
                game.startGame();
                results.add((double) game.getTurns());
            }
            
            results.sort(Double::compareTo);
            
            // Calculate Q3 with proper double precision interpolation
            double q3Position = (results.size() - 1) * 0.75;
            int lowerIndex = (int) Math.floor(q3Position);
            int upperIndex = (int) Math.ceil(q3Position);
            lowerIndex = Math.max(0, Math.min(lowerIndex, results.size() - 1));
            upperIndex = Math.max(0, Math.min(upperIndex, results.size() - 1));
            
            if (lowerIndex == upperIndex) {
                return results.get(lowerIndex);
            } else {
                double fraction = q3Position - lowerIndex;
                return results.get(lowerIndex) * (1.0 - fraction) + results.get(upperIndex) * fraction;
            }
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
                if (best == null || candidate.q3Score < best.q3Score) {
                    best = candidate;
                }
            }
            return best;
        }

        private ParameterSet crossover(ParameterSet p1, ParameterSet p2) {
            double alpha = random.nextDouble();
            return new ParameterSet(
                interpolate(p1.urgencyWeight, p2.urgencyWeight, alpha),
                interpolate(p1.futureWeight,   p2.futureWeight,   alpha)
            );
        }

        private void mutate(ParameterSet params) {
            if (random.nextDouble() < MUTATION_RATE) {
                params.urgencyWeight += gaussian(0, MUTATION_STRENGTH);
                params.futureWeight   += gaussian(0, MUTATION_STRENGTH);
                params.q3Score = 0;
            }
        }

        private double interpolate(double a, double b, double alpha) {
            return a + alpha * (b - a);
        }

        private double gaussian(double mean, double stdDev) {
            return mean + stdDev * random.nextGaussian();
        }

        private void printProgress(int generation, long startTime, boolean improved) {
            long elapsed = System.currentTimeMillis() - startTime;
            double progress = (double) generation / MAX_GENERATIONS;
            long estimatedTotal = (long) (elapsed / progress);
            long remaining = estimatedTotal - elapsed;
            
            System.out.printf("Generation %d/%d (%.1f%%) - Best Q3: %.4f turns - Elapsed: %s - ETA: %s%n", 
                generation, MAX_GENERATIONS, progress * 100, bestScore, 
                formatTime(elapsed), formatTime(remaining));
                
            if (improved && population.size() > 0) {
                ParameterSet best = population.get(0);
                System.out.println("  *** IMPROVEMENT *** Current best parameters:");
                System.out.printf("    Urgency=%.3f, Future=%.3f%n",
                    best.urgencyWeight, best.futureWeight);
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
            double urgencyWeight;
            double futureWeight;
            double q3Score = 0;

            ParameterSet(double urgencyWeight, double futureWeight) {
                this.urgencyWeight = urgencyWeight;
                this.futureWeight  = futureWeight;
            }

            ParameterSet copy() {
                ParameterSet c = new ParameterSet(urgencyWeight, futureWeight);
                c.q3Score = this.q3Score;
                return c;
            }

            void print() {
                System.out.printf("Best Q3 Performance: %.4f turns%n", q3Score);
                System.out.printf("UrgencyWeight=%.3f, FutureWeight=%.3f%n",
                                  urgencyWeight, futureWeight);
            }
        }
    }
}
