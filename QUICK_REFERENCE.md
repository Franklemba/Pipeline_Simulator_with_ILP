# Pipeline Simulator - Quick Reference

## 🚀 Quick Start

```bash
# Build the project
mvn clean compile

# Run all workloads
mvn exec:java

# Run specific workload
mvn exec:java -Dexec.args="arithmetic"
```

## 📊 The Pipeline in 30 Seconds

```
┌─────────────────────────────────────────────────────────────┐
│  5-STAGE PIPELINE: IF → ID → EX → MEM → WB                 │
└─────────────────────────────────────────────────────────────┘

IF  (Fetch)      → Get next instruction from memory
ID  (Decode)     → Figure out what it means, read registers
EX  (Execute)    → Do the math (ALU operations)
MEM (Memory)     → Read/write data memory (LOAD/STORE only)
WB  (WriteBack)  → Save result to register file
```

## ⚠️ Hazards Cheat Sheet

### Data Hazard (RAW)
```
Problem:  Instruction needs a value that's not ready yet
Example:  ADD R1, R2, R3
          ADD R4, R1, R5  ← Needs R1 (not ready!)
Solution: STALL (insert bubbles, wait for R1)
Cost:     1-2 cycles per hazard
```

### Control Hazard (Branch)
```
Problem:  Don't know which instruction to fetch next
Example:  BEQ R1, R2, TARGET
          ADD R3, R4, R5  ← Might be wrong instruction!
Solution: FLUSH (throw away wrong instruction)
Cost:     1 cycle if branch taken, 0 if not taken
```

## 🎮 Test Workloads

| Workload   | Tests                  | Expected Behavior           |
|------------|------------------------|-----------------------------|
| arithmetic | Data hazards           | Many stalls (RAW chains)    |
| memory     | Load-use hazards       | Memory operation stalls     |
| branch     | Control hazards        | Branch misprediction flush  |
| loop       | Mixed hazards          | Realistic loop behavior     |

## 📈 Performance Metrics

```
CPI (Cycles Per Instruction) = Total Cycles / Instructions
  → Lower is better
  → Ideal: 1.0 (one instruction per cycle)
  → Reality: 1.5-3.0 (due to stalls)

IPC (Instructions Per Cycle) = Instructions / Total Cycles
  → Higher is better
  → Ideal: 1.0 (inverse of CPI)
  → Reality: 0.3-0.7 (due to stalls)
```

## 🔍 Reading the Output

### Cycle Table
```
| Cycle | IF            | ID            | EX            | MEM           | WB            | Hazard     |
|-------|---------------|---------------|---------------|---------------|---------------|------------|
| 3     | ADD R4,R1,R5  | ADD R4,R1,R5  | ADD R1,R2,R3  | ---           | ---           | DATA STALL |
        ^^^^^^^^^^^^^^  ^^^^^^^^^^^^^^
        Same instruction = STALLED!
```

### Performance Summary
```
║  Total Cycles           : 14                ║  ← How long it took
║  Instructions Retired   : 5                 ║  ← How many completed
║  Total Stalls           : 4                 ║  ← Wasted cycles
║    ├─ Data Hazard Stalls : 4                ║  ← From dependencies
║    └─ Control Stalls     : 0                ║  ← From branches
║  CPI (Cycles/Instr)     : 2.8000            ║  ← 2.8 cycles per instruction
║  Throughput (IPC)       : 0.3571            ║  ← 0.36 instructions per cycle
```

## 🎯 Key Concepts

### Why Pipeline?
- **Goal**: Execute multiple instructions simultaneously
- **Benefit**: Higher throughput (more instructions per second)
- **Challenge**: Hazards reduce performance

### Why Stalls?
- **Without forwarding**: Must wait for values to be written to registers
- **Conservative approach**: Ensures correctness
- **Phase 2 improvement**: Add forwarding to reduce stalls

### Why Flush?
- **Branch prediction**: We guess "not taken"
- **Wrong guess**: Must throw away wrong instructions
- **Alternative**: Better branch prediction (Phase 3)

## 🏗️ Architecture Decisions

| Decision                          | Reason                                    |
|-----------------------------------|-------------------------------------------|
| Process stages in reverse order   | Ensures single-step advancement           |
| Evaluate branches in ID           | Minimize control hazard penalty           |
| No forwarding in Phase 1          | Educational: understand problem first     |
| Assume branches NOT taken         | Optimistic prediction (good for if-else)  |
| Separate instruction/data memory  | Eliminates structural hazards             |

## 📖 Instruction Set

### Arithmetic
```
ADD  R1, R2, R3    # R1 = R2 + R3
SUB  R1, R2, R3    # R1 = R2 - R3
MUL  R1, R2, R3    # R1 = R2 * R3
DIV  R1, R2, R3    # R1 = R2 / R3
```

### Logical
```
AND  R1, R2, R3    # R1 = R2 & R3
OR   R1, R2, R3    # R1 = R2 | R3
XOR  R1, R2, R3    # R1 = R2 ^ R3
```

### Memory
```
LOAD  R1, 4(R2)    # R1 = memory[R2 + 4]
STORE R1, 4(R2)    # memory[R2 + 4] = R1
```

### Control
```
BEQ  R1, R2, LABEL # if R1 == R2, jump to LABEL
BNE  R1, R2, LABEL # if R1 != R2, jump to LABEL
JUMP LABEL         # unconditional jump
```

### Special
```
NOP                # No operation (bubble)
```

## 🔧 Common Issues

### High CPI?
- Check for data dependencies (back-to-back instructions using same registers)
- Consider reordering instructions to reduce dependencies
- Phase 2: Add forwarding

### Many Control Stalls?
- Branches are expensive (1 cycle penalty when taken)
- Consider loop unrolling or branch elimination
- Phase 3: Add branch prediction

### Understanding Stalls?
- Read ARCHITECTURE_GUIDE.md for detailed explanations
- Run workloads individually to see specific hazard patterns
- Compare workload results to understand different scenarios

## 📚 Learn More

- **ARCHITECTURE_GUIDE.md** - Comprehensive architecture explanation
- **Source code** - Heavily commented for educational purposes
- **Workloads** - Four test cases demonstrating different hazards

## 💡 Pro Tips

1. **Run all workloads first** to see the comparison table
2. **Then run individually** to understand each hazard type
3. **Read the cycle table carefully** - it shows exactly what's happening
4. **Compare CPI across workloads** - shows impact of different hazard types
5. **Experiment** - modify workloads to see how changes affect performance
