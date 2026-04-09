package isa;

/**
 * Broad category of an instruction.
 * Used by the pipeline to decide which stage behaviour to apply.
 */
public enum OpType {
    ARITHMETIC,   // ADD, SUB, MUL, DIV
    LOGICAL,      // AND, OR, XOR
    LOAD,         // LOAD  rd, imm(rs)
    STORE,        // STORE rs, imm(rd)
    BRANCH,       // BEQ, BNE
    JUMP,         // JUMP label
    NOP           // bubble / no-operation
}
