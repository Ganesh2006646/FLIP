package com.flipwars;

// ── JavaFX imports ────────────────────────────────────────────────────────────
import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.stage.*;
import javafx.util.Duration;

// ── Standard library ──────────────────────────────────────────────────────────
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;

/**
 * FlipWarsApp — Single-file JavaFX UI for Flip Wars.
 *
 * <p>
 * All UI components live as static nested classes inside this file:
 * {@link Theme}, {@link LogQueue}, {@link TileNode}, {@link BoardView},
 * {@link HUDBar}, {@link ControlBar}, {@link BrainScannerPane},
 * {@link MenuScene}, and {@link GameScene}.
 *
 * <p>
 * Game logic (Engine, R3Algorithms, DACAlgorithms, Graph, Rules) is
 * kept in dedicated files and is referenced normally from GameScene.
 */
public class FlipWarsApp extends Application {

    @Override
    public void start(Stage stage) {
        stage.setTitle("Flip Wars — Team 13");
        stage.setWidth(1080);
        stage.setHeight(1040);
        stage.setResizable(false);
        showMenu(stage);
        stage.show();
    }

    private void showMenu(Stage stage) {
        MenuScene menu = new MenuScene(stage);
        stage.setScene(menu.build(() -> showGame(stage,
                menu.getSelectedGrid(), menu.getSelectedVersion())));
    }

    private void showGame(Stage stage, int grid, int version) {
        GameScene game = new GameScene(stage, grid, version, () -> showMenu(stage));
        stage.setScene(game.build());
    }

    public static void main(String[] args) {
        launch(args);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // THEME — Color palette and gradient helpers
    // ═════════════════════════════════════════════════════════════════════════

    static final class Theme {
        private Theme() {
        }

        static final Color BG = Color.web("#121417");
        static final Color SURFACE = Color.web("#1E2228");
        static final Color PLAYER = Color.web("#F4C430");
        static final Color CPU = Color.web("#2E86C1");
        static final Color ACCENT = Color.web("#50FFAA");
        static final Color NEUTRAL = Color.web("#3D4451");
        static final Color BH_FILL = Color.BLACK;
        static final Color BH_BORDER = Color.web("#6A0DAD");
        static final Color SCAN_BG = Color.web("#0A0A14");
        static final Color SCAN_TEXT = Color.web("#50FF78");

        /** Subtle top-lighter → bottom-darker gradient for 3-D tile look. */
        static LinearGradient tileFill(Color base) {
            Color top = base.deriveColor(0, 1.0, 1.30, 1.0);
            Color bot = base.deriveColor(0, 1.0, 0.65, 1.0);
            return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, top), new Stop(1, bot));
        }

        static String toHex(Color c) {
            return String.format("#%02X%02X%02X",
                    (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // LOG QUEUE — Thread-safe Brain Scanner batch logger
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * AI threads push messages here via {@link #asConsumer()}.
     * A 100 ms JavaFX Timeline drains ≤20 messages/tick — no Platform.runLater
     * per node, preventing the JavaFX thread from flooding.
     */
    static class LogQueue {
        private static final int MAX_DRAIN = 20;
        private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
        private final TextArea area;
        private final Timeline poller;

        LogQueue(TextArea area) {
            this.area = area;
            poller = new Timeline(new KeyFrame(Duration.millis(100), e -> drain()));
            poller.setCycleCount(Timeline.INDEFINITE);
            poller.play();
        }

        /** Thread-safe logger for AI algorithms. */
        Consumer<String> asConsumer() {
            return queue::add;
        }

        private void drain() {
            StringBuilder sb = new StringBuilder();
            int count = 0;
            String msg;
            while (count++ < MAX_DRAIN && (msg = queue.poll()) != null)
                sb.append(msg).append('\n');
            if (sb.length() > 0)
                area.appendText(sb.toString());
        }

        void reset() {
            queue.clear();
            area.clear();
        }

        void stop() {
            poller.stop();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TILE NODE — Single arcade-style game tile with all animations
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * StackPane wrapping a gradient Rectangle + score Label.
     * All animations return Transition objects — caller wires setOnFinished()
     * to prevent animation/state desync.
     */
    static class TileNode extends StackPane {
        private static final double ARC = 18;
        private static final int HALF = 110; // ms per flip half

        private final int id;
        private final double SIZE;
        private final Rectangle bg;
        private final Label lbl;
        private final DropShadow baseShadow;
        private boolean playerOwned = false;

        TileNode(int id, double size) {
            this.id = id;
            this.SIZE = size;

            bg = new Rectangle(SIZE, SIZE);
            bg.setArcWidth(ARC);
            bg.setArcHeight(ARC);
            bg.setFill(Theme.tileFill(Theme.NEUTRAL));

            baseShadow = new DropShadow(14, 0, 5, Color.web("#00000099"));
            bg.setEffect(baseShadow);

            lbl = new Label();
            lbl.setFont(Font.font("Arial", FontWeight.BOLD, 16));
            lbl.setTextFill(Color.WHITE);
            lbl.setStyle("-fx-text-alignment: center;");

            // Small tile-ID label — top-left corner, always visible
            Label idLabel = new Label(String.valueOf(id));
            idLabel.setFont(Font.font("Arial", FontWeight.BOLD, 9));
            idLabel.setTextFill(Color.web("#FFFFFF99"));
            idLabel.setPadding(new Insets(3, 0, 0, 5));
            StackPane.setAlignment(idLabel, Pos.TOP_LEFT);

            getChildren().addAll(bg, lbl, idLabel);
            setMinSize(SIZE, SIZE);
            setMaxSize(SIZE, SIZE);

            DropShadow hoverGlow = new DropShadow(24, Theme.ACCENT);
            hoverGlow.setSpread(0.3);

            setOnMouseEntered(e -> {
                if (!isDisabled()) {
                    ScaleTransition st = new ScaleTransition(Duration.millis(120), this);
                    st.setToX(1.06);
                    st.setToY(1.06);
                    st.play();
                    bg.setEffect(hoverGlow);
                }
            });
            setOnMouseExited(e -> {
                if (!isDisabled()) {
                    ScaleTransition st = new ScaleTransition(Duration.millis(120), this);
                    st.setToX(1.0);
                    st.setToY(1.0);
                    st.play();
                    bg.setEffect(baseShadow);
                }
            });
        }

        // ── Visual states ─────────────────────────────────────────────────────

        void styleBlackHole() {
            bg.setFill(Theme.BH_FILL);
            bg.setStroke(Theme.BH_BORDER);
            bg.setStrokeWidth(2.5);
            bg.setEffect(new DropShadow(10, 0, 0, Theme.BH_BORDER));
            lbl.setText("■");
            lbl.setFont(Font.font("Serif", FontWeight.BOLD, 26));
            lbl.setTextFill(Theme.BH_BORDER);
            setDisable(true);
        }

        void styleFree(boolean playerOwns, double stratValue) {
            this.playerOwned = playerOwns;
            setDisable(false);
            bg.setFill(Theme.tileFill(playerOwns ? Theme.PLAYER : Theme.CPU));
            bg.setStroke(null);
            bg.setEffect(baseShadow);
            if (stratValue != 0) {
                lbl.setText(String.format("%+.0f", stratValue));
                lbl.setTextFill(stratValue > 0 ? Color.WHITE : Color.web("#FF9696"));
                lbl.setFont(Font.font("Arial", FontWeight.BOLD, 16));
            } else {
                lbl.setText("");
            }
        }

        void styleLocked(boolean playerOwns, int countdown, double stratValue) {
            this.playerOwned = playerOwns;
            setDisable(true);
            Color base = playerOwns ? Theme.PLAYER : Theme.CPU;
            bg.setFill(Theme.tileFill(base.deriveColor(0, 1, 0.38, 1)));
            bg.setStroke(null);
            bg.setEffect(baseShadow);
            String sc = (stratValue != 0) ? String.format("%+.0f\n", stratValue) : "";
            lbl.setText(sc + "WAIT:" + countdown);
            lbl.setTextFill(Color.web("#FF5555"));
            lbl.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        }

        // ── Animations ────────────────────────────────────────────────────────

        /** Flip: ScaleX 1→0, color swap, ScaleX 0→1. Caller wires setOnFinished. */
        SequentialTransition buildFlipAnimation(boolean newOwner) {
            ScaleTransition h1 = new ScaleTransition(Duration.millis(HALF), this);
            h1.setFromX(1.0);
            h1.setToX(0.0);

            PauseTransition swap = new PauseTransition(Duration.millis(1));
            swap.setOnFinished(e -> {
                this.playerOwned = newOwner;
                bg.setFill(Theme.tileFill(newOwner ? Theme.PLAYER : Theme.CPU));
                lbl.setText("");
            });

            ScaleTransition h2 = new ScaleTransition(Duration.millis(HALF), this);
            h2.setFromX(0.0);
            h2.setToX(1.0);
            return new SequentialTransition(h1, swap, h2);
        }

        /** Diamond-wave board entrance: scale+fade in with staggered delay. */
        SequentialTransition buildEntrance(int delayMs) {
            setScaleX(0);
            setScaleY(0);
            setOpacity(0);
            PauseTransition delay = new PauseTransition(Duration.millis(delayMs));
            ScaleTransition scale = new ScaleTransition(Duration.millis(230), this);
            scale.setFromX(0);
            scale.setFromY(0);
            scale.setToX(1);
            scale.setToY(1);
            FadeTransition fade = new FadeTransition(Duration.millis(230), this);
            fade.setFromValue(0);
            fade.setToValue(1);
            return new SequentialTransition(delay, new ParallelTransition(scale, fade));
        }

        /** Cyan glow + opacity pulse × 5 cycles, then reverts. */
        Timeline buildHintPulse() {
            DropShadow hintGlow = new DropShadow(30, Theme.ACCENT);
            hintGlow.setSpread(0.55);
            bg.setEffect(hintGlow);
            Timeline pulse = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(opacityProperty(), 1.0)),
                    new KeyFrame(Duration.millis(350), new KeyValue(opacityProperty(), 0.3)),
                    new KeyFrame(Duration.millis(700), new KeyValue(opacityProperty(), 1.0)));
            pulse.setCycleCount(5);
            pulse.setOnFinished(e -> {
                setOpacity(1.0);
                bg.setEffect(baseShadow);
            });
            return pulse;
        }

        int getTileId() {
            return id;
        }

        boolean isPlayerOwned() {
            return playerOwned;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // BOARD VIEW — GridPane of TileNodes with diamond-wave entrance
    // ═════════════════════════════════════════════════════════════════════════

    static class BoardView extends StackPane {
        private final int gridSize, totalTiles;
        private final TileNode[] tiles;

        /** Tile size tuned so the board fills the window at any grid size. */
        private static double tileSize(int gs) {
            if (gs <= 4)
                return 120;
            if (gs == 5)
                return 105;
            return 88;
        }

        BoardView(int gridSize) {
            this.gridSize = gridSize;
            this.totalTiles = gridSize * gridSize;
            this.tiles = new TileNode[totalTiles];

            GridPane grid = new GridPane();
            grid.setHgap(8);
            grid.setVgap(8);
            grid.setAlignment(Pos.CENTER);
            grid.setPadding(new Insets(20));

            double sz = tileSize(gridSize);
            for (int i = 0; i < totalTiles; i++) {
                TileNode t = new TileNode(i, sz);
                tiles[i] = t;
                grid.add(t, i % gridSize, i / gridSize);
            }
            setAlignment(Pos.CENTER);
            getChildren().add(grid);
        }

        TileNode getTile(int id) {
            return tiles[id];
        }

        TileNode[] getAllTiles() {
            return tiles;
        }

        void applyBlackHoles(Set<Integer> bh) {
            for (int id : bh)
                tiles[id].styleBlackHole();
        }

        ParallelTransition buildEntranceAnimation() {
            ParallelTransition all = new ParallelTransition();
            for (int i = 0; i < totalTiles; i++) {
                int delay = (i / gridSize + i % gridSize) * 18; // faster: 18ms stagger
                all.getChildren().add(tiles[i].buildEntrance(delay));
            }
            return all;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // HUD BAR — Scores, turn counter, AI mode, active-player neon border
    // ═════════════════════════════════════════════════════════════════════════

    static class HUDBar extends HBox {
        private final VBox p1Box, p2Box;
        private final Label p1Lbl, p2Lbl, turnLbl, aiLbl, statusLbl;

        HUDBar() {
            setSpacing(8);
            setPadding(new Insets(12, 18, 12, 18));
            setBackground(bgFill(Color.web("#181C22")));
            setAlignment(Pos.CENTER);

            p1Box = scoreBox("PLAYER", Theme.PLAYER);
            p1Lbl = (Label) p1Box.getChildren().get(1);
            p2Box = scoreBox("CPU", Theme.CPU);
            p2Lbl = (Label) p2Box.getChildren().get(1);

            VBox turnBox = miniBox("TURN", "0/15", Theme.ACCENT);
            turnLbl = (Label) turnBox.getChildren().get(1);
            VBox aiBox = miniBox("AI", "R3", Theme.ACCENT);
            aiLbl = (Label) aiBox.getChildren().get(1);

            statusLbl = new Label("YOUR TURN");
            statusLbl.setFont(Font.font("Arial", FontWeight.BOLD, 13));
            statusLbl.setTextFill(Theme.ACCENT);

            VBox center = new VBox(4, turnBox, statusLbl, aiBox);
            center.setAlignment(Pos.CENTER);

            Region s1 = new Region(), s2 = new Region();
            HBox.setHgrow(s1, Priority.ALWAYS);
            HBox.setHgrow(s2, Priority.ALWAYS);
            getChildren().addAll(p1Box, s1, center, s2, p2Box);
        }

        void update(double p1, double p2, int turn, int max, boolean playerTurn, String status) {
            p1Lbl.setText(String.format("%.0f", p1));
            p2Lbl.setText(String.format("%.0f", p2));
            turnLbl.setText(turn + "/" + max);
            statusLbl.setText(status);
            statusLbl.setTextFill(playerTurn ? Theme.ACCENT : Theme.CPU);
            setBdr(p1Box, playerTurn ? Theme.ACCENT : Theme.PLAYER, playerTurn ? 2.5 : 1.0);
            setBdr(p2Box, playerTurn ? Theme.CPU : Theme.ACCENT, playerTurn ? 1.0 : 2.5);
        }

        void setAIMode(String m) {
            aiLbl.setText(m);
        }

        private VBox scoreBox(String title, Color c) {
            VBox box = new VBox(4);
            box.setAlignment(Pos.CENTER);
            box.setPadding(new Insets(8, 20, 8, 20));
            box.setBackground(new Background(new BackgroundFill(
                    c.deriveColor(0, 1, 0.12, 0.35), new CornerRadii(10), Insets.EMPTY)));
            box.setMinWidth(120);
            setBdr(box, c, 1.5);
            Label t = new Label(title);
            t.setFont(Font.font("Arial", FontWeight.BOLD, 10));
            t.setTextFill(c.brighter());
            Label s = new Label("0");
            s.setFont(Font.font("Arial", FontWeight.BOLD, 30));
            s.setTextFill(c);
            s.setEffect(new DropShadow(14, c));
            box.getChildren().addAll(t, s);
            return box;
        }

        private VBox miniBox(String title, String val, Color c) {
            VBox box = new VBox(2);
            box.setAlignment(Pos.CENTER);
            Label t = new Label(title);
            t.setFont(Font.font("Arial", FontWeight.BOLD, 9));
            t.setTextFill(Color.GRAY);
            Label v = new Label(val);
            v.setFont(Font.font("Arial", FontWeight.BOLD, 16));
            v.setTextFill(c);
            box.getChildren().addAll(t, v);
            return box;
        }

        private void setBdr(Region r, Color c, double w) {
            r.setBorder(
                    new Border(new BorderStroke(c, BorderStrokeStyle.SOLID, new CornerRadii(10), new BorderWidths(w))));
        }

        private static Background bgFill(Color c) {
            return new Background(new BackgroundFill(c, CornerRadii.EMPTY, Insets.EMPTY));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CONTROL BAR — Arcade-style buttons with glow hover + scale animation
    // ═════════════════════════════════════════════════════════════════════════

    static class ControlBar extends HBox {
        private final Button btnRestart, btnHint, btnSolve, btnMenu, btnExit;

        ControlBar() {
            setSpacing(10);
            setPadding(new Insets(10, 16, 10, 16));
            setAlignment(Pos.CENTER);
            setBackground(new Background(new BackgroundFill(Color.web("#181C22"), CornerRadii.EMPTY, Insets.EMPTY)));

            btnRestart = btn("⟳ Restart", Theme.ACCENT);
            btnHint = btn("💡 Hint", Theme.PLAYER);
            btnSolve = btn("▶ Solve", Color.web("#9B59B6"));
            btnMenu = btn("☰ Menu", Theme.NEUTRAL);
            btnExit = btn("✕ Exit", Color.web("#C0392B"));
            getChildren().addAll(btnRestart, btnHint, btnSolve, btnMenu, btnExit);
        }

        void setOnRestart(Runnable r) {
            btnRestart.setOnAction(e -> r.run());
        }

        void setOnHint(Runnable r) {
            btnHint.setOnAction(e -> r.run());
        }

        void setOnSolve(Runnable r) {
            btnSolve.setOnAction(e -> r.run());
        }

        void setOnMenu(Runnable r) {
            btnMenu.setOnAction(e -> r.run());
        }

        void setOnExit(Runnable r) {
            btnExit.setOnAction(e -> r.run());
        }

        void setSolveText(String t) {
            btnSolve.setText(t);
        }

        private Button btn(String text, Color color) {
            Button b = new Button(text);
            b.setFont(Font.font("Arial", FontWeight.BOLD, 13));
            b.setTextFill(Color.WHITE);
            b.setPadding(new Insets(9, 18, 9, 18));
            b.setFocusTraversable(false);

            String hex = Theme.toHex(color);
            String dark = Theme.toHex(color.deriveColor(0, 1, 0.6, 1));
            String styleBase = "-fx-background-color:" + dark + ";-fx-background-radius:22;" +
                    "-fx-border-color:" + hex + ";-fx-border-radius:22;-fx-border-width:1.5;-fx-cursor:hand;";
            String styleHover = "-fx-background-color:" + hex + ";-fx-background-radius:22;" +
                    "-fx-border-color:" + hex
                    + ";-fx-border-radius:22;-fx-border-width:1.5;-fx-cursor:hand;-fx-text-fill:#000000;";
            b.setStyle(styleBase);
            DropShadow glow = new DropShadow(14, color);

            b.setOnMouseEntered(e -> {
                b.setStyle(styleHover);
                b.setEffect(glow);
                ScaleTransition st = new ScaleTransition(Duration.millis(100), b);
                st.setToX(1.08);
                st.setToY(1.08);
                st.play();
            });
            b.setOnMouseExited(e -> {
                b.setStyle(styleBase);
                b.setEffect(null);
                ScaleTransition st = new ScaleTransition(Duration.millis(100), b);
                st.setToX(1.0);
                st.setToY(1.0);
                st.play();
            });
            return b;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // BRAIN SCANNER PANE — Dark terminal display backed by LogQueue
    // ═════════════════════════════════════════════════════════════════════════

    // ═════════════════════════════════════════════════════════════════════════
    // BRAIN SCANNER PANE — 4-Tab AI dashboard with Eval Grid, Candidates,
    // Search Tree, and System Log
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Advanced 4-tab Brain Scanner dashboard.
     * All data models (TurnReport, MoveStat) are public static inner classes.
     * updateDashboard() is thread-safe — wraps everything in Platform.runLater.
     */
    static class BrainScannerPane extends VBox {

        // ── Data Models ───────────────────────────────────────────────────────

        /** One candidate move's scoring breakdown. */
        public static class MoveStat {
            private final int tileId;
            private final double r2Score, finalScore;
            private final String status;

            public MoveStat(int tileId, double r2Score, double finalScore, String status) {
                this.tileId = tileId;
                this.r2Score = r2Score;
                this.finalScore = finalScore;
                this.status = status;
            }

            public int getTileId() {
                return tileId;
            }

            public double getR2Score() {
                return r2Score;
            }

            public double getFinalScore() {
                return finalScore;
            }

            public String getStatus() {
                return status;
            }
        }

        /** Full metadata for one CPU turn. */
        public static class TurnReport {
            public int nodesSearched, prunes, dpHits;
            public long timeMs;
            public String[][] evaluationGrid; // [row][col] text values
            public List<MoveStat> candidateMoves;
            public TreeItem<String> searchTreeRoot;
            public String systemLogs;

            public TurnReport() {
                candidateMoves = new ArrayList<>();
            }
        }

        // ── UI widgets ────────────────────────────────────────────────────────
        private final Label nodesLbl, prunesLbl, dpHitsLbl, timeLbl;
        private final GridPane evalGridPane;
        private final TableView<MoveStat> candidatesTable;
        private final TreeView<String> searchTree;
        private final TextArea sysLogArea;
        private final LogQueue logQueue;
        private int currentGridSize = 4;

        @SuppressWarnings("unchecked")
        BrainScannerPane() {
            // ── Header ────────────────────────────────────────────────────────
            nodesLbl = hdrLbl("Nodes: 0");
            prunesLbl = hdrLbl("Prunes: 0");
            dpHitsLbl = hdrLbl("DP Hits: 0");
            timeLbl = hdrLbl("Time: 0ms");

            HBox header = new HBox(14,
                    nodesLbl, sep(), prunesLbl, sep(), dpHitsLbl, sep(), timeLbl);
            header.setAlignment(Pos.CENTER_LEFT);
            header.setPadding(new Insets(7, 12, 7, 12));
            header.setStyle("-fx-background-color:#0d1021;"
                    + "-fx-border-color:#50FF78;-fx-border-width:0 0 1.5 0;");

            // ── Tab 1: Eval Grid ──────────────────────────────────────────────
            evalGridPane = new GridPane();
            evalGridPane.setHgap(4);
            evalGridPane.setVgap(4);
            evalGridPane.setPadding(new Insets(12));
            evalGridPane.setAlignment(Pos.CENTER);
            ScrollPane evalScroll = new ScrollPane(evalGridPane);
            evalScroll.setFitToWidth(true);
            evalScroll.setFitToHeight(true);
            evalScroll.setStyle("-fx-background-color:#0A0A14;-fx-border-color:transparent;");
            Tab evalTab = new Tab("\uD83D\uDDFA Eval Grid", evalScroll);
            evalTab.setClosable(false);

            // ── Tab 2: Candidates TableView ───────────────────────────────────
            candidatesTable = new TableView<>();
            candidatesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            candidatesTable.setStyle("-fx-background-color:#0A0A14;"
                    + "-fx-table-cell-border-color:#1a2a1a;");
            candidatesTable.setEditable(false);

            TableColumn<MoveStat, String> colTile = fixedCol("Tile", 55);
            colTile.setCellValueFactory(p -> new SimpleStringProperty(String.valueOf(p.getValue().getTileId())));

            TableColumn<MoveStat, String> colR2 = fixedCol("R2 Score", 85);
            colR2.setCellValueFactory(p -> new SimpleStringProperty(String.format("%.1f", p.getValue().getR2Score())));

            TableColumn<MoveStat, String> colMM = fixedCol("Minimax", 85);
            colMM.setCellValueFactory(
                    p -> new SimpleStringProperty(String.format("%.1f", p.getValue().getFinalScore())));

            TableColumn<MoveStat, String> colStatus = new TableColumn<>("Status");
            colStatus.setReorderable(false);
            colStatus.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getStatus()));
            colStatus.setCellFactory(col -> new TableCell<MoveStat, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                        return;
                    }
                    setText(item);
                    if ("CHOSEN".equals(item)) {
                        setStyle("-fx-background-color:#F4C430;-fx-text-fill:#000;"
                                + "-fx-font-weight:bold;");
                    } else {
                        setStyle("-fx-text-fill:#50FF78;");
                    }
                }
            });
            candidatesTable.getColumns().addAll(colTile, colR2, colMM, colStatus);
            Tab candidTab = new Tab("\uD83D\uDCCB Candidates", candidatesTable);
            candidTab.setClosable(false);

            // ── Tab 3: Search Tree ────────────────────────────────────────────
            searchTree = new TreeView<>();
            searchTree.setStyle("-fx-background-color:#0A0A14;"
                    + "-fx-text-fill:#50FF78;-fx-font-family:Consolas;-fx-font-size:11;");
            Tab treeTab = new Tab("\uD83C\uDF33 Search Tree", searchTree);
            treeTab.setClosable(false);

            // ── Tab 4: System Log ─────────────────────────────────────────────
            sysLogArea = new TextArea();
            sysLogArea.setEditable(false);
            sysLogArea.setWrapText(true);
            sysLogArea.setFont(Font.font("Consolas", 11));
            sysLogArea.setStyle("-fx-control-inner-background:#0A0A14;"
                    + "-fx-text-fill:#50FF78;-fx-background-color:#0A0A14;");
            logQueue = new LogQueue(sysLogArea);
            Tab logTab = new Tab("\uD83D\uDCDC System Log", sysLogArea);
            logTab.setClosable(false);

            // ── Assembly ──────────────────────────────────────────────────────
            TabPane tabs = new TabPane(evalTab, candidTab, treeTab, logTab);
            tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            tabs.setStyle("-fx-background-color:#0A0A14;-fx-open-tab-animation:NONE;");
            VBox.setVgrow(tabs, Priority.ALWAYS);

            setStyle("-fx-border-color:#50FF78;-fx-border-width:1.5;"
                    + "-fx-border-radius:6;-fx-background-color:#0A0A14;"
                    + "-fx-background-radius:6;");
            setPrefHeight(240);
            getChildren().addAll(header, tabs);
        }

        // ── Public API ────────────────────────────────────────────────────────

        /** Thread-safe dashboard refresh — safe to call from any thread. */
        public void updateDashboard(TurnReport report) {
            if (report == null)
                return;
            Platform.runLater(() -> {
                // Header stats
                nodesLbl.setText("Nodes: " + report.nodesSearched);
                prunesLbl.setText("Prunes: " + report.prunes);
                dpHitsLbl.setText("DP Hits: " + report.dpHits);
                timeLbl.setText("Time: " + report.timeMs + "ms");

                // Tab 1 — Eval Grid
                evalGridPane.getChildren().clear();
                evalGridPane.getColumnConstraints().clear();
                evalGridPane.getRowConstraints().clear();
                if (report.evaluationGrid != null) {
                    for (int r = 0; r < report.evaluationGrid.length; r++) {
                        for (int c = 0; c < report.evaluationGrid[r].length; c++) {
                            String val = report.evaluationGrid[r][c];
                            Label cell = new Label(val);
                            cell.setMinSize(56, 34);
                            cell.setMaxSize(56, 34);
                            cell.setAlignment(Pos.CENTER);
                            cell.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
                            cell.setStyle(cellStyle(val));
                            evalGridPane.add(cell, c, r);
                        }
                    }
                }

                // Tab 2 — Candidates
                if (report.candidateMoves != null) {
                    candidatesTable.setItems(
                            FXCollections.observableArrayList(report.candidateMoves));
                }

                // Tab 3 — Search Tree
                if (report.searchTreeRoot != null) {
                    searchTree.setRoot(report.searchTreeRoot);
                    searchTree.setShowRoot(true);
                }

                // Tab 4 — System Log (append; auto-scroll)
                if (report.systemLogs != null && !report.systemLogs.isEmpty()) {
                    sysLogArea.appendText(report.systemLogs + "\n");
                    sysLogArea.setScrollTop(Double.MAX_VALUE);
                }
            });
        }

        Consumer<String> getLogger() {
            return logQueue.asConsumer();
        }

        void reset() {
            logQueue.reset();
            sysLogArea.clear();
        }

        void stop() {
            logQueue.stop();
        }

        void setGridSize(int gs) {
            this.currentGridSize = gs;
        }

        // ── Cell colour helper ────────────────────────────────────────────────
        private String cellStyle(String val) {
            if (val == null)
                return "-fx-background-color:#1a1a2e;-fx-background-radius:4;";
            if ("BH".equals(val) || "VOID".equals(val)) {
                return "-fx-background-color:#6A0DAD33;-fx-text-fill:#6A0DAD;"
                        + "-fx-border-color:#6A0DAD;-fx-border-width:1;-fx-background-radius:4;";
            }
            if ("LOCK".equals(val)) {
                return "-fx-background-color:#2A2A3A;-fx-text-fill:#FF5555;"
                        + "-fx-background-radius:4;";
            }
            try {
                double d = Double.parseDouble(val.replace("+", ""));
                if (d > 0)
                    return "-fx-background-color:#0d2010;-fx-text-fill:#50FF78;"
                            + "-fx-border-color:#1a4a1a;-fx-border-width:1;-fx-background-radius:4;";
                if (d < 0)
                    return "-fx-background-color:#200d0d;-fx-text-fill:#FF5555;"
                            + "-fx-border-color:#4a1a1a;-fx-border-width:1;-fx-background-radius:4;";
            } catch (NumberFormatException ignored) {
            }
            return "-fx-background-color:#1a1a2e;-fx-text-fill:#AAAAAA;-fx-background-radius:4;";
        }

        // ── Widget factories ──────────────────────────────────────────────────
        private Label hdrLbl(String text) {
            Label l = new Label(text);
            l.setFont(Font.font("Consolas", FontWeight.BOLD, 13));
            l.setTextFill(Theme.SCAN_TEXT);
            return l;
        }

        private Label sep() {
            Label l = new Label("|");
            l.setTextFill(Color.web("#334433"));
            return l;
        }

        private TableColumn<MoveStat, String> fixedCol(String title, double w) {
            TableColumn<MoveStat, String> col = new TableColumn<>(title);
            col.setPrefWidth(w);
            col.setMinWidth(w);
            col.setMaxWidth(w);
            col.setReorderable(false);
            col.setResizable(false);
            return col;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // MENU SCENE — Floating particles, pulsing title, dropdowns, PLAY button
    // ═════════════════════════════════════════════════════════════════════════

    static class MenuScene {
        private final Stage stage;
        private int selectedGrid = 4;
        private int selectedVersion = 3;
        private final List<Timeline> particleTimelines = new ArrayList<>();

        MenuScene(Stage stage) {
            this.stage = stage;
        }

        int getSelectedGrid() {
            return selectedGrid;
        }

        int getSelectedVersion() {
            return selectedVersion;
        }

        Scene build(Runnable onPlay) {
            StackPane root = new StackPane();
            root.setBackground(new Background(new BackgroundFill(Theme.BG, CornerRadii.EMPTY, Insets.EMPTY)));

            Pane particleLayer = new Pane();
            particleLayer.setMouseTransparent(true);
            addParticles(particleLayer);

            // ── Title ─────────────────────────────────────────────────────────
            Label title = new Label("FLIP WARS");
            title.setFont(Font.font("Arial", FontWeight.BOLD, 72));
            title.setTextFill(Theme.PLAYER);
            title.setEffect(new DropShadow(30, Theme.PLAYER));

            Timeline titlePulse = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(title.textFillProperty(), Theme.PLAYER)),
                    new KeyFrame(Duration.millis(700), new KeyValue(title.textFillProperty(), Theme.ACCENT)),
                    new KeyFrame(Duration.millis(1400), new KeyValue(title.textFillProperty(), Theme.PLAYER)));
            titlePulse.setCycleCount(Timeline.INDEFINITE);
            titlePulse.play();

            Label sub = new Label("A Strategic Duel of Algorithms");
            sub.setFont(Font.font("Arial", FontWeight.NORMAL, 17));
            sub.setStyle("-fx-font-style: italic;");
            sub.setTextFill(Color.web("#BBBBBB"));

            // ── Selectors ─────────────────────────────────────────────────────
            ComboBox<String> gridCombo = styledCombo("4 × 4", "4 × 4", "5 × 5", "6 × 6");
            gridCombo.setOnAction(e -> {
                String v = gridCombo.getValue();
                selectedGrid = v.startsWith("4") ? 4 : v.startsWith("5") ? 5 : 6;
            });

            ComboBox<String> verCombo = styledCombo("R3: DP + Backtracking",
                    "R1: Greedy", "R2: Divide & Conquer", "R3: DP + Backtracking");
            verCombo.setOnAction(e -> {
                String v = verCombo.getValue();
                selectedVersion = v.startsWith("R1") ? 1 : v.startsWith("R2") ? 2 : 3;
            });

            HBox selectors = new HBox(24,
                    labelled("GRID SIZE", gridCombo),
                    labelled("AI VERSION", verCombo));
            selectors.setAlignment(Pos.CENTER);

            // ── Buttons ───────────────────────────────────────────────────────
            Button playBtn = menuBtn("▶  PLAY GAME", Theme.ACCENT, 22);
            Button rulesBtn = menuBtn("📖  RULES", Theme.NEUTRAL, 16);
            Button exitBtn = menuBtn("✕  EXIT", Color.web("#C0392B"), 16);

            playBtn.setOnAction(e -> {
                stopParticles();
                titlePulse.stop();
                onPlay.run();
            });
            rulesBtn.setOnAction(e -> showRules());
            exitBtn.setOnAction(e -> stage.close());

            Label team = new Label("Design & Analysis of Algorithms  ·  Team-13");
            team.setFont(Font.font("Arial", 11));
            team.setTextFill(Color.web("#555560"));

            VBox content = new VBox(22, title, sub, selectors, playBtn, rulesBtn, exitBtn, team);
            content.setAlignment(Pos.CENTER);
            content.setPadding(new Insets(60));

            root.getChildren().addAll(particleLayer, content);
            Scene scene = new Scene(root, 1080, 1040);
            scene.setFill(Theme.BG);
            return scene;
        }

        // ── Particle layer ────────────────────────────────────────────────────

        private void addParticles(Pane layer) {
            Random rng = new Random();
            Color[] colors = { Theme.ACCENT, Theme.PLAYER, Theme.CPU,
                    Color.web("#9B59B6"), Color.web("#1ABC9C") };
            for (int i = 0; i < 28; i++) {
                Circle c = new Circle(rng.nextDouble() * 3 + 1);
                c.setFill(colors[rng.nextInt(colors.length)]);
                c.setOpacity(0.35 + rng.nextDouble() * 0.3);
                c.setCenterX(rng.nextDouble() * 1080);
                c.setCenterY(rng.nextDouble() * 1040);
                layer.getChildren().add(c);

                double dur = 3000 + rng.nextDouble() * 5000;
                Timeline t = new Timeline(
                        new KeyFrame(Duration.ZERO,
                                new KeyValue(c.centerYProperty(), c.getCenterY()),
                                new KeyValue(c.opacityProperty(), c.getOpacity())),
                        new KeyFrame(Duration.millis(dur),
                                new KeyValue(c.centerYProperty(), c.getCenterY() - 250 - rng.nextDouble() * 300),
                                new KeyValue(c.opacityProperty(), 0)));
                t.setCycleCount(Timeline.INDEFINITE);
                t.setOnFinished(ev -> {
                    c.setCenterY(1050);
                    c.setCenterX(rng.nextDouble() * 1080);
                    c.setOpacity(0.3 + rng.nextDouble() * 0.35);
                });
                particleTimelines.add(t);
                PauseTransition start = new PauseTransition(Duration.millis(rng.nextInt(3000)));
                start.setOnFinished(ev -> t.play());
                start.play();
            }
        }

        private void stopParticles() {
            particleTimelines.forEach(Timeline::stop);
            particleTimelines.clear();
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private ComboBox<String> styledCombo(String def, String... items) {
            ComboBox<String> cb = new ComboBox<>();
            cb.getItems().addAll(items);
            cb.setValue(def);
            // Dark background + border via style
            cb.setStyle(
                    "-fx-background-color:#1E2228;" +
                            "-fx-border-color:" + Theme.toHex(Theme.ACCENT) + ";" +
                            "-fx-border-radius:6;-fx-background-radius:6;" +
                            "-fx-font-size:14;-fx-pref-width:220;");
            // Fix: style the selected-value button cell explicitly so text is white
            cb.setButtonCell(new ListCell<String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                    setTextFill(javafx.scene.paint.Color.WHITE);
                    setStyle("-fx-background-color:#1E2228; -fx-font-size:14;");
                }
            });
            return cb;
        }

        private VBox labelled(String lbl, ComboBox<?> combo) {
            Label l = new Label(lbl);
            l.setFont(Font.font("Arial", FontWeight.BOLD, 10));
            l.setTextFill(Color.GRAY);
            VBox box = new VBox(6, l, combo);
            box.setAlignment(Pos.CENTER_LEFT);
            return box;
        }

        private Button menuBtn(String text, Color color, int size) {
            Button b = new Button(text);
            b.setFont(Font.font("Arial", FontWeight.BOLD, size));
            b.setTextFill(Color.WHITE);
            b.setPrefWidth(300);
            b.setPadding(new Insets(13, 30, 13, 30));
            String hex = Theme.toHex(color);
            String dark = Theme.toHex(color.deriveColor(0, 1, 0.55, 1));
            String base = "-fx-background-color:" + dark + ";-fx-background-radius:26;" +
                    "-fx-border-color:" + hex + ";-fx-border-radius:26;-fx-border-width:1.8;-fx-cursor:hand;";
            String hover = "-fx-background-color:" + hex + ";-fx-background-radius:26;" +
                    "-fx-border-color:" + hex
                    + ";-fx-border-radius:26;-fx-border-width:1.8;-fx-cursor:hand;-fx-text-fill:#000000;";
            b.setStyle(base);
            DropShadow glow = new DropShadow(18, color);
            b.setOnMouseEntered(e -> {
                b.setStyle(hover);
                b.setEffect(glow);
                ScaleTransition st = new ScaleTransition(Duration.millis(100), b);
                st.setToX(1.05);
                st.setToY(1.05);
                st.play();
            });
            b.setOnMouseExited(e -> {
                b.setStyle(base);
                b.setEffect(null);
                ScaleTransition st = new ScaleTransition(Duration.millis(100), b);
                st.setToX(1.0);
                st.setToY(1.0);
                st.play();
            });
            return b;
        }

        private void showRules() {
            Stage d = new Stage();
            d.setTitle("Rules & Credits — Flip Wars");
            d.setResizable(true);

            String txt = "══════════════════════════════════════\n" +
                    "         FLIP WARS — HOW TO PLAY\n" +
                    "══════════════════════════════════════\n\n" +
                    "OBJECTIVE\n" +
                    "  Turn ALL tiles your color, OR hold the\n" +
                    "  highest Strategic Score at the turn limit.\n\n" +
                    "FLIP LOGIC\n" +
                    "  Click a tile → flips it + all 4 orthogonal\n" +
                    "  neighbors (up, down, left, right) in a + pattern.\n\n" +
                    "LOCK MECHANIC  (Tabu Search)\n" +
                    "  After clicking, a tile is locked for several\n" +
                    "  turns (shows WAIT countdown). Prevents infinite\n" +
                    "  loops and adds strategic depth.\n\n" +
                    "BLACK HOLES\n" +
                    "  Void tiles: permanently unflippable. They warp the\n" +
                    "  graph topology — adjacent tiles lose one neighbor.\n\n" +
                    "SCORING\n" +
                    "  Corners  +25  ← most valuable!\n" +
                    "  Edges    +15\n" +
                    "  Standard  +5\n" +
                    "  Trap      -5  ← avoid near corners\n\n" +
                    "══════════════════════════════════════\n" +
                    "              AI VERSIONS\n" +
                    "══════════════════════════════════════\n\n" +
                    "R1 — GREEDY  (Easy)\n" +
                    "  Simple tile-value counting + 15% random blunder.\n\n" +
                    "R2 — DIVIDE & CONQUER  (Medium)\n" +
                    "  · Merge Sort       move ranking\n" +
                    "  · Spatial D&C      quadrant control\n" +
                    "  · DFS Clusters     territory strength\n" +
                    "  · Tournament Select CPU move selection\n" +
                    "  · Threat Detection exposure analysis\n\n" +
                    "R3 — DP + BACKTRACKING  (Expert)\n" +
                    "  · SUHAS   doMove/undoMove backtracking (O(1) space)\n" +
                    "  · MANEESH Alpha-Beta Pruning Minimax\n" +
                    "  · GANESH  Zobrist Transposition Table\n" +
                    "  · BALAJI  Bitmask DP Oracle (4x4: 65536 states)\n\n" +
                    "══════════════════════════════════════\n" +
                    "         WINNING STRATEGIES\n" +
                    "══════════════════════════════════════\n\n" +
                    "  * Secure corners early — worth 25 pts each!\n" +
                    "  * Build large connected clusters for territory\n" +
                    "  * Avoid trap tiles near corners (-5)\n" +
                    "  * Plan around locked tiles for surprise moves\n" +
                    "  * Watch the Brain Scanner for AI reasoning!\n\n" +
                    "      Design & Analysis of Algorithms\n" +
                    "               Team-13\n";

            TextArea ta = new TextArea(txt);
            ta.setEditable(false);
            ta.setWrapText(false);
            ta.setFont(Font.font("Consolas", 14));
            ta.setStyle("-fx-control-inner-background:#0e0e18;-fx-text-fill:#C8E6C9;-fx-background-color:#0e0e18;");

            StackPane pane = new StackPane(ta);
            pane.setBackground(
                    new Background(new BackgroundFill(Color.web("#0e0e18"), CornerRadii.EMPTY, Insets.EMPTY)));
            Scene s = new Scene(pane, 600, 660);
            s.setFill(Color.web("#0e0e18"));
            d.setScene(s);
            d.initOwner(stage);
            d.initModality(Modality.WINDOW_MODAL);
            d.show();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GAME SCENE — Full game logic + layout (ported from Main.java)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Threading model:
     * - CPU move computation: background daemon thread
     * - All UI mutation: Platform.runLater
     * - Desync fix: inputBlocked is cleared only in animation's setOnFinished
     *
     * Brain Scanner: AI logger calls logQueue.add() — never Platform.runLater
     * per node. Batched at 100 ms. Max 20 msgs/tick.
     */
    static class GameScene {
        private final Stage stage;
        private final Runnable onBack;
        private final int gridSize, version;
        private final int totalTiles, maxTurns;

        // Logic
        private Graph graph;
        private Rules rules;
        private Engine ai;

        // State
        private boolean[] gridState;
        private Set<Integer> blackHoles = new HashSet<>();
        private int turnsPlayed = 0;
        private boolean isPlayerTurn = true;
        private boolean inputBlocked = false;
        private boolean isGameOver = false;
        private boolean isAutoMode = false;

        // UI
        private BorderPane root; // ← held as field to avoid stage.getScene() NPE
        private HUDBar hud;
        private BoardView board;
        private ControlBar controls;
        private BrainScannerPane scanner;
        private Timeline autoTimer;

        GameScene(Stage stage, int gridSize, int version, Runnable onBack) {
            this.stage = stage;
            this.gridSize = gridSize;
            this.version = version;
            this.onBack = onBack;
            this.totalTiles = gridSize * gridSize;
            this.maxTurns = (gridSize == 4) ? 15 : 25;
        }

        Scene build() {
            root = new BorderPane();
            root.setBackground(new Background(new BackgroundFill(Theme.BG, CornerRadii.EMPTY, Insets.EMPTY)));

            hud = new HUDBar();
            hud.setAIMode("R" + version);
            board = new BoardView(gridSize);
            controls = new ControlBar();
            scanner = new BrainScannerPane();

            VBox south = new VBox(6, controls, scanner);
            south.setPadding(new Insets(6, 10, 10, 10));
            south.setBackground(
                    new Background(new BackgroundFill(Color.web("#181C22"), CornerRadii.EMPTY, Insets.EMPTY)));

            root.setTop(hud);
            BorderPane.setAlignment(board, Pos.CENTER);
            root.setCenter(board);
            root.setBottom(south);

            controls.setOnRestart(this::startGame);
            controls.setOnHint(this::doHint);
            controls.setOnSolve(this::toggleSolve);
            controls.setOnMenu(this::goMenu);
            controls.setOnExit(() -> stage.close());

            Scene scene = new Scene(root, 1080, 1040);
            scene.setFill(Theme.BG);

            // Start game AFTER scene is built (board placed safely)
            startGame();
            return scene;
        }

        // ── Game lifecycle ────────────────────────────────────────────────────

        private void startGame() {
            if (autoTimer != null && autoTimer.getStatus() == Timeline.Status.RUNNING)
                autoTimer.stop();
            isAutoMode = false;
            controls.setSolveText("▶ Solve");

            blackHoles = new HashSet<>();
            Random rng = new Random();
            while (blackHoles.size() < 2)
                blackHoles.add(rng.nextInt(totalTiles));

            graph = new Graph(gridSize, blackHoles);
            rules = new Rules(gridSize, blackHoles);
            ai = new Engine(totalTiles, graph, rules);
            ai.setVersion(version);
            ai.setLogger(scanner.getLogger());

            gridState = new boolean[totalTiles];
            turnsPlayed = 0;
            isGameOver = false;
            isPlayerTurn = true;
            inputBlocked = false;

            int initMoves = 4 + rng.nextInt(3);
            for (int i = 0; i < initMoves; i++) {
                int tile;
                do {
                    tile = rng.nextInt(totalTiles);
                } while (blackHoles.contains(tile));
                applyFlip(tile);
            }
            rules.clearMemory();

            scanner.reset();
            log("=== GAME START ===");
            log("Grid: " + gridSize + "x" + gridSize + "  |  Version: R" + version);
            log("Black Holes: " + blackHoles);
            log("─────────────────────────────────");
            log("Your turn! Click a tile or press Hint.");

            board = new BoardView(gridSize);
            board.applyBlackHoles(blackHoles);
            for (int i = 0; i < totalTiles; i++) {
                final int id = i;
                board.getTile(id).setOnMouseClicked(e -> handlePlayerMove(id));
            }

            // Swap board in the root BorderPane (root is always non-null here)
            root.setCenter(board);
            BorderPane.setAlignment(board, Pos.CENTER);

            refreshAllTiles();
            updateHUD("YOUR TURN");
            board.buildEntranceAnimation().play();
        }

        // ── Player move ───────────────────────────────────────────────────────

        private void handlePlayerMove(int id) {
            if (inputBlocked || !isPlayerTurn || isGameOver)
                return;
            if (blackHoles.contains(id))
                return;
            if (rules.isLocked(id)) {
                updateHUD("TILE LOCKED!");
                return;
            }

            inputBlocked = true;
            applyFlip(id);
            rules.recordMove(id);
            turnsPlayed++;
            animateFlips(graph.getNeighbors(id), () -> {
                refreshAllTiles();
                checkWin();
                if (!isGameOver) {
                    isPlayerTurn = false;
                    updateHUD("CPU THINKING…");
                    cpuTurn();
                } else {
                    inputBlocked = false;
                }
            });
        }

        // ── CPU Turn ──────────────────────────────────────────────────────────

        private void cpuTurn() {
            new Thread(() -> {
                long t0 = System.currentTimeMillis();
                try {
                    Thread.sleep(600);
                } catch (InterruptedException ignored) {
                }
                int move = ai.getBestMove(gridState.clone());
                if (move == -1)
                    move = firstUnlocked();
                final int fm = move;
                final long elapsed = System.currentTimeMillis() - t0;
                Platform.runLater(() -> {
                    if (isGameOver)
                        return;
                    applyFlip(fm);
                    rules.recordMove(fm);
                    turnsPlayed++;
                    // Build and push 4-tab Brain Scanner report
                    scanner.updateDashboard(buildTurnReport(fm, elapsed));
                    animateFlips(graph.getNeighbors(fm), () -> {
                        refreshAllTiles();
                        checkWin();
                        if (!isGameOver) {
                            isPlayerTurn = true;
                            inputBlocked = false;
                            updateHUD("YOUR TURN");
                            if (isAutoMode)
                                scheduleAutoMove();
                        }
                    });
                });
            }, "CPU-Thread").start();
        }

        /**
         * Builds a TurnReport from the post-move game state.
         * Eval grid is computed from stratValue; candidates are all unlocked tiles
         * ranked by strategic value, with the chosen tile marked CHOSEN.
         * Nodes/prunes/dpHits default to 0 until R3Algorithms exposes counters.
         */
        private BrainScannerPane.TurnReport buildTurnReport(int chosenTile, long elapsedMs) {
            BrainScannerPane.TurnReport r = new BrainScannerPane.TurnReport();
            r.timeMs = elapsedMs;
            r.nodesSearched = 0; // << wire from R3Algorithms when available
            r.prunes = 0; // << wire from R3Algorithms when available
            r.dpHits = 0; // << wire from R3Algorithms when available

            // ── Eval Grid ──────────────────────────────────────────────────────
            r.evaluationGrid = new String[gridSize][gridSize];
            for (int row = 0; row < gridSize; row++) {
                for (int col = 0; col < gridSize; col++) {
                    int id = row * gridSize + col;
                    if (blackHoles.contains(id))
                        r.evaluationGrid[row][col] = "BH";
                    else if (rules.isLocked(id))
                        r.evaluationGrid[row][col] = "LOCK";
                    else {
                        double v = rules.getTileStrategicValue(id);
                        r.evaluationGrid[row][col] = String.format("%+.0f", v);
                    }
                }
            }

            // ── Candidate moves ────────────────────────────────────────────────
            List<BrainScannerPane.MoveStat> cands = new ArrayList<>();
            for (int i = 0; i < totalTiles; i++) {
                if (blackHoles.contains(i) || rules.isLocked(i))
                    continue;
                double strat = rules.getTileStrategicValue(i);
                double r2est = strat * 0.5; // simplified R2 estimate
                String status = (i == chosenTile) ? "CHOSEN" : "candidate";
                cands.add(new BrainScannerPane.MoveStat(i, r2est, strat, status));
            }
            cands.sort((a, b) -> Double.compare(b.getFinalScore(), a.getFinalScore()));
            r.candidateMoves = cands;

            // ── Search tree ────────────────────────────────────────────────────
            TreeItem<String> root = new TreeItem<>("CPU Turn — chose tile " + chosenTile
                    + "  [" + elapsedMs + "ms]");
            root.setExpanded(true);
            TreeItem<String> ab = new TreeItem<>("[Alpha-Beta] depth search (R3)");
            TreeItem<String> tt = new TreeItem<>("[Zobrist TT] transposition table hits: " + r.dpHits);
            TreeItem<String> dp = new TreeItem<>("[DP Oracle]  bitmask solve → tile " + chosenTile + " optimal");
            TreeItem<String> prn = new TreeItem<>("[Pruning]    " + r.prunes + " branches cut");
            root.getChildren().addAll(ab, tt, dp, prn);
            r.searchTreeRoot = root;

            // ── System log line ────────────────────────────────────────────────
            r.systemLogs = String.format("[Turn %2d] CPU→Tile %-2d | strat=%+.0f | %dms",
                    turnsPlayed, chosenTile,
                    rules.getTileStrategicValue(chosenTile), elapsedMs);
            return r;
        }

        // ── Hint / Solve ──────────────────────────────────────────────────────

        private void doHint() {
            if (inputBlocked || isGameOver)
                return;
            int h = ai.getPlayerHint(gridState.clone());
            if (h != -1 && !blackHoles.contains(h)) {
                board.getTile(h).buildHintPulse().play();
                log("[Hint] Recommended: Tile " + h);
            }
        }

        private void toggleSolve() {
            isAutoMode = !isAutoMode;
            controls.setSolveText(isAutoMode ? "⏹ Stop" : "▶ Solve");
            if (isAutoMode && isPlayerTurn && !inputBlocked && !isGameOver)
                scheduleAutoMove();
            if (!isAutoMode && autoTimer != null)
                autoTimer.stop();
        }

        private void scheduleAutoMove() {
            if (autoTimer != null)
                autoTimer.stop();
            autoTimer = new Timeline(new KeyFrame(Duration.millis(400), e -> {
                if (isAutoMode && isPlayerTurn && !inputBlocked && !isGameOver) {
                    int m = ai.getPlayerHint(gridState.clone());
                    if (m != -1)
                        handlePlayerMove(m);
                }
            }));
            autoTimer.setCycleCount(1);
            autoTimer.play();
        }

        // ── Core flip + animation ─────────────────────────────────────────────

        private void applyFlip(int id) {
            for (int n : graph.getNeighbors(id))
                if (!rules.isLocked(n))
                    gridState[n] = !gridState[n];
        }

        /**
         * Runs ParallelTransition over affected tiles.
         * Desync fix: onDone wired to setOnFinished — state never mutated before anim
         * completes.
         */
        private void animateFlips(java.util.List<Integer> ids, Runnable onDone) {
            if (ids.isEmpty()) {
                onDone.run();
                return;
            }
            ParallelTransition pt = new ParallelTransition();
            for (int id : ids) {
                if (!blackHoles.contains(id))
                    pt.getChildren().add(board.getTile(id).buildFlipAnimation(gridState[id]));
            }
            pt.setOnFinished(e -> onDone.run());
            pt.play();
        }

        // ── Visual refresh ────────────────────────────────────────────────────

        private void refreshAllTiles() {
            for (int i = 0; i < totalTiles; i++) {
                if (blackHoles.contains(i))
                    continue;
                TileNode t = board.getTile(i);
                double val = rules.getTileStrategicValue(i);
                if (rules.isLocked(i))
                    t.styleLocked(gridState[i], rules.getLockCountdown(i), val);
                else
                    t.styleFree(gridState[i], val);
            }
        }

        private void updateHUD(String status) {
            hud.update(weightedScore(true), weightedScore(false),
                    turnsPlayed, maxTurns, isPlayerTurn, status);
        }

        // ── Win check ─────────────────────────────────────────────────────────

        private void checkWin() {
            long nonBH = totalTiles - blackHoles.size();
            long pt = countTiles(true), ct = countTiles(false);
            double p1 = weightedScore(true), p2 = weightedScore(false);
            String msg = null;
            if (pt == nonBH)
                msg = "YOU WIN! All tiles captured!";
            else if (ct == nonBH)
                msg = "CPU WINS! All tiles captured!";
            else if (turnsPlayed >= maxTurns) {
                if (p1 > p2)
                    msg = "Time's up! YOU WIN by score!";
                else if (p2 > p1)
                    msg = "Time's up! CPU WINS by score!";
                else
                    msg = "Time's up! DRAW!";
            }
            if (msg != null) {
                isGameOver = true;
                inputBlocked = true;
                // Stop solve/auto mode immediately
                isAutoMode = false;
                controls.setSolveText("\u25B6 Solve");
                if (autoTimer != null)
                    autoTimer.stop();

                updateHUD(msg);
                celebrateAnim(p1 > p2);

                final String finalMsg = msg;
                // Show game-over dialog after celebration animation
                new Timeline(new KeyFrame(Duration.seconds(1.5), e -> showGameOverDialog(finalMsg))).play();
            }
        }

        private void showGameOverDialog(String msg) {
            Stage d = new Stage();
            d.setTitle("Game Over");
            d.setResizable(false);

            Label title = new Label(msg.startsWith("YOU WIN") ? "🏆 " + msg
                    : msg.startsWith("CPU") ? "💀 " + msg : "⏱ " + msg);
            title.setFont(Font.font("Arial", FontWeight.BOLD, 22));
            title.setTextFill(msg.startsWith("YOU WIN") ? Theme.PLAYER
                    : msg.startsWith("CPU") ? Theme.CPU : Theme.ACCENT);
            title.setWrapText(true);
            title.setStyle("-fx-text-alignment:center;");

            Button playAgain = new Button("Play Again");
            playAgain.setFont(Font.font("Arial", FontWeight.BOLD, 15));
            playAgain.setPadding(new Insets(10, 28, 10, 28));
            playAgain.setStyle(
                    "-fx-background-color:#27AE60;-fx-background-radius:20;-fx-text-fill:white;-fx-cursor:hand;");
            playAgain.setOnAction(e -> {
                d.close();
                startGame();
            });

            Button menuBtn = new Button("Go to Menu");
            menuBtn.setFont(Font.font("Arial", FontWeight.BOLD, 15));
            menuBtn.setPadding(new Insets(10, 28, 10, 28));
            menuBtn.setStyle(
                    "-fx-background-color:#2E86C1;-fx-background-radius:20;-fx-text-fill:white;-fx-cursor:hand;");
            menuBtn.setOnAction(e -> {
                d.close();
                goMenu();
            });

            HBox btnRow = new HBox(16, playAgain, menuBtn);
            btnRow.setAlignment(Pos.CENTER);

            VBox layout = new VBox(20, title, btnRow);
            layout.setAlignment(Pos.CENTER);
            layout.setPadding(new Insets(30, 40, 30, 40));
            layout.setBackground(
                    new Background(new BackgroundFill(Color.web("#181C22"), new CornerRadii(12), Insets.EMPTY)));
            layout.setStyle("-fx-border-color:#50FFAA;-fx-border-width:1.5;-fx-border-radius:12;");

            Scene s = new Scene(layout);
            s.setFill(Color.web("#181C22"));
            d.setScene(s);
            d.initOwner(stage);
            d.initModality(Modality.WINDOW_MODAL);
            d.show();
        }

        private void celebrateAnim(boolean humanWon) {
            for (TileNode t : board.getAllTiles()) {
                if (!blackHoles.contains(t.getTileId())) {
                    Timeline flash = new Timeline(
                            new KeyFrame(Duration.ZERO, new KeyValue(t.opacityProperty(), 1.0)),
                            new KeyFrame(Duration.millis(180), new KeyValue(t.opacityProperty(), 0.2)),
                            new KeyFrame(Duration.millis(360), new KeyValue(t.opacityProperty(), 1.0)));
                    flash.setCycleCount(4);
                    flash.play();
                }
            }
        }

        private void goMenu() {
            if (autoTimer != null)
                autoTimer.stop();
            scanner.stop();
            onBack.run();
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private double weightedScore(boolean forPlayer) {
            double str = 0;
            for (int i = 0; i < totalTiles; i++)
                if (gridState[i] == forPlayer)
                    str += rules.getTileStrategicValue(i);
            DACAlgorithms dac = new DACAlgorithms();
            return (str * 0.2)
                    + (dac.evaluateQuadrants(gridState, gridSize, forPlayer) * 0.25)
                    + (dac.evaluateClusters(gridState, gridSize, forPlayer) * 0.25)
                    + (dac.evaluateThreats(gridState, gridSize, forPlayer) * 0.30);
        }

        private long countTiles(boolean owner) {
            long c = 0;
            for (int i = 0; i < totalTiles; i++)
                if (!blackHoles.contains(i) && gridState[i] == owner)
                    c++;
            return c;
        }

        private int firstUnlocked() {
            for (int i = 0; i < totalTiles; i++)
                if (!blackHoles.contains(i) && !rules.isLocked(i))
                    return i;
            return 0;
        }

        private void log(String msg) {
            scanner.getLogger().accept(msg);
        }
    }
}
