package com.grumbo;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;
import org.joml.*;

import java.nio.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class OpenGLWindow {

    // The window handle
    private long window;
    
    // Mouse variables
    private double lastX = Settings.getInstance().getWidth() / 2.0;
    private double lastY = Settings.getInstance().getHeight() / 2.0;
    private boolean firstMouse = true;
    private boolean mouseCaptured = true;
    
    // Mouse wheel for Z movement
    private float scrollOffset = 0.0f;
    
    // Reference to gravity simulator
    private GravitySimulator simulator;
    private GravityUI ui;
    private Map<String, Slider> sliders = new HashMap<>();
    private Map<String, UITextField> textFields = new HashMap<>();
    private Map<String, UIButton> incButtons = new HashMap<>();
    private Map<String, UIButton> decButtons = new HashMap<>();
    private Slider activeSlider = null;
    private void bumpValue(Settings settings, Property<?> prop, String name, double factor) {
        String type = prop.getTypeName();
        try {
            if ("int".equals(type)) {
                int current = (Integer) settings.getValue(name);
                settings.setValue(name, (int) java.lang.Math.round(current * factor));
            } else if ("double".equals(type)) {
                double current = (Double) settings.getValue(name);
                settings.setValue(name, current * factor);
            } else if ("float".equals(type)) {
                float current = (Float) settings.getValue(name);
                settings.setValue(name, (float) (current * factor));
            }
            settings.saveSettings();
        } catch (Exception ignore) {}
    }
    private void syncTextField(String name, Settings settings) {
        UITextField tf = textFields.get(name);
        if (tf != null && !tf.isFocused()) {
            Object v = settings.getValue(name);
            tf.setTextFromValue(v);
        }
    }
    private double mouseX = 0.0;
    private double mouseY = 0.0;
    
    // Settings panel state
    private boolean debug = false;
    
    // Bitmap font for UI text
    private BitmapFont font;

    private enum State {
        LOADING,
        RUNNING,
        PAUSED,
    }
    private State state = State.RUNNING;


    public OpenGLWindow() {
        this(null);
    }

    public OpenGLWindow(GravitySimulator simulator) {
        this.simulator = simulator;
        this.ui = new GravityUI(simulator);
    }

    public void run() {
        init();
        loop();

        // Cleanup font resources
        if (font != null) {
            font.cleanup();
        }
        
        // Free the window callbacks and destroy the window
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        // Terminate GLFW and free the error callback
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void init() {
        // Setup an error callback
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW
        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        // Configure GLFW
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        // Restore default settings on startup
        Settings.getInstance().restoreDefaults();

        // Create the window
        window = glfwCreateWindow(Settings.getInstance().getWidth(), Settings.getInstance().getHeight(), "Gravity Simulator 3D", NULL, NULL);
        if (window == NULL)
            throw new RuntimeException("Failed to create the GLFW window");

        // Setup callbacks
        setupCallbacks();

        // Center the window
        centerWindow();

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);
        // Enable v-sync
        glfwSwapInterval(1);

        // Set cursor to disabled (hidden and locked to window)
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        // Make the window visible
        glfwShowWindow(window);
    }
    
    private void setupCallbacks() {
        // Key callback
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_GRAVE_ACCENT && action == GLFW_PRESS) {
                debug = !debug;
            }
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                mouseCaptured = !mouseCaptured;
                glfwSetInputMode(window, GLFW_CURSOR, mouseCaptured ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
                if (mouseCaptured) {
                    firstMouse = true;
                }
            }
            // Route keys to focused textfields when not captured
            if (!mouseCaptured && debug) {
                for (UITextField tf : textFields.values()) {
                    if (tf.isFocused() && tf.handleKey(key, action, mods)) {
                        return;
                    }
                }
            }
            
            ui.updateKeys(key, action);
        });
        
        // Mouse callback for FPS-style camera rotation (always active)
        glfwSetCursorPosCallback(window, (window, xpos, ypos) -> {
            // Track mouse for UI hit testing
            mouseX = xpos;
            mouseY = ypos;
            if (firstMouse) {
                lastX = xpos;
                lastY = ypos;
                firstMouse = false;
            }

            double xoffset = xpos - lastX;
            double yoffset = lastY - ypos; // Reversed since y-coordinates go from bottom to top
            lastX = xpos;
            lastY = ypos;
            if (mouseCaptured) {
                xoffset *= Settings.getInstance().getMouseRotationSensitivity();
                yoffset *= Settings.getInstance().getMouseRotationSensitivity();

                Settings.getInstance().setYaw((float)(Settings.getInstance().getYaw() + xoffset));
                Settings.getInstance().setPitch((float)(Settings.getInstance().getPitch() + yoffset));

                // Constrain pitch to prevent camera flipping
                if (Settings.getInstance().getPitch() > 89.0f)
                    Settings.getInstance().setPitch(89.0f);
                if (Settings.getInstance().getPitch() < -89.0f)
                    Settings.getInstance().setPitch(-89.0f);

                updateCameraDirection();
            } else if (debug && activeSlider != null) {
                activeSlider.handleMouseDrag(xpos, ypos);
            }
        });

        // Mouse button callback for UI
        glfwSetMouseButtonCallback(window, (window, button, action, mods) -> {
            if (!mouseCaptured && debug && button == GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW_PRESS) {
                    activeSlider = null;
                    for (Slider slider : sliders.values()) {
                        if (slider.handleMouseDown(mouseX, mouseY)) { activeSlider = slider; break; }
                    }
                    if (activeSlider == null) {
                        // Text fields
                        for (UITextField tf : textFields.values()) {
                            if (tf.handleMouseDown(mouseX, mouseY)) break;
                        }
                        // Buttons
                        for (UIButton b : incButtons.values()) { if (b.handleMouseDown(mouseX, mouseY)) break; }
                        for (UIButton b : decButtons.values()) { if (b.handleMouseDown(mouseX, mouseY)) break; }
                    }
                } else if (action == GLFW_RELEASE) {
                    if (activeSlider != null) {
                        activeSlider.handleMouseUp();
                        activeSlider = null;
                    }
                }
            }
        });

        // Text input callback
        glfwSetCharCallback(window, (window, codepoint) -> {
            if (!mouseCaptured && debug) {
                for (UITextField tf : textFields.values()) {
                    if (tf.isFocused() && tf.handleChar(codepoint)) {
                        break;
                    }
                }
            }
        });
        
        // Mouse wheel callback for Z movement
        glfwSetScrollCallback(window, (window, xoffset, yoffset) -> {
            scrollOffset += yoffset * 0.1f;
            // Move in the direction the camera is facing
            Vector3f moveDirection = new Vector3f(Settings.getInstance().getCameraFront()).mul((float)(yoffset * Settings.getInstance().getMouseWheelSensitivity()));
            Settings.getInstance().setCameraPos(Settings.getInstance().getCameraPos().add(moveDirection));
        });
        
        // Window resize callback
        glfwSetFramebufferSizeCallback(window, (window, width, height) -> {
            glViewport(0, 0, Settings.getInstance().getWidth(), Settings.getInstance().getHeight());
        });
        
        // Window close callback
        glfwSetWindowCloseCallback(window, (window) -> {
            glfwSetWindowShouldClose(window, true);
        });
    }
    
    private void updateCameraDirection() {
        Vector3f direction = new Vector3f();
        direction.x = (float)(java.lang.Math.cos(java.lang.Math.toRadians(Settings.getInstance().getYaw())) * java.lang.Math.cos(java.lang.Math.toRadians(Settings.getInstance().getPitch())));
        direction.y = (float)java.lang.Math.sin(java.lang.Math.toRadians(Settings.getInstance().getPitch()));
        direction.z = (float)(java.lang.Math.sin(java.lang.Math.toRadians(Settings.getInstance().getYaw())) * java.lang.Math.cos(java.lang.Math.toRadians(Settings.getInstance().getPitch())));
        Settings.getInstance().setCameraFront(direction.normalize());
    }
    
    private void centerWindow() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);

            glfwGetWindowSize(window, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            glfwSetWindowPos(
                window,
                (vidmode.width() - pWidth.get(0)) / 2,
                (vidmode.height() - pHeight.get(0)) / 2
            );
        }
    }

    private void loop() {
        GL.createCapabilities();
        
        // Initialize bitmap font AFTER OpenGL context is created
        font = new BitmapFont();
        if (!font.isLoaded()) {
            System.out.println("Warning: Bitmap font not loaded, using fallback text rendering");
        }
        
        System.out.println("OpenGL Version: " + glGetString(GL_VERSION));
        System.out.println("OpenGL Vendor: " + glGetString(GL_VENDOR));
        System.out.println("OpenGL Renderer: " + glGetString(GL_RENDERER));
        System.out.println("=== CONTROLS ===");
        System.out.println("Mouse: Look around (FPS-style)");
        System.out.println("WASD: Move camera (relative to view direction)");
        System.out.println("QE: Move up/down");
        System.out.println("Mouse wheel: Zoom in/out");
        System.out.println("ESC: Toggle mouse cursor (press to release/capture mouse)");
        System.out.println("P: Toggle performance stats");
        System.out.println("I: Print detailed performance info");
        System.out.println("B: Toggle chunk borders");
        System.out.println("T: Toggle planet trails");
        System.out.println("F: Toggle follow mode");
        System.out.println("R: Reset performance counters");
        System.out.println("Up/Down arrows: Change chunk size");
        System.out.println("[/]: Change simulation speed");
        System.out.println("+/-: Zoom in/out");
        System.out.println("==================");
        System.out.println("Initial camera position: " + Settings.getInstance().getCameraPos().x + ", " + Settings.getInstance().getCameraPos().y + ", " + Settings.getInstance().getCameraPos().z);
        System.out.println("Initial zoom: " + Settings.getInstance().getZoom());
        System.out.println("Initial shift: " + java.util.Arrays.toString(Settings.getInstance().getShift()));
        System.out.println("Camera front: " + Settings.getInstance().getCameraFront().x + ", " + Settings.getInstance().getCameraFront().y + ", " + Settings.getInstance().getCameraFront().z);

        // Enable depth testing for 3D
        glEnable(GL_DEPTH_TEST);
        
        // Set the clear color to dark gray
        glClearColor(0.2f, 0.2f, 0.2f, 1.0f);

        System.out.println("Starting render loop...");
        
        while (!glfwWindowShouldClose(window)) {

            if (state == State.RUNNING) {
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                // Process WASD movement
                processMovement();

                // Set up 3D projection matrix
                setupProjection();
                
                // Set up view matrix (camera)
                setupCamera();

                drawPlanets();
                
                // Draw crosshair last (on top)
                drawCrosshair();
                
                // Draw settings panel if visible (on top of everything)

            }

            if (debug) {
                drawSettingsPanel();

            }

            glfwSwapBuffers(window);
            glfwPollEvents();
            
        }
        
        System.out.println("Render loop ended");
    }

    
    private void processMovement() {
        // Handle relative camera movement based on key states
        Vector3f moveDirection = new Vector3f();
        float moveSpeed = Settings.getInstance().getWASDSensitivity();
        
        // Calculate right vector (perpendicular to forward and up)
        Vector3f right = new Vector3f(Settings.getInstance().getCameraFront()).cross(Settings.getInstance().getCameraUp()).normalize();
        
        // Check key states and calculate relative movement
        for (GravityUI.KeyEvent event : ui.keyEvents) {
            if (event.pressed) {
                switch (event.key) {
                    case GLFW.GLFW_KEY_W: // Forward in camera direction
                        moveDirection.add(new Vector3f(Settings.getInstance().getCameraFront()).mul(moveSpeed));
                        break;
                    case GLFW.GLFW_KEY_S: // Backward from camera direction
                        moveDirection.sub(new Vector3f(Settings.getInstance().getCameraFront()).mul(moveSpeed));
                        break;
                    case GLFW.GLFW_KEY_A: // Left relative to camera
                        moveDirection.sub(new Vector3f(right).mul(moveSpeed));
                        break;
                    case GLFW.GLFW_KEY_D: // Right relative to camera
                        moveDirection.add(new Vector3f(right).mul(moveSpeed));
                        break;
                    case GLFW.GLFW_KEY_Q: // Up in world space
                        moveDirection.y += moveSpeed;
                        break;
                    case GLFW.GLFW_KEY_E: // Down in world space
                        moveDirection.y -= moveSpeed;
                        break;
                }
            }
        }
        
        // Apply movement to camera position
        if (moveDirection.length() > 0) {
            Settings.getInstance().setCameraPos(Settings.getInstance().getCameraPos().add(moveDirection));
            
            // Update Settings to reflect new camera position
            Settings.getInstance().setShift(new double[] {Settings.getInstance().getCameraPos().x, Settings.getInstance().getCameraPos().y, Settings.getInstance().getCameraPos().z});
        }
        
        // Run other UI key functions (non-movement controls)
        for (GravityUI.KeyEvent event : ui.keyEvents) {
            if (event.pressed) {
                event.action.run();
            }
        }
    }
    
    private void setupProjection() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        
        float fov = Settings.getInstance().getFov();
        float aspect = (float) Settings.getInstance().getWidth() / (float) Settings.getInstance().getHeight();
        float near = Settings.getInstance().getNearPlane();
        float far = Settings.getInstance().getFarPlane();
        
        // Simple perspective matrix
        float fH = (float) java.lang.Math.tan(java.lang.Math.toRadians(fov) / 2.0) * near;
        float fW = fH * aspect;
        
        glFrustum(-fW, fW, -fH, fH, near, far);
    }
    
    private void setupCamera() {
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        
        // Get camera position from Settings
        Vector3f eye = new Vector3f(Settings.getInstance().getCameraPos());
        Vector3f center;
        Vector3f up = new Vector3f(Settings.getInstance().getCameraUp());
        
        // Handle follow mode
        if (Settings.getInstance().isFollow() && simulator != null) {
            // Get reference point from simulator
            int[] reference = simulator.getReference(true);
            center = new Vector3f(reference[0], reference[1], reference[2]);
        } else {
            // Look in the direction the camera is facing
            center = new Vector3f(eye).add(Settings.getInstance().getCameraFront());
        }
        
        // Manual lookAt implementation
        Vector3f f = new Vector3f(center).sub(eye).normalize();
        Vector3f s = new Vector3f(f).cross(up).normalize();
        Vector3f u = new Vector3f(s).cross(f);
        
        float[] m = new float[16];
        m[0] = s.x; m[4] = s.y; m[8] = s.z; m[12] = 0;
        m[1] = u.x; m[5] = u.y; m[9] = u.z; m[13] = 0;
        m[2] = -f.x; m[6] = -f.y; m[10] = -f.z; m[14] = 0;
        m[3] = 0; m[7] = 0; m[11] = 0; m[15] = 1;
        
        glMultMatrixf(m);
        glTranslatef(-eye.x, -eye.y, -eye.z);
    }
    
    private void drawPlanets() {
        
        // Get the render buffer read lock for thread-safe access
        ReentrantReadWriteLock.ReadLock readLock = simulator.getRenderBufferReadLock();
        readLock.lock();
        try {
            // Get thread-safe snapshot of chunks and planets from render buffer
            ArrayList<Chunk> chunks = simulator.getRenderBuffer().getChunks(); // Already returns a copy
            for (Chunk chunk : chunks) {
                // Create a snapshot of planets to avoid concurrent modification
                ArrayList<Planet> planets;
                synchronized (chunk.planets) {
                    planets = new ArrayList<>(chunk.planets);
                }
                
                for (Planet planet : planets) {
                    // Read planet properties atomically
                    float x, y, z, radius;
                    int red, green, blue;
                    
                    synchronized (planet) {
                        x = (float) planet.x;
                        y = (float) planet.y;
                        z = (float) planet.z;
                        radius = (float) planet.getRadius();
                        red = planet.getColor().getRed();
                        green = planet.getColor().getGreen();
                        blue = planet.getColor().getBlue();
                    }
                    
                    glPushMatrix();
                    
                    // Set planet color
                    glColor3f(red / 255.0f, green / 255.0f, blue / 255.0f);
                    
                    
                    // Draw sphere at planet position
                    drawSphere(x, y, z, radius, Settings.getInstance().getSphereSegments());
                    
                    glPopMatrix();
                }
            }

        } finally {
            readLock.unlock();
        }
    }
    
    private void drawSphere(float x, float y, float z, float radius, int segments) {
        glTranslatef(x, y, z);
        
        // Simple sphere using quad strips
        for (int i = 0; i < segments; i++) {
            float lat0 = (float) (java.lang.Math.PI * (-0.5 + (double) i / segments));
            float lat1 = (float) (java.lang.Math.PI * (-0.5 + (double) (i + 1) / segments));
            
            glBegin(GL_QUAD_STRIP);
            for (int j = 0; j <= segments; j++) {
                float lng = (float) (2 * java.lang.Math.PI * j / segments);
                
                float x0 = (float) (java.lang.Math.cos(lat0) * java.lang.Math.cos(lng));
                float y0 = (float) (java.lang.Math.sin(lat0));
                float z0 = (float) (java.lang.Math.cos(lat0) * java.lang.Math.sin(lng));
                
                float x1 = (float) (java.lang.Math.cos(lat1) * java.lang.Math.cos(lng));
                float y1 = (float) (java.lang.Math.sin(lat1));
                float z1 = (float) (java.lang.Math.cos(lat1) * java.lang.Math.sin(lng));
                
                glVertex3f(x0 * radius, y0 * radius, z0 * radius);
                glVertex3f(x1 * radius, y1 * radius, z1 * radius);
            }
            glEnd();
        }
    }
    
    private void drawCrosshair() {
        // Save current matrices
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        
        // Set up 2D orthographic projection for crosshair
        glOrtho(0, Settings.getInstance().getWidth(), Settings.getInstance().getHeight(), 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        // Disable depth testing for crosshair
        glDisable(GL_DEPTH_TEST);
        
        // Set crosshair color (white)
        glColor3f(1.0f, 1.0f, 1.0f);
        glLineWidth(2.0f);
        
        // Calculate center of screen
        float centerX = Settings.getInstance().getWidth() / 2.0f;
        float centerY = Settings.getInstance().getHeight() / 2.0f;
        float crosshairSize = 10.0f;
        
        // Draw crosshair lines
        glBegin(GL_LINES);
        // Horizontal line
        glVertex2f(centerX - crosshairSize, centerY);
        glVertex2f(centerX + crosshairSize, centerY);
        // Vertical line
        glVertex2f(centerX, centerY - crosshairSize);
        glVertex2f(centerX, centerY + crosshairSize);
        glEnd();
        
        // Re-enable depth testing
        glEnable(GL_DEPTH_TEST);
        
        // Restore matrices
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }
    
    private void drawSettingsPanel() {
        
        // Switch to 2D rendering mode
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, Settings.getInstance().getWidth(), Settings.getInstance().getHeight(), 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        // Disable depth testing for UI
        glDisable(GL_DEPTH_TEST);
        

        
        // For now, just display text indicating this is the settings panel
        // Later we'll add actual property display and controls
        glColor3f(1.0f, 1.0f, 1.0f);
        
        // Display properties
        float yPos = 30.0f;
        float rowHeight = (float) java.lang.Math.max(18.0f, font != null ? font.getCharHeight() * 1.3f : 18.0f);
        
        // Draw title
        drawText("=== SETTINGS ===", 20.0f, yPos, font.getFontSize());
        yPos += rowHeight * 2;
        
        // Build editable controls (once)
        // - Text input for all editable props (value only shown in text field)
        // - Sliders for numeric props with real, finite min/max
        // - +/- buttons for numeric text inputs without full range (factor 1.1)
        Settings settings = Settings.getInstance();
        if (sliders.isEmpty()) {
            for (String name : settings.getPropertyNames()) {
                Property<?> prop = settings.getProperty(name);
                if (prop == null || !prop.isEditable()) continue;
                // Always create a text field for the value display
                float tfX = 0f, tfY = 0f, tfW = 250.0f, tfH = (float) java.lang.Math.max(16.0f, font.getCharHeight());
                UITextField tf = new UITextField(tfX, tfY, tfW, tfH, String.valueOf(prop.getValue()));
                tf.setOnCommit(() -> {
                    String type = prop.getTypeName();
                    String txt = tf.getText();
                    try {
                        if ("int".equals(type)) settings.setValue(name, Integer.parseInt(txt.trim()));
                        else if ("double".equals(type)) settings.setValue(name, Double.parseDouble(txt.trim()));
                        else if ("float".equals(type)) settings.setValue(name, Float.parseFloat(txt.trim()));
                        else if ("string".equals(type)) settings.setValue(name, txt);
                        settings.saveSettings();
                        // If a slider exists for this property, sync it to the typed value
                        if (sliders.containsKey(name) && (prop.isNumeric())) {
                            double val = ((Number) settings.getValue(name)).doubleValue();
                            sliders.get(name).setValue(val);
                        }
                    } catch (Exception ignore) {}
                });
                textFields.put(name, tf);

                // Create slider for ranged numerics
                if (prop.isNumeric() && prop.hasRange() &&
                    java.lang.Double.isFinite(prop.getMinAsDouble()) && java.lang.Double.isFinite(prop.getMaxAsDouble()) &&
                    java.lang.Math.abs(prop.getMinAsDouble()) < 1e300 && java.lang.Math.abs(prop.getMaxAsDouble()) < 1e300) {
                    double current = ((Number) prop.getValue()).doubleValue();
                    float sx = 0f, sy = 0f;
                    float sw = 300.0f;
                    float sh = (float) java.lang.Math.max(16.0f, font.getCharHeight());
                    double min = prop.getMinAsDouble();
                    double max = prop.getMaxAsDouble();
                    Slider slider = new Slider(sx, sy, sw, sh, min, max, current, (val) -> {
                        // On change, set value and save
                        String type = prop.getTypeName();
                        if ("int".equals(type)) settings.setValue(name, (int) java.lang.Math.round(val));
                        else if ("double".equals(type)) settings.setValue(name, val);
                        else if ("float".equals(type)) settings.setValue(name, (float)val.doubleValue());
                        settings.saveSettings();
                        // Sync text field with slider value
                        syncTextField(name, settings);
                    });
                    sliders.put(name, slider);
                } else if (prop.isNumeric()) {
                    // +/- buttons for non-ranged numeric
                    float btnH = tfH;
                    float btnW = 28.0f;
                    UIButton plus = new UIButton(0f, 0f, btnW, btnH, "+", () -> {
                        bumpValue(settings, prop, name, 1.1);
                        syncTextField(name, settings);
                    });
                    UIButton minus = new UIButton(0f, 0f, btnW, btnH, "-", () -> {
                        bumpValue(settings, prop, name, 1.0/1.1);
                        syncTextField(name, settings);
                    });
                    incButtons.put(name, plus);
                    decButtons.put(name, minus);
                }
            }
        }

        for (String name : settings.getPropertyNames()) {
            Property<?> prop = settings.getProperty(name);
            if (prop == null || !prop.isEditable()) continue;
            try {
                Object value = settings.getValue(name);
                String label = name + ":";
                float labelX = 20.0f;
                drawText(label, labelX, yPos, font.getFontSize());
                // Compute dynamic control positions based on label width
                float padding = 10.0f;
                float labelWidth = (font != null && font.isLoaded()) ? font.getTextWidth(label, font.getFontSize()) : 100.0f;
                float controlsX = labelX + labelWidth + padding;
                float baselineY = yPos - font.getCharHeight() * 0.5f;

                // Text field always present
                if (textFields.containsKey(name)) {
                    UITextField tf = textFields.get(name);
                    float tfW = 220.0f;
                    float tfH = (float) java.lang.Math.max(16.0f, font.getCharHeight());
                    tf.setPosition(controlsX, baselineY);
                    tf.setSize(tfW, tfH);
                    if (!tf.isFocused()) tf.setTextFromValue(value);
                    tf.draw(font);

                    // Slider for ranged numerics placed after text field
                    if (sliders.containsKey(name)) {
                        Slider slider = sliders.get(name);
                        float sx = controlsX + tfW + 8.0f;
                        slider.setPosition(sx, baselineY);
                        slider.setSize(220.0f, (float) java.lang.Math.max(16.0f, font.getCharHeight()));
                        slider.setValue(((Number) value).doubleValue());
                        slider.draw();
                    }

                    // +/- buttons for non-ranged numerics placed after text field
                    if (incButtons.containsKey(name)) {
                        UIButton plus = incButtons.get(name);
                        UIButton minus = decButtons.get(name);
                        float btnW = 28.0f;
                        float btnH = (float) java.lang.Math.max(16.0f, font.getCharHeight());
                        plus.setPosition(controlsX + tfW + 8.0f, baselineY);
                        plus.setSize(btnW, btnH);
                        minus.setPosition(controlsX + tfW + 8.0f + btnW + 6.0f, baselineY);
                        minus.setSize(btnW, btnH);
                        plus.draw(font);
                        minus.draw(font);
                    }
                }
                yPos += rowHeight;
            } catch (Exception e) {
                // Skip if property missing
            }
        }
        
        // Instructions
        yPos += rowHeight;
        drawText("Press ESC to capture/release mouse. Click fields to edit. Enter to commit.", 20.0f, yPos, font.getFontSize());
        
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        
        // Restore matrices
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }
    
    private String formatValue(Object value) {
        if (value instanceof double[]) {
            double[] arr = (double[]) value;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(String.format("%.2f", arr[i]));
            }
            sb.append("]");
            return sb.toString();
        } else if (value instanceof Double) {
            return String.format("%.3f", (Double) value);
        } else if (value instanceof Float) {
            return String.format("%.3f", (Float) value);
        } else if (value instanceof java.awt.Color) {
            java.awt.Color color = (java.awt.Color) value;
            return String.format("RGB(%d,%d,%d)", color.getRed(), color.getGreen(), color.getBlue());
        } else {
            return value.toString();
        }
    }
    
    private void drawText(String text, float x, float y) {
        drawText(text, x, y, 1.0f);
    }
    
    
    private void drawText(String text, float x, float y, float scale) {
        if (font != null && font.isLoaded()) {
            font.drawText(text, x, y, 1.0f, scale);
        } else {
            // Fallback to simple line-based text
            drawSimpleText(text, x, y);
        }
    }
    
    private void drawSimpleText(String text, float x, float y) {
        // Fallback text rendering for when bitmap font fails to load
        float charWidth = 8.0f;
        float charHeight = 12.0f;
        
        glBegin(GL_LINES);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            float charX = x + i * charWidth;
            
            if (c != ' ') {
                // Draw a simple rectangle outline for each character
                glVertex2f(charX, y);
                glVertex2f(charX + charWidth - 2, y);
                glVertex2f(charX + charWidth - 2, y);
                glVertex2f(charX + charWidth - 2, y + charHeight);
                glVertex2f(charX + charWidth - 2, y + charHeight);
                glVertex2f(charX, y + charHeight);
                glVertex2f(charX, y + charHeight);
                glVertex2f(charX, y);
            }
        }
        glEnd();
    }
    
    public int[] getScreenLocation(double simX, double simY) {
		int[] followLocation = simulator.getReference(Settings.getInstance().isFollow());
		int screenWidth = Settings.getInstance().getWidth();
		int screenHeight = Settings.getInstance().getHeight();
		
		// Convert simulation coordinates to screen coordinates
		return new int[] {
			(int)(Settings.getInstance().getZoom() * (simX - followLocation[0]) + (screenWidth/2 + Settings.getInstance().getShift()[0])),
			(int)(Settings.getInstance().getZoom() * (simY - followLocation[1]) + (screenHeight/2 + Settings.getInstance().getShift()[1]))
		};
	}

	public int[] getSimulationLocation(double screenX, double screenY) {
		int[] followLocation = simulator.getReference(Settings.getInstance().isFollow());
		int screenWidth = Settings.getInstance().getWidth();
		int screenHeight = Settings.getInstance().getHeight();
		
		// Convert screen coordinates to simulation coordinates
		return new int[] {
			(int)((screenX - (screenWidth/2 + Settings.getInstance().getShift()[0])) / Settings.getInstance().getZoom() + followLocation[0]),
			(int)((screenY - (screenHeight/2 + Settings.getInstance().getShift()[1])) / Settings.getInstance().getZoom() + followLocation[1])
		};
	}

    public void drawTail(Planet p) {
        if (Settings.getInstance().isDrawTail()) {
            glColor3f(1.0f, 0.0f, 0.0f);
        long[] tailLast = p.tail[p.tailIndex];
        for (int i=1;i<Settings.getInstance().getTailLength();i++) {
            long[] tailNext = p.tail[(p.tailIndex+i)%Settings.getInstance().getTailLength()];
            int[] screenPosLast = getScreenLocation(tailLast[0], tailLast[1]);
            int[] screenPosNext = getScreenLocation(tailNext[0], tailNext[1]);
            glBegin(GL_LINES);
            glVertex3f(screenPosLast[0], screenPosLast[1], 0.0f);
            glVertex3f(screenPosNext[0], screenPosNext[1], 0.0f);
            glEnd();
            tailLast = tailNext;
            }
        }
    }
} 