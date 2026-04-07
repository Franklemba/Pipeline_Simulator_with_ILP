import java.util.List;

public class Pipeline {

    public static void simulatePipeline(List<Instruction> program) {

        Instruction IF = null;
        Instruction ID = null;
        Instruction EX = null;
        Instruction MEM = null;
        Instruction WB = null;

        int pc = 0;
        int cycle = 1;

        while (IF != null || ID != null || EX != null || MEM != null || WB != null || pc < program.size()) {

            // Move pipeline forward
            // WB = MEM;
            // MEM = EX;
            // EX = ID;
            // ID = IF;

            boolean stall = hasDataHazard(ID, EX);

            // Move pipeline forward
            WB = MEM;
            MEM = EX;

            if (stall) {
                // Insert bubble into EX
                EX = Instruction.NOP();
            
                // DO NOT move ID or IF
                // DO NOT fetch new instruction
            
            } else {
                EX = ID;
                ID = IF;
            
                // Fetch new instruction ONLY when no stall
                if (pc < program.size()) {
                    IF = program.get(pc);
                    pc++;
                } else {
                    IF = null;
                }
            }

            // Print pipeline state
            printPipeline(IF, ID, EX, MEM, WB, cycle);

            cycle++;
        }
    }

    private static void printPipeline(Instruction IF, Instruction ID,
                                      Instruction EX, Instruction MEM,
                                      Instruction WB, int cycle) {

        System.out.println("\nCycle " + cycle + ":");

        System.out.println("IF: " + (IF != null ? IF : "---"));
        System.out.println("ID: " + (ID != null ? ID : "---"));
        System.out.println("EX: " + (EX != null ? EX : "---"));
        System.out.println("MEM: " + (MEM != null ? MEM : "---"));
        System.out.println("WB: " + (WB != null ? WB : "---"));
        // System.out.println("EX: " + (EX != null ? EX : "---"));
    }

    private static boolean hasDataHazard(Instruction id, Instruction ex) {

        if (id == null) return false;
    
        String src1 = id.src1;
        String src2 = id.src2;
    
        // Check EX stage
        if (ex != null && ex.dest != null) {
            if (ex.dest.equals(src1) || ex.dest.equals(src2)) {
                return true;
            }
        }
    
        // Check MEM stage
        // if (mem != null && mem.dest != null) {
        //     if (mem.dest.equals(src1) || mem.dest.equals(src2)) {
        //         return true;
        //     }
        // }
    
        return false;
    }

}