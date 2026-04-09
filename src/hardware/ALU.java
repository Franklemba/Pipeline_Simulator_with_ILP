package hardware;

import isa.OpCode;

/**
 * Arithmetic Logic Unit.
 * Performs integer computation for all ALU-class instructions.
 * Also computes effective addresses for LOAD / STORE.
 */
public class ALU {

    /**
     * Execute an operation.
     *
     * @param opCode  the instruction opcode
     * @param a       first operand  (rs1 value, or base address)
     * @param b       second operand (rs2 value, or immediate offset)
     * @return        integer result
     */
    public int execute(OpCode opCode, int a, int b) {
        switch (opCode) {
            // ── Arithmetic ────────────────────────────────────────────────
            case ADD:  return a + b;
            case SUB:  return a - b;
            case MUL:  return a * b;
            case DIV:  return (b != 0) ? a / b : 0;  // guard divide-by-zero

            // ── Logical ───────────────────────────────────────────────────
            case AND:  return a & b;
            case OR:   return a | b;
            case XOR:  return a ^ b;

            // ── Effective address (LOAD / STORE) ──────────────────────────
            case LOAD:
            case STORE: return a + b;   // base + offset

            // ── Branches: compare, return 1 if taken ──────────────────────
            case BEQ:  return (a == b) ? 1 : 0;
            case BNE:  return (a != b) ? 1 : 0;

            default:   return 0;
        }
    }
}
