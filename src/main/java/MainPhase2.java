import assembler.Assembler;
import pipeline.PipelineSimulator;
import stats.Statistics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 2 Demo: Data Forwarding Comparison
 * 
 * This demonstrates the performance improvement from data forwarding.
 * Runs the same workloads with and without forwarding to show the difference.
 */
public class MainPhase2 {

    // Same workloads as Phase 1
    static final String WORKLOAD_ARITHMETIC =
        "# Arithmetic-intensive workload\n" +
        "ADD R1, R2, R3\n" +
        "ADD R4, R1, R5\n" +
        "SUB R6, R4, R1\n" +
        "MUL R7, R6, R2\n" +
        "ADD R8, R7, R3\n";

    static final String WORKLOAD_MEMORY =
        "# Memory-intensive workload\n" +
        "LOAD  R1, 0(R2)\n" +
        "LOAD  R3, 4(R2)\n" +
        "ADD   R4, R1, R3\n" +
        "STORE R4, 8(R2)\n" +
        "LOAD  R5, 8(R2)\n" +
        "ADD   R6, R5, R4\n";

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║          PHASE 2: DATA FORWARDING DEMONSTRATION                      ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Collect workloads
        Map<String, String> workloads = new LinkedHashMap<>();
        workloads.put("arithmetic", WORKLOAD_ARITHMETIC);
        workloads.put("memory", WORKLOAD_MEMORY);

        // Run comparison for each workload
        for (Map.Entry<String, String> entry : workloads.entrySet()) {
            String name = entry.getKey();
            String code = entry.getValue();
            
            System.out.println("═".repeat(110));
            System.out.printf("  WORKLOAD: %s%n", name.toUpperCase());
            System.out.println("═".repeat(110));
            System.out.println();
            
            // Run WITHOUT forwarding (Phase 1)
            System.out.println("  ┌─ WITHOUT FORWARDING (Phase 1 - Stalls Only) ─────────────────┐");
            Statistics statsNoFwd = runWorkload(name, code, false);
            System.out.println("  └───────────────────────────────────────────────────────────────┘");
            System.out.println();
            
            // Run WITH forwarding (Phase 2)
            System.out.println("  ┌─ WITH FORWARDING (Phase 2 - Bypass Paths) ───────────────────┐");
            Statistics statsFwd = runWorkload(name, code, true);
            System.out.println("  └───────────────────────────────────────────────────────────────┘");
            System.out.println();
            
            // Print comparison
            printComparison(name, statsNoFwd, statsFwd);
            System.out.println();
        }
        
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  KEY INSIGHTS:                                                       ║");
        System.out.println("║  • Forwarding dramatically reduces data stalls                       ║");
        System.out.println("║  • CPI improves (lower is better)                                    ║");
        System.out.println("║  • IPC improves (higher is better)                                   ║");
        System.out.println("║  • Load-use hazards still require 1-cycle stall                      ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
    }

    static Statistics runWorkload(String name, String source, boolean forwarding) {
        Assembler asm = new Assembler();
        Assembler.Program prog = asm.assemble(source);

        PipelineSimulator sim = new PipelineSimulator();
        sim.setForwardingEnabled(forwarding);
        sim.setVerbose(false);  // Disable cycle table for cleaner output

        // Initialize test data
        if (name.equalsIgnoreCase("memory")) {
            sim.getDataMemory().init(0, 10);
            sim.getDataMemory().init(4, 20);
        }

        sim.load(prog.instructions);
        Statistics stats = sim.run();
        
        // Print summary
        System.out.println(stats.summary());
        
        return stats;
    }

    static void printComparison(String workload, Statistics noFwd, Statistics withFwd) {
        int cycleReduction = noFwd.getTotalCycles() - withFwd.getTotalCycles();
        int stallReduction = noFwd.getDataStalls() - withFwd.getDataStalls();
        double speedup = (double) noFwd.getTotalCycles() / withFwd.getTotalCycles();
        double cpiImprovement = ((noFwd.cpi() - withFwd.cpi()) / noFwd.cpi()) * 100;
        
        System.out.println("  ┌─ COMPARISON ──────────────────────────────────────────────────────┐");
        System.out.printf("  │  Workload: %-58s│%n", workload.toUpperCase());
        System.out.println("  ├───────────────────────────────────────────────────────────────────┤");
        System.out.printf("  │  Metric                │ No Forwarding │ With Forwarding │ Δ      │%n");
        System.out.println("  ├───────────────────────────────────────────────────────────────────┤");
        System.out.printf("  │  Total Cycles          │ %13d │ %15d │ -%d    │%n", 
            noFwd.getTotalCycles(), withFwd.getTotalCycles(), cycleReduction);
        System.out.printf("  │  Data Stalls           │ %13d │ %15d │ -%d    │%n", 
            noFwd.getDataStalls(), withFwd.getDataStalls(), stallReduction);
        System.out.printf("  │  CPI                   │ %13.3f │ %15.3f │ %.1f%% │%n", 
            noFwd.cpi(), withFwd.cpi(), cpiImprovement);
        System.out.printf("  │  IPC                   │ %13.3f │ %15.3f │ +%.1f%% │%n", 
            noFwd.throughput(), withFwd.throughput(), 
            ((withFwd.throughput() - noFwd.throughput()) / noFwd.throughput()) * 100);
        System.out.printf("  │  Speedup               │ %13s │ %15.2fx │        │%n", 
            "1.00x", speedup);
        
        if (withFwd.getForwardingEvents() > 0) {
            System.out.printf("  │  Forwarding Events     │ %13s │ %15d │        │%n", 
                "N/A", withFwd.getForwardingEvents());
            System.out.printf("  │  Stalls Avoided        │ %13s │ %15d │        │%n", 
                "N/A", withFwd.getStallsAvoided());
        }
        
        System.out.println("  └───────────────────────────────────────────────────────────────────┘");
    }
}
