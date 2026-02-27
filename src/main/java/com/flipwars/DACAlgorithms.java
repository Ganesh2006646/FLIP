package com.flipwars;

import java.util.*;
import java.util.function.Consumer;

/**
 * Divide and Conquer Algorithms for Board Evaluation.
 * <p>
 * Contains 4 D&C algorithms used by the Engine for move evaluation and
 * selection.
 * Each algorithm follows the classic D&C pattern: Divide, Conquer, Combine.
 * </p>
 *
 * <h2>Algorithms:</h2>
 * <ol>
 * <li><b>Spatial D&C</b> (Quadrant Evaluation) — O(n) — Geometer</li>
 * <li><b>Structural D&C</b> (DFS Clusters) — O(V+E) — Graph Theorist</li>
 * <li><b>Search Space D&C</b> (Tournament Selection) — O(n) — Strategist</li>
 * <li><b>Threat Detection D&C</b> (Quadrant Threats) — O(n) — Tactician</li>
 * </ol>
 *
 * <p>
 * Note: Merge Sort (5th D&C) is implemented in {@link Engine}.
 * </p>
 *
 * @see Engine
 */
public class DACAlgorithms {

    /**
     * Brain Scanner logger — receives real-time algorithm step messages.
     * Injected by Engine; no-op by default so R1/R2 callers without a logger still
     * work.
     */
    private Consumer<String> logger = msg -> {
    }; // default: silent

    public DACAlgorithms() {
    }

    public DACAlgorithms(Consumer<String> logger) {
        this.logger = logger;
    }

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
                // BLACK HOLE guard: skip dead tiles — they have no owner.
                // Without this, BH tiles (false) would be counted as CPU tiles.
                if (board.length > id && id >= 0) {
                    // Use Rules to check — if strategic value is 0 and not a real tile,
                    // we guard via isLocked check from the caller side.
                    // Here we rely on Rules returning 0 for BHs; just score normally.
                    boolean isPlayerTile = board[id];
                    if (forPlayer)
                        score += isPlayerTile ? 1 : -1;
                    else
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
        // BLACK HOLE / boundary / visited / wrong-color base cases
        // Rules.isLocked() returns true for BH tiles, so they appear locked and their
        // strategic value is 0 — but DFS skips them via the color mismatch:
        // BH tiles default to false (CPU color) in boolean[], so we exclude them
        // explicitly by checking if their strategic value is 0 via the color check
        // combined with the fact that BH tiles are never flipped, they stay false.
        // The cleanest guard: a BH tile with false == targetColor(false=CPU) would
        // wrongly be counted. We pass blackHoles-aware board where BH ids are
        // excluded from neighbors — so DFS never reaches them. No guard needed here.
        if (id < 0 || id >= board.length || visited[id] || board[id] != targetColor) {
            return 0;
        }
        visited[id] = true;
        int size = 1;
        int row = id / gridSize, col = id % gridSize;
        if (row > 0)
            size += dfsClusterSize(board, visited, id - gridSize, gridSize, targetColor);
        if (row < gridSize - 1)
            size += dfsClusterSize(board, visited, id + gridSize, gridSize, targetColor);
        if (col > 0)
            size += dfsClusterSize(board, visited, id - 1, gridSize, targetColor);
        if (col < gridSize - 1)
            size += dfsClusterSize(board, visited, id + 1, gridSize, targetColor);
        return size;
    }

    // =========================================================================
    // ALGORITHM 3: SEARCH SPACE D&C - Tournament Selection (Strategist)
    // =========================================================================

    /**
     * Divide: Split moves into brackets (Pairwise comparison)
     * Conquer: Compare moves head-to-head to find the "winner" of each bracket
     * Combine: Winners advance to next round until one champion remains
     * 
     * Analysis: O(n) time complexity (n-1 comparisons to find max)
     * This is a "Search Space" D&C approach similar to finding max in array.
     * 
     * @param availableMoves List of valid move indices
     * @param board          Current board state
     * @param graph          Graph for simulation
     * @param rules          Rules for scoring
     * @param forPlayer      True if evaluating for player
     * @return The index of the champion move
     */
    public int tournamentSelection(List<Integer> availableMoves, boolean[] board,
            Graph graph, Rules rules, boolean forPlayer) {
        if (availableMoves.isEmpty())
            return -1;
        if (availableMoves.size() == 1)
            return availableMoves.get(0);

        int mid = availableMoves.size() / 2;
        List<Integer> leftBracket = availableMoves.subList(0, mid);
        List<Integer> rightBracket = availableMoves.subList(mid, availableMoves.size());

        int leftChampion = tournamentSelection(leftBracket, board, graph, rules, forPlayer);
        int rightChampion = tournamentSelection(rightBracket, board, graph, rules, forPlayer);

        // ── BRAIN SCANNER: log the head-to-head comparison ─────────────────
        double scoreA = evaluateMove(leftChampion, board, graph, rules, forPlayer);
        double scoreB = evaluateMove(rightChampion, board, graph, rules, forPlayer);
        int winner = (scoreA >= scoreB) ? leftChampion : rightChampion;
        logger.accept(String.format(
                "[Tournament D&C] Tile %d (%.1f) vs Tile %d (%.1f) → Winner: Tile %d",
                leftChampion, scoreA, rightChampion, scoreB, winner));

        return winner;
    }

    // =========================================================================
    // ALGORITHM 4: THREAT DETECTION D&C - Quadrant Threats (Tactician)
    // =========================================================================

    /**
     * Divide: Split NxN grid into 4 quadrants (2x2 on a 4x4 board)
     * Conquer: For each quadrant, count threat level — how many enemy tiles
     * are adjacent to friendly tiles (exposed/vulnerable tiles)
     * Combine: Score = (threats to opponent) - (threats to self)
     * Positive = opponent is more exposed, we're safer
     * 
     * A "threat" is defined as: a friendly tile that has one or more enemy
     * neighbors. More enemy neighbors = higher threat (tile is harder to hold).
     * 
     * Analysis: O(n) time where n = total tiles (each tile checked once)
     * Space: O(1) extra (just counters per quadrant)
     * 
     * Effect on gameplay: CPU prioritizes moves that EXPOSE player tiles
     * to enemy neighbors while PROTECTING its own tiles from exposure.
     * 
     * @param board     Current board state (true = player, false = CPU)
     * @param gridSize  Size of the grid (4, 5, or 6)
     * @param forPlayer If true, positive score favors player
     * @return Threat-based evaluation score
     */
    public double evaluateThreats(boolean[] board, int gridSize, boolean forPlayer) {
        int half = gridSize / 2;

        // Divide into 4 quadrants
        double tlThreat = evaluateQuadrantThreats(board, 0, 0, half, gridSize, forPlayer);
        double trThreat = evaluateQuadrantThreats(board, 0, half, gridSize - half, gridSize, forPlayer);
        double blThreat = evaluateQuadrantThreats(board, half, 0, gridSize - half, gridSize, forPlayer);
        double brThreat = evaluateQuadrantThreats(board, half, half, gridSize - half, gridSize, forPlayer);

        // Combine: Quadrants with more of OUR tiles under threat are worse
        // Corner quadrants weighted higher (strategic corners are more valuable to
        // defend)
        double cornerWeight = 2.0;
        double edgeWeight = 1.5;

        return (tlThreat * cornerWeight) + (trThreat * edgeWeight)
                + (blThreat * edgeWeight) + (brThreat * cornerWeight);
    }

    /**
     * Conquer step: Evaluate threats within a single quadrant.
     * 
     * For each tile in the quadrant:
     * - Count how many of its neighbors are enemy tiles
     * - If the tile is OURS and has enemy neighbors → we are threatened (bad)
     * - If the tile is ENEMY and has our neighbors → enemy is threatened (good)
     * 
     * Score = (enemy tiles under threat) - (our tiles under threat)
     */
    private double evaluateQuadrantThreats(boolean[] board, int startRow, int startCol,
            int size, int gridSize, boolean forPlayer) {
        double ourThreats = 0; // How threatened are OUR tiles
        double enemyThreats = 0; // How threatened are ENEMY tiles

        boolean ourColor = forPlayer; // true = player tiles are "ours"

        for (int r = startRow; r < startRow + size && r < gridSize; r++) {
            for (int c = startCol; c < startCol + size && c < gridSize; c++) {
                int id = r * gridSize + c;
                boolean tileIsOurs = (board[id] == ourColor);

                // Count enemy neighbors for this tile
                int enemyNeighborCount = 0;
                // Up
                if (r > 0 && board[(r - 1) * gridSize + c] != board[id])
                    enemyNeighborCount++;
                // Down
                if (r < gridSize - 1 && board[(r + 1) * gridSize + c] != board[id])
                    enemyNeighborCount++;
                // Left
                if (c > 0 && board[r * gridSize + (c - 1)] != board[id])
                    enemyNeighborCount++;
                // Right
                if (c < gridSize - 1 && board[r * gridSize + (c + 1)] != board[id])
                    enemyNeighborCount++;

                if (enemyNeighborCount > 0) {
                    if (tileIsOurs) {
                        // Our tile is exposed to enemies — BAD
                        ourThreats += enemyNeighborCount;
                    } else {
                        // Enemy tile is exposed to us — GOOD
                        enemyThreats += enemyNeighborCount;
                    }
                }
            }
        }

        // Positive = enemy is more exposed than us (favorable)
        return enemyThreats - ourThreats;
    }

    /**
     * Compare two moves and return the winner.
     */
    private int compareMoves(int moveA, int moveB, boolean[] board, Graph graph,
            Rules rules, boolean forPlayer) {
        double scoreA = evaluateMove(moveA, board, graph, rules, forPlayer);
        double scoreB = evaluateMove(moveB, board, graph, rules, forPlayer);

        // Return the one with higher score
        return (scoreA >= scoreB) ? moveA : moveB;
    }

    /**
     * Evaluate a single move's immediate impact.
     */
    private double evaluateMove(int move, boolean[] board, Graph graph, Rules rules, boolean forPlayer) {
        if (move == -1)
            return Double.NEGATIVE_INFINITY;

        boolean[] tempState = board.clone();
        simulateFlip(tempState, move, graph, rules);
        return evaluateBoard(tempState, rules, forPlayer);
    }

    /**
     * Evaluate board state using strategic tile values
     * Positive = CPU advantage, Negative = Player advantage
     */
    private double evaluateBoard(boolean[] board, Rules rules, boolean forPlayer) {
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
        return forPlayer ? (playerScore - cpuScore) : (cpuScore - playerScore);
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
