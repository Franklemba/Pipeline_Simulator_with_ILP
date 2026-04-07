import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {

        List<Instruction> program = new ArrayList<>();

        program.add(new Instruction("ADD", "R1", "R2", "R3"));
        program.add(new Instruction("SUB", "R4", "R1", "R5"));
        program.add(new Instruction("MUL", "R6", "R4", "R7"));

        Pipeline.simulatePipeline(program);
    }
}