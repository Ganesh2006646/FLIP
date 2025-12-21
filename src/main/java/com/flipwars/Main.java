package com.flipwars;

import javax.swing.*;
import java.awt.*;
import java.util.*;

/**
 * FLIP WARS - Multi-File Refactored Edition
 * This class serves as the Main Entry Point and View Manager (UI).
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

    // --- Logic Components (The Team's Work) ---
    private GameGraph graph;
    private GreedyEngine ai;
    private StrategyHeuristics metrics;

    // --- UI State ---
    private boolean[] gridState;
    private boolean isPlayerTurn = true;
    private boolean inputBlocked = false;
    private boolean isGameOver = false;
    private boolean isAutoMode = false;
    private int turnsPlayed = 0;

    private CardLayout cardLayout = new CardLayout();
    private JPanel mainPanel = new JPanel(cardLayout);
    private JButton[] tileButtons;
    private JLabel statusLabel, scoreLabel, turnLabel;
    private JPanel gamePanel; // Keep reference to game panel to replace it

    public Main() {
        initializeLogic(4);

        setTitle("Flip Wars - Team Edition");
        setSize(700, 900); // Increased size slightly to accommodate larger grids
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        mainPanel.add(createMenuPanel(), "MENU");
        // gamePanel will be created when starting the game or changing size
        mainPanel.add(new JPanel(), "GAME");
        mainPanel.add(createInstructionsPanel(), "INSTRUCTIONS");

        add(mainPanel);
        cardLayout.show(mainPanel, "MENU");
    }

    private void initializeLogic(int size) {
        this.gridSize = size;
        this.totalTiles = size * size;
        this.maxTurns = 25; // Fixed turns as requested
        this.graph = new GameGraph(gridSize);
        this.metrics = new StrategyHeuristics(gridSize);
        this.ai = new GreedyEngine(totalTiles, graph, metrics);
        this.gridState = new boolean[totalTiles];
        this.tileButtons = new JButton[totalTiles];
    }

    private void startGame() {
        Arrays.fill(gridState, false);
        metrics.clearMemory();
        turnsPlayed = 0;
        isGameOver = false;
        isPlayerTurn = true;
        inputBlocked = false;
        isAutoMode = false;

        Random rand = new Random();
        int initialMoves = 4 + rand.nextInt(3);
        for (int i = 0; i < initialMoves; i++) {
            performFlip(rand.nextInt(totalTiles));
        }

        if (gamePanel != null) {
            mainPanel.remove(gamePanel);
        }
        gamePanel = createGamePanel();
        mainPanel.add(gamePanel, "GAME");
        mainPanel.revalidate();
        mainPanel.repaint();

        updateBoardUI();
        updateScoreDisplay();
        cardLayout.show(mainPanel, "GAME");
    }

    private void performFlip(int id) {
        for (int neighbor : graph.getNeighbors(id)) {
            gridState[neighbor] = !gridState[neighbor];
        }
        Toolkit.getDefaultToolkit().beep();
    }

    private void handlePlayerMove(int id) {
        if (inputBlocked || !isPlayerTurn || isGameOver)
            return;

        if (metrics.isLocked(id)) {
            statusLabel.setText("Tile Locked!");
            statusLabel.setForeground(Color.RED);
            return;
        }

        performFlip(id);
        metrics.recordMove(id);
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
                Thread.sleep(800);
            } catch (Exception ignored) {
            }

            int move = ai.getBestMove(gridState);
            if (move == -1)
                move = new Random().nextInt(totalTiles);

            int finalMove = move;
            SwingUtilities.invokeLater(() -> {
                performFlip(finalMove);
                metrics.recordMove(finalMove);
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

    // --- UI Factory Methods ---

    private JPanel createMenuPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(COLOR_BG);

        // Title Section
        JLabel title = new JLabel("FLIP WARS");
        title.setFont(new Font("Verdana", Font.BOLD, 64));
        title.setForeground(StrategyHeuristics.COLOR_PLAYER);
        title.setAlignmentX(CENTER_ALIGNMENT);

        JLabel subTitle = new JLabel("A Strategic Duel of Algorithms");
        subTitle.setFont(new Font("Arial", Font.ITALIC, 18));
        subTitle.setForeground(Color.WHITE);
        subTitle.setAlignmentX(CENTER_ALIGNMENT);

        javax.swing.Timer pulse = new javax.swing.Timer(500, e -> {
            boolean isPlayerColor = title.getForeground().equals(StrategyHeuristics.COLOR_PLAYER);
            title.setForeground(isPlayerColor ? COLOR_ACCENT : StrategyHeuristics.COLOR_PLAYER);
        });
        pulse.start();

        // Buttons
        JButton btnStart = createBtn("PLAY GAME");
        btnStart.setPreferredSize(new Dimension(250, 50));
        btnStart.addActionListener(e -> startGame());

        JButton btnIns = createBtn("CREDITS & RULES");
        btnIns.addActionListener(e -> cardLayout.show(mainPanel, "INSTRUCTIONS"));

        // Team Footer
        JPanel footer = new JPanel(new GridLayout(2, 1));
        footer.setBackground(COLOR_BG);
        footer.setMaximumSize(new Dimension(400, 60));
        JLabel teamLabel = createLbl("Semester Project • Team of 4", 14, Color.LIGHT_GRAY);
        JLabel dAA = createLbl("Design & Analysis of Algorithms", 12, Color.GRAY);
        footer.add(teamLabel);
        footer.add(dAA);

        // Grid Size Selection
        JPanel sizePanel = new JPanel();
        sizePanel.setBackground(COLOR_BG);
        JLabel sizeLabel = createLbl("Select Grid Size: ", 18, Color.WHITE);
        Integer[] sizes = { 4, 5, 6 };
        JComboBox<Integer> sizeCombo = new JComboBox<>(sizes);
        sizeCombo.setSelectedItem(gridSize);
        sizeCombo.setFont(new Font("Arial", Font.BOLD, 16));
        sizeCombo.addActionListener(e -> {
            int selected = (int) sizeCombo.getSelectedItem();
            initializeLogic(selected);
        });
        sizePanel.add(sizeLabel);
        sizePanel.add(sizeCombo);

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
            if (isAutoMode && isPlayerTurn && !inputBlocked) {
                triggerAutoMove();
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

        // Header
        JLabel header = createLbl("GAME MANUAL & CREDITS", 28, StrategyHeuristics.COLOR_PLAYER);
        header.setBorder(BorderFactory.createEmptyBorder(20, 0, 10, 0));
        p.add(header, BorderLayout.NORTH);

        // Body with detailed rules and DAA concepts
        JTextArea t = new JTextArea();
        t.setBackground(COLOR_BG);
        t.setForeground(Color.WHITE);
        t.setFont(new Font("Consolas", Font.PLAIN, 15));
        t.setEditable(false);
        t.setLineWrap(true);
        t.setWrapStyleWord(true);
        t.setMargin(new Insets(20, 30, 20, 30));

        t.setText(
                "--- HOW TO PLAY ---\n" +
                        "1. Objective: Conquer the grid by turning all tiles YELLOW.\n" +
                        "2. Flip Logic: Clicking a tile flips its color and all 4 orthogonal \n" +
                        "   neighbors in a PLUS (+) formation.\n" +
                        "3. Tabu Lock: Once a tile is used, it is 'LOCKED'. You must WAIT \n" +
                        "   until the countdown expires to reuse it.\n\n" +
                        "--- DAA ALGORITHMS (TEAM ROLES) ---\n" +
                        "• [Mem 1] Graph Architect: Implemented the board as an Adjacency \n" +
                        "  List to manage tile connectivity patterns.\n" +
                        "• [Mem 2] Greedy Optimizer: Developed the move selection and \n" +
                        "  Smart Hint logic using Pure Greedy Local Search.\n" +
                        "• [Mem 3] Heuristic & Tabu Manager: Designed the strategic Heat Map \n" +
                        "  and the Tabu Search memory to prevent loops.\n" +
                        "• [Mem 4] State Monitor: Managed game transitions and efficiency \n" +
                        "  monitoring for the strategic board state.\n\n" +
                        "--- STRATEGY TIP ---\n" +
                        "Corners are worth 25 points, edges 15. Standard tiles are 5. \n" +
                        "Avoid 'Traps' which subtract 5 points!");

        JScrollPane scroll = new JScrollPane(t);
        scroll.setBorder(null);
        p.add(scroll, BorderLayout.CENTER);

        // Back Button
        JButton b = createBtn("RETURN TO MENU");
        b.addActionListener(e -> cardLayout.show(mainPanel, "MENU"));
        p.add(b, BorderLayout.SOUTH);

        return p;
    }

    // --- Helpers ---
    private void updateBoardUI() {
        for (int i = 0; i < totalTiles; i++) {
            boolean isLocked = metrics.isLocked(i);
            Color baseColor = (gridState[i] ? StrategyHeuristics.COLOR_PLAYER : StrategyHeuristics.COLOR_CPU);

            if (isLocked) {
                // Dim the color when locked so owner is still visible but it looks
                // "deactivated"
                Color dimmed = new Color(baseColor.getRed() / 2, baseColor.getGreen() / 2, baseColor.getBlue() / 2);
                tileButtons[i].setBackground(dimmed);

                int countdown = metrics.getLockCountdown(i);
                tileButtons[i].setText("WAIT: " + countdown);
                tileButtons[i].setForeground(Color.RED);
                tileButtons[i].setFont(new Font("Arial", Font.BOLD, 18));
                tileButtons[i].setBorder(BorderFactory.createLineBorder(Color.RED, 3));
            } else {
                tileButtons[i].setBackground(baseColor);
                double weight = metrics.getTileStrategicValue(i);
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
        double total = 0;
        for (int i = 0; i < totalTiles; i++) {
            if (gridState[i] == isYellow) {
                total += metrics.getTileStrategicValue(i);
            }
        }
        return total;
    }

    private void celebrate(boolean human) {
        javax.swing.Timer t = new javax.swing.Timer(150, e -> {
            for (int i = 0; i < totalTiles; i++) {
                tileButtons[i].setBackground(new Random().nextBoolean() ? Color.WHITE
                        : (human ? StrategyHeuristics.COLOR_PLAYER : StrategyHeuristics.COLOR_CPU));
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
                b.setBackground(StrategyHeuristics.COLOR_PLAYER);
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