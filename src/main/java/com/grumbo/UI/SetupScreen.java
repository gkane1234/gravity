package com.grumbo.ui;

import com.grumbo.simulation.SimulationSetup;
import com.grumbo.simulation.SimulationSetup.ObjectType;
import com.grumbo.simulation.SimulationSetup.Preset;
import com.grumbo.simulation.SimulationSetup.SceneItem;
import com.grumbo.simulation.SpiceKernel;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * First-launch setup screen: choose presets and place objects before GPU buffers are allocated.
 * The window is JavaFX. The simulation window stays GLFW and is not open at the same time.
 */
public final class SetupScreen {

    private static final Object TOOLKIT_LOCK = new Object();
    private static volatile boolean toolkitStarted;

    private final SimulationSetup setup = new SimulationSetup();
    /** Scene from the last successful launch. Null until the first launch in this process. */
    static LaunchMemory launchMemory;

    private boolean finished;
    private boolean cancelled;
    private SimulationSetup.LaunchConfig result;
    private CountDownLatch done;
    private Stage stage;

    private ObjectType addType = ObjectType.DISK;

    // Persist field values across launches. Tests write these without opening the window.
    private String vCx = "0", vCy = "0", vCz = "0";
    private String vCount = "50000", vRadius = "800", vMass = "1";
    private String vDensity = "1", vCenterMass = "50000", vExtent = "500";
    private String dLabel = "Disk", dInner = "1", dMassMin = "0.2", dMassMax = "2";
    private String dDensityMin = "1", dDensityMax = "1", dCenterDensity = "1";
    private String dNx = "0", dNy = "1", dNz = "0", dVx = "0", dVy = "0", dVz = "0";
    private String dAdherence = "0.98", dOrbital = "1";
    private boolean dCcw;
    private boolean dOrbitalVel = true;

    private TextField fieldCx, fieldCy, fieldCz;
    private TextField fieldCount, fieldRadius, fieldMass;
    private TextField fieldDensity, fieldCenterMass, fieldExtent;
    private Label statusLabel;
    private Label sceneHeading;
    private ListView<SceneItem> sceneList;
    private final VBox addFieldsHost = new VBox(8);
    private DiskForm addDiskForm;
    private final VBox diskEditorHost = new VBox(8);
    private DiskForm editDiskForm;
    private Label editHeading;
    private int editingIndex = -1;
    private final ObservableList<SceneItem> sceneItems = FXCollections.observableArrayList();
    private String lastNote = "";

    SetupScreen() {}

    /**
     * Starts the JavaFX toolkit once. Safe to call again. Must be called off the JavaFX thread.
     * Direct3D is selected when the caller has not already chosen a Prism pipeline, so JavaFX
     * does not create an OpenGL context ahead of the GLFW simulation window.
     */
    public static void startToolkit() {
        if (toolkitStarted) {
            return;
        }
        synchronized (TOOLKIT_LOCK) {
            if (toolkitStarted) {
                return;
            }
            if (System.getProperty("prism.order") == null) {
                System.setProperty("prism.order", "d3d");
            }
            CountDownLatch ready = new CountDownLatch(1);
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                ready.countDown();
            });
            toolkitStarted = true;
            try {
                ready.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("JavaFX startup interrupted", e);
            }
        }
    }

    /**
     * Stops the JavaFX toolkit. Call this only when the process is exiting.
     * The toolkit cannot be started again after this returns.
     */
    public static void stopToolkit() {
        if (!toolkitStarted) {
            return;
        }
        Platform.exit();
    }

    public static SimulationSetup.LaunchConfig run() {
        startToolkit();
        return new SetupScreen().loop();
    }

    static void forgetLaunch() {
        launchMemory = null;
    }

    private SimulationSetup.LaunchConfig loop() {
        done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                show();
            } catch (RuntimeException e) {
                e.printStackTrace();
                finish(true);
            }
        });
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Platform.runLater(() -> finish(true));
            return null;
        }
        if (cancelled || result == null) {
            return null;
        }
        return result;
    }

    private void show() {
        prepareInitialScene();

        stage = new Stage();
        stage.setTitle("GravityChunk — Setup");
        BorderPane root = buildUi();
        Scene scene = new Scene(root, 1000, 700);
        var stylesheet = SetupScreen.class.getResource("/ui/setup.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        stage.setScene(scene);
        stage.setMinWidth(860);
        stage.setMinHeight(640);
        stage.setOnCloseRequest(event -> {
            event.consume();
            quit();
        });
        refreshScene();
        stage.show();
    }

    private void onKeyPressed(KeyEvent event) {
        if (event.getCode() != KeyCode.ESCAPE || stage.getScene() == null) {
            return;
        }
        event.consume();
        // A focused field used to swallow Escape. Clear it first; the next Escape quits.
        if (stage.getScene().getFocusOwner() instanceof TextField) {
            stage.getScene().getRoot().requestFocus();
            return;
        }
        quit();
    }

    private void quit() {
        finish(true);
    }

    private void finish(boolean cancel) {
        if (finished) {
            return;
        }
        finished = true;
        if (cancel) {
            cancelled = true;
            result = null;
        }
        if (stage != null) {
            stage.hide();
        }
        done.countDown();
    }

    /**
     * Last launched scene, including add-object fields. Planets are already deep-copied.
     */
    static final class LaunchMemory {
        final SimulationSetup scene;
        final ObjectType addType;
        final String vCx, vCy, vCz, vCount, vRadius, vMass, vDensity, vCenterMass, vExtent;

        private LaunchMemory(SimulationSetup scene, ObjectType addType,
                             String vCx, String vCy, String vCz,
                             String vCount, String vRadius, String vMass,
                             String vDensity, String vCenterMass, String vExtent) {
            this.scene = scene;
            this.addType = addType;
            this.vCx = vCx;
            this.vCy = vCy;
            this.vCz = vCz;
            this.vCount = vCount;
            this.vRadius = vRadius;
            this.vMass = vMass;
            this.vDensity = vDensity;
            this.vCenterMass = vCenterMass;
            this.vExtent = vExtent;
        }

        static LaunchMemory capture(SimulationSetup setup, ObjectType addType,
                                    String vCx, String vCy, String vCz,
                                    String vCount, String vRadius, String vMass,
                                    String vDensity, String vCenterMass, String vExtent) {
            return new LaunchMemory(setup.copy(), addType,
                vCx, vCy, vCz, vCount, vRadius, vMass, vDensity, vCenterMass, vExtent);
        }
    }

    /** First open uses the full merger. Later opens use the scene that was just launched. */
    void prepareInitialScene() {
        if (!restoreRememberedScene()) {
            setup.applyPreset(Preset.MERGER_FULL);
        }
    }

    boolean restoreRememberedScene() {
        LaunchMemory memory = launchMemory;
        if (memory == null) {
            return false;
        }
        setup.restoreFrom(memory.scene);
        addType = memory.addType;
        vCx = memory.vCx;
        vCy = memory.vCy;
        vCz = memory.vCz;
        vCount = memory.vCount;
        vRadius = memory.vRadius;
        vMass = memory.vMass;
        vDensity = memory.vDensity;
        vCenterMass = memory.vCenterMass;
        vExtent = memory.vExtent;
        return true;
    }

    void rememberLaunch() {
        snapshotFields();
        launchMemory = LaunchMemory.capture(setup, addType,
            vCx, vCy, vCz, vCount, vRadius, vMass, vDensity, vCenterMass, vExtent);
    }

    SimulationSetup scene() {
        return setup;
    }

    ObjectType addObjectType() {
        return addType;
    }

    String[] addObjectFields() {
        return new String[] {vCx, vCy, vCz, vCount, vRadius, vMass, vDensity, vCenterMass, vExtent};
    }

    void setAddForm(ObjectType type, String[] fields) {
        addType = type;
        vCx = fields[0];
        vCy = fields[1];
        vCz = fields[2];
        vCount = fields[3];
        vRadius = fields[4];
        vMass = fields[5];
        vDensity = fields[6];
        vCenterMass = fields[7];
        vExtent = fields[8];
    }

    private void snapshotFields() {
        if (fieldCx != null) vCx = fieldCx.getText();
        if (fieldCy != null) vCy = fieldCy.getText();
        if (fieldCz != null) vCz = fieldCz.getText();
        if (fieldCount != null) vCount = fieldCount.getText();
        if (fieldRadius != null) vRadius = fieldRadius.getText();
        if (fieldMass != null) vMass = fieldMass.getText();
        if (fieldDensity != null) vDensity = fieldDensity.getText();
        if (fieldCenterMass != null) vCenterMass = fieldCenterMass.getText();
        if (fieldExtent != null) vExtent = fieldExtent.getText();
        if (addDiskForm != null) {
            dLabel = addDiskForm.label.getText();
            dInner = addDiskForm.inner.getText();
            dMassMin = addDiskForm.massMin.getText();
            dMassMax = addDiskForm.massMax.getText();
            dDensityMin = addDiskForm.densityMin.getText();
            dDensityMax = addDiskForm.densityMax.getText();
            dCenterDensity = addDiskForm.centerDensity.getText();
            dNx = addDiskForm.nx.getText();
            dNy = addDiskForm.ny.getText();
            dNz = addDiskForm.nz.getText();
            dVx = addDiskForm.vx.getText();
            dVy = addDiskForm.vy.getText();
            dVz = addDiskForm.vz.getText();
            dAdherence = addDiskForm.adherence.getText();
            dOrbital = addDiskForm.orbital.getText();
            dCcw = addDiskForm.ccw.isSelected();
            dOrbitalVel = addDiskForm.orbitalVelocity.isSelected();
        }
    }

    private BorderPane buildUi() {
        Label title = new Label("GravityChunk Setup");
        title.getStyleClass().add("title");

        VBox form = new VBox(8,
            title,
            section("Presets"),
            presetRow1(),
            presetRow2(),
            section("Add object"),
            typeRow(),
            addFieldsHost,
            actionRow(),
            statusLabel = new Label(),
            launchRow(),
            diskEditorHost
        );
        diskEditorHost.setVisible(false);
        diskEditorHost.setManaged(false);
        rebuildAddFields();
        statusLabel.setWrapText(true);
        form.setFillWidth(true);

        ScrollPane leftScroll = new ScrollPane(form);
        leftScroll.setFitToWidth(true);
        leftScroll.getStyleClass().add("setup-scroll");
        leftScroll.setMinWidth(540);
        leftScroll.setPrefWidth(560);
        leftScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        sceneHeading = new Label();
        sceneHeading.getStyleClass().add("section");
        Label editHint = new Label("Click a disk to edit it below Launch.");
        editHint.getStyleClass().add("help");
        sceneList = new ListView<>(sceneItems);
        sceneList.setCellFactory(list -> new SceneCell());
        sceneList.setPlaceholder(new Label("(empty)"));
        VBox.setVgrow(sceneList, Priority.ALWAYS);

        Label helpHeading = new Label("Simulation controls");
        helpHeading.getStyleClass().add("section");
        Label help = new Label(String.join("\n", CONTROL_HELP));
        help.getStyleClass().add("help");

        VBox right = new VBox(8, sceneHeading, editHint, sceneList, helpHeading, help);
        right.setMinWidth(340);
        VBox.setVgrow(sceneList, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));
        root.setLeft(leftScroll);
        root.setCenter(right);
        BorderPane.setMargin(right, new Insets(0, 0, 0, 16));
        return root;
    }

    private static final String[] CONTROL_HELP = {
        "Close sim window: back to this menu",
        "F1  Pause / resume (starts paused)",
        "ENTER  Step one frame while paused",
        "ESC  Capture / free mouse",
        "Mouse  Look (when captured)",
        "WASD  Move  |  Q/E  Up/down",
        "IJKL  Move on world axes",
        "Shift  Hold for faster move",
        "+/-  Camera scale (zoom)",
        "Up/Down  Time step (dt)",
        "Left/Right  Barnes-Hut theta",
        "[ / ]  Softening",
        "`  Settings window",
        "F2  Tree regions  |  F3  Debug",
        "F4  Stats  |  F5  Crosshair",
        "F6  Record PNG frames",
    };

    private Label section(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section");
        return label;
    }

    private HBox presetRow1() {
        return new HBox(8,
            presetButton(Preset.EMPTY, "Empty"),
            presetButton(Preset.SOLAR_SYSTEM, "Solar Sys"),
            presetButton(Preset.SMALL_GALAXY, "Small Gal")
        );
    }

    private HBox presetRow2() {
        return new HBox(8,
            presetButton(Preset.MERGER_SCALED, "Merger sm"),
            presetButton(Preset.MERGER_FULL, "Merger 3.8M"),
            actionButton("Randomize", this::randomizeLayout)
        );
    }

    private Button presetButton(Preset preset, String shortLabel) {
        return actionButton(shortLabel, () -> {
            snapshotFields();
            lastNote = "";
            setup.applyPreset(preset);
            refreshScene();
        });
    }

    private HBox typeRow() {
        ToggleGroup types = new ToggleGroup();
        types.selectedToggleProperty().addListener((obs, oldToggle, selected) -> {
            if (selected == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });
        return new HBox(8,
            typeButton(types, "Disk", ObjectType.DISK),
            typeButton(types, "Box", ObjectType.BOX),
            typeButton(types, "Body", ObjectType.SINGLE),
            typeButton(types, "Solar JSON", ObjectType.SOLAR_SYSTEM)
        );
    }

    private ToggleButton typeButton(ToggleGroup group, String label, ObjectType type) {
        ToggleButton button = new ToggleButton(label);
        button.setToggleGroup(group);
        button.setSelected(addType == type);
        button.setOnAction(event -> {
            snapshotFields();
            addType = type;
            button.setSelected(true);
            rebuildAddFields();
        });
        return button;
    }

    private HBox actionRow() {
        return new HBox(8,
            actionButton("Add", this::addCurrentObject),
            actionButton("Load BSP", this::loadBsp),
            actionButton("Clear", () -> {
                snapshotFields();
                lastNote = "";
                hideDiskEditor();
                setup.clear();
                refreshScene();
            })
        );
    }

    private HBox launchRow() {
        return new HBox(8,
            actionButton("LAUNCH", this::launch),
            actionButton("Quit app", this::quit)
        );
    }

    /** Disk gets every generator field. The other types keep the short form. */
    private void rebuildAddFields() {
        snapshotFields();
        addFieldsHost.getChildren().clear();
        fieldCx = fieldCy = fieldCz = null;
        fieldCount = fieldRadius = fieldMass = null;
        fieldDensity = fieldCenterMass = fieldExtent = null;
        addDiskForm = null;
        if (addType == ObjectType.DISK) {
            addDiskForm = new DiskForm();
            addDiskForm.cx.setText(vCx);
            addDiskForm.cy.setText(vCy);
            addDiskForm.cz.setText(vCz);
            addDiskForm.count.setText(vCount);
            addDiskForm.outer.setText(vRadius);
            addDiskForm.label.setText(dLabel);
            addDiskForm.inner.setText(dInner);
            addDiskForm.massMin.setText(dMassMin);
            addDiskForm.massMax.setText(dMassMax);
            addDiskForm.centerMass.setText(vCenterMass);
            addDiskForm.centerDensity.setText(dCenterDensity);
            addDiskForm.densityMin.setText(dDensityMin);
            addDiskForm.densityMax.setText(dDensityMax);
            addDiskForm.nx.setText(dNx);
            addDiskForm.ny.setText(dNy);
            addDiskForm.nz.setText(dNz);
            addDiskForm.vx.setText(dVx);
            addDiskForm.vy.setText(dVy);
            addDiskForm.vz.setText(dVz);
            addDiskForm.adherence.setText(dAdherence);
            addDiskForm.orbital.setText(dOrbital);
            addDiskForm.ccw.setSelected(dCcw);
            addDiskForm.orbitalVelocity.setSelected(dOrbitalVel);
            fieldCx = addDiskForm.cx;
            fieldCy = addDiskForm.cy;
            fieldCz = addDiskForm.cz;
            fieldCount = addDiskForm.count;
            fieldRadius = addDiskForm.outer;
            fieldCenterMass = addDiskForm.centerMass;
            addFieldsHost.getChildren().add(addDiskForm.root);
            return;
        }
        fieldCx = formField(vCx);
        fieldCy = formField(vCy);
        fieldCz = formField(vCz);
        fieldCount = formField(vCount);
        fieldRadius = formField(vRadius);
        fieldExtent = formField(vExtent);
        fieldMass = formField(vMass);
        fieldDensity = formField(vDensity);
        fieldCenterMass = formField(vCenterMass);
        addFieldsHost.getChildren().addAll(
            fieldRow("X", fieldCx, "Y", fieldCy, "Z", fieldCz),
            fieldRow("Count", fieldCount, "Radius", fieldRadius, "Extent", fieldExtent),
            fieldRow("Mass", fieldMass, "Dens", fieldDensity, "CtrMass", fieldCenterMass)
        );
    }

    private static Button actionButton(String text, Runnable onClick) {
        Button button = new Button(text);
        button.setOnAction(event -> onClick.run());
        return button;
    }

    private static TextField formField(String initial) {
        TextField field = new TextField(initial == null ? "" : initial);
        field.setPrefColumnCount(7);
        HBox.setHgrow(field, Priority.ALWAYS);
        return field;
    }

    private HBox fieldRow(String l1, TextField f1, String l2, TextField f2, String l3, TextField f3) {
        HBox row = new HBox(8, labeled(l1, f1), labeled(l2, f2), labeled(l3, f3));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox labeled(String name, TextField field) {
        Label label = new Label(name);
        label.setMinWidth(Region.USE_PREF_SIZE);
        HBox box = new HBox(4, label, field);
        box.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(box, Priority.ALWAYS);
        HBox.setHgrow(field, Priority.ALWAYS);
        field.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private void refreshScene() {
        sceneItems.setAll(setup.getItems());
        if (sceneHeading != null) {
            sceneHeading.setText("Scene (~" + setup.totalBodies() + " bodies)");
        }
        if (statusLabel != null) {
            statusLabel.setText(statusMessage());
        }
        if (sceneList != null && editingIndex >= 0 && editingIndex < sceneItems.size()) {
            sceneList.getSelectionModel().select(editingIndex);
        }
    }

    private final class SceneCell extends ListCell<SceneItem> {
        private final Label label = new Label();
        private final Button remove = new Button("x");
        private final HBox row = new HBox(8, label, remove);
        private final ContextMenu menu = new ContextMenu();
        private final MenuItem duplicateItem = new MenuItem("Duplicate");

        private SceneCell() {
            label.getStyleClass().add("scene-item");
            label.setWrapText(true);
            HBox.setHgrow(label, Priority.ALWAYS);
            remove.setMinWidth(Region.USE_PREF_SIZE);
            row.setAlignment(Pos.TOP_LEFT);
            remove.setOnMouseClicked(event -> event.consume());
            remove.setOnAction(event -> {
                int index = getIndex();
                if (index < 0 || index >= setup.getItems().size()) {
                    return;
                }
                snapshotFields();
                setup.remove(index);
                if (index == editingIndex) {
                    hideDiskEditor();
                } else if (editingIndex > index) {
                    editingIndex--;
                }
                refreshScene();
            });
            setOnMouseClicked(event -> {
                if (event.getButton() != MouseButton.PRIMARY || event.getTarget() instanceof Button) {
                    return;
                }
                SceneItem item = getItem();
                if (item == null) {
                    return;
                }
                if (item.type == ObjectType.DISK) {
                    if (event.getClickCount() == 1) {
                        showDiskEditor(getIndex());
                    }
                    return;
                }
                if (event.getClickCount() == 2) {
                    editSceneItem(getIndex());
                }
            });
            menu.getItems().add(duplicateItem);
            menu.setOnShowing(event -> {
                int index = getIndex();
                duplicateItem.setOnAction(e -> duplicateSceneItem(index));
            });
        }

        @Override
        protected void updateItem(SceneItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setContextMenu(null);
                return;
            }
            label.setText(item.settingsText());
            setGraphic(row);
            setContextMenu(menu);
        }
    }

    private void duplicateSceneItem(int index) {
        if (index < 0 || index >= setup.getItems().size()) {
            return;
        }
        snapshotFields();
        setup.duplicate(index);
        if (editingIndex >= 0 && index < editingIndex) {
            editingIndex++;
        }
        refreshScene();
    }

    private void showDiskEditor(int index) {
        if (index < 0 || index >= setup.getItems().size()) {
            return;
        }
        SceneItem item = setup.getItems().get(index);
        if (item.type != ObjectType.DISK) {
            return;
        }
        editingIndex = index;
        if (editDiskForm == null) {
            editDiskForm = new DiskForm();
            editHeading = section("Selected disk");
            diskEditorHost.getChildren().setAll(
                editHeading,
                editDiskForm.root,
                new HBox(8, actionButton("Save", this::saveDiskEdit), actionButton("Discard", this::discardDiskEdit))
            );
        }
        editHeading.setText(item.label);
        editDiskForm.load(item);
        diskEditorHost.setVisible(true);
        diskEditorHost.setManaged(true);
        if (sceneList != null) {
            sceneList.getSelectionModel().select(index);
        }
    }

    private void saveDiskEdit() {
        if (editDiskForm == null || editingIndex < 0 || editingIndex >= setup.getItems().size()) {
            return;
        }
        SceneItem current = setup.getItems().get(editingIndex);
        SceneItem updated = editDiskForm.apply(current);
        if (updated == null) {
            return;
        }
        setup.replace(editingIndex, updated);
        refreshScene();
        editHeading.setText(updated.label);
        editDiskForm.load(setup.getItems().get(editingIndex));
    }

    private void discardDiskEdit() {
        if (editDiskForm == null || editingIndex < 0 || editingIndex >= setup.getItems().size()) {
            hideDiskEditor();
            return;
        }
        editDiskForm.load(setup.getItems().get(editingIndex));
    }

    private void hideDiskEditor() {
        editingIndex = -1;
        diskEditorHost.setVisible(false);
        diskEditorHost.setManaged(false);
    }

    private void editSceneItem(int index) {
        if (index < 0 || index >= setup.getItems().size() || stage == null) {
            return;
        }
        SceneItem edited = SceneItemEditor.edit(stage, setup.getItems().get(index), setup.getUnitSet());
        if (edited == null) {
            return;
        }
        setup.replace(index, edited);
        refreshScene();
    }

    private void randomizeLayout() {
        snapshotFields();
        if (!setup.randomizeDisks()) {
            lastNote = "No galaxies to randomize.";
        } else {
            lastNote = "Randomized galaxy positions, sizes, and orientations.";
        }
        refreshScene();
        if (editDiskForm != null && editingIndex >= 0 && editingIndex < setup.getItems().size()
                && setup.getItems().get(editingIndex).type == ObjectType.DISK) {
            editHeading.setText(setup.getItems().get(editingIndex).label);
            editDiskForm.load(setup.getItems().get(editingIndex));
        }
    }

    private String statusMessage() {
        int n = setup.totalBodies();
        String ready;
        if (n <= 0) {
            ready = "Add a preset or object, then LAUNCH.";
        } else {
            SimulationSetup.SuggestedSettings s = setup.suggestSettings();
            String base = "Ready ~" + n + " | bounds ~" + (int) setup.suggestedSquareBounds()
                + " | dt~" + formatSuggest(s.dt) + " theta=" + s.theta + " cameraScale=" + s.cameraScale;
            if (n > 2_000_000) {
                ready = "WARN ~" + n + " bodies (heavy GPU) | " + base;
            } else {
                ready = base + " | paused; F1 runs";
            }
        }
        if (lastNote.isEmpty()) {
            return ready;
        }
        return lastNote + " | " + ready;
    }

    private static String formatSuggest(float v) {
        if (v >= 1e6f || (v > 0f && v < 1e-2f)) {
            return String.format("%.2g", v);
        }
        if (v >= 100f) {
            return String.format("%.0f", v);
        }
        return String.format("%.2f", v);
    }

    private void loadBsp() {
        snapshotFields();
        String chosen;
        try {
            chosen = chooseBspPath();
        } catch (HeadlessException e) {
            lastNote = "BSP upload needs a desktop file dialog.";
            refreshScene();
            return;
        }
        if (chosen == null) {
            return;
        }
        try {
            SpiceKernel.LoadResult loaded = SpiceKernel.load(Path.of(chosen));
            System.out.println(loaded.summary);
            if (loaded.planets.isEmpty()) {
                lastNote = loaded.summary;
            } else {
                setup.addSpiceBodies(loaded.planets, Path.of(chosen).getFileName().toString());
                lastNote = loaded.summary;
            }
        } catch (Exception e) {
            e.printStackTrace();
            lastNote = e.getMessage() == null ? "Could not read that BSP." : e.getMessage();
        }
        refreshScene();
    }

    /** Returns null when the user cancels. */
    private String chooseBspPath() {
        if (GraphicsEnvironment.isHeadless()) {
            throw new HeadlessException("BSP upload needs a desktop file dialog.");
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("SPICE kernel (.bsp)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SPICE kernel (*.bsp)", "*.bsp"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return null;
        }
        return file.getAbsolutePath();
    }

    private void addCurrentObject() {
        snapshotFields();
        lastNote = "";
        if (addType == ObjectType.DISK && addDiskForm != null) {
            SceneItem disk = addDiskForm.create("Disk");
            if (disk == null) {
                return;
            }
            setup.addItem(disk);
            refreshScene();
            return;
        }
        float cx = parseFloat(vCx, 0);
        float cy = parseFloat(vCy, 0);
        float cz = parseFloat(vCz, 0);
        switch (addType) {
            case BOX -> setup.addBox(
                parseInt(vCount, 30_000), cx, cy, cz,
                parseFloat(vExtent, 500), parseFloat(vMass, 1), parseFloat(vDensity, 1)
            );
            case SINGLE -> setup.addSingle(cx, cy, cz, parseFloat(vMass, 1000), parseFloat(vDensity, 1));
            case SOLAR_SYSTEM -> setup.addSolarSystem();
            case SPICE_KERNEL -> { }
        }
        refreshScene();
    }

    private void launch() {
        if (setup.getItems().isEmpty()) {
            if (statusLabel != null) {
                statusLabel.setText("Scene empty — add something first.");
            }
            return;
        }
        LaunchMemory previous = launchMemory;
        rememberLaunch();
        try {
            result = setup.toLaunchConfig();
            System.out.println("Launching with " + result.bodyCount + " bodies, bounds=" + result.squareBounds
                + ", " + result.suggestedSettings);
            finish(false);
        } catch (Exception e) {
            launchMemory = previous;
            e.printStackTrace();
            if (statusLabel != null) {
                statusLabel.setText("Launch failed: " + e.getMessage());
            }
        }
    }

    private static float parseFloat(String text, float fallback) {
        try {
            return Float.parseFloat(text.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim().replace("_", ""));
        } catch (Exception e) {
            return fallback;
        }
    }
}
