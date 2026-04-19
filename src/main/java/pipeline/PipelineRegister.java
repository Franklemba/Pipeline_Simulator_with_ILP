package pipeline;

import forwarding.ForwardingDecision;
import isa.Instruction;

/**
 * Inter-stage latch (pipeline register).
 *
 * Each stage reads from the latch in front of it and writes to the
 * latch behind it.  The latch also carries computed values that
 * earlier stages produce for later stages to consume.
 *
 *  ┌──────┐  IF/ID  ┌──────┐  ID/EX  ┌──────┐  EX/MEM  ┌──────┐  MEM/WB  ┌──────┐
 *  │  IF  │────────▶│  ID  │────────▶│  EX  │─────────▶│ MEM  │─────────▶│  WB  │
 *  └──────┘         └──────┘         └──────┘           └──────┘          └──────┘
 *  
 * Phase 2: Now also stores forwarding decisions for the EX stage.
 */
public class PipelineRegister {

    /** The instruction currently occupying this stage (null = empty slot). */
    private Instruction instruction;

    /** ALU result computed during the EX stage. */
    private int aluResult;

    /** Value read from data memory during the MEM stage. */
    private int memResult;
    
    /** Forwarding decision for this instruction (Phase 2). */
    private ForwardingDecision forwardingDecision;

    // ── Instruction slot ─────────────────────────────────────────────────

    public void setInstruction(Instruction inst) {
        this.instruction = inst;
    }

    public Instruction getInstruction() {
        return instruction;
    }

    /** True when this stage holds no real instruction (empty or NOP). */
    public boolean isEmpty() {
        return instruction == null || instruction.isNop();
    }

    /** Insert a NOP bubble (stall / flush). */
    public void insertBubble() {
        this.instruction = Instruction.NOP;
        this.aluResult   = 0;
        this.memResult   = 0;
        this.forwardingDecision = null;
    }

    /** Clear the latch entirely (end-of-program drain). */
    public void clear() {
        this.instruction = null;
        this.aluResult   = 0;
        this.memResult   = 0;
        this.forwardingDecision = null;
    }

    // ── Computed values ───────────────────────────────────────────────────

    public int  getAluResult()           { return aluResult; }
    public void setAluResult(int v)      { aluResult = v;    }

    public int  getMemResult()           { return memResult; }
    public void setMemResult(int v)      { memResult = v;    }
    
    // ── Forwarding (Phase 2) ──────────────────────────────────────────────
    
    public ForwardingDecision getForwardingDecision() { 
        return forwardingDecision; 
    }
    
    public void setForwardingDecision(ForwardingDecision fwd) { 
        this.forwardingDecision = fwd; 
    }

    // ── Display ───────────────────────────────────────────────────────────

    /** Returns the instruction mnemonic for the cycle table column. */
    public String display() {
        if (instruction == null)      return "---";
        if (instruction.isNop())      return "NOP";
        return instruction.toString();
    }
}
