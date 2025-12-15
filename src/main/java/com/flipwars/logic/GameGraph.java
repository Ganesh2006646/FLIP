package com.flipwars.logic;

import com.flipwars.constants.GameConstants;
import java.util.*;

public class GameGraph {
    private Map<Integer, List<Integer>> adjacencyList = new HashMap<>();

    public GameGraph() {
        initializeGraph();
    }

    private void initializeGraph() {
        int size = GameConstants.GRID_SIZE;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int id = r * size + c;
                List<Integer> neighbors = new ArrayList<>();

                // Standard Cross Pattern (Up, Down, Left, Right)
                if (r > 0)
                    neighbors.add((r - 1) * size + c);
                if (r < size - 1)
                    neighbors.add((r + 1) * size + c);
                if (c > 0)
                    neighbors.add(r * size + (c - 1));
                if (c < size - 1)
                    neighbors.add(r * size + (c + 1));

                // Add Self
                neighbors.add(id);

                adjacencyList.put(id, neighbors);
            }
        }
    }

    public List<Integer> getNeighbors(int tileIndex) {
        return adjacencyList.getOrDefault(tileIndex, new ArrayList<>());
    }
}
