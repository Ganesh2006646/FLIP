package com.flipwars;

import java.util.*;

/**
 * MEMBER 2: GREEDY OPTIMIZER
 * Concept: Greedy Search & Local Optima Selection.
 */
public class GreedyEngine {
    private final int totalTiles;
    private final GameGraph graph;
    private final StrategyHeuristics heuristics;

    public GreedyEngine(int totalTiles, GameGraph graph, StrategyHeuristics heuristics) {
        this.totalTiles = totalTiles;
        this.graph = graph;
        this.heuristics = heuristics;
    }

    /**
     * Simulation: Clones the board state and flips tiles in a virtual space.
     */
    private void simulateFlip(boolean[] state, int tileId) {
        for (int neighbor : graph.getNeighbors(tileId)) {
            state[neighbor] = !state[neighbor];
        }
    }

    /**
     * EVALUATION: Strategic Scoring
     */
    private double evaluateState(boolean[] state, boolean forPlayer) {
        double playerScore = 0;
        double cpuScore = 0;

        for (int i = 0; i < totalTiles; i++) {
            double tileVal = heuristics.getTileStrategicValue(i);
            if (state[i]) {
                playerScore += tileVal;
            } else {
                cpuScore += tileVal;
            }
        }
        return forPlayer ? (playerScore - cpuScore) : (cpuScore - playerScore);
    }

    /**
     * The Greedy Decision Loop - Pure Greedy (No Lookahead)
     */
    public int getBestMove(boolean[] currentState) {
        if (new Random().nextDouble() < 0.50) {
            List<Integer> valid = new ArrayList<>();
            for (int i = 0; i < totalTiles; i++)
                if (!heuristics.isLocked(i))
                    valid.add(i);
            if (!valid.isEmpty())
                return valid.get(new Random().nextInt(valid.size()));
        }

        int bestTile = -1;
        double maxScore = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < totalTiles; i++) {
            if (heuristics.isLocked(i))
                continue;

            boolean[] temp = currentState.clone();
            simulateFlip(temp, i);
            double score = evaluateState(temp, false);

            if (score > maxScore) {
                maxScore = score;
                bestTile = i;
            }
        }

        return bestTile;
    }

    /**
     * Hint logic - Pure Greedy
     */
    public int getPlayerHint(boolean[] currentState) {
        int bestTile = -1;
        double maxScore = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < totalTiles; i++) {
            if (heuristics.isLocked(i))
                continue;

            boolean[] temp = currentState.clone();
            simulateFlip(temp, i);
            double score = evaluateState(temp, true);

            if (score > maxScore) {
                maxScore = score;
                bestTile = i;
            }
        }

        return bestTile;
    }

}
