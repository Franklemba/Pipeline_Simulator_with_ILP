package forwarding;

import isa.Instruction;

/**
 * Forwarding Unit (Bypass Unit)
 * 
 * This unit lets us grab results from later pipeline stages instead of
 * waiting for them to be written to registers. Saves a lot of stall cycles!
 * 
 * THREE FORWARDING PATHS:
 * 
 * 1. EX→EX forwarding (most common)
 *    Grab the result right after it's calculated
 *    Example: ADD R1,R2,R3 followed by ADD R4,R1,R5
 * 
 * 2. MEM→EX forwarding
 *    Grab from one instruction back
 *    Example: ADD R1,R2,R3; NOP; ADD R4,R1,R5
 * 
 * 3. WB→EX forwarding
 *    Grab from two instructions back
 *    Less common but still useful
 * 
 * WHEN FORWARDING CAN'T HELP:
 * 
 * Load-Use Hazard:
 *   LOAD R1, 0(R2)
 *   ADD  R4, R1, R5  ← R1 isn't ready until memory access completes
 * 
 * We still have to stall 1 cycle even with forwarding.
 */
public class ForwardingUnit {

    /**
     * Figure out if we can forward data and where it should come from.
     * 
     * @param idInst  Instruction that needs data (in ID stage)
     * @param exInst  Instruction that might have it (in EX stage)
     * @param memInst Instruction that might have it (in MEM stage)
     * @param wbInst  Instruction that might have it (in WB stage)
     * @return Decision about where to get the data
     */
    public ForwardingDecision detectForwarding(Instruction idInst,
                                               Instruction exInst,
                                               Instruction memInst,
                                               Instruction wbInst) {
        
        if (idInst == null || idInst.isNop()) {
            return ForwardingDecision.noForwarding();
        }

        ForwardingDecision decision = new ForwardingDecision();

        // Check forwarding for rs1 (first source operand)
        if (idInst.rs1 != null) {
            decision.rs1Source = detectForwardingForOperand(idInst.rs1, exInst, memInst, wbInst);
        }

        // Check forwarding for rs2 (second source operand)
        if (idInst.rs2 != null) {
            decision.rs2Source = detectForwardingForOperand(idInst.rs2, exInst, memInst, wbInst);
        }

        return decision;
    }

    /**
     * Find the forwarding source for one operand.
     * 
     * Priority (check closest stage first):
     * 1. EX stage (most recent)
     * 2. MEM stage
     * 3. WB stage
     * 4. No forwarding needed (register file is fine)
     */
    private ForwardingSource detectForwardingForOperand(String operandReg,
                                                        Instruction exInst,
                                                        Instruction memInst,
                                                        Instruction wbInst) {
        
        // Check EX stage first (highest priority - most recent)
        if (canForwardFrom(exInst, operandReg)) {
            return ForwardingSource.FROM_EX;
        }

        // Check MEM stage
        if (canForwardFrom(memInst, operandReg)) {
            return ForwardingSource.FROM_MEM;
        }

        // Check WB stage
        if (canForwardFrom(wbInst, operandReg)) {
            return ForwardingSource.FROM_WB;
        }

        // No forwarding needed - register file has correct value
        return ForwardingSource.NONE;
    }

    /**
     * Check if an instruction can forward its result.
     * 
     * Requirements:
     * 1. Instruction exists and isn't a NOP
     * 2. It writes to a register
     * 3. The register matches what we need
     */
    private boolean canForwardFrom(Instruction producer, String operandReg) {
        if (producer == null || producer.isNop()) {
            return false;
        }

        if (!producer.writesRegister()) {
            return false;
        }

        if (producer.rd == null) {
            return false;
        }

        return producer.rd.equals(operandReg);
    }

    /**
     * Checks if a load-use hazard exists that forwarding cannot resolve.
     * 
     * A load-use hazard occurs when:
     * 1. The instruction in EX is a LOAD
     * 2. The instruction in ID needs the loaded value immediately
     * 
     * Even with forwarding, we must stall because the loaded value
     * isn't available until the MEM stage completes.
     * 
     * @param idInst Instruction in ID stage (consumer)
     * @param exInst Instruction in EX stage (potential LOAD)
     * @return true if a load-use hazard exists requiring a stall
     */
    public boolean isLoadUseHazard(Instruction idInst, Instruction exInst) {
        if (idInst == null || idInst.isNop()) {
            return false;
        }

        if (exInst == null || exInst.isNop()) {
            return false;
        }

        // Check if EX instruction is a LOAD
        if (exInst.opType() != isa.OpType.LOAD) {
            return false;
        }

        // Check if ID instruction needs the loaded register
        if (idInst.rs1 != null && idInst.rs1.equals(exInst.rd)) {
            return true;
        }

        if (idInst.rs2 != null && idInst.rs2.equals(exInst.rd)) {
            return true;
        }

        return false;
    }
}
