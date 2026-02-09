package com.flipwars;

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.Iterator;

/**
 * Tabu Search & Strategic Weighting.
 * Uses LinkedHashSet for O(1) lookup instead of O(n) LinkedList.
 */
public class Rules {
    private final int tabuSize;
    private final LinkedHashSet<Integer> tabuSet = new LinkedHashSet<>(); // O(1) contains()
    private final int gridSize;

    public static final Color COLOR_PLAYER = new Color(241, 196, 15); // Yellow
    public static final Color COLOR_CPU = new Color(127, 140, 141); // Grey

    public Rules(int gridSize) {
        this.gridSize = gridSize;
        this.tabuSize = Math.max(2, (gridSize * gridSize) / 4);
    }

    public void recordMove(int tileId) {
        // Remove if exists (to maintain order)
        tabuSet.remove(tileId);
        tabuSet.add(tileId);

        // Remove oldest if over capacity
        if (tabuSet.size() > tabuSize) {
            Iterator<Integer> it = tabuSet.iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
    }

    public boolean isLocked(int tileId) {
        return tabuSet.contains(tileId); // O(1) instead of O(n)!
    }

    public int getLockCountdown(int tileId) {
        if (!tabuSet.contains(tileId))
            return 0;

        int index = 0;
        for (Integer id : tabuSet) {
            if (id == tileId)
                return index + 1;
            index++;
        }
        return 0;
    }

    public void clearMemory() {
        tabuSet.clear();
    }

    public double getTileStrategicValue(int id) {
        int r = id / gridSize;
        int c = id % gridSize;

        // Corners: 25.0
        if ((r == 0 || r == gridSize - 1) && (c == 0 || c == gridSize - 1))
            return 25.0;

        // Edges: 15.0
        if (r == 0 || r == gridSize - 1 || c == 0 || c == gridSize - 1)
            return 15.0;

        // Near-Corners (Traps): -5.0
        if ((r <= 1 || r >= gridSize - 2) && (c <= 1 || c >= gridSize - 2))
            return -5.0;

        return 5.0; // Standard
    }
}
