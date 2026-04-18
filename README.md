# Pipeline Simulator

CS 510 – Advanced Computer Architecture  
Phase 1: Basic 5-Stage Pipeline Simulator

## 📚 Documentation

- **[README.md](README.md)** - Quick start guide and build instructions
- **[ARCHITECTURE_GUIDE.md](ARCHITECTURE_GUIDE.md)** - Comprehensive explanation of how the simulator works

## Overview

This simulator models a classic 5-stage RISC pipeline processor that demonstrates how modern CPUs execute multiple instructions simultaneously through pipelining. It accurately simulates pipeline hazards and their performance impact.

### The 5 Stages

1. **IF** (Instruction Fetch) - Fetch instruction from memory
2. **ID** (Instruction Decode) - Decode instruction and read registers  
3. **EX** (Execute) - Perform ALU operations
4. **MEM** (Memory Access) - Read/write data memory
5. **WB** (Write Back) - Write results to registers

### Hazards Simulated

- **Data Hazards (RAW)**: When instructions depend on results not yet computed
- **Control Hazards**: Branch mispredictions requiring pipeline flushes
- **Solution**: Pipeline stalls and flushes (no forwarding in Phase 1)

## Project Structure

```
pipeline-simulator/
├── pom.xml                          # Maven configuration
├── README.md                        # This file
├── ARCHITECTURE_GUIDE.md            # Detailed architecture explanation
├── src/main/java/
│   ├── Main.java                    # Entry point with test workloads
│   ├── assembler/
│   │   └── Assembler.java           # Converts assembly to instructions
│   ├── hardware/
│   │   ├── ALU.java                 # Arithmetic Logic Unit
│   │   ├── RegisterFile.java       # 32 general-purpose registers
│   │   └── DataMemory.java          # Word-addressable memory
│   ├── hazards/
│   │   └── HazardDetector.java      # Detects RAW and control hazards
│   ├── isa/
│   │   ├── Instruction.java         # Instruction representation
│   │   ├── OpCode.java              # Supported operations
│   │   └── OpType.java              # Operation categories
│   ├── pipeline/
│   │   ├── PipelineSimulator.java   # Main simulation engine
│   │   └── PipelineRegister.java    # Inter-stage latches
│   └── stats/
│       └── Statistics.java          # Performance metrics
```

## 📚 Documentation

- **[QUICK_REFERENCE.md](QUICK_REFERENCE.md)** - Fast lookup guide with examples
- **[ARCHITECTURE_GUIDE.md](ARCHITECTURE_GUIDE.md)** - Comprehensive explanation of how everything works
- **Source Code** - Heavily commented for educational purposes

## Prerequisites

- Java 11 or higher
- Maven 3.6 or higher

## Building the Project

```bash
# Clean and compile
mvn clean compile

# Create executable JAR
mvn clean package
```

## Running the Simulator

### Using Maven

```bash
# Run all 4 workloads + comparison table
mvn exec:java

# Run a specific workload
mvn exec:java -Dexec.args="arithmetic"
mvn exec:java -Dexec.args="memory"
mvn exec:java -Dexec.args="branch"
mvn exec:java -Dexec.args="loop"
```

### Using the JAR

```bash
# Build the JAR first
mvn clean package

# Run all workloads
java -jar target/pipeline-simulator-1.0.0.jar

# Run specific workload
java -jar target/pipeline-simulator-1.0.0.jar arithmetic
java -jar target/pipeline-simulator-1.0.0.jar memory
java -jar target/pipeline-simulator-1.0.0.jar branch
java -jar target/pipeline-simulator-1.0.0.jar loop
```

## Workloads

1. **arithmetic** - Arithmetic-intensive workload with back-to-back ALU operations creating RAW hazards
2. **memory** - Memory-intensive workload with LOADs followed by immediate use causing load-use stalls
3. **branch** - Branch-heavy workload demonstrating control hazards and pipeline flushes
4. **loop** - Loop-based workload with a counted loop and back-edge branch

## Maven Commands Reference

```bash
# Clean build artifacts
mvn clean

# Compile source code
mvn compile

# Run tests (if any)
mvn test

# Package into JAR
mvn package

# Clean and package
mvn clean package

# Run the application
mvn exec:java

# Run with arguments
mvn exec:java -Dexec.args="workload_name"
```

## Output

The simulator provides detailed output including:
- Cycle-by-cycle pipeline state
- Register file contents
- Memory state
- Performance statistics (CPI, IPC, stalls)
- Comparison table across all workloads

## Development

To import into your IDE:
- **IntelliJ IDEA**: File → Open → Select `pom.xml`
- **Eclipse**: File → Import → Maven → Existing Maven Projects
- **VS Code**: Open folder (Maven extension recommended)
