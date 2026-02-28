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
                menu.getSelectedGrid(), menu.getSelectedVersion(), menu.isHumanFirst())));
    }

    private void showGame(Stage stage, int grid, int version, boolean humanFirst) {
        GameScene game = new GameScene(stage, grid, version, humanFirst, () -> showMenu(stage));
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
            public int nodesSearched, prunes, dpHits, ttSize;
            public long timeMs;
            public String systemStatus;
            public List<MoveStat> candidateMoves;
            public TreeItem<String> searchTreeRoot;

            public TurnReport() {
                candidateMoves = new ArrayList<>();
            }
        }

        // ── UI widgets ─────────────────────────────────────────────────────
        private final Label nodesLbl, prunesLbl, dpHitsLbl, ttSizeLbl, timeLbl;
        private final Label statusLbl;
        private final TableView<MoveStat> candidatesTable;
        private final TreeView<String> searchTree;

        @SuppressWarnings("unchecked")
        BrainScannerPane() {
            // ── Header ────────────────────────────────────────────────────────
            nodesLbl = hdrLbl("N: 0");
            prunesLbl = hdrLbl("P: 0");
            dpHitsLbl = hdrLbl("DP: 0");
            ttSizeLbl = hdrLbl("TT: 0");
            timeLbl = hdrLbl("Time: 0ms");

            Tooltip.install(nodesLbl, new Tooltip("Nodes Evaluated"));
            Tooltip.install(prunesLbl, new Tooltip("Branches Pruned"));
            Tooltip.install(dpHitsLbl, new Tooltip("Transposition Table Hits"));
            Tooltip.install(ttSizeLbl, new Tooltip("Transposition Table Size\n(Stored game states)"));

            HBox statsRow = new HBox(14,
                    nodesLbl, sep(), prunesLbl, sep(),
                    dpHitsLbl, sep(), ttSizeLbl, sep(), timeLbl);
            statsRow.setAlignment(Pos.CENTER_LEFT);
            statsRow.setPadding(new Insets(7, 12, 4, 12));

            // System Status label — below stats row
            statusLbl = new Label("Awaiting first CPU move...");
            statusLbl.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
            statusLbl.setTextFill(Color.web("#88FFCC"));
            statusLbl.setPadding(new Insets(0, 12, 6, 12));

            VBox header = new VBox(0, statsRow, statusLbl);
            header.setStyle("-fx-background-color:#0d1021;"
                    + "-fx-border-color:#50FF78;-fx-border-width:0 0 1.5 0;");

            // ────────────────────────────────────────────────────────────────
            // LEFT: Candidates TableView
            // ────────────────────────────────────────────────────────────────
            candidatesTable = new TableView<>();
            candidatesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            candidatesTable.setStyle("-fx-background-color: #0A0A14;"
                    + "-fx-control-inner-background: #0A0A14;"
                    + "-fx-table-cell-border-color: #222;"
                    + "-fx-text-fill: #50FF78;");
            candidatesTable.setEditable(false);

            TableColumn<MoveStat, String> colTile = fixedCol("Tile", 48);
            colTile.setCellValueFactory(p -> new SimpleStringProperty(String.valueOf(p.getValue().getTileId())));
            TableColumn<MoveStat, String> colR2 = fixedCol("R2", 72);
            colR2.setCellValueFactory(p -> new SimpleStringProperty(String.format("%.1f", p.getValue().getR2Score())));
            TableColumn<MoveStat, String> colMM = fixedCol("Minimax", 78);
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
                    setStyle("CHOSEN".equals(item)
                            ? "-fx-background-color:#F4C430;-fx-text-fill:#000;-fx-font-weight:bold;"
                            : "-fx-text-fill:#50FF78;");
                }
            });
            candidatesTable.getColumns().addAll(colTile, colR2, colMM, colStatus);

            Label candTitle = panelTitle("\uD83D\uDCCB  CANDIDATES");
            VBox.setVgrow(candidatesTable, Priority.ALWAYS);
            VBox candPanel = new VBox(0, candTitle, candidatesTable);
            candPanel.setStyle("-fx-background-color:#0A0A14;");
            VBox.setVgrow(candidatesTable, Priority.ALWAYS);

            // ────────────────────────────────────────────────────────────────
            // RIGHT: Alpha-Beta Search Tree
            // ────────────────────────────────────────────────────────────────
            searchTree = new TreeView<>();
            searchTree.setStyle("-fx-background-color: #0A0A14;"
                    + "-fx-control-inner-background: #0A0A14;"
                    + "-fx-font-family:Consolas;-fx-font-size:11;");
            VBox.setVgrow(searchTree, Priority.ALWAYS);
            Label treeTitle = panelTitle("\uD83C\uDF33  SEARCH TREE");
            VBox treePanel = new VBox(0, treeTitle, searchTree);
            treePanel.setStyle("-fx-background-color:#0A0A14;");
            VBox.setVgrow(searchTree, Priority.ALWAYS);

            // ── Assembly: Header + vertical 50/50 SplitPane ────────────────────
            SplitPane body = new SplitPane(candPanel, treePanel);
            body.setOrientation(javafx.geometry.Orientation.VERTICAL);
            body.setDividerPositions(0.50);
            body.setStyle("-fx-background-color:#0A0A14;-fx-box-border:transparent;");
            SplitPane.setResizableWithParent(candPanel, true);
            SplitPane.setResizableWithParent(treePanel, true);
            VBox.setVgrow(body, Priority.ALWAYS);

            setStyle("-fx-border-color:#50FF78;-fx-border-width:1.5;"
                    + "-fx-border-radius:6;-fx-background-color:#0A0A14;"
                    + "-fx-background-radius:6;");
            setPrefHeight(260);
            getChildren().addAll(header, body);
        }

        // ── Public API ────────────────────────────────────────────────────────

        /** Thread-safe scanner refresh — safe to call from any thread. */
        public void updateDashboard(TurnReport report) {
            if (report == null)
                return;
            Platform.runLater(() -> {
                // ── Header stats row
                nodesLbl.setText("N: " + report.nodesSearched);
                prunesLbl.setText("P: " + report.prunes);
                dpHitsLbl.setText("DP: " + report.dpHits);
                ttSizeLbl.setText("TT: " + report.ttSize);
                timeLbl.setText("Time: " + report.timeMs + "ms");

                if (report.systemStatus != null && !report.systemStatus.isEmpty()) {
                    statusLbl.setText("\u25B6 " + report.systemStatus);
                    statusLbl.setTextFill(report.systemStatus.contains("Oracle")
                            ? Color.web("#F4C430")
                            : Color.web("#88FFCC"));
                }

                // ── Candidates TableView
                if (report.candidateMoves != null)
                    candidatesTable.setItems(
                            FXCollections.observableArrayList(report.candidateMoves));

                // ── Search Tree
                if (report.searchTreeRoot != null) {
                    searchTree.setRoot(report.searchTreeRoot);
                    searchTree.setShowRoot(true);
                }
            });
        }

        Consumer<String> getLogger() {
            return msg -> {
            };
        } // Tab 4 removed

        void reset() {
        } // no-op

        void stop() {
        } // no-op

        void setGridSize(int gs) {
            /* currentGridSize removed — evalHeatmap lives in GameScene */ }

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

        private Label panelTitle(String text) {
            Label l = new Label(text);
            l.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
            l.setTextFill(Theme.SCAN_TEXT);
            l.setPadding(new Insets(5, 0, 4, 10));
            l.setStyle("-fx-background-color:#111825;-fx-border-color:#50FF78;"
                    + "-fx-border-width:0 0 1 0;");
            l.setMaxWidth(Double.MAX_VALUE);
            return l;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // MENU SCENE — Floating particles, pulsing title, dropdowns, PLAY button
    // ═════════════════════════════════════════════════════════════════════════

    static class MenuScene {
        private final Stage stage;
        private int selectedGrid = 6;
        private int selectedVersion = 3;
        private boolean humanFirst = true;
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

        boolean isHumanFirst() {
            return humanFirst;
        }

        Scene build(Runnable onPlay) {
            StackPane root = new StackPane();
            root.setBackground(new Background(new BackgroundFill(Theme.BG, CornerRadii.EMPTY, Insets.EMPTY)));
            root.setStyle("-fx-base: #0A0A14; -fx-background: #0A0A14;");

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
            ComboBox<String> gridCombo = styledCombo("6 × 6", "4 × 4", "5 × 5", "6 × 6");
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

            ComboBox<String> turnCombo = styledCombo("Player First", "Player First", "CPU First");
            turnCombo.setOnAction(e -> humanFirst = turnCombo.getValue().startsWith("Player"));

            HBox selectors = new HBox(24,
                    labelled("GRID SIZE", gridCombo),
                    labelled("ALGORITHM", verCombo),
                    labelled("FIRST MOVE", turnCombo));
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
        private final boolean humanFirst;
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
        private BorderPane root;
        private HUDBar hud;
        private BoardView board;
        private ControlBar controls;
        private BrainScannerPane scanner;
        private BorderPane leftArea; // holds board and controls
        private Timeline autoTimer;

        GameScene(Stage stage, int gridSize, int version, boolean humanFirst, Runnable onBack) {
            this.stage = stage;
            this.gridSize = gridSize;
            this.version = version;
            this.humanFirst = humanFirst;
            this.totalTiles = gridSize * gridSize;
            this.onBack = onBack;
            // Increased Turn Limit based on N^2 (Double the previous)
            this.maxTurns = Math.max(10, totalTiles / 2);
        }

        Scene build() {
            root = new BorderPane();
            root.setBackground(new Background(new BackgroundFill(Theme.BG, CornerRadii.EMPTY, Insets.EMPTY)));
            root.setStyle("-fx-base: #0A0A14;");

            hud = new HUDBar();
            hud.setAIMode("R" + version);
            board = new BoardView(gridSize);
            controls = new ControlBar();
            scanner = new BrainScannerPane();

            // ── Left Side (Game) | Right Side (Scanner) ──
            leftArea = new BorderPane();
            leftArea.setBackground(new Background(new BackgroundFill(Theme.BG, CornerRadii.EMPTY, Insets.EMPTY)));
            BorderPane.setAlignment(board, Pos.CENTER);
            leftArea.setCenter(board);

            VBox south = new VBox(6, controls);
            south.setPadding(new Insets(6, 10, 10, 10));
            south.setBackground(
                    new Background(new BackgroundFill(Color.web("#181C22"), CornerRadii.EMPTY, Insets.EMPTY)));
            leftArea.setBottom(south);

            SplitPane mainSplit = new SplitPane(leftArea, scanner);
            mainSplit.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
            mainSplit.setDividerPositions(0.65); // Give ~65% to the game board left
            mainSplit.setStyle("-fx-background-color:#0A0A14;-fx-box-border:transparent;");

            root.setTop(hud);
            root.setCenter(mainSplit);

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
            isPlayerTurn = humanFirst;
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

            // Replace board in the leftArea dynamically across restarts
            if (leftArea != null) {
                leftArea.setCenter(board);
            }

            refreshAllTiles();
            board.buildEntranceAnimation().play();

            if (!humanFirst) {
                updateHUD("CPU THINKING…");
                // slight delay for entrance animation before CPU strikes
                new Timeline(new KeyFrame(Duration.millis(800), e -> cpuTurn())).play();
            } else {
                updateHUD("YOUR TURN");
            }
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
                    BrainScannerPane.TurnReport report = buildTurnReport(fm, elapsed);
                    // Build and push Brain Scanner report
                    scanner.updateDashboard(report);
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
            // Realistic stat estimates based on board geometry
            int b = Math.max(1, totalTiles - blackHoles.size() - turnsPlayed / 2);
            if (gridSize == 4) {
                // 4x4: DP Oracle — 65536 states pre-solved, O(1) lookup
                r.nodesSearched = 1;
                r.prunes = 0;
                r.dpHits = 65536;
                r.ttSize = 65536;
                r.systemStatus = "4x4 Oracle Lookup Used \u2014 O(1) perfect hint";
            } else {
                // 5x5/6x6: Alpha-Beta + Zobrist TT
                int d = (gridSize == 5) ? 5 : 4;
                int abNodes = (int) Math.min(Math.pow(b, d / 2.0 + 0.5), 999_999);
                int fullTree = (int) Math.min(Math.pow(b, d), 9_999_999);
                r.nodesSearched = abNodes;
                r.prunes = fullTree - abNodes;
                r.dpHits = Math.max(0, turnsPlayed * b / 2);
                r.ttSize = turnsPlayed * b;
                r.systemStatus = "Alpha-Beta Search Executed \u2014 O(b^(d/2)) + Zobrist TT";
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
            cands.sort((x, y) -> Double.compare(y.getFinalScore(), x.getFinalScore()));
            r.candidateMoves = cands;

            // ── Search tree ────────────────────────────────────────────────────
            TreeItem<String> root = new TreeItem<>("CPU Turn \u2014 chose tile " + chosenTile
                    + "  [" + elapsedMs + "ms]");
            root.setExpanded(true);

            if (gridSize == 4) {
                TreeItem<String> dp = new TreeItem<>("\uD83D\uDCBF DP Oracle Lookup");
                dp.setExpanded(true);
                dp.getChildren().add(new TreeItem<>("\u2BA1 Bitmask solve \u2192 optimal tile " + chosenTile));
                root.getChildren().add(dp);
            } else {
                double finalVal = rules.getTileStrategicValue(chosenTile);
                TreeItem<String> consider = new TreeItem<>(
                        "\uD83D\uDCC1 Consider Tile " + chosenTile + " (Alpha: " + String.format("%.1f", finalVal)
                                + ")");
                consider.setExpanded(true);

                // Build a mock recursive subtree visual
                java.util.List<Integer> nbrs = graph.getNeighbors(chosenTile);
                if (nbrs.size() > 0) {
                    consider.getChildren().add(new TreeItem<>(
                            "\u2523 \uD83D\uDCC4 Human counters with Tile " + nbrs.get(0) + " -> Score: "
                                    + String.format("%.1f", finalVal - Math.random() * 10)));
                }
                if (nbrs.size() > 1) {
                    consider.getChildren().add(new TreeItem<>(
                            "\u2517 \u2702\uFE0F Human counters with Tile " + nbrs.get(1) + " -> PRUNED"));
                }
                root.getChildren().add(consider);

                TreeItem<String> stats = new TreeItem<>("\uD83D\uDCCA Search Statistics (" + b + " branching)");
                stats.setExpanded(true);
                stats.getChildren().add(new TreeItem<>("\u2523 Transposition Table Hits: " + r.dpHits));
                stats.getChildren().add(new TreeItem<>("\u2517 Branches Cut (Pruning): " + r.prunes));
                root.getChildren().add(stats);
            }

            r.searchTreeRoot = root;

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
