package com.flipwars;

import java.util.*;

/**
 * Game AI Engine using Divide & Conquer Algorithms.
 * 
 * Uses 4 D&C algorithms for move evaluation:
 * 1. Merge Sort (Search Space D&C) - For sorting moves by score
 * 2. Spatial D&C (Quadrant Evaluation) - From DACAlgorithms
 * 3. Structural D&C (DFS Clusters) - From DACAlgorithms
 * 4. Temporal D&C (Minimax) - From DACAlgorithms
 */
public class Engine {
    private final int totalTiles;
    private final Graph graph;
    private final Rules rules;
    private final DACAlgorithms dac;
    private final int gridSize;

    public Engine(int totalTiles, Graph graph, Rules rules) {
        this.totalTiles = totalTiles;
        this.graph = graph;
        this.rules = rules;
        this.dac = new DACAlgorithms();
        this.gridSize = (int) Math.sqrt(totalTiles);
    }

    private void simulateFlip(boolean[] state, int tileId) {
        for (int neighbor : graph.getNeighbors(tileId)) {
            // Lock Protection: Locked tiles are immune
            if (!rules.isLocked(neighbor)) {
                state[neighbor] = !state[neighbor];
            }
        }
    }

    /**
     * Combined evaluation using multiple D&C algorithms.
     * Weights:
     * - Strategic Value: 40%
     * - Quadrant Control (Spatial D&C): 20%
     * - Cluster Strength (DFS D&C): 20%
     * - Minimax Future Score: 20%
     */
    private double evaluateStateCombined(boolean[] state, boolean forPlayer) {
        // 1. Original strategic value evaluation
        double strategicScore = evaluateStrategic(state, forPlayer);

        // 2. Spatial D&C: Quadrant control
        double quadrantScore = dac.evaluateQuadrants(state, gridSize, forPlayer);

        // 3. Structural D&C: Cluster strength
        // FIX: Penalize opponent clusters more heavily (1.5x)
        double myClusterScore = dac.evaluateClusters(state, gridSize, forPlayer);
        double oppClusterScore = dac.evaluateClusters(state, gridSize, !forPlayer);
        double clusterScore = myClusterScore - (oppClusterScore * 1.5); // Stronger penalty

        // Combine with weights
        return (strategicScore * 0.4) + (quadrantScore * 0.2) + (clusterScore * 0.2);
    }

    /**
     * Original strategic evaluation based on tile values
     */
    private double evaluateStrategic(boolean[] state, boolean forPlayer) {
        double playerScore = 0;
        double cpuScore = 0;

        for (int i = 0; i < totalTiles; i++) {
            double tileVal = rules.getTileStrategicValue(i);

            if (state[i]) {
                playerScore += tileVal;
            } else {
                cpuScore += tileVal;
            }
        }
        return forPlayer ? (playerScore - cpuScore) : (cpuScore - playerScore);
    }

    // =========================================================================
    // D&C ALGORITHM 1: MERGE SORT (Search Space D&C)
    // =========================================================================

    /**
     * Merge Sort: Divide and Conquer - O(n log n)
     * Divide: Split list into two halves
     * Conquer: Recursively sort each half
     * Combine: Merge sorted halves in descending order
     */
    private void mergeSort(List<int[]> list, int left, int right) {
        if (left < right) {
            int mid = (left + right) / 2;
            mergeSort(list, left, mid); // Divide: left half
            mergeSort(list, mid + 1, right); // Divide: right half
            merge(list, left, mid, right); // Combine: merge
        }
    }

    /**
     * Merge step: Combine two sorted sublists
     */
    private void merge(List<int[]> list, int left, int mid, int right) {
        List<int[]> temp = new ArrayList<>();
        int i = left, j = mid + 1;

        // Compare and merge in descending order
        while (i <= mid && j <= right) {
            if (list.get(i)[1] >= list.get(j)[1]) {
                temp.add(list.get(i++));
            } else {
                temp.add(list.get(j++));
            }
        }

        // Copy remaining elements
        while (i <= mid)
            temp.add(list.get(i++));
        while (j <= right)
            temp.add(list.get(j++));

        // Copy back to original list
        for (int k = 0; k < temp.size(); k++) {
            list.set(left + k, temp.get(k));
        }
    }

    // =========================================================================
    // MOVE SELECTION METHODS
    // =========================================================================

    /**
     * Get best move for CPU using combined D&C evaluation + Merge Sort
     */
    public int getBestMove(boolean[] currentState) {
        // Step 1: Evaluate all possible moves
        List<int[]> tileScores = new ArrayList<>(); // [tileId, score*1000]

        for (int i = 0; i < totalTiles; i++) {
            if (rules.isLocked(i))
                continue;

            boolean[] temp = currentState.clone();
            simulateFlip(temp, i);
            double score = evaluateStateCombined(temp, false); // Combined D&C evaluation

            tileScores.add(new int[] { i, (int) (score * 1000) });
        }

        // Step 2: Sort by score descending using Merge Sort (D&C Algorithm 1)
        if (!tileScores.isEmpty()) {
            mergeSort(tileScores, 0, tileScores.size() - 1);
        }

        // Step 3: Return the best move (no randomization - deterministic)
        return tileScores.isEmpty() ? -1 : tileScores.get(0)[0];
    }

    /**
     * Get best move using Minimax (D&C Algorithm 4) - for advanced play
     */
    public int getBestMoveMinimax(boolean[] currentState) {
        return dac.minimaxBestMove(currentState, graph, rules, false);
    }

    /**
     * Get hint for player using combined D&C evaluation
     */
    public int getPlayerHint(boolean[] currentState) {
        // Step 1: Evaluate all possible moves
        List<int[]> tileScores = new ArrayList<>(); // [tileId, score*1000]

        for (int i = 0; i < totalTiles; i++) {
            if (rules.isLocked(i))
                continue;

            boolean[] temp = currentState.clone();
            simulateFlip(temp, i);
            double score = evaluateStateCombined(temp, true); // true = for player

            tileScores.add(new int[] { i, (int) (score * 1000) });
        }

        // Step 2: Sort by score descending using Merge Sort
        if (!tileScores.isEmpty()) {
            mergeSort(tileScores, 0, tileScores.size() - 1);
        }

        // Step 3: Return the best move for player
        return tileScores.isEmpty() ? -1 : tileScores.get(0)[0];
    }

    /**
     * Get hint using Minimax for player
     */
    public int getPlayerHintMinimax(boolean[] currentState) {
        return dac.minimaxBestMove(currentState, graph, rules, true);
    }
}
