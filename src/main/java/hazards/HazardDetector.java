package hazards;

import isa.Instruction;
import isa.OpType;

/**
 * Hazard Detection Unit - Phase 1 (Stall-Only Strategy)
 * 
 * OVERVIEW:
 * ========
 * This unit identifies pipeline hazards and determines the appropriate response.
 * Phase 1 uses a conservative approach: STALL on data hazards, FLUSH on control hazards.
 * No data forwarding is implemented (that's Phase 2).
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
 *      ADD R4, R1, R5    # Needs R1 but it's not ready yet! → STALL
 *    
 *    Detection: Check if the instruction in ID reads a register that's being
 *    written by an instruction in EX or MEM.
 *    
 *    Solution: STALL the pipeline (freeze IF and ID, inject bubble into EX)
 *    until the producing instruction completes WB.
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
 * WHY NO FORWARDING IN PHASE 1?
 * =============================
 * Forwarding (bypassing) would allow us to use results before they're written
 * to the register file, reducing stalls. However, Phase 1 focuses on understanding
 * the fundamental hazard types. Forwarding is added in Phase 2.
 */
public class HazardDetector {

    // ── Data Hazard (RAW) ─────────────────────────────────────────────────

    /**
     * Detects Read-After-Write (RAW) data hazards.
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
     *   Result: STALL! ID can't proceed until R1 is written.
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
     * BRANCH PREDICTION STRATEGY: Assume NOT TAKEN
     * - Advantage: No penalty if prediction is correct (branch not taken)
     * - Disadvantage: 1 cycle penalty if prediction is wrong (branch taken)
     * 
     * Alternative strategies (not implemented):
     * - Always taken: Good for loops, bad for if-statements
     * - Static prediction: Based on branch direction (backward = taken)
     * - Dynamic prediction: Branch history table (Phase 2+)
     * 
     * @param inst   The branch/jump instruction being evaluated in ID
     * @param taken  true if the branch condition evaluated to true
     * @return Number of pipeline bubbles needed (0 or 1)
     */
    public int controlPenalty(Instruction inst, boolean taken) {
        if (inst == null || inst.isNop()) return 0;

        switch (inst.opCode.type) {
            case JUMP:   return 1;           // always redirect
            case BRANCH: return taken ? 1 : 0;
            default:     return 0;
        }
    }
}
