package com.grumbo.simulation;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import com.grumbo.UI.BitmapFont;
import com.grumbo.gpu.GravityUI;

import org.joml.*;

import java.nio.*;
import java.util.ArrayList;

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
    private boolean shiftPressed = false;
    

    private GravityUI ui;
    private SettingsPane settingsPane;
    
    // Settings panel state
    private boolean debug = false;
    
    // FPS monitoring
    private boolean showFPS = true;
    private double lastTime = 0.0;
    private int frameCount = 0;
    private double fps = 0.0;
    
    private BitmapFont font;
    private GPUSimulation gpuSimulation;
    private ArrayList<Planet> planets;

    private enum State {
        LOADING,
        RUNNING,
        PAUSED,
        FRAME_ADVANCE
    }
    private State state = State.FRAME_ADVANCE;
    
    private boolean frameReady = false;
    
    // FPS limiting using GPU swap interval
    private int maxFPS = 60; // Default max FPS


    public OpenGLWindow() {
        this.ui = new GravityUI();
        this.settingsPane = new SettingsPane();

        planets = new ArrayList<>();
        float[] xRange = {0, 200};
        float[] yRange = {0, 200};
        float[] zRange = {0, 200};
        float[] xVRange = {-0, 0};
        float[] yVRange = {-0, 0};
        float[] zVRange = {-0, 0};
        float[] mRange = {10, 100};
        float[] radius = {100, 1000};
        Planet center = new Planet(0, 0, 0, 0, 0, 0, 10000);
        Planet center2 = new Planet(100,0,0,0,0,0,10);
        planets = Planet.makeNew(1000, xRange, yRange, zRange, xVRange, yVRange, zVRange, mRange);
        planets.add(center);
        planets.add(center2);
        //planets = Planet.mergeOverlappingPlanets(planets);
        //planets.add(new Planet(0, 0, 0, 0, 0, 0, 10_000_000));


    }

    public void run() {
        init();
        loop();
        cleanup();

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


        // Make the OpenGL context current
        glfwMakeContextCurrent(window);

        // Set initial FPS limit
        setMaxFPS(maxFPS);

        // Set cursor to disabled (hidden and locked to window)
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        // Make the window visible
        glfwShowWindow(window);

        GL.createCapabilities();
        
        // Initialize bitmap font
        font = new BitmapFont();

        System.out.println(getStartupInfo());
        
        // Initialize GPU simulation
        gpuSimulation = new GPUSimulation(planets);
        gpuSimulation.init();

        // Enable depth testing for 3D
        glEnable(GL_DEPTH_TEST);
        
    }

    private void loop() {
        System.out.println("Starting render loop...");
        
        // Initialize FPS timing
        lastTime = glfwGetTime();
        
        while (!glfwWindowShouldClose(window)) {


            
            // Update FPS calculation
            updateFPS();

            if (state == State.RUNNING) {
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                // Process WASD movement
                if (!settingsPane.textFieldFocused) {
                    processMovement();
                }

                // Set up 3D projection matrix
                setupProjection();
                
                // Set up view matrix (camera)
                setupCamera();

                // Compute MVP using JOML from current Settings camera state
                float fov = Settings.getInstance().getFov();
                float aspect = (float) Settings.getInstance().getWidth() / (float) Settings.getInstance().getHeight();
                float near = Settings.getInstance().getNearPlane();
                float far = Settings.getInstance().getFarPlane();

                Matrix4f proj = new Matrix4f().perspective((float) java.lang.Math.toRadians(fov), aspect, near, far);
                Vector3f eye = new Vector3f(Settings.getInstance().getCameraPos());
                Vector3f center = new Vector3f(eye).add(Settings.getInstance().getCameraFront());
                Vector3f up = new Vector3f(Settings.getInstance().getCameraUp());
                Matrix4f view = new Matrix4f().lookAt(eye, center, up);
                Matrix4f mvp = new Matrix4f(proj).mul(view);
                try (MemoryStack stack = stackPush()) {
                    FloatBuffer mvpBuf = stack.mallocFloat(16);
                    mvp.get(mvpBuf);
                    gpuSimulation.setMvp(mvpBuf);
                }
                // Update camera-dependent uniforms for mesh spheres
                gpuSimulation.setCameraPos(eye.x, eye.y, eye.z);
                gpuSimulation.step();
                gpuSimulation.render();
                
                // Draw crosshair last (on top)
                drawCrosshair();
                
                // Draw FPS display (on top)
                if (showFPS) {
                    drawFPS();
                }
                
                // Draw settings panel if visible (on top of everything)
                if (debug) {
                    drawDebugInfo();
                    settingsPane.draw(font);
                }
                
                

            } else if (state == State.FRAME_ADVANCE) {
                // In frame advance mode, we just render the current state
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                // Process WASD movement
                if (!settingsPane.textFieldFocused) {
                    processMovement();
                }

                // Set up 3D projection matrix
                setupProjection();
                
                // Set up view matrix (camera)
                setupCamera();

                // Draw GPU points
                // Compute MVP using JOML from current Settings camera state
                float fov = Settings.getInstance().getFov();
                float aspect = (float) Settings.getInstance().getWidth() / (float) Settings.getInstance().getHeight();
                float near = Settings.getInstance().getNearPlane();
                float far = Settings.getInstance().getFarPlane();

                Matrix4f proj = new Matrix4f().perspective((float) java.lang.Math.toRadians(fov), aspect, near, far);
                Vector3f eye = new Vector3f(Settings.getInstance().getCameraPos());
                Vector3f center = new Vector3f(eye).add(Settings.getInstance().getCameraFront());
                Vector3f up = new Vector3f(Settings.getInstance().getCameraUp());
                Matrix4f view = new Matrix4f().lookAt(eye, center, up);
                Matrix4f mvp = new Matrix4f(proj).mul(view);
                try (MemoryStack stack = stackPush()) {
                    FloatBuffer mvpBuf = stack.mallocFloat(16);
                    mvp.get(mvpBuf);
                    gpuSimulation.setMvp(mvpBuf);
                }
                // Update camera-dependent uniforms for mesh spheres
                gpuSimulation.setCameraPos(eye.x, eye.y, eye.z);
                
                // Only advance simulation if Enter was pressed
                if (frameReady) {
                    gpuSimulation.step();
                    frameReady = false; // Reset for next Enter press
                }
                
                gpuSimulation.render();
                
                // Draw crosshair last (on top)
                drawCrosshair();
                
                // Draw FPS display (on top)
                if (showFPS) {
                    drawFPS();
                }
                
                // Draw frame advance indicator
                drawFrameAdvanceIndicator();
                
                // Draw settings panel if visible (on top of everything)
                if (debug) {
                    drawDebugInfo();
                    settingsPane.draw(font);
                }
                
                
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
            
        }
        
        System.out.println("Render loop ended");

        // Cleanup embedded sim GL objects
        if (gpuSimulation != null) {
            gpuSimulation.cleanupEmbedded();
        }
    }
 
    // Input Methods

    private void updateCameraDirection() {
        Vector3f direction = new Vector3f();
        direction.x = (float)(java.lang.Math.cos(java.lang.Math.toRadians(Settings.getInstance().getYaw())) * java.lang.Math.cos(java.lang.Math.toRadians(Settings.getInstance().getPitch())));
        direction.y = (float)java.lang.Math.sin(java.lang.Math.toRadians(Settings.getInstance().getPitch()));
        direction.z = (float)(java.lang.Math.sin(java.lang.Math.toRadians(Settings.getInstance().getYaw())) * java.lang.Math.cos(java.lang.Math.toRadians(Settings.getInstance().getPitch())));
        Settings.getInstance().setCameraFront(direction.normalize());
    }
    
    private void processMovement() {
        // Handle relative camera movement based on key states
        Vector3f moveDirection = new Vector3f();
        float moveSpeed = Settings.getInstance().getWASDSensitivity();

        if (shiftPressed) {
            moveSpeed *= 10;
        }
        
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
    
    
    // Initialization Methods
    
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
            if (key == GLFW_KEY_F1 && action == GLFW_PRESS) {
                showFPS = !showFPS;
            }
            if (key == GLFW_KEY_F2 && action == GLFW_PRESS) {
                if (state == State.FRAME_ADVANCE) {
                    state = State.RUNNING;
                    System.out.println("Switched to continuous simulation mode");
                } else {
                    state = State.FRAME_ADVANCE;
                    System.out.println("Switched to frame advance mode - press ENTER to advance frames");
                }
            }

            if (key == GLFW_KEY_ENTER && action == GLFW_PRESS) {
                if (state == State.FRAME_ADVANCE) {
                    frameReady = true;
                }
            }

             // Add shift key handling here
             if (key == GLFW_KEY_LEFT_SHIFT || key == GLFW_KEY_RIGHT_SHIFT) {
                shiftPressed = (action == GLFW_PRESS);
            }
            // Route keys to focused textfields when not captured
            if (!mouseCaptured && debug) {
                if (settingsPane.onKey(key, action, mods)) {
                    return;
                }
            }
            
            ui.updateKeys(key, action);
        });
        
        // Mouse callback for FPS-style camera rotation (always active)
        glfwSetCursorPosCallback(window, (window, xpos, ypos) -> {
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
            } else if (debug) {
                settingsPane.onMouseMove(xpos, ypos);
            }
        });

        // Mouse button callback for UI
        glfwSetMouseButtonCallback(window, (window, button, action, mods) -> {
            if (!mouseCaptured && debug) {
                settingsPane.onMouseButton(button, action);
            }
        });

        // Text input callback
        glfwSetCharCallback(window, (window, codepoint) -> {
            if (!mouseCaptured && debug) {
                settingsPane.onChar(codepoint);
            }
        });
        
        // Mouse wheel callback for Z movement
        glfwSetScrollCallback(window, (window, xoffset, yoffset) -> {
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
    
    private String getStartupInfo() {
        String info = "";
        if (!font.isLoaded()) {
            info += "Warning: Bitmap font not loaded, using fallback text rendering" + "\n";
        }
        info += "OpenGL Version: " + glGetString(GL_VERSION) + "\n";
        info += "OpenGL Vendor: " + glGetString(GL_VENDOR) + "\n";
        info += "OpenGL Renderer: " + glGetString(GL_RENDERER) + "\n";
        info += "=== CONTROLS ===" + "\n";
        info += "Mouse: Look around (FPS-style)" + "\n";
        info += "WASD: Move camera (relative to view direction)" + "\n";
        info += "QE: Move up/down" + "\n";
        info += "Mouse wheel: Zoom in/out" + "\n";
        info += "ESC: Toggle mouse cursor (press to release/capture mouse)" + "\n";
        info += "F1: Toggle FPS display" + "\n";
        info += "F2: Toggle frame advance mode" + "\n";
        info += "F3: Show FPS limiting information" + "\n";
        info += "Enter: Advance one frame (when in frame advance mode)" + "\n";
        info += "P: Toggle performance stats" + "\n";
        info += "I: Print detailed performance info" + "\n";
        info += "B: Toggle chunk borders" + "\n";
        info += "T: Toggle planet trails" + "\n";
        info += "F: Toggle follow mode" + "\n";
        info += "R: Reset performance counters" + "\n";
        info += "Up/Down arrows: Change chunk size" + "\n";
        info += "[/]: Change simulation speed" + "\n";
        info += "+/-: Zoom in/out" + "\n";
        info += "FPS Limiting: Use setMaxFPS(int) method to set GPU-based frame rate limit" + "\n";
        info += "==================" + "\n";
        info += "Initial camera position: " + Settings.getInstance().getCameraPos().x + ", " + Settings.getInstance().getCameraPos().y + ", " + Settings.getInstance().getCameraPos().z + "\n";
        info += "Initial zoom: " + Settings.getInstance().getZoom() + "\n";
        info += "Initial shift: " + java.util.Arrays.toString(Settings.getInstance().getShift()) + "\n";
        info += "Camera front: " + Settings.getInstance().getCameraFront().x + ", " + Settings.getInstance().getCameraFront().y + ", " + Settings.getInstance().getCameraFront().z + "\n";
        return info;

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
        Vector3f center = new Vector3f(eye).add(Settings.getInstance().getCameraFront());
        Vector3f up = new Vector3f(Settings.getInstance().getCameraUp());
        
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
    
    // Drawing Methods
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
        String fpsText = String.format("FPS: %.1f", fps);
        
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
        
        // Re-enable depth testing
        glEnable(GL_DEPTH_TEST);
        
        // Restore matrices
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
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
        
        int x = Settings.getInstance().getWidth() - 200;
        int y = 20;
        int padding = 4;
        
        // Get text dimensions
        float textWidth = font.getTextWidth(indicatorText);
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
        if (gpuSimulation == null) return;
        
        String performanceText = gpuSimulation.debugString;
        if (performanceText.isEmpty()) return;
        
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


    private void cleanup() {
        // Cleanup font resources
        if (font != null) {
            font.cleanup();
        }
        
        // Free the window callbacks and destroy the window
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        // Terminate GLFW and free the error callback
        gpuSimulation.cleanupEmbedded();
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    // Setters and Getters
    
    private void updateFPS() {
        double currentTime = glfwGetTime();
        frameCount++;
        
        // Update FPS every second
        if (currentTime - lastTime >= 1.0) {
            fps = frameCount / (currentTime - lastTime);
            frameCount = 0;
            lastTime = currentTime;
        }
    }
    
    public void setMaxFPS(int maxFPS) {
        this.maxFPS = maxFPS;
        
        // Apply the FPS limit using GPU swap intervals
        if (maxFPS <= 0) {
            // Unlimited FPS - no vsync
            glfwSwapInterval(0);
        } else if (maxFPS >= 60) {
            // 60+ FPS - use vsync (swap every frame)
            glfwSwapInterval(1);
        } else {
            // Custom FPS - calculate swap interval
            // For example: 30 FPS = swap every 2 frames (60/30 = 2)
            int interval = java.lang.Math.max(1, 60 / maxFPS);
            glfwSwapInterval(interval);
        }
    }
        
    public int getSwapInterval() {
        // Get the current swap interval from GLFW
        return glfwGetWindowAttrib(window, GLFW_CONTEXT_REVISION);
    }
    
    public int getMaxFPS() {
        return maxFPS;
    }

    public void applyFPSLimit() {
        // Re-apply the current FPS settings
        setMaxFPS(maxFPS);
    }

    

} 