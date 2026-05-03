# CPU Pipeline Simulator

**CS 510 – Advanced Computer Architecture**  
A 5-stage RISC pipeline simulator with real-world optimizations

![Phase 1](https://img.shields.io/badge/Phase%201-Complete-success) ![Phase 2](https://img.shields.io/badge/Phase%202-Complete-success) ![Phase 3](https://img.shields.io/badge/Phase%203-Complete-success)

## Quick Start

```bash
# Compile everything
mvn clean compile

# Run the basic pipeline (Phase 1)
mvn exec:java

# Run with all optimizations (Phase 2)
java -cp target/classes MainPhase2Complete

# Performance evaluation (Phase 3)
java -cp target/classes PerformanceEvaluator
```

## What's Inside

**Phase 1 - Basic Pipeline**
- Classic 5-stage design: Fetch → Decode → Execute → Memory → Writeback
- Handles data and control hazards
- Four test programs showing different hazard scenarios
- Shows you what's happening cycle-by-cycle

**Phase 2 - Making It Fast**
- Data forwarding cuts execution time by 80%
- Branch predictor gets 100% accuracy on loops
- ILP analyzer finds parallelism opportunities
- Loop unrolling reduces overhead by 11%
- Superscalar mode issues 2 instructions per cycle

## Performance Numbers

| What We Added | How Much Faster |
|---------------|-----------------|
| Data Forwarding | 1.8x speedup |
| Branch Prediction | 100% accurate on loops |
| Loop Unrolling | 11% fewer cycles |
| Superscalar | Issues 2 instructions 45% of the time |

## How It Works

```
┌─────┐   ┌─────┐   ┌─────┐   ┌─────┐   ┌─────┐
│ IF  │ → │ ID  │ → │ EX  │ → │ MEM │ → │ WB  │
└─────┘   └─────┘   └─────┘   └─────┘   └─────┘
   ↑          ↑         ↑         ↑         ↑
   └──────────┴─────────┴─────────┴─────────┘
         Results forwarded back when needed
```

The pipeline handles three types of problems:
- **Data hazards** - when an instruction needs a result that's not ready yet
- **Control hazards** - when we don't know which instruction comes next (branches)
- **Structural hazards** - none in our design (we have enough hardware)

## Test Programs

We include four programs that stress different parts of the pipeline:

1. **arithmetic** - Long chains of dependent calculations
2. **memory** - Lots of loads and stores
3. **branch** - Conditional jumps everywhere
4. **loop** - A realistic loop with mixed operations

## Running Tests

```bash
# Run a specific test
mvn exec:java -Dexec.args="arithmetic"
mvn exec:java -Dexec.args="memory"
mvn exec:java -Dexec.args="branch"
mvn exec:java -Dexec.args="loop"

# Build a standalone JAR
mvn clean package
java -jar target/pipeline-simulator-1.0.0.jar
```

## Requirements

- Java 11 or newer
- Maven 3.6 or newer

## Documentation

- **[PROJECT_GUIDE.md](PROJECT_GUIDE.md)** - Full walkthrough of everything
- **[TESTING.md](TESTING.md)** - How to run and verify each phase
- **[PHASE3_COMPLETE_REPORT.md](PHASE3_COMPLETE_REPORT.md)** - Performance analysis

## Project Status

All three phases are complete. Only the final technical report remains.

## What You'll Learn

This simulator shows you how modern CPUs actually work:
- Why pipelines make processors fast
- How forwarding avoids wasted cycles
- Why branch prediction matters
- How processors find parallelism
- What superscalar execution means

It's a hands-on way to understand the techniques that make your computer fast.

---

**Academic project for CS 510 - Advanced Computer Architecture**
