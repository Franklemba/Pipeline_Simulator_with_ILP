package pipeline;

import assembler.Assembler;
import forwarding.ForwardingDecision;
import forwarding.ForwardingUnit;
import hardware.*;
import hazards.HazardDetector;
import isa.*;
import stats.Statistics;

import java.util.List;

/**
 * 5-Stage In-Order Pipeline Simulator
 * 
 * ARCHITECTURE OVERVIEW:
 * =====================
 * This simulator models a classic RISC pipeline with five stages:
 * 
 *   IF (Instruction Fetch) → ID (Instruction Decode) → EX (Execute) → 
 *   MEM (Memory Access) → WB (Write Back)
 * 
 * Each stage is separated by pipeline registers (latches) that hold the state
 * between stages. Instructions flow through the pipeline one stage per clock cycle.
 * 
 * HAZARD HANDLING:
 * ===============
 * Phase 1: Stalls only (no forwarding)
 * Phase 2: Data forwarding + enhanced hazard detection
 * 
 * 1. DATA HAZARDS (RAW - Read After Write):
 *    Phase 1: STALL until data is ready in register file
 *    Phase 2: FORWARD data from later stages (reduces stalls)
 *    Exception: Load-use hazards still require 1-cycle stall
 * 
 * 2. CONTROL HAZARDS (Branches/Jumps):
 *    - Branch prediction: Assume NOT TAKEN (optimistic)
 *    - If branch IS taken: FLUSH the wrongly-fetched instruction (1 cycle penalty)
 *    - Jumps: Always taken, always flush (1 cycle penalty)
 * 
 * 3. STRUCTURAL HAZARDS:
 *    - None in this design (separate instruction/data memory, single-issue)
 * 
 * EXECUTION MODEL:
 * ===============
 * - Each tick() advances the pipeline by exactly one clock cycle
 * - Stages process in REVERSE order (WB→MEM→EX→ID→IF) to ensure each
 *   instruction moves exactly once per cycle
 * - Pipeline drains when all instructions complete WB stage
 * 
 * PERFORMANCE METRICS:
 * ===================
 * - CPI (Cycles Per Instruction): Total cycles / Instructions completed
 * - IPC (Instructions Per Cycle): Instructions completed / Total cycles
 * - Stall counts: Data hazards vs Control hazards
 * - Forwarding events: Number of times forwarding avoided a stall (Phase 2)
 */
public class PipelineSimulator {

    // ── Configuration ─────────────────────────────────────────────────────
    private static final int MAX_CYCLES = 500;
    
    /** Enable/disable data forwarding (Phase 2 feature) */
    private boolean forwardingEnabled = false;
    
    /** Branch predictor (Phase 2 feature) */
    private prediction.BranchPredictor branchPredictor = null;

    // ── Hardware ──────────────────────────────────────────────────────────
    private final RegisterFile  rf;
    private final DataMemory    dmem;
    private final ALU           alu;
    private final HazardDetector hazardDetector;
    private final ForwardingUnit forwardingUnit;  // Phase 2

    // ── Pipeline stage latches ────────────────────────────────────────────
    // Each latch represents the state of one stage at the START of a cycle.
    protected final PipelineRegister stageIF  = new PipelineRegister();
    protected final PipelineRegister stageID  = new PipelineRegister();
    protected final PipelineRegister stageEX  = new PipelineRegister();
    protected final PipelineRegister stageMEM = new PipelineRegister();
    protected final PipelineRegister stageWB  = new PipelineRegister();

    // ── Program state ─────────────────────────────────────────────────────
    protected List<Instruction> program;
    protected int               pc;        // next instruction to fetch

    // ── Statistics ────────────────────────────────────────────────────────
    private final Statistics stats = new Statistics();

    // ── Display toggle ────────────────────────────────────────────────────
    protected boolean verbose = true;

    // ── Constructor ───────────────────────────────────────────────────────

    public PipelineSimulator() {
        this.rf             = new RegisterFile();
        this.dmem           = new DataMemory();
        this.alu            = new ALU();
        this.hazardDetector = new HazardDetector();
        this.forwardingUnit = new ForwardingUnit();
    }

    // ── Public API ────────────────────────────────────────────────────────

    public RegisterFile  getRegisterFile() { return rf;    }
    public DataMemory    getDataMemory()   { return dmem;  }
    public Statistics    getStats()        { return stats; }
    public void          setVerbose(boolean v) { verbose = v; }
    
    /** Enable or disable data forwarding (Phase 2) */
    public void setForwardingEnabled(boolean enabled) {
        this.forwardingEnabled = enabled;
    }
    
    public boolean isForwardingEnabled() {
        return forwardingEnabled;
    }
    
    /** Set the branch predictor to use (Phase 2) */
    public void setBranchPredictor(prediction.BranchPredictor predictor) {
        this.branchPredictor = predictor;
    }
    
    public prediction.BranchPredictor getBranchPredictor() {
        return branchPredictor;
    }

    /** Load a compiled program into the simulator. */
    public void load(List<Instruction> instructions) {
        this.program = instructions;
        this.pc      = 0;
        stageIF .clear();
        stageID .clear();
        stageEX .clear();
        stageMEM.clear();
        stageWB .clear();
    }

    /**
     * Run until all instructions drain through WB or MAX_CYCLES is reached.
     * @return final statistics
     */
    public Statistics run() {
        if (verbose) printTableHeader();

        int cycle = 0;
        while (cycle < MAX_CYCLES) {
            cycle++;
            stats.incrementCycle();

            // Snapshot the stage labels BEFORE advancing (for display)
            String[] before = snapshot();

            tick();

            if (verbose) printCycleRow(cycle, before);

            if (pipelineEmpty() && pc >= program.size()) break;
        }
        return stats;
    }

    // ── Core Tick (one clock cycle) ───────────────────────────────────────

    /**
     * Advance the pipeline by one clock cycle.
     * 
     * CRITICAL: Stages must be processed in REVERSE order (WB → MEM → EX → ID → IF)
     * to ensure each instruction advances exactly once per cycle. If we processed
     * forward (IF → ID → ...), an instruction could move multiple stages in one cycle.
     * 
     * PIPELINE FLOW:
     * =============
     * 1. WB:  Complete the instruction, write results to register file
     * 2. MEM: Access data memory (LOAD/STORE), promote to WB
     * 3. EX:  Execute ALU operation (with forwarding if enabled), promote to MEM
     * 4. FORWARDING CHECK: Detect if forwarding can resolve hazards (Phase 2)
     * 5. HAZARD CHECK: Detect data dependencies before advancing ID/IF
     * 6. ID:  Decode instruction, evaluate branches, handle control hazards
     * 7. IF:  Fetch next instruction (or stall/flush based on hazards)
     * 
     * HAZARD RESPONSES:
     * ================
     * Phase 1 (no forwarding):
     * - RAW Data Hazard: Freeze IF and ID, inject bubble into EX
     * 
     * Phase 2 (with forwarding):
     * - RAW Data Hazard + Forwarding Available: Forward data, no stall
     * - Load-Use Hazard: Stall 1 cycle (forwarding can't help)
     * 
     * Both phases:
     * - Branch Taken: Flush IF and ID, redirect PC to branch target
     * - Normal: All stages advance, fetch next instruction
     */
    protected void tick() {

        // ── WB ────────────────────────────────────────────────────────────
        // Read results from stageWB's own latch (set when MEM→WB was promoted).
        Instruction wbInst = stageWB.getInstruction();
        if (wbInst != null && !wbInst.isNop()) {
            doWriteBack(wbInst, stageWB);
            stats.retireInstruction();
        }

        // ── MEM ───────────────────────────────────────────────────────────
        Instruction memInst = stageMEM.getInstruction();
        if (memInst != null && !memInst.isNop()) {
            doMemoryAccess(memInst, stageEX, stageMEM);
        }
        // Promote MEM → WB
        stageWB.setInstruction(memInst);
        stageWB.setAluResult(stageMEM.getAluResult());
        stageWB.setMemResult(stageMEM.getMemResult());

        // ── EX ────────────────────────────────────────────────────────────
        Instruction exInst = stageEX.getInstruction();
        
        // Phase 2: Execute current EX instruction with its forwarding decision
        // (the forwarding decision was computed in the previous cycle when this instruction was in ID)
        if (exInst != null && !exInst.isNop()) {
            ForwardingDecision exFwdDecision = stageEX.getForwardingDecision();
            if (exFwdDecision == null) {
                exFwdDecision = ForwardingDecision.noForwarding();
            }
            doExecute(exInst, stageEX, exFwdDecision);
        }
        
        // Detect forwarding opportunities for the NEXT instruction
        // (the one currently in ID that will enter EX next cycle)
        Instruction idInst = stageID.getInstruction();
        ForwardingDecision fwdDecision = ForwardingDecision.noForwarding();
        
        if (forwardingEnabled && idInst != null && !idInst.isNop()) {
            fwdDecision = forwardingUnit.detectForwarding(
                idInst,      // Consumer (in ID, about to enter EX)
                exInst,      // Producer (in EX, result available next cycle)
                memInst,     // Producer (in MEM)
                wbInst       // Producer (in WB)
            );
        }
        
        // Promote EX → MEM
        stageMEM.setInstruction(exInst);
        stageMEM.setAluResult(stageEX.getAluResult());
        stageMEM.setMemResult(0);

        // ── Hazard detection (before ID and IF advance) ───────────────────
        boolean rawHazard = hazardDetector.detectRAW(idInst, exInst, memInst);
        boolean needsStall = false;
        
        if (forwardingEnabled) {
            // Phase 2: Check if forwarding can resolve the hazard
            needsStall = hazardDetector.needsStallWithForwarding(
                idInst, 
                exInst, 
                rawHazard || fwdDecision.needsForwarding()
            );
        } else {
            // Phase 1: Any RAW hazard requires a stall
            needsStall = rawHazard;
        }

        // ── ID ────────────────────────────────────────────────────────────
        boolean branchTaken  = false;
        boolean branchPredicted = false;
        int     ctrlPenalty  = 0;

        if (!needsStall && idInst != null && !idInst.isNop()) {
            // For branches, get prediction first
            if (idInst.opType() == OpType.BRANCH) {
                branchPredicted = predictBranch(idInst);
            }
            
            // Evaluate the actual outcome
            branchTaken = doDecode(idInst);
            
            // Calculate penalty based on prediction accuracy
            ctrlPenalty = hazardDetector.controlPenalty(idInst, branchTaken, branchPredicted);
        }

        // ── Apply stall or flush, then advance IF and ID ──────────────────

        if (needsStall) {
            // ── DATA STALL ────────────────────────────────────────────────
            // Freeze IF and ID; inject a bubble into EX.
            stageEX.insertBubble();
            // IF and ID do NOT advance — leave them as-is.
            stats.addDataStall();

        } else if (ctrlPenalty > 0) {
            // ── CONTROL FLUSH ─────────────────────────────────────────────
            // The branch/jump was taken while IF already fetched a wrong instr.
            // Flush IF (replace with bubble), promote ID → EX.
            stageEX.setInstruction(idInst);   // promote the branch itself
            stageID.insertBubble();           // flush ID (was the wrong instr)
            stageIF.insertBubble();           // flush IF
            pc = idInst.imm;                  // redirect PC to target
            stats.addControlStall(ctrlPenalty);
            stats.recordBranch(branchTaken);

        } else {
            // ── NORMAL ADVANCE ────────────────────────────────────────────
            // Promote ID → EX (with forwarding decision), IF → ID, fetch next instruction into IF.
            stageEX.setInstruction(idInst);
            stageEX.setForwardingDecision(fwdDecision);  // Store for next cycle's EX stage
            stageID.setInstruction(stageIF.getInstruction());
            fetchNext();
        }
    }

    // ── Stage Implementations ─────────────────────────────────────────────

    /** IF: fetch the next instruction from the program list. */
    private void fetchNext() {
        if (pc < program.size()) {
            stageIF.setInstruction(program.get(pc));
            pc++;
        } else {
            stageIF.clear();
        }
    }

    /**
     * ID (Instruction Decode): Read register operands and evaluate branch conditions.
     * 
     * For branches, we evaluate the condition HERE (not in EX) because:
     * - We need to know the outcome ASAP to minimize control hazard penalty
     * - Register values are read in this stage
     * - Early resolution = fewer wasted cycles
     * 
     * Phase 2: Now uses branch predictor if available.
     * 
     * @param inst The instruction being decoded
     * @return true if a branch/jump was taken (control hazard occurred)
     */
    private boolean doDecode(Instruction inst) {
        switch (inst.opCode.type) {
            case BRANCH: {
                int a = rf.read(inst.rs1);
                int b = rf.read(inst.rs2);
                boolean actuallyTaken = alu.execute(inst.opCode, a, b) == 1;
                
                // Update branch predictor with actual outcome (if predictor is set)
                if (branchPredictor != null) {
                    boolean isBackward = inst.imm < inst.pc;  // Target before current PC = backward branch
                    branchPredictor.update(inst.pc, actuallyTaken);
                }
                
                return actuallyTaken;
            }
            case JUMP:
                return true;   // always taken
            default:
                return false;
        }
    }
    
    /**
     * Predicts whether a branch will be taken (Phase 2).
     * 
     * @param inst The branch instruction
     * @return true if predicting TAKEN, false if predicting NOT TAKEN
     */
    private boolean predictBranch(Instruction inst) {
        if (branchPredictor == null) {
            // Phase 1 behavior: assume NOT TAKEN
            return false;
        }
        
        // Phase 2: Use branch predictor
        boolean isBackward = inst.imm < inst.pc;  // Target before current PC = backward branch
        return branchPredictor.predict(inst.pc, isBackward);
    }

    /**
     * EX (Execute): Perform ALU computation and store result in the EX latch.
     * 
     * Phase 2: Now supports data forwarding from later pipeline stages.
     * 
     * The ALU handles:
     * - Arithmetic operations (ADD, SUB, MUL, DIV)
     * - Logical operations (AND, OR, XOR)
     * - Effective address calculation for LOAD/STORE (base + offset)
     * 
     * Forwarding: If enabled, operand values may come from:
     * - EX/MEM latch (most recent ALU result)
     * - MEM/WB latch (memory or ALU result)
     * - Register file (normal path)
     * 
     * Results are stored in the pipeline register for the MEM stage to use.
     */
    private void doExecute(Instruction inst, PipelineRegister exLatch, ForwardingDecision fwd) {
        int a = 0, b = 0;
        
        switch (inst.opCode.type) {
            case ARITHMETIC:
            case LOGICAL:
                a = getOperandValue(inst.rs1, fwd.rs1Source);
                b = getOperandValue(inst.rs2, fwd.rs2Source);
                break;
            case LOAD:
                a = getOperandValue(inst.rs1, fwd.rs1Source);
                b = inst.imm;
                break;
            case STORE:
                a = getOperandValue(inst.rs2, fwd.rs2Source);  // base register
                b = inst.imm;
                break;
            default:
                break;
        }
        exLatch.setAluResult(alu.execute(inst.opCode, a, b));
    }
    
    /**
     * Gets an operand value, either from forwarding or from the register file.
     * 
     * Phase 2: Implements the forwarding multiplexer logic.
     * 
     * @param register Register name (e.g., "R1")
     * @param fwdSource Where to get the value from
     * @return The operand value
     */
    private int getOperandValue(String register, forwarding.ForwardingSource fwdSource) {
        if (!forwardingEnabled || fwdSource == forwarding.ForwardingSource.NONE) {
            // No forwarding - read from register file
            return rf.read(register);
        }
        
        // Forward from appropriate pipeline stage
        switch (fwdSource) {
            case FROM_EX:
                // Forward from EX/MEM latch (ALU result from previous instruction)
                stats.recordForwarding();
                return stageMEM.getAluResult();
                
            case FROM_MEM:
                // Forward from MEM/WB latch
                stats.recordForwarding();
                // If it was a LOAD, use memory result; otherwise use ALU result
                Instruction memInst = stageWB.getInstruction();
                if (memInst != null && !memInst.isNop() && memInst.opType() == OpType.LOAD) {
                    return stageWB.getMemResult();
                } else {
                    return stageWB.getAluResult();
                }
                
            case FROM_WB:
                // Forward from WB stage (rare - could also read from register file)
                stats.recordForwarding();
                Instruction wbInst = stageWB.getInstruction();
                if (wbInst != null && !wbInst.isNop() && wbInst.opType() == OpType.LOAD) {
                    return stageWB.getMemResult();
                } else {
                    return stageWB.getAluResult();
                }
                
            default:
                return rf.read(register);
        }
    }

    /**
     * MEM (Memory Access): Read from or write to data memory.
     * 
     * - LOAD:  Read from memory at computed address, store in memResult
     * - STORE: Write register value to memory at computed address
     * - Other: Pass through (no memory operation)
     * 
     * The address comes from the ALU result computed in the EX stage.
     */
    private void doMemoryAccess(Instruction inst,
                                PipelineRegister exLatch,
                                PipelineRegister memLatch) {
        int addr = memLatch.getAluResult();
        switch (inst.opCode.type) {
            case LOAD:
                memLatch.setMemResult(dmem.read(addr));
                break;
            case STORE:
                dmem.write(addr, rf.read(inst.rs1));
                break;
            default:
                break;
        }
    }

    /**
     * WB (Write Back): Write the final result to the register file.
     * 
     * - Arithmetic/Logical: Write ALU result to destination register
     * - LOAD: Write memory data to destination register
     * - STORE/Branch/Jump: No write-back needed
     * 
     * This is the final stage - instruction is now complete and retired.
     */
    private void doWriteBack(Instruction inst, PipelineRegister memLatch) {
        switch (inst.opCode.type) {
            case ARITHMETIC:
            case LOGICAL:
                rf.write(inst.rd, memLatch.getAluResult());
                break;
            case LOAD:
                rf.write(inst.rd, memLatch.getMemResult());
                break;
            default:
                break;
        }
    }

    // ── Drain check ───────────────────────────────────────────────────────

    protected boolean pipelineEmpty() {
        return stageIF .isEmpty()
            && stageID .isEmpty()
            && stageEX .isEmpty()
            && stageMEM.isEmpty()
            && stageWB .isEmpty();
    }

    // ── Display helpers ───────────────────────────────────────────────────

    private static final String[] STAGE_NAMES = {"IF", "ID", "EX", "MEM", "WB"};
    private static final int      COL_W       = 18;   // column width — change this to resize table

    protected String[] snapshot() {
        return new String[]{
            stageIF .display(),
            stageID .display(),
            stageEX .display(),
            stageMEM.display(),
            stageWB .display()
        };
    }

    public void printTableHeader() {
        StringBuilder sep = new StringBuilder("+--------+");
        StringBuilder hdr = new StringBuilder("| Cycle  |");
        for (String s : STAGE_NAMES) {
            sep.append("-".repeat(COL_W + 2)).append("+");
            hdr.append(String.format(" %-" + COL_W + "s|", "  " + s));
        }
        sep.append("--------------+");
        hdr.append("   Hazard     |");
        System.out.println(sep);
        System.out.println(hdr);
        System.out.println(sep);
    }

    protected void printCycleRow(int cycle, String[] stages) {
        StringBuilder row = new StringBuilder(String.format("| %-6d |", cycle));
        for (String s : stages) {
            // Truncate if instruction label is longer than column width
            String cell = s.length() > COL_W ? s.substring(0, COL_W - 1) + "~" : s;
            row.append(String.format(" %-" + COL_W + "s|", cell));
        }
        // Dedicated hazard column (clean separation from data)
        Instruction idI  = stageID.getInstruction();
        Instruction exI  = stageEX.getInstruction();
        Instruction memI = stageMEM.getInstruction();
        String hazard = hazardDetector.detectRAW(idI, exI, memI)
                        ? " DATA STALL   |"
                        : "              |";
        row.append(hazard);
        System.out.println(row);
    }

    public void printResults() {
        System.out.println();
        System.out.println("  Register File:");
        System.out.println("  " + rf.dump());
        if (!dmem.isEmpty()) {
            System.out.println();
            System.out.println("  Data Memory:");
            System.out.print(dmem.dump());
        }
        System.out.println();
        System.out.println(stats.summary());
    }
}