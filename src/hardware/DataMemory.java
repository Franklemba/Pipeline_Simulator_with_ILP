package hardware;

import java.util.TreeMap;
import java.util.Map;

/**
 * Word-addressable data memory.
 * Sparse representation — only written addresses consume space.
 * Unwritten addresses read as 0.
 */
public class DataMemory {

    private final Map<Integer, Integer> mem = new TreeMap<>();

    /** Read a word from the given address. Returns 0 if never written. */
    public int read(int address) {
        return mem.getOrDefault(address, 0);
    }

    /** Write a word to the given address. */
    public void write(int address, int value) {
        mem.put(address, value);
    }

    /** Pre-initialise a memory location (useful for LOAD tests). */
    public void init(int address, int value) {
        mem.put(address, value);
    }

    /** Returns a readable dump of all written memory locations. */
    public String dump() {
        if (mem.isEmpty()) return "(empty)";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Integer> e : mem.entrySet()) {
            sb.append(String.format("  mem[%4d] = %d%n", e.getKey(), e.getValue()));
        }
        return sb.toString();
    }

    public boolean isEmpty() {
        return mem.isEmpty();
    }
}
