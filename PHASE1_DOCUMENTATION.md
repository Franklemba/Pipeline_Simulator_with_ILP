# CS 510 – Advanced Computer Architecture
## Phase 1: Basic 5-Stage Pipeline Simulator
### Documentation & Reference Guide

> **Assignment:** Design and Implementation of an Advanced CPU Pipeline Simulator  
> **Phase:** 1 — Basic Pipeline (Foundation)  
> **Language:** Java  
> **Author Reference:** Group Assignment — Copperbelt University, MSc Computer Science

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [File Structure](#2-file-structure)
3. [The 5-Stage Pipeline — How It Works](#3-the-5-stage-pipeline--how-it-works)
4. [Instruction Set Architecture (ISA)](#4-instruction-set-architecture-isa)
   - [Arithmetic Instructions](#41-arithmetic-instructions-add-sub-mul-div)
   - [Logical Instructions](#42-logical-instructions-and-or-xor)
   - [Load/Store Instructions](#43-loadstore-instructions-load-store)
   - [Control Instructions](#44-control-instructions-beq-bne-jump)
5. [Pipeline Hazards](#5-pipeline-hazards)
   - [Data Hazards (RAW)](#51-data-hazards-raw---read-after-write)
   - [Control Hazards](#52-control-hazards)
   - [Structural Hazards](#53-structural-hazards)
6. [Hazards Per Instruction Type](#6-hazards-per-instruction-type)
7. [Reading the Pipeline Output](#7-reading-the-pipeline-output)
8. [Performance Metrics](#8-performance-metrics)
9. [How to Compile and Run](#9-how-to-compile-and-run)

---

## 1. Project Overview

This simulator models a **5-stage in-order pipelined processor**. It fetches instructions
from an internal program list (assembled from a simple text-based assembly language),
moves them through five pipeline stages one cycle at a time, and reports exactly what
happens at every clock cycle — including when and why the pipeline stalls.

### What Phase 1 implements
| Feature | Status |
|---|---|
| 5-stage pipeline (IF, ID, EX, MEM, WB) | ✅ Complete |
| Full ISA (13 instructions across 4 types) | ✅ Complete |
| Cycle-by-cycle display | ✅ Complete |
| Data Hazard detection and stalling | ✅ Complete |
| Control Hazard detection and flushing | ✅ Complete |
| Structural Hazard (noted, N/A for single-issue) | ✅ Documented |
| Performance metrics (CPI, IPC, stall counts) | ✅ Complete |
| 4 test workloads | ✅ Complete |

### What Phase 2 will add
- Data Forwarding (bypassing)
- Full Hazard Detection Unit with forwarding paths
- Branch Prediction (static and dynamic)
- Instruction-Level Parallelism (ILP)
- Superscalar execution (dual-issue)
- Loop Unrolling experiment

---

## 2. File Structure

```
PipelineSimulator/
├── PHASE1_DOCUMENTATION.md        ← This file
└── src/
    ├── Main.java                   ← Entry point; defines all 4 workloads and runs them
    │
    ├── isa/                        ← Instruction Set Architecture layer
    │   ├── OpType.java             ← Enum: broad category of instruction
    │   ├── OpCode.java             ← Enum: every individual opcode
    │   └── Instruction.java        ← Data model for a single decoded instruction
    │
    ├── assembler/
    │   └── Assembler.java          ← Converts assembly text → List<Instruction>
    │
    ├── hardware/                   ← Physical hardware components
    │   ├── RegisterFile.java       ← 32 general-purpose registers (R0–R31)
    │   ├── DataMemory.java         ← Word-addressable data memory (sparse map)
    │   └── ALU.java                ← Performs all arithmetic, logical, address ops
    │
    ├── pipeline/
    │   ├── PipelineRegister.java   ← Inter-stage latch (holds instruction + results)
    │   └── PipelineSimulator.java  ← Core engine: tick(), hazard logic, stage methods
    │
    ├── hazards/
    │   └── HazardDetector.java     ← RAW detection + control penalty calculation
    │
    └── stats/
        └── Statistics.java         ← Tracks cycles, stalls, retired instructions; computes CPI
```

---

### File Descriptions

#### `Main.java`
The entry point of the program. It defines four assembly workload strings
(arithmetic, memory, branch, loop), assembles each one, configures the simulator
with any needed register/memory pre-loads, runs the simulation, and prints a
final comparison table across all workloads.

```
Usage:
  java Main               → runs all four workloads
  java Main arithmetic    → runs one workload by name
  java Main memory
  java Main branch
  java Main loop
```

---

#### `isa/OpType.java`
A simple enum that groups instructions into broad categories. Used throughout
the pipeline so each stage knows what kind of work to do without checking
individual opcodes everywhere.

```
ARITHMETIC  →  ADD, SUB, MUL, DIV
LOGICAL     →  AND, OR, XOR
LOAD        →  LOAD
STORE       →  STORE
BRANCH      →  BEQ, BNE
JUMP        →  JUMP
NOP         →  internal bubble / no-operation
```

---

#### `isa/OpCode.java`
An enum listing every individual opcode. Each opcode carries its `OpType` so
you can always ask `opCode.type` to get the category.

```java
ADD(OpType.ARITHMETIC),  SUB(OpType.ARITHMETIC), ...
LOAD(OpType.LOAD),       STORE(OpType.STORE), ...
BEQ(OpType.BRANCH),      JUMP(OpType.JUMP), ...
```

---

#### `isa/Instruction.java`
An immutable data class representing one decoded instruction. Uses the Builder
pattern so construction is readable and fields not needed by a given opcode
are simply left null.

Key fields:
| Field | Meaning | Example |
|---|---|---|
| `opCode` | The instruction opcode | `ADD` |
| `rd` | Destination register | `R1` |
| `rs1` | Source register 1 | `R2` |
| `rs2` | Source register 2 | `R3` |
| `imm` | Immediate value / memory offset | `4` |
| `label` | Branch/jump target label name | `LOOP` |
| `pc` | Position in the program list | `0` |

---

#### `assembler/Assembler.java`
A two-pass assembler. Pass 1 collects label definitions and maps them to PC
values. Pass 2 turns each line of text into an `Instruction` object.

Supported syntax:
```
ADD  R1, R2, R3          # arithmetic / logical
LOAD R1, 4(R2)           # load from mem[R2 + 4] into R1
STORE R3, 4(R2)          # store R3 into mem[R2 + 4]
BEQ  R1, R2, LABEL       # branch if R1 == R2
JUMP LABEL               # unconditional jump
LOOP:                    # label definition
# anything after hash    # comment — ignored
```

---

#### `hardware/RegisterFile.java`
Simulates the CPU register file. Contains 32 integer registers named R0–R31.
R0 is hardwired to 0 and cannot be overwritten (standard MIPS/RISC convention).
Provides `read(String reg)` and `write(String reg, int value)` methods.

---

#### `hardware/DataMemory.java`
Simulates data memory using a Java `TreeMap<Integer, Integer>`. Only addresses
that have been written appear in the map; all others return 0 by default.
Provides `read(int address)` and `write(int address, int value)`.

---

#### `hardware/ALU.java`
The Arithmetic Logic Unit. Takes an opcode and two integer operands and returns
a result. Also handles address computation for LOAD and STORE (base + offset),
and evaluates branch conditions for BEQ and BNE.

---

#### `pipeline/PipelineRegister.java`
The latch between two adjacent pipeline stages. Holds:
- The instruction currently in that stage
- The ALU result computed in EX (passed forward to MEM and WB)
- The memory result read in MEM (passed forward to WB)

Provides `insertBubble()` to inject a NOP when a stall or flush occurs.

---

#### `pipeline/PipelineSimulator.java`
The core simulation engine. Key responsibilities:

- `run()` — drives the clock; calls `tick()` each cycle until the pipeline drains
- `tick()` — processes all five stages in reverse order (WB → IF) so each
  instruction moves exactly once per cycle
- `fetchNext()` — IF stage: loads next instruction from program list
- `doDecode()` — ID stage: reads registers; evaluates branches
- `doExecute()` — EX stage: calls ALU; stores result in EX latch
- `doMemoryAccess()` — MEM stage: reads or writes DataMemory
- `doWriteBack()` — WB stage: writes ALU or memory result to RegisterFile

Hazard handling is embedded in `tick()`:
- If `HazardDetector.detectRAW()` returns true → freeze IF and ID, inject NOP into EX
- If a branch/jump is taken → flush IF and ID, redirect PC to target

---

#### `hazards/HazardDetector.java`
Contains two methods:

`detectRAW(idInst, exInst, memInst)`
- Looks at source registers of the instruction in ID
- Checks whether EX or MEM hold an instruction that writes to any of those registers
- Returns true if a stall is needed

`controlPenalty(inst, taken)`
- Called after a branch or jump is decoded
- Returns 1 if the branch was taken (or is a JUMP) — meaning one pipeline flush is needed
- Returns 0 if the branch was not taken (no penalty)

---

#### `stats/Statistics.java`
Accumulates counters every cycle and computes derived metrics at the end:

| Metric | Formula |
|---|---|
| CPI | Total Cycles ÷ Instructions Retired |
| Throughput (IPC) | Instructions Retired ÷ Total Cycles |
| Total Stalls | Data Stalls + Control Stalls |

---

## 3. The 5-Stage Pipeline — How It Works

```
Clock →   1     2     3     4     5     6     7     8     9
Instr 0   IF    ID    EX   MEM    WB
Instr 1         IF    ID    EX   MEM    WB
Instr 2               IF    ID    EX   MEM    WB
Instr 3                     IF    ID    EX   MEM    WB
Instr 4                           IF    ID    EX   MEM    WB
```

In an ideal pipeline with no hazards, a new instruction completes every cycle
after the initial fill (CPI = 1.0). Hazards break this ideal by forcing stalls
or flushes.

### Stage responsibilities

| Stage | Name | What happens |
|---|---|---|
| **IF** | Instruction Fetch | Read the next instruction from the program list using the PC counter |
| **ID** | Instruction Decode | Identify the opcode; read source register values; evaluate branch conditions |
| **EX** | Execute | Run the ALU operation; compute effective memory addresses |
| **MEM** | Memory Access | Read from or write to data memory (only LOAD and STORE do real work here) |
| **WB** | Write Back | Write the final result (ALU result or loaded value) back to the register file |

---

## 4. Instruction Set Architecture (ISA)

### Assembly Syntax Quick Reference

```
# Format             Example              Notes
ADD  rd, rs1, rs2    ADD  R1, R2, R3      R1 = R2 + R3
SUB  rd, rs1, rs2    SUB  R1, R2, R3      R1 = R2 - R3
MUL  rd, rs1, rs2    MUL  R1, R2, R3      R1 = R2 * R3
DIV  rd, rs1, rs2    DIV  R1, R2, R3      R1 = R2 / R3  (R3 must not be 0)
AND  rd, rs1, rs2    AND  R1, R2, R3      R1 = R2 & R3  (bitwise)
OR   rd, rs1, rs2    OR   R1, R2, R3      R1 = R2 | R3  (bitwise)
XOR  rd, rs1, rs2    XOR  R1, R2, R3      R1 = R2 ^ R3  (bitwise)
LOAD rd, imm(rs1)    LOAD R1, 4(R2)       R1 = mem[R2 + 4]
STORE rs1, imm(rs2)  STORE R3, 4(R2)      mem[R2 + 4] = R3
BEQ  rs1, rs2, lbl   BEQ  R1, R2, LOOP    if R1 == R2, jump to LOOP
BNE  rs1, rs2, lbl   BNE  R1, R2, LOOP    if R1 != R2, jump to LOOP
JUMP label           JUMP END             always jump to END
NOP                  NOP                  do nothing (1 cycle consumed)
```

---

### 4.1 Arithmetic Instructions: ADD, SUB, MUL, DIV

These instructions take **two source registers**, perform integer arithmetic,
and write the result to a **destination register**.

```
ADD R1, R2, R3    →    R1 = R2 + R3
SUB R1, R2, R3    →    R1 = R2 - R3
MUL R1, R2, R3    →    R1 = R2 × R3
DIV R1, R2, R3    →    R1 = R2 ÷ R3   (integer division; dividing by 0 returns 0)
```

#### Pipeline behaviour
| Stage | What happens |
|---|---|
| IF | Instruction is fetched from program memory |
| ID | Opcode identified; R2 and R3 are read from the register file |
| EX | ALU computes the result (e.g. R2 + R3) and stores it in the EX latch |
| MEM | No memory operation — instruction passes through unchanged |
| WB | ALU result is written into the destination register (R1) |

#### Hazard profile

**Data Hazard (RAW) — very common.**
Because the result is not written until WB, any instruction that immediately
follows and reads the destination register will find an out-of-date value.

```
ADD R1, R2, R3      ← writes R1 at WB (cycle 5)
ADD R4, R1, R5      ← reads R1 at ID  (cycle 3) — TOO EARLY → STALL
```

Without forwarding, the dependent instruction must wait in ID for **2 stall
cycles** until the producer completes WB. This is why the arithmetic workload
produces CPI = 2.80 — every instruction in the chain causes a 2-cycle stall.

**Control Hazard — none.**
Arithmetic instructions never change the PC. The pipeline always fetches the
correct next instruction.

**Structural Hazard — none.**
Only one arithmetic instruction is in EX at a time in a single-issue pipeline.

---

### 4.2 Logical Instructions: AND, OR, XOR

Logical instructions are structurally identical to arithmetic instructions.
They differ only in what the ALU computes.

```
AND R1, R2, R3    →    R1 = R2 & R3    (bitwise AND:  1 only where both bits are 1)
OR  R1, R2, R3    →    R1 = R2 | R3    (bitwise OR:   1 where either bit is 1)
XOR R1, R2, R3    →    R1 = R2 ^ R3    (bitwise XOR:  1 where bits differ)
```

#### Practical use cases in programs
| Instruction | Common use |
|---|---|
| AND | Masking bits, clearing flags, checking if a bit is set |
| OR | Setting specific bits, combining flags |
| XOR | Toggling bits, comparing values (R XOR R = 0 if equal) |

#### Pipeline behaviour
Identical to arithmetic instructions — IF, ID, EX (ALU), MEM (pass-through), WB.

#### Hazard profile
**Exactly the same as arithmetic instructions.**

- **Data Hazard (RAW):** Any instruction that immediately reads the destination
  register of an AND/OR/XOR will stall for 2 cycles without forwarding.
- **Control Hazard:** None — logical instructions never affect the PC.
- **Structural Hazard:** None.

```
AND R1, R2, R3      ← writes R1
OR  R4, R1, R5      ← reads R1 → 2-cycle DATA STALL
XOR R6, R1, R4      ← reads R1, R4 → further stalls if R4 not yet written
```

---

### 4.3 Load/Store Instructions: LOAD, STORE

Memory instructions interact with **data memory** in the MEM stage.
They require computing an **effective address** = base register + immediate offset.

```
LOAD  R1, 4(R2)    →    R1 = mem[R2 + 4]    (read from memory INTO register)
STORE R3, 4(R2)    →    mem[R2 + 4] = R3    (write FROM register INTO memory)
```

#### LOAD — Pipeline behaviour
| Stage | What happens |
|---|---|
| IF | Instruction fetched |
| ID | Opcode identified; base register R2 is read |
| EX | ALU computes effective address: R2 + 4 |
| MEM | Data memory is READ at that address; result placed in MEM latch |
| WB | Memory value written into destination register R1 |

#### STORE — Pipeline behaviour
| Stage | What happens |
|---|---|
| IF | Instruction fetched |
| ID | Opcode identified; both base (R2) and data (R3) registers are read |
| EX | ALU computes effective address: R2 + 4 |
| MEM | Data memory is WRITTEN: mem[R2+4] ← R3 |
| WB | Nothing to write back (STORE has no destination register) |

#### Hazard profile

**LOAD — Data Hazard (RAW) — most dangerous case (load-use hazard).**

The loaded value is not available until the end of MEM (one stage later than a
normal ALU instruction). If the very next instruction reads the loaded register,
it needs the value before LOAD has finished MEM. This is called a **load-use hazard**.

```
LOAD R1, 0(R2)      ← value available after MEM stage
ADD  R4, R1, R3     ← reads R1 at ID — even with forwarding this stalls 1 extra cycle
                       without forwarding it stalls 2 cycles
```

**STORE — Data Hazard (RAW) — on the value being stored.**

If an earlier instruction is still computing the value that STORE wants to write,
STORE must stall until that value is available.

```
ADD   R4, R1, R3     ← computes R4
STORE R4, 8(R2)      ← reads R4 to store it → RAW stall if ADD is not yet in WB
```

**Control Hazard — none for both.**
LOAD and STORE never change the PC.

**Structural Hazard — potential (noted for Phase 2).**
If the same memory port is shared between instruction fetch and data memory
access, a structural hazard occurs. In this simulator they are separate
(Harvard-style architecture) so no structural hazard exists in Phase 1.

---

### 4.4 Control Instructions: BEQ, BNE, JUMP

Control instructions change the flow of execution by modifying the Program Counter.

```
BEQ  R1, R2, LABEL    →    if R1 == R2, PC ← address of LABEL
BNE  R1, R2, LABEL    →    if R1 != R2, PC ← address of LABEL
JUMP LABEL            →    PC ← address of LABEL  (always)
```

#### BEQ / BNE — Pipeline behaviour
| Stage | What happens |
|---|---|
| IF | Instruction fetched; PC advances to next sequential instruction |
| ID | Registers R1 and R2 are read; branch condition is **evaluated here** |
| — | If taken: PC redirected to label; IF stage is **flushed** (1 bubble) |
| EX | Either the branch itself (as a NOP) or a bubble travels through |
| MEM | Pass-through |
| WB | Nothing to write back |

#### JUMP — Pipeline behaviour
| Stage | What happens |
|---|---|
| IF | Instruction fetched; next sequential instruction also begins fetching |
| ID | Jump target is resolved; PC redirected immediately |
| — | IF stage is **always flushed** (1 bubble — JUMP is always taken) |
| EX–WB | Bubble travels through pipeline |

#### Hazard profile

**Control Hazard — always.**

This is the defining hazard of control instructions. The problem arises because
the pipeline speculatively fetches the **next sequential instruction** into IF
while the branch is still being decoded in ID. When the branch resolves in ID
and turns out to be taken, that speculatively fetched instruction must be
thrown away.

```
Phase 1 strategy: Assume branch NOT taken (optimistic fetch)
  → If branch is NOT taken: 0 penalty cycles (the fetch was correct)
  → If branch IS taken:     1 penalty cycle  (the wrong fetch must be flushed)
  → JUMP always:            1 penalty cycle  (always redirects)
```

Illustrated:
```
Cycle 4   IF: ADD R3,R1,R2     ← speculatively fetched (wrong!)
Cycle 5   IF: ---              ← flushed and replaced with NOP
          ID: NOP              ← bubble propagates
          EX: BEQ R1,R2,END    ← branch itself continues through pipeline
```

**Data Hazard (RAW) — on the compared registers.**

If the registers being compared by BEQ or BNE were written by a recent
instruction, the branch cannot evaluate the correct values yet.

```
ADD  R1, R2, R3      ← writes R1
ADD  R2, R4, R5      ← writes R2
BEQ  R1, R2, LABEL   ← reads R1 and R2 — both may still be in EX/MEM → DATA STALL
```

This compounds the control hazard: the branch first stalls waiting for data,
then flushes the wrong fetch once the condition is evaluated.

**BEQ / BNE — Control Hazard only when taken.**
- If the condition evaluates to false (not taken): 0 control stall cycles
- If the condition evaluates to true (taken): 1 control stall cycle

**JUMP — Control Hazard always.**
- Always causes 1 control stall cycle regardless of any condition

---

## 5. Pipeline Hazards

### 5.1 Data Hazards (RAW — Read After Write)

A RAW hazard occurs when instruction B tries to **read** a register that
instruction A is still in the process of **writing**.

#### Why it happens in a pipeline
In a non-pipelined processor, instruction A completes entirely before B begins.
In a pipeline, A is still progressing through EX and MEM while B has already
entered ID and is trying to read the register A hasn't written yet.

#### Timing of the problem (without forwarding)
```
         IF   ID   EX  MEM   WB
Instr A:  1    2    3    4    5   ← R1 written at cycle 5
Instr B:  2    3    4    5    6   ← R1 read at cycle 3 — WRONG VALUE
Instr C:  3    4    5    6    7   ← R1 read at cycle 4 — WRONG VALUE
```

Instructions B and C try to read R1 before A has written it.

#### How Phase 1 resolves it — Pipeline Stalling
The `HazardDetector` freezes ID (and IF behind it) and injects a NOP bubble
into EX. This delays B until A has completed WB and the register file holds
the correct value.

```
         IF   ID   ID   ID   EX  MEM   WB     ← B stalls in ID for 2 cycles
Instr A:  1    2    3    4    5    6    7      ← A writes R1 at cycle 7
Instr B:  2    3    3    3    8    9   10      ← B reads R1 correctly at cycle 8
                ↑    ↑
              stall stall (NOP bubbles injected into EX)
```

#### Detection logic in `HazardDetector.java`
```java
public boolean detectRAW(Instruction idInst, Instruction exInst, Instruction memInst) {
    // Get the registers that the ID instruction needs to read
    Set<String> sources = { idInst.rs1, idInst.rs2 };

    // If the instruction in EX writes to any of those registers → STALL
    if (exInst.writesRegister() && sources.contains(exInst.rd))  return true;

    // If the instruction in MEM writes to any of those registers → STALL
    if (memInst.writesRegister() && sources.contains(memInst.rd)) return true;

    return false;
}
```

---

### 5.2 Control Hazards

A control hazard occurs when the pipeline fetches the **wrong instruction**
because it did not yet know whether a branch would be taken.

#### Why it happens
By the time a branch is decoded in ID, the pipeline has already fetched the
next sequential instruction into IF. If the branch is taken, that fetched
instruction is wrong and must be discarded.

#### Phase 1 strategy — Assume Not Taken
The pipeline always fetches the next sequential instruction. If the branch
turns out to be not taken, this was correct and costs nothing. If the branch
is taken (or it was a JUMP), the fetched instruction is flushed and a 1-cycle
bubble is inserted.

```
Cycle 4   ID: BEQ R1, R2, END    ← branch evaluated here
          IF: ADD R3, R1, R2     ← speculatively fetched (sequential)

→ Branch IS taken (R1 == R2):
Cycle 5   IF: NOP                ← wrong instruction flushed
          PC redirected to END
```

#### Control penalty calculation in `HazardDetector.java`
```java
public int controlPenalty(Instruction inst, boolean taken) {
    switch (inst.opCode.type) {
        case JUMP:   return 1;           // always 1 bubble
        case BRANCH: return taken ? 1 : 0;  // 1 bubble only if taken
        default:     return 0;
    }
}
```

---

### 5.3 Structural Hazards

A structural hazard occurs when two instructions need the **same hardware
resource** at the same time.

#### In this simulator
This pipeline uses a **Harvard architecture** model — instruction memory
(the program list) and data memory (`DataMemory`) are completely separate.
The single-issue design means only one instruction occupies each stage at a time.
Therefore **no structural hazards arise** in Phase 1.

#### When structural hazards would appear (Phase 2+)
- A unified memory where both IF and MEM try to access memory in the same cycle
- A single-port register file where two instructions try to write in the same cycle
- Superscalar issue where two instructions compete for the same functional unit

---

## 6. Hazards Per Instruction Type

### Complete Reference Table

| Instruction | Writes Register? | Data Hazard Risk | Control Hazard Risk | Notes |
|---|---|---|---|---|
| **ADD** | Yes (rd) | HIGH — every dependent instruction stalls | None | Most common source of RAW stalls |
| **SUB** | Yes (rd) | HIGH | None | Same as ADD |
| **MUL** | Yes (rd) | HIGH | None | Same as ADD; result used in later multiply chains |
| **DIV** | Yes (rd) | HIGH | None | Same as ADD; divide-by-zero returns 0 safely |
| **AND** | Yes (rd) | HIGH | None | Same RAW pattern as arithmetic |
| **OR** | Yes (rd) | HIGH | None | Same RAW pattern as arithmetic |
| **XOR** | Yes (rd) | HIGH | None | Same RAW pattern as arithmetic |
| **LOAD** | Yes (rd) | VERY HIGH — load-use hazard | None | Worst-case: value not available until after MEM |
| **STORE** | No | MEDIUM — on source register | None | Stalls if the value being stored isn't ready |
| **BEQ** | No | MEDIUM — on compared registers | HIGH — 1 cycle if taken | Double penalty when combined with data stall |
| **BNE** | No | MEDIUM — on compared registers | HIGH — 1 cycle if taken | Same as BEQ |
| **JUMP** | No | None | ALWAYS — 1 cycle | Unconditional redirect always flushes IF |
| **NOP** | No | None | None | Bubble; does nothing |

---

### Data Hazard Chains — How They Compound

When multiple instructions depend on each other in sequence, stalls compound:

```asm
ADD R1, R2, R3     ; cycle 1 in ID
ADD R4, R1, R5     ; depends on R1  → +2 stall cycles
SUB R6, R4, R1     ; depends on R4  → +2 stall cycles
MUL R7, R6, R2     ; depends on R6  → +2 stall cycles
ADD R8, R7, R3     ; depends on R7  → +2 stall cycles
```

Total extra cycles = 4 dependencies × 2 stall cycles = **8 stall cycles**
Total execution = 5 instructions × ideal + 8 stalls = **14 cycles**
CPI = 14 ÷ 5 = **2.80** ← matches the simulator output exactly

---

## 7. Reading the Pipeline Output

### The cycle table

```
+----------+----------------------------+----------------------------+...
|  Cycle   |  IF                        |  ID                        |...
+----------+----------------------------+----------------------------+...
|  4       | SUB R6,R4,R1               | ADD R4,R1,R5               |  << DATA STALL
|  5       | SUB R6,R4,R1               | ADD R4,R1,R5               |  << DATA STALL
|  6       | MUL R7,R6,R2               | SUB R6,R4,R1               |
```

**Reading each row:**
- Each column shows what instruction occupies that stage at the start of that cycle
- `---` means the stage is empty (pipeline filling or draining)
- `NOP` means a bubble is present (result of a stall or flush)
- `<< DATA STALL` means the hazard detector fired this cycle — ID is frozen
- When the **same instruction appears in the same stage on two consecutive rows**, it was stalled

**Spotting a stall sequence:**
```
Cycle 4   ID: ADD R4,R1,R5    EX: ADD R1,R2,R3   ← producer in EX, consumer in ID → STALL
Cycle 5   ID: ADD R4,R1,R5    EX: NOP             ← ID frozen, bubble in EX → STALL
Cycle 6   ID: SUB R6,R4,R1    EX: ADD R4,R1,R5   ← consumer finally advances to EX
```

The consumer (`ADD R4,R1,R5`) stays in ID for cycles 4 and 5 — two stall cycles.

---

## 8. Performance Metrics

### Cycles Per Instruction (CPI)

```
CPI = Total Cycles ÷ Instructions Retired
```

| Pipeline condition | Expected CPI |
|---|---|
| Ideal (no hazards, no stalls) | 1.0 |
| With data stalls only | > 1.0, depends on dependency density |
| With control stalls only | > 1.0, depends on branch frequency |
| With both | Additive |

### Throughput (IPC — Instructions Per Cycle)

```
IPC = Instructions Retired ÷ Total Cycles = 1 ÷ CPI
```

### Stall cycle cost

| Hazard | Stall cycles (Phase 1, no forwarding) |
|---|---|
| RAW — arithmetic/logical dependency | 2 cycles per dependent pair |
| RAW — load-use (LOAD followed immediately) | 2 cycles |
| Control — branch taken | 1 cycle |
| Control — branch not taken | 0 cycles |
| Control — JUMP | 1 cycle |

### Phase 1 Workload Results

| Workload | Cycles | Instructions | Data Stalls | Control Stalls | CPI |
|---|---|---|---|---|---|
| Arithmetic-intensive | 14 | 5 | 4 | 0 | 2.80 |
| Memory-intensive | 14 | 6 | 3 | 0 | 2.33 |
| Branch-heavy | 12 | 4 | 1 | 1 | 3.00 |
| Loop-based (3 iterations) | 31 | 15 | 5 | 3 | 2.07 |

---

## 9. How to Compile and Run

### Requirements
- Java JDK 11 or higher (`javac -version` to check)

### Compile
```bash
cd PipelineSimulator
mkdir out
javac -sourcepath src -d out src/Main.java
```

### Run all workloads
```bash
java -cp out Main
```

### Run a specific workload
```bash
java -cp out Main arithmetic
java -cp out Main memory
java -cp out Main branch
java -cp out Main loop
```

### Add your own program
In `Main.java`, define a new assembly string following the syntax in Section 4,
then call `runWorkload("myname", MY_WORKLOAD_STRING, null)`.

You can also pre-load register values before the run:
```java
sim.getRegisterFile().init("R4", 10);   // R4 = 10
sim.getDataMemory().init(0, 42);         // mem[0] = 42
```

---

*End of Phase 1 Documentation*
*Next: Phase 2 — Data Forwarding, Hazard Detection Unit, Branch Prediction*
