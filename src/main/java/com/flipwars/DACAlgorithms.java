package com.flipwars;

import java.util.*;

/**
 * Divide and Conquer Algorithms for Board Evaluation.
 * 
 * Contains 3 D&C algorithms:
 * 1. Spatial D&C (Quadrant Evaluation) - Geometer
 * 2. Structural D&C (DFS Clusters) - Graph Theorist
 * 3. Temporal D&C (Minimax) - Futurist
 * 
 * Note: Merge Sort (Search Space D&C) is in Engine.java
 */
public class DACAlgorithms {

    // =========================================================================
    // ALGORITHM 1: SPATIAL D&C - Quadrant Evaluation (Geometer)
    // =========================================================================

    /**
     * Divide: Split NxN grid into 4 quadrants
     * Conquer: Calculate control score for each quadrant
     * Combine: Sum weighted scores (corner quadrants weighted higher)
     * 
     * @param board     Current board state (true = player/yellow, false = CPU/grey)
     * @param gridSize  Size of the grid (4, 5, or 6)
     * @param forPlayer If true, positive score favors player
     * @return Weighted evaluation score
     */
    public double evaluateQuadrants(boolean[] board, int gridSize, boolean forPlayer) {
        int half = gridSize / 2;

        // Divide into 4 quadrants
        double topLeft = evaluateSubGrid(board, 0, 0, half, gridSize, forPlayer);
        double topRight = evaluateSubGrid(board, 0, half, gridSize - half, gridSize, forPlayer);
        double bottomLeft = evaluateSubGrid(board, half, 0, gridSize - half, gridSize, forPlayer);
        double bottomRight = evaluateSubGrid(board, half, half, gridSize - half, gridSize, forPlayer);

        // Combine: Corner quadrants (TL, BR) worth more - they contain actual corners
        // FIX: Different weights for corner vs edge quadrants
        double cornerWeight = 2.0; // TL and BR contain board corners
        double edgeWeight = 1.5; // TR and BL are edge-adjacent

        double score = (topLeft * cornerWeight) + (topRight * edgeWeight)
                + (bottomLeft * edgeWeight) + (bottomRight * cornerWeight);

        return score;
    }

    /**
     * Conquer step: Evaluate a single sub-grid
     * Score = (favorable tiles) - (unfavorable tiles)
     */
    private double evaluateSubGrid(boolean[] board, int startRow, int startCol,
            int size, int gridSize, boolean forPlayer) {
        double score = 0;

        for (int r = startRow; r < startRow + size && r < gridSize; r++) {
            for (int c = startCol; c < startCol + size && c < gridSize; c++) {
                int id = r * gridSize + c;
                boolean isPlayerTile = board[id];

                // +1 for favorable, -1 for unfavorable
                if (forPlayer) {
                    score += isPlayerTile ? 1 : -1;
                } else {
                    score += isPlayerTile ? -1 : 1;
                }
            }
        }

        return score;
    }

    // =========================================================================
    // ALGORITHM 2: STRUCTURAL D&C - DFS Clusters (Graph Theorist)
    // =========================================================================

    /**
     * Divide: Find connected components using DFS
     * Conquer: Score each island by size (large islands = strong position)
     * Combine: Sum of top 3 largest islands
     * 
     * @param board     Current board state
     * @param gridSize  Size of the grid
     * @param forPlayer If true, find player (yellow) clusters
     * @return Cluster-based evaluation score
     */
    public double evaluateClusters(boolean[] board, int gridSize, boolean forPlayer) {
        // Find all clusters for the target color
        List<Integer> clusterSizes = findClusters(board, gridSize, forPlayer);

        // Sort clusters by size (descending) using simple sort
        clusterSizes.sort(Collections.reverseOrder());

        // Combine: Sum top 3 largest clusters (or all if less than 3)
        double score = 0;
        int count = Math.min(3, clusterSizes.size());
        for (int i = 0; i < count; i++) {
            // Larger clusters are exponentially more valuable
            score += clusterSizes.get(i) * clusterSizes.get(i);
        }

        return score;
    }

    /**
     * Find all connected components of the target color using DFS
     */
    private List<Integer> findClusters(boolean[] board, int gridSize, boolean targetColor) {
        boolean[] visited = new boolean[board.length];
        List<Integer> clusterSizes = new ArrayList<>();

        for (int id = 0; id < board.length; id++) {
            if (!visited[id] && board[id] == targetColor) {
                // Found a new cluster, measure its size with DFS
                int size = dfsClusterSize(board, visited, id, gridSize, targetColor);
                if (size > 0) {
                    clusterSizes.add(size);
                }
            }
        }

        return clusterSizes;
    }

    /**
     * DFS to find the size of a connected component
     */
    private int dfsClusterSize(boolean[] board, boolean[] visited, int id,
            int gridSize, boolean targetColor) {
        // Base case: out of bounds, already visited, or wrong color
        if (id < 0 || id >= board.length || visited[id] || board[id] != targetColor) {
            return 0;
        }

        visited[id] = true;
        int size = 1;

        int row = id / gridSize;
        int col = id % gridSize;

        // Recursively visit 4 orthogonal neighbors (Divide step)
        // Up
        if (row > 0) {
            size += dfsClusterSize(board, visited, id - gridSize, gridSize, targetColor);
        }
        // Down
        if (row < gridSize - 1) {
            size += dfsClusterSize(board, visited, id + gridSize, gridSize, targetColor);
        }
        // Left
        if (col > 0) {
            size += dfsClusterSize(board, visited, id - 1, gridSize, targetColor);
        }
        // Right
        if (col < gridSize - 1) {
            size += dfsClusterSize(board, visited, id + 1, gridSize, targetColor);
        }

        return size;
    }

    // =========================================================================
    // ALGORITHM 3: TEMPORAL D&C - Minimax (Futurist)
    // =========================================================================

    /**
     * Divide: Generate future states for each possible move
     * Conquer: Recursively evaluate opponent's best response
     * Combine: Pick move that maximizes our score / minimizes opponent's
     * 
     * @param board     Current board state
     * @param graph     Graph for neighbor lookups
     * @param rules     Rules for locked tiles and strategic values
     * @param forPlayer If true, maximizing for player
     * @return Best move index
     */
    public int minimaxBestMove(boolean[] board, Graph graph, Rules rules, boolean forPlayer) {
        int bestMove = -1;
        double bestScore = forPlayer ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        int totalTiles = board.length;

        for (int move = 0; move < totalTiles; move++) {
            if (rules.isLocked(move))
                continue;

            // Simulate this move
            boolean[] newState = board.clone();
            simulateFlip(newState, move, graph, rules);

            // Recursively evaluate (depth 2 for performance)
            double score = minimaxScore(newState, 2, !forPlayer, graph, rules,
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

            if (forPlayer) {
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
            } else {
                if (score < bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
            }
        }

        return bestMove;
    }

    /**
     * Minimax recursive evaluation with Alpha-Beta pruning
     * 
     * @param board        Current state
     * @param depth        Remaining depth to search
     * @param isMaximizing True if maximizing player's turn
     * @param alpha        Best score for maximizer
     * @param beta         Best score for minimizer
     * @return Evaluation score
     */
    private double minimaxScore(boolean[] board, int depth, boolean isMaximizing,
            Graph graph, Rules rules, double alpha, double beta) {
        // Base case: leaf node
        if (depth == 0) {
            return evaluateBoard(board, rules);
        }

        int totalTiles = board.length;

        if (isMaximizing) {
            double maxEval = Double.NEGATIVE_INFINITY;

            for (int move = 0; move < totalTiles; move++) {
                if (rules.isLocked(move))
                    continue;

                boolean[] newState = board.clone();
                simulateFlip(newState, move, graph, rules);

                double eval = minimaxScore(newState, depth - 1, false, graph, rules, alpha, beta);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);

                // Alpha-Beta pruning
                if (beta <= alpha)
                    break;
            }

            return maxEval == Double.NEGATIVE_INFINITY ? evaluateBoard(board, rules) : maxEval;
        } else {
            double minEval = Double.POSITIVE_INFINITY;

            for (int move = 0; move < totalTiles; move++) {
                if (rules.isLocked(move))
                    continue;

                boolean[] newState = board.clone();
                simulateFlip(newState, move, graph, rules);

                double eval = minimaxScore(newState, depth - 1, true, graph, rules, alpha, beta);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);

                // Alpha-Beta pruning
                if (beta <= alpha)
                    break;
            }

            return minEval == Double.POSITIVE_INFINITY ? evaluateBoard(board, rules) : minEval;
        }
    }

    /**
     * Evaluate board state using strategic tile values
     * Positive = CPU advantage, Negative = Player advantage
     */
    private double evaluateBoard(boolean[] board, Rules rules) {
        double cpuScore = 0;
        double playerScore = 0;

        for (int i = 0; i < board.length; i++) {
            double value = rules.getTileStrategicValue(i);
            if (board[i]) {
                playerScore += value;
            } else {
                cpuScore += value;
            }
        }

        return cpuScore - playerScore;
    }

    /**
     * Simulate a flip on the board
     */
    private void simulateFlip(boolean[] state, int tileId, Graph graph, Rules rules) {
        for (int neighbor : graph.getNeighbors(tileId)) {
            if (!rules.isLocked(neighbor)) {
                state[neighbor] = !state[neighbor];
            }
        }
    }
}
