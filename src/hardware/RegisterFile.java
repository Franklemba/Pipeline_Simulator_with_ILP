package hardware;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * General-purpose register file with 32 integer registers (R0 – R31).
 * R0 is hardwired to 0 and can never be written.
 */
public class RegisterFile {

    private final int[] regs;
    private static final int SIZE = 32;

    public RegisterFile() {
        regs = new int[SIZE];
        // All registers start at 0 (R0 remains 0 always)
    }

    // ── Read ──────────────────────────────────────────────────────────────

    /**
     * Read a register by name ("R0"–"R31").
     * Returns 0 for null / unknown names (safe default).
     */
    public int read(String reg) {
        int idx = parseIndex(reg);
        if (idx < 0) return 0;
        return regs[idx];
    }

    // ── Write ─────────────────────────────────────────────────────────────

    /**
     * Write a value to a register.
     * Silently ignores writes to R0 or invalid names.
     */
    public void write(String reg, int value) {
        int idx = parseIndex(reg);
        if (idx <= 0) return;   // 0 = R0 (hardwired), -1 = invalid
        regs[idx] = value;
    }

    // ── Initialisation helper ─────────────────────────────────────────────

    /** Pre-load a register with a value (useful for test workloads). */
    public void init(String reg, int value) {
        write(reg, value);
    }

    // ── Display ───────────────────────────────────────────────────────────

    /** Returns a compact string of all non-zero registers. */
    public String dump() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < SIZE; i++) {
            if (regs[i] != 0) {
                if (sb.length() > 0) sb.append("  ");
                sb.append(String.format("R%d=%d", i, regs[i]));
            }
        }
        return sb.length() == 0 ? "(all zero)" : sb.toString();
    }

    // ── Internal ──────────────────────────────────────────────────────────

    /** Parse "R0"–"R31" → int index; returns -1 on failure. */
    private int parseIndex(String reg) {
        if (reg == null || reg.isEmpty()) return -1;
        String upper = reg.trim().toUpperCase();
        if (!upper.startsWith("R")) return -1;
        try {
            int idx = Integer.parseInt(upper.substring(1));
            if (idx < 0 || idx >= SIZE) return -1;
            return idx;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
