package prediction;

/**
 * Static Branch Predictors
 * 
 * Static predictors make the same prediction every time, regardless of history.
 * They are simple to implement but less accurate than dynamic predictors.
 */
public class StaticPredictors {
    
    /**
     * Always predicts NOT TAKEN.
     * 
     * Strategy: Assume all branches will fall through to the next instruction.
     * 
     * Good for: If-then statements (often not taken)
     * Bad for: Loops (usually taken on back-edge)
     * 
     * Typical accuracy: 30-40% (depends on workload)
     */
    public static class AlwaysNotTaken extends BaseBranchPredictor {
        
        @Override
        public String getName() {
            return "Always Not Taken";
        }
        
        @Override
        public boolean predict(int pc, boolean isBackward) {
            return false;  // Always predict NOT TAKEN
        }
    }
    
    /**
     * Always predicts TAKEN.
     * 
     * Strategy: Assume all branches will jump to their target.
     * 
     * Good for: Loops (back-edges usually taken)
     * Bad for: If-then statements (often not taken)
     * 
     * Typical accuracy: 60-70% (better than not-taken for loop-heavy code)
     */
    public static class AlwaysTaken extends BaseBranchPredictor {
        
        @Override
        public String getName() {
            return "Always Taken";
        }
        
        @Override
        public boolean predict(int pc, boolean isBackward) {
            return true;  // Always predict TAKEN
        }
    }
    
    /**
     * BTFNT: Backward Taken, Forward Not Taken
     * 
     * Strategy:
     * - Backward branches (target < PC): Predict TAKEN (loop back-edges)
     * - Forward branches (target > PC): Predict NOT TAKEN (if-then)
     * 
     * Rationale:
     * - Loops iterate multiple times → back-edge usually taken
     * - If-statements often fall through → forward branch not taken
     * 
     * Good for: Mixed code with loops and conditionals
     * 
     * Typical accuracy: 65-80% (best static predictor)
     */
    public static class BTFNT extends BaseBranchPredictor {
        
        @Override
        public String getName() {
            return "BTFNT";
        }
        
        @Override
        public boolean predict(int pc, boolean isBackward) {
            return isBackward;  // Taken if backward, not taken if forward
        }
    }
    
    /**
     * Base class providing common functionality for static predictors.
     */
    private static abstract class BaseBranchPredictor implements BranchPredictor {
        
        protected int totalPredictions = 0;
        protected int correctPredictions = 0;
        
        @Override
        public void update(int pc, boolean actuallyTaken) {
            boolean prediction = predict(pc, false);  // Get what we predicted
            totalPredictions++;
            if (prediction == actuallyTaken) {
                correctPredictions++;
            }
        }
        
        @Override
        public double getAccuracy() {
            return totalPredictions == 0 ? 0.0 
                   : (double) correctPredictions / totalPredictions;
        }
        
        @Override
        public int getTotalPredictions() {
            return totalPredictions;
        }
        
        @Override
        public int getCorrectPredictions() {
            return correctPredictions;
        }
        
        @Override
        public int getMispredictions() {
            return totalPredictions - correctPredictions;
        }
        
        @Override
        public void reset() {
            totalPredictions = 0;
            correctPredictions = 0;
        }
    }
}
