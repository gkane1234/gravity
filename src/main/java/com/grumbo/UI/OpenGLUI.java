package com.grumbo.UI;

import org.lwjgl.glfw.GLFW;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43.*;
import org.joml.Vector3f;

import com.grumbo.simulation.*;
import java.util.ArrayList;

public class OpenGLUI {

    private double lastX = Settings.getInstance().getWidth() / 2.0;
    private double lastY = Settings.getInstance().getHeight() / 2.0;    
    private boolean showStats = true;

    private boolean firstMouse = true;
    private boolean mouseCaptured = true;
    private boolean shiftPressed = false;
    private boolean displaySettings = false;
    private boolean displayDebug = false;
    private BitmapFont font;

    private SettingsPane settingsPane;
    public boolean showCrosshair = false;

    private boolean recordCurrentBodies = false;

    private int lastBodies = 0;
    private int lastSteps = 0;
    private int lastDifference = 0;

    public class KeyEvent {
        public int key;
        private Runnable pressAction;
        private Runnable releaseAction;
        public boolean pressed; 
        public boolean repeatable;
        private boolean repeat;

        private static final long REPEAT_DELAY = 30; // 200ms
        private long lastRepeatTime = 0;

        public KeyEvent(int key, Runnable pressAction, Runnable releaseAction, boolean repeatable) {
            this.key = key;
            this.pressAction = pressAction;
            this.releaseAction = releaseAction;
            this.pressed = false;
            this.repeatable = repeatable;
            this.repeat = false;
        }

        public KeyEvent(int key, Runnable pressAction, boolean repeatable) {
            this(key, pressAction, null, repeatable);
        }

        public KeyEvent(int key, Runnable action) {
            this(key, action,null, false);
        }

        public void press() {
            pressed = true;
            repeat = false;
        }

        public void release() {
            pressed = false;
            repeat = false;
        }

        public void run() {
            if (pressed && !repeat) {
                pressAction.run();
                repeat = true;
            }
            else if (repeatable && repeat) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastRepeatTime >= REPEAT_DELAY) {
                    pressAction.run();
                    lastRepeatTime = currentTime;
                }
            }
            else if (releaseAction != null) {
                releaseAction.run();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof KeyEvent) {
                KeyEvent other = (KeyEvent)o;
                return key == other.key;
            }
            if (o instanceof Integer) {
                return key == (Integer)o;
            }
            return false;
        }
    }

    public ArrayList<KeyEvent> keyEvents = new ArrayList<>();
    private OpenGLWindow openGlWindow;

    public OpenGLUI(OpenGLWindow openGlWindow) {
        // Initialize bitmap font
        font = new BitmapFont();
        this.openGlWindow = openGlWindow;
        settingsPane = new SettingsPane();
        initKeyEvents();
        setupCallbacks();

    }

    public void initKeyEvents() {
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_EQUAL, () -> Settings.getInstance().changeZoom(Settings.getInstance().getZoom() * 1.1),true));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_MINUS, () -> Settings.getInstance().changeZoom(Settings.getInstance().getZoom() / 1.1),true));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_W, () -> {processWASDQEMovement(GLFW.GLFW_KEY_W);},true));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_A, () -> {processWASDQEMovement(GLFW.GLFW_KEY_A);},true)); 
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_S, () -> {processWASDQEMovement(GLFW.GLFW_KEY_S);},true)); 
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_D, () -> {processWASDQEMovement(GLFW.GLFW_KEY_D);},true)); 
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_Q, () -> {processWASDQEMovement(GLFW.GLFW_KEY_Q);},true)); 
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_E, () -> {processWASDQEMovement(GLFW.GLFW_KEY_E);},true)); 
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_P, () -> {}));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_I, () -> {processIJKLMovement(GLFW.GLFW_KEY_I);},true));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_J, () -> {processIJKLMovement(GLFW.GLFW_KEY_J);},true));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_K, () -> {processIJKLMovement(GLFW.GLFW_KEY_K);},true));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_L, () -> {processIJKLMovement(GLFW.GLFW_KEY_L);},true));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_F, () -> Settings.getInstance().toggleFollow()));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_R, () -> {}));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_LEFT_SHIFT, () -> {shiftPressed = true;}, () -> {shiftPressed = false;},true));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_RIGHT_SHIFT, () -> {shiftPressed = true;}, () -> {shiftPressed = false;},true));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_UP, () -> {Settings.getInstance().setDt((float)(Settings.getInstance().getDt() * 1.1));},true));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_DOWN, () -> {Settings.getInstance().setDt((float)(Settings.getInstance().getDt() / 1.1));},true));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_LEFT, () -> {Settings.getInstance().setTheta((float)(Settings.getInstance().getTheta() * 0.9));},true));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_RIGHT, () -> {Settings.getInstance().setTheta((float)(Settings.getInstance().getTheta() * 1.1));},true));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_B, () -> {}));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_T, () -> Settings.getInstance().setDrawTail(!Settings.getInstance().isDrawTail()),false));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_SPACE, () -> Settings.getInstance().toggleFollow()));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_LEFT_BRACKET, () -> Settings.getInstance().setSoftening((float)(Settings.getInstance().getSoftening() * 0.9)),true));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_RIGHT_BRACKET, () -> Settings.getInstance().setSoftening((float)(Settings.getInstance().getSoftening() * 1.1)),true));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_GRAVE_ACCENT, () -> {displaySettings = !displaySettings;},false));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, () -> {mouseCaptured = !mouseCaptured;
            glfwSetInputMode(openGlWindow.getWindow(), GLFW_CURSOR, mouseCaptured ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
            if (mouseCaptured) {
                firstMouse = true;
            }},false));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_F1, () -> {showStats = !showStats;},false));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_F2, () -> {
            if (openGlWindow.getState() == GPUSimulation.State.PAUSED) {
                openGlWindow.setState(GPUSimulation.State.RUNNING);
                System.out.println("Switched to continuous simulation mode");
            } else {
                openGlWindow.setState(GPUSimulation.State.PAUSED);
                System.out.println("Switched to frame advance mode - press ENTER to advance frames");
            }
        },false));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_F3, () -> {displayDebug = !displayDebug;},false));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_F4, () -> {recordCurrentBodies = !recordCurrentBodies;},false));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_F5, () -> {openGlWindow.gpuSimulation.toggleRegions();},false));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_F6, () -> {openGlWindow.gpuSimulation.toggleCrosshair();},false));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_F7, () -> {openGlWindow.gpuSimulation.toggleRecording();},false));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_ENTER, () -> {if (openGlWindow.getState() == GPUSimulation.State.PAUSED) openGlWindow.gpuSimulation.frameAdvance();},false));
    }

        /**
     * Check for OpenGL errors.
     */
    private void checkGLError(String operation) {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            System.err.println("OpenGL Error after " + operation + ": " + error);
        }
    }

    public void drawUI() {
        checkGLError("before runKeyFunctions");
        runKeyFunctions();

        if (openGlWindow.getState() == GPUSimulation.State.PAUSED) {
            drawFrameAdvanceIndicator();
            checkGLError("after drawFrameAdvanceIndicator");
        }

        // Draw FPS display
        if (showStats) {
            drawStats();
            checkGLError("after drawStats");
        }
        
        // Draw settings panel
        if (displaySettings) {
            settingsPane.draw(font);
            checkGLError("after drawSettingsPane");
        }

        // Draw profiling info
        if (displayDebug) {
            drawDebugInfo();
            checkGLError("after drawDebugInfo");
        }

        if (showCrosshair) {
            drawCrosshair();
            checkGLError("after drawCrosshair");
        }
        checkGLError("after drawUI");
    }

    public void updateKeys(int key, int action) {
        for (KeyEvent event : keyEvents) {
            if (event.key == key && action == GLFW.GLFW_PRESS) {
                event.press();
            }
            if (event.key == key && action == GLFW.GLFW_RELEASE) {
                event.release();
            }
        }
    }

    public void runKeyFunctions() {
        for (KeyEvent event : keyEvents) {
            event.run();
        }
    }

    public void changeCamera(float xoffset, float yoffset) {
        xoffset *= Settings.getInstance().getMouseRotationSensitivity();
        yoffset *= Settings.getInstance().getMouseRotationSensitivity();
        Settings.getInstance().setYaw((float)(Settings.getInstance().getYaw() + xoffset));
        Settings.getInstance().setPitch((float)(Settings.getInstance().getPitch() + yoffset));
        // Constrain pitch to prevent camera flipping
        if (Settings.getInstance().getPitch() > 89.0f)
            Settings.getInstance().setPitch(89.0f);
        if (Settings.getInstance().getPitch() < -89.0f)
            Settings.getInstance().setPitch(-89.0f);
        // Normalize yaw to keep trig inputs well-conditioned
        float yaw = Settings.getInstance().getYaw();
        yaw = ((yaw + 180.0f) % 360.0f + 360.0f) % 360.0f - 180.0f;
        Settings.getInstance().setYaw(yaw);

        updateCameraDirection();
    }

    public void setupCallbacks() {

        // Key callback
        glfwSetKeyCallback(openGlWindow.getWindow(), (window, key, scancode, action, mods) -> {
            // Route keys to focused textfields when not captured
            if (!mouseCaptured && displaySettings) {
                if (settingsPane.onKey(key, action, mods)) {
                    return;
                }
            }

            updateKeys(key, action);
        });
        
        // Mouse callback for FPS-style camera rotation (always active)
        glfwSetCursorPosCallback(openGlWindow.getWindow(), (window, xpos, ypos) -> {
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
                changeCamera((float)xoffset, (float)yoffset);
            } else if (displaySettings) {
                settingsPane.onMouseMove(xpos, ypos);
            }
        });

        // Mouse button callback for UI
        glfwSetMouseButtonCallback(openGlWindow.getWindow(), (window, button, action, mods) -> {
            if (!mouseCaptured && displaySettings) {
                settingsPane.onMouseButton(button, action);
            }
        });

        // Text input callback
        glfwSetCharCallback(openGlWindow.getWindow(), (window, codepoint) -> {
            if (!mouseCaptured && displaySettings) {
                settingsPane.onChar(codepoint);
            }
        });
        
        // Mouse wheel callback for Z movement
        glfwSetScrollCallback(openGlWindow.getWindow(), (window, xoffset, yoffset) -> {
            // Move in the direction the camera is facing
            Vector3f moveDirection = new Vector3f(Settings.getInstance().getCameraFront()).mul((float)(yoffset * Settings.getInstance().getMouseWheelSensitivity()));
            Settings.getInstance().setCameraPos(Settings.getInstance().getCameraPos().add(moveDirection));
        });
        
        // Window resize callback
        glfwSetFramebufferSizeCallback(openGlWindow.getWindow(), (window, width, height) -> {
            glViewport(0, 0, Settings.getInstance().getWidth(), Settings.getInstance().getHeight());
        });
        
        // Window close callback
        glfwSetWindowCloseCallback(openGlWindow.getWindow(), (window) -> {
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
    private void processWASDQEMovement(int key) {
        // Don't process movement if a text field is focused
        if (settingsPane.textFieldFocused) {
            return;
        }

        // Handle relative camera movement based on key states
        Vector3f moveDirection = new Vector3f();
        float moveSpeed = Settings.getInstance().getWASDSensitivity();

        if (shiftPressed) {
            moveSpeed *= 10;
        }
        
        // Calculate right vector (perpendicular to forward and up)
        Vector3f right = new Vector3f(Settings.getInstance().getCameraFront()).cross(Settings.getInstance().getCameraUp()).normalize();
        
        // Check key states and calculate relative movement
        switch (key) {
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
        
        // Apply movement to camera position
        if (moveDirection.length() > 0) {
            Settings.getInstance().setCameraPos(Settings.getInstance().getCameraPos().add(moveDirection));
            
        }
    }
    private void processIJKLMovement(int key) {
        // Don't process movement if a text field is focused
        if (settingsPane.textFieldFocused) {
            return;
        }
        
        // Handle relative camera movement based on key states
        Vector3f moveDirection = new Vector3f();
        float moveSpeed = 1f;

        switch (key) {
            case GLFW.GLFW_KEY_I: // Up in world space
            changeCamera(0, moveSpeed);
                break;
            case GLFW.GLFW_KEY_J: // Left in world space
                changeCamera(-moveSpeed, 0);
                break;
        case GLFW.GLFW_KEY_K: // Down in world space
                changeCamera(0, -moveSpeed);
                break;
            case GLFW.GLFW_KEY_L: // Right in world space
                changeCamera(moveSpeed, 0);
                break;
        }
    }

    private void drawStats() {
        if (font == null || !font.isLoaded()) {
            return; // Skip if font is not available
        }

        boolean newFrame = lastSteps != openGlWindow.gpuSimulation.getSteps();

        if (recordCurrentBodies && newFrame) {
            openGlWindow.gpuSimulation.updateCurrentBodies();

        }
        
        // Format stats string
        int curBodies = openGlWindow.gpuSimulation.currentBodies();
        int jMerged = openGlWindow.gpuSimulation.justMerged();

        int lostToOutOfBounds = lastDifference - jMerged;
        String statsText = String.format("FPS: %.1f\nBodies: %d\nMerged: %d\nLost to out of bounds: %d", openGlWindow.getFPS(), curBodies, jMerged, lostToOutOfBounds);
        

        if (newFrame) {
            lastSteps = openGlWindow.gpuSimulation.getSteps();
            lastDifference = lastBodies - curBodies;
            lastBodies = curBodies;
        }


        
        // Draw FPS in top-left corner with a black background for better readability
        int x = 10;
        int y = 20;
        int padding = 4;
        int lineHeight = 16;

        drawMultiLineText(statsText, x, y, lineHeight, padding);
    }

    public String getPerformanceText() {
        return openGlWindow.getPerformanceText();
    }

    private void drawFrameAdvanceIndicator() {
        if (font == null || !font.isLoaded()) {
            return;
        }

        // Draw frame advance indicator in top-right corner
        String indicatorText = "FRAME ADVANCE MODE\n Press ENTER to advance";
        
        int x = Settings.getInstance().getWidth() - (int)font.getTextWidth(indicatorText.split("\n")[1]) - 20;
        int y = 20;
        int padding = 4;
        int lineHeight = 16;

        drawMultiLineText(indicatorText, x, y, lineHeight, padding);
    }

    private void drawCrosshair() {
        setUpFor2D();
        
        
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
        
        tearDownFor2D();
    }
    
    private void drawDebugInfo() {
        String performanceText = openGlWindow.getPerformanceText();
        if (performanceText == null || performanceText.isEmpty()) return;
        
        

        // Calculate position (right side of screen)

        // Split performance text into lines
        String[] lines = performanceText.split("\n");
        float width = 0;
        for (String line : lines) {
            if (font.getTextWidth(line) > width) {
                width = font.getTextWidth(line);
            }
        }
        
        int x = Settings.getInstance().getWidth() - (int)width - 20; // 400 pixels from right edge
        int y = 80;
        int lineHeight = 16;
        int padding = 6;

        drawMultiLineText(performanceText, x, y, lineHeight, padding);
    }

    private void setUpFor2D() {
        checkGLError("before setUpFor2D");
        // Save current OpenGL state
        glPushMatrix();
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        checkGLError("saved projection matrix");
        // Set up 2D orthographic projection
        glOrtho(0, Settings.getInstance().getWidth(), Settings.getInstance().getHeight(), 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        checkGLError("saved modelview matrix");
        // Disable depth testing for UI elements
        glDisable(GL_DEPTH_TEST);
        checkGLError("disabled depth testing");
    }

    private void tearDownFor2D() {
        glEnable(GL_DEPTH_TEST);
        glPopMatrix();                   // MODELVIEW
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();                   // PROJECTION
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix(); 
    }

    private void drawMultiLineText(String text, int x, int y, int lineHeight, int padding) {
        checkGLError("before setUpFor2D");
        setUpFor2D();
        checkGLError("after setUpFor2D");

        // Split performance text into lines
        String[] lines = text.split("\n");
        float width = 0;
        for (String line : lines) {
            if (font.getTextWidth(line) > width) {
                width = font.getTextWidth(line);
            }
        }

        // Calculate total height needed
        int totalHeight = lines.length * (lineHeight+10);

        // Draw background rectangle
        glColor4f(0.0f, 0.0f, 0.0f, 0.8f); // Semi-transparent black
        checkGLError("before glBegin");
        glBegin(GL_QUADS);
        glVertex2f(x - padding, y - padding);
        glVertex2f(x + width + padding, y - padding); // 280 pixels wide
        glVertex2f(x + width + padding, y + totalHeight + padding);
        glVertex2f(x - padding, y + totalHeight + padding);
        glEnd();
        checkGLError("after glBegin");
        
        // Draw text in white
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f); // White
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (font != null && font.isLoaded()) {
                // Use bitmap font if available
                font.drawText(line, x, y + i * (lineHeight+10));
            } 
        }
        checkGLError("after drawText");
        tearDownFor2D();
        checkGLError("after tearDownFor2D");

    }
        

    public boolean isFontLoaded() {
        return font != null && font.isLoaded();
    }
    
}