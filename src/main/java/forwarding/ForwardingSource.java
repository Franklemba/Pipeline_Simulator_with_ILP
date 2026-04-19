package forwarding;

/**
 * Identifies the source stage for data forwarding.
 * 
 * FORWARDING PATHS:
 * ================
 * 
 * NONE:     No forwarding needed
 *           - Register file has the correct value
 *           - No data hazard exists
 * 
 * FROM_EX:  Forward from EX/MEM pipeline register
 *           - Producer just completed EX stage
 *           - Consumer is about to enter EX stage
 *           - Example: ADD R1,R2,R3 (in MEM) → ADD R4,R1,R5 (in EX)
 *           - Saves 2 stall cycles
 * 
 * FROM_MEM: Forward from MEM/WB pipeline register
 *           - Producer just completed MEM stage
 *           - Consumer is about to enter EX stage
 *           - Example: ADD R1,R2,R3 (in WB) → ADD R4,R1,R5 (in EX)
 *           - Saves 1 stall cycle
 * 
 * FROM_WB:  Forward from WB stage (rare)
 *           - Producer is completing WB stage
 *           - Consumer is about to enter EX stage
 *           - Could also read from register file (same timing)
 *           - Included for completeness
 */
public enum ForwardingSource {
    NONE,       // No forwarding - use register file
    FROM_EX,    // Forward from EX/MEM latch (ALU result)
    FROM_MEM,   // Forward from MEM/WB latch (memory or ALU result)
    FROM_WB     // Forward from WB stage (about to write to register file)
}
