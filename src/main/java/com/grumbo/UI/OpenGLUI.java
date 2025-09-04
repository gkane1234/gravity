package com.grumbo.UI;

import org.lwjgl.glfw.GLFW;
import org.joml.Vector3f;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

import com.grumbo.simulation.*;


import java.util.ArrayList;

public class OpenGLUI {

    private double lastX = Settings.getInstance().getWidth() / 2.0;
    private double lastY = Settings.getInstance().getHeight() / 2.0;    
    private boolean showFPS = true;

    private boolean firstMouse = true;
    private boolean mouseCaptured = true;
    private boolean shiftPressed = false;
    private boolean displaySettings = false;
    private boolean displayDebug = false;
    private BitmapFont font;

    private SettingsPane settingsPane;





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
        // WASD movement is now handled directly in OpenGLWindow for relative camera movement
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_W, () -> {processWASDQEMovement(GLFW.GLFW_KEY_W);},true));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_A, () -> {processWASDQEMovement(GLFW.GLFW_KEY_A);},true)); // Left - handled in OpenGLWindow
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_S, () -> {processWASDQEMovement(GLFW.GLFW_KEY_S);},true)); // Backward - handled in OpenGLWindow
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_D, () -> {processWASDQEMovement(GLFW.GLFW_KEY_D);},true)); // Right - handled in OpenGLWindow
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_Q, () -> {processWASDQEMovement(GLFW.GLFW_KEY_Q);},true)); // Up - handled in OpenGLWindow
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_E, () -> {processWASDQEMovement(GLFW.GLFW_KEY_E);},true)); // Down - handled in OpenGLWindow
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_P, () -> {}));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_I, () -> {}));
       // keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_LEFT, () -> Settings.getInstance().setTickSize(incrementWait(Settings.getInstance().getTickSize(), false))));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_F, () -> Settings.getInstance().toggleFollow()));
       // keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_RIGHT, () -> Settings.getInstance().setTickSize(incrementWait(Settings.getInstance().getTickSize(), true))));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_R, () -> {}));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_LEFT_SHIFT, () -> {shiftPressed = true;}, () -> {shiftPressed = false;},true));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_RIGHT_SHIFT, () -> {shiftPressed = true;}, () -> {shiftPressed = false;},true));

        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_UP, () -> {Settings.getInstance().setDt((float)(Settings.getInstance().getDt() * 1.1));},true));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_DOWN, () -> {Settings.getInstance().setDt((float)(Settings.getInstance().getDt() / 1.1));},true));
        //theta
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
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_F1, () -> {showFPS = !showFPS;},false));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_F2, () -> {
            if (openGlWindow.state == OpenGLWindow.State.FRAME_ADVANCE) {
                openGlWindow.state = OpenGLWindow.State.RUNNING;
                System.out.println("Switched to continuous simulation mode");
            } else {
                openGlWindow.state = OpenGLWindow.State.FRAME_ADVANCE;
                System.out.println("Switched to frame advance mode - press ENTER to advance frames");
            }
        },false));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_F3, () -> {displayDebug = !displayDebug;},false));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_F4, () -> {openGlWindow.gpuSimulation.toggleRegions();},false));
        keyEvents.add(new KeyEvent(GLFW.GLFW_KEY_ENTER, () -> {if (openGlWindow.state == OpenGLWindow.State.FRAME_ADVANCE) openGlWindow.setAdvanceFrame(true);},false));
        
        
    }

    public void drawUI() {
        runKeyFunctions();

        if (openGlWindow.state == OpenGLWindow.State.FRAME_ADVANCE) {
            drawFrameAdvanceIndicator();
        }

        // Draw FPS display
        if (showFPS) {
            drawFPS();
        }
        
        // Draw settings panel
        if (displaySettings) {
            settingsPane.draw(font);
        }

        // Draw profiling info
        if (displayDebug) {
            drawDebugInfo();
        }

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
                xoffset *= Settings.getInstance().getMouseRotationSensitivity();
                yoffset *= Settings.getInstance().getMouseRotationSensitivity();

                Settings.getInstance().setYaw((float)(Settings.getInstance().getYaw() + xoffset));
                Settings.getInstance().setPitch((float)(Settings.getInstance().getPitch() + yoffset));

                // Constrain pitch to prevent camera flipping
                if (Settings.getInstance().getPitch() > 89.0f)
                    Settings.getInstance().setPitch(89.0f);
                if (Settings.getInstance().getPitch() < -89.0f)
                    Settings.getInstance().setPitch(-89.0f);

                openGlWindow.updateCameraDirection();
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
            //System.out.println("Camera pos: " + Settings.getInstance().getCameraPos().x + ", " + Settings.getInstance().getCameraPos().y + ", " + Settings.getInstance().getCameraPos().z);
            
        }
    }

       
    private void drawFPS() {
        if (font == null || !font.isLoaded()) {
            return; // Skip if font is not available
        }
        
        // Save current OpenGL state
        glPushMatrix();
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        
        // Set up 2D orthographic projection
        glOrtho(0, Settings.getInstance().getWidth(), Settings.getInstance().getHeight(), 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        
        // Disable depth testing for UI elements
        glDisable(GL_DEPTH_TEST);
        
        // Format FPS string
        String fpsText = String.format("FPS: %.1f", openGlWindow.getFPS());
        
        // Draw FPS in top-left corner with a black background for better readability
        int x = 10;
        int y = 20;
        int padding = 4;
        
        // Get text dimensions
        float textWidth = font.getTextWidth(fpsText);
        float textHeight = font.getCharHeight();
        
        // Draw background rectangle
        glColor4f(0.0f, 0.0f, 0.0f, 0.7f); // Semi-transparent black
        glBegin(GL_QUADS);
        glVertex2f(x - padding, y - padding);
        glVertex2f(x + textWidth + padding, y - padding);
        glVertex2f(x + textWidth + padding, y + textHeight + padding);
        glVertex2f(x - padding, y + textHeight + padding);
        glEnd();
        
        // Draw text in white
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f); // White
        font.drawText(fpsText, x, y);
        //font.drawText("Bodies: " + openGlWindow.gpuSimulation.currentBodies(), x, y + textHeight);
        
        // Re-enable depth testing
        glEnable(GL_DEPTH_TEST);
        
        // Restore matrices
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);


    }

    public String getPerformanceText() {
        return openGlWindow.getPerformanceText();
    }

    private void drawFrameAdvanceIndicator() {
        if (font == null || !font.isLoaded()) {
            return;
        }

        // Save current OpenGL state
        glPushMatrix();
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        
        // Set up 2D orthographic projection
        glOrtho(0, Settings.getInstance().getWidth(), Settings.getInstance().getHeight(), 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        // Disable depth testing for UI elements
        glDisable(GL_DEPTH_TEST);
        
        // Draw frame advance indicator in top-right corner
        String indicatorText = "FRAME ADVANCE MODE";
        String instructionText = "Press ENTER to advance";
        
        int x = Settings.getInstance().getWidth() - (int)font.getTextWidth(instructionText) - 20;
        int y = 20;
        int padding = 4;
        
        // Get text dimensions
        float textWidth = font.getTextWidth(instructionText);
        float textHeight = font.getCharHeight();
        
        // Draw background rectangle
        glColor4f(0.0f, 0.0f, 0.0f, 0.8f); // Semi-transparent black
        glBegin(GL_QUADS);
        glVertex2f(x - padding, y - padding);
        glVertex2f(x + textWidth + padding, y - padding);
        glVertex2f(x + textWidth + padding, y + textHeight * 2 + padding);
        glVertex2f(x - padding, y + textHeight * 2 + padding);
        glEnd();
        
        // Draw indicator text in yellow
        glColor4f(1.0f, 1.0f, 0.0f, 1.0f); // Yellow
        font.drawText(indicatorText, x, y);
        
        // Draw instruction text in white
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f); // White
        font.drawText(instructionText, x, y + textHeight);
        
        // Re-enable depth testing
        glEnable(GL_DEPTH_TEST);
        
        // Restore matrices
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }
    
    private void drawDebugInfo() {
        String performanceText = openGlWindow.getPerformanceText();
        if (performanceText == null || performanceText.isEmpty()) return;
        
        // Save current OpenGL state
        glPushMatrix();
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        
        // Set up 2D orthographic projection
        glOrtho(0, Settings.getInstance().getWidth(), Settings.getInstance().getHeight(), 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        // Disable depth testing for UI elements
        glDisable(GL_DEPTH_TEST);
        
        // Split performance text into lines
        String[] lines = performanceText.split("\n");
        float width = 0;
        for (String line : lines) {
            if (font.getTextWidth(line) > width) {
                width = font.getTextWidth(line);
            }
        }



        
        // Calculate position (right side of screen)
        int x = Settings.getInstance().getWidth() - (int)width - 20; // 400 pixels from right edge
        int y = 80;
        int lineHeight = 16;
        int padding = 6;
        
        // Calculate total height needed
        int totalHeight = lines.length * (lineHeight+10);


        
        // Draw background rectangle
        glColor4f(0.0f, 0.0f, 0.0f, 0.8f); // Semi-transparent black
        glBegin(GL_QUADS);
        glVertex2f(x - padding, y - padding);
        glVertex2f(x + width + padding, y - padding); // 280 pixels wide
        glVertex2f(x + width + padding, y + totalHeight + padding);
        glVertex2f(x - padding, y + totalHeight + padding);
        glEnd();
        
        // Draw text in white
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f); // White
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (font != null && font.isLoaded()) {
                // Use bitmap font if available
                font.drawText(line, x, y + i * (lineHeight+10));
            } 
        }
        
        // Re-enable depth testing
        glEnable(GL_DEPTH_TEST);
        
        // Restore matrices
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    public boolean isFontLoaded() {
        return font != null && font.isLoaded();
    }

    
    
    
    
    
}