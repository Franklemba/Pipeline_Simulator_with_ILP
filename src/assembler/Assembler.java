package assembler;

import isa.*;
import java.util.*;

/**
 * Two-pass assembler that converts assembly source text into a list of
 * {@link Instruction} objects and a label → PC map.
 *
 * Syntax rules
 * ─────────────────────────────────────────────────────────────────────────
 *   # anything after hash is a comment
 *   LABEL:                         → defines a label at the next instruction
 *   ADD  R1, R2, R3                → arithmetic / logical
 *   LOAD R1, 4(R2)                 → load with offset
 *   STORE R3, 4(R2)                → store with offset
 *   BEQ  R1, R2, LABEL             → branch
 *   JUMP LABEL                     → unconditional jump
 *   NOP                            → no-operation bubble
 */
public class Assembler {

    // ── Result container ─────────────────────────────────────────────────

    public static class Program {
        public final List<Instruction>   instructions;
        public final Map<String, Integer> labelMap;   // label → PC index

        Program(List<Instruction> insts, Map<String, Integer> labels) {
            this.instructions = Collections.unmodifiableList(insts);
            this.labelMap     = Collections.unmodifiableMap(labels);
        }
    }

    // ── Public API ───────────────────────────────────────────────────────

    public Program assemble(String source) {
        List<String>          lines    = clean(source);
        Map<String, Integer>  labelMap = new LinkedHashMap<>();
        List<String>          codeLines = new ArrayList<>();

        // ── Pass 1: collect labels ────────────────────────────────────────
        int pc = 0;
        for (String line : lines) {
            if (line.endsWith(":")) {
                String labelName = line.substring(0, line.length() - 1).trim();
                labelMap.put(labelName, pc);
            } else {
                codeLines.add(line);
                pc++;
            }
        }

        // ── Pass 2: build Instruction objects ────────────────────────────
        List<Instruction> instructions = new ArrayList<>();
        for (int i = 0; i < codeLines.size(); i++) {
            instructions.add(parseLine(codeLines.get(i), i, labelMap));
        }

        return new Program(instructions, labelMap);
    }

    // ── Private helpers ──────────────────────────────────────────────────

    /** Strip comments, trim whitespace, drop blank lines. */
    private List<String> clean(String source) {
        List<String> result = new ArrayList<>();
        for (String raw : source.split("\\r?\\n")) {
            int commentIdx = raw.indexOf('#');
            String line = (commentIdx >= 0 ? raw.substring(0, commentIdx) : raw).trim();
            if (!line.isEmpty()) result.add(line);
        }
        return result;
    }

    /** Parse a single line into an Instruction. */
    private Instruction parseLine(String line, int pc,
                                  Map<String, Integer> labelMap) {

        // Normalise: remove commas, collapse spaces
        String[] parts = line.replaceAll(",", " ").trim().split("\\s+");
        OpCode opCode  = OpCode.fromString(parts[0]);

        Instruction.Builder b = new Instruction.Builder(opCode, pc);

        switch (opCode.type) {

            case NOP:
                break;

            case ARITHMETIC:
            case LOGICAL:
                // ADD R1, R2, R3
                b.rd(parts[1]).rs1(parts[2]).rs2(parts[3]);
                break;

            case LOAD:
                // LOAD R1, 4(R2)  or  LOAD R1, R2
                b.rd(parts[1]);
                parseMemOperand(parts[2], b, true);
                break;

            case STORE:
                // STORE R3, 4(R2)  or  STORE R3, R2
                b.rs1(parts[1]);
                parseMemOperand(parts[2], b, false);
                break;

            case BRANCH:
                // BEQ R1, R2, LABEL
                b.rs1(parts[1]).rs2(parts[2]).label(parts[3]);
                b.imm(labelMap.getOrDefault(parts[3], 0));
                break;

            case JUMP:
                // JUMP LABEL
                b.label(parts[1]);
                b.imm(labelMap.getOrDefault(parts[1], 0));
                break;

            default:
                break;
        }

        return b.build();
    }

    /**
     * Parse a memory operand in the form  "imm(reg)"  or bare "reg".
     * Populates rs1 (base register) and imm (offset) on the builder.
     * When isLoad=false (STORE) the base goes into rs2.
     */
    private void parseMemOperand(String token, Instruction.Builder b,
                                 boolean isLoad) {
        if (token.contains("(")) {
            int paren  = token.indexOf('(');
            int immVal = token.substring(0, paren).isEmpty()
                         ? 0
                         : Integer.parseInt(token.substring(0, paren));
            String reg = token.substring(paren + 1).replace(")", "").trim();
            b.imm(immVal);
            if (isLoad) b.rs1(reg); else b.rs2(reg);
        } else {
            // No offset — just a register
            b.imm(0);
            if (isLoad) b.rs1(token); else b.rs2(token);
        }
    }
}
