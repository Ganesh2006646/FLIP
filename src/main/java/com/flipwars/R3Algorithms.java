package com.flipwars;

import java.util.*;
import java.util.function.Consumer;

/**
 * ============================================================================
 * R3Algorithms — Version 3 AI Engine: Dynamic Programming + Backtracking
 * ============================================================================
 *
 * <p>
 * This class forms the "State-Space Search Engine" for Review 3 of Flip Wars.
 * It integrates FOUR distinct algorithms that work together inside a single
 * recursive Minimax tree:
 * </p>
 *
 * <ol>
 * <li><b>SUHAS —</b> Pure Backtracking: in-place doMove / undoMove (O(1)
 * space)</li>
 * <li><b>MANEESH —</b> Alpha-Beta Pruning Minimax with D&C move ordering</li>
 * <li><b>GANESH —</b> Transposition Table (Top-Down Memoization, Zobrist
 * Hash)</li>
 * <li><b>BALAJI —</b> Bottom-Up Bitmask DP / End-Game Oracle (4x4: 65536
 * states)</li>
 * </ol>
 *
 * <p>
 * Algorithms 1–3 collaborate inside {@link #alphaBeta}. Algorithm 4 runs
 * independently as a precomputed lookup table and is used by
 * {@link #getPlayerHintR3} as an O(1) oracle for 4x4 boards.
 * </p>
 */
public class R3Algorithms {

    // =========================================================================
    // FIELDS
    // =========================================================================

    private final int gridSize;
    private final int totalTiles;
    private final Graph graph;
    private final Rules rules;
    private final DACAlgorithms dac;

    // ---- MANEESH: Dynamic depth limit based on grid size --------------------
    /**
     * Search depth scales inversely with branching factor to keep UI responsive.
     */
    private final int MAX_DEPTH;

    // ---- GANESH: Transposition Table (Memoization) -------------------------
    /**
     * Maps Zobrist board-hash → heuristic score.
     * Eliminates re-evaluation of repeated board states (overlapping subproblems).
     */
    private final HashMap<Long, Double> memoTable = new HashMap<>();

    /**
     * A separate table keyed by (hash, depth) to store upper/lower bounds.
     * Format: hash → double[]{score, depth, nodeType}
     * nodeType: 0=exact, 1=lower-bound (alpha), 2=upper-bound (beta)
     */
    private final HashMap<Long, double[]> ttTable = new HashMap<>();

    /**
     * GANESH — Zobrist random keys.
     * zobristTile[i] XOR-ed when tile i is true (player-owned).
     * zobristLock[i] XOR-ed when tile i is locked (Tabu).
     * Separate arrays ensure Board(same tiles, different locks) ≠ same hash.
     */
    private final long[] zobristTile;
    private final long[] zobristLock;

    // ---- BALAJI: Bottom-Up Bitmask DP Oracle (4x4 only) --------------------
    /**
     * exactSolver[state] = minimum number of moves to reach a "near-win" state.
     * Indexed by a 16-bit integer representing the 4x4 board.
     * Only valid when gridSize == 4 and oracleReady == true.
     */
    private final int[] exactSolver = new int[65536];

    /**
     * Set to true once the background BFS precomputation finishes.
     * Checked before every Oracle lookup; falls back to Alpha-Beta if false.
     */
    private volatile boolean oracleReady = false;

    // ---- BRAIN SCANNER: Thread-safe logging ---------------------------------
    /**
     * Logger callback — receives algorithm decision strings.
     * Defaults to a no-op; set via setLogger() to wire up the Brain Scanner UI.
     * SwingUtilities.invokeLater() is called by the UI side (Main.logBrain),
     * so this Consumer just fires the message without worrying about threads.
     */
    private Consumer<String> logger = msg -> {
    }; // no-op default

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    /**
     * Constructs the R3 engine and kicks off Oracle precomputation in the
     * background.
     *
     * @param gridSize Size of the grid (4, 5, or 6)
     * @param graph    Pre-built adjacency graph
     * @param rules    Rules engine (strategic values + Tabu locks)
     */
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

    /**
     * Sets the Brain Scanner logger. Called by Engine after construction.
     * Messages are emitted at: TT hits, α-β prune moments, Oracle lookups.
     */
    public void setLogger(Consumer<String> logger) {
        this.logger = (logger != null) ? logger : msg -> {
        };
    }

    /** Emits a log message via the Brain Scanner callback. */
    private void log(String msg) {
        logger.accept(msg);
    }

    // =========================================================================
    // PUBLIC ENTRY POINTS (called by Engine)
    // =========================================================================

    /**
     * CPU's best move for version 3.
     * Runs Alpha-Beta Minimax (isMaximizing = false → CPU minimizes player score).
     *
     * @param board     Current board state
     * @param forPlayer false → CPU is playing, true → player is playing
     * @return Tile index of the best move, or -1 if none available
     */
    public int getBestMoveR3(boolean[] board, boolean forPlayer) {
        clearMemo(); // Fresh transposition table each turn

        List<Integer> moves = getAvailableMoves();
        if (moves.isEmpty())
            return -1;

        // ---- MANEESH: Order moves best-first before root expansion ----------
        // This ensures the first branch gives a tight alpha bound,
        // maximising cutoffs for all subsequent siblings.
        moves = orderMoves(moves, board, forPlayer);

        int bestMove = moves.get(0);
        double bestVal = Double.NEGATIVE_INFINITY;
        double alpha = Double.NEGATIVE_INFINITY;
        double beta = Double.POSITIVE_INFINITY;

        for (int move : moves) {
            // ---- SUHAS: doMove — flip in-place (O(1) extra space) -----------
            doMove(board, move);
            double val = alphaBeta(board, MAX_DEPTH - 1, alpha, beta, !forPlayer);
            // ---- SUHAS: undoMove — exact reverse of doMove ------------------
            undoMove(board, move);

            if (val > bestVal) {
                bestVal = val;
                bestMove = move;
            }
            alpha = Math.max(alpha, bestVal);
        }
        return bestMove;
    }

    /**
     * Player hint for version 3.
     * <ul>
     * <li>4x4 + Oracle ready → O(1) lookup from exactSolver[]</li>
     * <li>Otherwise → Alpha-Beta search (forPlayer = true)</li>
     * </ul>
     *
     * @param board Current board state
     * @return Tile index of the recommended hint move
     */
    public int getPlayerHintR3(boolean[] board) {
        // ---- BALAJI: Oracle path (4x4 only) ---------------------------------
        if (gridSize == 4 && oracleReady) {
            int state = boardToInt(board);
            int hint = getExactWinMove(state);
            log("[ORACLE] 4x4 exact lookup → tile " + hint);
            return hint;
        }
        // Fallback: Alpha-Beta for 5x5, 6x6, or while Oracle is still computing.
        return getBestMoveR3(board, true);
    }

    /**
     * Clears the transposition table.
     * Called at the start of every move and when the engine version changes.
     */
    public void clearMemo() {
        memoTable.clear();
        ttTable.clear();
    }

    // =========================================================================
    // ALGORITHM 1 — SUHAS: PURE BACKTRACKING
    // In-place doMove / undoMove — O(1) extra space per depth level.
    // No board.clone() ever used inside the search tree.
    // =========================================================================

    /**
     * SUHAS — doMove: Applies a move to the shared board array in-place.
     * <p>
     * Mechanism: A tile-flip toggles the tile and all its orthogonal neighbors.
     * XOR/NOT is its own inverse, so doMove == undoMove on the same tile.
     * </p>
     * <p>
     * Complexity: O(k) where k ≤ 5 (the flip group size), effectively O(1).
     * </p>
     * <p>
     * Space: O(1) — no new arrays allocated.
     * </p>
     *
     * @param board Shared boolean array (mutated in-place)
     * @param move  Tile index to flip
     */
    public void doMove(boolean[] board, int move) {
        // Flip the tile and every orthogonal neighbor (including self via graph)
        for (int neighbor : graph.getNeighbors(move)) {
            if (!rules.isLocked(neighbor)) {
                board[neighbor] = !board[neighbor]; // XOR flip — its own inverse
            }
        }
    }

    /**
     * SUHAS — undoMove: Reverses a move applied by doMove.
     * <p>
     * Because flipping is an involuntary operation (XOR), applying the exact
     * same flip a second time restores the original state perfectly.
     * This is the mathematical proof that no board copy is ever needed.
     * </p>
     *
     * @param board Shared boolean array (mutated in-place to restore prior state)
     * @param move  Tile index that was previously flipped
     */
    public void undoMove(boolean[] board, int move) {
        // Identical to doMove — XOR is self-inverse.
        // Calling it again on the same tile undoes the previous doMove exactly.
        doMove(board, move);
    }

    // =========================================================================
    // ALGORITHM 2 — MANEESH: ALPHA-BETA PRUNING / BRANCH & BOUND
    // Recursive Minimax with pruning and D&C move ordering.
    // =========================================================================

    /**
     * MANEESH — Alpha-Beta Pruning Minimax.
     *
     * <p>
     * <b>Minimax logic:</b> The maximizing player (human/player) wants the
     * highest scoring state. The minimizing player (CPU) wants the lowest.
     * The search explores the game tree to depth {@link #MAX_DEPTH}.
     * </p>
     *
     * <p>
     * <b>Alpha-Beta pruning:</b>
     * <ul>
     * <li>alpha = best score the maximizer can GUARANTEE so far</li>
     * <li>beta = best score the minimizer can GUARANTEE so far</li>
     * <li>If beta ≤ alpha: the current branch will never be chosen → prune
     * (cut-off).</li>
     * </ul>
     * This eliminates up to √(branching^depth) nodes from the search tree.
     * </p>
     *
     * <p>
     * <b>Move ordering (D&C integration):</b> Candidate moves are sorted
     * using the R2 heuristic before expansion (see {@link #orderMoves}).
     * Best moves searched first → tighter alpha bound early → more cutoffs.
     * </p>
     *
     * <p>
     * <b>Transposition Table integration (GANESH):</b> The board hash is
     * checked before recursing to skip already-evaluated states.
     * </p>
     *
     * @param board        Shared board state (mutated in-place via doMove/undoMove)
     * @param depth        Remaining depth; 0 triggers leaf evaluation
     * @param alpha        Best score maximizer can guarantee (initially -∞)
     * @param beta         Best score minimizer can guarantee (initially +∞)
     * @param isMaximizing true if it's the player's turn, false if CPU's turn
     * @return Heuristic evaluation score from this node's perspective
     */
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
                // GANESH: Transposition Table HIT — return cached result instantly
                log("  [TT HIT] d=" + depth + " score=" + String.format("%.1f", storedScore));
                if (nodeType == 0)
                    return storedScore;
                if (nodeType == 1)
                    alpha = Math.max(alpha, storedScore);
                if (nodeType == 2)
                    beta = Math.min(beta, storedScore);
                if (beta <= alpha)
                    return storedScore;
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
                if (beta <= alpha) {
                    // MANEESH: α-β PRUNE (β-cut) — minimizer won't pick this
                    log("  [α-β CUT] β=" + String.format("%.1f", beta) + " ≤ α=" + String.format("%.1f", alpha));
                    break;
                }
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
                if (beta <= alpha) {
                    // MANEESH: α-β PRUNE (α-cut) — maximizer won't pick this
                    log("  [α-β CUT] β=" + String.format("%.1f", beta) + " ≤ α=" + String.format("%.1f", alpha));
                    break;
                }
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

    /**
     * GANESH — Zobrist Board Hash.
     *
     * <p>
     * <b>Algorithm:</b>
     * Start with hash = 0. For each tile that is true (player-owned), XOR in
     * a unique pre-generated random 64-bit integer for that tile.
     * Additionally, for each <em>locked</em> tile, XOR in a second independent
     * random key to encode the Tabu Search state.
     * </p>
     *
     * <p>
     * <b>Why include lock state?</b><br>
     * Board A (tiles identical to B, but tile 5 locked) is a <em>different</em>
     * game state — legal moves differ, so re-using B's cached score for A
     * would be incorrect. Including locks prevents false cache collisions.
     * </p>
     *
     * <p>
     * <b>Complexity:</b> O(n) where n = totalTiles.
     * <b>Collision probability:</b> ≈ 1 / 2^64 (negligible for a game).
     * </p>
     *
     * @param board Current board state (true = player tile)
     * @return 64-bit Zobrist hash encoding tile ownership + lock status
     */
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

    /**
     * BALAJI — 4x4 End-Game Oracle Precomputation (Bottom-Up DP).
     *
     * <p>
     * <b>State space:</b> A 4x4 board has 16 tiles. Each tile is true/false.
     * Total states = 2^16 = 65,536. These fit in a single {@code int[65536]}.
     * </p>
     *
     * <p>
     * <b>Algorithm (Bottom-Up BFS from winning states):</b>
     * <ol>
     * <li><b>Base:</b> States where ALL tiles are one color are "distance 0"
     * (already won).
     * We treat state {@code 0x0000} (all CPU) and {@code 0xFFFF} (all Player) as
     * goal states.</li>
     * <li><b>Level k:</b> For each state at distance k, simulate all possible
     * flips.
     * Any unvisited resulting state has distance k+1.</li>
     * <li><b>Result:</b> exactSolver[s] = fewest moves to reach a goal from state
     * s.</li>
     * </ol>
     * </p>
     *
     * <p>
     * <b>Complexity:</b> O(65536 × 16) = O(1M) — runs in &lt;50ms, safe for
     * background thread.
     * </p>
     * <p>
     * <b>Space:</b> O(65536) for exactSolver + O(65536) for BFS queue = O(1)
     * relative to game.
     * </p>
     *
     * <p>
     * Since this uses a fixed 4x4 connectivity pattern, we compute neighbor masks
     * directly from tile indices without needing the Graph object for the BFS.
     * </p>
     */
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

    /**
     * BALAJI — Oracle Hint Lookup: O(1) after precomputation.
     *
     * <p>
     * Tries every available (unlocked) tile, simulates the flip using XOR on
     * the bitmask representation, and returns the tile whose resulting state has
     * the smallest {@code exactSolver[]} value — i.e., the move that puts the
     * board closest to a winning state.
     * </p>
     *
     * @param boardState 16-bit integer representation of current board
     * @return Tile index of the optimal move (O(1) lookup)
     */
    private int getExactWinMove(int boardState) {
        int bestMove = -1;
        int bestDist = Integer.MAX_VALUE;

        // Precompute flip mask inline for the current board (reuse 4x4 logic)
        for (int tile = 0; tile < 16; tile++) {
            if (rules.isLocked(tile) || rules.isDeadTile(tile))
                continue; // Respect Tabu + Black Holes

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

    /**
     * Collects all tiles that are NOT currently locked (valid moves).
     * Respects the Tabu Search lock mechanism from Rules.
     */
    private List<Integer> getAvailableMoves() {
        List<Integer> moves = new ArrayList<>();
        for (int i = 0; i < totalTiles; i++) {
            // Skip both Tabu-locked tiles AND permanent Black Hole dead tiles
            if (!rules.isLocked(i) && !rules.isDeadTile(i))
                moves.add(i);
        }
        return moves;
    }

    /**
     * MANEESH — Move Ordering via R2 D&C Tournament Heuristic.
     *
     * <p>
     * Sorts candidate moves so the most promising ones are searched first.
     * This is the "Branch & Bound" component: by exploring high-value branches
     * early, we get tight alpha/beta bounds that prune more subsequent branches.
     * </p>
     *
     * <p>
     * Uses R2's {@code tournamentSelection} logic to score each move,
     * then sorts in descending order (best score first).
     * </p>
     *
     * <p>
     * Complexity: O(n log n) for sort, O(n) for scoring — done once per node.
     * </p>
     *
     * @param moves     Available move indices
     * @param board     Current board state (NOT modified — uses temp clone for
     *                  scoring only)
     * @param forPlayer true = order for player's perspective, false = CPU
     * @return Reordered move list, best move first
     */
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

    /**
     * Leaf node evaluation — combined R2 weighted scoring.
     * This is the heuristic at depth-0 nodes:
     * 
     * <pre>
     * Score = (strategic * 0.20) + (quadrant * 0.25) + (cluster * 0.25) + (threat * 0.30)
     * </pre>
     * 
     * Positive score = good for the player currently being evaluated.
     *
     * @param board     Board to evaluate
     * @param forPlayer true → positive score means player is winning
     * @return Combined heuristic evaluation score
     */
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

    /**
     * Applies a flip to a board copy (used ONLY for move ordering scoring).
     * This is NOT used inside the search tree — doMove/undoMove handles that.
     */
    private void applyFlip(boolean[] board, int move) {
        for (int neighbor : graph.getNeighbors(move)) {
            if (!rules.isLocked(neighbor)) {
                board[neighbor] = !board[neighbor];
            }
        }
    }

    /**
     * BALAJI — Converts a boolean[] board to a 16-bit integer (4x4 only).
     * Bit i is set (1) if board[i] is true (player-owned).
     *
     * @param board Boolean board array
     * @return 16-bit integer representation
     */
    private int boardToInt(boolean[] board) {
        int state = 0;
        for (int i = 0; i < Math.min(board.length, 16); i++) {
            if (board[i])
                state |= (1 << i);
        }
        return state;
    }

    /**
     * BALAJI — Converts a 16-bit integer back to a boolean[] board (4x4 only).
     * Used by the Oracle fallback path.
     *
     * @param state 16-bit integer
     * @return Boolean board array
     */
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

    /** Returns true if the 4x4 Oracle BFS is complete and ready to use. */
    public boolean isOracleReady() {
        return oracleReady;
    }

    /**
     * Returns the current size of the Transposition Table (for viva diagnostics).
     */
    public int getMemoTableSize() {
        return ttTable.size();
    }

    /** Returns the dynamic depth limit (for viva diagnostics). */
    public int getMaxDepth() {
        return MAX_DEPTH;
    }
}
