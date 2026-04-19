# Testing Guide - Pipeline Simulator

**CS 510 – Advanced Computer Architecture**

This guide provides clear, step-by-step instructions for testing each phase of the pipeline simulator.

---

## 📋 Prerequisites

```bash
# Ensure you're in the project root directory
cd pipeline-simulator

# Compile the project
mvn clean compile
```

**Expected output:**
```
[INFO] BUILD SUCCESS
```

---

## 🧪 Phase 1: Basic Pipeline Testing

Phase 1 demonstrates the **basic 5-stage pipeline** with hazard detection using **stalls only** (no optimizations).

### Test 1.1: Run All Workloads (Comparison Table)

```bash
mvn exec:java
```

**What this shows:**
- Comparison of 4 different workloads
- CPI for each workload
- Total cycles and instructions
- Stall counts (data vs control)

**Expected output:**
```
╔════════════════════════════════════════════════════════════════════════════════════════════════════════╗
║  PHASE 1: BASIC PIPELINE SIMULATOR                                                                     ║
╚════════════════════════════════════════════════════════════════════════════════════════════════════════╝

Running workload: ARITHMETIC
...
CPI: 2.00-3.00 (high due to data hazards)
```

**Key metrics to observe:**
- **Arithmetic workload:** High data stalls (RAW dependencies)
- **Memory workload:** Load-use hazards cause stalls
- **Branch workload:** Control stalls from branches
- **Loop workload:** Mixed hazards

---

### Test 1.2: Run Individual Workloads (Detailed View)

#### Arithmetic Workload
```bash
mvn exec:java -Dexec.args="arithmetic"
```

**What this shows:**
- Cycle-by-cycle pipeline state
- Data hazards (RAW dependencies)
- Pipeline stalls marked in "Hazard" column

**Look for:**
- Instructions like `ADD R4, R1, R5` stalling because R1 isn't ready
- "DATA STALL" appearing in the hazard column
- Bubbles (empty stages) in the pipeline

---

#### Memory Workload
```bash
mvn exec:java -Dexec.args="memory"
```

**What this shows:**
- Load-use hazards (most severe type)
- Memory operations (LOAD/STORE)

**Look for:**
- LOAD followed by instruction using loaded value
- Mandatory 1-cycle stall (even with forwarding in Phase 2)

---

#### Branch Workload
```bash
mvn exec:java -Dexec.args="branch"
```

**What this shows:**
- Control hazards from branches
- Pipeline flushes when branch is taken
- Wasted cycles from wrong-path instructions

**Look for:**
- Branch instructions (BEQ, BNE)
- Pipeline flush (bubbles inserted)
- Control stall count

---

#### Loop Workload
```bash
mvn exec:java -Dexec.args="loop"
```

**What this shows:**
- Realistic code with mixed hazards
- Loop back-edge branches
- Combination of data and control hazards

**Look for:**
- Repeated branch at end of loop
- Data dependencies within loop body
- Both data and control stalls

---

### Phase 1 Success Criteria

✅ **You should see:**
- Pipeline visualization with 5 stages (IF, ID, EX, MEM, WB)
- Data stalls when instructions depend on previous results
- Control stalls when branches are taken
- CPI > 1.0 (typically 1.5-3.5)
- Clear hazard indicators

❌ **Red flags:**
- CPI = 1.0 (means hazards aren't being detected)
- No stalls appearing
- Compilation errors

---

## 🚀 Phase 2: Advanced Optimizations Testing

Phase 2 adds **data forwarding**, **branch prediction**, **ILP analysis**, and **loop unrolling**.

### Test 2.1: Complete Phase 2 Demo (All Features)

```bash
# Compile first
mvn clean compile

# Run Phase 2 complete demo
java -cp target/classes MainPhase2Complete
```

**What this shows:**
- Part 1: Data forwarding comparison
- Part 2: All 5 branch predictors
- Part 3: ILP analysis
- Part 4: Loop unrolling comparison

**Expected output:**
```
╔══════════════════════════════════════════════════════════════════════════════════════════════════════╗
║  PHASE 2: COMPLETE DEMONSTRATION                                                                     ║
╚══════════════════════════════════════════════════════════════════════════════════════════════════════╝

═══════════════════════════════════════════════════════════════════════════════════════════════════════
PART 1: DATA FORWARDING
═══════════════════════════════════════════════════════════════════════════════════════════════════════
...
```

**Duration:** ~5-10 seconds

---

### Test 2.2: Data Forwarding (Isolated)

To test **only** data forwarding, modify `MainPhase2Complete.java` temporarily or use this command:

```bash
# Create a simple test file
cat > TestForwarding.java << 'EOF'
import assembler.Assembler;
import pipeline.PipelineSimulator;
import stats.Statistics;

public class TestForwarding {
    public static void main(String[] args) {
        String code = 
            "ADD R1, R2, R3\n" +
            "ADD R4, R1, R5\n" +  // Depends on R1
            "SUB R6, R4, R1\n" +  // Depends on R4 and R1
            "MUL R7, R6, R2\n";   // Depends on R6
        
        Assembler asm = new Assembler();
        Assembler.Program prog = asm.assemble(code);
        
        // WITHOUT forwarding
        System.out.println("=== WITHOUT FORWARDING ===");
        PipelineSimulator sim1 = new PipelineSimulator();
        sim1.setForwardingEnabled(false);
        sim1.setVerbose(true);
        sim1.load(prog.instructions);
        Statistics stats1 = sim1.run();
        System.out.println("\nCycles: " + stats1.getTotalCycles());
        System.out.println("Data Stalls: " + stats1.getDataStalls());
        
        // WITH forwarding
        System.out.println("\n=== WITH FORWARDING ===");
        PipelineSimulator sim2 = new PipelineSimulator();
        sim2.setForwardingEnabled(true);
        sim2.setVerbose(true);
        sim2.load(prog.instructions);
        Statistics stats2 = sim2.run();
        System.out.println("\nCycles: " + stats2.getTotalCycles());
        System.out.println("Data Stalls: " + stats2.getDataStalls());
        System.out.println("Forwarding Events: " + stats2.getForwardingCount());
        
        // Comparison
        System.out.println("\n=== COMPARISON ===");
        System.out.printf("Speedup: %.2fx\n", 
            (double)stats1.getTotalCycles() / stats2.getTotalCycles());
        System.out.printf("Stalls Eliminated: %d → %d (%.0f%% reduction)\n",
            stats1.getDataStalls(), stats2.getDataStalls(),
            (1.0 - (double)stats2.getDataStalls()/stats1.getDataStalls()) * 100);
    }
}
EOF

# Compile and run
javac -cp target/classes TestForwarding.java
java -cp target/classes:. TestForwarding
```

**What to observe:**
- **Without forwarding:** Many data stalls (6-8 stalls)
- **With forwarding:** Few or no stalls (0-2 stalls)
- **Speedup:** 1.5x - 2.0x
- **Forwarding events:** Should match eliminated stalls

---

### Test 2.3: Branch Prediction (Isolated)

The Phase 2 demo already shows all predictors, but here's how to test individually:

```bash
# Run the complete demo and look at PART 2
java -cp target/classes MainPhase2Complete | grep -A 30 "PART 2: BRANCH PREDICTION"
```

**What to observe:**

**For BRANCH workload:**
- Always Not Taken: ~50% accuracy (baseline)
- Always Taken: ~50% accuracy
- BTFNT: ~60-70% accuracy (best static)
- 1-bit: ~70-80% accuracy
- 2-bit: ~80-90% accuracy (best overall)

**For LOOP workload:**
- Always Taken: ~90% accuracy (good for loops!)
- BTFNT: ~90% accuracy
- 2-bit: ~95-100% accuracy (learns the pattern)

**Key insight:** Dynamic predictors learn and adapt!

---

### Test 2.4: ILP Analysis (Isolated)

```bash
# Run the complete demo and look at PART 3
java -cp target/classes MainPhase2Complete | grep -A 40 "PART 3: INSTRUCTION LEVEL PARALLELISM"
```

**What to observe:**

**ILP-Friendly workload:**
- Theoretical ILP: **2.5-3.0** (high parallelism)
- Max parallel instructions: 4
- Many independent instructions can execute simultaneously

**ILP-Unfriendly workload:**
- Theoretical ILP: **1.0** (no parallelism)
- Max parallel instructions: 1
- Long dependency chain (each instruction depends on previous)

**Key insight:** Code structure dramatically affects parallelism!

---

### Test 2.5: Loop Unrolling (Isolated)

```bash
# Run the complete demo and look at PART 4
java -cp target/classes MainPhase2Complete | grep -A 25 "PART 4: LOOP UNROLLING"
```

**What to observe:**
- **Original loop:** More branches, fewer instructions
- **Unrolled loop:** No branches, more instructions
- **Result:** Unrolled is faster despite more instructions!

**Key metrics:**
- Control stalls: Original has 4-5, Unrolled has 0
- CPI improvement: 10-15%
- Trade-off: Code size increases

---

### Test 2.6: Superscalar Execution (Isolated)

```bash
# Run the complete demo and look at PART 5
java -cp target/classes MainPhase2Complete | grep -A 50 "PART 5: SUPERSCALAR"
```

**What to observe:**

**ILP-Friendly workload:**
- **Dual-issue rate:** 40-60% (many cycles issue 2 instructions)
- **Average issue rate:** 1.4-1.6 instructions per cycle
- **Speedup:** 1.3-1.5x over scalar
- **Why:** Many independent instructions can execute in parallel

**ILP-Unfriendly workload:**
- **Dual-issue rate:** 0-10% (few cycles issue 2 instructions)
- **Average issue rate:** 1.0-1.1 instructions per cycle
- **Speedup:** 1.0-1.1x over scalar (minimal)
- **Why:** Long dependency chain prevents parallel execution

**Key insight:** Superscalar processors need ILP to be effective!

---

### Phase 2 Success Criteria

✅ **You should see:**
- **Forwarding:** 1.5x-2.0x speedup, 80%+ stall reduction
- **Branch Prediction:** 2-bit predictor achieves 90%+ accuracy on loops
- **ILP Analysis:** Clear difference between friendly (ILP=3.0) and unfriendly (ILP=1.0) code
- **Loop Unrolling:** Eliminates control stalls, improves CPI by 10-15%
- **Superscalar:** 1.3-1.5x speedup on ILP-friendly code, minimal speedup on ILP-unfriendly code

❌ **Red flags:**
- Forwarding shows no improvement (check if enabled)
- All predictors have same accuracy (predictor not being used)
- ILP always 1.0 (dependency detection broken)
- Unrolled loop slower than original (something wrong)
- Superscalar shows no dual-issue cycles (dependency checking broken)

---

## 📊 Phase 3: Performance Evaluation Testing

Phase 3 focuses on **comprehensive performance analysis** across all configurations.

### Test 3.1: Generate Performance Report

```bash
# This will be implemented in Phase 3
# For now, use the Phase 2 demo which shows most metrics
java -cp target/classes MainPhase2Complete > phase2_results.txt

# View the results
cat phase2_results.txt
```

**What this generates:**
- Complete performance data for all optimizations
- Comparison tables
- Speedup calculations
- Stall analysis

---

### Test 3.2: Compare All Configurations

**Manual comparison using Phase 1 and Phase 2:**

```bash
# 1. Phase 1 (no optimizations)
mvn exec:java -Dexec.args="arithmetic" > phase1_arithmetic.txt

# 2. Phase 2 (all optimizations)
java -cp target/classes MainPhase2Complete > phase2_all.txt

# Compare
echo "=== PHASE 1 (No Optimizations) ==="
grep "CPI:" phase1_arithmetic.txt

echo "=== PHASE 2 (With Optimizations) ==="
grep "CPI" phase2_all.txt
```

---

## 🎯 Quick Reference: All Test Commands

### Phase 1
```bash
# All workloads comparison
mvn exec:java

# Individual workloads
mvn exec:java -Dexec.args="arithmetic"
mvn exec:java -Dexec.args="memory"
mvn exec:java -Dexec.args="branch"
mvn exec:java -Dexec.args="loop"
```

### Phase 2
```bash
# Complete demo (all features)
java -cp target/classes MainPhase2Complete

# Specific parts (using grep)
java -cp target/classes MainPhase2Complete | grep -A 20 "PART 1"  # Forwarding
java -cp target/classes MainPhase2Complete | grep -A 30 "PART 2"  # Branch Prediction
java -cp target/classes MainPhase2Complete | grep -A 40 "PART 3"  # ILP Analysis
java -cp target/classes MainPhase2Complete | grep -A 25 "PART 4"  # Loop Unrolling
java -cp target/classes MainPhase2Complete | grep -A 50 "PART 5"  # Superscalar
```

### Build JAR (Alternative)
```bash
# Build
mvn clean package

# Run Phase 1
java -jar target/pipeline-simulator-1.0.0.jar

# Run Phase 2
java -cp target/pipeline-simulator-1.0.0.jar MainPhase2Complete
```

---

## 🐛 Troubleshooting

### Problem: "Could not find or load main class"
```bash
# Solution: Recompile
mvn clean compile
```

### Problem: "No such file or directory"
```bash
# Solution: Make sure you're in project root
cd pipeline-simulator
pwd  # Should end with /pipeline-simulator
```

### Problem: Wrong results or unexpected behavior
```bash
# Solution: Clean rebuild
mvn clean
mvn compile
mvn exec:java
```

### Problem: Maven not found
```bash
# Solution: Install Maven
# macOS: brew install maven
# Linux: sudo apt-get install maven
# Windows: Download from maven.apache.org
```

---

## 📈 Expected Performance Numbers

### Phase 1 (No Optimizations)
- **Arithmetic:** CPI = 2.5-3.0 (many data stalls)
- **Memory:** CPI = 2.0-2.5 (load-use hazards)
- **Branch:** CPI = 1.8-2.2 (control stalls)
- **Loop:** CPI = 2.0-2.5 (mixed hazards)

### Phase 2 (With Optimizations)
- **Forwarding:** CPI = 1.3-1.5 (80% stall reduction)
- **Branch Prediction:** 90-100% accuracy on loops
- **ILP-Friendly:** Theoretical ILP = 2.5-3.0
- **Loop Unrolling:** CPI = 1.2-1.4 (10-15% improvement)
- **Superscalar (ILP-friendly):** 1.3-1.5x speedup, 40-60% dual-issue rate
- **Superscalar (ILP-unfriendly):** 1.0-1.1x speedup, 0-10% dual-issue rate

---

## ✅ Validation Checklist

### Phase 1
- [ ] All 4 workloads run without errors
- [ ] Pipeline visualization shows 5 stages
- [ ] Data stalls appear for dependent instructions
- [ ] Control stalls appear for taken branches
- [ ] CPI > 1.0 for all workloads

### Phase 2
- [ ] Forwarding reduces stalls by 80%+
- [ ] 2-bit predictor achieves 90%+ accuracy on loops
- [ ] ILP analysis shows difference between friendly/unfriendly code
- [ ] Loop unrolling eliminates control stalls
- [ ] Superscalar shows dual-issue on ILP-friendly code
- [ ] Superscalar shows minimal dual-issue on ILP-unfriendly code
- [ ] All metrics are reasonable (no negative numbers, no CPI < 1.0)

---

## 📚 Additional Resources

- **PROJECT_GUIDE.md** - Complete architecture and implementation guide
- **README.md** - Quick overview and setup
- **Source code** - Heavily commented, read for understanding

---

## 🎓 For Grading/Demonstration

**Recommended demonstration sequence:**

1. **Show Phase 1 basics:**
   ```bash
   mvn exec:java -Dexec.args="arithmetic"
   ```
   Point out: data stalls, pipeline stages, hazard detection

2. **Show Phase 2 complete demo:**
   ```bash
   java -cp target/classes MainPhase2Complete
   ```
   Highlight: forwarding speedup, prediction accuracy, ILP analysis, loop unrolling

3. **Show specific optimization:**
   - Pick one (e.g., branch prediction)
   - Show the comparison table
   - Explain why 2-bit is best

4. **Discuss results:**
   - 1.8x speedup from forwarding
   - 100% prediction accuracy on loops
   - 11% CPI improvement from unrolling

**Total demo time: 5-10 minutes**

---

**Last Updated:** Phase 2 Complete  
**Status:** Ready for testing and demonstration ✅
