
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.animation.FadeTransition;
import javafx.util.Duration;

import java.util.*;

/**
 * FLIP WARS - A JavaFX Semester Project
 * 
 * Student Name: [Your Name]
 * Project: 2-Player Strategy Game (Player vs Greedy CPU)
 * 
 * Description:
 * A 4x4 grid game where clicking a tile flips its color and its neighbors.
 * The goal is to maximize your color (Yellow) on the board.
 * The CPU tries to maximize its color (Grey).
 */

public class Main extends Application {

    // --- Game Constants ---
    private static final int GRID_SIZE = 4;
    private static final int TOTAL_TILES = 16;
    private static final double TILE_SIZE = 100.0;
    
    // --- Colors ---
    private static final Color COLOR_ON = Color.YELLOW; // Player's Color
    private static final Color COLOR_OFF = Color.GREY;  // CPU's Color
    private static final Color HIGHLIGHT_COLOR = Color.RED; // CPU move highlight
    private static final Color BORDER_COLOR = Color.BLACK;

    // --- Game State ---
    private boolean[] gridState = new boolean[TOTAL_TILES]; // true = Yellow, false = Grey
    private Map<Integer, List<Integer>> adjacencyList = new HashMap<>();
    private boolean isPlayerTurn = true; // Player starts ensuring fair play? Or random? Let's say Player starts.
    private boolean inputBlocked = false; // Block input during CPU turn

    // --- UI Elements ---
    private Stage primaryStage;
    private Scene menuScene;
    private Scene gameScene;
    private Rectangle[] tileRects = new Rectangle[TOTAL_TILES];
    private Label scoreLabel;
    private Label turnLabel;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        
        // 1. Initialize the Graph (Connections between tiles)
        initializeGraph();

        // 2. Create the Scenes
        createMenuScene();
        createGameScene();

        // 3. Show the Menu first
        primaryStage.setTitle("Flip Wars");
        primaryStage.setScene(menuScene);
        primaryStage.show();
    }

    // ==========================================
    //              GRAPH & LOGIC
    // ==========================================

    /**
     * Initializes the adjacency list representing the graph of the 4x4 grid.
     * Each tile connects to its Top, Bottom, Left, and Right neighbors.
     */
    private void initializeGraph() {
        // Loop through every tile index from 0 to 15
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                int id = r * GRID_SIZE + c;
                List<Integer> neighbors = new ArrayList<>();

                // Add neighbors (checking boundaries)
                if (r > 0) neighbors.add((r - 1) * GRID_SIZE + c); // Top
                if (r < GRID_SIZE - 1) neighbors.add((r + 1) * GRID_SIZE + c); // Bottom
                if (c > 0) neighbors.add(r * GRID_SIZE + (c - 1)); // Left
                if (c < GRID_SIZE - 1) neighbors.add(r * GRID_SIZE + (c + 1)); // Right
                
                // Add itself? The rules often say "tile and its neighbors". 
                // Let's include SELF in the flip logic, but usually adjacency list stores *others*.
                // I will handle "flip self" in the action method separately for clarity.
                
                adjacencyList.put(id, neighbors);
            }
        }
    }

    /**
     * Resets the board with a random starting pattern.
     */
    private void resetGame() {
        // Clear board (all false/Grey)
        for (int i = 0; i < TOTAL_TILES; i++) {
            gridState[i] = false;
        }

        // Randomly assign patterns (Cross or Plus)
        // Simple logic: Pick a few random centers and apply the flip mechanic
        Random rand = new Random();
        int randomMoves = 3 + rand.nextInt(3); // 3 to 5 random setups

        for (int i = 0; i < randomMoves; i++) {
            int randomTile = rand.nextInt(TOTAL_TILES);
            applyMoveLogic(randomTile);
        }

        isPlayerTurn = true;
        updateBoardUI();
        updateScore();
        turnLabel.setText("Turn: Player (Yellow)");
    }

    /**
     * The Core Logic: Flips a tile and its neighbors.
     */
    private void applyMoveLogic(int tileIndex) {
        // Toggle the clicked tile
        gridState[tileIndex] = !gridState[tileIndex];

        // Toggle all neighbors defined in our graph
        List<Integer> neighbors = adjacencyList.get(tileIndex);
        for (int neighbor : neighbors) {
            gridState[neighbor] = !gridState[neighbor];
        }
    }

    // ==========================================
    //              ARTIFICIAL INTELLIGENCE
    // ==========================================

    /**
     * Greedy CPU Logic:
     * 1. Loop through all 16 tiles.
     * 2. Simulate what happens if we click that tile.
     * 3. Calculate the score (CPU wants more GREY).
     * 4. Pick the move that results in the most GREY tiles.
     */
    private void playGreedyMove() {
        inputBlocked = true;
        turnLabel.setText("Turn: CPU is thinking...");

        // Run in a separate thread/timer so UI doesn't freeze and we can see the delay
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Artificial delay to make it feel like "thinking"
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            int bestMove = -1;
            int bestGreyCount = -1;

            // --- GREEDY SEARCH ---
            for (int i = 0; i < TOTAL_TILES; i++) {
                // 1. Copy current state to simulate
                boolean[] simulationState = gridState.clone();

                // 2. Simulate flip on copy
                simulationState[i] = !simulationState[i]; // Flip self
                for (int neighbor : adjacencyList.get(i)) {
                    simulationState[neighbor] = !simulationState[neighbor]; // Flip neighbors
                }

                // 3. Count Score (CPU wants GREY, which is 'false')
                int greyCount = 0;
                for (boolean b : simulationState) {
                    if (!b) greyCount++;
                }

                // 4. Check if this is better
                if (greyCount > bestGreyCount) {
                    bestGreyCount = greyCount;
                    bestMove = i;
                }
            }

            // --- EXECUTE MOVE ---
            final int chosenMove = bestMove;
            Platform.runLater(() -> {
                // Highlight the move for user visibility
                highlightTile(chosenMove);
                
                // Apply the move after a short visual delay
                // Using valid JavaFX concurrency pattern or simpler delay:
                applyMoveLogic(chosenMove);
                updateBoardUI();
                updateScore();
                
                isPlayerTurn = true;
                inputBlocked = false;
                turnLabel.setText("Turn: Player (Yellow)");
            });

        }).start();
    }

    // ==========================================
    //              UI & SCENES
    // ==========================================

    private void createMenuScene() {
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #2c3e50;"); // Nice dark blue/grey

        // Title
        Text title = new Text("FLIP WARS");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 40));
        title.setFill(Color.WHITE);

        // Buttons
        Button btnStart = createStyledButton("Start Game");
        btnStart.setOnAction(e -> {
            resetGame();
            primaryStage.setScene(gameScene);
        });

        Button btnInstruct = createStyledButton("Instructions");
        btnInstruct.setOnAction(e -> showInstructions());

        root.getChildren().addAll(title, btnStart, btnInstruct);
        menuScene = new Scene(root, 600, 500);
    }

    private void createGameScene() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #34495e;");

        // --- Top: Scoreboard ---
        HBox topBar = new HBox(20);
        topBar.setAlignment(Pos.CENTER);
        topBar.setPadding(new javafx.geometry.Insets(15));
        topBar.setStyle("-fx-background-color: #2c3e50;");

        scoreLabel = new Label("Yellow: 0 | Grey: 0");
        scoreLabel.setTextFill(Color.WHITE);
        scoreLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        turnLabel = new Label("Turn: Player");
        turnLabel.setTextFill(Color.LIGHTGREEN);
        turnLabel.setFont(Font.font("Arial", 16));

        topBar.getChildren().addAll(scoreLabel, turnLabel);
        root.setTop(topBar);

        // --- Center: The Grid ---
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(10);

        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                int idx = r * GRID_SIZE + c;

                // Create the visual tile
                Rectangle rect = new Rectangle(TILE_SIZE, TILE_SIZE);
                rect.setArcWidth(20);
                rect.setArcHeight(20);
                rect.setStroke(BORDER_COLOR);
                rect.setStrokeWidth(3);
                
                tileRects[idx] = rect;

                // StackPane to hold the rect (and maybe text if we wanted)
                StackPane tileLayout = new StackPane(rect);

                // Mouse Click Handler
                tileLayout.setOnMouseClicked(e -> handlePlayerClick(idx));

                grid.add(tileLayout, c, r);
            }
        }
        root.setCenter(grid);

        // --- Bottom: Back Button ---
        HBox bottomBar = new HBox();
        bottomBar.setAlignment(Pos.CENTER);
        bottomBar.setPadding(new javafx.geometry.Insets(15));
        
        Button btnBack = createStyledButton("Back to Menu");
        btnBack.setOnAction(e -> primaryStage.setScene(menuScene));
        
        bottomBar.getChildren().add(btnBack);
        root.setBottom(bottomBar);

        gameScene = new Scene(root, 600, 700);
    }

    private void showInstructions() {
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new javafx.geometry.Insets(20));
        root.setStyle("-fx-background-color: #ecf0f1;"); 

        Label title = new Label("How to Play");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 24));

        TextArea text = new TextArea(
            "1. The game is played on a 4x4 Grid.\n" +
            "2. Clicking a tile flips its color and its neighbors'.\n" +
            "3. Colors:\n" + 
            "   - YELLOW = ON (+1 Pt for You)\n" +
            "   - GREY   = OFF (+1 Pt for CPU)\n\n" +
            "4. Start by making your move.\n" +
            "5. The CPU will then make a Greedy Move (trying to maximize Grey).\n\n" +
            "Beat the CPU by covering the board in Yellow!"
        );
        text.setEditable(false);
        text.setWrapText(true);
        text.setMaxWidth(400);
        text.setMaxHeight(200);

        Button btnBack = createStyledButton("Back");
        btnBack.setOnAction(e -> primaryStage.setScene(menuScene)); // Reuse existing scene

        root.getChildren().addAll(title, text, btnBack);
        
        // Temporarily change scene content to instructions? 
        // Or better, just swap the scene on the stage?
        // Let's create a dedicated scene for Instructions to keep it clean.
        Scene instructionScene = new Scene(root, 600, 500);
        primaryStage.setScene(instructionScene);
    }

    // ==========================================
    //              GAMEPLAY HELPERS
    // ==========================================

    private void handlePlayerClick(int idx) {
        if (inputBlocked || !isPlayerTurn) return;

        // Player Move
        applyMoveLogic(idx);
        updateBoardUI();
        updateScore();

        // Switch Logic
        isPlayerTurn = false;
        
        // Trigger CPU
        playGreedyMove();
    }

    private void updateBoardUI() {
        for (int i = 0; i < TOTAL_TILES; i++) {
            Rectangle rect = tileRects[i];
            Color newColor = gridState[i] ? COLOR_ON : COLOR_OFF;
            
            // Only animate if color is different (optional optimization)
            if (!rect.getFill().equals(newColor)) {
                // Simple Fade/Color Transition
                FadeTransition ft = new FadeTransition(Duration.millis(300), rect);
                ft.setFromValue(0.5);
                ft.setToValue(1.0);
                ft.play();
                
                rect.setFill(newColor);
                
                // Reset border (remove highlight from CPU move)
                rect.setStroke(BORDER_COLOR);
            }
        }
    }
    
    // Highlights the tile the CPU selected
    private void highlightTile(int idx) {
        tileRects[idx].setStroke(HIGHLIGHT_COLOR);
    }

    private void updateScore() {
        int yellow = 0;
        int grey = 0;
        for (boolean b : gridState) {
            if (b) yellow++;
            else grey++;
        }
        scoreLabel.setText(String.format("Yellow (You): %d | Grey (CPU): %d", yellow, grey));
    }

    // ==========================================
    //              STYLE UTILS
    // ==========================================
    
    private Button createStyledButton(String text) {
        Button btn = new Button(text);
        btn.setStyle(
            "-fx-background-color: #e67e22;" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 16px;" +
            "-fx-font-weight: bold;" + 
            "-fx-background-radius: 10;"
        );
        btn.setMinWidth(150);
        return btn;
    }
}
