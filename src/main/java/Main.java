package main;

import assembler.Assembler;
import pipeline.PipelineSimulator;
import stats.Statistics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CS 510 – Advanced Computer Architecture
 * Phase 1: Basic 5-Stage Pipeline Simulator
 * 
 * This simulator models a classic 5-stage RISC pipeline with realistic hazard handling.
 * It demonstrates how data dependencies and control flow affect pipeline performance.
 *
 * Usage:
 *   mvn exec:java                        → runs all four workloads with comparison
 *   mvn exec:java -Dexec.args="arithmetic"   → runs specific workload
 *   
 * Available workloads: arithmetic, memory, branch, loop
 */
public class Main {

    // ═══════════════════════════════════════════════════════════════════════
    //  TEST WORKLOADS
    // ═══════════════════════════════════════════════════════════════════════
    // These workloads demonstrate different pipeline hazard scenarios:
    // - R0 is hardwired to zero (RISC convention)
    // - Registers default to 0; some workloads pre-initialize values
    // - Each workload stresses different aspects of the pipeline
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Arithmetic-intensive workload.
     * Demonstrates Read-After-Write (RAW) data hazards with back-to-back ALU operations.
     * Each instruction depends on the result of the previous one, forcing pipeline stalls.
     */
    static final String WORKLOAD_ARITHMETIC =
        "# Arithmetic-intensive workload\n" +
        "# Each instruction depends on the previous result → RAW stalls\n" +
        "ADD R1, R2, R3\n" +     // R1 = R2 + R3  (both 0 initially)
        "ADD R4, R1, R5\n" +     // RAW on R1 → stall
        "SUB R6, R4, R1\n" +     // RAW on R4 → stall
        "MUL R7, R6, R2\n" +     // RAW on R6 → stall
        "ADD R8, R7, R3\n";      // RAW on R7 → stall

    /**
     * Memory-intensive workload.
     * Demonstrates load-use hazards where instructions immediately use loaded data.
     * Memory operations have longer latency, causing additional stalls.
     */
    static final String WORKLOAD_MEMORY =
        "# Memory-intensive workload\n" +
        "LOAD  R1, 0(R2)\n" +    // R1 ← mem[0]
        "LOAD  R3, 4(R2)\n" +    // R3 ← mem[4]
        "ADD   R4, R1, R3\n" +   // RAW on R1, R3 → stalls
        "STORE R4, 8(R2)\n" +    // mem[8] ← R4
        "LOAD  R5, 8(R2)\n" +    // R5 ← mem[8]
        "ADD   R6, R5, R4\n";    // RAW on R5 → stall

    /**
     * Branch-heavy workload.
     * Demonstrates control hazards with branch misprediction.
     * Pipeline assumes branches are NOT taken; when taken, requires flushing wrong instructions.
     */
    static final String WORKLOAD_BRANCH =
        "# Branch-heavy workload\n" +
        "# Branch assumed NOT-taken; if taken → flush 1 cycle\n" +
        "ADD  R1, R0, R0\n" +    // R1 = 0
        "ADD  R2, R0, R0\n" +    // R2 = 0
        "BEQ  R1, R2, END\n" +   // taken (0==0) → flush 1 cycle, jump to END
        "ADD  R3, R1, R2\n" +    // skipped
        "SUB  R4, R3, R1\n" +    // skipped
        "END:\n" +
        "ADD  R5, R1, R2\n";     // continues here

    /**
     * Loop-based workload.
     * Demonstrates realistic loop behavior with back-edge branches.
     * Combines data dependencies with control flow, showing mixed hazard scenarios.
     */
    static final String WORKLOAD_LOOP =
        "# Loop-based workload\n" +
        "# R4=1 (step), R5=3 (limit) — pre-loaded before run\n" +
        "ADD  R3, R0, R0\n" +    // i   = 0
        "ADD  R1, R0, R0\n" +    // acc = 0
        "LOOP:\n" +
        "ADD  R1, R1, R2\n" +    // acc += R2
        "ADD  R3, R3, R4\n" +    // i   += 1
        "BNE  R3, R5, LOOP\n" +  // if i != 3 → back to LOOP
        "ADD  R6, R1, R0\n";     // store result in R6

    // ═══════════════════════════════════════════════════════════════════════
    //  MAIN
    // ═══════════════════════════════════════════════════════════════════════

    public static void main(String[] args) {

        // Collect workloads in order
        Map<String, String> workloads = new LinkedHashMap<>();
        workloads.put("arithmetic", WORKLOAD_ARITHMETIC);
        workloads.put("memory",     WORKLOAD_MEMORY);
        workloads.put("branch",     WORKLOAD_BRANCH);
        workloads.put("loop",       WORKLOAD_LOOP);

        if (args.length > 0 && !args[0].equalsIgnoreCase("all")) {
            String key = args[0].toLowerCase();
            if (!workloads.containsKey(key)) {
                System.out.println("Unknown workload: " + key);
                System.out.println("Available: arithmetic, memory, branch, loop, all");
                return;
            }
            runWorkload(key, workloads.get(key), null);
        } else {
            // Run all and print comparison table
            Map<String, Statistics> results = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : workloads.entrySet()) {
                Statistics s = runWorkload(e.getKey(), e.getValue(), null);
                results.put(e.getKey(), s);
            }
            printComparisonTable(results);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Runner
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Executes a single workload through the complete pipeline simulation cycle.
     * 
     * Process:
     * 1. Assemble: Convert assembly text to machine instructions
     * 2. Initialize: Set up registers and memory with test data
     * 3. Load: Load instructions into the pipeline
     * 4. Execute: Run the simulation cycle-by-cycle
     * 5. Report: Display results and performance metrics
     *
     * @param name    Workload identifier for display and configuration
     * @param source  Assembly language source code
     * @param sim     Existing simulator instance (null creates new one)
     * @return        Performance statistics from the run
     */
    static Statistics runWorkload(String name, String source, PipelineSimulator sim) {
        // Step 1: Assemble the source code into executable instructions
        Assembler asm = new Assembler();
        Assembler.Program prog = asm.assemble(source);

        // Step 2: Create or reuse simulator instance
        if (sim == null) {
            sim = new PipelineSimulator();
        }

        // Step 3: Initialize test data specific to each workload
        initializeWorkloadData(name, sim);

        // Step 4: Load the program into the simulator

        sim.load(prog.instructions);

        // Step 5: Display program information
        printWorkloadHeader(name, prog);

        // Step 6: Execute the simulation
        Statistics stats = sim.run();
        
        // Step 7: Display results
        sim.printResults();

        return stats;
    }

    /**
     * Initializes registers and memory with workload-specific test data.
     */
    private static void initializeWorkloadData(String name, PipelineSimulator sim) {
        switch (name.toLowerCase()) {
            case "memory":
                // Pre-populate memory locations for LOAD instructions
                sim.getDataMemory().init(0, 10);   // mem[0] = 10
                sim.getDataMemory().init(4, 20);   // mem[4] = 20
                break;
                
            case "loop":
                // Set up loop control variables
                sim.getRegisterFile().init("R4", 1);  // step = 1
                sim.getRegisterFile().init("R5", 3);  // limit = 3
                sim.getRegisterFile().init("R2", 5);  // accumulator value
                break;
                
            default:
                // Other workloads use default zero-initialized state
                break;
        }
    }

    /**
     * Prints formatted header with program listing and labels.
     */
    private static void printWorkloadHeader(String name, Assembler.Program prog) {
        System.out.println();
        System.out.println("═".repeat(110));
        System.out.printf("  WORKLOAD: %s%n", name.toUpperCase());
        System.out.println("═".repeat(110));
        System.out.printf("  Program (%d instructions)%n", prog.instructions.size());
        
        for (int i = 0; i < prog.instructions.size(); i++) {
            System.out.printf("    [%2d]  %s%n", i, prog.instructions.get(i));
        }
        
        if (!prog.labelMap.isEmpty()) {
            System.out.println("  Labels: " + prog.labelMap);
        }
        System.out.println();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Comparison table
    // ═══════════════════════════════════════════════════════════════════════

    private static void printComparisonTable(Map<String, Statistics> results) {
        String sep = "═".repeat(96);
        System.out.println("\n\n" + sep);
        System.out.println("  WORKLOAD COMPARISON TABLE");
        System.out.println(sep);
        System.out.printf("  %-18s %8s %14s %8s %10s %11s %10s %10s%n",
            "Workload", "Cycles", "Instructions", "Stalls",
            "DataStalls", "CtrlStalls", "CPI", "IPC");
        System.out.println("-".repeat(96));
        for (Map.Entry<String, Statistics> e : results.entrySet()) {
            Statistics s = e.getValue();
            System.out.printf("  %-18s %8d %14d %8d %10d %11d %10.3f %10.3f%n",
                e.getKey(),
                s.getTotalCycles(),
                s.getInstructionsRetired(),
                s.totalStalls(),
                s.getDataStalls(),
                s.getControlStalls(),
                s.cpi(),
                s.throughput());
        }
        System.out.println(sep);
    }
}
