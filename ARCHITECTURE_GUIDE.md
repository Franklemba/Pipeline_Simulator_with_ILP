# Pipeline Simulator - Architecture Guide

## 🎯 What This Simulator Does

This is an educational tool that simulates how modern CPUs execute instructions using **pipelining** - a technique where multiple instructions are processed simultaneously at different stages, like an assembly line in a factory.

Think of it like a car wash:
- **Station 1 (IF)**: Car enters
- **Station 2 (ID)**: Identify what needs cleaning
- **Station 3 (EX)**: Wash the car
- **Station 4 (MEM)**: Rinse
- **Station 5 (WB)**: Dry and finish

While one car is being washed, another is being rinsed, and another is entering. This is **pipelining** - doing multiple things at once to improve throughput!

---

## 🏗️ The 5-Stage Pipeline

### Stage 1: IF (Instruction Fetch)
**What it does**: Grabs the next instruction from memory

**Real-world analogy**: Reading the next recipe step from a cookbook

**Example**: Fetch "ADD R1, R2, R3" from program memory

---

### Stage 2: ID (Instruction Decode)
**What it does**: Figures out what the instruction means and reads register values

**Real-world analogy**: Understanding "add 2 cups of flour" and getting the measuring cup

**Example**: 
- Decode: "This is an ADD operation"
- Read: Get current values of R2 and R3 from the register file

---

### Stage 3: EX (Execute)
**What it does**: Performs the actual computation (addition, subtraction, etc.)

**Real-world analogy**: Actually mixing the ingredients together

**Example**: Calculate R2 + R3 = 15

---

### Stage 4: MEM (Memory Access)
**What it does**: Reads from or writes to data memory (only for LOAD/STORE instructions)

**Real-world analogy**: Getting ingredients from the pantry or putting leftovers back

**Example**: 
- LOAD: Read value from memory address
- STORE: Write value to memory address
- Other instructions: Just pass through

---

### Stage 5: WB (Write Back)
**What it does**: Writes the final result back to the register file

**Real-world analogy**: Putting the finished dish on the table

**Example**: Write result (15) into register R1

---

## ⚠️ Pipeline Hazards (The Problems)

Pipelining is great, but it creates problems when instructions depend on each other. These are called **hazards**.

### 1. Data Hazards (RAW - Read After Write)

**The Problem**: An instruction needs a value that hasn't been computed yet.

**Example**:
```assembly
ADD R1, R2, R3    # Cycle 1: Calculate R1 = R2 + R3
ADD R4, R1, R5    # Cycle 2: Needs R1, but it's not ready yet!
```

**Timeline**:
```
Cycle 1: IF[ADD R1] → ID[---] → EX[---] → MEM[---] → WB[---]
Cycle 2: IF[ADD R4] → ID[ADD R1] → EX[---] → MEM[---] → WB[---]
Cycle 3: IF[---] → ID[ADD R4] → EX[ADD R1] → MEM[---] → WB[---]  ← R1 not ready!
```

**Solution**: **STALL** - Freeze the pipeline until R1 is ready
- Insert "bubbles" (NOPs) to wait
- Performance cost: Wasted cycles

**Real-world analogy**: You can't frost a cake until it's baked and cooled!

---

### 2. Control Hazards (Branches and Jumps)

**The Problem**: We don't know which instruction to fetch next until a branch is evaluated.

**Example**:
```assembly
BEQ R1, R2, TARGET    # If R1 == R2, jump to TARGET
ADD R3, R4, R5        # Already fetched, but might be wrong!
SUB R6, R7, R8        # This too!
TARGET:
MUL R9, R10, R11      # Should go here if branch taken
```

**Our Strategy**: **Assume NOT TAKEN** (optimistic prediction)
- Keep fetching the next sequential instruction
- If we're wrong, **FLUSH** the pipeline (throw away wrong instructions)

**Cost**:
- Branch NOT taken: 0 penalty (we guessed right!)
- Branch IS taken: 1 cycle penalty (flush wrong instruction)

**Real-world analogy**: You start walking to the store, but then your friend calls and says "actually, go to the park instead" - you wasted time walking the wrong way!

---

### 3. Structural Hazards

**The Problem**: Multiple instructions need the same hardware at the same time.

**Our Design**: **NO STRUCTURAL HAZARDS** because:
- Separate instruction and data memory (Harvard architecture)
- Each stage has dedicated hardware
- Only one instruction per stage (single-issue)

**Real-world analogy**: Having separate ovens for baking and broiling - no conflicts!

---

## 📊 How Instructions Flow Through the Pipeline

### Normal Execution (No Hazards)

```
Cycle 1: IF[I1] → ID[--] → EX[--] → MEM[--] → WB[--]
Cycle 2: IF[I2] → ID[I1] → EX[--] → MEM[--] → WB[--]
Cycle 3: IF[I3] → ID[I2] → EX[I1] → MEM[--] → WB[--]
Cycle 4: IF[I4] → ID[I3] → EX[I2] → MEM[I1] → WB[--]
Cycle 5: IF[I5] → ID[I4] → EX[I3] → MEM[I2] → WB[I1]  ← I1 completes!
```

**Ideal CPI (Cycles Per Instruction)**: 1.0 (after pipeline fills)

---

### With Data Hazard (Stall)

```
Cycle 1: IF[ADD R1,R2,R3] → ID[--] → EX[--] → MEM[--] → WB[--]
Cycle 2: IF[ADD R4,R1,R5] → ID[ADD R1] → EX[--] → MEM[--] → WB[--]
Cycle 3: IF[ADD R4,R1,R5] → ID[ADD R4] → EX[ADD R1] → MEM[--] → WB[--]  ← STALL!
         ^^^^^^^^^^^^^^^^   ^^^^^^^^^^
         Same instruction!  Can't proceed - R1 not ready
         
Cycle 4: IF[ADD R4,R1,R5] → ID[ADD R4] → EX[NOP] → MEM[ADD R1] → WB[--]  ← STALL!
Cycle 5: IF[SUB R6,R4,R1] → ID[ADD R4] → EX[NOP] → MEM[NOP] → WB[ADD R1]  ← R1 ready!
Cycle 6: IF[next] → ID[SUB R6] → EX[ADD R4] → MEM[NOP] → WB[NOP]  ← Resume
```

**Result**: 2 extra cycles wasted (bubbles inserted)

---

### With Control Hazard (Flush)

```
Cycle 1: IF[BEQ R1,R2,END] → ID[--] → EX[--] → MEM[--] → WB[--]
Cycle 2: IF[ADD R3,R4,R5] → ID[BEQ] → EX[--] → MEM[--] → WB[--]
         ^^^^^^^^^^^^^^^^
         Wrong instruction! (branch will be taken)
         
Cycle 3: IF[NOP] → ID[NOP] → EX[BEQ] → MEM[--] → WB[--]  ← FLUSH!
         ^^^^^^^^   ^^^^^^^^
         Flushed!   Flushed!
         PC redirected to END
         
Cycle 4: IF[END instruction] → ID[NOP] → EX[NOP] → MEM[BEQ] → WB[--]  ← Resume
```

**Result**: 1 cycle wasted (wrong instruction flushed)

---

## 🎮 The Four Test Workloads

### 1. Arithmetic Workload
**Purpose**: Stress-test data hazards

**Code**:
```assembly
ADD R1, R2, R3    # R1 = 0 + 0 = 0
ADD R4, R1, R5    # Needs R1 → STALL
SUB R6, R4, R1    # Needs R4 → STALL
MUL R7, R6, R2    # Needs R6 → STALL
ADD R8, R7, R3    # Needs R7 → STALL
```

**Expected**: Many data stalls (every instruction depends on the previous one)

---

### 2. Memory Workload
**Purpose**: Test load-use hazards

**Code**:
```assembly
LOAD  R1, 0(R2)     # R1 ← mem[0] = 10
LOAD  R3, 4(R2)     # R3 ← mem[4] = 20
ADD   R4, R1, R3    # Needs R1 and R3 → STALL
STORE R4, 8(R2)     # mem[8] ← R4
LOAD  R5, 8(R2)     # R5 ← mem[8]
ADD   R6, R5, R4    # Needs R5 → STALL
```

**Expected**: Load-use stalls (memory operations take time)

---

### 3. Branch Workload
**Purpose**: Test control hazards

**Code**:
```assembly
ADD  R1, R0, R0      # R1 = 0
ADD  R2, R0, R0      # R2 = 0
BEQ  R1, R2, END     # 0 == 0 → TAKEN! (flush next 2 instructions)
ADD  R3, R1, R2      # Skipped (flushed)
SUB  R4, R3, R1      # Skipped (flushed)
END:
ADD  R5, R1, R2      # Execution continues here
```

**Expected**: 1 control stall (branch misprediction)

---

### 4. Loop Workload
**Purpose**: Realistic loop with mixed hazards

**Code**:
```assembly
ADD  R3, R0, R0      # i = 0
ADD  R1, R0, R0      # acc = 0
LOOP:
ADD  R1, R1, R2      # acc += 5
ADD  R3, R3, R4      # i += 1
BNE  R3, R5, LOOP    # if i != 3, repeat
ADD  R6, R1, R0      # store result
```

**Expected**: Mix of data stalls and control stalls (loop iterations)

---

## 📈 Performance Metrics

### CPI (Cycles Per Instruction)
**Formula**: Total Cycles / Instructions Completed

**Ideal**: 1.0 (one instruction completes per cycle)

**Reality**: Higher due to stalls
- Arithmetic workload: ~2.8 (lots of dependencies)
- Branch workload: ~1.5 (fewer dependencies)

**Lower is better!**

---

### IPC (Instructions Per Cycle)
**Formula**: Instructions Completed / Total Cycles

**Ideal**: 1.0 (inverse of CPI)

**Reality**: Lower due to stalls
- Good pipeline: 0.7-0.9
- Poor pipeline: 0.3-0.5

**Higher is better!**

---

### Stall Breakdown
- **Data Stalls**: RAW hazards (waiting for register values)
- **Control Stalls**: Branch mispredictions (flushing wrong instructions)

**Goal**: Minimize both through better design (forwarding, branch prediction)

---

## 🔧 Key Design Decisions

### Why Process Stages in Reverse Order?
**Answer**: To ensure each instruction moves exactly once per cycle.

If we processed forward (IF → ID → EX → MEM → WB), an instruction could move multiple stages in one cycle, breaking the pipeline model.

By processing backward (WB → MEM → EX → ID → IF), each stage reads from the previous stage's latch BEFORE that stage updates, ensuring clean single-step advancement.

---

### Why Evaluate Branches in ID Instead of EX?
**Answer**: Early resolution minimizes control hazard penalty.

- Evaluate in ID: 1 cycle penalty (flush IF only)
- Evaluate in EX: 2 cycle penalty (flush IF and ID)

We want to know the branch outcome ASAP!

---

### Why No Forwarding in Phase 1?
**Answer**: Educational progression - understand the problem before the solution.

Phase 1: Learn about hazards and stalls
Phase 2: Add forwarding to reduce stalls
Phase 3: Add branch prediction to reduce control penalties

---

## 🚀 Running the Simulator

```bash
# Compile
mvn clean compile

# Run all workloads with comparison
mvn exec:java

# Run specific workload
mvn exec:java -Dexec.args="arithmetic"
mvn exec:java -Dexec.args="memory"
mvn exec:java -Dexec.args="branch"
mvn exec:java -Dexec.args="loop"
```

---

## 📖 Understanding the Output

### Cycle-by-Cycle Table
```
| Cycle  |   IF              |   ID              |   EX              |   MEM             |   WB              |   Hazard     |
| 3      | ADD R4,R1,R5      | ADD R4,R1,R5      | ADD R1,R2,R3      | ---               | ---               | DATA STALL   |
```

**Reading**: 
- Cycle 3: Three instructions in the pipeline
- IF and ID are frozen (same instruction in both)
- EX is computing R1
- Hazard column shows "DATA STALL" (R4 needs R1)

---

### Performance Summary
```
║  Total Cycles           : 14                ║
║  Instructions Retired   : 5                 ║
║  Total Stalls           : 4                 ║
║    ├─ Data Hazard Stalls : 4                ║
║    └─ Control Stalls     : 0                ║
║  CPI (Cycles/Instr)     : 2.8000            ║
║  Throughput (IPC)       : 0.3571            ║
```

**Analysis**:
- 5 instructions took 14 cycles (should take 9 in ideal pipeline)
- 4 stalls due to data hazards
- CPI of 2.8 means each instruction took 2.8 cycles on average
- IPC of 0.357 means we complete 0.357 instructions per cycle

---

## 🎓 Key Takeaways

1. **Pipelining improves throughput** by overlapping instruction execution
2. **Hazards reduce performance** by forcing stalls and flushes
3. **Data dependencies are expensive** without forwarding
4. **Branch prediction matters** for control-heavy code
5. **Real CPUs use advanced techniques** (forwarding, out-of-order execution, speculative execution) to minimize these penalties

This simulator shows the fundamental challenges that modern CPU designers must solve!
