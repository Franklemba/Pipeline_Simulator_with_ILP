package forwarding;

import isa.Instruction;

/**
 * Forwarding Unit (Bypass Unit)
 * 
 * PURPOSE:
 * ========
 * Detects when results from later pipeline stages can be forwarded directly
 * to earlier stages, avoiding pipeline stalls caused by data hazards.
 * 
 * FORWARDING PATHS:
 * ================
 * 1. EX/MEM → EX (EX forwarding)
 *    - Result from EX stage forwarded to next instruction's EX stage
 *    - Most common case: back-to-back ALU operations
 *    - Example: ADD R1,R2,R3 followed by ADD R4,R1,R5
 * 
 * 2. MEM/WB → EX (MEM forwarding)
 *    - Result from MEM stage forwarded to EX stage
 *    - One instruction gap between producer and consumer
 *    - Example: ADD R1,R2,R3; NOP; ADD R4,R1,R5
 * 
 * 3. WB → EX (WB forwarding)
 *    - Result from WB stage forwarded to EX stage
 *    - Two instruction gap between producer and consumer
 *    - Less common but still useful
 * 
 * WHEN FORWARDING CANNOT HELP:
 * ============================
 * Load-Use Hazard: LOAD followed immediately by use
 *   LOAD R1, 0(R2)
 *   ADD  R4, R1, R5  ← R1 not available until MEM stage completes
 * 
 * In this case, we MUST stall one cycle even with forwarding.
 */
public class ForwardingUnit {

    /**
     * Detects if forwarding is possible and determines the forwarding source.
     * 
     * @param idInst  Instruction in ID stage (consumer - needs data)
     * @param exInst  Instruction in EX stage (potential producer)
     * @param memInst Instruction in MEM stage (potential producer)
     * @param wbInst  Instruction in WB stage (potential producer)
     * @return ForwardingDecision containing source and operand info
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
     * Detects forwarding source for a single operand register.
     * 
     * Priority (closest stage first):
     * 1. EX stage (most recent result)
     * 2. MEM stage
     * 3. WB stage
     * 4. No forwarding needed (register file has correct value)
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
     * Checks if an instruction can forward its result to a given register.
     * 
     * Requirements:
     * 1. Instruction exists and is not a NOP
     * 2. Instruction writes to a register (has destination)
     * 3. Destination register matches the operand we need
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
