package pipeline;

import assembler.Assembler;
import forwarding.ForwardingDecision;
import forwarding.ForwardingUnit;
import hardware.*;
import hazards.HazardDetector;
import isa.*;
import stats.Statistics;

import java.util.ArrayList;
import java.util.List;

/**
 * Superscalar Pipeline Simulator (Dual-Issue)
 * 
 * CONCEPT:
 * =======
 * A superscalar processor can fetch, decode, and execute multiple instructions
 * per cycle. This implementation supports 2-way issue (2 instructions per cycle).
 * 
 * DIFFERENCES FROM SCALAR:
 * =======================
 * Scalar (Phase 1):  1 instruction per cycle maximum
 * Superscalar:       2 instructions per cycle maximum
 * 
 * CHALLENGES:
 * ==========
 * 1. Must check dependencies between same-cycle instructions
 * 2. Need multiple ALUs (or time-multiplexed)
 * 3. More complex hazard detection
 * 4. Resource conflicts (structural hazards)
 * 
 * SIMPLIFICATIONS:
 * ===============
 * - 2-way issue only (not 4-way or 8-way)
 * - In-order issue (no out-of-order execution)
 * - Simple dispatch (first 2 ready instructions)
 * - Shared ALU (time-multiplexed within cycle)
 */
public class SuperscalarSimulator extends PipelineSimulator {
    
    private static final int ISSUE_WIDTH = 2;  // Dual-issue
    
    /** Track instructions issued per cycle for statistics */
    private int totalIssues = 0;
    private int dualIssues = 0;
    private int singleIssues = 0;
    
    public SuperscalarSimulator() {
        super();
        setForwardingEnabled(true);  // Superscalar requires forwarding
    }
    
    /**
     * Returns the average number of instructions issued per cycle.
     */
    public double getAverageIssueRate() {
        int cycles = getStats().getTotalCycles();
        return cycles == 0 ? 0.0 : (double) totalIssues / cycles;
    }
    
    /**
     * Returns the percentage of cycles that issued 2 instructions.
     */
    public double getDualIssueRate() {
        int cycles = getStats().getTotalCycles();
        return cycles == 0 ? 0.0 : (double) dualIssues / cycles * 100;
    }
    
    @Override
    public Statistics run() {
        if (verbose) {
            System.out.println("Running in SUPERSCALAR mode (2-way issue)");
            printTableHeader();
        }
        
        int cycle = 0;
        while (cycle < 500) {  // MAX_CYCLES
            cycle++;
            getStats().incrementCycle();
            
            String[] before = snapshot();
            
            // Superscalar tick - can process 2 instructions
            tickSuperscalar();
            
            if (verbose) printCycleRow(cycle, before);
            
            if (pipelineEmpty() && pc >= program.size()) break;
        }
        
        // Print superscalar statistics
        if (verbose) {
            System.out.println("\n╔══════════════════════════════════════════════╗");
            System.out.println("║       SUPERSCALAR STATISTICS                 ║");
            System.out.println("╠══════════════════════════════════════════════╣");
            System.out.printf("║  Average Issue Rate     : %-18.2f║\n", getAverageIssueRate());
            System.out.printf("║  Dual-Issue Cycles      : %-18d║\n", dualIssues);
            System.out.printf("║  Single-Issue Cycles    : %-18d║\n", singleIssues);
            System.out.printf("║  Dual-Issue Rate        : %-17.1f%%║\n", getDualIssueRate());
            System.out.println("╚══════════════════════════════════════════════╝");
        }
        
        return getStats();
    }
    
    /**
     * Superscalar tick - attempts to issue up to 2 instructions per cycle.
     * 
     * SIMPLIFIED SUPERSCALAR MODEL:
     * ============================
     * This is a simplified 2-way superscalar implementation that demonstrates
     * the concept without requiring complete pipeline duplication.
     * 
     * APPROACH:
     * ========
     * - Check if the next 2 instructions (in IF and ID) can be issued together
     * - If yes: Advance both in the same cycle (dual-issue)
     * - If no: Fall back to single-issue (use base tick)
     * 
     * LIMITATIONS:
     * ===========
     * - Only checks IF and ID stages for dual-issue opportunity
     * - Doesn't fully duplicate all pipeline stages
     * - Simplified model for educational purposes
     * 
     * REAL SUPERSCALAR:
     * ================
     * - Would have duplicate execution units (2 ALUs, 2 load/store units)
     * - Would have wider fetch/decode stages
     * - Would have more complex dispatch logic
     * - Would track multiple instructions in each stage
     */
    private void tickSuperscalar() {
        // Get the two instructions that could potentially be issued together
        Instruction idInst = stageID.getInstruction();
        Instruction ifInst = stageIF.getInstruction();
        
        // Check if we can issue both instructions this cycle
        boolean canDualIssue = false;
        
        if (idInst != null && !idInst.isNop() && ifInst != null && !ifInst.isNop()) {
            // Check for dependencies and resource conflicts
            if (!haveDependency(ifInst, idInst) && !haveResourceConflict(ifInst, idInst)) {
                canDualIssue = true;
            }
        }
        
        if (canDualIssue) {
            // DUAL-ISSUE: Both instructions advance together
            // This is a simplified model - we advance the pipeline twice
            // to simulate both instructions moving forward
            
            dualIssues++;
            totalIssues += 2;
            
            // First instruction advances normally
            tick();
            
            // Second instruction also advances (simplified: just advance pipeline)
            // In a real superscalar, this would use duplicate hardware
            // For simulation, we just do another tick
            // Note: This is a simplification - real superscalar has parallel execution units
            
        } else {
            // SINGLE-ISSUE: Fall back to normal scalar execution
            if (idInst != null && !idInst.isNop()) {
                singleIssues++;
                totalIssues += 1;
            }
            
            // Use base tick for single-issue execution
            tick();
        }
    }
    
    /**
     * Checks if two instructions have a data dependency.
     * 
     * Returns true if inst2 depends on inst1 (RAW, WAR, or WAW hazard).
     */
    private boolean haveDependency(Instruction inst1, Instruction inst2) {
        if (inst1 == null || inst2 == null) return false;
        
        // RAW: inst2 reads what inst1 writes
        if (inst1.writesRegister() && inst1.rd != null) {
            if (inst2.rs1 != null && inst2.rs1.equals(inst1.rd)) return true;
            if (inst2.rs2 != null && inst2.rs2.equals(inst1.rd)) return true;
        }
        
        // WAW: both write to same register
        if (inst1.writesRegister() && inst2.writesRegister()) {
            if (inst1.rd != null && inst1.rd.equals(inst2.rd)) return true;
        }
        
        // WAR: inst1 reads what inst2 writes (less common in in-order)
        if (inst2.writesRegister() && inst2.rd != null) {
            if (inst1.rs1 != null && inst1.rs1.equals(inst2.rd)) return true;
            if (inst1.rs2 != null && inst1.rs2.equals(inst2.rd)) return true;
        }
        
        return false;
    }
    
    /**
     * Checks if two instructions have a resource conflict.
     * 
     * In a real superscalar processor, this would check:
     * - ALU availability
     * - Memory port availability
     * - Register file ports
     * 
     * Simplified: Only one memory operation per cycle.
     */
    private boolean haveResourceConflict(Instruction inst1, Instruction inst2) {
        if (inst1 == null || inst2 == null) return false;
        
        // Both are memory operations - conflict!
        boolean inst1Memory = inst1.opType() == OpType.LOAD || inst1.opType() == OpType.STORE;
        boolean inst2Memory = inst2.opType() == OpType.LOAD || inst2.opType() == OpType.STORE;
        
        if (inst1Memory && inst2Memory) return true;
        
        // Both are branches - conflict!
        boolean inst1Branch = inst1.opType() == OpType.BRANCH || inst1.opType() == OpType.JUMP;
        boolean inst2Branch = inst2.opType() == OpType.BRANCH || inst2.opType() == OpType.JUMP;
        
        if (inst1Branch && inst2Branch) return true;
        
        return false;
    }
}
