package prediction;

import java.util.HashMap;
import java.util.Map;

/**
 * 1-Bit Branch Predictor
 * 
 * CONCEPT:
 * =======
 * Maintains a Branch History Table (BHT) that remembers the last outcome
 * of each branch. Uses that outcome to predict the next time.
 * 
 * STATE MACHINE:
 * =============
 *     Last outcome = NOT TAKEN (0)
 *            ↓
 *     Predict: NOT TAKEN
 *            ↓
 *     If actually TAKEN → switch to state 1
 *     If actually NOT TAKEN → stay in state 0
 * 
 *     Last outcome = TAKEN (1)
 *            ↓
 *     Predict: TAKEN
 *            ↓
 *     If actually NOT TAKEN → switch to state 0
 *     If actually TAKEN → stay in state 1
 * 
 * ADVANTAGES:
 * ==========
 * - Simple to implement
 * - Learns from history
 * - Better than static for regular patterns
 * 
 * DISADVANTAGES:
 * =============
 * - Too sensitive to single mispredictions
 * - Poor for alternating patterns (T, NT, T, NT, ...)
 * - Mispredicts twice when loop exits (last iteration + first time back)
 * 
 * Example:
 *   Loop that iterates 10 times:
 *   - Iterations 1-9: Predict TAKEN (correct)
 *   - Iteration 10: Predict TAKEN (WRONG - loop exits)
 *   - Next time loop starts: Predict NOT TAKEN (WRONG - just entered loop)
 *   - Accuracy: 8/10 = 80%
 * 
 * TYPICAL ACCURACY: 70-85%
 */
public class OneBitPredictor implements BranchPredictor {
    
    /** Branch History Table: PC → last outcome (true = taken, false = not taken) */
    private final Map<Integer, Boolean> branchHistoryTable;
    
    /** Statistics */
    private int totalPredictions = 0;
    private int correctPredictions = 0;
    
    public OneBitPredictor() {
        this.branchHistoryTable = new HashMap<>();
    }
    
    @Override
    public String getName() {
        return "1-Bit Dynamic Predictor";
    }
    
    @Override
    public boolean predict(int pc, boolean isBackward) {
        // If we've never seen this branch, use static prediction (BTFNT)
        if (!branchHistoryTable.containsKey(pc)) {
            return isBackward;  // Backward = taken, forward = not taken
        }
        
        // Otherwise, predict based on last outcome
        return branchHistoryTable.get(pc);
    }
    
    @Override
    public void update(int pc, boolean actuallyTaken) {
        // Get what we predicted
        boolean prediction = predict(pc, false);
        
        // Update statistics
        totalPredictions++;
        if (prediction == actuallyTaken) {
            correctPredictions++;
        }
        
        // Update history table with actual outcome
        branchHistoryTable.put(pc, actuallyTaken);
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
        branchHistoryTable.clear();
        totalPredictions = 0;
        correctPredictions = 0;
    }
}
