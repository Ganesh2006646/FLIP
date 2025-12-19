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

    public void clearMemory() {
        tabuList.clear();
    }

    /**
     * Strategic Heat Map Evaluation
     */
    public double getTileStrategicValue(int id) {
        int r = id / gridSize;
        int c = id % gridSize;

        // Corners: Strategically supreme
        if ((r == 0 || r == gridSize - 1) && (c == 0 || c == gridSize - 1))
            return 15.0;

        // Edges: Strong defensive positions
        if (r == 0 || r == gridSize - 1 || c == 0 || c == gridSize - 1)
            return 3.0;

        // Near-Corners: Dangerous "Traps"
        if ((r <= 1 || r >= gridSize - 2) && (c <= 1 || c >= gridSize - 2))
            return -2.0;

        return 0.0;
    }

    public LinkedList<Integer> getTabuList() {
        return tabuList;
    }
}
