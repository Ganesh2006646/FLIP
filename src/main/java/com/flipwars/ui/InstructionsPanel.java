package com.flipwars.ui;

import com.flipwars.constants.GameConstants;
import javax.swing.*;
import java.awt.*;

public class InstructionsPanel extends JPanel {

    public InstructionsPanel(CardLayout cardLayout, JPanel mainPanel) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(new Color(236, 240, 241));

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
                        "3. CPU STRATEGY:\n" +
                        "   - Hard Mode: The CPU calculates the move that gives the most points.\n" +
                        "   - Easy Mode: The CPU flips 50% randomly.\n");
        text.setEditable(false);
        text.setBackground(getBackground());
        text.setFont(new Font("Monospaced", Font.PLAIN, 14));
        text.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));

        JButton btnBack = UIUtils.createStyledButton("Back");
        btnBack.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnBack.addActionListener(e -> cardLayout.show(mainPanel, "MENU"));

        add(Box.createVerticalGlue());
        add(title);
        add(text);
        add(btnBack);
        add(Box.createVerticalGlue());
    }
}
