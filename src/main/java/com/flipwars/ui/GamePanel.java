package com.flipwars.ui;

import com.flipwars.constants.GameConstants;
import com.flipwars.logic.AILogic;
import com.flipwars.logic.GameGraph;

import javax.swing.*;
import java.awt.*;
import java.util.Random;

public class GamePanel extends JPanel {
    private CardLayout cardLayout;
    private JPanel mainContainer;

    private JButton[] tileButtons = new JButton[GameConstants.TOTAL_TILES];
    private JLabel scoreLabel;
    private JLabel turnLabel;

    // Logic State
    private boolean[] gridState = new boolean[GameConstants.TOTAL_TILES];
    private GameGraph gameGraph;

    private boolean isPlayerTurn = true;
    private boolean inputBlocked = false;
    private boolean isGameOver = false;
    private boolean isHardMode = true;

    private volatile int gameRoundId = 0;
    private int turnsPlayed = 0;
    private int lastHumanMove = -1;

    public GamePanel(CardLayout cardLayout, JPanel mainContainer) {
        this.cardLayout = cardLayout;
        this.mainContainer = mainContainer;
        this.gameGraph = new GameGraph();

        setLayout(new BorderLayout());
        setBackground(GameConstants.BG_COLOR_LIGHT);

        createUI();
    }

    public void startNewGame(boolean hardMode) {
        this.isHardMode = hardMode;

        // Reset State
        for (int i = 0; i < GameConstants.TOTAL_TILES; i++) {
            gridState[i] = false;
        }

        gameRoundId++;
        turnsPlayed = 0;
        lastHumanMove = -1;
        isGameOver = false;
        inputBlocked = false;
        isPlayerTurn = true;

        // Randomize
        Random rand = new Random();
        int randomMoves = 4 + rand.nextInt(3);
        for (int i = 0; i < randomMoves; i++) {
            applyMoveLogic(rand.nextInt(GameConstants.TOTAL_TILES), false);
        }

        updateBoardUI();
        updateScore();
        turnLabel.setText("Turn: Player (Yellow) - Round 1/" + GameConstants.MAX_TURNS);
    }

    private void createUI() {
        // Top Bar
        JPanel topBar = new JPanel();
        topBar.setBackground(GameConstants.BG_COLOR_DARK);
        topBar.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        scoreLabel = new JLabel("Yellow: 0 | Grey: 0");
        scoreLabel.setForeground(GameConstants.TEXT_COLOR);
        scoreLabel.setFont(new Font("Arial", Font.BOLD, 20));

        turnLabel = new JLabel("Turn: Player");
        turnLabel.setForeground(Color.GREEN);
        turnLabel.setFont(new Font("Arial", Font.PLAIN, 16));

        topBar.add(scoreLabel);
        topBar.add(Box.createHorizontalStrut(30));
        topBar.add(turnLabel);

        add(topBar, BorderLayout.NORTH);

        // Grid
        JPanel gridPanel = new JPanel(new GridLayout(GameConstants.GRID_SIZE, GameConstants.GRID_SIZE, 8, 8));
        gridPanel.setBackground(GameConstants.BG_COLOR_LIGHT);
        gridPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        for (int i = 0; i < GameConstants.TOTAL_TILES; i++) {
            JButton btn = new JButton();
            btn.setFocusPainted(false);
            btn.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));

            final int idx = i;
            btn.addActionListener(e -> handlePlayerClick(idx));

            tileButtons[i] = btn;
            gridPanel.add(btn);
        }
        add(gridPanel, BorderLayout.CENTER);

        // Bottom Bar
        JPanel bottomBar = new JPanel();
        bottomBar.setBackground(GameConstants.BG_COLOR_DARK);
        bottomBar.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        JButton btnHint = UIUtils.createStyledButton("Get Hint");
        btnHint.setBackground(new Color(46, 204, 113));
        btnHint.setFont(new Font("Arial", Font.BOLD, 14));
        btnHint.addActionListener(e -> showHint());

        JButton btnBack = UIUtils.createStyledButton("Back to Menu");
        btnBack.setFont(new Font("Arial", Font.BOLD, 14));
        btnBack.addActionListener(e -> cardLayout.show(mainContainer, "MENU"));

        bottomBar.add(btnHint);
        bottomBar.add(Box.createHorizontalStrut(20));
        bottomBar.add(btnBack);

        add(bottomBar, BorderLayout.SOUTH);
    }

    private void handlePlayerClick(int idx) {
        if (inputBlocked || !isPlayerTurn || isGameOver)
            return;

        applyMoveLogic(idx, true);
        lastHumanMove = idx;

        updateBoardUI();
        updateScore();
        checkWinCondition();

        if (!isGameOver) {
            isPlayerTurn = false;
            triggerCPUTurn();
        }
    }

    private void applyMoveLogic(int tileIndex, boolean playSound) {
        if (playSound) {
            Toolkit.getDefaultToolkit().beep();
        }
        for (int target : gameGraph.getNeighbors(tileIndex)) {
            gridState[target] = !gridState[target];
        }
    }

    private void triggerCPUTurn() {
        if (isGameOver)
            return;

        inputBlocked = true;
        turnLabel.setText("Turn: CPU is thinking...");

        final int currentRound = gameRoundId;

        new Thread(() -> {
            if (gameRoundId != currentRound)
                return;
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
            }

            int bestMove;
            boolean playRandomly = !isHardMode && Math.random() < 0.5;

            if (playRandomly) {
                bestMove = AILogic.getRandomMove();
            } else {
                bestMove = AILogic.getGreedyMove(gridState, gameGraph, false, lastHumanMove, turnsPlayed);
            }

            final int chosenMove = bestMove;

            SwingUtilities.invokeLater(() -> {
                if (currentRound != gameRoundId || isGameOver)
                    return;

                tileButtons[chosenMove].setBorder(BorderFactory.createLineBorder(GameConstants.HIGHLIGHT_COLOR, 4));
                applyMoveLogic(chosenMove, true);
                turnsPlayed++;

                updateBoardUI();
                updateScore();
                checkWinCondition();

                if (!isGameOver) {
                    isPlayerTurn = true;
                    inputBlocked = false;
                    turnLabel.setText(
                            "Turn: Player (Yellow) - Round " + (turnsPlayed / 2 + 1) + "/" + GameConstants.MAX_TURNS);
                }
            });
        }).start();
    }

    private void showHint() {
        if (isGameOver || inputBlocked)
            return;
        int bestMove = AILogic.getGreedyMove(gridState, gameGraph, true, -1, turnsPlayed); // lastHumanMove irrelevant
                                                                                           // for player hint
        if (bestMove != -1) {
            tileButtons[bestMove].setBorder(BorderFactory.createLineBorder(GameConstants.HINT_COLOR, 4));
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

        if (yellow == GameConstants.TOTAL_TILES) {
            winner = "Perfect Victory! HUMAN WINS!";
            gameEnded = true;
        } else if (grey == GameConstants.TOTAL_TILES) {
            winner = "Perfect Victory! CPU WINS!";
            gameEnded = true;
        } else if (turnsPlayed >= GameConstants.MAX_TURNS) {
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

    private void updateBoardUI() {
        for (int i = 0; i < GameConstants.TOTAL_TILES; i++) {
            JButton btn = tileButtons[i];
            Color targetColor = gridState[i] ? GameConstants.COLOR_ON : GameConstants.COLOR_OFF;
            btn.setBackground(targetColor);
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
}
