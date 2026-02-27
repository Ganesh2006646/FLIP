package com.flipwars;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 * TileButton — Custom 3D Nintendo-Style Game Tile.
 * <p>
 * Replaces the default flat OS-rendered JButton with a hand-painted
 * 3D tile using a three-layer rendering technique.
 * </p>
 *
 * <h2>Three-Layer Rendering (inside paintComponent):</h2>
 * <ol>
 * <li><b>Layer 1 — Shadow/Base:</b> A very dark version of the tile color,
 * drawn at the bottom. Creates the illusion of 3D thickness/depth.</li>
 * <li><b>Layer 2 — Face:</b> The bright, normal-colored rectangle on top.
 * Shifted DOWN by {@code yOffset} pixels when pressed (click physics).</li>
 * <li><b>Layer 3 — Glare:</b> A small semi-transparent white rectangle near
 * the top edge. Simulates glossy plastic highlight.</li>
 * </ol>
 *
 * <h2>Click Physics (yOffset):</h2>
 * <p>
 * When {@code getModel().isPressed()} is true, the Face and Glare layers
 * shift down by {@link #PRESS_DEPTH} pixels. Because Layer 1 (the Shadow)
 * stays fixed, this perfectly mimics a real keyboard key being pressed into
 * its base.
 * </p>
 *
 * <h2>Why Disable Default Paint?</h2>
 * <p>
 * Java Swing delegates button rendering to the OS Look-and-Feel by default,
 * producing flat, unthemed rectangles. By calling
 * {@code setContentAreaFilled(false)} and {@code setBorderPainted(false)},
 * and overriding {@code paintComponent}, we take full control of every pixel.
 * </p>
 */
public class TileButton extends JButton {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** Pixel depth of the 3D shadow/base layer. */
    private static final int SHADOW_DEPTH = 6;

    /** Pixels the face layer shifts down when clicked (press simulation). */
    private static final int PRESS_DEPTH = 4;

    /** Corner arc radius for rounded rectangles. */
    private static final int ARC = 14;

    /** Alpha value (0-255) for the glare highlight (semi-transparent white). */
    private static final int GLARE_ALPHA = 70;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** Current tile face color — set by the game to reflect tile ownership. */
    private Color topColor = new Color(127, 140, 141); // default: CPU grey

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a TileButton and disables all default OS-level rendering
     * so we can draw everything ourselves.
     */
    public TileButton() {
        setContentAreaFilled(false); // We will paint the background ourselves
        setBorderPainted(false); // We will paint the border ourselves
        setFocusPainted(false); // No dotted focus rectangle
        setOpaque(false); // Let parent background show outside our shape

        // Redraw on every mouse press/release so click physics responds instantly
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                repaint();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Color API
    // -------------------------------------------------------------------------

    /**
     * Sets the face color of this tile (called by Main when board state changes).
     * Triggers a repaint automatically.
     *
     * @param color The new face color (yellow for player, grey for CPU, etc.)
     */
    public void setTileColor(Color color) {
        this.topColor = color;
        repaint();
    }

    // -------------------------------------------------------------------------
    // Core Paint Logic
    // -------------------------------------------------------------------------

    /**
     * Paints the 3D tile using the three-layer technique.
     *
     * <p>
     * Paint order (back → front):
     * </p>
     * <ol>
     * <li>Shadow rectangle (dark, fixed at bottom)</li>
     * <li>Face rectangle (bright color, moves on press)</li>
     * <li>Glare rectangle (white translucent, moves with face)</li>
     * <li>Optional text label (score value, WAIT countdown)</li>
     * </ol>
     *
     * @param g Graphics context provided by Swing
     */
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();

        // ---- Anti-aliasing: smooth rounded corners, no pixel jaggies --------
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // ---- Click Physics: shift face down when pressed --------------------
        // When the user holds down the mouse button, we push the face into the
        // shadow layer by PRESS_DEPTH pixels, simulating a physical button press.
        int yOffset = getModel().isPressed() ? PRESS_DEPTH : 0;

        // ---- Derive colors ---------------------------------------------------
        Color shadowColor = topColor.darker().darker(); // ~50% darker than face
        Color glareColor = new Color(255, 255, 255, GLARE_ALPHA); // white, 27% opaque

        // =====================================================================
        // LAYER 1: SHADOW / BASE
        // Fixed position — anchored to the bottom of the tile.
        // The visual "thickness" between this and the face IS the 3D illusion.
        // =====================================================================
        g2.setColor(shadowColor);
        g2.fillRoundRect(0, SHADOW_DEPTH, w, h - SHADOW_DEPTH, ARC, ARC);

        // =====================================================================
        // LAYER 2: FACE
        // Bright tile color. Moves DOWN by yOffset on press.
        // When pressed, it overlaps the shadow less → shadow appears "exposed"
        // → creates the visual of the tile sinking into its base.
        // =====================================================================
        g2.setColor(topColor);
        g2.fillRoundRect(0, yOffset, w, h - SHADOW_DEPTH, ARC, ARC);

        // =====================================================================
        // LAYER 3: GLARE HIGHLIGHT
        // Semi-transparent white strip near the top of the face.
        // Simulates a glossy plastic surface catching the light from above.
        // Moves with the face (same yOffset) so it stays "on" the tile.
        // =====================================================================
        int glareH = (h - SHADOW_DEPTH) / 4; // glare occupies top 25% of face
        g2.setColor(glareColor);
        g2.fillRoundRect(3, yOffset + 3, w - 6, glareH, ARC, ARC);

        // =====================================================================
        // TEXT: Score value or WAIT countdown
        // Rendered on top of all layers, centered on the face.
        // =====================================================================
        String text = getText();
        if (text != null && !text.isEmpty()) {
            // HTML text is handled by Swing's own renderer if HTML tags detected
            if (text.startsWith("<html>")) {
                // Delegate to super for HTML (score + WAIT label)
                super.paintComponent(g);
            } else {
                // Plain text: manual centered draw
                g2.setFont(getFont() != null ? getFont() : new Font("Arial", Font.BOLD, 14));
                g2.setColor(getForeground() != null ? getForeground() : Color.WHITE);
                FontMetrics fm = g2.getFontMetrics();
                int tx = (w - fm.stringWidth(text)) / 2;
                int ty = yOffset + ((h - SHADOW_DEPTH - fm.getHeight()) / 2) + fm.getAscent();
                g2.drawString(text, tx, ty);
            }
        }

        g2.dispose();
    }

    /**
     * Override preferred size hint so the Layout Manager gives tiles
     * enough room to show the shadow depth below the face.
     */
    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(Math.max(d.width, 60), Math.max(d.height, 60));
    }
}
