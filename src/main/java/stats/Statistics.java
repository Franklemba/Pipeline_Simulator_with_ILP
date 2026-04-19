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
    private int forwardingEvents    = 0;   // Phase 2: data forwarding
    private int stallsAvoided       = 0;   // Phase 2: stalls prevented by forwarding

    // ── Mutators ─────────────────────────────────────────────────────────

    public void incrementCycle()            { totalCycles++;           }
    public void retireInstruction()         { instructionsRetired++;   }
    public void addDataStall()              { dataStalls++;            }
    public void addControlStall(int n)      { controlStalls += n;      }
    public void recordBranch(boolean taken) {
        branchCount++;
        if (taken) branchMispredictions++;  // we assumed not-taken
    }
    
    /** Records a forwarding event (Phase 2) */
    public void recordForwarding() {
        forwardingEvents++;
        stallsAvoided++;  // Each forward typically avoids a stall
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
    public int getForwardingEvents()     { return forwardingEvents;     }
    public int getStallsAvoided()        { return stallsAvoided;        }

    // ── Report ────────────────────────────────────────────────────────────

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════╗\n");
        sb.append("║          PERFORMANCE SUMMARY                 ║\n");
        sb.append("╠══════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Total Cycles           : %-18d║\n", totalCycles));
        sb.append(String.format("║  Instructions Retired   : %-18d║\n", instructionsRetired));
        sb.append(String.format("║  Total Stalls           : %-18d║\n", totalStalls()));
        sb.append(String.format("║    ├─ Data Hazard Stalls : %-17d║\n", dataStalls));
        sb.append(String.format("║    └─ Control Stalls     : %-17d║\n", controlStalls));
        
        // Show forwarding stats if forwarding was used
        if (forwardingEvents > 0) {
            sb.append(String.format("║  Forwarding Events      : %-18d║\n", forwardingEvents));
            sb.append(String.format("║  Stalls Avoided         : %-18d║\n", stallsAvoided));
        }
        
        sb.append(String.format("║  CPI (Cycles/Instr)     : %-18.4f║\n", cpi()));
        sb.append(String.format("║  Throughput (IPC)       : %-18.4f║\n", throughput()));
        sb.append("╚══════════════════════════════════════════════╝");
        
        return sb.toString();
    }
}
