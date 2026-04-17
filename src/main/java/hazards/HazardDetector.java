package hazards;

import isa.Instruction;
import isa.OpType;

/**
 * Phase 1 Hazard Detection Unit — stalls only, no forwarding.
 *
 * ── Data Hazards (RAW) ───────────────────────────────────────────────────
 *  A RAW hazard occurs when the instruction in ID needs a register that
 *  is still being computed by an instruction in EX or MEM.
 *
 *  Without forwarding, we must stall until the producing instruction
 *  completes WB.  That means we stall if the destination register of
 *  the instruction in EX *or* MEM matches a source register of the
 *  instruction currently in ID.
 *
 * ── Control Hazards ──────────────────────────────────────────────────────
 *  A branch resolves at the ID stage (we read the registers and compute
 *  the outcome there).  If the branch is taken we have already fetched
 *  one wrong instruction into IF → flush it (1-cycle penalty).
 *  If the branch is not taken nothing extra needs to happen.
 *
 *  A JUMP is always taken → always flush the IF stage (1-cycle penalty).
 *
 * ── Structural Hazards ───────────────────────────────────────────────────
 *  In this single-issue in-order pipeline with separate instruction and
 *  data memories there are no structural hazards by construction.
 *  (Included as a comment for completeness and Phase 2 extension.)
 */
public class HazardDetector {

    // ── Data Hazard (RAW) ─────────────────────────────────────────────────

    /**
     * Returns true if the instruction currently in the ID stage has a
     * RAW dependency on the instruction in EX or MEM.
     *
     * @param idInst   instruction in the ID stage
     * @param exInst   instruction in the EX stage  (may be null/NOP)
     * @param memInst  instruction in the MEM stage (may be null/NOP)
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
     * Returns the number of pipeline bubbles (flush slots) required after
     * a branch or jump has been resolved in the ID stage.
     *
     * Strategy: assume branch NOT taken (optimistic fetch).
     *   - If the branch IS taken  → 1 bubble (the IF slot was wrong).
     *   - If the branch NOT taken → 0 bubbles (fetch was correct).
     *   - JUMP always             → 1 bubble.
     *
     * @param inst   the branch/jump instruction just decoded in ID
     * @param taken  true if the branch was evaluated as taken
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
