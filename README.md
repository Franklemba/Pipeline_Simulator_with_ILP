# Pipeline Simulator

CS 510 – Advanced Computer Architecture  
Phase 1: Basic 5-Stage Pipeline Simulator

## Project Structure

This is a Maven-based Java project with the following structure:

```
pipeline-simulator/
├── pom.xml                          # Maven configuration
├── src/
│   └── main/
│       └── java/
│           ├── Main.java            # Entry point
│           ├── assembler/           # Assembly parser
│           ├── hardware/            # ALU, registers, memory
│           ├── hazards/             # Hazard detection
│           ├── isa/                 # Instruction set architecture
│           ├── pipeline/            # Pipeline simulator
│           └── stats/               # Statistics tracking
└── README.md
```

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
