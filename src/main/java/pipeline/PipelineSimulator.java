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
 * 5-Stage Pipeline Simulator
 * 
 * This simulates a classic RISC processor pipeline with five stages:
 * IF (fetch) → ID (decode) → EX (execute) → MEM (memory) → WB (writeback)
 * 
 * HOW IT HANDLES PROBLEMS:
 * 
 * Data Hazards (when an instruction needs a result that's not ready):
 *   Phase 1: Stall the pipeline until the data is available
 *   Phase 2: Forward the data from later stages (much faster!)
 *   Exception: Load instructions still need 1 stall cycle
 * 
 * Control Hazards (branches and jumps):
 *   We guess that branches won't be taken
 *   If we're wrong, flush the pipeline and jump to the right place
 *   Costs 1 cycle when we guess wrong
 * 
 * HOW IT WORKS:
 * Each tick() moves the pipeline forward by one clock cycle.
 * We process stages backwards (WB→MEM→EX→ID→IF) so each instruction
 * only moves once per cycle.
 * 
 * PERFORMANCE TRACKING:
 * - CPI: How many cycles per instruction (lower is better)
 * - IPC: How many instructions per cycle (higher is better)
 * - Stalls: Wasted cycles due to hazards
 * - Forwarding: Times we avoided a stall (Phase 2)
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
     * Why backwards? We process stages in reverse order (WB→MEM→EX→ID→IF)
     * to make sure each instruction only moves once per cycle. If we went
     * forward, an instruction could accidentally move through multiple stages.
     * 
     * What happens each cycle:
     * 1. WB:  Finish the instruction, write results to registers
     * 2. MEM: Access memory for loads/stores, move to WB
     * 3. EX:  Do the actual computation, move to MEM
     * 4. Check for forwarding opportunities (Phase 2)
     * 5. Check for hazards before moving ID and IF
     * 6. ID:  Decode instruction, check branches
     * 7. IF:  Fetch next instruction (or stall/flush if there's a problem)
     * 
     * When problems happen:
     * - Data hazard: Freeze IF and ID, put a bubble in EX
     * - Branch taken: Flush IF and ID, jump to the branch target
     * - Normal: Everything moves forward one stage
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
     * Decode stage: Read registers and figure out what branches do.
     * 
     * We evaluate branches here (not in EX) because we want to know ASAP
     * if we need to change direction. The sooner we know, the fewer cycles
     * we waste fetching the wrong instructions.
     * 
     * Phase 2 adds branch prediction - we try to guess which way the branch
     * will go before we actually know.
     * 
     * @param inst The instruction we're decoding
     * @return true if we're taking a branch/jump (need to flush the pipeline)
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
     * Execute stage: Do the actual computation.
     * 
     * Phase 2 adds forwarding - instead of always reading from registers,
     * we can grab results directly from later pipeline stages if they're ready.
     * 
     * The ALU handles:
     * - Math (add, subtract, multiply, divide)
     * - Logic (and, or, xor)
     * - Address calculation for memory operations
     * 
     * Results get stored in the pipeline register for the next stage.
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
     * Get a value for an operand - either from forwarding or from registers.
     * 
     * This is the "forwarding multiplexer" - it decides where to get data from:
     * - If forwarding is off or not needed: read from register file
     * - If forwarding from EX: grab the ALU result from the previous instruction
     * - If forwarding from MEM: grab from memory stage (might be a load result)
     * - If forwarding from WB: grab from writeback stage
     * 
     * @param register Which register we need (like "R1")
     * @param fwdSource Where to get it from
     * @return The actual value
     */
    private int getOperandValue(String register, forwarding.ForwardingSource fwdSource) {
        if (!forwardingEnabled || fwdSource == forwarding.ForwardingSource.NONE) {
            // No forwarding - read from register file
            return rf.read(register);
        }
        
        // Forward from appropriate pipeline stage
        switch (fwdSource) {
            case FROM_EX:
                // Grab the ALU result from the instruction that just finished EX
                stats.recordForwarding();
                return stageMEM.getAluResult();
                
            case FROM_MEM:
                // Grab from the instruction in MEM stage
                // If it was a load, use the memory value; otherwise use ALU result
                stats.recordForwarding();
                Instruction memInst = stageWB.getInstruction();
                if (memInst != null && !memInst.isNop() && memInst.opType() == OpType.LOAD) {
                    return stageWB.getMemResult();
                } else {
                    return stageWB.getAluResult();
                }
                
            case FROM_WB:
                // Grab from writeback stage (rare but possible)
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
     * Memory stage: Read from or write to memory.
     * 
     * - LOAD:  Read from memory, save the value
     * - STORE: Write a register value to memory
     * - Everything else: Just pass through
     * 
     * The address comes from the ALU (calculated in EX stage).
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
     * Writeback stage: Save the final result to a register.
     * 
     * - Math/logic instructions: Write the ALU result
     * - Load instructions: Write the value from memory
     * - Stores/branches/jumps: Nothing to write
     * 
     * This is the last stage - the instruction is done!
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