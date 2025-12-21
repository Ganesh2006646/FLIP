package com.flipwars;

import java.awt.Color;
import java.util.LinkedList;

/**
 * Concept: Meta-Heuristics (Tabu Search) & Strategic Weighting.
 */
public class Rules {
    private final int tabuSize;
    private final LinkedList<Integer> tabuList = new LinkedList<>();
    private final int gridSize;

    public static final Color COLOR_PLAYER = new Color(241, 196, 15); // Yellow
    public static final Color COLOR_CPU = new Color(127, 140, 141); // Grey

    public Rules(int gridSize) {
        this.gridSize = gridSize;
        this.tabuSize = Math.max(2, (gridSize * gridSize) / 4);
    }

    public void recordMove(int tileId) {
        tabuList.remove(Integer.valueOf(tileId));
        tabuList.add(tileId);
        if (tabuList.size() > tabuSize) {
            tabuList.removeFirst();
        }
    }

    public boolean isLocked(int tileId) {
        return tabuList.contains(tileId);
    }

    public int getLockCountdown(int tileId) {
        int index = tabuList.indexOf(tileId);
        if (index == -1)
            return 0;
        return index + 1;
    }

    public void clearMemory() {
        tabuList.clear();
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
