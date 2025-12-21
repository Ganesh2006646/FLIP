package com.flipwars;

import javax.swing.*;
import java.awt.*;
import java.util.*;

/**
 * Main Entry Point and View Manager (UI).
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
        this.graph = new Graph(gridSize);
        this.rules = new Rules(gridSize);
        this.ai = new Engine(totalTiles, graph, rules);
        this.gridState = new boolean[totalTiles];
        this.tileButtons = new JButton[totalTiles];
    }

    private void startGame() {
        Arrays.fill(gridState, false);
        rules.clearMemory();
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
                Thread.sleep(800);
            } catch (Exception ignored) {
            }

            int move = ai.getBestMove(gridState);
            if (move == -1)
                move = new Random().nextInt(totalTiles);

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
                "--- HOW TO PLAY ---\n" +
                        "1. Objective: Conquer the grid by turning all tiles YELLOW or have the \n" +
                        "   highest Strategic Score after the turn limit.\n" +
                        "2. Flip Logic: Clicking a tile flips its color and all 4 orthogonal \n" +
                        "   neighbors in a PLUS (+) formation.\n" +
                        "3. Lock Mechanic: Tiles are 'LOCKED' after use. Check the 'WAIT' timer \n" +
                        "   on the tile to see when it will become available again.\n\n" +
                        "--- LOCK PROTECTION ---\n" +
                        "While a tile is LOCKED, it is IMMUNE to flipping from neighbors. \n" +
                        "Use this to safely hold high-value tiles like Corners!\n\n" +
                        "--- WINNING STRATEGIES ---\n" +
                        "• Corner Control (+25 pts): Secure corners early! Locked corners are \n" +
                        "  impenetrable defensive anchors.\n" +
                        "• Edge Supremacy (+15 pts): Edges provide stable strategic points.\n" +
                        "• Avoid Traps (-5 pts): Tiles near corners are 'Traps'. They lower \n" +
                        "  your score and expose your corners to enemy flips.\n\n" +
                        "--- SCORE VALUES ---\n" +
                        "Corners: 25.0 | Edges: 15.0 | Standard: 5.0 | Traps: -5.0");

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
            boolean isLocked = rules.isLocked(i);
            Color baseColor = (gridState[i] ? Rules.COLOR_PLAYER : Rules.COLOR_CPU);

            if (isLocked) {
                Color dimmed = new Color(baseColor.getRed() / 2, baseColor.getGreen() / 2, baseColor.getBlue() / 2);
                tileButtons[i].setBackground(dimmed);

                int countdown = rules.getLockCountdown(i);
                tileButtons[i].setText("WAIT: " + countdown);
                tileButtons[i].setForeground(Color.RED);
                tileButtons[i].setFont(new Font("Arial", Font.BOLD, 18));
                tileButtons[i].setBorder(BorderFactory.createLineBorder(Color.RED, 3));
            } else {
                tileButtons[i].setBackground(baseColor);
                double weight = rules.getTileStrategicValue(i);
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
                double val = rules.getTileStrategicValue(i);
                // Lock Protection Bonus in scoring: if owned and locked, it's safer
                if (rules.isLocked(i))
                    val *= 1.2;
                total += val;
            }
        }
        return total;
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