package stats;

/**
 * Accumulates performance counters and computes derived metrics.
 */
public class Statistics {

    private int totalCycles         = 0;
    private int instructionsRetired = 0;   // completed WB
    private int dataStalls          = 0;
    private int controlStalls       = 0;
    private int branchCount         = 0;
    private int branchMispredictions= 0;   // always-not-taken in Phase 1

    // ── Mutators ─────────────────────────────────────────────────────────

    public void incrementCycle()            { totalCycles++;           }
    public void retireInstruction()         { instructionsRetired++;   }
    public void addDataStall()              { dataStalls++;            }
    public void addControlStall(int n)      { controlStalls += n;      }
    public void recordBranch(boolean taken) {
        branchCount++;
        if (taken) branchMispredictions++;  // we assumed not-taken
    }

    // ── Derived metrics ───────────────────────────────────────────────────

    public double cpi() {
        return instructionsRetired == 0 ? 0.0
               : (double) totalCycles / instructionsRetired;
    }

    public double throughput() {
        return totalCycles == 0 ? 0.0
               : (double) instructionsRetired / totalCycles;
    }

    public int totalStalls() {
        return dataStalls + controlStalls;
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public int getTotalCycles()          { return totalCycles;          }
    public int getInstructionsRetired()  { return instructionsRetired;  }
    public int getDataStalls()           { return dataStalls;           }
    public int getControlStalls()        { return controlStalls;        }
    public int getBranchCount()          { return branchCount;          }
    public int getBranchMispredictions() { return branchMispredictions; }

    // ── Report ────────────────────────────────────────────────────────────

    public String summary() {
        return String.join("\n",
            "╔══════════════════════════════════════════════╗",
            "║          PERFORMANCE SUMMARY                 ║",
            "╠══════════════════════════════════════════════╣",
            String.format("║  Total Cycles           : %-18d║", totalCycles),
            String.format("║  Instructions Retired   : %-18d║", instructionsRetired),
            String.format("║  Total Stalls           : %-18d║", totalStalls()),
            String.format("║    ├─ Data Hazard Stalls : %-17d║", dataStalls),
            String.format("║    └─ Control Stalls     : %-17d║", controlStalls),
            String.format("║  CPI (Cycles/Instr)     : %-18.4f║", cpi()),
            String.format("║  Throughput (IPC)       : %-18.4f║", throughput()),
            "╚══════════════════════════════════════════════╝"
        );
    }
}
