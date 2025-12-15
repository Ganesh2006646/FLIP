package com.flipwars.ui;

import com.flipwars.constants.GameConstants;
import javax.swing.*;
import java.awt.*;

public class MenuPanel extends JPanel {
    private JCheckBox hardModeCheckbox;

    public MenuPanel(CardLayout cardLayout, JPanel mainPanel, Runnable onStartGame) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(GameConstants.BG_COLOR_DARK);

        JLabel title = new JLabel("FLIP WARS");
        title.setFont(new Font("Verdana", Font.BOLD, 48));
        title.setForeground(new Color(241, 196, 15));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Greedy Algorithm Project");
        subtitle.setFont(new Font("Arial", Font.PLAIN, 18));
        subtitle.setForeground(Color.LIGHT_GRAY);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton btnStart = UIUtils.createStyledButton("Start Game");
        btnStart.addActionListener(e -> {
            onStartGame.run();
            cardLayout.show(mainPanel, "GAME");
        });

        JButton btnInstruct = UIUtils.createStyledButton("Instructions");
        btnInstruct.addActionListener(e -> cardLayout.show(mainPanel, "INSTRUCTIONS"));

        hardModeCheckbox = new JCheckBox("Hard Mode (Greedy AI)");
        hardModeCheckbox.setSelected(true);
        hardModeCheckbox.setFont(new Font("Arial", Font.BOLD, 16));
        hardModeCheckbox.setForeground(Color.WHITE);
        hardModeCheckbox.setBackground(GameConstants.BG_COLOR_DARK);
        hardModeCheckbox.setAlignmentX(Component.CENTER_ALIGNMENT);

        add(Box.createVerticalGlue());
        add(title);
        add(Box.createRigidArea(new Dimension(0, 10)));
        add(subtitle);
        add(Box.createRigidArea(new Dimension(0, 50)));
        add(btnStart);
        add(Box.createRigidArea(new Dimension(0, 15)));
        add(hardModeCheckbox);
        add(Box.createRigidArea(new Dimension(0, 15)));
        add(btnInstruct);
        add(Box.createVerticalGlue());
    }

    public boolean isHardMode() {
        return hardModeCheckbox.isSelected();
    }
}
