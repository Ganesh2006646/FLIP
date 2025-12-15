package com.flipwars;

import com.flipwars.ui.GamePanel;
import com.flipwars.ui.InstructionsPanel;
import com.flipwars.ui.MenuPanel;
import javax.swing.*;
import java.awt.*;

public class Main extends JFrame {

    private CardLayout cardLayout = new CardLayout();
    private JPanel mainPanel = new JPanel(cardLayout);
    private MenuPanel menuPanel;
    private GamePanel gamePanel;
    private InstructionsPanel instructionsPanel;

    public Main() {
        setTitle("Flip Wars - Algorithm Project");
        setSize(600, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Initialize Panels
        // Pass a Runnable to MenuPanel to start the game
        menuPanel = new MenuPanel(cardLayout, mainPanel, this::handleStartGame);
        gamePanel = new GamePanel(cardLayout, mainPanel);
        instructionsPanel = new InstructionsPanel(cardLayout, mainPanel);

        // Add to CardLayout
        mainPanel.add(menuPanel, "MENU");
        mainPanel.add(gamePanel, "GAME");
        mainPanel.add(instructionsPanel, "INSTRUCTIONS");

        add(mainPanel);
        cardLayout.show(mainPanel, "MENU");
    }

    private void handleStartGame() {
        // Pass difficulty setting from Menu to Game
        boolean isHard = menuPanel.isHardMode();
        gamePanel.startNewGame(isHard);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new Main().setVisible(true);
        });
    }
}