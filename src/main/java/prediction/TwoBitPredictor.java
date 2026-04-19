package prediction;

import java.util.HashMap;
import java.util.Map;

/**
 * 2-Bit Saturating Counter Branch Predictor
 * 
 * CONCEPT:
 * =======
 * Uses a 2-bit counter for each branch, providing more stability than 1-bit.
 * Requires TWO consecutive mispredictions to change the prediction.
 * 
 * STATE MACHINE:
 * =============
 * 
 *   00 (Strongly Not Taken) ←→ 01 (Weakly Not Taken)
 *            ↓                           ↓
 *      Predict: NOT TAKEN          Predict: NOT TAKEN
 *            ↓                           ↓
 *   10 (Weakly Taken) ←→ 11 (Strongly Taken)
 *            ↓                           ↓
 *      Predict: TAKEN              Predict: TAKEN
 * 
 * Transitions:
 * - If branch TAKEN: increment counter (saturate at 11)
 * - If branch NOT TAKEN: decrement counter (saturate at 00)
 * - Predict TAKEN if counter >= 10 (2 or 3)
 * - Predict NOT TAKEN if counter <= 01 (0 or 1)
 * 
 * ADVANTAGES:
 * ==========
 * - More stable than 1-bit (tolerates single mispredictions)
 * - Better for loops (doesn't flip on loop exit)
 * - Industry standard for simple predictors
 * 
 * EXAMPLE - Loop with 10 iterations:
 * ================================
 * Initial state: 00 (Strongly Not Taken)
 * 
 * First iteration:
 *   - Predict: NOT TAKEN (WRONG)
 *   - Actually: TAKEN
 *   - New state: 01 (Weakly Not Taken)
 * 
 * Second iteration:
 *   - Predict: NOT TAKEN (WRONG)
 *   - Actually: TAKEN
 *   - New state: 10 (Weakly Taken)
 * 
 * Iterations 3-10:
 *   - Predict: TAKEN (CORRECT)
 *   - Actually: TAKEN
 *   - State: 11 (Strongly Taken)
 * 
 * Loop exit:
 *   - Predict: TAKEN (WRONG)
 *   - Actually: NOT TAKEN
 *   - New state: 10 (Weakly Taken)
 * 
 * Next time loop starts:
 *   - Predict: TAKEN (CORRECT!)  ← Better than 1-bit!
 *   - Actually: TAKEN
 *   - State: 11 (Strongly Taken)
 * 
 * Accuracy: 9/11 = 82% (vs 1-bit: 8/10 = 80%)
 * 
 * TYPICAL ACCURACY: 85-95%
 */
public class TwoBitPredictor implements BranchPredictor {
    
    /**
     * 2-bit saturating counter states
     */
    private enum CounterState {
        STRONGLY_NOT_TAKEN(0),   // 00
        WEAKLY_NOT_TAKEN(1),     // 01
        WEAKLY_TAKEN(2),         // 10
        STRONGLY_TAKEN(3);       // 11
        
        private final int value;
        
        CounterState(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
        
        public boolean predictTaken() {
            return value >= 2;  // Taken if 10 or 11
        }
        
        public CounterState increment() {
            switch (this) {
                case STRONGLY_NOT_TAKEN: return WEAKLY_NOT_TAKEN;
                case WEAKLY_NOT_TAKEN:   return WEAKLY_TAKEN;
                case WEAKLY_TAKEN:       return STRONGLY_TAKEN;
                case STRONGLY_TAKEN:     return STRONGLY_TAKEN;  // Saturate
                default: return this;
            }
        }
        
        public CounterState decrement() {
            switch (this) {
                case STRONGLY_TAKEN:     return WEAKLY_TAKEN;
                case WEAKLY_TAKEN:       return WEAKLY_NOT_TAKEN;
                case WEAKLY_NOT_TAKEN:   return STRONGLY_NOT_TAKEN;
                case STRONGLY_NOT_TAKEN: return STRONGLY_NOT_TAKEN;  // Saturate
                default: return this;
            }
        }
        
        public static CounterState fromValue(int value) {
            for (CounterState state : values()) {
                if (state.value == value) return state;
            }
            return WEAKLY_NOT_TAKEN;  // Default
        }
    }
    
    /** Branch History Table: PC → 2-bit counter state */
    private final Map<Integer, CounterState> branchHistoryTable;
    
    /** Statistics */
    private int totalPredictions = 0;
    private int correctPredictions = 0;
    
    public TwoBitPredictor() {
        this.branchHistoryTable = new HashMap<>();
    }
    
    @Override
    public String getName() {
        return "2-Bit Saturating Counter Predictor";
    }
    
    @Override
    public boolean predict(int pc, boolean isBackward) {
        // If we've never seen this branch, initialize based on direction
        if (!branchHistoryTable.containsKey(pc)) {
            // Initialize to weakly predict based on direction
            CounterState initialState = isBackward 
                ? CounterState.WEAKLY_TAKEN      // Loops usually taken
                : CounterState.WEAKLY_NOT_TAKEN; // If-statements usually not taken
            branchHistoryTable.put(pc, initialState);
        }
        
        // Predict based on current counter state
        return branchHistoryTable.get(pc).predictTaken();
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
        
        // Update counter based on actual outcome
        CounterState currentState = branchHistoryTable.get(pc);
        CounterState newState = actuallyTaken 
            ? currentState.increment()   // Branch taken → move toward "taken"
            : currentState.decrement();  // Branch not taken → move toward "not taken"
        
        branchHistoryTable.put(pc, newState);
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
