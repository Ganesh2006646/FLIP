package com.flipwars;

import java.util.*;

/**
 * Concept: Graph Representation & Adjacency Lists.
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
