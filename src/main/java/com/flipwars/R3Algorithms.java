package com.flipwars;

import java.util.*;
import java.util.function.Consumer;

public class R3Algorithms {

    // =========================================================================
    // FIELDS
    // =========================================================================

    private final int gridSize;
    private final int totalTiles;
    private final Graph graph;
    private final Rules rules;
    private DACAlgorithms dac;

    // Brain Scanner logger — silent by default, wired in by Engine.setLogger()
    private Consumer<String> logger = msg -> {
    };

    // Allows Engine to inject the Brain Scanner logger after construction.
    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
        this.dac = new DACAlgorithms(logger);
    }

    // ---- MANEESH: Dynamic depth limit based on grid size --------------------
    // Search depth scales inversely with branching factor to keep UI responsive.
    private final int MAX_DEPTH;

    // ---- GANESH: Transposition Table (Memoization) -------------------------
    // Maps Zobrist board-hash → heuristic score.
    // Eliminates re-evaluation of repeated board states (overlapping subproblems).
    private final HashMap<Long, Double> memoTable = new HashMap<>();

    // A separate table keyed by (hash, depth) to store upper/lower bounds.
    // Format: hash → double[]{score, depth, nodeType}
    // nodeType: 0=exact, 1=lower-bound (alpha), 2=upper-bound (beta)
    private final HashMap<Long, double[]> ttTable = new HashMap<>();

    // GANESH — Zobrist random keys.
    // zobristTile[i] XOR-ed when tile i is true (player-owned).
    // zobristLock[i] XOR-ed when tile i is locked (Tabu).
    // Separate arrays ensure Board(same tiles, different locks) ≠ same hash.
    private final long[] zobristTile;
    private final long[] zobristLock;

    // ---- BALAJI: Bottom-Up Bitmask DP Oracle (4x4 only) --------------------
    // exactSolver[state] = minimum number of moves to reach a "near-win" state.
    // Indexed by a 16-bit integer representing the 4x4 board.
    // Only valid when gridSize == 4 and oracleReady == true.
    private final int[] exactSolver = new int[65536];

    // Set to true once the background BFS precomputation finishes.
    // Checked before every Oracle lookup; falls back to Alpha-Beta if false.
    private volatile boolean oracleReady = false;

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    public R3Algorithms(int gridSize, Graph graph, Rules rules) {
        this.gridSize = gridSize;
        this.totalTiles = gridSize * gridSize;
        this.graph = graph;
        this.rules = rules;
        this.dac = new DACAlgorithms();

        // ---- MANEESH: Set dynamic depth limit --------------------------------
        // 4x4: branching ≤ 16 → 16^6 ≈ 16M, but α-β prunes to ~4K → depth 6 is safe
        // 5x5: branching ≤ 25 → 25^4 ≈ 390K, α-β to ~19K → depth 4
        // 6x6: branching ≤ 36 → 36^3 ≈ 46K, α-β to ~2K → depth 3
        if (gridSize == 4)
            this.MAX_DEPTH = 6;
        else if (gridSize == 5)
            this.MAX_DEPTH = 4;
        else
            this.MAX_DEPTH = 3;

        // ---- GANESH: Initialise Zobrist random keys -------------------------
        // Two independent arrays so tile-state and lock-state hash independently.
        // Fixed seed 0xDAAF17L = "DAA Flip Wars" mnemonic → reproducible Zobrist
        // tables.
        Random rng = new Random(0xDAAF17L);
        zobristTile = new long[totalTiles];
        zobristLock = new long[totalTiles];
        for (int i = 0; i < totalTiles; i++) {
            zobristTile[i] = rng.nextLong();
            zobristLock[i] = rng.nextLong();
        }

        // ---- BALAJI: Launch Oracle BFS on a background thread ---------------
        // The UI thread is never blocked; 'oracleReady' gates the lookup.
        if (gridSize == 4) {
            Thread oracleThread = new Thread(() -> {
                precompute4x4Oracle();
                oracleReady = true;
                System.out.println("[R3] 4x4 Oracle ready — 65536 states solved.");
            }, "Oracle-BFS-Thread");
            oracleThread.setDaemon(true); // Won't prevent JVM exit
            oracleThread.start();
        }
    }

    // =========================================================================
    // PUBLIC ENTRY POINTS (called by Engine)
    // =========================================================================

    public int getBestMoveR3(boolean[] board, boolean forPlayer) {
        clearMemo(); // Fresh transposition table each turn

        List<Integer> moves = getAvailableMoves();
        if (moves.isEmpty())
            return -1;

        moves = orderMoves(moves, board, forPlayer);

        // ── BRAIN SCANNER: Pseudo-Grid board evaluation map ──────────────
        printBoardGrid(board);

        // ── BRAIN SCANNER: Alpha-Beta header ────────────────────────────
        logger.accept(String.format(
                "[Alpha-Beta] Search started. Depth=%d | Moves to explore: %d",
                MAX_DEPTH, moves.size()));

        int bestMove = moves.get(0);
        double bestVal = Double.NEGATIVE_INFINITY;
        double alpha = Double.NEGATIVE_INFINITY;
        double beta = Double.POSITIVE_INFINITY;

        for (int move : moves) {
            doMove(board, move);
            double val = alphaBeta(board, MAX_DEPTH - 1, alpha, beta, !forPlayer);
            undoMove(board, move);

            if (val > bestVal) {
                bestVal = val;
                bestMove = move;
            }
            alpha = Math.max(alpha, bestVal);
        }
        logger.accept(String.format(
                "[Alpha-Beta] Final Champion: Tile %d  (score=%.2f)", bestMove, bestVal));
        return bestMove;
    }

    public int getPlayerHintR3(boolean[] board) {
        if (gridSize == 4 && oracleReady) {
            int state = boardToInt(board);
            int hint = getExactWinMove(state);
            // ── BRAIN SCANNER ─────────────────────────────────────────
            logger.accept("[Bitmask DP Oracle] 65536-state BFS table lookup.");
            logger.accept(String.format(
                    "[Oracle] Best hint → Tile %d  (O(1) lookup, precomputed table)", hint));
            return hint;
        }
        logger.accept("[Alpha-Beta] Oracle not ready — falling back to Alpha-Beta search.");
        return getBestMoveR3(board, true);
    }

    public void clearMemo() {
        memoTable.clear();
        ttTable.clear();
    }

    // =========================================================================
    // ALGORITHM 1 — SUHAS: PURE BACKTRACKING
    // In-place doMove / undoMove — O(1) extra space per depth level.
    // No board.clone() ever used inside the search tree.
    // =========================================================================

    public void doMove(boolean[] board, int move) {
        // Flip the tile and every orthogonal neighbor (including self via graph)
        for (int neighbor : graph.getNeighbors(move)) {
            if (!rules.isLocked(neighbor)) {
                board[neighbor] = !board[neighbor]; // XOR flip — its own inverse
            }
        }
    }

    public void undoMove(boolean[] board, int move) {
        // Identical to doMove — XOR is self-inverse.
        // Calling it again on the same tile undoes the previous doMove exactly.
        doMove(board, move);
    }

    // =========================================================================
    // ALGORITHM 2 — MANEESH: ALPHA-BETA PRUNING / BRANCH & BOUND
    // Recursive Minimax with pruning and D&C move ordering.
    // =========================================================================

    private double alphaBeta(boolean[] board, int depth,
            double alpha, double beta, boolean isMaximizing) {

        // ---- GANESH: Transposition Table lookup (Top-Down Memoization) ------
        // Compute Zobrist hash for the current board + lock state.
        long hash = getBoardHash(board);

        // Check if we have an exact result for this state at sufficient depth
        if (ttTable.containsKey(hash)) {
            double[] entry = ttTable.get(hash);
            double storedScore = entry[0];
            double storedDepth = entry[1];
            double nodeType = entry[2];

            if (storedDepth >= depth) {
                if (nodeType == 0) {
                    // ── GANESH: Announce exact cache hit to Brain Scanner ──
                    logger.accept(String.format(
                            "[DP Cache Hit] Zobrist hash 0x%X → score=%.2f (depth=%d, EXACT)",
                            hash & 0xFFFFFFFFL, storedScore, (int) storedDepth));
                    return storedScore; // Exact hit
                }
                if (nodeType == 1)
                    alpha = Math.max(alpha, storedScore); // Lower bound
                if (nodeType == 2)
                    beta = Math.min(beta, storedScore); // Upper bound
                if (beta <= alpha)
                    return storedScore; // Pruned via TT
            }
        }

        // ---- Base case: depth exhausted → evaluate the leaf state -----------
        List<Integer> moves = getAvailableMoves();
        if (depth == 0 || moves.isEmpty()) {
            double score = evaluateLeaf(board, isMaximizing);
            // Cache leaf as exact result
            ttTable.put(hash, new double[] { score, depth, 0 });
            return score;
        }

        // ---- MANEESH: Order moves via R2 heuristic for better cutoffs -------
        moves = orderMoves(moves, board, isMaximizing);

        double originalAlpha = alpha;
        double bestScore;

        if (isMaximizing) {
            // Maximizing player (human): pick the move with the highest score
            bestScore = Double.NEGATIVE_INFINITY;
            for (int move : moves) {
                doMove(board, move); // SUHAS: in-place
                double val = alphaBeta(board, depth - 1, alpha, beta, false);
                undoMove(board, move); // SUHAS: restore

                bestScore = Math.max(bestScore, val);
                alpha = Math.max(alpha, bestScore);

                // ---- Alpha-Beta cut-off (β-cut): minimizer won't choose this branch
                if (beta <= alpha)
                    break; // ← The actual "prune" moment
            }
        } else {
            // Minimizing player (CPU): pick the move with the lowest score
            bestScore = Double.POSITIVE_INFINITY;
            for (int move : moves) {
                doMove(board, move); // SUHAS: in-place
                double val = alphaBeta(board, depth - 1, alpha, beta, true);
                undoMove(board, move); // SUHAS: restore

                bestScore = Math.min(bestScore, val);
                beta = Math.min(beta, bestScore);

                // ---- Alpha-Beta cut-off (α-cut): maximizer won't choose this branch
                if (beta <= alpha)
                    break; // ← The actual "prune" moment
            }
        }

        // ---- GANESH: Store result in Transposition Table --------------------
        double nodeType;
        if (bestScore <= originalAlpha)
            nodeType = 2; // Upper bound (β-cutoff)
        else if (bestScore >= beta)
            nodeType = 1; // Lower bound (α-cutoff)
        else
            nodeType = 0; // Exact
        ttTable.put(hash, new double[] { bestScore, depth, nodeType });

        return bestScore;
    }

    // =========================================================================
    // ALGORITHM 3 — GANESH: TOP-DOWN MEMOIZATION / TRANSPOSITION TABLE
    // Zobrist Hashing: converts board + lock state to a 64-bit integer.
    // =========================================================================

    private long getBoardHash(boolean[] board) {
        long hash = 0L;
        for (int i = 0; i < totalTiles; i++) {
            if (board[i]) {
                hash ^= zobristTile[i]; // XOR in key for player-owned tile
            }
            if (rules.isLocked(i)) {
                hash ^= zobristLock[i]; // XOR in key for locked tile (Tabu state)
            }
        }
        return hash;
    }

    // =========================================================================
    // ALGORITHM 4 — BALAJI: BOTTOM-UP BITMASK DP / END-GAME ORACLE (4x4)
    // Precomputed BFS over all 65,536 possible 4x4 board states.
    // =========================================================================

    private void precompute4x4Oracle() {
        // Precompute the flip mask for each tile on a 4x4 board.
        // flipMask[i] is a 16-bit integer where bit j is set if flipping tile i also
        // flips tile j.
        int[] flipMask = new int[16];
        for (int i = 0; i < 16; i++) {
            int mask = (1 << i); // always flip self
            int row = i / 4, col = i % 4;
            if (row > 0)
                mask |= (1 << (i - 4)); // up
            if (row < 3)
                mask |= (1 << (i + 4)); // down
            if (col > 0)
                mask |= (1 << (i - 1)); // left
            if (col < 3)
                mask |= (1 << (i + 1)); // right
            flipMask[i] = mask;
        }

        // Initialize exactSolver: -1 = unvisited, 0 = goal state
        Arrays.fill(exactSolver, -1);

        // BFS queue — holds states by their bit-integer representation
        Queue<Integer> bfsQueue = new ArrayDeque<>();

        // Seed winning states: all-CPU (0) and all-Player (0xFFFF)
        exactSolver[0] = 0;
        exactSolver[0xFFFF] = 0;
        bfsQueue.add(0);
        bfsQueue.add(0xFFFF);

        // Bottom-Up BFS: expand from goal states outward
        while (!bfsQueue.isEmpty()) {
            int state = bfsQueue.poll();
            int dist = exactSolver[state];

            // Try every possible tile flip that could have LED to this state
            for (int tile = 0; tile < 16; tile++) {
                // Applying the flip mask gives the predecessor state
                // (since flip is its own inverse: predecessor XOR mask = state)
                int predecessor = state ^ flipMask[tile];

                if (exactSolver[predecessor] == -1) {
                    // Unvisited: this predecessor needs one more move than 'state'
                    exactSolver[predecessor] = dist + 1;
                    bfsQueue.add(predecessor);
                }
            }
        }

        // Any state still -1 is unreachable (shouldn't happen on a connected 4x4 graph)
        // Set them to a large sentinel so they're never picked as optimal.
        for (int s = 0; s < 65536; s++) {
            if (exactSolver[s] == -1)
                exactSolver[s] = Integer.MAX_VALUE;
        }
    }

    private int getExactWinMove(int boardState) {
        int bestMove = -1;
        int bestDist = Integer.MAX_VALUE;

        // Precompute flip mask inline for the current board (reuse 4x4 logic)
        for (int tile = 0; tile < 16; tile++) {
            if (rules.isLocked(tile))
                continue; // Respect Tabu locks

            // Compute the resulting state after flipping this tile
            int row = tile / 4, col = tile % 4;
            int mask = (1 << tile);
            if (row > 0)
                mask |= (1 << (tile - 4));
            if (row < 3)
                mask |= (1 << (tile + 4));
            if (col > 0)
                mask |= (1 << (tile - 1));
            if (col < 3)
                mask |= (1 << (tile + 1));

            int nextState = boardState ^ mask;
            int dist = exactSolver[nextState];

            if (dist < bestDist) {
                bestDist = dist;
                bestMove = tile;
            }
        }

        // If oracle gives no improvement (all locked), fall back
        return (bestMove == -1) ? getBestMoveR3(intToBoard(boardState), true) : bestMove;
    }

    // =========================================================================
    // SHARED HELPERS
    // =========================================================================

    private void printBoardGrid(boolean[] board) {
        logger.accept("[Board Evaluation Grid] ─────────────────────");
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < totalTiles; i++) {
            // Determine cell label
            String cell;
            if (graph.isBlackHole(i)) {
                cell = "[  VOID ]";
            } else if (rules.isLocked(i)) {
                cell = "[  LOCK ]";
            } else {
                double val = rules.getTileStrategicValue(i);
                // Show tile ownership sign: + if player owns it, - if CPU owns
                double signed = board[i] ? val : -val;
                cell = String.format("[%+6.1f ]", signed);
            }
            row.append(cell).append(" ");
            // Line break at end of each grid row
            if ((i + 1) % gridSize == 0) {
                logger.accept(row.toString().trim());
                row.setLength(0);
            }
        }
        logger.accept("────────────────────────────────────────────");
    }

    // Collects all tiles that are NOT currently locked (valid moves).
    // Respects the Tabu Search lock mechanism from Rules.
    private List<Integer> getAvailableMoves() {
        List<Integer> moves = new ArrayList<>();
        for (int i = 0; i < totalTiles; i++) {
            if (!rules.isLocked(i))
                moves.add(i);
        }
        return moves;
    }

    // MANEESH — Move Ordering via R2 D&C Tournament Heuristic.
    // Sorts candidate moves so the most promising ones are searched first.
    // Best moves searched first → tighter alpha bound early → more cutoffs.
    // Complexity: O(n log n) for sort, O(n) for scoring — done once per node.
    private List<Integer> orderMoves(List<Integer> moves, boolean[] board, boolean forPlayer) {
        // Score each move with a quick heuristic (leaf evaluation after one step)
        List<int[]> scored = new ArrayList<>();
        for (int move : moves) {
            // Shallow clone ONLY for scoring/ordering — NOT for the search itself.
            // The actual search uses doMove/undoMove on the shared board (Suhas's
            // contribution).
            boolean[] temp = board.clone();
            applyFlip(temp, move);
            double score = evaluateLeaf(temp, forPlayer);
            scored.add(new int[] { move, (int) (score * 1000) });
        }

        // Sort descending (highest score = most promising branch first)
        scored.sort((a, b) -> Integer.compare(b[1], a[1]));

        List<Integer> ordered = new ArrayList<>();
        for (int[] entry : scored)
            ordered.add(entry[0]);
        return ordered;
    }

    // Leaf node evaluation — combined R2 weighted scoring.
    // Score = (strategic * 0.20) + (quadrant * 0.25) + (cluster * 0.25) + (threat *
    // 0.30)
    // Positive score = good for the player currently being evaluated.
    private double evaluateLeaf(boolean[] board, boolean forPlayer) {
        // Strategic tile values (corners, edges, traps)
        double strategic = 0;
        for (int i = 0; i < totalTiles; i++) {
            double v = rules.getTileStrategicValue(i);
            strategic += board[i] ? v : -v;
        }
        if (!forPlayer)
            strategic = -strategic;

        // Spatial D&C (quadrant control)
        double quadrant = dac.evaluateQuadrants(board, gridSize, forPlayer);

        // Structural D&C (cluster strength)
        double cluster = dac.evaluateClusters(board, gridSize, forPlayer)
                - dac.evaluateClusters(board, gridSize, !forPlayer) * 1.5;

        // Threat Detection D&C (exposure analysis)
        double threat = dac.evaluateThreats(board, gridSize, forPlayer);

        return (strategic * 0.20) + (quadrant * 0.25) + (cluster * 0.25) + (threat * 0.30);
    }

    // Applies a flip to a board copy (used ONLY for move ordering scoring).
    // This is NOT used inside the search tree — doMove/undoMove handles that.
    private void applyFlip(boolean[] board, int move) {
        for (int neighbor : graph.getNeighbors(move)) {
            if (!rules.isLocked(neighbor)) {
                board[neighbor] = !board[neighbor];
            }
        }
    }

    // BALAJI — Converts a boolean[] board to a 16-bit integer (4x4 only).
    // Bit i is set (1) if board[i] is true (player-owned).
    private int boardToInt(boolean[] board) {
        int state = 0;
        for (int i = 0; i < Math.min(board.length, 16); i++) {
            if (board[i])
                state |= (1 << i);
        }
        return state;
    }

    // BALAJI — Converts a 16-bit integer back to a boolean[] board (4x4 only).
    // Used by the Oracle fallback path.
    private boolean[] intToBoard(int state) {
        boolean[] board = new boolean[16];
        for (int i = 0; i < 16; i++) {
            board[i] = ((state >> i) & 1) == 1;
        }
        return board;
    }

    // =========================================================================
    // DIAGNOSTICS
    // =========================================================================

    // Returns true if the 4x4 Oracle BFS is complete and ready to use.
    public boolean isOracleReady() {
        return oracleReady;
    }

    // Returns the current size of the Transposition Table (for viva diagnostics).
    public int getMemoTableSize() {
        return ttTable.size();
    }

    // Returns the dynamic depth limit (for viva diagnostics).
    public int getMaxDepth() {
        return MAX_DEPTH;
    }
}
