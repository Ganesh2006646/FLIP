package com.flipwars;

import java.util.*;

// Graph Representation using Adjacency Lists — Black Hole Aware.
// Models the game board as a graph where each tile is a vertex and edges
// connect orthogonally adjacent tiles (up, down, left, right + self).
// Black Hole tiles are registered with EMPTY neighbor lists so the flip
// mechanic and DFS algorithms naturally skip them with zero code changes.
//
// Black Hole Effect on Graph Topology:
//   - Black Hole tiles have no outgoing or incoming adjacency edges
//   - Their neighbors are excluded from adjacent tiles' neighbor lists
//   - DFS Cluster algorithm flows around them without modification
//   - This demonstrates real-world irregular graph topology
public class Graph {
    private final int gridSize;
    private final Map<Integer, List<Integer>> adjacencyList = new HashMap<>();
    private final Set<Integer> blackHoles;

    // Constructs a standard graph with no black holes.
    public Graph(int gridSize) {
        this(gridSize, Collections.emptySet());
    }

    // Constructs a Black-Hole-aware graph.
    // Black hole tiles get empty adjacency lists; their IDs are also excluded
    // from their neighbors' neighbor lists, making the exclusion bidirectional.
    public Graph(int gridSize, Set<Integer> blackHoles) {
        this.gridSize = gridSize;
        this.blackHoles = blackHoles;
        initializeGraph();
    }

    private void initializeGraph() {
        adjacencyList.clear();

        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                int id = r * gridSize + c;

                // ── BLACK HOLE: register with empty list, no edges in or out ──
                if (blackHoles.contains(id)) {
                    adjacencyList.put(id, Collections.emptyList());
                    continue;
                }

                List<Integer> neighbors = new ArrayList<>();
                neighbors.add(id); // Always flip self

                // Add orthogonal neighbors — ONLY if they are NOT black holes
                addIfValid(neighbors, r - 1, c); // Up
                addIfValid(neighbors, r + 1, c); // Down
                addIfValid(neighbors, r, c - 1); // Left
                addIfValid(neighbors, r, c + 1); // Right

                adjacencyList.put(id, neighbors);
            }
        }
    }

    // Adds the neighbor at (r, c) if it is within bounds AND is not a Black Hole.
    // Skipping Black Holes here means they are never flipped as side effects.
    private void addIfValid(List<Integer> list, int r, int c) {
        if (r >= 0 && r < gridSize && c >= 0 && c < gridSize) {
            int neighbor = r * gridSize + c;
            if (!blackHoles.contains(neighbor)) {
                list.add(neighbor);
            }
        }
    }

    public List<Integer> getNeighbors(int tileId) {
        List<Integer> neighbors = adjacencyList.get(tileId);
        return (neighbors != null) ? neighbors : Collections.emptyList();
    }

    // Returns true if the given tile is a Black Hole.
    public boolean isBlackHole(int tileId) {
        return blackHoles.contains(tileId);
    }
}
