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

    /** Selected algorithm version: 1=R1 Greedy, 2=R2 D&C, 3=R3 DP+BT */
    private int selectedVersion = 2;
    /** Timer for auto-solve mode */
    private javax.swing.Timer autoPlayTimer;
    /** Black Hole tile IDs — unclickable, unownable, graphically void. */
    private Set<Integer> blackHoles = new HashSet<>();
    /** Brain Scanner text area — receives real-time AI log messages. */
    private JTextArea brainLog;

    private CardLayout cardLayout = new CardLayout();
    private JPanel mainPanel = new JPanel(cardLayout);
    private JButton[] tileButtons;
    private JLabel statusLabel, scoreLabel, turnLabel;
    private JPanel gamePanel;

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
        this.blackHoles = new HashSet<>(); // Reset BH for new grid size
        this.graph = new Graph(gridSize, blackHoles);
        this.rules = new Rules(gridSize, blackHoles);
        this.ai = new Engine(totalTiles, graph, rules);
        this.gridState = new boolean[totalTiles];
        this.tileButtons = new JButton[totalTiles];
    }

    private void startGame() {
        // ── 1. Generate Black Holes ──────────────────────────────────────────
        blackHoles = new HashSet<>();
        Random rand = new Random();
        while (blackHoles.size() < 2) {
            blackHoles.add(rand.nextInt(totalTiles));
        }

        // ── 2. Rebuild Graph + Rules + Engine with BH topology ───────────────
        this.graph = new Graph(gridSize, blackHoles);
        this.rules = new Rules(gridSize, blackHoles);
        this.ai = new Engine(totalTiles, graph, rules, this::logBrain);
        ai.setVersion(selectedVersion);

        // ── 3. Reset game state ───────────────────────────────────────────────
        Arrays.fill(gridState, false);
        turnsPlayed = 0;
        isGameOver = false;
        isPlayerTurn = true;
        inputBlocked = false;
        isAutoMode = false;
        if (autoPlayTimer != null && autoPlayTimer.isRunning())
            autoPlayTimer.stop();

        // ── 4. Randomise initial board (skip black holes) ─────────────────────
        int initialMoves = 4 + rand.nextInt(3);
        for (int i = 0; i < initialMoves; i++) {
            int tile;
            do {
                tile = rand.nextInt(totalTiles);
            } while (blackHoles.contains(tile));
            performFlip(tile);
        }
        rules.clearMemory();

        // ── 5. Brain Scanner ─────────────────────────────────────────────────
        if (brainLog != null)
            brainLog.setText("");
        logBrain("=== GAME START ===");
        logBrain("Grid: " + gridSize + "x" + gridSize + " | R" + selectedVersion);
        logBrain("Black Holes at tiles: " + blackHoles);
        logBrain("Your turn! Click a tile or press Hint.");

        // ── 6. Build game panel ───────────────────────────────────────────────
        if (gamePanel != null)
            mainPanel.remove(gamePanel);
        gamePanel = createGamePanel();
        mainPanel.add(gamePanel, "GAME");
        mainPanel.revalidate();
        mainPanel.repaint();

        updateBoardUI();
        updateScoreDisplay();
        cardLayout.show(mainPanel, "GAME");
    }

    /**
     * Logs a message to the Brain Scanner text area.
     * Called by Engine/DACAlgorithms via Consumer<String> callback.
     * Thread-safe: routes through SwingUtilities.invokeLater.
     */
    public void logBrain(String message) {
        if (brainLog == null)
            return;
        SwingUtilities.invokeLater(() -> {
            brainLog.append(message + "\n");
            // Auto-scroll to bottom
            brainLog.setCaretPosition(brainLog.getDocument().getLength());
        });
    }

    private void performFlip(int id) {
        for (int neighbor : graph.getNeighbors(id)) {
            // Lock Protection Mechanic
            if (!rules.isLocked(neighbor)) {
                gridState[neighbor] = !gridState[neighbor];
            }
        }
        Toolkit.getDefaultToolkit().beep();
    }

    private void handlePlayerMove(int id) {
        if (inputBlocked || !isPlayerTurn || isGameOver)
            return;
        if (blackHoles.contains(id))
            return; // Black Hole — cannot interact
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

        new Thread(() -> {
            try {
                Thread.sleep(1200); // Delay for better visual separation between moves
            } catch (Exception ignored) {
            }

            // CPU uses Greedy with combined D&C evaluation (no backtracking)
            int move = ai.getBestMove(gridState);
            if (move == -1)
                move = 0; // Fallback to first tile

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
                    if (isAutoMode) {
                        triggerAutoMove();
                    }
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

        JLabel title = new JLabel("FLIP WARS");
        title.setFont(new Font("Verdana", Font.BOLD, 64));
        title.setForeground(Rules.COLOR_PLAYER);
        title.setAlignmentX(CENTER_ALIGNMENT);

        JLabel subTitle = new JLabel("A Strategic Duel of Algorithms");
        subTitle.setFont(new Font("Arial", Font.ITALIC, 18));
        subTitle.setForeground(Color.WHITE);
        subTitle.setAlignmentX(CENTER_ALIGNMENT);

        javax.swing.Timer pulse = new javax.swing.Timer(500, e -> {
            boolean isPlayerColor = title.getForeground().equals(Rules.COLOR_PLAYER);
            title.setForeground(isPlayerColor ? COLOR_ACCENT : Rules.COLOR_PLAYER);
        });
        pulse.start();

        JButton btnStart = createBtn("PLAY GAME");
        btnStart.setPreferredSize(new Dimension(250, 50));
        btnStart.addActionListener(e -> startGame());

        JButton btnIns = createBtn("CREDITS & RULES");
        btnIns.addActionListener(e -> cardLayout.show(mainPanel, "INSTRUCTIONS"));

        JPanel footer = new JPanel(new GridLayout(2, 1));
        footer.setBackground(COLOR_BG);
        footer.setMaximumSize(new Dimension(400, 60));
        JLabel teamLabel = createLbl("Design & Analysis of Algorithms", 14, Color.LIGHT_GRAY);
        JLabel dAA = createLbl("team-13", 12, Color.GRAY);
        footer.add(teamLabel);
        footer.add(dAA);

        JPanel sizePanel = new JPanel();
        sizePanel.setBackground(COLOR_BG);
        JLabel sizeLabel = createLbl("Grid Size: ", 18, Color.WHITE);
        Integer[] sizes = { 4, 5, 6 };
        JComboBox<Integer> sizeCombo = new JComboBox<>(sizes);
        sizeCombo.setSelectedItem(gridSize);
        sizeCombo.setFont(new Font("Arial", Font.BOLD, 16));
        sizeCombo.addActionListener(e -> {
            int selected = (int) sizeCombo.getSelectedItem();
            initializeLogic(selected);
        });

        // Version Selector: R1 (Greedy) vs R2 (D&C) vs R3 (Coming Soon)
        JLabel verLabel = createLbl("  Version: ", 18, Color.WHITE);
        String[] versions = { "R1: Greedy", "R2: D&C", "R3: Backtracking" };
        JComboBox<String> verCombo = new JComboBox<>(versions);
        verCombo.setSelectedIndex(selectedVersion - 1);
        verCombo.setFont(new Font("Arial", Font.BOLD, 16));
        verCombo.addActionListener(e -> {
            selectedVersion = verCombo.getSelectedIndex() + 1;
        });

        sizePanel.add(sizeLabel);
        sizePanel.add(sizeCombo);
        sizePanel.add(verLabel);
        sizePanel.add(verCombo);

        p.add(Box.createVerticalGlue());
        p.add(title);
        p.add(subTitle);
        p.add(Box.createRigidArea(new Dimension(0, 40)));
        p.add(sizePanel);
        p.add(Box.createRigidArea(new Dimension(0, 20)));
        p.add(btnStart);
        p.add(Box.createRigidArea(new Dimension(0, 20)));
        p.add(btnIns);
        p.add(Box.createVerticalGlue());
        p.add(footer);
        p.add(Box.createRigidArea(new Dimension(0, 20)));
        return p;
    }

    private JPanel createGamePanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(COLOR_BG);

        JPanel top = new JPanel(new GridLayout(3, 1));
        top.setBackground(COLOR_BG);
        scoreLabel = createLbl("Yellow: 0 | Grey: 0", 24, Color.WHITE);
        turnLabel = createLbl("Turn: 0 / " + maxTurns, 18, COLOR_ACCENT);
        statusLabel = createLbl("Your Turn", 18, COLOR_HINT);
        top.add(scoreLabel);
        top.add(turnLabel);
        top.add(statusLabel);
        p.add(top, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(gridSize, gridSize, 8, 8));
        grid.setBackground(COLOR_BG);
        grid.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        for (int i = 0; i < totalTiles; i++) {
            JButton b = new JButton();
            b.setFocusPainted(false);
            final int id = i;
            b.addActionListener(e -> handlePlayerMove(id));
            tileButtons[i] = b;
            grid.add(b);
        }
        p.add(grid, BorderLayout.CENTER);

        JPanel bot = new JPanel();
        bot.setBackground(COLOR_BG);
        JButton bh = createBtn("Get Hint");
        bh.addActionListener(e -> {
            // Hint uses version-appropriate logic (R1: Greedy, R2: D&C Merge Sort)
            int hint = ai.getPlayerHint(gridState);
            if (hint != -1)
                tileButtons[hint].setBorder(BorderFactory.createLineBorder(COLOR_HINT, 4));
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
                        if (isPlayerTurn && !inputBlocked) {
                            triggerAutoMove();
                        }
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

        // ── Brain Scanner log panel ──────────────────────────────────────────
        brainLog = new JTextArea(6, 40);
        brainLog.setBackground(new Color(15, 15, 25));
        brainLog.setForeground(new Color(80, 255, 120)); // terminal green
        brainLog.setFont(new Font("Consolas", Font.PLAIN, 11));
        brainLog.setEditable(false);
        brainLog.setLineWrap(true);
        brainLog.setWrapStyleWord(true);
        JScrollPane brainScroll = new JScrollPane(brainLog);
        brainScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(80, 255, 120), 1),
                " 🧠 Brain Scanner",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font("Consolas", Font.BOLD, 11),
                new Color(80, 255, 120)));
        brainScroll.setPreferredSize(new Dimension(680, 120));

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.setBackground(COLOR_BG);
        southPanel.add(bot, BorderLayout.NORTH);
        southPanel.add(brainScroll, BorderLayout.SOUTH);

        p.add(southPanel, BorderLayout.SOUTH);
        return p;
    }

    private JPanel createInstructionsPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(COLOR_BG);

        JLabel header = createLbl("GAME MANUAL & TIPS", 28, Rules.COLOR_PLAYER);
        header.setBorder(BorderFactory.createEmptyBorder(20, 0, 10, 0));
        p.add(header, BorderLayout.NORTH);

        JTextArea t = new JTextArea();
        t.setBackground(COLOR_BG);
        t.setForeground(Color.WHITE);
        t.setFont(new Font("Consolas", Font.PLAIN, 15));
        t.setEditable(false);
        t.setLineWrap(true);
        t.setWrapStyleWord(true);
        t.setMargin(new Insets(20, 30, 20, 30));

        t.setText(
                "=== HOW TO PLAY ===\n" +
                        "1. OBJECTIVE: Conquer the grid by turning all tiles YELLOW or have\n" +
                        "   the highest Strategic Score when the turn limit is reached.\n\n" +
                        "2. FLIP LOGIC: Clicking a tile flips its color AND all 4 orthogonal\n" +
                        "   neighbors in a PLUS (+) pattern.\n\n" +
                        "3. LOCK MECHANIC: Tiles are LOCKED after being clicked. Check the\n" +
                        "   'WAIT' countdown to see when they unlock.\n\n" +
                        "=== VERSION SELECTOR ===\n\n" +
                        "R1 (GREEDY): Basic AI from Review 1. Uses simple tile counting\n" +
                        "   with 15% random blunder. Easy to beat.\n\n" +
                        "R2 (DIVIDE & CONQUER): Smart AI from Review 2. Uses 5 D&C\n" +
                        "   algorithms for sophisticated board evaluation.\n\n" +
                        "R3 (DP + BACKTRACKING): Review 3 AI. State-Space Search Engine with:\n" +
                        "  - SUHAS   : Pure Backtracking (doMove/undoMove, O(1) space)\n" +
                        "  - MANEESH : Alpha-Beta Pruning Minimax + D&C move ordering\n" +
                        "  - GANESH  : Zobrist Transposition Table (Top-Down Memoization)\n" +
                        "  - BALAJI  : Bottom-Up Bitmask DP Oracle (4x4: 65536 states)\n\n" +
                        "=== 5 DIVIDE & CONQUER ALGORITHMS (R2) ===\n\n" +
                        "1. MERGE SORT (Search Space D&C) - O(n log n)\n" +
                        "   * Ranks all possible moves by score\n" +
                        "   * Divide: Split moves into halves\n" +
                        "   * Conquer: Sort each half recursively\n" +
                        "   * Combine: Merge sorted halves\n" +
                        "   * Used for: Player Hints\n\n" +
                        "2. SPATIAL D&C (Quadrant Evaluation) - O(n)\n" +
                        "   * Evaluates regional control\n" +
                        "   * Divide: Split grid into 4 quadrants\n" +
                        "   * Conquer: Score each quadrant\n" +
                        "   * Combine: Weight corners 2.0x, edges 1.5x\n" +
                        "   * Used for: Board scoring (25% weight)\n\n" +
                        "3. DFS CLUSTERS (Structural D&C) - O(V+E)\n" +
                        "   * Finds connected tile groups\n" +
                        "   * Divide: Separate into components via DFS\n" +
                        "   * Conquer: Score each island = size^2\n" +
                        "   * Combine: Sum top 3 largest clusters\n" +
                        "   * Used for: Board scoring (25% weight)\n\n" +
                        "4. TOURNAMENT SELECTION (Search Space D&C) - O(n)\n" +
                        "   * Selects best move via knockout tournament\n" +
                        "   * Divide: Split moves into brackets\n" +
                        "   * Conquer: Find winner of each bracket\n" +
                        "   * Combine: Champions face off for title\n" +
                        "   * Used for: CPU Move Selection\n\n" +
                        "5. THREAT DETECTION (Scoring D&C) - O(n)\n" +
                        "   * Identifies vulnerable/exposed tiles\n" +
                        "   * Divide: Split grid into 4 quadrants\n" +
                        "   * Conquer: Count enemy neighbors per tile\n" +
                        "   * Combine: Weight corner quadrants 2.0x\n" +
                        "   * Used for: Board scoring (30% weight)\n\n" +
                        "=== SCORING VALUES ===\n" +
                        "Corners: +25 | Edges: +15 | Standard: +5 | Traps: -5\n\n" +
                        "=== WINNING STRATEGIES ===\n" +
                        "* Secure corners early - they're worth the most!\n" +
                        "* Build large connected clusters for territory control\n" +
                        "* Avoid trap tiles near corners (-5 points)\n" +
                        "* Plan around locked tiles for surprise moves");

        JScrollPane scroll = new JScrollPane(t);
        scroll.setBorder(null);
        p.add(scroll, BorderLayout.CENTER);

        JButton b = createBtn("RETURN TO MENU");
        b.addActionListener(e -> cardLayout.show(mainPanel, "MENU"));
        p.add(b, BorderLayout.SOUTH);

        return p;
    }

    private void updateBoardUI() {
        for (int i = 0; i < totalTiles; i++) {
            // ── BLACK HOLE: render as a void ───────────────────────────────
            if (blackHoles.contains(i)) {
                tileButtons[i].setBackground(Color.BLACK);
                tileButtons[i].setText("■"); // solid square glyph
                tileButtons[i].setForeground(new Color(60, 0, 80)); // dark purple
                tileButtons[i].setFont(new Font("Serif", Font.BOLD, 20));
                tileButtons[i].setEnabled(false);
                tileButtons[i].setBorder(BorderFactory.createLineBorder(
                        new Color(80, 0, 100), 2));
                continue;
            }
            boolean isLocked = rules.isLocked(i);
            Color baseColor = (gridState[i] ? Rules.COLOR_PLAYER : Rules.COLOR_CPU);
            double weight = rules.getTileStrategicValue(i);

            if (isLocked) {
                Color dimmed = new Color(baseColor.getRed() / 2, baseColor.getGreen() / 2, baseColor.getBlue() / 2);
                tileButtons[i].setBackground(dimmed);

                int countdown = rules.getLockCountdown(i);
                // Display: score on top, WAIT with countdown below in red
                String scoreText = (weight != 0) ? ((weight > 0 ? "+" : "") + (int) weight) : "";
                tileButtons[i].setText("<html><center>" + scoreText + "<br><font color='red'><b>WAIT:" + countdown
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

    private void updateScoreDisplay() {
        double yScore = calculateWeightedScore(true);
        double gScore = calculateWeightedScore(false);
        scoreLabel.setText(String.format("Yellow: %.1f | Grey: %.1f", yScore, gScore));
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