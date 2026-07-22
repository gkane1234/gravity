package com.grumbo.ui;

import com.grumbo.simulation.SimulationSetup;
import com.grumbo.simulation.SimulationSetup.ObjectType;
import com.grumbo.simulation.SimulationSetup.Preset;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * First-launch setup screen: choose presets and place objects before GPU buffers are allocated.
 */
public final class SetupScreen {

    private static final int WIDTH = 1000;
    private static final int HEIGHT = 700;
    private static final float FONT_SIZE = 10f;
    private static final float MARGIN = 12f;
    private static final float COL_GAP = 24f;
    private static final float LEFT_W = 520f;

    private final SimulationSetup setup = new SimulationSetup();
    private BitmapFont font;

    private long window;
    private boolean finished;
    private boolean cancelled;
    private SimulationSetup.LaunchConfig result;

    private ObjectType addType = ObjectType.DISK;
    private final List<UIElement> elements = new ArrayList<>();
    private final List<UIButton> typeButtons = new ArrayList<>();

    // Persist field values across rebuilds
    private String vCx = "0", vCy = "0", vCz = "0";
    private String vCount = "50000", vRadius = "800", vMass = "1";
    private String vDensity = "1", vCenterMass = "50000", vExtent = "500";

    private UITextField fieldCx, fieldCy, fieldCz;
    private UITextField fieldCount, fieldRadius, fieldMass;
    private UITextField fieldDensity, fieldCenterMass, fieldExtent;
    private UIText statusText;

    private double mouseX;
    private double mouseY;
    /** Current GLFW window size (cursor space). UI is laid out in WIDTH×HEIGHT logical coords. */
    private int windowW = WIDTH;
    private int windowH = HEIGHT;

    private SetupScreen() {}

    public static SimulationSetup.LaunchConfig run() {
        return new SetupScreen().loop();
    }

    private SimulationSetup.LaunchConfig loop() {
        initWindow();
        setup.applyPreset(Preset.SMALL_GALAXY);
        rebuildUi();

        while (!finished && !glfwWindowShouldClose(window)) {
            glClearColor(0.08f, 0.09f, 0.12f, 1f);
            glClear(GL_COLOR_BUFFER_BIT);

            // Always draw in fixed logical coordinates; viewport stretches to the window.
            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            glOrtho(0, WIDTH, HEIGHT, 0, -1, 1);
            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();

            for (UIElement el : elements) {
                el.draw(font);
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }

        cleanupWindow();
        if (cancelled || result == null) {
            glfwTerminate();
            GLFWErrorCallback cb = glfwSetErrorCallback(null);
            if (cb != null) cb.free();
            return null;
        }
        return result;
    }

    /** Map cursor position from window pixels into the logical UI coordinate system. */
    private double[] uiMouse() {
        double sx = windowW > 0 ? (mouseX * WIDTH / windowW) : mouseX;
        double sy = windowH > 0 ? (mouseY * HEIGHT / windowH) : mouseY;
        return new double[] { sx, sy };
    }

    private void initWindow() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_COMPAT_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_FALSE);

        window = glfwCreateWindow(WIDTH, HEIGHT, "GravityChunk — Setup", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create setup window");
        }
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        glfwShowWindow(window);
        GL.createCapabilities();

        int[] fbw = new int[1], fbh = new int[1];
        glfwGetFramebufferSize(window, fbw, fbh);
        glViewport(0, 0, fbw[0], fbh[0]);

        int[] ww = new int[1], wh = new int[1];
        glfwGetWindowSize(window, ww, wh);
        windowW = Math.max(1, ww[0]);
        windowH = Math.max(1, wh[0]);

        font = new BitmapFont();
        font.setFontSize(FONT_SIZE);

        glfwSetCursorPosCallback(window, (w, x, y) -> {
            mouseX = x;
            mouseY = y;
        });
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (button != GLFW_MOUSE_BUTTON_LEFT) return;
            double[] m = uiMouse();
            if (action == GLFW_PRESS) {
                for (UIElement el : elements) {
                    if (el.handleMousePress(m[0], m[1])) break;
                }
            } else if (action == GLFW_RELEASE) {
                for (UIElement el : elements) {
                    el.handleMouseRelease();
                }
            }
        });
        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            for (UIElement el : elements) {
                if (el.handleKeyPress(key, action, mods)) return;
            }
            if (action == GLFW_PRESS && key == GLFW_KEY_ESCAPE) {
                cancelled = true;
                finished = true;
            }
        });
        glfwSetCharCallback(window, (w, codepoint) -> {
            for (UIElement el : elements) {
                if (el.handleCharPress(codepoint)) return;
            }
        });
        glfwSetWindowSizeCallback(window, (win, w, h) -> {
            windowW = Math.max(1, w);
            windowH = Math.max(1, h);
        });
        glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            glViewport(0, 0, w, h);
        });
    }

    private void cleanupWindow() {
        if (window != 0) {
            glfwFreeCallbacks(window);
            glfwDestroyWindow(window);
            window = 0;
        }
    }

    private void snapshotFields() {
        if (fieldCx != null) {
            vCx = fieldCx.getText();
            vCy = fieldCy.getText();
            vCz = fieldCz.getText();
            vCount = fieldCount.getText();
            vRadius = fieldRadius.getText();
            vMass = fieldMass.getText();
            vDensity = fieldDensity.getText();
            vCenterMass = fieldCenterMass.getText();
            vExtent = fieldExtent.getText();
        }
    }

    private void rebuildUi() {
        snapshotFields();
        elements.clear();
        typeButtons.clear();

        float leftX = MARGIN;
        float rightX = MARGIN + LEFT_W + COL_GAP;
        float y = MARGIN;

        elements.add(label("GravityChunk Setup", leftX, y));
        y += lineGap() + 4;

        // --- Presets (two short rows) ---
        elements.add(label("Presets", leftX, y));
        y += lineGap();
        UIRow presetRow1 = new UIRow();
        addPreset(presetRow1, Preset.EMPTY, "Empty");
        addPreset(presetRow1, Preset.SOLAR_SYSTEM, "Solar Sys");
        addPreset(presetRow1, Preset.SMALL_GALAXY, "Small Gal");
        placeRow(presetRow1, leftX, y);
        y += presetRow1.height + 4;
        UIRow presetRow2 = new UIRow();
        addPreset(presetRow2, Preset.MERGER_SCALED, "Merger sm");
        addPreset(presetRow2, Preset.MERGER_FULL, "Merger 3.8M");
        placeRow(presetRow2, leftX, y);
        y += presetRow2.height + 10;

        // --- Object type ---
        elements.add(label("Add object", leftX, y));
        y += lineGap();
        UIRow typeRow = new UIRow();
        addTypeButton(typeRow, "Disk", ObjectType.DISK);
        addTypeButton(typeRow, "Box", ObjectType.BOX);
        addTypeButton(typeRow, "Body", ObjectType.SINGLE);
        addTypeButton(typeRow, "Solar JSON", ObjectType.SOLAR_SYSTEM);
        placeRow(typeRow, leftX, y);
        y += typeRow.height + 8;

        fieldCx = field(vCx);
        fieldCy = field(vCy);
        fieldCz = field(vCz);
        fieldCount = field(vCount);
        fieldRadius = field(vRadius);
        fieldMass = field(vMass);
        fieldDensity = field(vDensity);
        fieldCenterMass = field(vCenterMass);
        fieldExtent = field(vExtent);

        // Compact field grid: 3 columns
        y = placeFieldTriple(leftX, y, "X", fieldCx, "Y", fieldCy, "Z", fieldCz);
        y = placeFieldTriple(leftX, y, "Count", fieldCount, "Radius", fieldRadius, "Extent", fieldExtent);
        y = placeFieldTriple(leftX, y, "Mass", fieldMass, "Dens", fieldDensity, "CtrMass", fieldCenterMass);
        y += 4;

        UIRow actionRow = new UIRow();
        actionRow.getElements().add(btn("Add", this::addCurrentObject));
        actionRow.getElements().add(btn("Clear", () -> {
            snapshotFields();
            setup.clear();
            rebuildUi();
        }));
        placeRow(actionRow, leftX, y);
        y += actionRow.height + 10;

        statusText = label(statusMessage(), leftX, y);
        elements.add(statusText);
        y += lineGap() + 8;

        UIRow launchRow = new UIRow();
        launchRow.getElements().add(btn("LAUNCH", this::launch));
        launchRow.getElements().add(btn("Quit app", () -> {
            cancelled = true;
            finished = true;
        }));
        placeRow(launchRow, leftX, y);

        // --- Right column: scene list + controls help ---
        float ry = MARGIN;
        elements.add(label("Scene (~" + setup.totalBodies() + " bodies)", rightX, ry));
        ry += lineGap() + 4;

        List<SimulationSetup.SceneItem> items = setup.getItems();
        int maxLines = 8;
        for (int i = 0; i < items.size() && i < maxLines; i++) {
            final int index = i;
            String text = truncate(items.get(i).toString(), 42);
            elements.add(label(text, rightX, ry));
            UIButton remove = btn("x", () -> {
                snapshotFields();
                setup.remove(index);
                rebuildUi();
            });
            remove.calculateSize(font);
            remove.setPosition(WIDTH - MARGIN - remove.width, ry - 1);
            elements.add(remove);
            ry += lineGap() + 2;
        }
        if (items.size() > maxLines) {
            elements.add(label("... +" + (items.size() - maxLines) + " more", rightX, ry));
            ry += lineGap() + 2;
        } else if (items.isEmpty()) {
            elements.add(label("(empty)", rightX, ry));
            ry += lineGap() + 2;
        }

        ry += 10;
        elements.add(label("Simulation controls", rightX, ry));
        ry += lineGap() + 2;
        for (String line : CONTROL_HELP) {
            elements.add(label(line, rightX, ry));
            ry += lineGap();
        }

        updateTypeSelection();
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
        "`  Settings pane",
        "F2  Tree regions  |  F3  Debug",
        "F4  Stats  |  F5  Crosshair",
        "F6  Record PNG frames",
    };

    private float placeFieldTriple(float x, float y, String l1, UITextField f1,
                                   String l2, UITextField f2, String l3, UITextField f3) {
        UIRow row = new UIRow();
        row.getElements().add(tinyLabel(l1));
        row.getElements().add(f1);
        row.getElements().add(tinyLabel(l2));
        row.getElements().add(f2);
        row.getElements().add(tinyLabel(l3));
        row.getElements().add(f3);
        placeRow(row, x, y);
        return y + row.height + 3;
    }

    private void addPreset(UIRow row, Preset preset, String shortLabel) {
        UIButton b = btn(shortLabel, () -> {
            snapshotFields();
            setup.applyPreset(preset);
            rebuildUi();
        });
        row.getElements().add(b);
    }

    private void addTypeButton(UIRow row, String label, ObjectType type) {
        UIButton b = btn(label, () -> {
            addType = type;
            updateTypeSelection();
        });
        typeButtons.add(b);
        row.getElements().add(b);
    }

    private void updateTypeSelection() {
        ObjectType[] types = {ObjectType.DISK, ObjectType.BOX, ObjectType.SINGLE, ObjectType.SOLAR_SYSTEM};
        for (int i = 0; i < typeButtons.size() && i < types.length; i++) {
            typeButtons.get(i).setSelected(addType == types[i]);
        }
    }

    private String statusMessage() {
        int n = setup.totalBodies();
        if (n <= 0) return "Add a preset or object, then LAUNCH.";
        SimulationSetup.SuggestedSettings s = setup.suggestSettings();
        String base = "Ready ~" + n + " | bounds ~" + (int) setup.suggestedSquareBounds()
            + " | dt~" + formatSuggest(s.dt) + " theta=" + s.theta;
        if (n > 2_000_000) return "WARN ~" + n + " bodies | " + base;
        return base + " | paused; F1 runs";
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

    private void addCurrentObject() {
        snapshotFields();
        float cx = parseFloat(vCx, 0);
        float cy = parseFloat(vCy, 0);
        float cz = parseFloat(vCz, 0);
        switch (addType) {
            case DISK -> setup.addDisk(
                parseInt(vCount, 50_000), cx, cy, cz,
                parseFloat(vRadius, 800), parseFloat(vMass, 1),
                parseFloat(vDensity, 1), parseFloat(vCenterMass, 50_000)
            );
            case BOX -> setup.addBox(
                parseInt(vCount, 30_000), cx, cy, cz,
                parseFloat(vExtent, 500), parseFloat(vMass, 1), parseFloat(vDensity, 1)
            );
            case SINGLE -> setup.addSingle(cx, cy, cz, parseFloat(vMass, 1000), parseFloat(vDensity, 1));
            case SOLAR_SYSTEM -> setup.addSolarSystem();
        }
        rebuildUi();
    }

    private void launch() {
        if (setup.getItems().isEmpty()) {
            if (statusText != null) statusText.setText("Scene empty — add something first.");
            return;
        }
        try {
            result = setup.toLaunchConfig();
            System.out.println("Launching with " + result.bodyCount + " bodies, bounds=" + result.squareBounds
                + ", " + result.suggestedSettings);
            finished = true;
        } catch (Exception e) {
            e.printStackTrace();
            if (statusText != null) statusText.setText("Launch failed: " + e.getMessage());
        }
    }

    private float lineGap() {
        return font.getCharHeight() + 4f;
    }

    private void placeRow(UIRow row, float x, float y) {
        row.calculateSize(font);
        row.setPosition(x, y);
        elements.add(row);
    }

    private UIButton btn(String text, Runnable onClick) {
        UIButton b = new UIButton(text, onClick);
        b.setOnRelease(() -> {});
        return b;
    }

    private UITextField field(String initial) {
        UITextField f = new UITextField(initial);
        f.setMinWidth(7); // characters, not pixels
        f.setNumericalRounding(3);
        f.calculateSize(font);
        return f;
    }

    private UIText tinyLabel(String text) {
        UIText t = new UIText(text);
        t.calculateSize(font);
        return t;
    }

    private UIText label(String text, float x, float y) {
        UIText t = new UIText(text);
        t.calculateSize(font);
        t.setPosition(x, y);
        return t;
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 2) + "..";
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
