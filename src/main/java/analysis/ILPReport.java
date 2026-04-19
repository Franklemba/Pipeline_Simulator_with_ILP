package analysis;

import isa.Instruction;

import java.util.*;

/**
 * ILP Analysis Report
 * 
 * Contains the results of instruction-level parallelism analysis.
 */
public class ILPReport {
    
    private final int totalInstructions;
    private final int criticalPathLength;
    private final double theoreticalILP;
    private final List<Set<Instruction>> parallelGroups;
    private final Map<Instruction, Set<Instruction>> dependencies;
    
    public ILPReport(int totalInstructions,
                     int criticalPathLength,
                     double theoreticalILP,
                     List<Set<Instruction>> parallelGroups,
                     Map<Instruction, Set<Instruction>> dependencies) {
        this.totalInstructions = totalInstructions;
        this.criticalPathLength = criticalPathLength;
        this.theoreticalILP = theoreticalILP;
        this.parallelGroups = parallelGroups;
        this.dependencies = dependencies;
    }
    
    public int getTotalInstructions() {
        return totalInstructions;
    }
    
    public int getCriticalPathLength() {
        return criticalPathLength;
    }
    
    public double getTheoreticalILP() {
        return theoreticalILP;
    }
    
    public List<Set<Instruction>> getParallelGroups() {
        return parallelGroups;
    }
    
    public Map<Instruction, Set<Instruction>> getDependencies() {
        return dependencies;
    }
    
    public int getMaxParallelism() {
        int max = 0;
        for (Set<Instruction> group : parallelGroups) {
            max = Math.max(max, group.size());
        }
        return max;
    }
    
    public double getAverageParallelism() {
        if (parallelGroups.isEmpty()) return 0.0;
        
        int total = 0;
        for (Set<Instruction> group : parallelGroups) {
            total += group.size();
        }
        return (double) total / parallelGroups.size();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════╗\n");
        sb.append("║       INSTRUCTION LEVEL PARALLELISM          ║\n");
        sb.append("╠══════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Total Instructions     : %-18d║\n", totalInstructions));
        sb.append(String.format("║  Critical Path Length   : %-18d║\n", criticalPathLength));
        sb.append(String.format("║  Theoretical ILP        : %-18.3f║\n", theoreticalILP));
        sb.append(String.format("║  Parallel Groups        : %-18d║\n", parallelGroups.size()));
        sb.append(String.format("║  Max Parallelism        : %-18d║\n", getMaxParallelism()));
        sb.append(String.format("║  Avg Parallelism        : %-18.3f║\n", getAverageParallelism()));
        sb.append("╚══════════════════════════════════════════════╝");
        return sb.toString();
    }
}
