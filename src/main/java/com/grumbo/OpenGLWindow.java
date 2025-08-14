package com.grumbo;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;
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
    
    
    // Reference to gravity simulator
    private GravityUI ui;
    private SettingsPane settingsPane;
    
    // Settings panel state
    private boolean debug = false;
    
    // Bitmap font for UI text
    private BitmapFont font;
    // Embedded GPU sim that renders into this window
    private GPUSimulation gpuPoints;
    private ArrayList<Planet> planets;

    private enum State {
        LOADING,
        RUNNING,
        PAUSED,
    }
    private State state = State.RUNNING;


    public OpenGLWindow() {
        this.ui = new GravityUI();
        this.settingsPane = new SettingsPane();

        planets = new ArrayList<>();
        float[] xRange = {-1000, 1000};
        float[] yRange = {-1000, 1000};
        float[] zRange = {-1000, 1000};
        float[] xVRange = {-100, 100};
        float[] yVRange = {-100, 100};
        float[] zVRange = {-100, 100};
        float[] mRange = {100, 1000};
        planets = Planet.makeNew(100000, xRange, yRange, zRange, xVRange, yVRange, zVRange, mRange);
        planets.add(new Planet(0, 0, 0, 0, 0, 0, 1000000));


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

        // Initialize embedded GPU points simulation (headless)
        gpuPoints = new GPUSimulation(planets);
        gpuPoints.initWithCurrentContext();

        // Enable depth testing for 3D
        //glEnable(GL_DEPTH_TEST);
        
        // Set the clear color to dark gray
        glClearColor(0.2f, 0.2f, 0.2f, 1.0f);

        System.out.println("Starting render loop...");
        
        while (!glfwWindowShouldClose(window)) {

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
                    gpuPoints.setMvp(mvpBuf);
                }
                // Update camera-dependent uniforms for mesh spheres
                gpuPoints.setCameraPos(eye.x, eye.y, eye.z);
                gpuPoints.step();
                gpuPoints.render();
                
                // Draw crosshair last (on top)
                drawCrosshair();
                
                // Draw settings panel if visible (on top of everything)

            }

            if (debug) {
                settingsPane.draw(font);

            }

            glfwSwapBuffers(window);
            glfwPollEvents();
            
        }
        
        System.out.println("Render loop ended");

        // Cleanup embedded sim GL objects
        if (gpuPoints != null) {
            gpuPoints.cleanupEmbedded();
        }
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


} 