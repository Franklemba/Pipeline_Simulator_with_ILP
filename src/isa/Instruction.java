package isa;

/**
 * A single decoded instruction.
 *
 * Encoding conventions
 * ─────────────────────────────────────────────────────────
 * Arithmetic / Logical : op  rd,  rs1, rs2      ADD  R1, R2, R3
 * LOAD                 : op  rd,  imm, rs1      LOAD R1, 4(R2)
 * STORE                : op  rs1, imm, rs2      STORE R3, 4(R2)
 * BEQ / BNE            : op  rs1, rs2, label    BEQ  R1, R2, LOOP
 * JUMP                 : op  label              JUMP END
 * NOP                  : NOP
 * ─────────────────────────────────────────────────────────
 * Unused fields are left null / 0.
 */
public class Instruction {

    // ── Fields ────────────────────────────────────────────────────────────
    public final OpCode  opCode;
    public final String  rd;        // destination register
    public final String  rs1;       // source register 1
    public final String  rs2;       // source register 2
    public final int     imm;       // immediate value / memory offset
    public final String  label;     // branch / jump target label
    public final int     pc;        // program-counter position

    // ── Constructor ───────────────────────────────────────────────────────
    private Instruction(Builder b) {
        this.opCode = b.opCode;
        this.rd     = b.rd;
        this.rs1    = b.rs1;
        this.rs2    = b.rs2;
        this.imm    = b.imm;
        this.label  = b.label;
        this.pc     = b.pc;
    }

    // ── Convenience helpers ───────────────────────────────────────────────

    public OpType opType() {
        return opCode.type;
    }

    public boolean isNop() {
        return opCode == OpCode.NOP;
    }

    /** Returns true if this instruction writes to a register (has a destination). */
    public boolean writesRegister() {
        OpType t = opType();
        return t == OpType.ARITHMETIC
            || t == OpType.LOGICAL
            || t == OpType.LOAD;
    }

    /** Readable mnemonic for display (pipeline table column). */
    @Override
    public String toString() {
        switch (opCode.type) {
            case NOP:
                return "NOP";
            case ARITHMETIC:
            case LOGICAL:
                return String.format("%s %s,%s,%s", opCode, rd, rs1, rs2);
            case LOAD:
                return String.format("LOAD %s,%d(%s)", rd, imm, rs1);
            case STORE:
                return String.format("STORE %s,%d(%s)", rs1, imm, rs2);
            case BRANCH:
                return String.format("%s %s,%s,%s", opCode, rs1, rs2, label);
            case JUMP:
                return String.format("JUMP %s", label);
            default:
                return opCode.toString();
        }
    }

    // ── Static NOP singleton ──────────────────────────────────────────────
    public static final Instruction NOP = new Builder(OpCode.NOP, 0).build();

    // ── Builder ───────────────────────────────────────────────────────────
    public static class Builder {
        private final OpCode opCode;
        private final int    pc;
        private String rd    = null;
        private String rs1   = null;
        private String rs2   = null;
        private int    imm   = 0;
        private String label = null;

        public Builder(OpCode opCode, int pc) {
            this.opCode = opCode;
            this.pc     = pc;
        }
        public Builder rd   (String v) { rd    = v; return this; }
        public Builder rs1  (String v) { rs1   = v; return this; }
        public Builder rs2  (String v) { rs2   = v; return this; }
        public Builder imm  (int    v) { imm   = v; return this; }
        public Builder label(String v) { label = v; return this; }
        public Instruction build()     { return new Instruction(this); }
    }
}
