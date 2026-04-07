public class Instruction {
    String op;
    String dest;
    String src1;
    String src2;

    public Instruction(String op, String dest, String src1, String src2) {
        this.op = op;
        this.dest = dest;
        this.src1 = src1;
        this.src2 = src2;
    }


    public static Instruction NOP() {
        return new Instruction("NOP", null, null, null);
    }
    
    public boolean isNOP() {
        return "NOP".equals(this.op);
    }

    @Override
    public String toString() {
        return op + " " +
                (dest != null ? dest : "") + " " +
                (src1 != null ? src1 : "") + " " +
                (src2 != null ? src2 : "");
    }
}