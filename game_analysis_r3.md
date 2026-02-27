# Flip Wars — Review 3 Analysis: Dynamic Programming + Backtracking

> **Team-13 · Design & Analysis of Algorithms · Review 3**

---

## 1. Overview — What Changed in R3?

Review 3 replaces the one-shot heuristic evaluation of R2 with a **State-Space Search Engine** that looks ahead multiple moves into the future. Instead of asking *"which single move scores best right now?"*, it asks *"which move leads to the best position several turns from now?"*

The engine is composed of **four tightly integrated algorithms**, each contributed by a team member:

| # | Algorithm | Member | File |
|---|-----------|--------|------|
| 1 | **Pure Backtracking** — in-place doMove/undoMove | Suhas | `R3Algorithms.java` |
| 2 | **Alpha-Beta Pruning Minimax** | Maneesh | `R3Algorithms.java` |
| 3 | **Transposition Table (Zobrist Hash)** | Ganesh | `R3Algorithms.java` |
| 4 | **Bottom-Up Bitmask DP Oracle** (4×4) | Balaji | `R3Algorithms.java` |

### New Features Also Added in R3
- **3D Nintendo-style tiles** — custom `TileButton.java` with 3-layer paint
- **Brain Scanner** — real-time AI decision log panel (thread-safe)
- **Dynamic Black Hole tiles** — 1–2 random dead tiles per game

---

## 2. Algorithm 1 — SUHAS: Pure Backtracking

### Key Insight: XOR Flip Is Its Own Inverse

In Flip Wars, every flip toggles tiles using boolean NOT. Critically:

```
NOT(NOT(x)) = x
```

This means applying the same flip **twice** restores the original state exactly. So `doMove` and `undoMove` are identical functions — no separate "undo logic" is needed.

### Implementation

```java
// R3Algorithms.java
public void doMove(boolean[] board, int move) {
    for (int neighbor : graph.getNeighbors(move)) {
        if (!rules.isLocked(neighbor) && !rules.isDeadTile(neighbor)) {
            board[neighbor] = !board[neighbor];   // XOR flip
        }
    }
}

public void undoMove(boolean[] board, int move) {
    doMove(board, move);   // Identical — XOR inverts itself
}
```

### Why This Matters

| Approach | Space per depth level | Allocations for depth-6 search |
|---|---|---|
| `board.clone()` (old R2) | O(n) | 6 × n booleans |
| **doMove/undoMove (R3)** | **O(1)** | **Zero** |

A single shared `boolean[]` is passed through the entire search tree. Only the last move is ever "in flight" — when we return from recursion, `undoMove` restores it.

### Complexity
- **Time:** O(k) per call where k ≤ 5 (flip group size), effectively O(1)
- **Space:** O(1) extra per depth level — O(depth) total call stack

---

## 3. Algorithm 2 — MANEESH: Alpha-Beta Pruning Minimax

### What Is Minimax?

The game tree is a perfect-information two-player zero-sum game. Minimax models both players optimally:
- **Maximizing player** (Human, Yellow): picks the move with the **highest** score
- **Minimizing player** (CPU, Grey): picks the move with the **lowest** score

### Alpha-Beta Pruning

Alpha-Beta prunes entire subtrees that cannot affect the final decision:

```
alpha = best score the MAXIMIZER can guarantee (starts at -∞)
beta  = best score the MINIMIZER can guarantee (starts at +∞)

If beta ≤ alpha:  the current branch can NEVER be chosen → PRUNE
```

```
Game tree example (depth 2):
         MAX(root)
        /    \    \
      MIN    MIN   MIN
     / \    / \    / \
    3   5  2   9  [PRUNED]
        ↑
    beta=5, so right branch with MIN(2,9)
    is pruned: MAX already guaranteed 5
```

### Move Ordering — D&C Integration

Before exploring a node's children, we **sort moves best-first** using the R2 heuristic evaluator:

```java
private List<Integer> orderMoves(List<Integer> moves, boolean[] board, boolean forPlayer) {
    List<int[]> scored = new ArrayList<>();
    for (int move : moves) {
        boolean[] temp = board.clone();  // Shallow clone ONLY for ordering
        applyFlip(temp, move);
        double score = evaluateLeaf(temp, forPlayer);
        scored.add(new int[]{move, (int)(score * 1000)});
    }
    scored.sort((a, b) -> Integer.compare(b[1], a[1]));  // Best first
    ...
}
```

By searching the best move first, alpha gets a tight upper bound early, causing more β-cutoffs on siblings.

### Dynamic Depth Limit

| Grid | MAX_DEPTH | Raw nodes | After α-β |
|------|-----------|-----------|-----------|
| 4×4  | 6 | ≈16M | ≈4,000 |
| 5×5  | 4 | ≈390K | ≈19,000 |
| 6×6  | 3 | ≈46K | ≈2,000 |

Deeper search is safe on 4×4 because the branching factor (≤16) is much smaller than 5×5 (≤25).

### Complexity

| Metric | Worst Case | With Move Ordering |
|--------|------------|-------------------|
| Time | O(b^d) | O(b^(d/2)) |
| Space | O(d) | O(d) |

Where b = branching factor, d = MAX_DEPTH. Alpha-Beta effectively **doubles** the achievable depth vs raw Minimax.

---

## 4. Algorithm 3 — GANESH: Transposition Table (Top-Down Memoization)

### The Problem: Overlapping Subproblems

In a game tree, the same board position can be reached via many different move sequences:

```
Root → Move A → Move B → [State X]
Root → Move B → Move A → [State X]

Without memoization: State X evaluated TWICE (or more).
With memoization:    State X evaluated ONCE, instantly returned on second visit.
```

### Zobrist Hashing

A Zobrist hash converts the board to a 64-bit integer in O(n):

```java
// R3Algorithms.java
private long getBoardHash(boolean[] board) {
    long hash = 0L;
    for (int i = 0; i < totalTiles; i++) {
        if (board[i])          hash ^= zobristTile[i];  // Tile ownership
        if (rules.isLocked(i)) hash ^= zobristLock[i];  // Lock state (Tabu)
    }
    return hash;
}
```

**Why include lock state?** Board A and Board B with identical tiles but different locked tiles have different legal moves → they are different game states. Hashing only tile ownership would produce incorrect cache hits.

**Collision probability:** ≈ 1/2^64 ≈ 5×10⁻²⁰ — negligible for a game.

### Transposition Table with Bounds

The table stores not just a score but also whether it's an exact value, lower bound, or upper bound (standard enhancement):

```java
private final HashMap<Long, double[]> ttTable = new HashMap<>();
// Entry: {score, depth, nodeType}
// nodeType: 0=exact, 1=lower-bound (α-cutoff), 2=upper-bound (β-cutoff)
```

```java
// Inside alphaBeta()
if (ttTable.containsKey(hash)) {
    double[] entry = ttTable.get(hash);
    if (entry[1] >= depth) {         // Stored at sufficient depth
        log("  [TT HIT] d=" + depth);
        if (nodeType == 0) return storedScore;           // Exact match
        if (nodeType == 1) alpha = max(alpha, stored);   // Tighten alpha
        if (nodeType == 2) beta  = min(beta,  stored);   // Tighten beta
        if (beta <= alpha) return storedScore;           // Prune via TT
    }
}
```

### Complexity

| Operation | Complexity |
|-----------|-----------|
| Hash computation | O(n) |
| Table lookup | O(1) average (HashMap) |
| Table store | O(1) average |
| Memory | O(unique states visited) |

---

## 5. Algorithm 4 — BALAJI: Bottom-Up Bitmask DP Oracle (4×4)

### State Space

A 4×4 board has 16 tiles. Each tile is true/false → 2^16 = **65,536 possible states**.

```
State 0x0000 = 0000000000000000 = all CPU (game over, CPU wins)
State 0xFFFF = 1111111111111111 = all Player (game over, Player wins)
State 0x3A7B = some mid-game position
```

This fits in a single `int[65536]` array (256KB) — tiny.

### Bottom-Up BFS Algorithm

Instead of computing "how far from current state to goal?", we work in **reverse** — broadcast distances backward from all goal states simultaneously:

```
Step 0: Mark winning states (exactSolver[0x0000] = 0, exactSolver[0xFFFF] = 0)
Step 1: All states reachable in 1 flip from a goal → distance = 1
Step 2: All unvisited states reachable from distance-1 states → distance = 2
...
```

```java
// R3Algorithms.java — precompute4x4Oracle()
// Precompute flip masks (16-bit XOR for each tile click)
int[] flipMask = new int[16];
for (int i = 0; i < 16; i++) {
    int mask = (1 << i);  // self
    int row = i / 4, col = i % 4;
    if (row > 0) mask |= (1 << (i - 4));  // up
    if (row < 3) mask |= (1 << (i + 4));  // down
    if (col > 0) mask |= (1 << (i - 1));  // left
    if (col < 3) mask |= (1 << (i + 1));  // right
    flipMask[i] = mask;
}

// BFS from goal states
exactSolver[0] = exactSolver[0xFFFF] = 0;
bfsQueue.add(0); bfsQueue.add(0xFFFF);

while (!bfsQueue.isEmpty()) {
    int state = bfsQueue.poll();
    int dist  = exactSolver[state];
    for (int tile = 0; tile < 16; tile++) {
        int prev = state ^ flipMask[tile];  // XOR = predecessor (flip is inverse)
        if (exactSolver[prev] == -1) {
            exactSolver[prev] = dist + 1;
            bfsQueue.add(prev);
        }
    }
}
```

### O(1) Oracle Lookup

After precomputation, finding the best hint move is O(16) = O(1):

```java
private int getExactWinMove(int boardState) {
    int bestMove = -1, bestDist = Integer.MAX_VALUE;
    for (int tile = 0; tile < 16; tile++) {
        if (rules.isLocked(tile) || rules.isDeadTile(tile)) continue;
        int nextState = boardState ^ flipMask[tile];
        if (exactSolver[nextState] < bestDist) {
            bestDist = exactSolver[nextState];
            bestMove = tile;
        }
    }
    return bestMove;
}
```

### Threading (Non-Blocking Startup)

The BFS runs on a daemon background thread at startup:

```java
Thread oracleThread = new Thread(() -> {
    precompute4x4Oracle();       // ~10ms, O(65536 × 16) ops
    oracleReady = true;          // volatile — visible to all threads
}, "Oracle-BFS-Thread");
oracleThread.setDaemon(true);   // Won't block JVM exit
oracleThread.start();
```

If `getPlayerHintR3()` is called before the oracle is ready, it transparently falls back to Alpha-Beta.

### Complexity

| Phase | Time | Space |
|-------|------|-------|
| Precomputation | O(65536 × 16) ≈ O(1M) | O(65536) = 256 KB |
| Single lookup | O(16) = **O(1)** | O(1) |

---

## 6. How All 4 Algorithms Work Together

```
getPlayerHintR3() / getBestMoveR3()
         │
         ▼
   ┌─────────────────────────────────┐
   │  4×4 + oracleReady?             │
   │  YES → BALAJI Oracle O(1)       │
   │  NO  → Alpha-Beta search        │
   └─────────────────────────────────┘
                    │
                    ▼
         alphaBeta(board, depth, α, β)
         │
         ├─ GANESH: Check ttTable[hash]
         │  HIT → return cached score instantly
         │  MISS → continue search
         │
         ├─ MANEESH: Order candidate moves (R2 heuristic, best first)
         │
         └─ For each ordered move:
              SUHAS: doMove(board, move)   ← in-place, O(1) space
              recursive alphaBeta(...)
              SUHAS: undoMove(board, move) ← XOR restore
              MANEESH: α-β prune if β ≤ α
              GANESH: Store result in ttTable
```

---

## 7. Dynamic Obstacles — Black Hole Tiles

At every game start, 1–2 tiles are randomly designated as **Black Holes**:

- **Cannot be clicked** — blocked in `handlePlayerMove()`
- **Cannot be flipped** — skipped in `performFlip()`
- **Skipped by all AIs** — filtered in `getAvailableMoves()`
- **Rendered as dark pits** — custom `TileButton` coloring in `updateBoardUI()`

**Why this impresses:** It proves that `Graph.java` (adjacency list) and `DACAlgorithms.evaluateClusters()` (DFS) handle **irregular, non-symmetric graphs** without any code changes — the algorithms are graph-general by design.

---

## 8. R3 Brain Scanner — Thread Safety

The Alpha-Beta search runs on a background `Thread` (same as R2's CPU thinking thread). Logging to a Swing `JTextArea` from a non-EDT thread would cause race conditions and visual corruption.

**Solution:** `SwingUtilities.invokeLater()` queues UI updates onto the Event Dispatch Thread:

```java
private void logBrain(String msg) {
    SwingUtilities.invokeLater(() -> {   // EDT-safe
        brainLog.append(msg + "\n");
        brainLog.setCaretPosition(brainLog.getDocument().getLength());
    });
}
```

**Panel explanation:** *"Our AI search runs on asynchronous background threads to prevent UI freezing. All Brain Scanner updates are marshalled back to the Event Dispatch Thread via `invokeLater()`, ensuring thread safety without locks."*

---

## 9. Complete Complexity Summary (All 3 Reviews)

| Algorithm | Version | Time | Space | Paradigm |
|-----------|---------|------|-------|---------|
| Greedy (tile count) | R1 | O(n) | O(1) | Greedy |
| Merge Sort | R1, R2 | O(n log n) | O(n) | D&C |
| Spatial D&C (Quadrants) | R2 | O(n) | O(1) | D&C — Spatial |
| DFS Clusters | R2 | O(V+E) | O(n) | D&C — Structural |
| Tournament Selection | R2 | O(n) | O(log n) | D&C — Search Space |
| Threat Detection | R2 | O(n) | O(1) | D&C — Scoring |
| **Pure Backtracking** | **R3** | **O(b^d)** | **O(d)** | **Backtracking** |
| **Alpha-Beta Minimax** | **R3** | **O(b^(d/2))** | **O(d)** | **Branch & Bound** |
| **Zobrist TT (Memoization)** | **R3** | **O(1) per hit** | **O(states)** | **Top-Down DP** |
| **Bitmask Oracle (BFS DP)** | **R3** | **O(1) lookup** | **O(2^n)** | **Bottom-Up DP** |

---

## 10. Architecture (Updated for R3)

```
Main.java ──────────────────► Engine.java ──────────────► DACAlgorithms.java
(UI: 3D TileButton,           (Version Router:             (5 D&C algorithms:
 Brain Scanner,                R1 Greedy +                  Spatial, DFS,
 Black Hole tiles,             R2 D&C +                     Tournament,
 logBrain() EDT logger)        R3 DP+BT)                    Threat, Merge Sort)
      │                              │
      │                              ▼
      │                      R3Algorithms.java
      │                      ┌─────────────────────┐
      │                      │ Suhas: doMove/undoMove│
      │                      │ Maneesh: alphaBeta   │
      │                      │ Ganesh: Zobrist TT   │
      │                      │ Balaji: 4×4 Oracle   │
      │                      └─────────────────────┘
      │                              │
      └──────────┬───────────────────┘
                 ▼
          Rules.java ◄──── Graph.java
       (Tabu + Black Holes)  (Adjacency Lists)
       (Strategic Values)     (O(1) neighbor lookup)
             │
             ▼
       TileButton.java
       (3D paint: shadow/face/glare)
```
