package com.flipwars;

import java.awt.*;
import java.util.*;
import javax.swing.*;

/**
 * Main Entry Point and View Manager (UI).
 * <p>
 * Handles the Swing GUI, user interactions, and game loop orchestration.
 * Supports runtime switching between algorithm versions (R1 Greedy vs R2 D&C)
 * to demonstrate improvement across review milestones.
 * </p>
 *
 * <h2>Review 2 Features:</h2>
 * <ul>
 * <li>Version Selector: R1 (Greedy) vs R2 (D&C) vs R3 (Coming Soon)</li>
 * <li>SOLVE button for automated AI vs AI gameplay</li>
 * <li>Visual feedback for hints and CPU moves</li>
 * <li>5 D&C algorithms powering the R2 engine</li>
 * </ul>
 */
public class Main extends JFrame {

    // --- Configuration ---
    private int gridSize = 4;
    private int totalTiles = 16;
    private int maxTurns = 25;

    // --- Colors ---
    private static final Color COLOR_BG = new Color(44, 62, 80);
    private static final Color COLOR_ACCENT = new Color(230, 126, 34);
    private static final Color COLOR_HINT = new Color(46, 204, 113);
    private static final Color COLOR_CPU_MOVE = new Color(231, 76, 60);

    // --- Logic Components ---
    private Graph graph;
    private Engine ai;
    private Rules rules;

    // --- UI State ---
    private boolean[] gridState;
    private boolean isPlayerTurn = true;
    private boolean inputBlocked = false;
    private boolean isGameOver = false;
    private boolean isAutoMode = false;
    private int turnsPlayed = 0;

    /** Selected algorithm version: 1=R1 Greedy, 2=R2 D&C */
    private int selectedVersion = 2;
    /** Timer for auto-solve mode */
    private javax.swing.Timer autoPlayTimer;

    private CardLayout cardLayout = new CardLayout();
    private JPanel mainPanel = new JPanel(cardLayout);
    private TileButton[] tileButtons;
    private JLabel statusLabel, scoreLabel, turnLabel;
    private JPanel gamePanel;
    /** Brain Scanner text area — logs AI decisions in real-time. */
    private JTextArea brainLog;

    public Main() {
        initializeLogic(4);

        setTitle("Flip Wars");
        setSize(700, 900);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        mainPanel.add(createMenuPanel(), "MENU");
        mainPanel.add(new JPanel(), "GAME");
        mainPanel.add(createInstructionsPanel(), "INSTRUCTIONS");

        add(mainPanel);
        cardLayout.show(mainPanel, "MENU");
    }

    private void initializeLogic(int size) {
        this.gridSize = size;
        this.totalTiles = size * size;
        this.maxTurns = (size == 4) ? 15 : 25;
        this.graph = new Graph(gridSize);
        this.rules = new Rules(gridSize);
        this.ai = new Engine(totalTiles, graph, rules);
        this.gridState = new boolean[totalTiles];
        this.tileButtons = new TileButton[totalTiles];
    }

    private void startGame() {
        Arrays.fill(gridState, false);
        rules.clearMemory();
        rules.clearDeadTiles(); // Fresh black holes each game
        turnsPlayed = 0;
        isGameOver = false;
        isPlayerTurn = true;
        inputBlocked = false;
        isAutoMode = false;
        if (autoPlayTimer != null && autoPlayTimer.isRunning())
            autoPlayTimer.stop();

        ai.setVersion(selectedVersion);

        Random rand = new Random();
        int initialMoves = 4 + rand.nextInt(3);
        for (int i = 0; i < initialMoves; i++)
            performFlip(rand.nextInt(totalTiles));
        rules.clearMemory();

        // ---- DYNAMIC OBSTACLES: Spawn 1–2 Black Hole tiles --------------------
        // Corners are protected (highest strategic value) so the game stays fair.
        java.util.Set<Integer> corners = new java.util.HashSet<>(Arrays.asList(
                0, gridSize - 1, (gridSize - 1) * gridSize, totalTiles - 1));
        java.util.List<Integer> candidates = new java.util.ArrayList<>();
        for (int i = 0; i < totalTiles; i++) {
            if (!corners.contains(i))
                candidates.add(i);
        }
        java.util.Collections.shuffle(candidates, rand);
        int numDead = 1 + rand.nextInt(2); // 1 or 2 black holes per game
        for (int i = 0; i < Math.min(numDead, candidates.size()); i++) {
            int id = candidates.get(i);
            rules.addDeadTile(id);
            gridState[id] = false; // Neutral state — neither player owns it
        }

        if (gamePanel != null)
            mainPanel.remove(gamePanel);
        gamePanel = createGamePanel();
        mainPanel.add(gamePanel, "GAME");
        mainPanel.revalidate();
        mainPanel.repaint();

        // Wire Brain Scanner logger into the R3 engine AFTER brainLog is built
        ai.setR3Logger(msg -> logBrain(msg));
        logBrain("=== GAME START ===");
        logBrain("Grid: " + gridSize + "x" + gridSize + "  Version: R" + selectedVersion);
        logBrain("Black Holes: " + rules.getDeadTiles());

        updateBoardUI();
        updateScoreDisplay();
        cardLayout.show(mainPanel, "GAME");
    }

    private void performFlip(int id) {
        for (int neighbor : graph.getNeighbors(id)) {
            // Skip locked AND permanently dead (Black Hole) tiles
            if (!rules.isLocked(neighbor) && !rules.isDeadTile(neighbor)) {
                gridState[neighbor] = !gridState[neighbor];
            }
        }
        Toolkit.getDefaultToolkit().beep();
    }

    private void handlePlayerMove(int id) {
        if (inputBlocked || !isPlayerTurn || isGameOver)
            return;

        // Black Hole: unclickable
        if (rules.isDeadTile(id)) {
            statusLabel.setText("☠ Black Hole — cannot click!");
            statusLabel.setForeground(new Color(120, 60, 60));
            return;
        }

        if (rules.isLocked(id)) {
            statusLabel.setText("Tile Locked!");
            statusLabel.setForeground(Color.RED);
            return;
        }

        performFlip(id);
        rules.recordMove(id);
        turnsPlayed++;

        updateBoardUI();
        updateScoreDisplay();
        checkGameStatus();

        if (!isGameOver) {
            isPlayerTurn = false;
            playCPUTurn();
        } else {
            isAutoMode = false;
        }
    }

    private void playCPUTurn() {
        statusLabel.setText("CPU thinking...");
        statusLabel.setForeground(COLOR_HINT);
        inputBlocked = true;
        logBrain("--- CPU TURN (v" + selectedVersion + ") ---");

        new Thread(() -> {
            try {
                Thread.sleep(1200);
            } catch (Exception ignored) {
            }

            int move = ai.getBestMove(gridState);
            if (move == -1)
                move = 0;
            logBrain("CPU chose tile " + move + " (row=" + (move / gridSize) + ", col=" + (move % gridSize) + ")");

            int finalMove = move;
            SwingUtilities.invokeLater(() -> {
                performFlip(finalMove);
                rules.recordMove(finalMove);
                turnsPlayed++;
                updateBoardUI();
                updateScoreDisplay();
                checkGameStatus();

                if (!isGameOver) {
                    isPlayerTurn = true;
                    inputBlocked = false;
                    statusLabel.setText("Your Turn");
                    if (isAutoMode)
                        triggerAutoMove();
                }
            });
        }).start();
    }

    private void triggerAutoMove() {
        if (!isPlayerTurn || isGameOver || inputBlocked)
            return;

        // SOLVE uses Greedy with combined D&C evaluation (no backtracking)
        int move = ai.getPlayerHint(gridState);
        if (move != -1) {
            handlePlayerMove(move);
        }
    }

    private void checkGameStatus() {
        int yCount = countTiles(true);
        int gCount = countTiles(false);
        double yScore = calculateWeightedScore(true);
        double gScore = calculateWeightedScore(false);
        String msg = null;

        if (yCount == totalTiles)
            msg = "Victorious! Human wins!";
        else if (gCount == totalTiles)
            msg = "Defeat! CPU wins!";
        else if (turnsPlayed >= maxTurns) {
            msg = (yScore > gScore) ? "Time's up! You win by Strategic Points!"
                    : (gScore > yScore) ? "Time's up! CPU wins by Strategic Points!" : "It's a draw!";
        }

        if (msg != null) {
            isGameOver = true;
            statusLabel.setText(msg);
            celebrate(yScore > gScore);

            int choice = JOptionPane.showConfirmDialog(this, msg + "\nPlay again?", "Game Over",
                    JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION)
                startGame();
            else {
                cardLayout.show(mainPanel, "MENU");
            }
        }
    }

    private JPanel createMenuPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(COLOR_BG);

        // ---- Animated title ----
        JLabel title = new JLabel("FLIP WARS");
        title.setFont(new Font("Verdana", Font.BOLD, 64));
        title.setForeground(Rules.COLOR_PLAYER);
        title.setAlignmentX(CENTER_ALIGNMENT);
        new javax.swing.Timer(500, e -> {
            boolean isPlayer = title.getForeground().equals(Rules.COLOR_PLAYER);
            title.setForeground(isPlayer ? COLOR_ACCENT : Rules.COLOR_PLAYER);
        }).start();

        JLabel subTitle = new JLabel("⚔️  A Strategic Duel of Algorithms  ⚔️");
        subTitle.setFont(new Font("Arial", Font.ITALIC, 16));
        subTitle.setForeground(new Color(180, 180, 200));
        subTitle.setAlignmentX(CENTER_ALIGNMENT);

        // ---- Config panel ----
        JPanel configPanel = new JPanel(new GridLayout(2, 2, 10, 8));
        configPanel.setBackground(new Color(36, 50, 65));
        configPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_ACCENT, 1),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)));
        configPanel.setMaximumSize(new Dimension(460, 100));
        configPanel.setAlignmentX(CENTER_ALIGNMENT);

        JLabel sizeLabel = createLbl("Grid Size:", 15, Color.WHITE);
        Integer[] sizes = { 4, 5, 6 };
        JComboBox<Integer> sizeCombo = new JComboBox<>(sizes);
        sizeCombo.setSelectedItem(gridSize);
        sizeCombo.setFont(new Font("Arial", Font.BOLD, 15));
        sizeCombo.setBackground(new Color(25, 35, 50));
        sizeCombo.setForeground(Color.WHITE);
        sizeCombo.addActionListener(e -> initializeLogic((int) sizeCombo.getSelectedItem()));

        JLabel verLabel = createLbl("AI Version:", 15, Color.WHITE);
        String[] versions = { "R1: Greedy (Easy)", "R2: D&C (Medium)", "R3: DP + BT (Hard)" };
        JComboBox<String> verCombo = new JComboBox<>(versions);
        verCombo.setSelectedIndex(selectedVersion - 1);
        verCombo.setFont(new Font("Arial", Font.BOLD, 15));
        verCombo.setBackground(new Color(25, 35, 50));
        verCombo.setForeground(Color.WHITE);
        verCombo.addActionListener(e -> selectedVersion = verCombo.getSelectedIndex() + 1);

        configPanel.add(sizeLabel);
        configPanel.add(sizeCombo);
        configPanel.add(verLabel);
        configPanel.add(verCombo);

        // ---- Version description badge ----
        String[] vDesc = {
                "R1 Greedy  |  Simple tile scoring  |  15% blunder rate  |  Easy to beat",
                "R2 D&C     |  5 divide-and-conquer algorithms  |  Spatial + Cluster + Threat",
                "R3 DP+BT   |  Alpha-Beta + Zobrist Memo + 4x4 Oracle  |  Hardest"
        };
        JLabel vBadge = createLbl(vDesc[selectedVersion - 1], 11, new Color(100, 200, 140));
        vBadge.setAlignmentX(CENTER_ALIGNMENT);
        vBadge.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        verCombo.addActionListener(e -> vBadge.setText(vDesc[verCombo.getSelectedIndex()]));

        // ---- Buttons ----
        JButton btnStart = createBtn("▶  PLAY GAME");
        btnStart.setPreferredSize(new Dimension(260, 52));
        btnStart.setMaximumSize(new Dimension(260, 52));
        btnStart.setAlignmentX(CENTER_ALIGNMENT);
        btnStart.addActionListener(e -> startGame());

        JButton btnIns = createBtn("📚  RULES & CREDITS");
        btnIns.setMaximumSize(new Dimension(260, 48));
        btnIns.setAlignmentX(CENTER_ALIGNMENT);
        btnIns.addActionListener(e -> cardLayout.show(mainPanel, "INSTRUCTIONS"));

        // ---- Footer ----
        JPanel footer = new JPanel(new GridLayout(3, 1));
        footer.setBackground(COLOR_BG);
        footer.setMaximumSize(new Dimension(500, 72));
        footer.setAlignmentX(CENTER_ALIGNMENT);
        footer.add(createLbl("Design & Analysis of Algorithms  |  Team-13", 12, Color.LIGHT_GRAY));
        footer.add(createLbl("Suhas  ·  Maneesh  ·  Ganesh  ·  Balaji", 11, new Color(150, 150, 170)));
        footer.add(createLbl("R1: Greedy  |  R2: D&C  |  R3: DP + Backtracking", 11, new Color(100, 130, 160)));

        p.add(Box.createVerticalGlue());
        p.add(title);
        p.add(Box.createRigidArea(new Dimension(0, 6)));
        p.add(subTitle);
        p.add(Box.createRigidArea(new Dimension(0, 30)));
        p.add(configPanel);
        p.add(Box.createRigidArea(new Dimension(0, 6)));
        p.add(vBadge);
        p.add(Box.createRigidArea(new Dimension(0, 24)));
        p.add(btnStart);
        p.add(Box.createRigidArea(new Dimension(0, 12)));
        p.add(btnIns);
        p.add(Box.createVerticalGlue());
        p.add(footer);
        p.add(Box.createRigidArea(new Dimension(0, 16)));
        return p;
    }

    private JPanel createGamePanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(COLOR_BG);

        // NORTH: Score + Turn + Status
        JPanel top = new JPanel(new GridLayout(3, 1));
        top.setBackground(COLOR_BG);
        scoreLabel = createLbl("Red: 0 | Blue: 0", 24, Color.WHITE);
        turnLabel = createLbl("Turn: 0 / " + maxTurns, 18, COLOR_ACCENT);
        statusLabel = createLbl("Your Turn", 18, COLOR_HINT);
        top.add(scoreLabel);
        top.add(turnLabel);
        top.add(statusLabel);
        p.add(top, BorderLayout.NORTH);

        // CENTER: 3D Tile Grid — GridBagLayout wrapper forces a perfect square
        // so tiles never stretch on widescreen monitors.
        JPanel gridWrapper = new JPanel(new GridBagLayout());
        gridWrapper.setBackground(new Color(106, 219, 36)); // Nintendo Grass Green
        gridWrapper.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel grid = new JPanel(new GridLayout(gridSize, gridSize, 16, 16));
        grid.setOpaque(false); // Let grass green show through tile gaps
        int boardPixelSize = (gridSize == 4) ? 500 : (gridSize == 5) ? 600 : 700;
        // Per-tile preferred size: cleaner than setting it on the whole grid panel
        // and prevents GridBagLayout from shrinking tiles to fit the text label.

        for (int i = 0; i < totalTiles; i++) {
            TileButton b = new TileButton();
            b.setPreferredSize(new Dimension(120, 120)); // Force big, chunky square tiles
            final int id = i;
            b.addActionListener(e -> handlePlayerMove(id));
            tileButtons[i] = b;
            grid.add(b);
        }
        gridWrapper.add(grid); // GridBagLayout centers the fixed-size grid
        p.add(gridWrapper, BorderLayout.CENTER);

        // EAST: Brain Scanner panel — real-time AI decision log
        // Using SwingUtilities.invokeLater() to safely update from background AI
        // threads
        JPanel scannerPanel = new JPanel(new BorderLayout(0, 4));
        scannerPanel.setBackground(new Color(18, 18, 28));
        scannerPanel.setPreferredSize(new Dimension(210, 0));
        scannerPanel.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, COLOR_ACCENT));

        JLabel scanTitle = createLbl("🧠 BRAIN SCANNER", 11, COLOR_ACCENT);
        scanTitle.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
        scannerPanel.add(scanTitle, BorderLayout.NORTH);

        brainLog = new JTextArea();
        brainLog.setBackground(new Color(10, 10, 18));
        brainLog.setForeground(new Color(0, 230, 100)); // terminal green
        brainLog.setFont(new Font("Consolas", Font.PLAIN, 10));
        brainLog.setEditable(false);
        brainLog.setLineWrap(true);
        brainLog.setWrapStyleWord(true);
        brainLog.setMargin(new Insets(4, 6, 4, 6));

        JScrollPane brainScroll = new JScrollPane(brainLog);
        brainScroll.setBorder(null);
        brainScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scannerPanel.add(brainScroll, BorderLayout.CENTER);
        p.add(scannerPanel, BorderLayout.EAST);

        // SOUTH: Control buttons
        JPanel bot = new JPanel();
        bot.setBackground(COLOR_BG);
        JButton bh = createBtn("Get Hint");
        bh.addActionListener(e -> {
            int hint = ai.getPlayerHint(gridState);
            if (hint != -1) {
                logBrain("[HINT] Suggested tile " + hint);
                tileButtons[hint].setBorder(BorderFactory.createLineBorder(COLOR_HINT, 4));
            }
        });

        JButton bs = createBtn("SOLVE");
        bs.addActionListener(e -> {
            if (isGameOver)
                return;
            isAutoMode = !isAutoMode;
            bs.setText(isAutoMode ? "STOP" : "SOLVE");
            bs.setBackground(isAutoMode ? Color.RED : COLOR_ACCENT);
            if (isAutoMode) {
                String vLabel = ai.getVersion() == 1 ? "R1 Greedy"
                        : ai.getVersion() == 3 ? "R3 DP+BT" : "R2 D&C";
                statusLabel.setText("Auto-Solving... (" + vLabel + ")");
                if (autoPlayTimer == null) {
                    autoPlayTimer = new javax.swing.Timer(600, evt -> {
                        if (isGameOver || !isAutoMode) {
                            isAutoMode = false;
                            if (autoPlayTimer != null)
                                autoPlayTimer.stop();
                            bs.setText("SOLVE");
                            bs.setBackground(COLOR_ACCENT);
                            return;
                        }
                        if (isPlayerTurn && !inputBlocked)
                            triggerAutoMove();
                    });
                }
                autoPlayTimer.start();
                if (isPlayerTurn && !inputBlocked)
                    triggerAutoMove();
            } else {
                if (autoPlayTimer != null)
                    autoPlayTimer.stop();
            }
        });

        JButton bm = createBtn("Menu");
        bm.addActionListener(e -> cardLayout.show(mainPanel, "MENU"));
        bot.add(bh);
        bot.add(bs);
        bot.add(bm);
        p.add(bot, BorderLayout.SOUTH);
        return p;
    }

    private JPanel createInstructionsPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(COLOR_BG);

        JLabel header = createLbl("GAME MANUAL & ALGORITHMS", 24, Rules.COLOR_PLAYER);
        header.setBorder(BorderFactory.createEmptyBorder(16, 0, 8, 0));
        p.add(header, BorderLayout.NORTH);

        JTextArea t = new JTextArea();
        t.setBackground(new Color(30, 40, 54));
        t.setForeground(new Color(220, 220, 230));
        t.setFont(new Font("Consolas", Font.PLAIN, 13));
        t.setEditable(false);
        t.setLineWrap(true);
        t.setWrapStyleWord(true);
        t.setMargin(new Insets(16, 24, 16, 24));

        t.setText(
                "================================================\n" +
                        "  HOW TO PLAY\n" +
                        "================================================\n" +
                        " 1. OBJECTIVE  : Turn ALL tiles YELLOW — or have the\n" +
                        "                 highest Strategic Score at turn limit.\n\n" +
                        " 2. FLIP LOGIC : Clicking any tile flips it AND all 4\n" +
                        "                 orthogonal neighbors in a PLUS pattern.\n\n" +
                        " 3. LOCK TIMER : Clicked tiles show WAIT:N countdown.\n" +
                        "                 Locked tiles cannot be re-clicked yet.\n\n" +
                        " 4. BLACK HOLES: Dark tiles appear randomly each game.\n" +
                        "                 Cannot be clicked, flipped, or owned.\n" +
                        "                 Proves Graph + DFS handle irregular grids!\n\n" +
                        "================================================\n" +
                        "  AI VERSIONS\n" +
                        "================================================\n\n" +
                        " R1 GREEDY (Easy)\n" +
                        "   Tile-value counting. 15% blunder. Merge Sort O(n log n)\n\n" +
                        " R2 DIVIDE & CONQUER (Medium)\n" +
                        "   1. Merge Sort       O(n log n) - Player Hints\n" +
                        "   2. Spatial D&C      O(n)       - Quadrant scoring 25%\n" +
                        "   3. DFS Clusters     O(V+E)     - Territory scoring 25%\n" +
                        "   4. Tournament Sel.  O(n)       - CPU move selection\n" +
                        "   5. Threat Detection O(n)       - Vulnerability 30%\n\n" +
                        " R3 DP + BACKTRACKING (Hard) - State-Space Search Engine\n" +
                        "   +- SUHAS   Pure Backtracking doMove/undoMove\n" +
                        "   |          XOR flip is self-inverse: undo = redo\n" +
                        "   +- MANEESH Alpha-Beta Pruning Minimax + D&C ordering\n" +
                        "   |          Dynamic depth: 6 (4x4) / 4 (5x5) / 3 (6x6)\n" +
                        "   +- GANESH  Zobrist Transposition Table (top-down DP)\n" +
                        "   |          Hash includes tile state + Tabu lock state\n" +
                        "   +- BALAJI  Bitmask DP Oracle (4x4 only)\n" +
                        "              BFS over all 65,536 states at startup\n" +
                        "              Hint lookup becomes O(1) — optimal move!\n\n" +
                        "================================================\n" +
                        "  BRAIN SCANNER (right panel during R3)\n" +
                        "================================================\n" +
                        " [TT HIT]  Transposition table cache hit (Ganesh)\n" +
                        " [a-b CUT] Branch pruned, subtree skipped (Maneesh)\n" +
                        " [ORACLE]  4x4 BFS exact lookup used (Balaji)\n" +
                        " [HINT]    Suggested best player move\n\n" +
                        " Thread Safety: AI runs on background thread.\n" +
                        " SwingUtilities.invokeLater() marshals log messages\n" +
                        " safely back to the Event Dispatch Thread (EDT).\n\n" +
                        "================================================\n" +
                        "  SCORING VALUES\n" +
                        "================================================\n" +
                        " Corners  : +25  Edges: +15  Standard: +5  Traps: -5\n\n" +
                        "================================================\n" +
                        "  WINNING STRATEGIES\n" +
                        "================================================\n" +
                        " * Secure corners early — highest value tiles!\n" +
                        " * Build large connected clusters for territory bonuses\n" +
                        " * Avoid trap tiles — they expose high-value corners\n" +
                        " * Route plays around Black Holes to force asymmetry\n" +
                        " * Watch the Brain Scanner to predict the CPU's plan\n\n" +
                        "  Team-13 | Suhas | Maneesh | Ganesh | Balaji");

        JScrollPane scroll = new JScrollPane(t);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(60, 80, 100), 1));
        scroll.getViewport().setBackground(new Color(30, 40, 54));
        p.add(scroll, BorderLayout.CENTER);

        JButton b = createBtn("< RETURN TO MENU");
        b.addActionListener(e -> cardLayout.show(mainPanel, "MENU"));
        p.add(b, BorderLayout.SOUTH);

        return p;
    }

    private void updateBoardUI() {
        for (int i = 0; i < totalTiles; i++) {
            // ---- BLACK HOLE TILE: render as an unowned dark pit ----
            if (rules.isDeadTile(i)) {
                tileButtons[i].setBackground(new Color(15, 10, 10));
                tileButtons[i].setText("⚫"); // solid black circle
                tileButtons[i].setForeground(new Color(90, 20, 20));
                tileButtons[i].setFont(new Font("Arial", Font.BOLD, 22));
                tileButtons[i].setBorder(BorderFactory.createLineBorder(new Color(80, 0, 0), 2));
                continue;
            }

            boolean isLocked = rules.isLocked(i);
            Color baseColor = gridState[i] ? Rules.COLOR_PLAYER : Rules.COLOR_CPU;
            double weight = rules.getTileStrategicValue(i);

            if (isLocked) {
                // Dim the tile color when locked (Tabu Search)
                Color dimColor = new Color(
                        baseColor.getRed() / 2,
                        baseColor.getGreen() / 2,
                        baseColor.getBlue() / 2);
                tileButtons[i].setBackground(dimColor);
                int countdown = rules.getLockCountdown(i);
                String scoreText = (weight != 0) ? ((weight > 0 ? "+" : "") + (int) weight) : "";
                tileButtons[i].setText("<html><center>" + scoreText
                        + "<br><font color='red'><b>WAIT:" + countdown
                        + "</b></font></center></html>");
                tileButtons[i].setForeground(Color.WHITE);
                tileButtons[i].setFont(new Font("Arial", Font.BOLD, 14));
                tileButtons[i].setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
            } else {
                tileButtons[i].setBackground(baseColor);
                if (weight != 0) {
                    tileButtons[i].setText((weight > 0 ? "+" : "") + (int) weight);
                    tileButtons[i].setForeground(weight > 0 ? Color.WHITE : new Color(255, 150, 150));
                    tileButtons[i].setFont(new Font("Arial", Font.BOLD, 12));
                } else {
                    tileButtons[i].setText("");
                }
                tileButtons[i].setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
            }
        }
    }

    /**
     * Thread-safe Brain Scanner logging.
     *
     * <p>
     * This method can be called from ANY thread (including the R3 AI background
     * thread). {@code SwingUtilities.invokeLater()} queues the UI update safely
     * back onto the Event Dispatch Thread (EDT), preventing UI freezing and
     * thread collisions.
     * </p>
     *
     * <p>
     * Panel explanation: "Our AI search runs on asynchronous background threads.
     * To safely visualise execution in the Brain Scanner without freezing the UI
     * or causing thread collisions, we use SwingUtilities.invokeLater() to marshal
     * log messages back to the Event Dispatch Thread."
     * </p>
     *
     * @param msg Log message from any algorithm (R1/R2/R3)
     */
    private void logBrain(String msg) {
        SwingUtilities.invokeLater(() -> {
            if (brainLog != null) {
                brainLog.append(msg + "\n");
                // Auto-scroll to bottom so latest event is always visible
                brainLog.setCaretPosition(brainLog.getDocument().getLength());
            }
        });
    }

    private void updateScoreDisplay() {
        double yScore = calculateWeightedScore(true);
        double gScore = calculateWeightedScore(false);
        scoreLabel.setText(String.format("Red: %.1f | Blue: %.1f", yScore, gScore));
        turnLabel.setText("Turn: " + turnsPlayed + " / " + maxTurns);
    }

    private int countTiles(boolean isYellow) {
        int c = 0;
        for (boolean s : gridState)
            if (s == isYellow)
                c++;
        return c;
    }

    private double calculateWeightedScore(boolean isYellow) {
        if (ai.getVersion() == 1) {
            // R1: Simple tile value counting
            double score = 0;
            for (int i = 0; i < totalTiles; i++) {
                if (gridState[i] == isYellow) {
                    score += rules.getTileStrategicValue(i);
                }
            }
            return score;
        }

        // R2: D&C weighted scoring
        double strategic = 0;
        for (int i = 0; i < totalTiles; i++) {
            if (gridState[i] == isYellow) {
                strategic += rules.getTileStrategicValue(i);
            }
        }

        DACAlgorithms dac = new DACAlgorithms();
        double quadrant = dac.evaluateQuadrants(gridState, gridSize, isYellow);
        double cluster = dac.evaluateClusters(gridState, gridSize, isYellow);
        double threat = dac.evaluateThreats(gridState, gridSize, isYellow);

        return (strategic * 0.2) + (quadrant * 0.25) + (cluster * 0.25) + (threat * 0.3);
    }

    private void celebrate(boolean human) {
        javax.swing.Timer t = new javax.swing.Timer(150, e -> {
            for (int i = 0; i < totalTiles; i++) {
                tileButtons[i].setBackground(new Random().nextBoolean() ? Color.WHITE
                        : (human ? Rules.COLOR_PLAYER : Rules.COLOR_CPU));
            }
        });
        t.start();
        new javax.swing.Timer(2000, e -> t.stop()).start();
    }

    private JButton createBtn(String txt) {
        JButton b = new JButton(txt);
        b.setBackground(COLOR_ACCENT);
        b.setForeground(Color.WHITE);
        b.setFont(new Font("Arial", Font.BOLD, 16));
        b.setAlignmentX(CENTER_ALIGNMENT);
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.BLACK, 1),
                BorderFactory.createEmptyBorder(10, 25, 10, 25)));

        b.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) {
                b.setBackground(Rules.COLOR_PLAYER);
                b.setForeground(Color.BLACK);
            }

            public void mouseExited(java.awt.event.MouseEvent e) {
                b.setBackground(COLOR_ACCENT);
                b.setForeground(Color.WHITE);
            }
        });
        return b;
    }

    private JLabel createLbl(String txt, int size, Color c) {
        JLabel l = new JLabel(txt, SwingConstants.CENTER);
        l.setFont(new Font("Monospaced", Font.BOLD, size));
        l.setForeground(c);
        return l;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}