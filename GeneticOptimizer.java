import java.util.*;
import java.util.concurrent.*;

import diecup.Statistics;
import strategies.ImprovedWeightedSelect;

/**
 * Genetic Algorithm optimizer for tuning strategy parameters.
 * 
 * Features:
 * - Tournament selection with elitism
 * - Multiple crossover strategies (blend, average, uniform)
 * - Per-gene Gaussian mutation with adaptive strength
 * - Confirmation evaluation to reduce noise in best selection
 * - Periodic elite re-evaluation to prevent stale estimates
 * - Diversity injection and stagnation detection
 */
public class GeneticOptimizer {

    // ===== CONFIGURATION =====
    
    /** GA Population and Generation Settings */
    private static final int POPULATION_SIZE = 500;
    private static final int MAX_GENERATIONS = 50;
    private static final int ELITE_COUNT = 20;
    private static final double DIVERSITY_RATIO = 0.15;  // 15% random injection per generation
    
    /** Selection Settings */
    private static final int TOURNAMENT_SIZE = 3;
    
    /** Mutation Settings */
    private static final double MUTATION_RATE_PER_GENE = 0.30;  // 30% chance per gene
    private static final double MUTATION_STRENGTH_INITIAL = 0.15;
    private static final double MUTATION_STRENGTH_MAX = 0.5;
    private static final double LARGE_MUTATION_RATE = 0.08;  // Chance to reset a gene completely
    
    /** Evaluation Settings */
    private static final int INITIAL_EVALUATIONS = 10_000;
    private static final int CONFIRMATION_EVALUATIONS = 20_000;
    private static final int ELITE_REEVALUATION_INTERVAL = 5;  // Re-evaluate elites every N generations
    private static final int TOP_N_TO_CONFIRM = 10;  // Confirm top N candidates - confirmation is cheap (~1s each)
    
    /** Stagnation Settings */
    private static final int STAGNATION_THRESHOLD = 8;   // Increase mutation after N generations without improvement
    private static final int RESTART_THRESHOLD = 15;     // Inject diversity after N generations without improvement
    private static final double RESTART_FRACTION = 0.40; // Replace 40% on restart
    
    /** Threading */
    private static final int THREAD_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    
    /** Game Configuration */
    private static final int DICE_COUNT = 6;
    private static final int SIDES_PER_DIE = 6;
    
    /** Parameter Names (for display) */
    private static final String[] PARAM_NAMES = {
        "OpportunityWeight", "RarityWeight", "ProgressWeight", "RarityScalar",
        "CollectionWeight", "CollectionScalar", "CompletionWeight", "CatchUpWeight"
    };
    private static final int PARAM_COUNT = PARAM_NAMES.length;
    
    // ===== INSTANCE FIELDS =====
    
    private final Random random = new Random();
    private final Statistics statistics;
    private final List<Individual> population = new ArrayList<>();
    
    private Individual globalBest = null;
    private int stagnationCount = 0;
    private double mutationStrength = MUTATION_STRENGTH_INITIAL;
    
    // ===== MAIN =====
    
    public static void main(String[] args) {
        System.out.println("=== Genetic Optimizer for ImprovedWeightedSelect ===\n");
        
        GeneticOptimizer optimizer = new GeneticOptimizer();
        Individual best = optimizer.run();
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("OPTIMIZATION COMPLETE");
        System.out.println("=".repeat(60));
        best.printDetails();
        System.out.println("=".repeat(60));
    }
    
    // ===== CONSTRUCTOR =====
    
    public GeneticOptimizer() {
        this.statistics = new Statistics(DICE_COUNT, SIDES_PER_DIE);
    }
    
    // ===== MAIN OPTIMIZATION LOOP =====
    
    public Individual run() {
        long startTime = System.currentTimeMillis();
        
        // Initialize
        initializePopulation();
        evaluatePopulation(0);
        
        System.out.printf("Initial best: %.4f turns%n%n", globalBest.fitness);
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
        
        System.out.printf("%nTotal time: %s%n", formatDuration(System.currentTimeMillis() - startTime));
        return globalBest;
    }
    
    // ===== POPULATION MANAGEMENT =====
    
    private void initializePopulation() {
        System.out.println("Initializing population...");
        
        // Seed with known good parameters from strategy defaults
        double[] knownGood = {
            ImprovedWeightedSelect.getDefaultOpportunityWeight(),
            ImprovedWeightedSelect.getDefaultRarityWeight(),
            ImprovedWeightedSelect.getDefaultProgressWeight(),
            ImprovedWeightedSelect.getDefaultRarityScalar(),
            ImprovedWeightedSelect.getDefaultCollectionWeight(),
            ImprovedWeightedSelect.getDefaultCollectionScalar(),
            ImprovedWeightedSelect.getDefaultCompletionWeight(),
            ImprovedWeightedSelect.getDefaultCatchUpWeight()
        };
        population.add(new Individual(knownGood));
        System.out.println("Seeded with known good parameters from strategy defaults");
        
        // Fill rest with random individuals
        for (int i = 1; i < POPULATION_SIZE; i++) {
            population.add(createRandomIndividual());
        }
    }
    
    private Individual createRandomIndividual() {
        double[] genes = new double[PARAM_COUNT];
        for (int i = 0; i < PARAM_COUNT; i++) {
            genes[i] = random.nextDouble();
        }
        return new Individual(genes);
    }
    
    // ===== EVALUATION =====
    
    private void evaluatePopulation(int generation) {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<?>> futures = new ArrayList<>();
        
        // Determine which individuals need evaluation
        boolean reEvaluateElites = (generation % ELITE_REEVALUATION_INTERVAL == 0) && generation > 0;
        
        for (Individual ind : population) {
            boolean needsEval = ind.evaluationCount == 0 || (reEvaluateElites && ind.isElite);
            if (needsEval) {
                futures.add(executor.submit(() -> {
                    evaluate(ind, INITIAL_EVALUATIONS);
                    ind.isElite = false;
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
        
        // Confirm best candidate if promising
        confirmBestCandidate();
    }
    
    private void confirmBestCandidate() {
        // Confirm top N candidates to reduce false negatives
        // A good candidate might have gotten unlucky in initial evaluation
        for (int i = 0; i < Math.min(TOP_N_TO_CONFIRM, population.size()); i++) {
            Individual candidate = population.get(i);
            
            // Should we confirm this candidate?
            boolean shouldConfirm = globalBest == null || 
                candidate.fitness < globalBest.fitness + globalBest.standardError * 2;  // Wider threshold
            
            boolean needsConfirmation = candidate.evaluationCount < INITIAL_EVALUATIONS + CONFIRMATION_EVALUATIONS;
            
            if (shouldConfirm && needsConfirmation) {
                if (i == 0) System.out.println("  Confirming top candidates...");
                
                // Run additional evaluations and combine
                double oldFitness = candidate.fitness;
                int oldCount = candidate.evaluationCount;
                
                evaluate(candidate, CONFIRMATION_EVALUATIONS);
                
                // Weighted average
                double newFitness = (oldFitness * oldCount + candidate.fitness * CONFIRMATION_EVALUATIONS) 
                        / (oldCount + CONFIRMATION_EVALUATIONS);
                candidate.fitness = newFitness;
                candidate.evaluationCount = oldCount + CONFIRMATION_EVALUATIONS;
                candidate.standardError *= Math.sqrt((double) oldCount / candidate.evaluationCount);
                candidate.isConfirmed = true;  // Mark as having high-quality estimate
            }
        }
        
        // Re-sort after all confirmations
        population.sort(Comparator.comparingDouble(ind -> ind.fitness));
        
        // Update global best - check the top candidate after sorting
        Individual candidate = population.get(0);
        boolean isFullyConfirmed = candidate.evaluationCount >= INITIAL_EVALUATIONS + CONFIRMATION_EVALUATIONS;
        
        if (isFullyConfirmed) {
            if (globalBest == null || candidate.fitness < globalBest.fitness) {
                double improvement = globalBest != null ? globalBest.fitness - candidate.fitness : 0;
                globalBest = candidate.copy();
                System.out.printf("  New best: %.4f (improved by %.4f)%n", candidate.fitness, improvement);
            } else {
                System.out.printf("  Best confirmed: %.4f (current best: %.4f)%n", 
                    candidate.fitness, globalBest.fitness);
            }
        } else if (globalBest == null) {
            globalBest = candidate.copy();
            System.out.printf("  Initial best (pending confirmation): %.4f%n", candidate.fitness);
        }
    }
    
    private void evaluate(Individual ind, int runs) {
        double sum = 0;
        double sumSq = 0;
        
        for (int i = 0; i < runs; i++) {
            ImprovedWeightedSelect strategy = new ImprovedWeightedSelect(
                statistics,
                ind.genes[0], ind.genes[1], ind.genes[2], ind.genes[3],
                ind.genes[4], ind.genes[5], ind.genes[6], ind.genes[7]
            );
            
            diecup.Game game = new diecup.Game(DICE_COUNT, SIDES_PER_DIE, strategy, false, false);
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
        // Tournament selection with preference for confirmed individuals
        // Confirmed individuals have reliable fitness, so we trust their rankings more
        Individual best = null;
        for (int i = 0; i < TOURNAMENT_SIZE; i++) {
            Individual candidate = population.get(random.nextInt(population.size()));
            if (best == null) {
                best = candidate;
            } else if (candidate.isConfirmed && !best.isConfirmed) {
                // Prefer confirmed over unconfirmed (more reliable estimate)
                best = candidate;
            } else if (candidate.isConfirmed == best.isConfirmed && candidate.fitness < best.fitness) {
                // Same confirmation status - pick better fitness
                best = candidate;
            }
        }
        return best;
    }
    
    // ===== CROSSOVER =====
    
    private Individual crossover(Individual p1, Individual p2) {
        double[] childGenes = new double[PARAM_COUNT];
        
        int method = random.nextInt(3);
        switch (method) {
            case 0 -> { // Blend crossover
                double alpha = random.nextDouble();
                for (int i = 0; i < PARAM_COUNT; i++) {
                    childGenes[i] = clamp(p1.genes[i] + alpha * (p2.genes[i] - p1.genes[i]));
                }
            }
            case 1 -> { // Average crossover
                for (int i = 0; i < PARAM_COUNT; i++) {
                    childGenes[i] = (p1.genes[i] + p2.genes[i]) / 2.0;
                }
            }
            case 2 -> { // Uniform crossover
                for (int i = 0; i < PARAM_COUNT; i++) {
                    childGenes[i] = random.nextBoolean() ? p1.genes[i] : p2.genes[i];
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
            if (random.nextDouble() < MUTATION_RATE_PER_GENE) {
                ind.genes[i] = clamp(ind.genes[i] + random.nextGaussian() * mutationStrength);
                mutated = true;
            }
        }
        
        // Occasional large mutation (exploration)
        if (random.nextDouble() < LARGE_MUTATION_RATE) {
            int idx = random.nextInt(PARAM_COUNT);
            ind.genes[idx] = random.nextDouble();
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
                System.out.println("  Stagnation detected - injecting diversity...");
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
    
    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
    
    private void printProgress(int generation, long startTime, boolean improved) {
        long elapsed = System.currentTimeMillis() - startTime;
        double progress = (double) generation / MAX_GENERATIONS;
        long eta = (long) (elapsed / progress) - elapsed;
        
        System.out.printf("Gen %d/%d (%.0f%%) - Best: %.4f (±%.4f) - %s elapsed, ETA %s%n",
            generation, MAX_GENERATIONS, progress * 100,
            globalBest.fitness, globalBest.standardError * 1.96,
            formatDuration(elapsed), formatDuration(eta));
        
        if (improved) {
            System.out.println("  *** IMPROVEMENT ***");
            for (int i = 0; i < PARAM_COUNT; i++) {
                System.out.printf("    %s = %.3f%n", PARAM_NAMES[i], globalBest.genes[i]);
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
        
        void printDetails() {
            System.out.printf("Fitness: %.4f turns (±%.4f, %d evaluations)%n",
                fitness, standardError * 1.96, evaluationCount);
            System.out.println("Parameters:");
            for (int i = 0; i < PARAM_NAMES.length; i++) {
                System.out.printf("  %s = %.4f%n", PARAM_NAMES[i], genes[i]);
            }
        }
    }
}
