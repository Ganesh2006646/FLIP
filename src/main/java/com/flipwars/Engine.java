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

            if (state[i]) {
                playerScore += tileVal;
            } else {
                cpuScore += tileVal;
            }
        }
        return forPlayer ? (playerScore - cpuScore) : (cpuScore - playerScore);
    }

    // Merge Sort: Divide and Conquer - O(n log n)
    private void mergeSort(List<int[]> list, int left, int right) {
        if (left < right) {
            int mid = (left + right) / 2;
            mergeSort(list, left, mid); // Sort left half
            mergeSort(list, mid + 1, right); // Sort right half
            merge(list, left, mid, right); // Merge sorted halves
        }
    }

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

    public int getBestMove(boolean[] currentState) {
        // 15% blunder factor - sometimes make a random move
        if (new Random().nextDouble() < 0.15) {
            List<Integer> valid = new ArrayList<>();
            for (int i = 0; i < totalTiles; i++)
                if (!rules.isLocked(i))
                    valid.add(i);
            if (!valid.isEmpty())
                return valid.get(new Random().nextInt(valid.size()));
        }

        // Step 1: Iterate every tile and evaluate them
        List<int[]> tileScores = new ArrayList<>(); // [tileId, score*1000]

        for (int i = 0; i < totalTiles; i++) {
            if (rules.isLocked(i))
                continue;

            boolean[] temp = currentState.clone();
            simulateFlip(temp, i);
            double score = evaluateState(temp, false);

            tileScores.add(new int[] { i, (int) (score * 1000) });
        }

        // Step 2: Sort by score descending (Merge Sort)
        mergeSort(tileScores, 0, tileScores.size() - 1);

        // Step 3: Return the first one (best tile)
        return tileScores.isEmpty() ? -1 : tileScores.get(0)[0];
    }

    public int getPlayerHint(boolean[] currentState) {
        // Step 1: Iterate every tile and evaluate them
        List<int[]> tileScores = new ArrayList<>(); // [tileId, score*1000]

        for (int i = 0; i < totalTiles; i++) {
            if (rules.isLocked(i))
                continue;

            boolean[] temp = currentState.clone();
            simulateFlip(temp, i);
            double score = evaluateState(temp, true); // true = for player

            tileScores.add(new int[] { i, (int) (score * 1000) });
        }

        // Step 2: Sort by score descending (Merge Sort)
        mergeSort(tileScores, 0, tileScores.size() - 1);

        // Step 3: Return the first one (best tile)
        return tileScores.isEmpty() ? -1 : tileScores.get(0)[0];
    }
}
