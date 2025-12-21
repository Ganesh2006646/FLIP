package com.flipwars;

import java.util.*;

/**
 * Concept: Greedy Search & Local Optima Selection.
 */
public class Engine {
    private final int totalTiles;
    private final Graph graph;
    private final Rules rules;

    public Engine(int totalTiles, Graph graph, Rules rules) {
        this.totalTiles = totalTiles;
        this.graph = graph;
        this.rules = rules;
    }

    private void simulateFlip(boolean[] state, int tileId) {
        for (int neighbor : graph.getNeighbors(tileId)) {
            // Lock Protection: Locked tiles are immune
            if (!rules.isLocked(neighbor)) {
                state[neighbor] = !state[neighbor];
            }
        }
    }

    private double evaluateState(boolean[] state, boolean forPlayer) {
        double playerScore = 0;
        double cpuScore = 0;

        for (int i = 0; i < totalTiles; i++) {
            double tileVal = rules.getTileStrategicValue(i);

            // Strategic Bonus for Locked Tiles (Protected Points)
            if (rules.isLocked(i)) {
                tileVal *= 1.5; // Locked tiles are worth 50% more due to protection
            }

            if (state[i]) {
                playerScore += tileVal;
            } else {
                cpuScore += tileVal;
            }
        }
        return forPlayer ? (playerScore - cpuScore) : (cpuScore - playerScore);
    }

    public int getBestMove(boolean[] currentState) {
        if (new Random().nextDouble() < 0.15) { // 15% blunder factor
            List<Integer> valid = new ArrayList<>();
            for (int i = 0; i < totalTiles; i++)
                if (!rules.isLocked(i))
                    valid.add(i);
            if (!valid.isEmpty())
                return valid.get(new Random().nextInt(valid.size()));
        }

        int bestTile = -1;
        double maxScore = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < totalTiles; i++) {
            if (rules.isLocked(i))
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

    public int getPlayerHint(boolean[] currentState) {
        int bestTile = -1;
        double maxScore = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < totalTiles; i++) {
            if (rules.isLocked(i))
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
