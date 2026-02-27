package com.flipwars;

import java.awt.Color;
import java.util.*;

/**
 * Game Rules Engine — Tabu Search & Strategic Weighting.
 * <p>
 * Manages the lock/tabu mechanic and assigns strategic values to tiles.
 * Uses {@link LinkedHashSet} for O(1) lookup instead of O(n) with LinkedList.
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
 * </ul>
 *
 * @see Engine
 */
public class Rules {
    private final int tabuSize;
    private final LinkedHashSet<Integer> tabuSet = new LinkedHashSet<>(); // O(1) contains()
    private final int gridSize;

    /**
     * DYNAMIC OBSTACLES — "Black Hole" tiles.
     * Dead tiles cannot be clicked, flipped, or owned by any player.
     * They persist for the entire game and are regenerated each new game.
     * Proves that our Graph + DFS Cluster algorithms handle irregular grids.
     */
    private final Set<Integer> deadTiles = new HashSet<>();

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

    // ---- Black Hole / Dead Tile API ----

    /** Permanently marks a tile as a dead Black Hole for this game. */
    public void addDeadTile(int id) {
        deadTiles.add(id);
    }

    /** Returns true if this tile is a Black Hole (permanently unplayable). */
    public boolean isDeadTile(int id) {
        return deadTiles.contains(id);
    }

    /** Clears all Black Holes — call at the start of each new game. */
    public void clearDeadTiles() {
        deadTiles.clear();
    }

    /** Returns an immutable view of all current dead tile IDs. */
    public Set<Integer> getDeadTiles() {
        return Collections.unmodifiableSet(deadTiles);
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
