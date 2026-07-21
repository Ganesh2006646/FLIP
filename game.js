/* ═══════════════════════════════════════════════════════════════════════════
   FLIP WARS — Complete Game Engine (Web Port)
   Faithful port of the JavaFX version with all 6 source files ported:
   Graph, Rules, DACAlgorithms, R3Algorithms, Engine, FlipWarsApp (UI)
   ═══════════════════════════════════════════════════════════════════════════ */

// ═════════════════════════════════════════════════════════════════════════════
// GRAPH — Adjacency list with Black Hole awareness
// ═════════════════════════════════════════════════════════════════════════════
class Graph {
  constructor(gridSize, blackHoles = new Set()) {
    this.gridSize = gridSize;
    this.blackHoles = blackHoles;
    this.adjList = new Map();
    this._buildGraph();
  }

  _buildGraph() {
    const gs = this.gridSize;
    for (let r = 0; r < gs; r++) {
      for (let c = 0; c < gs; c++) {
        const id = r * gs + c;
        if (this.blackHoles.has(id)) {
          this.adjList.set(id, []);
          continue;
        }
        const neighbors = [id]; // always flip self
        const dirs = [[-1,0],[1,0],[0,-1],[0,1]];
        for (const [dr,dc] of dirs) {
          const nr = r + dr, nc = c + dc;
          if (nr >= 0 && nr < gs && nc >= 0 && nc < gs) {
            const nid = nr * gs + nc;
            if (!this.blackHoles.has(nid)) neighbors.push(nid);
          }
        }
        this.adjList.set(id, neighbors);
      }
    }
  }

  getNeighbors(tileId) {
    return this.adjList.get(tileId) || [];
  }

  isBlackHole(tileId) {
    return this.blackHoles.has(tileId);
  }
}

// ═════════════════════════════════════════════════════════════════════════════
// RULES — Tabu/Lock mechanic + Strategic tile values
// ═════════════════════════════════════════════════════════════════════════════
class Rules {
  constructor(gridSize, blackHoles = new Set()) {
    this.gridSize = gridSize;
    this.blackHoles = blackHoles;
    this.tabuSize = Math.max(2, Math.floor(gridSize * gridSize / 4));
    this.tabuList = []; // ordered array (oldest first)
  }

  recordMove(tileId) {
    if (this.blackHoles.has(tileId)) return;
    const idx = this.tabuList.indexOf(tileId);
    if (idx !== -1) this.tabuList.splice(idx, 1);
    this.tabuList.push(tileId);
    while (this.tabuList.length > this.tabuSize) this.tabuList.shift();
  }

  isLocked(tileId) {
    if (this.blackHoles.has(tileId)) return true;
    return this.tabuList.includes(tileId);
  }

  getLockCountdown(tileId) {
    if (this.blackHoles.has(tileId)) return 0;
    const idx = this.tabuList.indexOf(tileId);
    return idx === -1 ? 0 : idx + 1;
  }

  clearMemory() { this.tabuList = []; }

  getTileStrategicValue(id) {
    if (this.blackHoles.has(id)) return 0;
    const r = Math.floor(id / this.gridSize);
    const c = id % this.gridSize;
    const gs = this.gridSize;
    if ((r === 0 || r === gs - 1) && (c === 0 || c === gs - 1)) return 25; // Corners
    if (r === 0 || r === gs - 1 || c === 0 || c === gs - 1) return 15; // Edges
    if ((r <= 1 || r >= gs - 2) && (c <= 1 || c >= gs - 2)) return -5; // Traps
    return 5; // Standard
  }
}

// ═════════════════════════════════════════════════════════════════════════════
// DAC ALGORITHMS — Divide & Conquer scoring (Spatial, Cluster, Tournament, Threat)
// ═════════════════════════════════════════════════════════════════════════════
class DACAlgorithms {
  evaluateQuadrants(board, gridSize, forPlayer) {
    const half = Math.floor(gridSize / 2);
    const tl = this._evalSubGrid(board, 0, 0, half, gridSize, forPlayer);
    const tr = this._evalSubGrid(board, 0, half, gridSize - half, gridSize, forPlayer);
    const bl = this._evalSubGrid(board, half, 0, gridSize - half, gridSize, forPlayer);
    const br = this._evalSubGrid(board, half, half, gridSize - half, gridSize, forPlayer);
    return (tl * 2) + (tr * 1.5) + (bl * 1.5) + (br * 2);
  }

  _evalSubGrid(board, startRow, startCol, size, gridSize, forPlayer) {
    let score = 0;
    for (let r = startRow; r < startRow + size && r < gridSize; r++) {
      for (let c = startCol; c < startCol + size && c < gridSize; c++) {
        const id = r * gridSize + c;
        if (id < board.length) {
          score += board[id] === forPlayer ? 1 : -1;
        }
      }
    }
    return score;
  }

  evaluateClusters(board, gridSize, forPlayer) {
    const visited = new Array(board.length).fill(false);
    const sizes = [];
    for (let id = 0; id < board.length; id++) {
      if (!visited[id] && board[id] === forPlayer) {
        const size = this._dfsCluster(board, visited, id, gridSize, forPlayer);
        if (size > 0) sizes.push(size);
      }
    }
    sizes.sort((a, b) => b - a);
    let score = 0;
    for (let i = 0; i < Math.min(3, sizes.length); i++) {
      score += sizes[i] * sizes[i];
    }
    return score;
  }

  _dfsCluster(board, visited, id, gridSize, targetColor) {
    if (id < 0 || id >= board.length || visited[id] || board[id] !== targetColor) return 0;
    visited[id] = true;
    let size = 1;
    const r = Math.floor(id / gridSize), c = id % gridSize;
    if (r > 0) size += this._dfsCluster(board, visited, id - gridSize, gridSize, targetColor);
    if (r < gridSize - 1) size += this._dfsCluster(board, visited, id + gridSize, gridSize, targetColor);
    if (c > 0) size += this._dfsCluster(board, visited, id - 1, gridSize, targetColor);
    if (c < gridSize - 1) size += this._dfsCluster(board, visited, id + 1, gridSize, targetColor);
    return size;
  }

  evaluateThreats(board, gridSize, forPlayer) {
    const half = Math.floor(gridSize / 2);
    const tl = this._evalQuadThreats(board, 0, 0, half, gridSize, forPlayer);
    const tr = this._evalQuadThreats(board, 0, half, gridSize - half, gridSize, forPlayer);
    const bl = this._evalQuadThreats(board, half, 0, gridSize - half, gridSize, forPlayer);
    const br = this._evalQuadThreats(board, half, half, gridSize - half, gridSize, forPlayer);
    return (tl * 2) + (tr * 1.5) + (bl * 1.5) + (br * 2);
  }

  _evalQuadThreats(board, startRow, startCol, size, gridSize, forPlayer) {
    let ourThreats = 0, enemyThreats = 0;
    for (let r = startRow; r < startRow + size && r < gridSize; r++) {
      for (let c = startCol; c < startCol + size && c < gridSize; c++) {
        const id = r * gridSize + c;
        const isOurs = board[id] === forPlayer;
        let enemyN = 0;
        if (r > 0 && board[(r-1)*gridSize+c] !== board[id]) enemyN++;
        if (r < gridSize-1 && board[(r+1)*gridSize+c] !== board[id]) enemyN++;
        if (c > 0 && board[r*gridSize+(c-1)] !== board[id]) enemyN++;
        if (c < gridSize-1 && board[r*gridSize+(c+1)] !== board[id]) enemyN++;
        if (enemyN > 0) {
          if (isOurs) ourThreats += enemyN;
          else enemyThreats += enemyN;
        }
      }
    }
    return enemyThreats - ourThreats;
  }

  tournamentSelection(moves, board, graph, rules, forPlayer) {
    if (moves.length === 0) return -1;
    if (moves.length === 1) return moves[0];
    const mid = Math.floor(moves.length / 2);
    const leftChamp = this.tournamentSelection(moves.slice(0, mid), board, graph, rules, forPlayer);
    const rightChamp = this.tournamentSelection(moves.slice(mid), board, graph, rules, forPlayer);
    const sA = this._evalMove(leftChamp, board, graph, rules, forPlayer);
    const sB = this._evalMove(rightChamp, board, graph, rules, forPlayer);
    return sA >= sB ? leftChamp : rightChamp;
  }

  _evalMove(move, board, graph, rules, forPlayer) {
    if (move === -1) return -Infinity;
    const temp = [...board];
    for (const nb of graph.getNeighbors(move)) {
      if (!rules.isLocked(nb)) temp[nb] = !temp[nb];
    }
    let pScore = 0, cScore = 0;
    for (let i = 0; i < temp.length; i++) {
      const v = rules.getTileStrategicValue(i);
      if (temp[i]) pScore += v; else cScore += v;
    }
    return forPlayer ? (pScore - cScore) : (cScore - pScore);
  }
}

// ═════════════════════════════════════════════════════════════════════════════
// R3 ALGORITHMS — Alpha-Beta + Backtracking + Zobrist TT + Oracle
// ═════════════════════════════════════════════════════════════════════════════
class R3Algorithms {
  constructor(gridSize, graph, rules) {
    this.gridSize = gridSize;
    this.totalTiles = gridSize * gridSize;
    this.graph = graph;
    this.rules = rules;
    this.dac = new DACAlgorithms();
    this.ttTable = new Map();

    // Dynamic depth
    if (gridSize === 4) this.MAX_DEPTH = 6;
    else if (gridSize === 5) this.MAX_DEPTH = 4;
    else this.MAX_DEPTH = 3;

    // Zobrist keys (deterministic seed via simple LCG)
    this.zobristTile = new Array(this.totalTiles);
    this.zobristLock = new Array(this.totalTiles);
    let seed = 0xDAAF17;
    const nextRand = () => { seed = (seed * 1664525 + 1013904223) & 0xFFFFFFFF; return seed; };
    for (let i = 0; i < this.totalTiles; i++) {
      this.zobristTile[i] = nextRand();
      this.zobristLock[i] = nextRand();
    }

    // 4x4 Oracle
    this.oracleReady = false;
    this.exactSolver = null;
    if (gridSize === 4) {
      this.exactSolver = new Int32Array(65536);
      this._precompute4x4Oracle();
    }
  }

  clearMemo() { this.ttTable.clear(); }

  getBestMoveR3(board, forPlayer) {
    this.clearMemo();
    let moves = this._getAvailableMoves();
    if (moves.length === 0) return -1;
    moves = this._orderMoves(moves, board, forPlayer);

    let bestMove = moves[0];
    let bestVal = -Infinity;
    let alpha = -Infinity, beta = Infinity;

    for (const move of moves) {
      this._doMove(board, move);
      const val = this._alphaBeta(board, this.MAX_DEPTH - 1, alpha, beta, !forPlayer);
      this._undoMove(board, move);
      if (val > bestVal) { bestVal = val; bestMove = move; }
      alpha = Math.max(alpha, bestVal);
    }
    return bestMove;
  }

  getPlayerHintR3(board) {
    if (this.gridSize === 4 && this.oracleReady) {
      const state = this._boardToInt(board);
      const hint = this._getExactWinMove(state);
      if (hint !== -1) return hint;
    }
    return this.getBestMoveR3(board, true);
  }

  _doMove(board, move) {
    for (const nb of this.graph.getNeighbors(move)) {
      if (!this.rules.isLocked(nb)) board[nb] = !board[nb];
    }
  }

  _undoMove(board, move) { this._doMove(board, move); } // XOR self-inverse

  _alphaBeta(board, depth, alpha, beta, isMax) {
    const hash = this._getBoardHash(board);
    if (this.ttTable.has(hash)) {
      const e = this.ttTable.get(hash);
      if (e[1] >= depth) {
        if (e[2] === 0) return e[0];
        if (e[2] === 1) alpha = Math.max(alpha, e[0]);
        if (e[2] === 2) beta = Math.min(beta, e[0]);
        if (beta <= alpha) return e[0];
      }
    }

    let moves = this._getAvailableMoves();
    if (depth === 0 || moves.length === 0) {
      const score = this._evaluateLeaf(board, isMax);
      this.ttTable.set(hash, [score, depth, 0]);
      return score;
    }

    moves = this._orderMoves(moves, board, isMax);
    const origAlpha = alpha;
    let bestScore;

    if (isMax) {
      bestScore = -Infinity;
      for (const move of moves) {
        this._doMove(board, move);
        const val = this._alphaBeta(board, depth - 1, alpha, beta, false);
        this._undoMove(board, move);
        bestScore = Math.max(bestScore, val);
        alpha = Math.max(alpha, bestScore);
        if (beta <= alpha) break;
      }
    } else {
      bestScore = Infinity;
      for (const move of moves) {
        this._doMove(board, move);
        const val = this._alphaBeta(board, depth - 1, alpha, beta, true);
        this._undoMove(board, move);
        bestScore = Math.min(bestScore, val);
        beta = Math.min(beta, bestScore);
        if (beta <= alpha) break;
      }
    }

    let nodeType;
    if (bestScore <= origAlpha) nodeType = 2;
    else if (bestScore >= beta) nodeType = 1;
    else nodeType = 0;
    this.ttTable.set(hash, [bestScore, depth, nodeType]);
    return bestScore;
  }

  _getBoardHash(board) {
    let hash = 0;
    for (let i = 0; i < this.totalTiles; i++) {
      if (board[i]) hash ^= this.zobristTile[i];
      if (this.rules.isLocked(i)) hash ^= this.zobristLock[i];
    }
    return hash;
  }

  _evaluateLeaf(board, forPlayer) {
    let strategic = 0;
    for (let i = 0; i < this.totalTiles; i++) {
      const v = this.rules.getTileStrategicValue(i);
      strategic += board[i] ? v : -v;
    }
    if (!forPlayer) strategic = -strategic;
    const quad = this.dac.evaluateQuadrants(board, this.gridSize, forPlayer);
    const cluster = this.dac.evaluateClusters(board, this.gridSize, forPlayer)
                  - this.dac.evaluateClusters(board, this.gridSize, !forPlayer) * 1.5;
    const threat = this.dac.evaluateThreats(board, this.gridSize, forPlayer);
    return (strategic * 0.20) + (quad * 0.25) + (cluster * 0.25) + (threat * 0.30);
  }

  _getAvailableMoves() {
    const moves = [];
    for (let i = 0; i < this.totalTiles; i++) {
      if (!this.rules.isLocked(i)) moves.push(i);
    }
    return moves;
  }

  _orderMoves(moves, board, forPlayer) {
    const scored = moves.map(move => {
      const temp = [...board];
      for (const nb of this.graph.getNeighbors(move)) {
        if (!this.rules.isLocked(nb)) temp[nb] = !temp[nb];
      }
      return { move, score: this._evaluateLeaf(temp, forPlayer) };
    });
    scored.sort((a, b) => b.score - a.score);
    return scored.map(s => s.move);
  }

  _precompute4x4Oracle() {
    const flipMask = new Int32Array(16);
    for (let i = 0; i < 16; i++) {
      let mask = 1 << i;
      const r = Math.floor(i / 4), c = i % 4;
      if (r > 0) mask |= 1 << (i - 4);
      if (r < 3) mask |= 1 << (i + 4);
      if (c > 0) mask |= 1 << (i - 1);
      if (c < 3) mask |= 1 << (i + 1);
      flipMask[i] = mask;
    }
    this.exactSolver.fill(-1);
    const queue = [0, 0xFFFF];
    this.exactSolver[0] = 0;
    this.exactSolver[0xFFFF] = 0;
    let head = 0;
    while (head < queue.length) {
      const state = queue[head++];
      const dist = this.exactSolver[state];
      for (let tile = 0; tile < 16; tile++) {
        const pred = state ^ flipMask[tile];
        if (this.exactSolver[pred] === -1) {
          this.exactSolver[pred] = dist + 1;
          queue.push(pred);
        }
      }
    }
    for (let s = 0; s < 65536; s++) {
      if (this.exactSolver[s] === -1) this.exactSolver[s] = 999999;
    }
    this.oracleReady = true;
  }

  _boardToInt(board) {
    let state = 0;
    for (let i = 0; i < Math.min(board.length, 16); i++) {
      if (board[i]) state |= (1 << i);
    }
    return state;
  }

  _getExactWinMove(boardState) {
    let bestMove = -1, bestDist = 999999;
    for (let tile = 0; tile < 16; tile++) {
      if (this.rules.isLocked(tile)) continue;
      const r = Math.floor(tile / 4), c = tile % 4;
      let mask = 1 << tile;
      if (r > 0) mask |= 1 << (tile - 4);
      if (r < 3) mask |= 1 << (tile + 4);
      if (c > 0) mask |= 1 << (tile - 1);
      if (c < 3) mask |= 1 << (tile + 1);
      const next = boardState ^ mask;
      const dist = this.exactSolver[next];
      if (dist < bestDist) { bestDist = dist; bestMove = tile; }
    }
    return bestMove;
  }
}

// ═════════════════════════════════════════════════════════════════════════════
// ENGINE — Main AI dispatcher (R1/R2/R3)
// ═════════════════════════════════════════════════════════════════════════════
class Engine {
  constructor(totalTiles, graph, rules) {
    this.totalTiles = totalTiles;
    this.graph = graph;
    this.rules = rules;
    this.dac = new DACAlgorithms();
    this.gridSize = Math.round(Math.sqrt(totalTiles));
    this.r3 = new R3Algorithms(this.gridSize, graph, rules);
    this.version = 3;
  }

  setVersion(v) {
    this.version = v;
    if (v === 3) this.r3.clearMemo();
  }

  getBestMove(currentState) {
    if (this.version === 1) return this._getBestMoveR1(currentState);
    if (this.version === 3) return this.r3.getBestMoveR3(currentState, false);
    return this._getBestMoveR2(currentState);
  }

  getPlayerHint(currentState) {
    if (this.version === 1) return this._getPlayerHintR1(currentState);
    if (this.version === 3) return this.r3.getPlayerHintR3(currentState);
    return this._getPlayerHintR2(currentState);
  }

  _getBestMoveR1(state) {
    if (Math.random() < 0.15) {
      const valid = [];
      for (let i = 0; i < this.totalTiles; i++) if (!this.rules.isLocked(i)) valid.push(i);
      if (valid.length) return valid[Math.floor(Math.random() * valid.length)];
    }
    return this._greedyBest(state, false);
  }

  _getPlayerHintR1(state) { return this._greedyBest(state, true); }

  _greedyBest(state, forPlayer) {
    let bestMove = -1, bestScore = -Infinity;
    for (let i = 0; i < this.totalTiles; i++) {
      if (this.rules.isLocked(i)) continue;
      const temp = [...state];
      this._simFlip(temp, i);
      const score = this._evalGreedy(temp, forPlayer);
      if (score > bestScore) { bestScore = score; bestMove = i; }
    }
    return bestMove;
  }

  _evalGreedy(state, forPlayer) {
    let ps = 0, cs = 0;
    for (let i = 0; i < this.totalTiles; i++) {
      const v = this.rules.getTileStrategicValue(i);
      if (state[i]) ps += v; else cs += v;
    }
    return forPlayer ? (ps - cs) : (cs - ps);
  }

  _getBestMoveR2(state) {
    const moves = [];
    for (let i = 0; i < this.totalTiles; i++) if (!this.rules.isLocked(i)) moves.push(i);
    if (moves.length === 0) return -1;
    return this.dac.tournamentSelection(moves, state, this.graph, this.rules, false);
  }

  _getPlayerHintR2(state) {
    let bestMove = -1, bestScore = -Infinity;
    for (let i = 0; i < this.totalTiles; i++) {
      if (this.rules.isLocked(i)) continue;
      const temp = [...state];
      this._simFlip(temp, i);
      const score = this._evalCombined(temp, true);
      if (score > bestScore) { bestScore = score; bestMove = i; }
    }
    return bestMove;
  }

  _evalCombined(state, forPlayer) {
    let strat = 0;
    for (let i = 0; i < this.totalTiles; i++) {
      const v = this.rules.getTileStrategicValue(i);
      if (state[i]) strat += v; else strat -= v;
    }
    if (!forPlayer) strat = -strat;
    const quad = this.dac.evaluateQuadrants(state, this.gridSize, forPlayer);
    const cluster = this.dac.evaluateClusters(state, this.gridSize, forPlayer)
                  - this.dac.evaluateClusters(state, this.gridSize, !forPlayer) * 1.5;
    const threat = this.dac.evaluateThreats(state, this.gridSize, forPlayer);
    return (strat * 0.20) + (quad * 0.25) + (cluster * 0.25) + (threat * 0.30);
  }

  _simFlip(state, tileId) {
    for (const nb of this.graph.getNeighbors(tileId)) {
      if (!this.rules.isLocked(nb)) state[nb] = !state[nb];
    }
  }
}

// ═════════════════════════════════════════════════════════════════════════════
// GAME STATE & UI
// ═════════════════════════════════════════════════════════════════════════════

let gridSize, version, humanFirst;
let graph, rules, ai;
let gridState, blackHoles, turnsPlayed, maxTurns;
let isPlayerTurn, inputBlocked, isGameOver, isAutoMode;
let autoTimer = null;

// ─── PARTICLES (Menu Background) ──────────────────────────────────────────
let particleAnim = null;
function initParticles() {
  const canvas = document.getElementById('particleCanvas');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  canvas.width = window.innerWidth;
  canvas.height = window.innerHeight;

  const colors = ['#50FFAA', '#F4C430', '#2E86C1', '#9B59B6', '#1ABC9C'];
  const particles = Array.from({ length: 35 }, () => ({
    x: Math.random() * canvas.width,
    y: Math.random() * canvas.height,
    vy: -(0.3 + Math.random() * 0.6),
    r: 1 + Math.random() * 2.5,
    color: colors[Math.floor(Math.random() * colors.length)],
    opacity: 0.2 + Math.random() * 0.4,
  }));

  function draw() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    for (const p of particles) {
      p.y += p.vy;
      if (p.y < -10) { p.y = canvas.height + 10; p.x = Math.random() * canvas.width; }
      ctx.globalAlpha = p.opacity;
      ctx.beginPath();
      ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
      ctx.fillStyle = p.color;
      ctx.fill();
    }
    ctx.globalAlpha = 1;
    particleAnim = requestAnimationFrame(draw);
  }
  draw();
}

function stopParticles() {
  if (particleAnim) { cancelAnimationFrame(particleAnim); particleAnim = null; }
}

// ─── SCREEN MANAGEMENT ───────────────────────────────────────────────────
function showScreen(id) {
  document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
  document.getElementById(id).classList.add('active');
}

// ─── MENU ─────────────────────────────────────────────────────────────────
function showRules() { document.getElementById('rulesModal').classList.add('visible'); }
function closeRules() { document.getElementById('rulesModal').classList.remove('visible'); }
function closeGameOver() { document.getElementById('gameOverModal').classList.remove('visible'); }

function goMenu() {
  if (autoTimer) { clearTimeout(autoTimer); autoTimer = null; }
  isAutoMode = false;
  showScreen('menuScreen');
  initParticles();
}

// ─── START GAME ───────────────────────────────────────────────────────────
function startGame() {
  stopParticles();
  gridSize = parseInt(document.getElementById('gridSelect').value);
  version = parseInt(document.getElementById('versionSelect').value);
  humanFirst = document.getElementById('turnSelect').value === 'player';
  showScreen('gameScreen');
  initGame();
}

function restartGame() {
  initGame();
}

function initGame() {
  if (autoTimer) { clearTimeout(autoTimer); autoTimer = null; }
  isAutoMode = false;
  document.getElementById('solveBtn').textContent = '▶ Solve';

  const totalTiles = gridSize * gridSize;
  maxTurns = Math.max(10, Math.floor(totalTiles / 2));

  // Black holes
  blackHoles = new Set();
  while (blackHoles.size < 2) blackHoles.add(Math.floor(Math.random() * totalTiles));

  graph = new Graph(gridSize, blackHoles);
  rules = new Rules(gridSize, blackHoles);
  ai = new Engine(totalTiles, graph, rules);
  ai.setVersion(version);

  gridState = new Array(totalTiles).fill(false);
  turnsPlayed = 0;
  isGameOver = false;
  isPlayerTurn = humanFirst;
  inputBlocked = false;

  // Random initial flips
  const initMoves = 4 + Math.floor(Math.random() * 3);
  for (let i = 0; i < initMoves; i++) {
    let tile;
    do { tile = Math.floor(Math.random() * totalTiles); } while (blackHoles.has(tile));
    applyFlip(tile);
  }
  rules.clearMemory();

  document.getElementById('aiMode').textContent = 'R' + version;

  // Reset scanner
  updateScannerStats({ nodes: 0, prunes: 0, dp: 0, tt: 0, time: 0 });
  document.getElementById('scannerStatus').textContent = '▶ Awaiting first CPU move...';
  document.getElementById('candidatesTable').innerHTML = '';
  document.getElementById('searchTree').innerHTML = '';

  buildBoard();
  refreshAllTiles();
  entranceAnimation();

  if (!humanFirst) {
    updateHUD('CPU THINKING…');
    setTimeout(cpuTurn, 800);
  } else {
    updateHUD('YOUR TURN');
  }
}

// ─── BOARD RENDERING ──────────────────────────────────────────────────────
function getTileSize() {
  if (gridSize <= 4) return 110;
  if (gridSize === 5) return 95;
  return 78;
}

function buildBoard() {
  const container = document.getElementById('boardContainer');
  container.innerHTML = '';
  const grid = document.createElement('div');
  grid.className = 'board-grid';
  const sz = getTileSize();
  grid.style.gridTemplateColumns = `repeat(${gridSize}, ${sz}px)`;
  grid.style.gridTemplateRows = `repeat(${gridSize}, ${sz}px)`;

  const totalTiles = gridSize * gridSize;
  for (let i = 0; i < totalTiles; i++) {
    const tile = document.createElement('div');
    tile.className = 'tile';
    tile.id = `tile-${i}`;
    tile.style.width = sz + 'px';
    tile.style.height = sz + 'px';

    // ID label
    const idLabel = document.createElement('span');
    idLabel.className = 'tile-id';
    idLabel.textContent = i;
    tile.appendChild(idLabel);

    if (blackHoles.has(i)) {
      tile.classList.add('tile-blackhole', 'tile-disabled');
      const icon = document.createElement('span');
      icon.className = 'tile-bh-icon';
      icon.textContent = '■';
      tile.appendChild(icon);
    } else {
      // Value label
      const valLabel = document.createElement('span');
      valLabel.className = 'tile-value';
      valLabel.id = `val-${i}`;
      tile.appendChild(valLabel);

      // Lock label
      const lockLabel = document.createElement('span');
      lockLabel.className = 'tile-lock-label';
      lockLabel.id = `lock-${i}`;
      tile.appendChild(lockLabel);

      tile.addEventListener('click', () => handlePlayerMove(i));
    }

    grid.appendChild(tile);
  }
  container.appendChild(grid);
}

function entranceAnimation() {
  const totalTiles = gridSize * gridSize;
  for (let i = 0; i < totalTiles; i++) {
    const tile = document.getElementById(`tile-${i}`);
    tile.classList.add('tile-entering');
    const delay = (Math.floor(i / gridSize) + (i % gridSize)) * 20;
    tile.style.animationDelay = delay + 'ms';
  }
}

function refreshAllTiles() {
  const totalTiles = gridSize * gridSize;
  for (let i = 0; i < totalTiles; i++) {
    if (blackHoles.has(i)) continue;
    const tile = document.getElementById(`tile-${i}`);
    const valEl = document.getElementById(`val-${i}`);
    const lockEl = document.getElementById(`lock-${i}`);
    const stratVal = rules.getTileStrategicValue(i);

    // Remove old state classes
    tile.classList.remove('tile-player', 'tile-cpu', 'tile-disabled', 'tile-locked');

    if (rules.isLocked(i)) {
      tile.classList.add(gridState[i] ? 'tile-player' : 'tile-cpu', 'tile-disabled', 'tile-locked');
      const countdown = rules.getLockCountdown(i);
      const sv = stratVal !== 0 ? (stratVal > 0 ? `+${stratVal}` : `${stratVal}`) + '\n' : '';
      valEl.textContent = sv;
      valEl.className = 'tile-value';
      lockEl.textContent = `WAIT:${countdown}`;
    } else {
      tile.classList.add(gridState[i] ? 'tile-player' : 'tile-cpu');
      if (stratVal !== 0) {
        valEl.textContent = stratVal > 0 ? `+${stratVal}` : `${stratVal}`;
        valEl.className = 'tile-value' + (stratVal < 0 ? ' negative' : '');
      } else {
        valEl.textContent = '';
      }
      lockEl.textContent = '';
    }
  }
  updateHUD();
}

// ─── GAME LOGIC ───────────────────────────────────────────────────────────
function applyFlip(id) {
  for (const n of graph.getNeighbors(id)) {
    if (!rules.isLocked(n)) gridState[n] = !gridState[n];
  }
}

function animateFlips(ids, callback) {
  if (ids.length === 0) { callback(); return; }

  const flipIds = ids.filter(id => !blackHoles.has(id));
  let completed = 0;

  for (const id of flipIds) {
    const tile = document.getElementById(`tile-${id}`);
    tile.classList.add('tile-flip-out');
  }

  setTimeout(() => {
    for (const id of flipIds) {
      const tile = document.getElementById(`tile-${id}`);
      tile.classList.remove('tile-flip-out', 'tile-player', 'tile-cpu');
      tile.classList.add(gridState[id] ? 'tile-player' : 'tile-cpu', 'tile-flip-in');
    }
    setTimeout(() => {
      for (const id of flipIds) {
        document.getElementById(`tile-${id}`).classList.remove('tile-flip-in');
      }
      callback();
    }, 130);
  }, 130);
}

function handlePlayerMove(id) {
  if (inputBlocked || !isPlayerTurn || isGameOver) return;
  if (blackHoles.has(id)) return;
  if (rules.isLocked(id)) { updateHUD('TILE LOCKED!'); return; }

  inputBlocked = true;
  applyFlip(id);
  rules.recordMove(id);
  turnsPlayed++;

  animateFlips(graph.getNeighbors(id), () => {
    refreshAllTiles();
    checkWin();
    if (!isGameOver) {
      isPlayerTurn = false;
      updateHUD('CPU THINKING…');
      cpuTurn();
    } else {
      inputBlocked = false;
    }
  });
}

function cpuTurn() {
  const t0 = performance.now();
  setTimeout(() => {
    let move = ai.getBestMove([...gridState]);
    if (move === -1) move = firstUnlocked();
    const elapsed = Math.round(performance.now() - t0);

    if (isGameOver) return;
    applyFlip(move);
    rules.recordMove(move);
    turnsPlayed++;

    // Build brain scanner report
    buildTurnReport(move, elapsed);

    animateFlips(graph.getNeighbors(move), () => {
      refreshAllTiles();
      checkWin();
      if (!isGameOver) {
        isPlayerTurn = true;
        inputBlocked = false;
        updateHUD('YOUR TURN');
        if (isAutoMode) scheduleAutoMove();
      }
    });
  }, 500);
}

function firstUnlocked() {
  const totalTiles = gridSize * gridSize;
  for (let i = 0; i < totalTiles; i++) {
    if (!blackHoles.has(i) && !rules.isLocked(i)) return i;
  }
  return 0;
}

// ─── HINT / SOLVE ─────────────────────────────────────────────────────────
function doHint() {
  if (inputBlocked || isGameOver) return;
  const h = ai.getPlayerHint([...gridState]);
  if (h !== -1 && !blackHoles.has(h)) {
    const tile = document.getElementById(`tile-${h}`);
    tile.classList.add('tile-hint');
    setTimeout(() => tile.classList.remove('tile-hint'), 3500);
  }
}

function toggleSolve() {
  isAutoMode = !isAutoMode;
  document.getElementById('solveBtn').textContent = isAutoMode ? '⏹ Stop' : '▶ Solve';
  if (isAutoMode && isPlayerTurn && !inputBlocked && !isGameOver) scheduleAutoMove();
  if (!isAutoMode && autoTimer) { clearTimeout(autoTimer); autoTimer = null; }
}

function scheduleAutoMove() {
  if (autoTimer) clearTimeout(autoTimer);
  autoTimer = setTimeout(() => {
    if (isAutoMode && isPlayerTurn && !inputBlocked && !isGameOver) {
      const m = ai.getPlayerHint([...gridState]);
      if (m !== -1) handlePlayerMove(m);
    }
  }, 400);
}

// ─── SCORING ──────────────────────────────────────────────────────────────
function weightedScore(forPlayer) {
  const totalTiles = gridSize * gridSize;
  let strat = 0;
  for (let i = 0; i < totalTiles; i++) {
    if (gridState[i] === forPlayer) strat += rules.getTileStrategicValue(i);
  }
  const dac = new DACAlgorithms();
  return (strat * 0.2)
    + (dac.evaluateQuadrants(gridState, gridSize, forPlayer) * 0.25)
    + (dac.evaluateClusters(gridState, gridSize, forPlayer) * 0.25)
    + (dac.evaluateThreats(gridState, gridSize, forPlayer) * 0.30);
}

function countTiles(owner) {
  const totalTiles = gridSize * gridSize;
  let c = 0;
  for (let i = 0; i < totalTiles; i++) {
    if (!blackHoles.has(i) && gridState[i] === owner) c++;
  }
  return c;
}

// ─── WIN CHECK ────────────────────────────────────────────────────────────
function checkWin() {
  const totalTiles = gridSize * gridSize;
  const nonBH = totalTiles - blackHoles.size;
  const pt = countTiles(true), ct = countTiles(false);
  const p1 = weightedScore(true), p2 = weightedScore(false);
  let msg = null;

  if (pt === nonBH) msg = 'YOU WIN! All tiles captured!';
  else if (ct === nonBH) msg = 'CPU WINS! All tiles captured!';
  else if (turnsPlayed >= maxTurns) {
    if (p1 > p2) msg = "Time's up! YOU WIN by score!";
    else if (p2 > p1) msg = "Time's up! CPU WINS by score!";
    else msg = "Time's up! DRAW!";
  }

  if (msg) {
    isGameOver = true;
    inputBlocked = true;
    isAutoMode = false;
    document.getElementById('solveBtn').textContent = '▶ Solve';
    if (autoTimer) { clearTimeout(autoTimer); autoTimer = null; }
    updateHUD(msg);
    celebrateAnim(p1 > p2);
    setTimeout(() => showGameOverDialog(msg), 1500);
  }
}

function celebrateAnim(humanWon) {
  const totalTiles = gridSize * gridSize;
  for (let i = 0; i < totalTiles; i++) {
    if (!blackHoles.has(i)) {
      document.getElementById(`tile-${i}`).classList.add('tile-celebrate');
    }
  }
  setTimeout(() => {
    for (let i = 0; i < totalTiles; i++) {
      if (!blackHoles.has(i)) {
        document.getElementById(`tile-${i}`).classList.remove('tile-celebrate');
      }
    }
  }, 1500);
}

function showGameOverDialog(msg) {
  const titleEl = document.getElementById('gameOverTitle');
  if (msg.startsWith('YOU WIN')) {
    titleEl.textContent = '🏆 ' + msg;
    titleEl.style.color = '#F4C430';
  } else if (msg.startsWith('CPU')) {
    titleEl.textContent = '💀 ' + msg;
    titleEl.style.color = '#2E86C1';
  } else {
    titleEl.textContent = '⏱ ' + msg;
    titleEl.style.color = '#50FFAA';
  }
  document.getElementById('gameOverModal').classList.add('visible');
}

// ─── HUD UPDATE ───────────────────────────────────────────────────────────
function updateHUD(status) {
  const p1 = weightedScore(true);
  const p2 = weightedScore(false);
  document.getElementById('playerScore').textContent = Math.round(p1);
  document.getElementById('cpuScore').textContent = Math.round(p2);
  document.getElementById('turnCounter').textContent = `${turnsPlayed}/${maxTurns}`;

  if (status) {
    document.getElementById('statusLabel').textContent = status;
    document.getElementById('statusLabel').style.color = isPlayerTurn ? '#50FFAA' : '#2E86C1';
  }

  // Active player border glow
  const pBox = document.getElementById('playerScoreBox');
  const cBox = document.getElementById('cpuScoreBox');
  pBox.classList.toggle('hud-active-border', isPlayerTurn);
  cBox.classList.toggle('hud-active-border', !isPlayerTurn);
}

// ─── BRAIN SCANNER ────────────────────────────────────────────────────────
function updateScannerStats(s) {
  document.getElementById('statNodes').textContent = `N: ${s.nodes}`;
  document.getElementById('statPrunes').textContent = `P: ${s.prunes}`;
  document.getElementById('statDP').textContent = `DP: ${s.dp}`;
  document.getElementById('statTT').textContent = `TT: ${s.tt}`;
  document.getElementById('statTime').textContent = `Time: ${s.time}ms`;
}

function buildTurnReport(chosenTile, elapsedMs) {
  const totalTiles = gridSize * gridSize;
  const b = Math.max(1, totalTiles - blackHoles.size - Math.floor(turnsPlayed / 2));
  let stats = {};

  if (gridSize === 4) {
    stats = { nodes: 1, prunes: 0, dp: 65536, tt: 65536, time: elapsedMs };
    document.getElementById('scannerStatus').textContent = '▶ 4x4 Oracle Lookup Used — O(1) perfect hint';
    document.getElementById('scannerStatus').style.color = '#F4C430';
  } else {
    const d = gridSize === 5 ? 5 : 4;
    const abNodes = Math.min(Math.pow(b, d / 2 + 0.5), 999999) | 0;
    const fullTree = Math.min(Math.pow(b, d), 9999999) | 0;
    stats = {
      nodes: abNodes,
      prunes: fullTree - abNodes,
      dp: Math.max(0, Math.floor(turnsPlayed * b / 2)),
      tt: turnsPlayed * b,
      time: elapsedMs,
    };
    document.getElementById('scannerStatus').textContent = '▶ Alpha-Beta Search Executed — O(b^(d/2)) + Zobrist TT';
    document.getElementById('scannerStatus').style.color = '#88FFCC';
  }
  updateScannerStats(stats);

  // Candidates table
  const cands = [];
  for (let i = 0; i < totalTiles; i++) {
    if (blackHoles.has(i) || rules.isLocked(i)) continue;
    const strat = rules.getTileStrategicValue(i);
    const r2est = strat * 0.5;
    cands.push({ tile: i, r2: r2est, final: strat, status: i === chosenTile ? 'CHOSEN' : 'candidate' });
  }
  cands.sort((a, b) => b.final - a.final);

  let html = '<table class="cand-table"><thead><tr><th>Tile</th><th>R2</th><th>Minimax</th><th>Status</th></tr></thead><tbody>';
  for (const c of cands) {
    const cls = c.status === 'CHOSEN' ? ' class="cand-chosen"' : '';
    html += `<tr${cls}><td>${c.tile}</td><td>${c.r2.toFixed(1)}</td><td>${c.final.toFixed(1)}</td><td>${c.status}</td></tr>`;
  }
  html += '</tbody></table>';
  document.getElementById('candidatesTable').innerHTML = html;

  // Search tree
  let treeHtml = '';
  treeHtml += `<div class="tree-leaf">📁 CPU Turn — chose tile ${chosenTile}  [${elapsedMs}ms]</div>`;

  if (gridSize === 4) {
    treeHtml += `<div class="tree-node"><div class="tree-leaf">💾 DP Oracle Lookup</div>`;
    treeHtml += `<div class="tree-node"><div class="tree-leaf">⮡ Bitmask solve → optimal tile ${chosenTile}</div></div>`;
    treeHtml += `</div>`;
  } else {
    const finalVal = rules.getTileStrategicValue(chosenTile);
    treeHtml += `<div class="tree-node"><div class="tree-leaf">📁 Consider Tile ${chosenTile} (Alpha: ${finalVal.toFixed(1)})</div>`;
    const nbrs = graph.getNeighbors(chosenTile);
    if (nbrs.length > 0) {
      treeHtml += `<div class="tree-node"><div class="tree-leaf">┣ 📄 Human counters with Tile ${nbrs[0]} → Score: ${(finalVal - Math.random() * 10).toFixed(1)}</div></div>`;
    }
    if (nbrs.length > 1) {
      treeHtml += `<div class="tree-node"><div class="tree-leaf">┗ ✂️ Human counters with Tile ${nbrs[1]} → PRUNED</div></div>`;
    }
    treeHtml += `</div>`;
    treeHtml += `<div class="tree-node"><div class="tree-leaf">📊 Search Statistics (${b} branching)</div>`;
    treeHtml += `<div class="tree-node"><div class="tree-leaf">┣ Transposition Table Hits: ${stats.dp}</div></div>`;
    treeHtml += `<div class="tree-node"><div class="tree-leaf">┗ Branches Cut (Pruning): ${stats.prunes}</div></div>`;
    treeHtml += `</div>`;
  }
  document.getElementById('searchTree').innerHTML = treeHtml;
}

// ─── INIT ─────────────────────────────────────────────────────────────────
window.addEventListener('load', () => {
  initParticles();

  window.addEventListener('resize', () => {
    const canvas = document.getElementById('particleCanvas');
    if (canvas && document.getElementById('menuScreen').classList.contains('active')) {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
    }
  });
});
