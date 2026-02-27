package com.flipwars;

import javax.swing.*;
import java.awt.*;

/**
 * TileButton — "Candy Plastic" 3D Nintendo-Style Game Tile.
 *
 * <h2>Four-Layer Rendering:</h2>
 * <ol>
 * <li><b>Shadow/Base:</b> Dark rounded rect anchored at bottom — 3D thickness
 * illusion</li>
 * <li><b>Face:</b> Bright tile color from getBackground() — shifts down on
 * press</li>
 * <li><b>Inner Bevel:</b> Faint white rounded stroke — adds pop and rim
 * depth</li>
 * <li><b>Pill Glare:</b> Thin soft white pill at the top — shiny plastic
 * highlight</li>
 * </ol>
 *
 * <p>
 * Press physics: {@code yOffset = depth/2} when mouse is down.
 * Face + Bevel + Glare all shift down; Shadow stays fixed → tile appears to
 * sink.
 * </p>
 */
public class TileButton extends JButton {

    public TileButton() {
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();

        // Highest quality rendering — smooth curves and crisp text
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();
        int arc = 40; // Extremely rounded, bubble-tile corners
        int depth = 16; // Thick 3D base (pixel height of the shadow)

        // --- Click Physics ---
        // When pressed, shift face layers down so the tile appears to sink into its
        // base
        int yOffset = getModel().isPressed() ? depth / 2 : 0;

        Color topColor = getBackground(); // Set externally via setBackground()
        Color shadowColor = topColor.darker().darker();

        // =====================================================================
        // LAYER 1: Shadow / Base (fixed — creates 3D depth illusion)
        // =====================================================================
        g2.setColor(shadowColor);
        g2.fillRoundRect(0, yOffset + depth, width, height - depth, arc, arc);

        // =====================================================================
        // LAYER 2: Main Face (moves down on press)
        // =====================================================================
        g2.setColor(topColor);
        g2.fillRoundRect(0, yOffset, width, height - depth, arc, arc);

        // =====================================================================
        // LAYER 3: Inner Bevel / Rim
        // A very faint white ring just inside the top face.
        // Gives the tile extra depth, like a raised plastic edge.
        // =====================================================================
        g2.setColor(new Color(255, 255, 255, 60));
        g2.setStroke(new BasicStroke(3f));
        g2.drawRoundRect(2, yOffset + 2, width - 4, height - depth - 4, arc, arc);

        // =====================================================================
        // LAYER 4: Pill-Shaped Glare
        // Thin, soft, bright-white pill near the top of the face.
        // Simulates a light source reflecting off shiny Nintendo plastic.
        // Moves with the face (same yOffset) so it stays "painted on" the tile.
        // =====================================================================
        g2.setColor(new Color(255, 255, 255, 140));
        int glareW = width - 40;
        int glareH = 12;
        int glareX = 20;
        int glareY = yOffset + 8;
        g2.fillRoundRect(glareX, glareY, glareW, glareH, 10, 10);

        g2.dispose();

        // Let Swing draw the text label (score, WAIT countdown) on top of our layers
        super.paintComponent(g);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(Math.max(d.width, 60), Math.max(d.height, 60));
    }
}
