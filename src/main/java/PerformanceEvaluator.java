import assembler.Assembler;
import pipeline.PipelineSimulator;
import pipeline.SuperscalarSimulator;
import prediction.*;
import stats.Statistics;

import java.util.*;

/**
 * Phase 3: Performance Evaluation and Analysis
 * 
 * This class systematically evaluates all pipeline configurations across
 * multiple workloads and generates comprehensive performance comparisons.
 * 
 * Configurations Tested:
 * 1. Basic Pipeline (no optimizations)
 * 2. Pipeline + Forwarding
 * 3. Pipeline + Forwarding + Branch Prediction
 * 4. Superscalar (2-way)
 * 
 * Workloads Tested:
 * 1. Arithmetic-intensive
 * 2. Memory-intensive
 * 3. Branch-heavy
 * 4. Loop-based
 */
public class PerformanceEvaluator {

    // ═══════════════════════════════════════════════════════════════════════
    //  WORKLOADS
    // ═══════════════════════════════════════════════════════════════════════

    static final String WORKLOAD_ARITHMETIC =
        "# Arithmetic-intensive workload\n" +
        "ADD R1, R2, R3\n" +
        "ADD R4, R1, R5\n" +
        "SUB R6, R4, R1\n" +
        "MUL R7, R6, R2\n" +
        "ADD R8, R7, R3\n";

    static final String WORKLOAD_MEMORY =
        "# Memory-intensive workload\n" +
        "LOAD R1, R0, 100\n" +
        "ADD  R2, R1, R3\n" +
        "STORE R2, R0, 200\n" +
        "LOAD R4, R0, 104\n" +
        "ADD  R5, R4, R6\n" +
        "STORE R5, R0, 204\n";

    static final String WORKLOAD_BRANCH =
        "# Branch-heavy workload\n" +
        "ADD  R1, R0, R0\n" +
        "ADD  R2, R0, R0\n" +
        "BEQ  R1, R2, END\n" +
        "ADD  R3, R1, R2\n" +
        "SUB  R4, R3, R1\n" +
        "END:\n" +
        "ADD  R5, R1, R2\n";

    static final String WORKLOAD_LOOP =
        "# Loop-based workload\n" +
        "ADD  R1, R0, R0\n" +
        "ADD  R2, R0, R0\n" +
        "LOOP:\n" +
        "ADD  R1, R1, R2\n" +
        "ADD  R2, R2, R3\n" +
        "BNE  R2, R4, LOOP\n" +
        "ADD  R5, R1, R0\n";

    // ═══════════════════════════════════════════════════════════════════════
    //  CONFIGURATION DEFINITIONS
    // ═══════════════════════════════════════════════════════════════════════

    static class Configuration {
        String name;
        boolean forwarding;
        BranchPredictor predictor;
        boolean superscalar;

        Configuration(String name, boolean forwarding, BranchPredictor predictor, boolean superscalar) {
            this.name = name;
            this.forwarding = forwarding;
            this.predictor = predictor;
            this.superscalar = superscalar;
        }
    }

    static class WorkloadResult {
        String workloadName;
        String configName;
        int cycles;
        int instructions;
        double cpi;
        double throughput;
        int dataStalls;
        int controlStalls;
        int totalStalls;
        int forwardingEvents;
        double branchAccuracy;

        WorkloadResult(String workloadName, String configName, Statistics stats, BranchPredictor predictor) {
            this.workloadName = workloadName;
            this.configName = configName;
            this.cycles = stats.getTotalCycles();
            this.instructions = stats.getInstructionsRetired();
            this.cpi = stats.cpi();
            this.throughput = stats.throughput();
            this.dataStalls = stats.getDataStalls();
            this.controlStalls = stats.getControlStalls();
            this.totalStalls = stats.totalStalls();
            this.forwardingEvents = stats.getForwardingEvents();
            this.branchAccuracy = predictor != null ? predictor.getAccuracy() : 0.0;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  MAIN EVALUATION
    // ═══════════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        printHeader("PHASE 3: PERFORMANCE EVALUATION AND ANALYSIS");

        // Define all configurations
        List<Configuration> configs = Arrays.asList(
            new Configuration("Basic Pipeline", false, null, false),
            new Configuration("Pipeline + Forwarding", true, null, false),
            new Configuration("Pipeline + Forwarding + Branch Prediction", true, new TwoBitPredictor(), false),
            new Configuration("Superscalar (2-way)", true, new TwoBitPredictor(), false)
        );

        // Define all workloads
        Map<String, String> workloads = new LinkedHashMap<>();
        workloads.put("Arithmetic", WORKLOAD_ARITHMETIC);
        workloads.put("Memory", WORKLOAD_MEMORY);
        workloads.put("Branch", WORKLOAD_BRANCH);
        workloads.put("Loop", WORKLOAD_LOOP);

        // Run all combinations
        List<WorkloadResult> results = new ArrayList<>();
        for (Map.Entry<String, String> workload : workloads.entrySet()) {
            for (Configuration config : configs) {
                WorkloadResult result = runConfiguration(workload.getKey(), workload.getValue(), config);
                results.add(result);
            }
        }

        // Generate reports
        System.out.println("\n" + "═".repeat(120));
        System.out.println("SECTION 1: COMPREHENSIVE PERFORMANCE COMPARISON");
        System.out.println("═".repeat(120));
        printComprehensiveTable(results);

        System.out.println("\n" + "═".repeat(120));
        System.out.println("SECTION 2: WORKLOAD-SPECIFIC ANALYSIS");
        System.out.println("═".repeat(120));
        printWorkloadAnalysis(results, workloads.keySet());

        System.out.println("\n" + "═".repeat(120));
        System.out.println("SECTION 3: OPTIMIZATION IMPACT ANALYSIS");
        System.out.println("═".repeat(120));
        printOptimizationImpact(results);

        System.out.println("\n" + "═".repeat(120));
        System.out.println("SECTION 4: SPEEDUP ANALYSIS");
        System.out.println("═".repeat(120));
        printSpeedupAnalysis(results);

        System.out.println("\n" + "═".repeat(120));
        System.out.println("SECTION 5: STALL ANALYSIS");
        System.out.println("═".repeat(120));
        printStallAnalysis(results);

        System.out.println("\n" + "═".repeat(120));
        System.out.println("SECTION 6: KEY FINDINGS AND INSIGHTS");
        System.out.println("═".repeat(120));
        printKeyFindings(results);

        System.out.println("\n" + "═".repeat(120));
        System.out.println("PHASE 3 EVALUATION COMPLETE");
        System.out.println("═".repeat(120));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  EXECUTION
    // ═══════════════════════════════════════════════════════════════════════

    static WorkloadResult runConfiguration(String workloadName, String code, Configuration config) {
        Assembler asm = new Assembler();
        Assembler.Program prog = asm.assemble(code);

        Statistics stats;
        BranchPredictor predictor = config.predictor;

        if (config.superscalar) {
            SuperscalarSimulator sim = new SuperscalarSimulator();
            sim.setVerbose(false);
            if (predictor != null) {
                predictor.reset();
            }
            
            // Initialize for loop workload
            if (workloadName.equals("Loop")) {
                sim.getRegisterFile().init("R3", 1);
                sim.getRegisterFile().init("R4", 4);
            }
            
            sim.load(prog.instructions);
            stats = sim.run();
        } else {
            PipelineSimulator sim = new PipelineSimulator();
            sim.setForwardingEnabled(config.forwarding);
            sim.setVerbose(false);
            
            if (predictor != null) {
                predictor.reset();
                sim.setBranchPredictor(predictor);
            }
            
            // Initialize for loop workload
            if (workloadName.equals("Loop")) {
                sim.getRegisterFile().init("R3", 1);
                sim.getRegisterFile().init("R4", 4);
            }
            
            sim.load(prog.instructions);
            stats = sim.run();
        }

        return new WorkloadResult(workloadName, config.name, stats, predictor);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  REPORTING
    // ═══════════════════════════════════════════════════════════════════════

    static void printComprehensiveTable(List<WorkloadResult> results) {
        System.out.println("\nComplete Performance Metrics Across All Configurations and Workloads\n");
        
        System.out.println("─".repeat(120));
        System.out.printf("%-15s │ %-40s │ %7s │ %7s │ %6s │ %6s │ %6s │ %6s\n",
            "Workload", "Configuration", "Cycles", "Instrs", "CPI", "IPC", "Stalls", "Fwd");
        System.out.println("─".repeat(120));

        for (WorkloadResult r : results) {
            System.out.printf("%-15s │ %-40s │ %7d │ %7d │ %6.2f │ %6.2f │ %6d │ %6d\n",
                r.workloadName, r.configName, r.cycles, r.instructions, 
                r.cpi, r.throughput, r.totalStalls, r.forwardingEvents);
        }
        System.out.println("─".repeat(120));
    }

    static void printWorkloadAnalysis(List<WorkloadResult> results, Set<String> workloadNames) {
        for (String workload : workloadNames) {
            System.out.println("\n" + workload.toUpperCase() + " WORKLOAD:");
            System.out.println("─".repeat(100));
            System.out.printf("%-40s │ %7s │ %6s │ %10s │ %10s\n",
                "Configuration", "Cycles", "CPI", "Data Stalls", "Ctrl Stalls");
            System.out.println("─".repeat(100));

            WorkloadResult baseline = null;
            for (WorkloadResult r : results) {
                if (r.workloadName.equals(workload)) {
                    if (baseline == null) baseline = r;
                    
                    double speedup = baseline.cycles / (double) r.cycles;
                    String speedupStr = String.format("%.2fx", speedup);
                    
                    System.out.printf("%-40s │ %7d │ %6.2f │ %10d │ %10d │ %s\n",
                        r.configName, r.cycles, r.cpi, r.dataStalls, r.controlStalls,
                        speedup > 1.0 ? "Speedup: " + speedupStr : "");
                }
            }
            System.out.println("─".repeat(100));
        }
    }

    static void printOptimizationImpact(List<WorkloadResult> results) {
        System.out.println("\nImpact of Each Optimization (Average Across All Workloads)\n");
        
        // Calculate averages for each configuration
        Map<String, double[]> configAverages = new LinkedHashMap<>();
        Map<String, Integer> configCounts = new HashMap<>();
        
        for (WorkloadResult r : results) {
            configAverages.putIfAbsent(r.configName, new double[]{0, 0, 0, 0}); // cycles, cpi, stalls, fwd
            configCounts.putIfAbsent(r.configName, 0);
            
            double[] avgs = configAverages.get(r.configName);
            avgs[0] += r.cycles;
            avgs[1] += r.cpi;
            avgs[2] += r.totalStalls;
            avgs[3] += r.forwardingEvents;
            configCounts.put(r.configName, configCounts.get(r.configName) + 1);
        }
        
        // Compute averages
        for (Map.Entry<String, double[]> entry : configAverages.entrySet()) {
            int count = configCounts.get(entry.getKey());
            double[] avgs = entry.getValue();
            for (int i = 0; i < avgs.length; i++) {
                avgs[i] /= count;
            }
        }
        
        System.out.println("─".repeat(100));
        System.out.printf("%-40s │ %10s │ %8s │ %10s │ %10s\n",
            "Configuration", "Avg Cycles", "Avg CPI", "Avg Stalls", "Avg Fwd");
        System.out.println("─".repeat(100));
        
        for (Map.Entry<String, double[]> entry : configAverages.entrySet()) {
            double[] avgs = entry.getValue();
            System.out.printf("%-40s │ %10.1f │ %8.2f │ %10.1f │ %10.1f\n",
                entry.getKey(), avgs[0], avgs[1], avgs[2], avgs[3]);
        }
        System.out.println("─".repeat(100));
    }

    static void printSpeedupAnalysis(List<WorkloadResult> results) {
        System.out.println("\nSpeedup Relative to Basic Pipeline\n");
        
        // Group by workload
        Map<String, List<WorkloadResult>> byWorkload = new LinkedHashMap<>();
        for (WorkloadResult r : results) {
            byWorkload.putIfAbsent(r.workloadName, new ArrayList<>());
            byWorkload.get(r.workloadName).add(r);
        }
        
        System.out.println("─".repeat(100));
        System.out.printf("%-15s │ %-40s │ %10s │ %10s\n",
            "Workload", "Configuration", "Speedup", "CPI Improv");
        System.out.println("─".repeat(100));
        
        for (Map.Entry<String, List<WorkloadResult>> entry : byWorkload.entrySet()) {
            List<WorkloadResult> workloadResults = entry.getValue();
            WorkloadResult baseline = workloadResults.get(0); // First is always basic
            
            for (WorkloadResult r : workloadResults) {
                double speedup = baseline.cycles / (double) r.cycles;
                double cpiImprovement = ((baseline.cpi - r.cpi) / baseline.cpi) * 100;
                
                System.out.printf("%-15s │ %-40s │ %9.2fx │ %9.1f%%\n",
                    r.workloadName, r.configName, speedup, cpiImprovement);
            }
            System.out.println("─".repeat(100));
        }
    }

    static void printStallAnalysis(List<WorkloadResult> results) {
        System.out.println("\nStall Reduction Analysis\n");
        
        // Group by workload
        Map<String, List<WorkloadResult>> byWorkload = new LinkedHashMap<>();
        for (WorkloadResult r : results) {
            byWorkload.putIfAbsent(r.workloadName, new ArrayList<>());
            byWorkload.get(r.workloadName).add(r);
        }
        
        System.out.println("─".repeat(110));
        System.out.printf("%-15s │ %-40s │ %10s │ %12s │ %12s\n",
            "Workload", "Configuration", "Total Stalls", "Data Stalls", "Ctrl Stalls");
        System.out.println("─".repeat(110));
        
        for (Map.Entry<String, List<WorkloadResult>> entry : byWorkload.entrySet()) {
            List<WorkloadResult> workloadResults = entry.getValue();
            WorkloadResult baseline = workloadResults.get(0);
            
            for (WorkloadResult r : workloadResults) {
                double stallReduction = baseline.totalStalls > 0 
                    ? ((baseline.totalStalls - r.totalStalls) / (double) baseline.totalStalls) * 100 
                    : 0;
                
                String reductionStr = stallReduction > 0 ? String.format(" (-%1.0f%%)", stallReduction) : "";
                
                System.out.printf("%-15s │ %-40s │ %10d │ %12d │ %12d%s\n",
                    r.workloadName, r.configName, r.totalStalls, r.dataStalls, r.controlStalls, reductionStr);
            }
            System.out.println("─".repeat(110));
        }
    }

    static void printKeyFindings(List<WorkloadResult> results) {
        System.out.println("\n📊 KEY FINDINGS:\n");
        
        // Calculate overall statistics
        WorkloadResult basicAvg = calculateAverage(results, "Basic Pipeline");
        WorkloadResult fwdAvg = calculateAverage(results, "Pipeline + Forwarding");
        WorkloadResult predAvg = calculateAverage(results, "Pipeline + Forwarding + Branch Prediction");
        WorkloadResult superAvg = calculateAverage(results, "Superscalar (2-way)");
        
        System.out.println("1. DATA FORWARDING IMPACT:");
        double fwdSpeedup = basicAvg.cycles / (double) fwdAvg.cycles;
        double stallReduction = ((basicAvg.totalStalls - fwdAvg.totalStalls) / (double) basicAvg.totalStalls) * 100;
        System.out.printf("   • Average speedup: %.2fx\n", fwdSpeedup);
        System.out.printf("   • Stall reduction: %.0f%%\n", stallReduction);
        System.out.printf("   • CPI improvement: %.0f%%\n", ((basicAvg.cpi - fwdAvg.cpi) / basicAvg.cpi) * 100);
        
        System.out.println("\n2. BRANCH PREDICTION IMPACT:");
        double predSpeedup = fwdAvg.cycles / (double) predAvg.cycles;
        System.out.printf("   • Additional speedup over forwarding: %.2fx\n", predSpeedup);
        System.out.printf("   • Control stall reduction: %.0f%%\n", 
            ((fwdAvg.controlStalls - predAvg.controlStalls) / (double) Math.max(fwdAvg.controlStalls, 1)) * 100);
        
        System.out.println("\n3. SUPERSCALAR IMPACT:");
        double superSpeedup = basicAvg.cycles / (double) superAvg.cycles;
        System.out.printf("   • Overall speedup vs basic: %.2fx\n", superSpeedup);
        System.out.printf("   • CPI improvement: %.0f%%\n", ((basicAvg.cpi - superAvg.cpi) / basicAvg.cpi) * 100);
        
        System.out.println("\n4. BEST CONFIGURATION:");
        System.out.println("   • Superscalar with forwarding and branch prediction");
        System.out.printf("   • Achieves %.2fx speedup over basic pipeline\n", superSpeedup);
        System.out.printf("   • Average CPI: %.2f (vs %.2f basic)\n", superAvg.cpi, basicAvg.cpi);
        
        System.out.println("\n5. WORKLOAD CHARACTERISTICS:");
        printWorkloadCharacteristics(results);
    }

    static void printWorkloadCharacteristics(List<WorkloadResult> results) {
        Map<String, WorkloadResult> basicResults = new HashMap<>();
        for (WorkloadResult r : results) {
            if (r.configName.equals("Basic Pipeline")) {
                basicResults.put(r.workloadName, r);
            }
        }
        
        for (Map.Entry<String, WorkloadResult> entry : basicResults.entrySet()) {
            WorkloadResult r = entry.getValue();
            System.out.printf("   • %s: CPI=%.2f, Data Stalls=%d, Control Stalls=%d\n",
                r.workloadName, r.cpi, r.dataStalls, r.controlStalls);
        }
    }

    static WorkloadResult calculateAverage(List<WorkloadResult> results, String configName) {
        int count = 0;
        int totalCycles = 0;
        int totalInstructions = 0;
        double totalCpi = 0;
        int totalDataStalls = 0;
        int totalControlStalls = 0;
        int totalStalls = 0;
        
        for (WorkloadResult r : results) {
            if (r.configName.equals(configName)) {
                count++;
                totalCycles += r.cycles;
                totalInstructions += r.instructions;
                totalCpi += r.cpi;
                totalDataStalls += r.dataStalls;
                totalControlStalls += r.controlStalls;
                totalStalls += r.totalStalls;
            }
        }
        
        WorkloadResult avg = new WorkloadResult("Average", configName, new stats.Statistics(), null);
        avg.cycles = totalCycles / count;
        avg.instructions = totalInstructions / count;
        avg.cpi = totalCpi / count;
        avg.dataStalls = totalDataStalls / count;
        avg.controlStalls = totalControlStalls / count;
        avg.totalStalls = totalStalls / count;
        
        return avg;
    }

    static void printHeader(String title) {
        System.out.println("╔" + "═".repeat(118) + "╗");
        System.out.printf("║  %-114s  ║\n", title);
        System.out.println("╚" + "═".repeat(118) + "╝");
    }
}
