package hazards;

import forwarding.ForwardingDecision;
import forwarding.ForwardingSource;
import isa.Instruction;
import isa.OpType;

/**
 * Hazard Detection Unit - Phase 2 (With Forwarding Support)
 * 
 * OVERVIEW:
 * ========
 * This unit identifies pipeline hazards and determines the appropriate response.
 * Phase 2 adds forwarding support - stalls are only needed when forwarding cannot resolve the hazard.
 * 
 * THREE TYPES OF HAZARDS:
 * ======================
 * 
 * 1. DATA HAZARDS (RAW - Read After Write)
 *    ----------------------------------------
 *    Occurs when an instruction needs a register that's still being computed
 *    by a previous instruction that hasn't completed yet.
 *    
 *    Example:
 *      ADD R1, R2, R3    # R1 is being computed
 *      ADD R4, R1, R5    # Needs R1 but it's not ready yet!
 *    
 *    Phase 1 Solution: STALL until R1 is written to register file
 *    Phase 2 Solution: FORWARD R1 from EX/MEM latch (no stall needed!)
 *    
 *    Exception - Load-Use Hazard:
 *      LOAD R1, 0(R2)    # R1 not available until MEM completes
 *      ADD  R4, R1, R5   # Needs R1 immediately
 *    
 *    Even with forwarding, we must stall 1 cycle because the loaded value
 *    isn't available until the MEM stage completes.
 * 
 * 2. CONTROL HAZARDS (Branches and Jumps)
 *    ---------------------------------------
 *    Occurs when we don't know which instruction to fetch next because a
 *    branch/jump hasn't been resolved yet.
 *    
 *    Strategy: Assume branches are NOT TAKEN (optimistic prediction)
 *    - If branch NOT taken: Continue normally (prediction was correct)
 *    - If branch IS taken: FLUSH the wrong instruction from IF (1 cycle penalty)
 *    - Jumps: Always taken, always flush (1 cycle penalty)
 *    
 *    Example:
 *      BEQ R1, R2, TARGET    # Branch resolves in ID stage
 *      ADD R3, R4, R5        # Already fetched, but might be wrong!
 *      
 *    If branch is taken, the ADD must be flushed (converted to bubble).
 * 
 * 3. STRUCTURAL HAZARDS
 *    --------------------
 *    Occurs when multiple instructions need the same hardware resource.
 *    
 *    This pipeline has NO structural hazards because:
 *    - Separate instruction and data memories (Harvard architecture)
 *    - Single-issue (only one instruction per stage)
 *    - Dedicated hardware for each stage
 *    
 *    (Included for completeness and future phases)
 * 
 * PHASE 2 CHANGES:
 * ===============
 * - detectRAW() now considers forwarding
 * - New method: needsStallWithForwarding() determines if stall is still needed
 * - Load-use hazards always require stalls (forwarding can't help)
 */
public class HazardDetector {

    // ── Data Hazard (RAW) ─────────────────────────────────────────────────

    /**
     * Detects Read-After-Write (RAW) data hazards.
     * 
     * Phase 2: This method now considers whether forwarding can resolve the hazard.
     * Returns true only if a stall is REQUIRED (forwarding cannot help).
     * 
     * A RAW hazard exists when:
     * 1. The instruction in ID needs to READ a register (rs1 or rs2)
     * 2. An instruction in EX or MEM will WRITE to that same register
     * 3. The write hasn't completed yet (hasn't reached WB)
     * 
     * Visual example:
     * 
     *   Cycle 3:
     *   IF: SUB R6, R4, R1
     *   ID: ADD R4, R1, R5  ← needs R1
     *   EX: ADD R1, R2, R3  ← will write R1 (not ready yet!)
     *   
     *   Phase 1: STALL! ID can't proceed until R1 is written.
     *   Phase 2: FORWARD! Use R1 from EX/MEM latch (no stall needed).
     * 
     * @param idInst   Instruction in ID stage (consumer)
     * @param exInst   Instruction in EX stage (potential producer)
     * @param memInst  Instruction in MEM stage (potential producer)
     * @return true if a RAW hazard exists and pipeline must stall
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
     * Determines if a stall is needed even with forwarding available.
     * 
     * Phase 2: Most RAW hazards can be resolved with forwarding, but
     * load-use hazards still require a 1-cycle stall.
     * 
     * Load-Use Hazard Example:
     *   LOAD R1, 0(R2)    # R1 not available until MEM stage completes
     *   ADD  R4, R1, R5   # Needs R1 immediately in EX stage
     *   
     * Timeline:
     *   Cycle N:   LOAD in EX (computing address)
     *   Cycle N+1: LOAD in MEM (reading memory) ← R1 becomes available HERE
     *              ADD in EX (needs R1) ← Too early! R1 not ready yet!
     *   
     * Solution: Stall ADD for 1 cycle, then forward from MEM/WB latch.
     * 
     * @param idInst           Instruction in ID stage
     * @param exInst           Instruction in EX stage
     * @param forwardingNeeded Whether forwarding was detected
     * @return true if a stall is required despite forwarding
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
