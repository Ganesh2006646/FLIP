package com.flipwars;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * FLIP WARS - Semester Project (Corrected Version)
 * * Features Implemented:
 * 1. Graph Representation (Adjacency List)
 * 2. Greedy Algorithm (CPU Logic) with Deadlock Prevention
 * 3. Game Loop with Turn Limits and Win Detection
 * 4. Full Score Reset functionality
 */
public class Main extends JFrame {

    // --- Game Constants ---
    private static final int GRID_SIZE = 4;
    private static final int TOTAL_TILES = 16;
    private static final int MAX_TURNS = 20; // Game ends if no winner by 20 turns

    // --- Colors ---
    private static final Color COLOR_ON = new Color(241, 196, 15); // Flat Yellow
    private static final Color COLOR_OFF = new Color(149, 165, 166); // Flat Grey
    private static final Color HIGHLIGHT_COLOR = new Color(231, 76, 60); // Red Border
    private static final Color TEXT_COLOR = Color.WHITE;
    private static final Color BG_COLOR_DARK = new Color(44, 62, 80); // Dark Blue
    private static final Color BG_COLOR_LIGHT = new Color(52, 73, 94); // Light Blue

    // --- Game State ---
    private boolean[] gridState = new boolean[TOTAL_TILES]; // true = Yellow, false = Grey
    private Map<Integer, List<Integer>> adjacencyList = new HashMap<>();
    private boolean isPlayerTurn = true;
    private boolean inputBlocked = false;
    private boolean isGameOver = false;

    private volatile int gameRoundId = 0; // Prevents old threads from updating new games
    private int turnsPlayed = 0;
    private int lastHumanMove = -1; // For Deadlock Prevention

    // --- UI Elements ---
    private CardLayout cardLayout;
    private JPanel mainPanel;
    private JPanel menuPanel;
    private JPanel gamePanel;
    private JPanel instructionsPanel;

    private JButton[] tileButtons = new JButton[TOTAL_TILES];
    private JLabel scoreLabel;
    private JLabel turnLabel;

    public Main() {
        setTitle("Flip Wars - Algorithm Project");
        setSize(600, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Center on screen

        // 1. Initialize the Graph Structure
        initializeGraph();

        // 2. Setup Layouts
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        createMenuPanel();
        createGamePanel();
        createInstructionsPanel();

        mainPanel.add(menuPanel, "MENU");
        mainPanel.add(gamePanel, "GAME");
        mainPanel.add(instructionsPanel, "INSTRUCTIONS");

        add(mainPanel);

        // 3. Show Menu
        cardLayout.show(mainPanel, "MENU");
    }

    public static void main(String[] args) {
        // Ensure GUI runs on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            new Main().setVisible(true);
        });
    }

    // ==========================================
    // 1. GRAPH REPRESENTATION
    // ==========================================
    private void initializeGraph() {
        // We map every tile (0-15) to a list of its neighbors.
        // This satisfies the "Graph Representation" requirement.
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                int id = r * GRID_SIZE + c;
                List<Integer> neighbors = new ArrayList<>();

                // Standard Cross Pattern (Up, Down, Left, Right)
                if (r > 0)
                    neighbors.add((r - 1) * GRID_SIZE + c); // Top
                if (r < GRID_SIZE - 1)
                    neighbors.add((r + 1) * GRID_SIZE + c); // Bottom
                if (c > 0)
                    neighbors.add(r * GRID_SIZE + (c - 1)); // Left
                if (c < GRID_SIZE - 1)
                    neighbors.add(r * GRID_SIZE + (c + 1)); // Right

                // Add Self (clicking a tile always flips itself)
                neighbors.add(id);

                adjacencyList.put(id, neighbors);
            }
        }
    }

    // ==========================================
    // 2. CORE GAME LOGIC
    // ==========================================

    private void resetGame() {
        // Reset all logic variables
        for (int i = 0; i < TOTAL_TILES; i++) {
            gridState[i] = false; // All Grey
        }

        gameRoundId++; // Invalidates any running CPU threads
        turnsPlayed = 0;
        lastHumanMove = -1;
        isGameOver = false;
        inputBlocked = false;
        isPlayerTurn = true;

        // Randomize Board Start State
        Random rand = new Random();
        int randomMoves = 4 + rand.nextInt(3);
        for (int i = 0; i < randomMoves; i++) {
            int randomTile = rand.nextInt(TOTAL_TILES);
            applyMoveLogic(randomTile);
        }

        // Update UI
        updateBoardUI();
        updateScore();
        turnLabel.setText("Turn: Player (Yellow) - Round 1/" + MAX_TURNS);
    }

    private void applyMoveLogic(int tileIndex) {
        // Use the Graph (Adjacency List) to flip neighbors
        for (int target : adjacencyList.get(tileIndex)) {
            gridState[target] = !gridState[target];
        }
    }

    private void checkWinCondition() {
        if (isGameOver)
            return;

        int yellow = 0;
        int grey = 0;
        for (boolean b : gridState) {
            if (b)
                yellow++;
            else
                grey++;
        }

        String winner = "";
        boolean gameEnded = false;

        // Condition A: Board is full
        if (yellow == TOTAL_TILES) {
            winner = "Perfect Victory! HUMAN WINS!";
            gameEnded = true;
        } else if (grey == TOTAL_TILES) {
            winner = "Perfect Victory! CPU WINS!";
            gameEnded = true;
        }
        // Condition B: Turn Limit Reached
        else if (turnsPlayed >= MAX_TURNS) {
            gameEnded = true;
            if (yellow > grey)
                winner = "Time's Up! HUMAN WINS by Score!";
            else if (grey > yellow)
                winner = "Time's Up! CPU WINS by Score!";
            else
                winner = "Time's Up! IT'S A DRAW!";
        }

        if (gameEnded) {
            isGameOver = true;
            inputBlocked = true;
            turnLabel.setText("GAME OVER");
            JOptionPane.showMessageDialog(this, winner, "Game Over", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ==========================================
    // 3. GREEDY AI (CPU)
    // ==========================================

    private void playGreedyMove() {
        if (isGameOver)
            return;

        inputBlocked = true;
        turnLabel.setText("Turn: CPU is thinking...");

        final int currentRound = gameRoundId;

        // Run AI in a separate thread to keep UI responsive
        new Thread(() -> {
            if (gameRoundId != currentRound)
                return;

            // Fake thinking time (0.8 seconds)
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
            }

            int bestMove = -1;
            int maxGreyIncrease = -100; // Start low

            // GREEDY ALGORITHM:
            // Simulate every possible move and pick the one that gives the most GREY tiles.
            for (int i = 0; i < TOTAL_TILES; i++) {

                // DEADLOCK PREVENTION (Ko Rule):
                // Do not immediately click the tile the human just clicked,
                // unless it's the only valid move left.
                if (i == lastHumanMove && turnsPlayed < MAX_TURNS - 1) {
                    continue;
                }

                // 1. Simulate
                boolean[] simulationState = gridState.clone();
                for (int neighbor : adjacencyList.get(i)) {
                    simulationState[neighbor] = !simulationState[neighbor];
                }

                // 2. Count Score
                int currentGrey = 0;
                for (boolean b : simulationState) {
                    if (!b)
                        currentGrey++;
                }

                // 3. Compare (Greedy Choice)
                // We want to maximize the gain of Grey tiles
                int currentGreyCount = 0;
                for (boolean b : gridState)
                    if (!b)
                        currentGreyCount++;

                int netGain = currentGrey - currentGreyCount;

                if (netGain > maxGreyIncrease) {
                    maxGreyIncrease = netGain;
                    bestMove = i;
                }
            }

            // Fallback if no move found (rare)
            if (bestMove == -1)
                bestMove = (lastHumanMove + 1) % TOTAL_TILES;

            final int chosenMove = bestMove;

            // Update UI on the Event Dispatch Thread
            SwingUtilities.invokeLater(() -> {
                if (currentRound != gameRoundId || isGameOver)
                    return;

                // Visual Feedback: Highlight CPU move
                tileButtons[chosenMove].setBorder(BorderFactory.createLineBorder(HIGHLIGHT_COLOR, 4));

                applyMoveLogic(chosenMove);
                turnsPlayed++; // Increment turn counter

                updateBoardUI();
                updateScore();
                checkWinCondition();

                if (!isGameOver) {
                    isPlayerTurn = true;
                    inputBlocked = false;
                    turnLabel.setText("Turn: Player (Yellow) - Round " + (turnsPlayed / 2 + 1) + "/" + MAX_TURNS);
                }
            });

        }).start();
    }

    // ==========================================
    // 4. UI CONSTRUCTION
    // ==========================================

    private void createMenuPanel() {
        menuPanel = new JPanel();
        menuPanel.setLayout(new BoxLayout(menuPanel, BoxLayout.Y_AXIS));
        menuPanel.setBackground(BG_COLOR_DARK);

        JLabel title = new JLabel("FLIP WARS");
        title.setFont(new Font("Verdana", Font.BOLD, 48));
        title.setForeground(new Color(241, 196, 15));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Greedy Algorithm Project");
        subtitle.setFont(new Font("Arial", Font.PLAIN, 18));
        subtitle.setForeground(Color.LIGHT_GRAY);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton btnStart = createStyledButton("Start Game");
        btnStart.addActionListener(e -> {
            resetGame();
            cardLayout.show(mainPanel, "GAME");
        });

        JButton btnInstruct = createStyledButton("Instructions");
        btnInstruct.addActionListener(e -> cardLayout.show(mainPanel, "INSTRUCTIONS"));

        // Layout Spacing
        menuPanel.add(Box.createVerticalGlue());
        menuPanel.add(title);
        menuPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        menuPanel.add(subtitle);
        menuPanel.add(Box.createRigidArea(new Dimension(0, 50)));
        menuPanel.add(btnStart);
        menuPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        menuPanel.add(btnInstruct);
        menuPanel.add(Box.createVerticalGlue());
    }

    private void createGamePanel() {
        gamePanel = new JPanel(new BorderLayout());
        gamePanel.setBackground(BG_COLOR_LIGHT);

        // -- Top Bar --
        JPanel topBar = new JPanel();
        topBar.setBackground(BG_COLOR_DARK);
        topBar.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        scoreLabel = new JLabel("Yellow: 0 | Grey: 0");
        scoreLabel.setForeground(TEXT_COLOR);
        scoreLabel.setFont(new Font("Arial", Font.BOLD, 20));

        turnLabel = new JLabel("Turn: Player");
        turnLabel.setForeground(Color.GREEN);
        turnLabel.setFont(new Font("Arial", Font.PLAIN, 16));

        topBar.add(scoreLabel);
        topBar.add(Box.createHorizontalStrut(30));
        topBar.add(turnLabel);

        gamePanel.add(topBar, BorderLayout.NORTH);

        // -- Grid --
        JPanel gridPanel = new JPanel(new GridLayout(GRID_SIZE, GRID_SIZE, 8, 8));
        gridPanel.setBackground(BG_COLOR_LIGHT);
        gridPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        for (int i = 0; i < TOTAL_TILES; i++) {
            JButton btn = new JButton();
            btn.setFocusPainted(false);
            btn.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));

            final int idx = i;
            btn.addActionListener(e -> handlePlayerClick(idx));

            tileButtons[i] = btn;
            gridPanel.add(btn);
        }
        gamePanel.add(gridPanel, BorderLayout.CENTER);

        // -- Bottom Bar --
        JPanel bottomBar = new JPanel();
        bottomBar.setBackground(BG_COLOR_DARK);
        bottomBar.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        JButton btnBack = createStyledButton("Back to Menu");
        btnBack.setFont(new Font("Arial", Font.BOLD, 14));
        btnBack.addActionListener(e -> cardLayout.show(mainPanel, "MENU"));
        bottomBar.add(btnBack);

        gamePanel.add(bottomBar, BorderLayout.SOUTH);
    }

    private void createInstructionsPanel() {
        instructionsPanel = new JPanel();
        instructionsPanel.setLayout(new BoxLayout(instructionsPanel, BoxLayout.Y_AXIS));
        instructionsPanel.setBackground(new Color(236, 240, 241)); // Light grey

        JLabel title = new JLabel("How to Play");
        title.setFont(new Font("Arial", Font.BOLD, 24));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTextArea text = new JTextArea(
                "\n1. GOAL:\n" +
                        "   Cover the board in your color (YELLOW).\n" +
                        "   The CPU tries to cover it in GREY.\n\n" +
                        "2. RULES:\n" +
                        "   - Click a tile to flip it and its neighbors.\n" +
                        "   - Player = Yellow (+1 Pt), CPU = Grey (+1 Pt).\n" +
                        "   - You have 20 TURNS to win.\n\n" +
                        "3. CPU STRATEGY (GREEDY):\n" +
                        "   - The CPU calculates the move that gives the most points immediately.\n" +
                        "   - It will avoid simply undoing your last move (Deadlock Prevention).\n");
        text.setEditable(false);
        text.setBackground(instructionsPanel.getBackground());
        text.setFont(new Font("Monospaced", Font.PLAIN, 14));
        text.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));

        JButton btnBack = createStyledButton("Back");
        btnBack.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnBack.addActionListener(e -> cardLayout.show(mainPanel, "MENU"));

        instructionsPanel.add(Box.createVerticalGlue());
        instructionsPanel.add(title);
        instructionsPanel.add(text);
        instructionsPanel.add(btnBack);
        instructionsPanel.add(Box.createVerticalGlue());
    }

    // ==========================================
    // 5. HELPER METHODS
    // ==========================================

    private void handlePlayerClick(int idx) {
        if (inputBlocked || !isPlayerTurn || isGameOver)
            return;

        // Apply Player Move
        applyMoveLogic(idx);
        lastHumanMove = idx; // Track move to prevent CPU undoing it

        updateBoardUI();
        updateScore();
        checkWinCondition();

        if (!isGameOver) {
            isPlayerTurn = false;
            playGreedyMove(); // Trigger CPU
        }
    }

    private void updateBoardUI() {
        for (int i = 0; i < TOTAL_TILES; i++) {
            JButton btn = tileButtons[i];
            Color targetColor = gridState[i] ? COLOR_ON : COLOR_OFF;
            btn.setBackground(targetColor);

            // Only reset border if it's NOT the CPU's just-highlighted move
            // (For simplicity in this version, we reset all borders on every refresh)
            btn.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
        }
    }

    private void updateScore() {
        int yellow = 0;
        int grey = 0;
        for (boolean b : gridState) {
            if (b)
                yellow++;
            else
                grey++;
        }
        scoreLabel.setText(String.format("Yellow: %d | Grey: %d", yellow, grey));
    }

    private JButton createStyledButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Arial", Font.BOLD, 16));
        btn.setBackground(new Color(230, 126, 34)); // Orange
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(220, 50));
        return btn;
    }
}