package prediction;

/**
 * Branch Predictor Interface
 * 
 * Branch prediction is a technique used to guess whether a branch will be taken
 * or not taken BEFORE the branch condition is evaluated. This allows the pipeline
 * to continue fetching instructions without waiting for the branch to resolve.
 * 
 * WHY BRANCH PREDICTION?
 * =====================
 * Without prediction:
 *   - Must wait for branch to resolve in ID stage
 *   - Pipeline stalls or fetches wrong instructions
 *   - 1-2 cycle penalty per branch
 * 
 * With prediction:
 *   - Guess the outcome and keep fetching
 *   - If guess is correct: No penalty!
 *   - If guess is wrong: Flush and redirect (same penalty as before)
 * 
 * TYPES OF PREDICTORS:
 * ===================
 * 1. Static Prediction (compile-time decision)
 *    - Always Taken
 *    - Always Not Taken
 *    - BTFNT (Backward Taken, Forward Not Taken)
 * 
 * 2. Dynamic Prediction (runtime learning)
 *    - 1-bit predictor (remembers last outcome)
 *    - 2-bit saturating counter (more stable)
 *    - More advanced: gshare, tournament, etc.
 */
public interface BranchPredictor {
    
    /**
     * Predicts whether a branch at the given PC will be taken.
     * 
     * @param pc Program counter of the branch instruction
     * @param isBackward true if branch target is before current PC (loop)
     * @return true if predicting TAKEN, false if predicting NOT TAKEN
     */
    boolean predict(int pc, boolean isBackward);
    
    /**
     * Updates the predictor with the actual branch outcome.
     * This allows dynamic predictors to learn from history.
     * 
     * @param pc Program counter of the branch instruction
     * @param actuallyTaken true if branch was actually taken
     */
    void update(int pc, boolean actuallyTaken);
    
    /**
     * Returns the prediction accuracy as a percentage (0.0 to 1.0).
     * 
     * @return accuracy = correct predictions / total predictions
     */
    double getAccuracy();
    
    /**
     * Returns the total number of predictions made.
     */
    int getTotalPredictions();
    
    /**
     * Returns the number of correct predictions.
     */
    int getCorrectPredictions();
    
    /**
     * Returns the number of mispredictions.
     */
    int getMispredictions();
    
    /**
     * Resets the predictor state (for testing multiple workloads).
     */
    void reset();
    
    /**
     * Returns a human-readable name for this predictor.
     */
    String getName();
}
