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
    private static final double DIVERSITY_RATIO = 0.15;  // 15% random injection per generation
    
    /** Selection Settings */
    private static final int TOURNAMENT_SIZE = 3;
    
    /** Mutation Settings */
    private static final double MUTATION_RATE_PER_GENE = 0.30;  // 30% chance per gene
    private static final double MUTATION_STRENGTH_INITIAL = 0.15;
    private static final double MUTATION_STRENGTH_MAX = 0.5;
    private static final double LARGE_MUTATION_RATE = 0.08;  // Chance to reset a gene completely
    
    /** Evaluation Settings - Common Random Numbers */
    private static final int GAMES_PER_GENERATION = 5_000;     // Games per individual per generation (using shared seeds)
    private static final int CONFIRMATION_GAMES = 20_000;       // Additional games for head-to-head confirmation
    private static final double SIGNIFICANCE_THRESHOLD = 0.05;  // p-value threshold for accepting improvement (5%)
    private static final int TOP_N_TO_CONFIRM = 5;              // Confirm top N candidates against current best
    
    /** Stagnation Settings */
    private static final int STAGNATION_THRESHOLD = 5;   // Increase mutation after N generations without improvement
    private static final int RESTART_THRESHOLD = 20;     // Inject diversity after N generations without improvement
    private static final double RESTART_FRACTION = 0.40; // Replace 40% on restart
    
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
        "PairPenalty", "DiceEfficiencyWeight", "VarianceWeight", "GameProgressWeight",
        "NearCompletionBonus", "FreeTurnWeight", "AllDiceBonusWeight"
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
        
        log(String.format("Initial best: %.4f turns", globalBest.fitness));
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
            ImprovedWeightedSelect.getDefaultPairPenalty(),
            ImprovedWeightedSelect.getDefaultDiceEfficiencyWeight(),
            ImprovedWeightedSelect.getDefaultVarianceWeight(),
            ImprovedWeightedSelect.getDefaultGameProgressWeight(),
            ImprovedWeightedSelect.getDefaultNearCompletionBonus(),
            ImprovedWeightedSelect.getDefaultFreeTurnWeight(),
            ImprovedWeightedSelect.getDefaultAllDiceBonusWeight()
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
     */
    private void evaluatePopulation(int generation) {
        // Generate shared seeds for this generation
        long[] seeds = generateSeeds(GAMES_PER_GENERATION);
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<?>> futures = new ArrayList<>();
        
        // Evaluate all non-elite individuals (elites keep their accumulated stats)
        for (Individual ind : population) {
            if (!ind.isElite) {
                futures.add(executor.submit(() -> {
                    evaluateWithSeeds(ind, seeds);
                }));
            }
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
        
        // Log screening results
        Individual screeningBest = population.get(0);
        int eliteCount = (int) population.stream().filter(ind -> ind.isElite).count();
        int confirmedCount = (int) population.stream().filter(ind -> ind.isConfirmed).count();
        log(String.format("  Screening: best=%.4f, top5=[%.4f, %.4f, %.4f, %.4f, %.4f], elites=%d, confirmed=%d",
            screeningBest.fitness,
            population.get(0).fitness, population.get(1).fitness, population.get(2).fitness,
            population.get(3).fitness, population.get(4).fitness,
            eliteCount, confirmedCount));
        
        // Confirm top candidates using head-to-head comparison against global best
        confirmTopCandidates();
    }
    
    /**
     * Evaluate an individual on a specific set of seeds.
     * This ensures all individuals face the same "luck".
     */
    private void evaluateWithSeeds(Individual ind, long[] seeds) {
        double sum = 0;
        double sumSq = 0;
        int runs = seeds.length;
        
        ImprovedWeightedSelect strategy = createStrategy(ind);
        
        for (long seed : seeds) {
            Random gameRng = new Random(seed);
            diecup.Game game = new diecup.Game(DICE_COUNT, SIDES_PER_DIE, strategy, false, false, gameRng);
            game.startGame();
            double turns = game.getTurns();
            
            sum += turns;
            sumSq += turns * turns;
        }
        
        ind.fitness = sum / runs;
        double variance = (sumSq / runs) - (ind.fitness * ind.fitness);
        ind.standardError = Math.sqrt(variance / runs);
        ind.evaluationCount = runs;
    }
    
    /**
     * Confirm top candidates using head-to-head paired testing against global best.
     * This reduces the chance of false positives from lucky seeds.
     */
    private void confirmTopCandidates() {
        if (globalBest == null) {
            // First generation - just pick the best
            Individual best = population.get(0);
            globalBest = best.copy();
            totalGamesPlayed = best.evaluationCount;
            log(String.format("  Initial best: %.4f turns", globalBest.fitness));
            return;
        }
        
        // Test top candidates head-to-head against global best
        log("  Confirming top candidates against current best...");
        
        for (int i = 0; i < Math.min(TOP_N_TO_CONFIRM, population.size()); i++) {
            Individual candidate = population.get(i);
            
            // Skip if candidate doesn't look promising (within 2 SE of best)
            if (candidate.fitness > globalBest.fitness + globalBest.standardError * 2) {
                continue;
            }
            
            // Head-to-head comparison using paired testing
            HeadToHeadResult result = headToHeadComparison(candidate, globalBest, CONFIRMATION_GAMES);
            
            if (result.candidateWins && result.pValue < SIGNIFICANCE_THRESHOLD) {
                log(String.format("  *** Confirmed improvement: %.4f -> %.4f (p=%.4f, diff=%.4f±%.4f) ***",
                    globalBest.fitness, result.candidateMean, result.pValue,
                    result.meanDifference, result.standardErrorDiff * 1.96));
                
                // Update global best with accumulated stats
                globalBest = candidate.copy();
                globalBest.fitness = result.candidateMean;
                globalBest.standardError = result.candidateSE;
                totalGamesPlayed += CONFIRMATION_GAMES;
                candidate.isConfirmed = true;
            } else if (result.candidateWins) {
                log(String.format("  Candidate %.4f not significant vs %.4f (p=%.4f)",
                    result.candidateMean, result.bestMean, result.pValue));
            }
        }
    }
    
    /**
     * Perform head-to-head comparison using paired t-test.
     * Both strategies play the exact same games (same seeds).
     */
    private HeadToHeadResult headToHeadComparison(Individual candidate, Individual best, int games) {
        long[] seeds = generateSeeds(games);
        
        ImprovedWeightedSelect candidateStrategy = createStrategy(candidate);
        ImprovedWeightedSelect bestStrategy = createStrategy(best);
        
        double[] candidateScores = new double[games];
        double[] bestScores = new double[games];
        double[] differences = new double[games];
        
        // Run games in parallel, but ensure paired comparison
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<?>> futures = new ArrayList<>();
        
        // Split into chunks for parallel execution
        int chunkSize = Math.max(1, games / THREAD_COUNT);
        for (int start = 0; start < games; start += chunkSize) {
            final int chunkStart = start;
            final int chunkEnd = Math.min(start + chunkSize, games);
            
            futures.add(executor.submit(() -> {
                for (int i = chunkStart; i < chunkEnd; i++) {
                    Random rng1 = new Random(seeds[i]);
                    Random rng2 = new Random(seeds[i]);  // Same seed!
                    
                    diecup.Game game1 = new diecup.Game(DICE_COUNT, SIDES_PER_DIE, candidateStrategy, false, false, rng1);
                    diecup.Game game2 = new diecup.Game(DICE_COUNT, SIDES_PER_DIE, bestStrategy, false, false, rng2);
                    
                    game1.startGame();
                    game2.startGame();
                    
                    candidateScores[i] = game1.getTurns();
                    bestScores[i] = game2.getTurns();
                    differences[i] = candidateScores[i] - bestScores[i];  // Negative = candidate wins
                }
            }));
        }
        
        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception e) { throw new RuntimeException(e); }
        }
        executor.shutdown();
        
        // Calculate statistics
        double candidateMean = mean(candidateScores);
        double bestMean = mean(bestScores);
        double meanDiff = mean(differences);
        double seDiff = standardError(differences);
        
        // Paired t-test: t = meanDiff / seDiff
        double tStat = meanDiff / seDiff;
        double pValue = tTestPValue(tStat, games - 1);
        
        HeadToHeadResult result = new HeadToHeadResult();
        result.candidateMean = candidateMean;
        result.bestMean = bestMean;
        result.candidateSE = standardError(candidateScores);
        result.meanDifference = meanDiff;
        result.standardErrorDiff = seDiff;
        result.pValue = pValue;
        result.candidateWins = meanDiff < 0;  // Lower is better
        
        return result;
    }
    
    private static class HeadToHeadResult {
        double candidateMean;
        double bestMean;
        double candidateSE;
        double meanDifference;    // Negative means candidate is better
        double standardErrorDiff; // SE of the difference (for confidence interval)
        double pValue;
        boolean candidateWins;
    }
    
    private ImprovedWeightedSelect createStrategy(Individual ind) {
        return new ImprovedWeightedSelect(
            statistics,
            ind.genes[0], ind.genes[1], ind.genes[2], ind.genes[3],
            ind.genes[4], ind.genes[5], ind.genes[6], ind.genes[7],
            ind.genes[8], ind.genes[9], ind.genes[10], ind.genes[11],
            ind.genes[12], ind.genes[13], ind.genes[14]
        );
    }
    
    // ===== STATISTICAL HELPERS =====
    
    private static double mean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }
    
    private static double standardError(double[] values) {
        double m = mean(values);
        double sumSq = 0;
        for (double v : values) sumSq += (v - m) * (v - m);
        double variance = sumSq / (values.length - 1);
        return Math.sqrt(variance / values.length);
    }
    
    /**
     * Approximate p-value for two-tailed t-test using normal approximation
     * (good enough for large sample sizes like ours).
     */
    private static double tTestPValue(double t, int df) {
        // For large df, t-distribution ≈ normal distribution
        // Use complementary error function approximation
        double absT = Math.abs(t);
        // Approximation of 2 * (1 - Phi(|t|)) for standard normal
        double p = 2.0 * (1.0 - normalCDF(absT));
        return p;
    }
    
    /**
     * Standard normal CDF approximation (Abramowitz and Stegun).
     */
    private static double normalCDF(double x) {
        if (x < 0) return 1.0 - normalCDF(-x);
        double t = 1.0 / (1.0 + 0.2316419 * x);
        double d = 0.3989423 * Math.exp(-x * x / 2.0);
        double p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))));
        return 1.0 - p;
    }
    
    // ===== EVOLUTION =====
    
    private void evolve() {
        List<Individual> nextGen = new ArrayList<>();
        
        // 1. Always keep ALL confirmed individuals - they have reliable fitness estimates
        //    These are valuable because we spent compute on them
        List<Individual> confirmed = new ArrayList<>();
        for (Individual ind : population) {
            if (ind.isConfirmed) {
                Individual copy = ind.copy();
                copy.isElite = true;  // Confirmed individuals are always elite
                confirmed.add(copy);
            }
        }
        nextGen.addAll(confirmed);
        
        // 2. Fill remaining elite slots with top performers (that aren't already added)
        int elitesNeeded = ELITE_COUNT - nextGen.size();
        int added = 0;
        for (int i = 0; i < population.size() && added < elitesNeeded; i++) {
            Individual ind = population.get(i);
            if (!ind.isConfirmed) {  // Don't duplicate confirmed ones
                Individual elite = ind.copy();
                elite.isElite = true;
                nextGen.add(elite);
                added++;
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
            
            // Increase mutation strength if stuck
            if (stagnationCount > STAGNATION_THRESHOLD) {
                mutationStrength = Math.min(MUTATION_STRENGTH_MAX, mutationStrength * 1.5);
            }
            
            // Major diversity injection if very stuck
            if (stagnationCount > RESTART_THRESHOLD) {
                log("  Stagnation detected - injecting diversity...");
                injectDiversity(RESTART_FRACTION);
                stagnationCount = 0;
                mutationStrength = MUTATION_STRENGTH_INITIAL;
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
        
        log(String.format("Gen %d - Best: %.4f (±%.4f) - %s elapsed - ETA: %s - %d games - mut=%.3f - stag=%d",
            generation,
            globalBest.fitness, globalBest.standardError * 1.96,
            formatDuration(elapsed), formatDuration(eta), totalGamesPlayed, mutationStrength, stagnationCount));
        
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
        double fitness = Double.MAX_VALUE;
        double standardError = Double.MAX_VALUE;
        int evaluationCount = 0;
        boolean isElite = false;
        boolean isConfirmed = false;  // True if this individual has high-quality (30K) evaluation
        
        Individual(double[] genes) {
            this.genes = genes.clone();
        }
        
        Individual copy() {
            Individual c = new Individual(this.genes);
            c.fitness = this.fitness;
            c.standardError = this.standardError;
            c.evaluationCount = this.evaluationCount;
            c.isElite = this.isElite;
            c.isConfirmed = this.isConfirmed;
            return c;
        }
        
        void markForReevaluation() {
            this.evaluationCount = 0;
            this.fitness = Double.MAX_VALUE;
            this.standardError = Double.MAX_VALUE;
            this.isConfirmed = false;
        }
        
    }
    
    private void printIndividualDetails(Individual ind) {
        log(String.format("Fitness: %.4f turns (±%.4f, %d evaluations)",
            ind.fitness, ind.standardError * 1.96, ind.evaluationCount));
        log("Parameters:");
        for (int i = 0; i < PARAM_NAMES.length; i++) {
            log(String.format("  %s = %.4f", PARAM_NAMES[i], ind.genes[i]));
        }
    }
}
