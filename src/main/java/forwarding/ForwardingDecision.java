package forwarding;

/**
 * Represents a forwarding decision for an instruction.
 * 
 * Each instruction can have up to two source operands (rs1 and rs2),
 * and each operand may need forwarding from a different source.
 * 
 * Example:
 *   ADD R1, R2, R3  (in MEM)
 *   SUB R4, R5, R6  (in WB)
 *   MUL R7, R1, R4  (in ID, about to enter EX)
 *   
 *   ForwardingDecision:
 *     rs1Source = FROM_EX  (forward R1 from EX/MEM latch)
 *     rs2Source = FROM_MEM (forward R4 from MEM/WB latch)
 */
public class ForwardingDecision {
    
    /** Forwarding source for first operand (rs1) */
    public ForwardingSource rs1Source;
    
    /** Forwarding source for second operand (rs2) */
    public ForwardingSource rs2Source;

    /**
     * Creates a decision with no forwarding for either operand.
     */
    public ForwardingDecision() {
        this.rs1Source = ForwardingSource.NONE;
        this.rs2Source = ForwardingSource.NONE;
    }

    /**
     * Factory method for no forwarding needed.
     */
    public static ForwardingDecision noForwarding() {
        return new ForwardingDecision();
    }

    /**
     * Checks if any forwarding is needed.
     */
    public boolean needsForwarding() {
        return rs1Source != ForwardingSource.NONE 
            || rs2Source != ForwardingSource.NONE;
    }

    /**
     * Checks if forwarding is needed for rs1.
     */
    public boolean needsRs1Forwarding() {
        return rs1Source != ForwardingSource.NONE;
    }

    /**
     * Checks if forwarding is needed for rs2.
     */
    public boolean needsRs2Forwarding() {
        return rs2Source != ForwardingSource.NONE;
    }

    @Override
    public String toString() {
        if (!needsForwarding()) {
            return "No forwarding";
        }
        
        StringBuilder sb = new StringBuilder("Forward: ");
        if (needsRs1Forwarding()) {
            sb.append("rs1←").append(rs1Source);
        }
        if (needsRs2Forwarding()) {
            if (needsRs1Forwarding()) sb.append(", ");
            sb.append("rs2←").append(rs2Source);
        }
        return sb.toString();
    }
}
