package com.flipwars;

/**
 * MEMBER 4: PREDICTIVE ANALYST
 * Concept: State Space Simulation & Lookahead (Adversarial Search).
 */
public class PredictiveAnalyst {
    private final GameGraph graph;
    private final StrategyHeuristics heuristics;
    private final int totalTiles;

    public PredictiveAnalyst(GameGraph graph, StrategyHeuristics heuristics, int totalTiles) {
        this.graph = graph;
        this.heuristics = heuristics;
        this.totalTiles = totalTiles;
    }

    /**
     * Simulation: Clones the board state and flips tiles in a virtual space.
     */
    public void simulateFlip(boolean[] state, int tileId) {
        for (int neighbor : graph.getNeighbors(tileId)) {
            state[neighbor] = !state[neighbor];
        }
    }

    /**
     * ADVANCED EVALUATION: Differential Scoring (Zero-Sum)
     * Score = (AI_Tiles + AI_Bonus) - (Player_Tiles + Player_Bonus)
     */
    public double evaluateState(boolean[] state, boolean forPlayer) {
        double playerScore = 0;
        double cpuScore = 0;

        for (int i = 0; i < totalTiles; i++) {
            double tileVal = 1.0 + heuristics.getTileStrategicValue(i);
            if (state[i]) {
                playerScore += tileVal;
            } else {
                cpuScore += tileVal;
            }
        }
        // Return relative strength
        return forPlayer ? (playerScore - cpuScore) : (cpuScore - playerScore);
    }

    /**
     * Deep Lookahead: Predicts the best move an opponent can make.
     */
    public int predictBestResponse(boolean[] state, boolean isPlayer) {
        double bestVal = Double.NEGATIVE_INFINITY;
        int bestId = -1;

        for (int i = 0; i < totalTiles; i++) {
            if (heuristics.isLocked(i))
                continue;

            boolean[] temp = state.clone();
            simulateFlip(temp, i);
            double val = evaluateState(temp, isPlayer);
            if (val > bestVal) {
                bestVal = val;
                bestId = i;
            }
        }
        return bestId;
    }
}
