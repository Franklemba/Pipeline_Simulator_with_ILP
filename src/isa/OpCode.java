package isa;

/**
 * Every opcode supported by the ISA.
 * Each opcode knows its own OpType, making dispatch easy throughout the pipeline.
 */
public enum OpCode {

    // ── Arithmetic ────────────────────────────────────────────────────────
    ADD (OpType.ARITHMETIC),
    SUB (OpType.ARITHMETIC),
    MUL (OpType.ARITHMETIC),
    DIV (OpType.ARITHMETIC),

    // ── Logical ───────────────────────────────────────────────────────────
    AND (OpType.LOGICAL),
    OR  (OpType.LOGICAL),
    XOR (OpType.LOGICAL),

    // ── Memory ────────────────────────────────────────────────────────────
    LOAD  (OpType.LOAD),
    STORE (OpType.STORE),

    // ── Control ───────────────────────────────────────────────────────────
    BEQ  (OpType.BRANCH),
    BNE  (OpType.BRANCH),
    JUMP (OpType.JUMP),

    // ── Internal ──────────────────────────────────────────────────────────
    NOP (OpType.NOP);

    // ─────────────────────────────────────────────────────────────────────
    public final OpType type;

    OpCode(OpType type) {
        this.type = type;
    }

    /** Case-insensitive lookup; returns NOP for unknown strings. */
    public static OpCode fromString(String s) {
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NOP;
        }
    }
}
