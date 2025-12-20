package com.flipwars;

import java.awt.Color;
import java.util.LinkedList;

/**
 * MEMBER 3: CONSTRAINT & HEURISTIC MANAGER
 * Concept: Meta-Heuristics (Tabu Search) & Strategic Weighting.
 */
public class StrategyHeuristics {
    private static final int TABU_SIZE = 8;
    private final LinkedList<Integer> tabuList = new LinkedList<>();
    private final int gridSize;

    // Colors moved here for central access if needed, but primarily used for AI
    // logic
    public static final Color COLOR_PLAYER = new Color(241, 196, 15); // Yellow
    public static final Color COLOR_CPU = new Color(127, 140, 141); // Grey

    public StrategyHeuristics(int gridSize) {
        this.gridSize = gridSize;
    }

    public void recordMove(int tileId) {
        tabuList.remove(Integer.valueOf(tileId));
        tabuList.add(tileId);
        if (tabuList.size() > TABU_SIZE) {
            tabuList.removeFirst();
        }
    }

    public boolean isLocked(int tileId) {
        return tabuList.contains(tileId);
    }

    /**
     * Returns the number of moves remaining until this tile is unlocked.
     * 1 means it will be unlocked on the next recorded move.
     * Returns 0 if not locked.
     */
    public int getLockCountdown(int tileId) {
        int index = tabuList.indexOf(tileId);
        if (index == -1)
            return 0;

        // If the list is full, index 0 is removed next (1 move left)
        // If the list isn't full yet, it will take (TABU_SIZE - currentSize + 1 +
        // index) moves?
        // Actually, in Tabu Search, it's simpler: it's just the position in the "queue"
        return index + 1;
    }

    public void clearMemory() {
        tabuList.clear();
    }

    /**
     * Strategic Heat Map Evaluation
     * Now using clean multiples of 5 as requested.
     */
    public double getTileStrategicValue(int id) {
        int r = id / gridSize;
        int c = id % gridSize;

        // Corners: Strategically supreme
        if ((r == 0 || r == gridSize - 1) && (c == 0 || c == gridSize - 1))
            return 25.0;

        // Edges: Strong defensive positions
        if (r == 0 || r == gridSize - 1 || c == 0 || c == gridSize - 1)
            return 15.0;

        // Near-Corners: Dangerous "Traps"
        if ((r <= 1 || r >= gridSize - 2) && (c <= 1 || c >= gridSize - 2))
            return -5.0;

        return 5.0; // Standard base tile value
    }

    public LinkedList<Integer> getTabuList() {
        return tabuList;
    }
}
