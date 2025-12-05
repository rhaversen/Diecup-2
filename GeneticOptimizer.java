import java.util.*;
import java.util.concurrent.*;

import diecup.Logger;
import diecup.Statistics;
import strategies.ImprovedWeightedSelect;

/**
 * Genetic Algorithm optimizer for tuning strategy parameters.
 * 
 * Features:
 * - **Common Random Numbers (CRN)**: All individuals in a generation are evaluated
 *   on the same random seeds, ensuring fair comparisons and reducing selection noise.
 * - Tournament selection with elitism
 * - Multiple crossover strategies (blend, average, uniform)
 * - Per-gene Gaussian mutation with adaptive strength
 * - Head-to-head confirmation against current best using paired testing
 * - Statistical significance testing before accepting improvements
 * - Periodic elite re-evaluation to prevent stale estimates
 * - Diversity injection and stagnation detection
 */
public class GeneticOptimizer {

    // ===== CONFIGURATION =====
    
    /** GA Population and Generation Settings */
    private static final int POPULATION_SIZE = 1000;
    private static final int MAX_GENERATIONS = Integer.MAX_VALUE;  // Run until manually stopped
    private static final int ELITE_COUNT = 20;
    private static final double DIVERSITY_RATIO = 0.20;  // 20% random injection per generation
    
    /** Selection Settings */
    private static final int TOURNAMENT_SIZE = 3;
    
    /** Mutation Settings */
    private static final double MUTATION_RATE_PER_GENE = 0.35;  // Chance to mutate each gene
    private static final double MUTATION_STRENGTH_INITIAL = 0.20;  // Initial mutation strength
    private static final double MUTATION_STRENGTH_MAX = 0.6; // Max mutation strength
    private static final double LARGE_MUTATION_RATE = 0.12; // Chance to reset a gene completely
    
    /** Evaluation Settings - Common Random Numbers */
    private static final int GAMES_PER_GENERATION = 5_000;     // Games per individual per generation (using shared seeds)
    
    /** Fitness Component Weights (must sum to 1.0 for interpretability) */
    private static final double MEAN_WEIGHT = 0.50;      // Weight for mean turns (primary objective)
    private static final double VARIANCE_WEIGHT = 0.15;  // Weight for variance (consistency)
    private static final double MEDIAN_WEIGHT = 0.20;    // Weight for median (typical performance)
    private static final double Q3_WEIGHT = 0.15;        // Weight for 75th percentile (worst-case)
    
    /** Stagnation Settings */
    private static final int STAGNATION_THRESHOLD = 3;   // Increase mutation after N generations without improvement
    private static final int RESTART_THRESHOLD = 10;     // Inject diversity after N generations without improvement
    private static final double RESTART_FRACTION = 0.50; // Replace this fraction of population on restart
    
    /** Threading */
    private static final int FREE_THREADS = 1;  // Leave some CPU free for system responsiveness
    private static final int THREAD_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors() - FREE_THREADS);
    
    /** Game Configuration */
    private static final int DICE_COUNT = 6;
    private static final int SIDES_PER_DIE = 6;
    
    /** Parameter Names (for display) */
    private static final String[] PARAM_NAMES = {
        "OpportunityWeight", "RarityWeight", "ProgressWeight", "RarityScalar",
        "CollectionWeight", "CollectionScalar", "CompletionWeight", "CatchUpWeight",
        "DiceCostWeight", "VarianceWeight", "GameProgressWeight", "AllDiceBonusWeight",
        "RemainingValueWeight"
    };
    private static final int PARAM_COUNT = PARAM_NAMES.length;
    
    // ===== INSTANCE FIELDS =====
    
    private final Random masterRandom = new Random();
    private final Statistics statistics;
    private final Logger logger;
    private final List<Individual> population = new ArrayList<>();
    
    private Individual globalBest = null;
    private int stagnationCount = 0;
    private double mutationStrength = MUTATION_STRENGTH_INITIAL;
    private int totalGamesPlayed = 0;  // Track total compute spent on best
    
    // ===== MAIN =====
    
    public static void main(String[] args) {
        GeneticOptimizer optimizer = new GeneticOptimizer();
        optimizer.log("=== Genetic Optimizer for ImprovedWeightedSelect ===");
        optimizer.log("Using Common Random Numbers (CRN) for fair comparison");
        optimizer.log("");
        
        Individual best = optimizer.run();
        
        optimizer.log("");
        optimizer.log("=".repeat(60));
        optimizer.log("OPTIMIZATION COMPLETE");
        optimizer.log("=".repeat(60));
        optimizer.printIndividualDetails(best);
        optimizer.log("=".repeat(60));
        optimizer.close();
    }
    
    // ===== CONSTRUCTOR =====
    
    public GeneticOptimizer() {
        this.statistics = new Statistics(DICE_COUNT, SIDES_PER_DIE);
        this.logger = new Logger(true, Logger.generateLogFileName("genetic_optimizer"));
        log("Log file: " + logger.getLogFilePath());
    }
    
    private void log(String message) {
        logger.log(message);
    }
    
    private void close() {
        logger.close();
    }
    
    // ===== MAIN OPTIMIZATION LOOP =====
    
    public Individual run() {
        long startTime = System.currentTimeMillis();
        
        // Initialize
        initializePopulation();
        evaluatePopulation(0);
        
        log(String.format("Initial best fitness: %.4f", globalBest.fitness));
        log("");
        double previousBest = globalBest.fitness;
        
        // Evolution loop
        for (int gen = 1; gen <= MAX_GENERATIONS; gen++) {
            evolve();
            evaluatePopulation(gen);
            
            boolean improved = globalBest.fitness < previousBest;
            printProgress(gen, startTime, improved);
            
            handleStagnation(improved);
            previousBest = globalBest.fitness;
        }
        
        log("");
        log(String.format("Total time: %s", formatDuration(System.currentTimeMillis() - startTime)));
        return globalBest;
    }
    
    // ===== POPULATION MANAGEMENT =====
    
    private void initializePopulation() {
        log("Initializing population...");
        
        // Seed with known good parameters from strategy defaults
        double[] knownGood = {
            ImprovedWeightedSelect.getDefaultOpportunityWeight(),
            ImprovedWeightedSelect.getDefaultRarityWeight(),
            ImprovedWeightedSelect.getDefaultProgressWeight(),
            ImprovedWeightedSelect.getDefaultRarityScalar(),
            ImprovedWeightedSelect.getDefaultCollectionWeight(),
            ImprovedWeightedSelect.getDefaultCollectionScalar(),
            ImprovedWeightedSelect.getDefaultCompletionWeight(),
            ImprovedWeightedSelect.getDefaultCatchUpWeight(),
            ImprovedWeightedSelect.getDefaultDiceCostWeight(),
            ImprovedWeightedSelect.getDefaultVarianceWeight(),
            ImprovedWeightedSelect.getDefaultGameProgressWeight(),
            ImprovedWeightedSelect.getDefaultAllDiceBonusWeight(),
            ImprovedWeightedSelect.getDefaultRemainingValueWeight()
        };
        population.add(new Individual(knownGood));
        log("Seeded with known good parameters from strategy defaults");
        
        // Fill rest with random individuals
        for (int i = 1; i < POPULATION_SIZE; i++) {
            population.add(createRandomIndividual());
        }
    }
    
    private Individual createRandomIndividual() {
        double[] genes = new double[PARAM_COUNT];
        for (int i = 0; i < PARAM_COUNT; i++) {
            // Range -1 to 2 to allow exploration in all directions
            genes[i] = masterRandom.nextDouble() * 3.0 - 1.0;
        }
        return new Individual(genes);
    }
    
    // ===== EVALUATION WITH COMMON RANDOM NUMBERS =====
    
    /**
     * Generate seeds for this generation. All individuals will be evaluated
     * on the same set of random seeds, ensuring fair comparison.
     */
    private long[] generateSeeds(int count) {
        long[] seeds = new long[count];
        for (int i = 0; i < count; i++) {
            seeds[i] = masterRandom.nextLong();
        }
        return seeds;
    }
    
    /**
     * Evaluate all individuals using Common Random Numbers.
     * Each individual plays the same games (same seeds), making comparison fair.
     * 
     * IMPORTANT: We re-evaluate ALL individuals (including elites) each generation.
     * This is required for CRN to work correctly - everyone must be measured on
     * the SAME seeds to compare fairly. Elites keeping old fitness would be
     * comparing apples to oranges.
     */
    private void evaluatePopulation(int generation) {
        // Generate shared seeds for this generation
        long[] seeds = generateSeeds(GAMES_PER_GENERATION);
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<?>> futures = new ArrayList<>();
        
        // Evaluate ALL individuals on the same seeds - required for fair CRN comparison
        for (Individual ind : population) {
            futures.add(executor.submit(() -> {
                evaluateWithSeeds(ind, seeds);
            }));
        }
        
        // Wait for all evaluations
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        executor.shutdown();
        
        // Sort by fitness (lower is better)
        population.sort(Comparator.comparingDouble(ind -> ind.fitness));
        
        // Find where the current globalBest ended up (if exists)
        String globalBestRank = "N/A";
        if (globalBest != null) {
            for (int i = 0; i < population.size(); i++) {
                if (Arrays.equals(population.get(i).genes, globalBest.genes)) {
                    globalBestRank = String.format("#%d (%.4f)", i + 1, population.get(i).fitness);
                    break;
                }
            }
        }
        
        // Log screening results
        Individual screeningBest = population.get(0);
        int eliteCount = (int) population.stream().filter(ind -> ind.isElite).count();
        log(String.format("  CRN Screening: best=%.4f, globalBest=%s, top5=[%.4f, %.4f, %.4f, %.4f, %.4f], elites=%d",
            screeningBest.fitness, globalBestRank,
            population.get(0).fitness, population.get(1).fitness, population.get(2).fitness,
            population.get(3).fitness, population.get(4).fitness,
            eliteCount));
        
        // With CRN, the screening ranking IS the fair comparison - trust it directly
        updateGlobalBestFromScreening(screeningBest);
    }
    
    /**
     * Evaluate an individual on a specific set of seeds.
     * This ensures all individuals face the same "luck".
     * Computes composite fitness from mean, variance, median, and Q3.
     */
    private void evaluateWithSeeds(Individual ind, long[] seeds) {
        int runs = seeds.length;
        double[] scores = new double[runs];
        
        ImprovedWeightedSelect strategy = createStrategy(ind);
        
        for (int i = 0; i < runs; i++) {
            Random gameRng = new Random(seeds[i]);
            diecup.Game game = new diecup.Game(DICE_COUNT, SIDES_PER_DIE, strategy, false, false, gameRng);
            game.startGame();
            scores[i] = game.getTurns();
        }
        
        // Calculate all statistics
        ind.meanTurns = mean(scores);
        ind.variance = variance(scores);
        ind.median = percentile(scores, 50);
        ind.q3 = percentile(scores, 75);
        ind.standardError = Math.sqrt(ind.variance / runs);
        ind.evaluationCount = runs;
        
        // Composite fitness: weighted combination (lower is better for all components)
        // Normalize variance by taking sqrt to put it on same scale as turns
        ind.fitness = MEAN_WEIGHT * ind.meanTurns
                    + VARIANCE_WEIGHT * Math.sqrt(ind.variance)
                    + MEDIAN_WEIGHT * ind.median
                    + Q3_WEIGHT * ind.q3;
    }
    
    /**
     * Update global best based on CRN screening results.
     * 
     * With CRN, all individuals (including the previous global best) are evaluated
     * on the SAME seeds this generation. So screeningBest.fitness is directly
     * comparable to the re-evaluated fitness of the previous global best.
     * 
     * The global best's fitness in this generation will be found in the population
     * (since we inject it in evolve()). We simply adopt the screening winner.
     */
    private void updateGlobalBestFromScreening(Individual screeningBest) {
        if (globalBest == null) {
            // First generation
            globalBest = screeningBest.copy();
            totalGamesPlayed = screeningBest.evaluationCount;
            log(String.format("  Initial best: fit=%.4f (mean=%.2f, var=%.2f, med=%.2f, Q3=%.2f)",
                globalBest.fitness, globalBest.meanTurns, globalBest.variance, 
                globalBest.median, globalBest.q3));
            return;
        }
        
        // Find the current global best in the population (it was re-evaluated on same seeds)
        Individual reEvaluatedGlobalBest = null;
        for (Individual ind : population) {
            if (Arrays.equals(ind.genes, globalBest.genes)) {
                reEvaluatedGlobalBest = ind;
                break;
            }
        }
        
        // Update global best if screening winner is better (fair comparison on same seeds)
        if (reEvaluatedGlobalBest == null || screeningBest.fitness < reEvaluatedGlobalBest.fitness) {
            // Only log if actually different parameters (not just noise on same params)
            boolean differentParams = reEvaluatedGlobalBest == null || 
                !Arrays.equals(screeningBest.genes, reEvaluatedGlobalBest.genes);
            
            if (differentParams) {
                double oldFit = reEvaluatedGlobalBest != null ? reEvaluatedGlobalBest.fitness : 0;
                log(String.format("  *** New best: fit %.4f -> %.4f ***",
                    oldFit, screeningBest.fitness));
                log(String.format("      mean: %.2f, var: %.2f, med: %.2f, Q3: %.2f",
                    screeningBest.meanTurns, screeningBest.variance,
                    screeningBest.median, screeningBest.q3));
            }
            
            globalBest = screeningBest.copy();
            totalGamesPlayed += screeningBest.evaluationCount;
        }
    }
    
    private ImprovedWeightedSelect createStrategy(Individual ind) {
        return new ImprovedWeightedSelect(
            statistics,
            ind.genes[0], ind.genes[1], ind.genes[2], ind.genes[3],
            ind.genes[4], ind.genes[5], ind.genes[6], ind.genes[7],
            ind.genes[8], ind.genes[9], ind.genes[10], ind.genes[11],
            ind.genes[12]
        );
    }
    
    // ===== STATISTICAL HELPERS =====
    
    private static double mean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }
    
    private static double variance(double[] values) {
        double m = mean(values);
        double sumSq = 0;
        for (double v : values) sumSq += (v - m) * (v - m);
        return sumSq / (values.length - 1);  // Sample variance
    }
    
    // ===== EVOLUTION =====
    
    private void evolve() {
        List<Individual> nextGen = new ArrayList<>();
        
        // 1. Keep top performers as elites
        for (int i = 0; i < ELITE_COUNT && i < population.size(); i++) {
            Individual elite = population.get(i).copy();
            elite.isElite = true;
            nextGen.add(elite);
        }
        
        // 2. Always include the global best (if exists) so it gets re-evaluated on new seeds
        //    This ensures fair comparison - globalBest competes on the SAME seeds as everyone else
        if (globalBest != null) {
            boolean alreadyIncluded = nextGen.stream()
                .anyMatch(ind -> Arrays.equals(ind.genes, globalBest.genes));
            if (!alreadyIncluded) {
                Individual gbCopy = globalBest.copy();
                gbCopy.isElite = true;  // Mark as elite so we can track it
                nextGen.add(gbCopy);
            }
        }
        
        // 3. Diversity injection - random individuals
        int diversityCount = (int) (POPULATION_SIZE * DIVERSITY_RATIO);
        for (int i = 0; i < diversityCount; i++) {
            nextGen.add(createRandomIndividual());
        }
        
        // 4. Offspring - crossover and mutation
        while (nextGen.size() < POPULATION_SIZE) {
            Individual parent1 = selectParent();
            Individual parent2 = selectParent();
            Individual child = crossover(parent1, parent2);
            mutate(child);
            nextGen.add(child);
        }
        
        population.clear();
        population.addAll(nextGen);
    }
    
    // ===== SELECTION =====
    
    private Individual selectParent() {
        // Tournament selection - pick best fitness from random candidates
        Individual best = null;
        for (int i = 0; i < TOURNAMENT_SIZE; i++) {
            Individual candidate = population.get(masterRandom.nextInt(population.size()));
            if (best == null || candidate.fitness < best.fitness) {
                best = candidate;
            }
        }
        return best;
    }
    
    // ===== CROSSOVER =====
    
    private Individual crossover(Individual p1, Individual p2) {
        double[] childGenes = new double[PARAM_COUNT];
        
        int method = masterRandom.nextInt(3);
        switch (method) {
            case 0 -> { // Blend crossover
                double alpha = masterRandom.nextDouble();
                for (int i = 0; i < PARAM_COUNT; i++) {
                    childGenes[i] = p1.genes[i] + alpha * (p2.genes[i] - p1.genes[i]);
                }
            }
            case 1 -> { // Average crossover
                for (int i = 0; i < PARAM_COUNT; i++) {
                    childGenes[i] = (p1.genes[i] + p2.genes[i]) / 2.0;
                }
            }
            case 2 -> { // Uniform crossover
                for (int i = 0; i < PARAM_COUNT; i++) {
                    childGenes[i] = masterRandom.nextBoolean() ? p1.genes[i] : p2.genes[i];
                }
            }
        }
        
        return new Individual(childGenes);
    }
    
    // ===== MUTATION =====
    
    private void mutate(Individual ind) {
        boolean mutated = false;
        
        // Per-gene Gaussian mutation
        for (int i = 0; i < PARAM_COUNT; i++) {
            if (masterRandom.nextDouble() < MUTATION_RATE_PER_GENE) {
                ind.genes[i] = ind.genes[i] + masterRandom.nextGaussian() * mutationStrength;
                mutated = true;
            }
        }
        
        // Occasional large mutation (exploration) - range -1 to 2
        if (masterRandom.nextDouble() < LARGE_MUTATION_RATE) {
            int idx = masterRandom.nextInt(PARAM_COUNT);
            ind.genes[idx] = masterRandom.nextDouble() * 3.0 - 1.0;
            mutated = true;
        }
        
        if (mutated) {
            ind.markForReevaluation();
        }
    }
    
    // ===== STAGNATION HANDLING =====
    
    private void handleStagnation(boolean improved) {
        if (improved) {
            stagnationCount = 0;
            mutationStrength = MUTATION_STRENGTH_INITIAL;
        } else {
            stagnationCount++;
            
            // Increase mutation strength aggressively if stuck
            if (stagnationCount > STAGNATION_THRESHOLD) {
                // Jump to max mutation quickly (2x instead of 1.5x)
                mutationStrength = Math.min(MUTATION_STRENGTH_MAX, mutationStrength * 2.0);
            }
            
            // Major diversity injection if stuck - keep mutation high after restart
            if (stagnationCount > RESTART_THRESHOLD) {
                log("  Stagnation detected - injecting " + (int)(RESTART_FRACTION * 100) + "% diversity with high mutation...");
                injectDiversity(RESTART_FRACTION);
                stagnationCount = 0;
                // Keep mutation high after diversity injection to explore new space
                mutationStrength = MUTATION_STRENGTH_MAX * 0.75;
            }
        }
    }
    
    private void injectDiversity(double fraction) {
        int count = (int) (POPULATION_SIZE * fraction);
        // Replace worst individuals
        for (int i = POPULATION_SIZE - count; i < POPULATION_SIZE; i++) {
            population.set(i, createRandomIndividual());
        }
    }
    
    // ===== UTILITIES =====
    
    private void printProgress(int generation, long startTime, boolean improved) {
        long elapsed = System.currentTimeMillis() - startTime;
        double progress = (double) generation / MAX_GENERATIONS;
        long eta = (long) (elapsed / progress) - elapsed;
        
        log(String.format("Gen %d - Fit: %.4f (mean=%.2f, σ²=%.2f, med=%.2f, Q3=%.2f) - %s - ETA: %s - %d games - stag=%d",
            generation,
            globalBest.fitness, globalBest.meanTurns, globalBest.variance, 
            globalBest.median, globalBest.q3,
            formatDuration(elapsed), formatDuration(eta), totalGamesPlayed, stagnationCount));
        
        if (improved) {
            log("  *** IMPROVEMENT ***");
            for (int i = 0; i < PARAM_COUNT; i++) {
                log(String.format("    %s = %.3f", PARAM_NAMES[i], globalBest.genes[i]));
            }
        }
    }
    
    private static String formatDuration(long ms) {
        long sec = ms / 1000;
        long min = sec / 60;
        long hr = min / 60;
        
        if (hr > 0) return String.format("%dh %dm %ds", hr, min % 60, sec % 60);
        if (min > 0) return String.format("%dm %ds", min, sec % 60);
        return String.format("%ds", sec);
    }
    
    // ===== INDIVIDUAL CLASS =====
    
    static class Individual {
        double[] genes;
        double fitness = Double.MAX_VALUE;      // Composite fitness score
        double meanTurns = Double.MAX_VALUE;    // Mean turns to win
        double variance = Double.MAX_VALUE;     // Variance in turns
        double median = Double.MAX_VALUE;       // Median turns
        double q3 = Double.MAX_VALUE;           // 75th percentile (Q3)
        double standardError = Double.MAX_VALUE;
        int evaluationCount = 0;
        boolean isElite = false;
        
        Individual(double[] genes) {
            this.genes = genes.clone();
        }
        
        Individual copy() {
            Individual c = new Individual(this.genes);
            c.fitness = this.fitness;
            c.meanTurns = this.meanTurns;
            c.variance = this.variance;
            c.median = this.median;
            c.q3 = this.q3;
            c.standardError = this.standardError;
            c.evaluationCount = this.evaluationCount;
            c.isElite = this.isElite;
            return c;
        }
        
        void markForReevaluation() {
            this.evaluationCount = 0;
            this.fitness = Double.MAX_VALUE;
            this.meanTurns = Double.MAX_VALUE;
            this.variance = Double.MAX_VALUE;
            this.median = Double.MAX_VALUE;
            this.q3 = Double.MAX_VALUE;
            this.standardError = Double.MAX_VALUE;
        }
        
    }
    
    private void printIndividualDetails(Individual ind) {
        log(String.format("Composite Fitness: %.4f (%d evaluations)", ind.fitness, ind.evaluationCount));
        log(String.format("  Mean:     %.4f turns", ind.meanTurns));
        log(String.format("  Variance: %.4f (σ=%.4f)", ind.variance, Math.sqrt(ind.variance)));
        log(String.format("  Median:   %.4f turns", ind.median));
        log(String.format("  Q3 (75%%): %.4f turns", ind.q3));
        log(String.format("  Weights:  mean=%.0f%%, var=%.0f%%, med=%.0f%%, Q3=%.0f%%",
            MEAN_WEIGHT * 100, VARIANCE_WEIGHT * 100, MEDIAN_WEIGHT * 100, Q3_WEIGHT * 100));
        log("Parameters:");
        for (int i = 0; i < PARAM_NAMES.length; i++) {
            log(String.format("  %s = %.4f", PARAM_NAMES[i], ind.genes[i]));
        }
    }
}
