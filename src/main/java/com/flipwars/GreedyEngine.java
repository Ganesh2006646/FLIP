package com.flipwars;

import java.util.*;

/**
 * MEMBER 2: GREEDY OPTIMIZER
 * Concept: Greedy Search & Local Optima Selection.
 */
public class GreedyEngine {
    private final int totalTiles;
    private final PredictiveAnalyst analyst;
    private final StrategyHeuristics heuristics;

    public GreedyEngine(int totalTiles, PredictiveAnalyst analyst, StrategyHeuristics heuristics) {
        this.totalTiles = totalTiles;
        this.analyst = analyst;
        this.heuristics = heuristics;
    }

    /**
     * The Greedy Decision Loop
     */
    public int getBestMove(boolean[] currentState) {
        double maxScore = Double.NEGATIVE_INFINITY;
        List<Integer> candidates = new ArrayList<>();

        for (int i = 0; i < totalTiles; i++) {
            if (heuristics.isLocked(i))
                continue;

            // 1. Simulate Move
            boolean[] stateAfterMove = currentState.clone();
            analyst.simulateFlip(stateAfterMove, i);

            // 2. Lookahead (Predictive Analysis)
            int opponentResponse = analyst.predictBestResponse(stateAfterMove, true); // Assume response is Player

            boolean[] stateAfterBoth = stateAfterMove.clone();
            if (opponentResponse != -1) {
                analyst.simulateFlip(stateAfterBoth, opponentResponse);
            }

            // 3. Final Evaluation
            double moveScore = analyst.evaluateState(stateAfterBoth, false); // For CPU

            if (moveScore > maxScore) {
                maxScore = moveScore;
                candidates.clear();
                candidates.add(i);
            } else if (moveScore == maxScore) {
                candidates.add(i);
            }
        }

        if (candidates.isEmpty())
            return -1;
        return candidates.get(new Random().nextInt(candidates.size()));
    }

    /**
     * Hint logic (Simplified greedy for Player)
     */
    public int getPlayerHint(boolean[] currentState) {
        int bestId = -1;
        double bestVal = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < totalTiles; i++) {
            if (heuristics.isLocked(i))
                continue;

            boolean[] temp = currentState.clone();
            analyst.simulateFlip(temp, i);
            double val = analyst.evaluateState(temp, true);
            if (val > bestVal) {
                bestVal = val;
                bestId = i;
            }
        }
        return bestId;
    }
}
