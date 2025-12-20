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
    private static final int GRID_SIZE = 6;
    private static final int TOTAL_TILES = 36;
    private static final int MAX_TURNS = 30;

    // --- Colors ---
    private static final Color COLOR_BG = new Color(44, 62, 80);
    private static final Color COLOR_ACCENT = new Color(230, 126, 34);
    private static final Color COLOR_HINT = new Color(46, 204, 113);
    private static final Color COLOR_CPU_MOVE = new Color(231, 76, 60);

    // --- Logic Components (The Team's Work) ---
    private final GameGraph graph; // Member 1
    private final GreedyEngine ai; // Member 2
    private final StrategyHeuristics metrics; // Member 3

    // --- UI State ---
    private boolean[] gridState = new boolean[TOTAL_TILES];
    private boolean isPlayerTurn = true;
    private boolean inputBlocked = false;
    private boolean isGameOver = false;
    private int turnsPlayed = 0;

    private CardLayout cardLayout = new CardLayout();
    private JPanel mainPanel = new JPanel(cardLayout);
    private JButton[] tileButtons = new JButton[TOTAL_TILES];
    private JLabel statusLabel, scoreLabel, turnLabel;

    public Main() {
        // Initialize Logic
        this.graph = new GameGraph(GRID_SIZE);
        this.metrics = new StrategyHeuristics(GRID_SIZE);
        this.ai = new GreedyEngine(TOTAL_TILES, graph, metrics);

        setTitle("Flip Wars - Team Edition");
        setSize(600, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        mainPanel.add(createMenuPanel(), "MENU");
        mainPanel.add(createGamePanel(), "GAME");
        mainPanel.add(createInstructionsPanel(), "INSTRUCTIONS");

        add(mainPanel);
        cardLayout.show(mainPanel, "MENU");
    }

    private void startGame() {
        Arrays.fill(gridState, false);
        metrics.clearMemory();
        turnsPlayed = 0;
        isGameOver = false;
        isPlayerTurn = true;
        inputBlocked = false;

        Random rand = new Random();
        int initialMoves = 4 + rand.nextInt(3);
        for (int i = 0; i < initialMoves; i++) {
            performFlip(rand.nextInt(TOTAL_TILES));
        }

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
                move = new Random().nextInt(TOTAL_TILES);

            int finalMove = move;
            SwingUtilities.invokeLater(() -> {
                tileButtons[finalMove].setBorder(BorderFactory.createLineBorder(COLOR_CPU_MOVE, 4));
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
                }
            });
        }).start();
    }

    private void checkGameStatus() {
        int yCount = countTiles(true);
        int gCount = countTiles(false);
        double yScore = calculateWeightedScore(true);
        double gScore = calculateWeightedScore(false);
        String msg = null;

        if (yCount == TOTAL_TILES)
            msg = "Victorious! Human wins!";
        else if (gCount == TOTAL_TILES)
            msg = "Defeat! CPU wins!";
        else if (turnsPlayed >= MAX_TURNS) {
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
            else
                cardLayout.show(mainPanel, "MENU");
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

        p.add(Box.createVerticalGlue());
        p.add(title);
        p.add(subTitle);
        p.add(Box.createRigidArea(new Dimension(0, 60)));
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
        turnLabel = createLbl("Turn: 0 / 30", 18, COLOR_ACCENT);
        statusLabel = createLbl("Your Turn", 18, COLOR_HINT);
        top.add(scoreLabel);
        top.add(turnLabel);
        top.add(statusLabel);
        p.add(top, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(GRID_SIZE, GRID_SIZE, 8, 8));
        grid.setBackground(COLOR_BG);
        grid.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        for (int i = 0; i < TOTAL_TILES; i++) {
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
        JButton bm = createBtn("Menu");
        bm.addActionListener(e -> cardLayout.show(mainPanel, "MENU"));
        bot.add(bh);
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
                        "3. Tabu Lock: Once a tile is used, it is 'LOCKED' (X) for 8 turns. \n" +
                        "   Neither you nor the CPU can touch it during this time.\n\n" +
                        "--- DAA ALGORITHMS (TEAM ROLES) ---\n" +
                        "• [Mem 1] Graph Architect: Implemented the board as an Adjacency \n" +
                        "  List to manage tile connectivity patterns.\n" +
                        "• [Mem 2] Greedy Optimizer: Developed the move selection and \n" +
                        "  the 'Smart Hint' logic using Bubble Sort application.\n" +
                        "• [Mem 3] Heuristic & Tabu Manager: Designed the strategic Heat Map \n" +
                        "  and the Tabu Search memory to prevent cycles.\n" +
                        "• [Mem 4] Performance Monitor: Optimized algorithmic complexity \n" +
                        "  and managed state transitions effectively.\n\n" +
                        "--- STRATEGY TIP ---\n" +
                        "Corners are worth 15x more than center tiles. Protect your corners!");

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
        for (int i = 0; i < TOTAL_TILES; i++) {
            boolean isLocked = metrics.isLocked(i);
            tileButtons[i].setBackground(isLocked ? new Color(20, 20, 20)
                    : (gridState[i] ? StrategyHeuristics.COLOR_PLAYER : StrategyHeuristics.COLOR_CPU));

            if (isLocked) {
                int countdown = metrics.getLockCountdown(i);
                tileButtons[i].setText("L" + countdown);
                tileButtons[i].setForeground(Color.RED);
                tileButtons[i].setFont(new Font("Arial", Font.BOLD, 18));
            } else {
                double weight = metrics.getTileStrategicValue(i);
                if (weight != 0) {
                    tileButtons[i].setText((weight > 0 ? "+" : "") + (int) weight);
                    tileButtons[i].setForeground(weight > 0 ? Color.WHITE : new Color(255, 150, 150));
                    tileButtons[i].setFont(new Font("Arial", Font.BOLD, 12));
                } else {
                    tileButtons[i].setText("");
                }
            }
            tileButtons[i].setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
        }
    }

    private void updateScoreDisplay() {
        double yScore = calculateWeightedScore(true);
        double gScore = calculateWeightedScore(false);
        scoreLabel.setText(String.format("Yellow: %.1f | Grey: %.1f", yScore, gScore));
        turnLabel.setText("Turn: " + turnsPlayed + " / " + MAX_TURNS);
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
        for (int i = 0; i < TOTAL_TILES; i++) {
            if (gridState[i] == isYellow) {
                total += metrics.getTileStrategicValue(i);
            }
        }
        return total;
    }

    private void celebrate(boolean human) {
        javax.swing.Timer t = new javax.swing.Timer(150, e -> {
            for (int i = 0; i < TOTAL_TILES; i++) {
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