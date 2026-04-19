# Phase 2 Progress Report

## ✅ Completed: Data Forwarding (Bypassing)

**Date Completed:** April 19, 2026  
**Time Invested:** ~2 hours  
**Status:** ✅ **WORKING**

---

## 📊 Results Summary

### Arithmetic Workload
- **Without Forwarding:** 18 cycles, CPI = 3.60, 8 data stalls
- **With Forwarding:** 10 cycles, CPI = 2.00, 0 data stalls
- **Improvement:** 1.80x speedup, 44.4% CPI reduction, 80% IPC increase

### Memory Workload  
- **Without Forwarding:** 17 cycles, CPI = 2.83, 6 data stalls
- **With Forwarding:** 13 cycles, CPI = 2.17, 2 data stalls (load-use)
- **Improvement:** 1.31x speedup, 23.5% CPI reduction, 30.8% IPC increase

**Key Insight:** Load-use hazards still require stalls (as expected), but most other data hazards are eliminated!

---

## 🏗️ Implementation Details

### Files Created

1. **`src/main/java/forwarding/ForwardingUnit.java`**
   - Detects forwarding opportunities
   - Implements forwarding path selection logic
   - Handles load-use hazard detection

2. **`src/main/java/forwarding/ForwardingSource.java`**
   - Enum defining forwarding sources (FROM_EX, FROM_MEM, FROM_WB, NONE)

3. **`src/main/java/forwarding/ForwardingDecision.java`**
   - Stores forwarding decisions for rs1 and rs2 operands
   - Tracks which pipeline stage to forward from

4. **`src/main/java/MainPhase2.java`**
   - Demonstration program comparing with/without forwarding
   - Side-by-side performance metrics

### Files Modified

1. **`src/main/java/pipeline/PipelineSimulator.java`**
   - Added `ForwardingUnit` instance
   - Added `forwardingEnabled` configuration flag
   - Updated `tick()` method to detect and apply forwarding
   - Modified `doExecute()` to use forwarded values
   - Added `getOperandValue()` method implementing forwarding multiplexer

2. **`src/main/java/pipeline/PipelineRegister.java`**
   - Added `forwardingDecision` field
   - Added getters/setters for forwarding decisions

3. **`src/main/java/hazards/HazardDetector.java`**
   - Updated documentation for Phase 2
   - Added `needsStallWithForwarding()` method
   - Enhanced RAW detection to work with forwarding

4. **`src/main/java/stats/Statistics.java`**
   - Added `forwardingEvents` counter
   - Added `stallsAvoided` counter
   - Updated `summary()` to display forwarding stats
   - Added getters for new metrics

---

## 🎯 Forwarding Paths Implemented

### 1. EX → EX Forwarding (EX/MEM → EX)
**Most common case**
```
Cycle N:   ADD R1, R2, R3  (in MEM - result available)
Cycle N+1: ADD R4, R1, R5  (in EX - needs R1)
           ↑ Forward R1 from EX/MEM latch
```
**Benefit:** Avoids 2-cycle stall

### 2. MEM → EX Forwarding (MEM/WB → EX)
**One instruction gap**
```
Cycle N:   ADD R1, R2, R3  (in WB - result available)
Cycle N+1: NOP
Cycle N+2: ADD R4, R1, R5  (in EX - needs R1)
           ↑ Forward R1 from MEM/WB latch
```
**Benefit:** Avoids 1-cycle stall

### 3. WB → EX Forwarding (WB → EX)
**Two instruction gap (rare)**
```
Cycle N:   ADD R1, R2, R3  (completing WB)
Cycle N+1: NOP
Cycle N+2: NOP
Cycle N+3: ADD R4, R1, R5  (in EX - needs R1)
           ↑ Forward R1 from WB stage
```
**Benefit:** Could also read from register file (same timing)

---

## ⚠️ Load-Use Hazards (Still Require Stalls)

### Why Forwarding Can't Help

```
LOAD R1, 0(R2)    # R1 not available until MEM completes
ADD  R4, R1, R5   # Needs R1 immediately in EX

Timeline:
Cycle N:   LOAD in EX (computing address)
Cycle N+1: LOAD in MEM (reading memory) ← R1 becomes available HERE
           ADD in EX (needs R1) ← Too early! Must stall 1 cycle
```

**Solution:** Stall 1 cycle, then forward from MEM/WB latch

**Evidence in Results:**
- Memory workload: 2 remaining stalls (both load-use hazards)
- Arithmetic workload: 0 stalls (no loads)

---

## 🧪 How to Test

### Run Phase 2 Demo
```bash
# Compile
mvn clean compile

# Run comparison demo
java -cp target/classes MainPhase2
```

### Run Original Main (Phase 1 mode)
```bash
# Without forwarding (default)
mvn exec:java -Dexec.args="arithmetic"
```

### Enable Forwarding in Main.java
```java
// In Main.java, modify runWorkload():
if (sim == null) {
    sim = new PipelineSimulator();
    sim.setForwardingEnabled(true);  // Add this line
}
```

---

## 📈 Performance Impact

### Arithmetic Workload (Best Case)
- **Stalls Eliminated:** 100% (8 → 0)
- **Speedup:** 1.80x
- **Why:** All dependencies are ALU→ALU, perfect for forwarding

### Memory Workload (Realistic Case)
- **Stalls Eliminated:** 67% (6 → 2)
- **Speedup:** 1.31x
- **Why:** 2 load-use hazards still require stalls

### Expected for Other Workloads
- **Branch:** Minimal impact (control hazards, not data hazards)
- **Loop:** Moderate impact (mix of ALU and memory operations)

---

## 🎓 Educational Value

### What Students Learn

1. **Forwarding Concept**
   - Data doesn't need to go through register file
   - Can bypass directly from pipeline latches
   - Dramatically reduces stalls

2. **Forwarding Limitations**
   - Load-use hazards still require stalls
   - Memory latency is the bottleneck
   - Not all hazards can be eliminated

3. **Hardware Complexity**
   - Need multiplexers at ALU inputs
   - Need forwarding detection logic
   - Need to track data through pipeline

4. **Performance Trade-offs**
   - Hardware cost vs performance gain
   - When is forwarding worth it?
   - Real CPUs always use forwarding

---

## 🔄 Next Steps (Remaining Phase 2 Tasks)

### Priority 1: Enhanced Hazard Detection ✅ (Partially Done)
- [x] Basic integration with forwarding
- [x] Load-use hazard detection
- [ ] Create logic diagram for report
- [ ] Add more comprehensive testing

### Priority 2: Branch Prediction 🔴 (Not Started)
**Estimated:** 4-5 days

**Static Prediction:**
- [ ] Always Taken
- [ ] BTFNT (Backward Taken / Forward Not Taken)
- [ ] Comparison of all 3 static methods

**Dynamic Prediction:**
- [ ] 1-bit branch predictor
- [ ] 2-bit saturating counter predictor
- [ ] Prediction accuracy tracking
- [ ] Performance comparison

### Priority 3: ILP Analysis 🔴 (Not Started)
**Estimated:** 2-3 days
- [ ] Dependency graph construction
- [ ] Independent instruction detection
- [ ] Theoretical ILP calculation
- [ ] Actual ILP measurement

### Priority 4: Loop Unrolling 🟢 (Quick Win)
**Estimated:** 1 day
- [ ] Create unrolled workload
- [ ] Compare with original loop
- [ ] Document benefits

### Priority 5: Superscalar ⚠️ (Optional)
**Estimated:** 5-7 days
- [ ] Dual-issue pipeline
- [ ] Instruction dispatch logic
- [ ] Parallel hazard detection
- [ ] Performance comparison

---

## 📝 For the Report

### Section 4: Hazard Detection & Resolution

**Content to Include:**

1. **Forwarding Paths Diagram**
```
     EX/MEM          MEM/WB          WB
        ↓               ↓             ↓
        └───────────────┴─────────────┴──→ [MUX] → ALU
                                              ↑
                                         Register File
```

2. **Forwarding Detection Algorithm**
```
For each source operand (rs1, rs2):
  1. Check if EX stage writes to this register → Forward from EX/MEM
  2. Else check if MEM stage writes to this register → Forward from MEM/WB
  3. Else check if WB stage writes to this register → Forward from WB
  4. Else read from register file (no hazard)
```

3. **Load-Use Hazard Handling**
- Explain why forwarding can't help
- Show 1-cycle stall is still needed
- Demonstrate with timeline diagram

4. **Performance Results**
- Include comparison tables
- Show speedup calculations
- Discuss why different workloads benefit differently

---

## ✅ Checklist

- [x] ForwardingUnit implemented
- [x] ForwardingSource enum created
- [x] ForwardingDecision class created
- [x] PipelineSimulator updated with forwarding logic
- [x] HazardDetector enhanced for Phase 2
- [x] Statistics tracking forwarding events
- [x] PipelineRegister stores forwarding decisions
- [x] Demo program (MainPhase2) created
- [x] Tested with arithmetic workload
- [x] Tested with memory workload
- [x] Verified load-use hazards still stall
- [x] Verified performance improvements
- [x] Code compiles without errors
- [x] Documentation updated

---

## 🎉 Success Metrics

✅ **Arithmetic workload:** 1.80x speedup (excellent!)  
✅ **Memory workload:** 1.31x speedup (good, limited by load-use)  
✅ **Load-use hazards:** Correctly detected and stalled  
✅ **Code quality:** Clean, well-documented, extensible  
✅ **Educational value:** Clear demonstration of forwarding benefits  

**Phase 2 Task 1 (Data Forwarding): COMPLETE** 🎉

---

## 📊 Updated Assignment Progress

| Phase | Task | Status | Completion |
|-------|------|--------|------------|
| **Phase 1** | Basic Pipeline | ✅ Complete | 100% |
| **Phase 2** | Data Forwarding | ✅ Complete | 100% |
| **Phase 2** | Enhanced Hazard Detection | ⚠️ Partial | 70% |
| **Phase 2** | Branch Prediction | 🔴 Not Started | 0% |
| **Phase 2** | ILP Analysis | 🔴 Not Started | 0% |
| **Phase 2** | Superscalar | 🔴 Not Started | 0% |
| **Phase 2** | Loop Unrolling | 🔴 Not Started | 0% |
| **Phase 3** | Performance Evaluation | ⚠️ Partial | 25% |

**Overall Progress: ~45%** (up from 35%)

**Next Priority:** Branch Prediction (static variants first)
