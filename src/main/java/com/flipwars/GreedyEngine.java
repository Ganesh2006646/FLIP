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
     * Hint logic (Advanced strategic lookahead for Human)
     */
    public int getPlayerHint(boolean[] currentState) {
        double maxScore = Double.NEGATIVE_INFINITY;
        int bestMove = -1;

        for (int i = 0; i < totalTiles; i++) {
            if (heuristics.isLocked(i))
                continue;

            // 1. Simulate Human Move
            boolean[] stateAfterHuman = currentState.clone();
            analyst.simulateFlip(stateAfterHuman, i);

            // 2. Predict CPU Counter-Attack
            // We ask the analyst: "If the human makes this move, what is the best thing the
            // CPU can do?"
            int cpuResponse = analyst.predictBestResponse(stateAfterHuman, false);

            boolean[] finalState = stateAfterHuman.clone();
            if (cpuResponse != -1) {
                analyst.simulateFlip(finalState, cpuResponse);
            }

            // 3. Final Strategic Evaluation
            // We evaluate how good this move is for the HUMAN after the CPU responds
            double moveScore = analyst.evaluateState(finalState, true);

            if (moveScore > maxScore) {
                maxScore = moveScore;
                bestMove = i;
            }
        }
        return bestMove;
    }
}
