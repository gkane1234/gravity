package com.grumbo.simulation;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.system.MemoryUtil.*;

import com.grumbo.ui.OpenGLUI;

/**
 * OpenGLWindow class creates the OpenGL window, manages window events, and draws the UI.
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public class OpenGLWindow {

    // The window handle
    private long window;

    private OpenGLUI openGlUI;

    // FPS monitoring
    private double lastTime = 0.0;
    private int frameCount = 0;
    private double fps = 0.0;
    

    public GPUSimulation gpuSimulation;

    // FPS limiting using GPU swap interval
    private int maxFPS = -1; // Default max FPS
    private int framebufferWidth = 1;
    private int framebufferHeight = 1;
    private int windowedX;
    private int windowedY;
    private int windowedW;
    private int windowedH;
    private boolean fullscreen;
    /**
     * Constructor for the OpenGLWindow class.
     * @param gpuSimulation the GPU simulation.
     */
    public OpenGLWindow(GPUSimulation gpuSimulation) {
        this.gpuSimulation = gpuSimulation;
    }

    /**
     * Initializes the OpenGL window.
     */
    public void init() {
        // Setup an error callback
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW
        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        // Configure GLFW
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        // Set the OpenGL context version to 4.6
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
        // Set the OpenGL profile to COMPAT to allow for legacy OpenGL features.
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_COMPAT_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_FALSE);

        // Create the window
        window = glfwCreateWindow(Settings.getInstance().getWidth(), Settings.getInstance().getHeight(), "Gravity Simulator 3D", NULL, NULL);
        if (window == NULL)
            throw new RuntimeException("Failed to create the GLFW window");

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);

        // Set initial FPS limit
        setMaxFPS(maxFPS);

        // Set cursor to disabled (hidden and locked to window)
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        // Make the window visible
        glfwShowWindow(window);

        GL.createCapabilities();

        // Enable point size and point sprite.
        glEnable(GL_PROGRAM_POINT_SIZE);
        glEnable(GL_POINT_SPRITE);
        glPointParameteri(GL_POINT_SPRITE_COORD_ORIGIN, GL_LOWER_LEFT);

        // Create the OpenGLUI. Its callbacks replace the framebuffer-size callback,
        // so the initial size event has to be drained after that.
        openGlUI = new OpenGLUI(this);
        // The first framebuffer size event recreates the swapchain. Drain it before
        // any scene is drawn, or that event arrives after the first present and the
        // first frame is discarded.
        glfwPollEvents();
        applyFramebufferSize();

        System.out.println(getStartupInfo());
    }

    /**
     * Sets the viewport to the real framebuffer size, in pixels.
     * The saved window width and height are left alone, so a larger window
     * shows more of the scene instead of stretching the old image.
     */
    public void applyFramebufferSize() {
        if (window == 0) {
            return;
        }
        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetFramebufferSize(window, width, height);
        if (width[0] < 1 || height[0] < 1) {
            return;
        }
        framebufferWidth = width[0];
        framebufferHeight = height[0];
        Settings.getInstance().setDrawableSize(framebufferWidth, framebufferHeight);
        glViewport(0, 0, framebufferWidth, framebufferHeight);
    }

    /**
     * Covers the monitor at its own resolution, then returns to the previous window.
     * Vertical field of view stays the same, so objects keep their angular size.
     */
    public void toggleFullscreen() {
        if (window == 0) {
            return;
        }
        if (fullscreen) {
            glfwSetWindowMonitor(window, NULL, windowedX, windowedY, windowedW, windowedH, GLFW_DONT_CARE);
            fullscreen = false;
        } else {
            int[] x = new int[1];
            int[] y = new int[1];
            int[] width = new int[1];
            int[] height = new int[1];
            glfwGetWindowPos(window, x, y);
            glfwGetWindowSize(window, width, height);
            windowedX = x[0];
            windowedY = y[0];
            windowedW = Math.max(1, width[0]);
            windowedH = Math.max(1, height[0]);
            long monitor = monitorForWindow();
            GLFWVidMode mode = glfwGetVideoMode(monitor);
            if (mode == null) {
                return;
            }
            glfwSetWindowMonitor(window, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate());
            fullscreen = true;
        }
        applyFramebufferSize();
    }

    /**
     * Maps a cursor position from window coordinates into framebuffer pixels.
     */
    public double toFramebufferX(double windowX) {
        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetWindowSize(window, width, height);
        if (width[0] < 1) {
            return windowX;
        }
        return windowX * framebufferWidth / (double) width[0];
    }

    /**
     * Maps a cursor position from window coordinates into framebuffer pixels.
     */
    public double toFramebufferY(double windowY) {
        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetWindowSize(window, width, height);
        if (height[0] < 1) {
            return windowY;
        }
        return windowY * framebufferHeight / (double) height[0];
    }

    private long monitorForWindow() {
        int[] x = new int[1];
        int[] y = new int[1];
        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetWindowPos(window, x, y);
        glfwGetWindowSize(window, width, height);
        int centerX = x[0] + width[0] / 2;
        int centerY = y[0] + height[0] / 2;
        PointerBuffer monitors = glfwGetMonitors();
        long primary = glfwGetPrimaryMonitor();
        if (monitors == null) {
            return primary;
        }
        for (int i = 0; i < monitors.limit(); i++) {
            long monitor = monitors.get(i);
            GLFWVidMode mode = glfwGetVideoMode(monitor);
            int[] mx = new int[1];
            int[] my = new int[1];
            glfwGetMonitorPos(monitor, mx, my);
            if (mode != null
                    && centerX >= mx[0] && centerX < mx[0] + mode.width()
                    && centerY >= my[0] && centerY < my[0] + mode.height()) {
                return monitor;
            }
        }
        return primary;
    }
    /**
     * Steps the OpenGL window.
     */
    public void step() {

        if (window == 0) {
            return;
        }

        if (glfwWindowShouldClose(window)) {
            System.out.println("Render loop ended");
            gpuSimulation.stop();
            cleanup();
            return;
        }
        
        // Update FPS calculation
        updateFPS();
        openGlUI.drawUI();
        GPUSimulation.checkGLError("after openGlUI.drawUI");
        glfwSwapBuffers(window);
        glfwPollEvents();
        GPUSimulation.checkGLError("after glfwPollEvents");
    }


    // Initialization Methods
   
    /**
     * Gets the startup information.
     * @return the startup information.
     */
    private String getStartupInfo() {
        String info = "";
        if (!openGlUI.isFontLoaded()) {
            info += "Warning: Bitmap font not loaded, using fallback text rendering" + "\n";
        }
        info += "OpenGL Version: " + glGetString(GL_VERSION) + "\n";
        info += "OpenGL Vendor: " + glGetString(GL_VENDOR) + "\n";
        info += "OpenGL Renderer: " + glGetString(GL_RENDERER) + "\n";
        info += "=== CONTROLS ===" + "\n";
        info += "Mouse: Look around (when captured)" + "\n";
        info += "WASD / QE: Move camera" + "\n";
        info += "IJKL: Move in world axes" + "\n";
        info += "ESC: Toggle mouse capture" + "\n";
        info += "F1: Pause / resume" + "\n";
        info += "ENTER: Advance one frame while paused" + "\n";
        info += "F2: Toggle tree regions" + "\n";
        info += "F3: Toggle debug overlay" + "\n";
        info += "F4: Toggle stats" + "\n";
        info += "F5: Toggle crosshair" + "\n";
        info += "F6: Toggle recording (PNG frames)" + "\n";
        info += "F11: Full screen" + "\n";
        info += "`: Settings window" + "\n";
        info += "+/-: Camera scale" + "\n";
        info += "Arrows: Rotate camera" + "\n";
        info += "Shift+Up/Down: Time step (dt)" + "\n";
        info += "Shift+Left/Right: Barnes-Hut theta" + "\n";
        info += "[/]: Softening" + "\n";
        info += "Close window: Return to setup menu" + "\n";
        info += "==================" + "\n";
        info += "Initial camera position: " + Settings.getInstance().getCameraPos().x + ", " + Settings.getInstance().getCameraPos().y + ", " + Settings.getInstance().getCameraPos().z + "\n";
        info += "Initial camera scale: " + Settings.getInstance().getCameraScale() + "\n";
        info += "Initial shift: " + java.util.Arrays.toString(Settings.getInstance().getShift()) + "\n";
        info += "Camera front: " + Settings.getInstance().getCameraFront().x + ", " + Settings.getInstance().getCameraFront().y + ", " + Settings.getInstance().getCameraFront().z + "\n";
        return info;

    }

    /**
     * Cleans up the OpenGL window.
     */
    private void cleanup() {
        if (window == 0) {
            return;
        }
        // Free the window callbacks and destroy the window
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        window = 0;

        // Tear down GPU resources. Leave GLFW so the setup menu can open again
        // (Main loops SetupScreen -> simulation -> SetupScreen).
        gpuSimulation.cleanup();
        glfwTerminate();
        var cb = glfwSetErrorCallback(null);
        if (cb != null) {
            cb.free();
        }
    }

    // Setters and Getters
    
    /**
     * Updates the FPS.
     */
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
    
    /**
     * Sets the maximum FPS.
     * @param maxFPS the maximum FPS.
     */
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
        
    /**
     * Gets the swap interval.
     * @return the swap interval.
     */
    public int getSwapInterval() {
        // Get the current swap interval from GLFW
        return glfwGetWindowAttrib(window, GLFW_CONTEXT_REVISION);
    }
    
    /**
     * Gets the maximum FPS.
     * @return the maximum FPS.
     */
    public int getMaxFPS() {
        return maxFPS;
    }

    /**
     * Applies the FPS limit.
     */
    public void applyFPSLimit() {
        // Re-apply the current FPS settings
        setMaxFPS(maxFPS);
    }
    /**
     * Gets the window.
     * @return the window.
     */
    public long getWindow() {
        return window;
    }

    /**
     * Gets the FPS.
     * @return the FPS.
     */
    public double getFPS() {
        return fps;
    }

    /**
     * Gets the show crosshair.
     * @return the show crosshair.
     */
    public boolean getShowCrosshair() {
        return openGlUI.showCrosshair;
    }
    /**
     * Sets the show crosshair.
     * @param showCrosshair the show crosshair.
     */
    public void setShowCrosshair(boolean showCrosshair) {
        openGlUI.showCrosshair = showCrosshair;
    }

    /**
     * Gets the state of the GPU simulation.
     * @return the state of the GPU simulation.
     */
    public GPUSimulation.State getState() {
        return gpuSimulation.state;
    }
    /**
     * Sets the state of the GPU simulation.
     * @param state the state of the GPU simulation.
     */
    public void setState(GPUSimulation.State state) {
        this.gpuSimulation.state = state;
    }

    /**
     * Gets the performance text.
     * @return the performance text.
     */
    public String getPerformanceText() {
        return gpuSimulation.getPerformanceText();
    }
} 