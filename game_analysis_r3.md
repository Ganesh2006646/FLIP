# Flip Wars — Complete Game & Algorithm Analysis (Review 3)

## 1. What Is Flip Wars?

Flip Wars is a **two-player strategic tile-flipping board game** where a human player (Yellow) battles a CPU opponent (Grey). Review 3 demonstrates **Dynamic Programming + Backtracking** as the AI backbone, with two new "Out-of-the-Box" features: **Dynamic Obstacles (Black Holes)** and a **Brain Scanner** real-time AI log.

### Game Configuration

| Attribute | Value |
|-----------|-------|
| Grid Sizes | 4×4 (16 tiles), 5×5 (25 tiles), 6×6 (36 tiles) |
| Players | Human (Yellow) vs CPU (Grey) |
| Turn Limit | 15 turns (4×4), 25 turns (5×5, 6×6) |
| Win Condition | All tiles one color OR highest score at turn limit |
| Black Holes | 2 dead tiles per game (unclickable, unownable) |

### Review 3 New Features
- **Dynamic Obstacles (Black Holes):** 2 randomly placed void tiles warp the graph topology each game
- **Brain Scanner:** Real-time AI reasoning logged to a UI text area
- **4 DP + Backtracking Algorithms:** State-Space Search Engine replaces heuristic-only play
- **Version Selector:** R1 (Greedy) → R2 (D&C) → R3 (DP + Backtracking)

---

## 🔄 2. How the Game Works — Step by Step

### 2.0 Game Loop Workflow

```mermaid
graph TD
    Start([Start]) --> BH[Generate 2 Black Holes]
    BH --> RebuildGraph[Rebuild Graph\nwithout BH edges]
    RebuildGraph --> PlayerTurn[Player Turn]
    PlayerTurn --> PlayerClick[Player Click]
    PlayerClick --> Validate{Valid Move?\nNot BH, Not Locked}
    Validate -->|No| PlayerTurn
    Validate -->|Yes| FlipTiles[Flip Tiles & Neighbors]
    FlipTiles --> BrainLog[Brain Scanner Log]
    BrainLog --> UpdateScore[Update Scores]
    UpdateScore --> CheckWin{Game Over?}
    CheckWin -->|No| CPUTurn[CPU Turn]
    CheckWin -->|Yes| GameOver([End Game])
    CPUTurn --> AlphaBeta[Alpha-Beta Minimax\nDepth 6/4/3]
    AlphaBeta --> MemoCheck{Transposition\nTable Hit?}
    MemoCheck -->|Yes| ReturnCached[Return Cached Score O(1)]
    MemoCheck -->|No| Explore[Explore Branch\ndoMove/undoMove]
    Explore --> MemoStore[Store in TT]
    MemoStore --> FlipTiles

    style Start fill:#f9f,stroke:#333,stroke-width:2px
    style GameOver fill:#f9f,stroke:#333,stroke-width:2px
    style Validate fill:#ffd,stroke:#333,stroke-width:2px
    style CheckWin fill:#ffd,stroke:#333,stroke-width:2px
    style MemoCheck fill:#ffd,stroke:#333,stroke-width:2px
    style BH fill:#222,color:#fff,stroke:#f00,stroke-width:2px
```

### 2.1 Black Holes — Dynamic Obstacles

At game start, 2 tiles are randomly selected as **Black Holes**. They are:
- Rendered black and disabled in the UI
- Removed from the Graph adjacency list (no edges to/from them)
- Skipped by all scoring and DFS algorithms

```java
// Main.java — startGame()
blackHoles = new HashSet<>();
Random rand = new Random();
while (blackHoles.size() < 2) {
    blackHoles.add(rand.nextInt(totalTiles));
}
graph = new Graph(gridSize, blackHoles);    // Graph rebuilt without BH edges
rules = new Rules(gridSize, blackHoles);    // Rules ignores BH in scoring
logBrain("=== GAME START ===");
logBrain("Grid Size: " + gridSize + "x" + gridSize);
logBrain("Black Holes at tiles: " + blackHoles);
```

**Why is this powerful for graph theory?**
The DFS Cluster algorithm naturally "flows around" Black Holes without any code change — because Black Hole tiles have **no edges** in the adjacency list, DFS simply never visits them. This demonstrates real **irregular graph handling**.

```
4×4 Board with Black Holes at tile 5 and 10:
┌───┬───┬───┬───┐
│ 0 │ 1 │ 2 │ 3 │   Tile 1's neighbors: {0, 2, 5} → 5 is BH
├───┼───┼───┼───┤                           → after rebuild: {0, 2}
│ 4 │ ■ │ 6 │ 7 │   DFS from tile 4 cannot cross tile 5
├───┼───┼───┼───┤   DFS from tile 11 cannot visit tile 10
│ 8 │ 9 │ ■ │11 │   This creates TWO disconnected graph components!
├───┼───┼───┼───┤
│12 │13 │14 │15 │
└───┴───┴───┴───┘
■ = Black Hole (no adjacency edges)
```

### 2.2 Flip Mechanic (The Core Action)

When any tile is clicked, it flips **itself AND all 4 orthogonal neighbors** in a **plus (+) pattern**. Black Holes are skipped automatically since they have no neighbors in the graph.

```java
// Graph.java — initializeGraph() with Black Hole support
private void initializeGraph() {
    for (int r = 0; r < gridSize; r++) {
        for (int c = 0; c < gridSize; c++) {
            int id = r * gridSize + c;
            if (blackHoles.contains(id)) {
                adjacencyList.put(id, Collections.emptyList()); // BH: no edges
                continue;
            }
            List<Integer> neighbors = new ArrayList<>();
            neighbors.add(id);  // self
            // Only add neighbors that are NOT black holes
            if (r > 0 && !blackHoles.contains(id - gridSize)) neighbors.add(id - gridSize);
            if (r < gridSize-1 && !blackHoles.contains(id + gridSize)) neighbors.add(id + gridSize);
            if (c > 0 && !blackHoles.contains(id - 1)) neighbors.add(id - 1);
            if (c < gridSize-1 && !blackHoles.contains(id + 1)) neighbors.add(id + 1);
            adjacencyList.put(id, neighbors);
        }
    }
}
```

**Why a Graph?** Neighbors are precomputed once at construction → O(1) lookup per flip. Black Hole exclusion at construction time costs zero during gameplay.

---

### 2.3 Lock Mechanic (Tabu Search)

Same as R2 — after clicking, a tile is locked for several turns using a `LinkedHashSet` for O(1) lookup. Black Holes are never added to the Tabu set (they can't be clicked).

---

### 2.4 Brain Scanner (Real-Time AI Logging)

A `Consumer<String> logger` is injected into `Engine` and `DACAlgorithms` at construction time. During each AI computation, key reasoning steps are sent to the UI's `logBrain()` text area.

```
[GAME START] Grid: 4x4 | Black Holes: {5, 10}
[Hint] Merge Sort ranked 14 moves. Top: Tile 3
[Tournament D&C] Tile 7 vs Tile 3 → Winner: Tile 3 (score: 142.5 > 98.0)
[Alpha-Beta] Depth=6 | α=-∞ β=+∞ | Exploring 14 moves
[TT Hit] State hash=0xABCD1234 at depth=4 → returning cached score 87.3
[Alpha-Beta Cut] β(72.1) ≤ α(89.4) → Pruned 9 branches
[Oracle] 4x4 BFS lookup: Tile 3 → distance=2 to win
```

---

## 🧠 3. The 4 DP + Backtracking Algorithms (R3)

### CPU Decision Flow

```mermaid
graph TD
    A([CPU Turn Start]) --> B[Get & Order Moves\nR2 Heuristic Sort]

    subgraph BT ["SUHAS — Pure Backtracking"]
        B --> C[doMove board move\nO(1) in-place flip]
        C --> D{Base Case?\ndepth=0 or no moves}
        D -->|Yes| E[evaluateLeaf\nR2 scoring]
        D -->|No| F[Recurse alphaBeta]
        F --> G[undoMove board move\nXOR self-inverse restore]
    end

    subgraph TT ["GANESH — Transposition Table"]
        H{Zobrist Hash\nin ttTable?}
        H -->|Hit O(1)| I[Return cached score]
        H -->|Miss| C
        G --> J[Store hash → score\nin ttTable]
    end

    subgraph AB ["MANEESH — Alpha-Beta Pruning"]
        K{beta ≤ alpha?}
        K -->|Yes: Prune| L[Skip remaining branches]
        K -->|No| F
        J --> K
    end

    subgraph OR ["BALAJI — Bitmask DP Oracle (4x4)"]
        M{Grid == 4x4\n& oracleReady?}
        M -->|Yes| N[exactSolver lookup\nO(1) BFS distance]
        M -->|No| B
    end

    B --> H
    E --> J

    style A fill:#c084fc,stroke:#7c3aed,stroke-width:2px,color:#fff
    style BT fill:#1e3a5f,stroke:#3b82f6,color:#fff
    style TT fill:#1a3a1a,stroke:#22c55e,color:#fff
    style AB fill:#3a1a1a,stroke:#ef4444,color:#fff
    style OR fill:#3a2a00,stroke:#f59e0b,color:#fff
```

---

### 3.1 ALGORITHM 1 — SUHAS: Pure Backtracking

**File:** [R3Algorithms.java](file:///d:/DAA/src/main/java/com/flipwars/R3Algorithms.java)

**Key Insight:** A tile flip is an **involuntary operation** (XOR). Applying the same flip twice returns to the exact original state. This means `undoMove ≡ doMove` mathematically.

```java
public void doMove(boolean[] board, int move) {
    for (int neighbor : graph.getNeighbors(move)) {
        if (!rules.isLocked(neighbor)) {
            board[neighbor] = !board[neighbor];  // XOR flip
        }
    }
}

public void undoMove(boolean[] board, int move) {
    doMove(board, move);  // Identical — XOR is self-inverse
}
```

**D&C Pattern (State-Space Search):**
```
DIVIDE:   Split game tree into MAX/MIN layers
CONQUER:  Recurse to depth limit, evaluate leaf
COMBINE:  Propagate best score upward via α-β
```

**Complexity per depth level:**

| Metric | Value | Justification |
|--------|-------|---------------|
| **Space** | O(1) | No board.clone() — single shared array |
| **Time per flip** | O(k), k≤5 | Only neighbors flipped (graph degree ≤ 5) |

---

### 3.2 ALGORITHM 2 — MANEESH: Alpha-Beta Pruning Minimax

**File:** [R3Algorithms.java](file:///d:/DAA/src/main/java/com/flipwars/R3Algorithms.java)

**What:** Recursive Minimax with α-β pruning. The maximizer (Player) and minimizer (CPU) alternate. Branches guaranteed to be worse than the current best are skipped.

**Dynamic Depth — prevents UI freeze:**

| Grid | MAX_DEPTH | Raw nodes | After α-β |
|------|-----------|-----------|-----------|
| 4×4  | 6         | ~16M      | ~4,000    |
| 5×5  | 4         | ~390K     | ~19,000   |
| 6×6  | 3         | ~46K      | ~2,000    |

**Move Ordering (D&C Integration):** Before expanding any node, moves are scored by the R2 heuristic and sorted best-first. This maximizes α-β cutoffs (best moves evaluated first → tighter bounds → more pruning).

```java
private double alphaBeta(boolean[] board, int depth,
                         double alpha, double beta, boolean isMaximizing) {
    // GANESH: TT lookup before any recursion
    long hash = getBoardHash(board);
    if (ttTable.containsKey(hash) && ttTable.get(hash)[1] >= depth)
        return ttTable.get(hash)[0];

    if (depth == 0) return evaluateLeaf(board, isMaximizing);

    // MANEESH: Order moves best-first (D&C heuristic sort)
    List<Integer> moves = orderMoves(getAvailableMoves(), board, isMaximizing);

    double best = isMaximizing ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
    for (int move : moves) {
        doMove(board, move);                                    // SUHAS
        double val = alphaBeta(board, depth-1, alpha, beta, !isMaximizing);
        undoMove(board, move);                                  // SUHAS

        if (isMaximizing) { best = Math.max(best, val); alpha = Math.max(alpha, best); }
        else              { best = Math.min(best, val); beta  = Math.min(beta,  best); }

        if (beta <= alpha) break;  // ← The prune moment (β-cut or α-cut)
    }
    ttTable.put(hash, new double[]{best, depth, nodeType});    // GANESH
    return best;
}
```

**Recurrence:**
```
T(n,d) = n × T(n, d-1)  (no pruning)
       ≈ √n^d            (with optimal α-β ordering)
```

---

### 3.3 ALGORITHM 3 — GANESH: Zobrist Transposition Table (Top-Down DP)

**File:** [R3Algorithms.java](file:///d:/DAA/src/main/java/com/flipwars/R3Algorithms.java)

**What:** Memoizes already-evaluated board states. Two different move sequences reaching the same board are overlapping subproblems — only evaluated once.

**Key design — lock state included in hash:**
```java
private long getBoardHash(boolean[] board) {
    long hash = 0L;
    for (int i = 0; i < totalTiles; i++) {
        if (board[i])          hash ^= zobristTile[i]; // tile ownership
        if (rules.isLocked(i)) hash ^= zobristLock[i]; // Tabu lock state
    }
    return hash;
}
```

**Why include lock state?** Board A (tile 5 locked) and Board B (same tiles, tile 5 unlocked) have different legal moves. Using B's cached score for A would return a wrong result. Including lock state → **zero false cache hits**.

| Metric | Value |
|--------|-------|
| Hash Complexity | O(n) |
| Lookup | O(1) — HashMap |
| Collision Probability | ~1 / 2^64 ≈ 0 |

---

### 3.4 ALGORITHM 4 — BALAJI: Bottom-Up Bitmask DP Oracle (4×4)

**File:** [R3Algorithms.java](file:///d:/DAA/src/main/java/com/flipwars/R3Algorithms.java)

**What:** Precomputes the exact minimum number of moves to reach a winning state for all 65,536 possible 4×4 board configurations. This is a true **Bottom-Up Dynamic Programming** solution — no approximation.

**State representation:** A 4×4 board has 16 binary tiles → 2^16 = 65,536 states fit in `int[65536]`.

**BFS from goal states (backward induction):**
```
exactSolver[0x0000] = 0  (all-CPU: winner!)
exactSolver[0xFFFF] = 0  (all-Player: winner!)

BFS level 1: all states reachable in 1 flip from {0, 0xFFFF}
BFS level 2: all states reachable in 2 flips from level-1 states
...until all 65,536 states assigned a distance
```

**Hint lookup — O(1):**
```java
private int getExactWinMove(int boardState) {
    int bestMove = -1, bestDist = Integer.MAX_VALUE;
    for (int tile = 0; tile < 16; tile++) {
        if (rules.isLocked(tile)) continue;
        int nextState = boardState ^ flipMask[tile];  // bitmask XOR = instant flip
        if (exactSolver[nextState] < bestDist) {
            bestDist = exactSolver[nextState];
            bestMove = tile;
        }
    }
    return bestMove;
}
```

**Threading:** BFS runs in a daemon background thread at startup. `volatile boolean oracleReady` gates usage. Falls back to Alpha-Beta transparently if called before BFS completes.

| Metric | Value |
|--------|-------|
| Precomputation | O(65536 × 16) = O(1M) < 50ms |
| Hint lookup | O(16) = O(1) |
| Space | O(65536) ≈ 256KB |

---

## 🔵 4. Dynamic Obstacles — Black Holes

### Effect on Each Algorithm

| Algorithm | Effect of Black Holes |
|-----------|----------------------|
| **Graph (Adjacency)** | BH tiles have empty neighbor lists → no edges in/out |
| **DFS Clusters** | DFS never traverses into BH → natural component splitting |
| **Spatial D&C (Quadrants)** | BH IDs skipped in subgrid scoring → no phantom CPU score |
| **Threat Detection** | BH tiles skipped → no false threat counts |
| **Strategic Value** | `getTileStrategicValue(BH)` returns 0.0 |
| **Alpha-Beta** | BH tiles excluded from available moves list |

### Why This Demonstrates Irregular Graph Handling

Standard grid graphs are **regular** (every interior node has degree 4). With Black Holes, nodes adjacent to them have **reduced degree**. The DFS, BFS, and Alpha-Beta all work correctly on this irregular structure without any algorithm-level changes — only the graph topology changes at construction.

---

## 5. Version Comparison (R1 vs R2 vs R3)

| Feature | R1: Greedy | R2: D&C | R3: DP + BT |
|---------|-----------|---------|------------|
| **CPU Move** | Greedy + 15% blunder | Tournament Selection O(n²) | Alpha-Beta Minimax |
| **Player Hint** | Merge Sort | Merge Sort + D&C eval | Oracle O(1) (4×4) / Alpha-Beta |
| **Lookahead** | 0 (single move) | 0 (single move) | 3–6 moves deep |
| **Memory** | None | None | Transposition Table |
| **Black Holes** | ❌ | ❌ | ✅ |
| **Brain Scanner** | ❌ | ❌ | ✅ |
| **Difficulty** | Easy | Medium | Hard |

---

## 6. Complete Complexity Summary (R3)

| Algorithm | Time | Space | Recurrence |
|-----------|------|-------|------------|
| doMove / undoMove (Suhas) | O(k), k≤5 | O(1) | — |
| Alpha-Beta Minimax (Maneesh) | O(b^(d/2)) with ordering | O(d) | T(n,d)=nT(n,d-1) |
| Zobrist Hash (Ganesh) | O(n) per hash | O(n) table | — |
| TT Lookup (Ganesh) | O(1) | O(states visited) | — |
| Oracle BFS (Balaji) | O(2^16 × 16) pre | O(2^16) | BFS |
| Oracle Hint (Balaji) | O(16) = O(1) | O(1) | — |
| Black Hole Graph init | O(n) | O(n) | — |

**Total per CPU move (4×4):** O(1) Oracle hit or O(√b^d) with α-β — far faster than R2's O(n²) for deep lookahead.

---

## 7. Architecture (Review 3)

```
┌──────────────────────────────┐
│          Main.java           │
│  UI + Game Loop              │
│  + blackHoles: Set<Integer>  │
│  + logBrain(String)          │
│  + Brain Scanner TextArea    │
└───────┬──────────────────────┘
        │ Consumer<String> logger injected
        v
┌───────────────────────┐       ┌──────────────────────────┐
│      Engine.java      │──────►│    DACAlgorithms.java    │
│  R1 Greedy            │       │  Spatial + DFS +         │
│  R2 D&C               │       │  Tournament + Threat     │
│  R3 → R3Algorithms    │       │  (logger-aware)          │
└───────┬───────────────┘       └──────────────────────────┘
        │
        v
┌──────────────────────────────┐
│       R3Algorithms.java      │
│  SUHAS   — doMove/undoMove   │
│  MANEESH — Alpha-Beta        │
│  GANESH  — Zobrist TT        │
│  BALAJI  — Bitmask DP Oracle │
└───────┬──────────────────────┘
        │ uses
        v
┌─────────────────┐    ┌──────────────────────┐
│   Graph.java    │    │      Rules.java       │
│  Adjacency List │    │  Tabu + Scoring       │
│  BH-aware edges │    │  BH-aware evaluation  │
└─────────────────┘    └──────────────────────┘
```

---

## 8. Conclusion

| # | Algorithm | Paradigm | Who | Used For |
|---|-----------|----------|-----|----------|
| 1 | **doMove / undoMove** | Backtracking | Suhas | In-place search tree traversal |
| 2 | **Alpha-Beta Minimax** | Branch & Bound | Maneesh | CPU move selection (depth 3–6) |
| 3 | **Zobrist Transposition Table** | Top-Down DP | Ganesh | Overlapping subproblem caching |
| 4 | **Bitmask DP Oracle** | Bottom-Up DP | Balaji | O(1) perfect 4×4 hints |
| 5 | **Black Holes** | Irregular Graphs | Feature | Dynamic topology disruption |
| 6 | **Brain Scanner** | Observability | Feature | Real-time AI reasoning log |

**Key takeaway for the viva:** R3 is not just "smarter AI" — it is a fundamentally different problem formulation. R1 and R2 are **single-step heuristics**. R3 is a **multi-step state-space search** with provably correct play (Oracle) and polynomial-time approximation (Alpha-Beta), unified by the mathematical property that XOR is its own inverse.
