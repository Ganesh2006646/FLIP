package com.flipwars;

import java.util.*;

/**
 * Graph Representation using Adjacency Lists.
 * <p>
 * Models the game board as a graph where each tile is a vertex and edges
 * connect orthogonally adjacent tiles (up, down, left, right + self).
 * Neighbors are precomputed at construction for O(1) lookup during gameplay.
 * </p>
 *
 * <h2>Why a Graph?</h2>
 * <ul>
 *   <li>Flip mechanic requires knowing neighbors instantly</li>
 *   <li>DFS Cluster analysis traverses the adjacency structure</li>
 *   <li>Precomputation avoids repeated neighbor calculation</li>
 * </ul>
 *
 * @see Engine
 * @see DACAlgorithms
 */
public class Graph {
    private final int gridSize;
    private final Map<Integer, List<Integer>> adjacencyList = new HashMap<>();

    public Graph(int gridSize) {
        this.gridSize = gridSize;
        initializeGraph();
    }

    private void initializeGraph() {
        adjacencyList.clear();
        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                int id = r * gridSize + c;
                List<Integer> neighbors = new ArrayList<>();
                neighbors.add(id); // Always flip self

                // + Pattern: Orthogonal
                addIfValid(neighbors, r - 1, c); // Up
                addIfValid(neighbors, r + 1, c); // Down
                addIfValid(neighbors, r, c - 1); // Left
                addIfValid(neighbors, r, c + 1); // Right

                adjacencyList.put(id, neighbors);
            }
        }
    }

    private void addIfValid(List<Integer> list, int r, int c) {
        if (r >= 0 && r < gridSize && c >= 0 && c < gridSize) {
            list.add(r * gridSize + c);
        }
    }

    public List<Integer> getNeighbors(int tileId) {
        return adjacencyList.get(tileId);
    }
}
