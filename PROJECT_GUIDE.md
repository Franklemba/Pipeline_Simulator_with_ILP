# Pipeline Simulator - Complete Guide

**CS 510 – Advanced Computer Architecture**  
**Status:** Phase 1 & 2 Complete ✅ | Phase 3 In Progress ⚠️

---

## 🚀 Quick Start

```bash
# Compile
mvn clean compile

# Run Phase 1 (basic pipeline, all workloads)
mvn exec:java

# Run Phase 2 (complete demonstration)
java -cp target/classes MainPhase2Complete

# Run specific workload
mvn exec:java -Dexec.args="arithmetic"
```

---

## 📊 What This Simulator Does

Simulates a **5-stage RISC pipeline** showing how modern CPUs execute multiple instructions simultaneously:

```
IF (Fetch) → ID (Decode) → EX (Execute) → MEM (Memory) → WB (WriteBack)
```

**Key Features:**
- ✅ Phase 1: Basic pipeline with hazard detection
- ✅ Phase 2: Data forwarding, branch prediction, ILP analysis
- ⚠️ Phase 3: Performance evaluation (in progress)

---

## 🎯 Phase 1: Basic Pipeline

### Features
- 5-stage pipeline (IF → ID → EX → MEM → WB)
- Complete instruction set (arithmetic, logical, memory, control)
- Hazard detection (data and control)
- Pipeline stalls and flushes
- Cycle-by-cycle visualization

### Workloads
1. **Arithmetic** - Data hazards (RAW dependencies)
2. **Memory** - Load-use hazards
3. **Branch** - Control hazards
4. **Loop** - Mixed hazards

### Results
- CPI: 1.5-3.5 depending on hazards
- Clear demonstration of pipeline stalls

---

## 🚀 Phase 2: Advanced Optimizations

### 1. Data Forwarding
**Result:** 1.8x speedup on arithmetic code

**How it works:**
- Forwards results from later stages to earlier stages
- Avoids waiting for register file write
- 3 paths: EX→EX, MEM→EX, WB→EX

**Exception:** Load-use hazards still require 1-cycle stall

### 2. Branch Prediction
**Result:** 100% accuracy with 2-bit predictor on loops

**Predictors implemented:**
- **Static:** Always Taken, Always Not Taken, BTFNT
- **Dynamic:** 1-bit predictor, 2-bit saturating counter

**How it works:**
- Predicts branch outcome before evaluation
- Learns from history (dynamic predictors)
- Flushes pipeline only on misprediction

### 3. ILP Analysis
**Result:** Identifies parallelism opportunities

**Metrics:**
- Dependency graph construction
- Critical path length
- Theoretical ILP calculation
- Parallel instruction groups

**Example:**
- ILP-friendly code: ILP = 3.0 (high parallelism)
- ILP-unfriendly code: ILP = 1.0 (sequential)

### 4. Loop Unrolling
**Result:** 11% CPI improvement

**How it works:**
- Eliminates loop branch overhead
- Exposes more parallelism
- Trade-off: Larger code size

---

## 📈 Performance Results

| Optimization | Metric | Improvement |
|--------------|--------|-------------|
| Data Forwarding | Speedup | **1.80x** |
| Data Forwarding | Stall Reduction | **80-100%** |
| Branch Prediction | Accuracy | **100%** (loops) |
| Loop Unrolling | CPI | **-11%** |

---

## 🏗️ Architecture

### Pipeline Stages

**IF (Instruction Fetch)**
- Fetches next instruction from program memory
- Updates program counter

**ID (Instruction Decode)**
- Decodes instruction
- Reads register operands
- Evaluates branch conditions

**EX (Execute)**
- Performs ALU operations
- Applies data forwarding (Phase 2)
- Calculates memory addresses

**MEM (Memory Access)**
- Reads/writes data memory
- Only for LOAD/STORE instructions

**WB (Write Back)**
- Writes results to register file
- Instruction completes

### Hazards

**Data Hazards (RAW)**
- Problem: Instruction needs value not yet computed
- Phase 1: Stall until value ready
- Phase 2: Forward value from later stage

**Control Hazards**
- Problem: Don't know which instruction to fetch next
- Phase 1: Assume NOT TAKEN, flush if wrong
- Phase 2: Use predictor, flush only on misprediction

**Structural Hazards**
- None (separate instruction/data memory)

---

## 📁 Project Structure

```
pipeline-simulator/
├── pom.xml                          # Maven configuration
├── README.md                        # Quick overview
├── PROJECT_GUIDE.md                 # This file (complete guide)
├── ASSIGNMENT_PROGRESS.md           # Progress tracking
├── PHASE2_COMPLETE.md               # Phase 2 detailed report
│
├── src/main/java/
│   ├── Main.java                    # Phase 1 entry point
│   ├── MainPhase2Complete.java      # Phase 2 demonstration
│   │
│   ├── assembler/
│   │   └── Assembler.java           # Assembly → Instructions
│   │
│   ├── hardware/
│   │   ├── ALU.java                 # Arithmetic Logic Unit
│   │   ├── RegisterFile.java       # 32 registers
│   │   └── DataMemory.java          # Data memory
│   │
│   ├── isa/
│   │   ├── Instruction.java         # Instruction representation
│   │   ├── OpCode.java              # Operation codes
│   │   └── OpType.java              # Operation types
│   │
│   ├── pipeline/
│   │   ├── PipelineSimulator.java   # Main simulator
│   │   └── PipelineRegister.java    # Inter-stage latches
│   │
│   ├── hazards/
│   │   └── HazardDetector.java      # Hazard detection
│   │
│   ├── forwarding/                  # Phase 2
│   │   ├── ForwardingUnit.java      # Forwarding logic
│   │   ├── ForwardingSource.java    # Forwarding paths
│   │   └── ForwardingDecision.java  # Forwarding decisions
│   │
│   ├── prediction/                  # Phase 2
│   │   ├── BranchPredictor.java     # Interface
│   │   ├── StaticPredictors.java    # Static predictors
│   │   ├── OneBitPredictor.java     # 1-bit dynamic
│   │   └── TwoBitPredictor.java     # 2-bit dynamic
│   │
│   ├── analysis/                    # Phase 2
│   │   ├── ILPAnalyzer.java         # ILP analysis
│   │   └── ILPReport.java           # ILP results
│   │
│   └── stats/
│       └── Statistics.java          # Performance metrics
```

---

## 🧪 Testing

### Run All Phase 1 Workloads
```bash
mvn exec:java
```
Shows comparison table of all 4 workloads.

### Run Specific Workload
```bash
mvn exec:java -Dexec.args="arithmetic"
mvn exec:java -Dexec.args="memory"
mvn exec:java -Dexec.args="branch"
mvn exec:java -Dexec.args="loop"
```

### Run Phase 2 Complete Demo
```bash
java -cp target/classes MainPhase2Complete
```
Demonstrates:
- Data forwarding comparison
- All 5 branch predictors
- ILP analysis
- Loop unrolling

### Build JAR
```bash
mvn clean package
java -jar target/pipeline-simulator-1.0.0.jar
```

---

## 🎓 Educational Value

### What Students Learn

**Pipeline Concepts:**
- How instructions flow through stages
- Why pipelining improves throughput
- Pipeline hazards and their impact

**Data Forwarding:**
- How CPUs avoid stalls
- Hardware complexity vs performance
- When forwarding can't help

**Branch Prediction:**
- Static vs dynamic prediction
- Learning from history
- Prediction accuracy impact on CPI

**ILP Analysis:**
- Dependency analysis
- Critical path concept
- Parallelism opportunities

**Compiler Optimizations:**
- Loop unrolling benefits
- Code size vs performance trade-offs

---

## 📊 Performance Metrics

### CPI (Cycles Per Instruction)
- **Lower is better**
- Ideal: 1.0 (one instruction per cycle)
- Reality: 1.5-3.5 (due to hazards)

### IPC (Instructions Per Cycle)
- **Higher is better**
- Ideal: 1.0 (inverse of CPI)
- Reality: 0.3-0.7 (due to hazards)

### Speedup
- Ratio of cycles without optimization / cycles with optimization
- Data forwarding: 1.8x on arithmetic code

### Prediction Accuracy
- Percentage of correct branch predictions
- 2-bit predictor: 100% on loops

---

## 🔧 Configuration

### Enable Data Forwarding
```java
PipelineSimulator sim = new PipelineSimulator();
sim.setForwardingEnabled(true);
```

### Set Branch Predictor
```java
sim.setBranchPredictor(new TwoBitPredictor());
```

### Analyze ILP
```java
ILPAnalyzer analyzer = new ILPAnalyzer();
ILPReport report = analyzer.analyze(instructions);
System.out.println(report);
```

---

## 📝 Assignment Progress

| Phase | Tasks | Status | Completion |
|-------|-------|--------|------------|
| **Phase 1** | Basic Pipeline | ✅ Complete | 100% |
| **Phase 2** | Data Forwarding | ✅ Complete | 100% |
| **Phase 2** | Branch Prediction | ✅ Complete | 100% |
| **Phase 2** | ILP Analysis | ✅ Complete | 100% |
| **Phase 2** | Loop Unrolling | ✅ Complete | 100% |
| **Phase 2** | Superscalar | ⚠️ Skipped | 0% |
| **Phase 3** | Performance Eval | ⚠️ Partial | 40% |

**Overall Progress: ~80%**

---

## 🎯 Next Steps

1. **Complete Phase 3 Performance Evaluation**
   - Run all configurations
   - Generate comparison tables
   - Calculate speedups

2. **Write Technical Report**
   - Sections 8-10
   - Performance analysis
   - Discussion of results

3. **Final Testing**
   - Verify all features
   - Validate metrics
   - Check edge cases

**Target Grade: A (93/100)**

---

## 💡 Key Insights

1. **Forwarding is Critical**
   - 1.8x speedup shows why all modern CPUs use it
   - Hardware complexity justified by performance

2. **Branch Prediction Matters**
   - 100% accuracy possible with good predictors
   - Dynamic predictors learn and adapt

3. **ILP Varies by Code**
   - Some code has high parallelism (ILP = 3.0)
   - Some code is sequential (ILP = 1.0)
   - Compilers try to increase ILP

4. **Loop Unrolling Works**
   - Eliminates branch overhead
   - Exposes more parallelism
   - Trade-off: Code size

---

## 🐛 Troubleshooting

### Compilation Errors
```bash
mvn clean compile
```

### Can't Find Main Class
```bash
# Make sure you're in project root
cd pipeline-simulator
mvn exec:java
```

### Wrong Results
- Check register initialization for memory/loop workloads
- Verify forwarding is enabled/disabled as intended
- Check branch predictor is set correctly

---

## 📚 References

- **ARCHITECTURE_GUIDE.md** - Detailed architecture explanation
- **PHASE2_COMPLETE.md** - Phase 2 completion report
- **ASSIGNMENT_PROGRESS.md** - Detailed progress tracking
- **Source Code** - Heavily commented

---

## ✅ Success Criteria

- [x] Phase 1: Complete 5-stage pipeline
- [x] Phase 2: Data forwarding working
- [x] Phase 2: Branch prediction implemented
- [x] Phase 2: ILP analysis functional
- [x] Phase 2: Loop unrolling demonstrated
- [ ] Phase 3: Performance evaluation complete
- [ ] Technical report written

**Current Status: Excellent progress, on track for A grade!**
