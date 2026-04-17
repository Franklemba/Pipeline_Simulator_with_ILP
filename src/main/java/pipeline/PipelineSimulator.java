package pipeline;

import assembler.Assembler;
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
 * HAZARD HANDLING (Phase 1):
 * ==========================
 * 1. DATA HAZARDS (RAW - Read After Write):
 *    - Detected when an instruction needs a register that's still being computed
 *    - Solution: STALL the pipeline (insert bubbles) until data is ready
 *    - No forwarding in Phase 1 (conservative approach)
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
 */
public class PipelineSimulator {

    // ── Configuration ─────────────────────────────────────────────────────
    private static final int MAX_CYCLES = 500;

    // ── Hardware ──────────────────────────────────────────────────────────
    private final RegisterFile  rf;
    private final DataMemory    dmem;
    private final ALU           alu;
    private final HazardDetector hazardDetector;

    // ── Pipeline stage latches ────────────────────────────────────────────
    // Each latch represents the state of one stage at the START of a cycle.
    private final PipelineRegister stageIF  = new PipelineRegister();
    private final PipelineRegister stageID  = new PipelineRegister();
    private final PipelineRegister stageEX  = new PipelineRegister();
    private final PipelineRegister stageMEM = new PipelineRegister();
    private final PipelineRegister stageWB  = new PipelineRegister();

    // ── Program state ─────────────────────────────────────────────────────
    private List<Instruction> program;
    private int               pc;        // next instruction to fetch

    // ── Statistics ────────────────────────────────────────────────────────
    private final Statistics stats = new Statistics();

    // ── Display toggle ────────────────────────────────────────────────────
    private boolean verbose = true;

    // ── Constructor ───────────────────────────────────────────────────────

    public PipelineSimulator() {
        this.rf             = new RegisterFile();
        this.dmem           = new DataMemory();
        this.alu            = new ALU();
        this.hazardDetector = new HazardDetector();
    }

    // ── Public API ────────────────────────────────────────────────────────

    public RegisterFile  getRegisterFile() { return rf;    }
    public DataMemory    getDataMemory()   { return dmem;  }
    public Statistics    getStats()        { return stats; }
    public void          setVerbose(boolean v) { verbose = v; }

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
     * 3. EX:  Execute ALU operation, promote to MEM
     * 4. HAZARD CHECK: Detect data dependencies before advancing ID/IF
     * 5. ID:  Decode instruction, evaluate branches, handle control hazards
     * 6. IF:  Fetch next instruction (or stall/flush based on hazards)
     * 
     * HAZARD RESPONSES:
     * ================
     * - RAW Data Hazard: Freeze IF and ID, inject bubble into EX
     * - Branch Taken:    Flush IF and ID, redirect PC to branch target
     * - Normal:          All stages advance, fetch next instruction
     */
    private void tick() {

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
        if (exInst != null && !exInst.isNop()) {
            doExecute(exInst, stageEX);
        }
        // Promote EX → MEM
        stageMEM.setInstruction(exInst);
        stageMEM.setAluResult(stageEX.getAluResult());
        stageMEM.setMemResult(0);

        // ── Hazard detection (before ID and IF advance) ───────────────────
        Instruction idInst  = stageID.getInstruction();
        boolean     rawStall = hazardDetector.detectRAW(idInst,
                                                        exInst,
                                                        stageMEM.getInstruction());

        // ── ID ────────────────────────────────────────────────────────────
        boolean branchTaken  = false;
        int     ctrlPenalty  = 0;

        if (!rawStall && idInst != null && !idInst.isNop()) {
            branchTaken = doDecode(idInst);
            ctrlPenalty = hazardDetector.controlPenalty(idInst, branchTaken);
        }

        // ── Apply stall or flush, then advance IF and ID ──────────────────

        if (rawStall) {
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
            // Promote ID → EX, IF → ID, fetch next instruction into IF.
            stageEX.setInstruction(idInst);
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
     * @param inst The instruction being decoded
     * @return true if a branch/jump was taken (control hazard occurred)
     */
    private boolean doDecode(Instruction inst) {
        switch (inst.opCode.type) {
            case BRANCH: {
                int a = rf.read(inst.rs1);
                int b = rf.read(inst.rs2);
                boolean taken = alu.execute(inst.opCode, a, b) == 1;
                return taken;
            }
            case JUMP:
                return true;   // always taken
            default:
                return false;
        }
    }

    /**
     * EX (Execute): Perform ALU computation and store result in the EX latch.
     * 
     * The ALU handles:
     * - Arithmetic operations (ADD, SUB, MUL, DIV)
     * - Logical operations (AND, OR, XOR)
     * - Effective address calculation for LOAD/STORE (base + offset)
     * 
     * Results are stored in the pipeline register for the MEM stage to use.
     */
    private void doExecute(Instruction inst, PipelineRegister exLatch) {
        int a = 0, b = 0;
        switch (inst.opCode.type) {
            case ARITHMETIC:
            case LOGICAL:
                a = rf.read(inst.rs1);
                b = rf.read(inst.rs2);
                break;
            case LOAD:
                a = rf.read(inst.rs1);
                b = inst.imm;
                break;
            case STORE:
                a = rf.read(inst.rs2);   // base register
                b = inst.imm;
                break;
            default:
                break;
        }
        exLatch.setAluResult(alu.execute(inst.opCode, a, b));
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

    private boolean pipelineEmpty() {
        return stageIF .isEmpty()
            && stageID .isEmpty()
            && stageEX .isEmpty()
            && stageMEM.isEmpty()
            && stageWB .isEmpty();
    }

    // ── Display helpers ───────────────────────────────────────────────────

    private static final String[] STAGE_NAMES = {"IF", "ID", "EX", "MEM", "WB"};
    private static final int      COL_W       = 18;   // column width — change this to resize table

    private String[] snapshot() {
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

    private void printCycleRow(int cycle, String[] stages) {
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