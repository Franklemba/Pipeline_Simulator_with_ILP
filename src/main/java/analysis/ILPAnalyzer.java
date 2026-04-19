package analysis;

import isa.Instruction;
import isa.OpType;

import java.util.*;

/**
 * Instruction Level Parallelism (ILP) Analyzer
 * 
 * PURPOSE:
 * =======
 * Analyzes a sequence of instructions to determine how much parallelism exists.
 * ILP measures how many instructions could theoretically execute simultaneously
 * if we had unlimited hardware resources.
 * 
 * KEY CONCEPTS:
 * ============
 * 1. Data Dependencies: Instructions that read/write the same registers
 * 2. Independent Instructions: Instructions with no dependencies
 * 3. Critical Path: Longest chain of dependent instructions
 * 4. Theoretical ILP: Instructions / Critical Path Length
 * 5. Actual ILP: What the pipeline achieves in practice
 * 
 * EXAMPLE:
 * =======
 * Program:
 *   ADD R1, R2, R3    # Instruction 1
 *   ADD R4, R5, R6    # Instruction 2 (independent of 1)
 *   ADD R7, R1, R4    # Instruction 3 (depends on 1 and 2)
 * 
 * Dependencies:
 *   1 → 3 (R1)
 *   2 → 3 (R4)
 * 
 * Critical Path: 1 → 3 (length = 2)
 * Theoretical ILP: 3 instructions / 2 cycles = 1.5
 * 
 * Meaning: With perfect hardware, we could execute 1.5 instructions per cycle
 */
public class ILPAnalyzer {
    
    /**
     * Analyzes a program and returns ILP metrics.
     */
    public ILPReport analyze(List<Instruction> instructions) {
        if (instructions == null || instructions.isEmpty()) {
            return new ILPReport(0, 0, 0, new ArrayList<>(), new HashMap<>());
        }
        
        // Build dependency graph
        Map<Instruction, Set<Instruction>> dependencies = buildDependencyGraph(instructions);
        
        // Find critical path
        int criticalPathLength = findCriticalPathLength(instructions, dependencies);
        
        // Calculate theoretical ILP
        double theoreticalILP = criticalPathLength == 0 ? 0.0 
                               : (double) instructions.size() / criticalPathLength;
        
        // Find independent instruction groups
        List<Set<Instruction>> parallelGroups = findParallelGroups(instructions, dependencies);
        
        return new ILPReport(
            instructions.size(),
            criticalPathLength,
            theoreticalILP,
            parallelGroups,
            dependencies
        );
    }
    
    /**
     * Builds a dependency graph showing which instructions depend on which.
     * 
     * Returns: Map of instruction → set of instructions it depends on
     */
    private Map<Instruction, Set<Instruction>> buildDependencyGraph(List<Instruction> instructions) {
        Map<Instruction, Set<Instruction>> dependencies = new HashMap<>();
        Map<String, Instruction> lastWriter = new HashMap<>();  // Register → last instruction that wrote it
        
        for (Instruction inst : instructions) {
            Set<Instruction> deps = new HashSet<>();
            
            // Check RAW dependencies (Read After Write)
            if (inst.rs1 != null && lastWriter.containsKey(inst.rs1)) {
                deps.add(lastWriter.get(inst.rs1));
            }
            if (inst.rs2 != null && lastWriter.containsKey(inst.rs2)) {
                deps.add(lastWriter.get(inst.rs2));
            }
            
            dependencies.put(inst, deps);
            
            // Update last writer for this instruction's destination
            if (inst.writesRegister() && inst.rd != null) {
                lastWriter.put(inst.rd, inst);
            }
        }
        
        return dependencies;
    }
    
    /**
     * Finds the length of the critical path (longest dependency chain).
     */
    private int findCriticalPathLength(List<Instruction> instructions,
                                      Map<Instruction, Set<Instruction>> dependencies) {
        Map<Instruction, Integer> depths = new HashMap<>();
        int maxDepth = 0;
        
        for (Instruction inst : instructions) {
            int depth = calculateDepth(inst, dependencies, depths);
            maxDepth = Math.max(maxDepth, depth);
        }
        
        return maxDepth;
    }
    
    /**
     * Recursively calculates the depth of an instruction in the dependency graph.
     */
    private int calculateDepth(Instruction inst,
                              Map<Instruction, Set<Instruction>> dependencies,
                              Map<Instruction, Integer> memo) {
        if (memo.containsKey(inst)) {
            return memo.get(inst);
        }
        
        Set<Instruction> deps = dependencies.get(inst);
        if (deps == null || deps.isEmpty()) {
            memo.put(inst, 1);
            return 1;
        }
        
        int maxDepDep = 0;
        for (Instruction dep : deps) {
            maxDepDep = Math.max(maxDepDep, calculateDepth(dep, dependencies, memo));
        }
        
        int depth = maxDepDep + 1;
        memo.put(inst, depth);
        return depth;
    }
    
    /**
     * Finds groups of instructions that can execute in parallel.
     */
    private List<Set<Instruction>> findParallelGroups(List<Instruction> instructions,
                                                      Map<Instruction, Set<Instruction>> dependencies) {
        List<Set<Instruction>> groups = new ArrayList<>();
        Set<Instruction> remaining = new HashSet<>(instructions);
        
        while (!remaining.isEmpty()) {
            Set<Instruction> group = new HashSet<>();
            Set<String> writtenRegs = new HashSet<>();
            Set<String> readRegs = new HashSet<>();
            
            for (Instruction inst : new ArrayList<>(remaining)) {
                // Check if this instruction can be added to the current group
                boolean canAdd = true;
                
                // Check for WAR (Write After Read) hazard
                if (inst.writesRegister() && inst.rd != null) {
                    if (readRegs.contains(inst.rd)) {
                        canAdd = false;
                    }
                }
                
                // Check for WAW (Write After Write) hazard
                if (inst.writesRegister() && inst.rd != null) {
                    if (writtenRegs.contains(inst.rd)) {
                        canAdd = false;
                    }
                }
                
                // Check for RAW (Read After Write) hazard
                if (inst.rs1 != null && writtenRegs.contains(inst.rs1)) {
                    canAdd = false;
                }
                if (inst.rs2 != null && writtenRegs.contains(inst.rs2)) {
                    canAdd = false;
                }
                
                if (canAdd) {
                    group.add(inst);
                    remaining.remove(inst);
                    
                    // Update read/write sets
                    if (inst.rs1 != null) readRegs.add(inst.rs1);
                    if (inst.rs2 != null) readRegs.add(inst.rs2);
                    if (inst.writesRegister() && inst.rd != null) {
                        writtenRegs.add(inst.rd);
                    }
                }
            }
            
            if (!group.isEmpty()) {
                groups.add(group);
            } else {
                // Deadlock - shouldn't happen with proper dependency analysis
                break;
            }
        }
        
        return groups;
    }
}
