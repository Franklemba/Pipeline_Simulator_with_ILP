package hazards;

import forwarding.ForwardingDecision;
import forwarding.ForwardingSource;
import isa.Instruction;
import isa.OpType;

/**
 * Hazard Detector - Finds problems before they cause errors
 * 
 * This unit spots three types of problems:
 * 
 * 1. DATA HAZARDS (Read-After-Write)
 *    When an instruction needs a value that's still being calculated.
 *    
 *    Example:
 *      ADD R1, R2, R3    // R1 is being calculated
 *      ADD R4, R1, R5    // Needs R1 but it's not ready!
 *    
 *    Phase 1: Stall until R1 is written to the register file
 *    Phase 2: Forward R1 from the pipeline (no stall needed!)
 *    
 *    Exception - Load-Use Hazard:
 *      LOAD R1, 0(R2)    // R1 won't be ready until memory access completes
 *      ADD  R4, R1, R5   // Needs R1 right away
 *    
 *    Even with forwarding, we have to stall 1 cycle because the memory
 *    value isn't available yet.
 * 
 * 2. CONTROL HAZARDS (Branches and Jumps)
 *    When we don't know which instruction to fetch next.
 *    
 *    We guess that branches won't be taken. If we're wrong, we have to
 *    throw away the instruction we already fetched (costs 1 cycle).
 *    Jumps always flush because they're always taken.
 * 
 * 3. STRUCTURAL HAZARDS
 *    When two instructions need the same hardware at the same time.
 *    
 *    Our design doesn't have these because:
 *    - Separate instruction and data memory
 *    - Only one instruction per stage
 *    - Enough hardware for everyone
 */
public class HazardDetector {

    // ── Data Hazard (RAW) ─────────────────────────────────────────────────

    /**
     * Check if there's a data hazard (Read-After-Write).
     * 
     * A hazard exists when:
     * 1. The instruction in ID wants to read a register
     * 2. An instruction in EX or MEM is going to write to that register
     * 3. The write hasn't happened yet
     * 
     * Example:
     *   Cycle 3:
     *   IF: SUB R6, R4, R1
     *   ID: ADD R4, R1, R5  ← wants to read R1
     *   EX: ADD R1, R2, R3  ← will write R1 (not done yet!)
     *   
     *   Phase 1: Stall! Can't proceed until R1 is ready.
     *   Phase 2: Forward! Grab R1 from the EX/MEM latch.
     * 
     * @param idInst   Instruction that needs data (in ID stage)
     * @param exInst   Instruction that might produce it (in EX stage)
     * @param memInst  Instruction that might produce it (in MEM stage)
     * @return true if there's a hazard
     */
    public boolean detectRAW(Instruction idInst,
                             Instruction exInst,
                             Instruction memInst) {

        if (idInst == null || idInst.isNop()) return false;

        // Collect source registers used by the ID instruction
        java.util.Set<String> sources = new java.util.HashSet<>();
        if (idInst.rs1 != null) sources.add(idInst.rs1);
        if (idInst.rs2 != null) sources.add(idInst.rs2);
        if (sources.isEmpty())  return false;

        // Check EX stage
        if (writesTo(exInst, sources))  return true;

        // Check MEM stage
        if (writesTo(memInst, sources)) return true;

        return false;
    }
    
    /**
     * Check if we still need to stall even with forwarding.
     * 
     * Most hazards can be fixed with forwarding, but load-use hazards
     * still need a stall because the memory value isn't ready yet.
     * 
     * Load-Use Hazard:
     *   LOAD R1, 0(R2)    // R1 won't be ready until MEM completes
     *   ADD  R4, R1, R5   // Needs R1 in EX stage
     *   
     * Timeline:
     *   Cycle N:   LOAD in EX (calculating address)
     *   Cycle N+1: LOAD in MEM (reading memory) ← R1 ready HERE
     *              ADD in EX (needs R1) ← Too early! R1 not ready yet!
     *   
     * Solution: Stall ADD for 1 cycle, then forward from MEM/WB.
     * 
     * @param idInst           Instruction in ID stage
     * @param exInst           Instruction in EX stage
     * @param forwardingNeeded Whether we detected a forwarding opportunity
     * @return true if we still need to stall
     */
    public boolean needsStallWithForwarding(Instruction idInst,
                                           Instruction exInst,
                                           boolean forwardingNeeded) {
        
        if (!forwardingNeeded) {
            return false;  // No hazard, no stall
        }
        
        // Check for load-use hazard
        if (exInst != null && !exInst.isNop() && exInst.opType() == OpType.LOAD) {
            // EX stage has a LOAD instruction
            // Check if ID instruction needs the loaded register
            if (idInst != null && !idInst.isNop()) {
                if (idInst.rs1 != null && idInst.rs1.equals(exInst.rd)) {
                    return true;  // Load-use hazard on rs1
                }
                if (idInst.rs2 != null && idInst.rs2.equals(exInst.rd)) {
                    return true;  // Load-use hazard on rs2
                }
            }
        }
        
        return false;  // Forwarding can resolve the hazard
    }

    /**
     * Returns true if {@code producer} writes to one of the given registers.
     */
    private boolean writesTo(Instruction producer, java.util.Set<String> regs) {
        if (producer == null || producer.isNop()) return false;
        if (!producer.writesRegister())           return false;
        if (producer.rd == null)                  return false;
        return regs.contains(producer.rd);
    }

    // ── Control Hazard ────────────────────────────────────────────────────

    /**
     * Calculates the control hazard penalty (flush cycles needed).
     * 
     * Phase 2: Now considers branch prediction.
     * 
     * BRANCH PREDICTION STRATEGIES:
     * ============================
     * Phase 1: Always assume NOT TAKEN
     * - No penalty if branch not taken (prediction correct)
     * - 1 cycle penalty if branch taken (prediction wrong)
     * 
     * Phase 2: Use predictor
     * - No penalty if prediction matches actual outcome
     * - 1 cycle penalty if prediction wrong (misprediction)
     * 
     * Alternative strategies (not implemented):
     * - Always taken: Good for loops, bad for if-statements
     * - Static prediction: Based on branch direction (backward = taken)
     * - Dynamic prediction: Branch history table (Phase 2+)
     * 
     * @param inst       The branch/jump instruction being evaluated in ID
     * @param taken      true if the branch condition evaluated to true
     * @param predicted  true if we predicted the branch would be taken
     * @return Number of pipeline bubbles needed (0 or 1)
     */
    public int controlPenalty(Instruction inst, boolean taken, boolean predicted) {
        if (inst == null || inst.isNop()) return 0;

        switch (inst.opCode.type) {
            case JUMP:
                // Jumps are always taken, always mispredicted (we fetch sequentially)
                return 1;
                
            case BRANCH:
                // Penalty only if prediction was wrong
                return (predicted != taken) ? 1 : 0;
                
            default:
                return 0;
        }
    }
    
    /**
     * Legacy method for Phase 1 compatibility (assumes NOT TAKEN prediction).
     */
    public int controlPenalty(Instruction inst, boolean taken) {
        return controlPenalty(inst, taken, false);  // Assume predicted NOT TAKEN
    }
}
