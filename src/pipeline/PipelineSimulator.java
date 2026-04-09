package pipeline;

import assembler.Assembler;
import hardware.*;
import hazards.HazardDetector;
import isa.*;
import stats.Statistics;

import java.util.List;

/**
 * 5-Stage In-Order Pipeline Simulator — Phase 1
 *
 * Stages : IF → ID → EX → MEM → WB
 * Hazards: stall-only (no forwarding), branch assumed NOT taken.
 *
 * Each call to tick() advances the simulation by exactly one clock cycle.
 * run() drives tick() until the pipeline drains.
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
     * Advance the pipeline by one cycle.
     *
     * Process order: WB → MEM → EX → (hazard check) → ID → IF
     * Processing in reverse ensures each instruction moves exactly once.
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
     * ID: read register operands; for branches/jumps evaluate condition.
     * Returns true if a branch was taken (control hazard).
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

    /** EX: compute the ALU result and store it in the EX latch. */
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

    /** MEM: read or write data memory. */
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

    /** WB: write the result back to the register file. */
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