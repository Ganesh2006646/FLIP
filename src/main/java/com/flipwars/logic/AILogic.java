package com.flipwars.logic;

import com.flipwars.constants.GameConstants;
import java.util.Random;

public class AILogic {

    /**
     * Calculates the best move for the AI (CPU/Grey) or Hint (Player/Yellow).
     * 
     * @param gridState         Current state of the grid
     * @param graph             The game graph
     * @param maximizeForYellow If true, maximize Yellow (Hint). If false, maximize
     *                          Grey (CPU).
     * @param lastHumanMove     The last move made by human (for deadlock
     *                          prevention)
     * @param turnsPlayed       Current turns played
     * @return The best tile index to flip
     */
    public static int getGreedyMove(boolean[] gridState, GameGraph graph, boolean maximizeForYellow, int lastHumanMove,
            int turnsPlayed) {
        int bestMove = -1;
        int maxGain = -100;

        for (int i = 0; i < GameConstants.TOTAL_TILES; i++) {
            // Deadlock Prevention (Ko Rule)
            // If aiming for CPU move (maximizeForYellow=false), avoid lastHumanMove
            // immediately.
            if (!maximizeForYellow && i == lastHumanMove && turnsPlayed < GameConstants.MAX_TURNS - 1) {
                continue;
            }

            // 1. Simulate
            boolean[] simulationState = gridState.clone();
            for (int neighbor : graph.getNeighbors(i)) {
                simulationState[neighbor] = !simulationState[neighbor];
            }

            // 2. Count Color of Interest
            int currentCount = 0;
            for (boolean b : simulationState) {
                // b is true for Yellow, false for Grey
                if (maximizeForYellow) {
                    if (b)
                        currentCount++;
                } else {
                    if (!b)
                        currentCount++;
                }
            }

            // 3. Calculate Net Gain relative to current state
            int existingCount = 0;
            for (boolean b : gridState) {
                if (maximizeForYellow) {
                    if (b)
                        existingCount++;
                } else {
                    if (!b)
                        existingCount++;
                }
            }

            int netGain = currentCount - existingCount;

            if (netGain > maxGain) {
                maxGain = netGain;
                bestMove = i;
            }
        }

        // Fallback for CPU if entirely blocked (rare)
        if (bestMove == -1 && !maximizeForYellow) {
            bestMove = (lastHumanMove + 1) % GameConstants.TOTAL_TILES;
        }

        return bestMove;
    }

    public static int getRandomMove() {
        return new Random().nextInt(GameConstants.TOTAL_TILES);
    }
}
