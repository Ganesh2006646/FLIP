package com.flipwars;

import java.util.*;
import java.util.function.Consumer;

/**
 * Game AI Engine — Supports Multiple Algorithm Versions.
 * <p>
 * This engine can switch between different algorithm paradigms at runtime,
 * allowing direct comparison of AI behavior across review milestones.
 * </p>
 *
 * <h2>Version 1 (R1 — Greedy Baseline):</h2>
 * <ul>
 * <li>Simple tile-value counting (corners, edges, traps)</li>
 * <li>15% random blunder factor (makes CPU beatable)</li>
 * <li>Merge Sort for move ranking</li>
 * </ul>
 *
 * <h2>Version 2 (R2 — Divide & Conquer):</h2>
 * <ul>
 * <li>5 D&C algorithms for sophisticated board evaluation</li>
 * <li>Tournament Selection for CPU move (O(n))</li>
 * <li>Merge Sort for Player Hint ranking (O(n log n))</li>
 * <li>Spatial, Cluster, and Threat D&C for scoring</li>
 * </ul>
 *
 * <h2>Version 3 (R3 — DP + Backtracking):</h2>
 * <ul>
 * <li>SUHAS — Pure Backtracking: in-place doMove/undoMove (O(1) space)</li>
 * <li>MANEESH — Alpha-Beta Pruning Minimax with D&amp;C move ordering</li>
 * <li>GANESH — Transposition Table (Zobrist Hash + Top-Down Memoization)</li>
 * <li>BALAJI — Bottom-Up Bitmask DP / 4x4 End-Game Oracle (65536 states)</li>
 * </ul>
 */
public class Engine {
    private final int totalTiles;
    private final Graph graph;
    private final Rules rules;
    private DACAlgorithms dac; // non-final: logger can be reinjected
    private final R3Algorithms r3; // R3: DP + Backtracking engine
    private final int gridSize;

    /** Brain Scanner logger — forwards AI reasoning to the UI text area. */
    private Consumer<String> logger = msg -> {
    }; // default: silent

    /** Current algorithm version: 1 = R1 Greedy, 2 = R2 D&C, 3 = R3 (future) */
    private int version = 2;

    public Engine(int totalTiles, Graph graph, Rules rules) {
        this.totalTiles = totalTiles;
        this.graph = graph;
        this.rules = rules;
        this.dac = new DACAlgorithms();
        this.gridSize = (int) Math.sqrt(totalTiles);
        this.r3 = new R3Algorithms(this.gridSize, graph, rules);
    }

    /**
     * Brain-Scanner-aware constructor.
     * The logger is forwarded to DACAlgorithms so Tournament Selection
     * comparisons appear live in the Brain Scanner text area.
     */
    public Engine(int totalTiles, Graph graph, Rules rules, Consumer<String> logger) {
        this.totalTiles = totalTiles;
        this.graph = graph;
        this.rules = rules;
        this.logger = logger;
        this.dac = new DACAlgorithms(logger);
        this.gridSize = (int) Math.sqrt(totalTiles);
        this.r3 = new R3Algorithms(this.gridSize, graph, rules);
    }

    /**
     * Re-injects the logger at runtime (e.g., after Brain Scanner panel is built).
     */
    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
        this.dac = new DACAlgorithms(logger);
        r3.setLogger(logger); // propagate to R3 Alpha-Beta + Oracle logs
    }

    /**
     * Sets the algorithm version for the AI.
     * Switching to version 3 flushes the transposition table
     * so stale memos from a previous game don't pollute the search.
     * 
     * @param v 1 = R1 Greedy, 2 = R2 D&C, 3 = R3 DP+Backtracking
     */
    public void setVersion(int v) {
        this.version = v;
        if (v == 3)
            r3.clearMemo(); // Flush transposition table on entry to R3
    }

    /** Returns the currently active version. */
    public int getVersion() {
        return version;
    }

    // =========================================================================
    // MAIN ENTRY POINTS (Delegates to version-specific logic)
    // =========================================================================

    /**
     * 
     * 
     * Determines the best move for the CPU.
     * Delegates to R1 (Greedy) or R2 (D&C) based on selected version.
     *
     * @param currentState Current board (true=Yellow/Player, false=Grey/CPU)
     * @return Tile ID of the best move, or -1 if no valid moves
     */
    public int getBestMove(boolean[] currentState) {
        if (version == 1) {
            return getBestMoveR1(currentState);
        } else if (version == 3) {
            return getBestMoveR3(currentState);
        } else {
            return getBestMoveR2(currentState);
        }
    }

    /**
     * Generates a strategic hint for the human player.
     * Delegates to R1 (Greedy) or R2 (D&C) based on selected version.
     *
     * @param currentState Current board state
     * @return Tile ID of the best move for the player
     */
    public int getPlayerHint(boolean[] currentState) {
        if (version == 1) {
            return getPlayerHintR1(currentState);
        } else if (version == 3) {
            return getPlayerHintR3(currentState);
        } else {
            return getPlayerHintR2(currentState);
        }
    }

    // =========================================================================
    // VERSION 3: DYNAMIC PROGRAMMING + BACKTRACKING (R3 — State-Space Search)
    // CPU Move: Alpha-Beta Pruning Minimax (Maneesh) + doMove/undoMove (Suhas)
    // Player Hint: 4x4 Oracle O(1) (Balaji) | Alpha-Beta fallback (5x5, 6x6)
    // Memoization: Zobrist Transposition Table (Ganesh)
    // =========================================================================

    /**
     * R3 CPU Move: Delegates to the Alpha-Beta engine (CPU is minimizing).
     * The R3Algorithms engine uses in-place backtracking (Suhas), Alpha-Beta
     * pruning with D&C move ordering (Maneesh), and Zobrist memoization (Ganesh).
     */
    private int getBestMoveR3(boolean[] currentState) {
        // CPU plays as the minimizer (forPlayer = false)
        return r3.getBestMoveR3(currentState, false);
    }

    /**
     * R3 Player Hint:
     * - 4x4 grid: O(1) Oracle lookup from BFS-precomputed exactSolver[] (Balaji).
     * - 5x5 / 6x6: Falls back to Alpha-Beta (forPlayer = true).
     * If the Oracle thread hasn't finished yet, falls back to Alpha-Beta
     * gracefully.
     */
    private int getPlayerHintR3(boolean[] currentState) {
        return r3.getPlayerHintR3(currentState);
    }

    // =========================================================================
    // VERSION 2: DIVIDE & CONQUER (R2 — Current / Smart)
    // CPU Move: Tournament Selection D&C — O(n)
    // Player Hint: Merge Sort D&C — O(n log n)
    // Scoring: Spatial + Cluster + Threat D&C
    // =========================================================================

    /**
     * R2 CPU Move: Uses Tournament Selection to find the single best move.
     * The tournament evaluates each move using the combined D&C scoring.
     */
    private int getBestMoveR2(boolean[] currentState) {
        List<Integer> availableMoves = new ArrayList<>();
        for (int i = 0; i < totalTiles; i++) {
            if (!rules.isLocked(i))
                availableMoves.add(i);
        }
        if (availableMoves.isEmpty())
            return -1;

        // Tournament Selection D&C: O(n) — finds the champion move
        return dac.tournamentSelection(availableMoves, currentState, graph, rules, false);
    }

    /**
     * R2 Player Hint: Uses Merge Sort to rank ALL moves, then picks the top one.
     * This requires the full sorted ranking (not just the max).
     */
    private int getPlayerHintR2(boolean[] currentState) {
        List<int[]> tileScores = new ArrayList<>();
        for (int i = 0; i < totalTiles; i++) {
            if (rules.isLocked(i))
                continue;
            boolean[] temp = currentState.clone();
            simulateFlip(temp, i);
            double score = evaluateStateCombined(temp, true);
            tileScores.add(new int[] { i, (int) (score * 1000) });
        }
        // Merge Sort D&C: O(n log n) — full ranking
        if (!tileScores.isEmpty()) {
            mergeSort(tileScores, 0, tileScores.size() - 1);
        }
        int topTile = tileScores.isEmpty() ? -1 : tileScores.get(0)[0];
        // ── BRAIN SCANNER ──────────────────────────────────────────────
        logger.accept(String.format(
                "[Merge Sort D&C] Ranked %d moves. Top recommended move: Tile %d",
                tileScores.size(), topTile));
        return topTile;
    }

    /**
     * R2 Combined Evaluation: Weighted sum of 4 scoring components.
     * 
     * <pre>
     * FinalScore = (Strategic * 0.2) + (Spatial * 0.25)
     *         + (Cluster * 0.25) + (Threat * 0.3)
     * </pre>
     */
    private double evaluateStateCombined(boolean[] state, boolean forPlayer) {
        double strategicScore = evaluateStrategic(state, forPlayer);
        double quadrantScore = dac.evaluateQuadrants(state, gridSize, forPlayer);

        double myClusterScore = dac.evaluateClusters(state, gridSize, forPlayer);
        double oppClusterScore = dac.evaluateClusters(state, gridSize, !forPlayer);
        double clusterScore = myClusterScore - (oppClusterScore * 1.5);

        double threatScore = dac.evaluateThreats(state, gridSize, forPlayer);

        return (strategicScore * 0.2) + (quadrantScore * 0.25)
                + (clusterScore * 0.25) + (threatScore * 0.3);
    }

    // =========================================================================
    // VERSION 1: GREEDY (R1 — Baseline for Comparison)
    // CPU Move: Greedy best with 15% blunder factor
    // Player Hint: Greedy best (no blunder)
    // Scoring: Simple tile value counting
    // =========================================================================

    /**
     * R1 CPU Move: Greedy evaluation with 15% random blunder.
     * Evaluates each tile by simple score difference, sorted via Merge Sort.
     * The blunder factor makes the CPU occasionally pick a random move,
     * simulating imperfect play for an easier difficulty.
     */
    private int getBestMoveR1(boolean[] currentState) {
        // 15% blunder factor — occasionally make a random move
        if (new Random().nextDouble() < 0.15) {
            List<Integer> valid = new ArrayList<>();
            for (int i = 0; i < totalTiles; i++) {
                if (!rules.isLocked(i))
                    valid.add(i);
            }
            if (!valid.isEmpty())
                return valid.get(new Random().nextInt(valid.size()));
        }

        List<int[]> tileScores = new ArrayList<>();
        for (int i = 0; i < totalTiles; i++) {
            if (rules.isLocked(i))
                continue;
            boolean[] temp = currentState.clone();
            simulateFlip(temp, i);
            double score = evaluateStateGreedy(temp, false);
            tileScores.add(new int[] { i, (int) (score * 1000) });
        }

        mergeSort(tileScores, 0, tileScores.size() - 1);
        return tileScores.isEmpty() ? -1 : tileScores.get(0)[0];
    }

    /**
     * R1 Player Hint: Same greedy logic but without the blunder factor.
     */
    private int getPlayerHintR1(boolean[] currentState) {
        List<int[]> tileScores = new ArrayList<>();
        for (int i = 0; i < totalTiles; i++) {
            if (rules.isLocked(i))
                continue;
            boolean[] temp = currentState.clone();
            simulateFlip(temp, i);
            double score = evaluateStateGreedy(temp, true);
            tileScores.add(new int[] { i, (int) (score * 1000) });
        }

        mergeSort(tileScores, 0, tileScores.size() - 1);
        return tileScores.isEmpty() ? -1 : tileScores.get(0)[0];
    }

    /**
     * R1 Evaluation: Simple tile value counting.
     * Score = sum(our tile values) - sum(opponent tile values).
     * No spatial awareness, no cluster detection, no threat analysis.
     */
    private double evaluateStateGreedy(boolean[] state, boolean forPlayer) {
        double playerScore = 0, cpuScore = 0;
        for (int i = 0; i < totalTiles; i++) {
            double tileVal = rules.getTileStrategicValue(i);
            if (state[i])
                playerScore += tileVal;
            else
                cpuScore += tileVal;
        }
        return forPlayer ? (playerScore - cpuScore) : (cpuScore - playerScore);
    }

    // =========================================================================
    // SHARED: Strategic Evaluation (Used by R2's combined scoring)
    // =========================================================================

    /**
     * Strategic tile value evaluation.
     * Corners = +25, Edges = +15, Standard = +5, Traps = -5.
     */
    private double evaluateStrategic(boolean[] state, boolean forPlayer) {
        double playerScore = 0, cpuScore = 0;
        for (int i = 0; i < totalTiles; i++) {
            double tileVal = rules.getTileStrategicValue(i);
            if (state[i])
                playerScore += tileVal;
            else
                cpuScore += tileVal;
        }
        return forPlayer ? (playerScore - cpuScore) : (cpuScore - playerScore);
    }

    // =========================================================================
    // SHARED: Simulation Helper
    // =========================================================================

    /** Simulates flipping a tile and its neighbors (respects locks). */
    private void simulateFlip(boolean[] state, int tileId) {
        for (int neighbor : graph.getNeighbors(tileId)) {
            if (!rules.isLocked(neighbor)) {
                state[neighbor] = !state[neighbor];
            }
        }
    }

    // =========================================================================
    // SHARED: Merge Sort D&C (Used by R1 + R2 Hints)
    // =========================================================================

    /**
     * Merge Sort: Divide and Conquer — O(n log n).
     * Divide: Split list into two halves.
     * Conquer: Recursively sort each half.
     * Combine: Merge sorted halves in descending order (best move first).
     */
    private void mergeSort(List<int[]> list, int left, int right) {
        if (left < right) {
            int mid = (left + right) / 2;
            mergeSort(list, left, mid);
            mergeSort(list, mid + 1, right);
            merge(list, left, mid, right);
        }
    }

    /** Merge step: Combine two sorted sublists in descending order. */
    private void merge(List<int[]> list, int left, int mid, int right) {
        List<int[]> temp = new ArrayList<>();
        int i = left, j = mid + 1;
        while (i <= mid && j <= right) {
            if (list.get(i)[1] >= list.get(j)[1])
                temp.add(list.get(i++));
            else
                temp.add(list.get(j++));
        }
        while (i <= mid)
            temp.add(list.get(i++));
        while (j <= right)
            temp.add(list.get(j++));
        for (int k = 0; k < temp.size(); k++)
            list.set(left + k, temp.get(k));
    }
}
