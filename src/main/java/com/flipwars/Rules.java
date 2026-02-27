package com.flipwars;

import java.awt.Color;
import java.util.*;

/**
 * Game Rules Engine — Tabu Search, Strategic Weighting, Black Hole Awareness.
 * <p>
 * Manages the lock/tabu mechanic and assigns strategic values to tiles.
 * Black Hole tiles return a value of 0.0 and are never added to the Tabu set.
 * </p>
 *
 * <h2>Lock Mechanic (Tabu Search):</h2>
 * <ul>
 * <li>After a tile is clicked, it gets locked for several turns</li>
 * <li>Prevents infinite flip loops and adds strategic depth</li>
 * <li>Tabu size scales with grid: max(2, gridSize^2 / 4)</li>
 * </ul>
 *
 * <h2>Strategic Tile Values:</h2>
 * <ul>
 * <li>Corners: +25 (most valuable)</li>
 * <li>Edges: +15</li>
 * <li>Standard: +5</li>
 * <li>Near-Corners (Traps): -5 (dangerous positions)</li>
 * <li>Black Holes: 0.0 (neutral — excluded from scoring)</li>
 * </ul>
 *
 * @see Engine
 */
public class Rules {
    private final int tabuSize;
    private final LinkedHashSet<Integer> tabuSet = new LinkedHashSet<>(); // O(1) contains()
    private final int gridSize;
    private final Set<Integer> blackHoles;

    public static final Color COLOR_PLAYER = new Color(241, 196, 15); // Yellow
    public static final Color COLOR_CPU = new Color(127, 140, 141); // Grey

    /** Constructs Rules with no black holes. */
    public Rules(int gridSize) {
        this(gridSize, Collections.emptySet());
    }

    /**
     * Constructs Rules with Black Hole awareness.
     * Black Holes are never locked (can't be clicked) and always score 0.
     *
     * @param gridSize   Size of the grid
     * @param blackHoles Set of tile IDs that are permanently dead
     */
    public Rules(int gridSize, Set<Integer> blackHoles) {
        this.gridSize = gridSize;
        this.blackHoles = blackHoles;
        this.tabuSize = Math.max(2, (gridSize * gridSize) / 4);
    }

    public void recordMove(int tileId) {
        if (blackHoles.contains(tileId))
            return; // Black holes are never recorded
        tabuSet.remove(tileId);
        tabuSet.add(tileId);
        if (tabuSet.size() > tabuSize) {
            Iterator<Integer> it = tabuSet.iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
    }

    public boolean isLocked(int tileId) {
        // Black holes always behave as locked (unclickable)
        if (blackHoles.contains(tileId))
            return true;
        return tabuSet.contains(tileId); // O(1)
    }

    public int getLockCountdown(int tileId) {
        if (blackHoles.contains(tileId))
            return 0;
        if (!tabuSet.contains(tileId))
            return 0;
        int index = 0;
        for (Integer id : tabuSet) {
            if (id.equals(tileId))
                return index + 1;
            index++;
        }
        return 0;
    }

    public void clearMemory() {
        tabuSet.clear();
    }

    /**
     * Returns the strategic tile value.
     * Black Holes always return 0.0 — they have no ownership and contribute
     * no score, preventing the AI from treating them as free CPU tiles.
     */
    public double getTileStrategicValue(int id) {
        // BLACK HOLE: zero value — ignored by all scoring algorithms
        if (blackHoles.contains(id))
            return 0.0;

        int r = id / gridSize;
        int c = id % gridSize;

        if ((r == 0 || r == gridSize - 1) && (c == 0 || c == gridSize - 1))
            return 25.0; // Corners
        if (r == 0 || r == gridSize - 1 || c == 0 || c == gridSize - 1)
            return 15.0; // Edges
        if ((r <= 1 || r >= gridSize - 2) && (c <= 1 || c >= gridSize - 2))
            return -5.0; // Traps
        return 5.0; // Standard
    }

    /** Returns true if the given tile is a Black Hole. */
    public boolean isBlackHole(int tileId) {
        return blackHoles.contains(tileId);
    }
}
