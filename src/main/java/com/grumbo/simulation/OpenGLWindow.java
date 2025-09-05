package com.grumbo.simulation;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import com.grumbo.UI.OpenGLUI;
import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.system.MemoryUtil.*;

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

    public OpenGLWindow(GPUSimulation gpuSimulation) {
        this.gpuSimulation = gpuSimulation;
    }

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

        // Restore default settings on startup
        Settings.getInstance().restoreDefaults();

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

        glViewport(0, 0, Settings.getInstance().getWidth(), Settings.getInstance().getHeight());
        glfwSetFramebufferSizeCallback(window, (win, w, h) -> glViewport(0, 0, w, h));

        glEnable(GL_PROGRAM_POINT_SIZE);
        glEnable(GL_POINT_SPRITE);
        glPointParameteri(GL_POINT_SPRITE_COORD_ORIGIN, GL_LOWER_LEFT);

        openGlUI = new OpenGLUI(this);
        
        System.out.println(getStartupInfo());
    }

    public void step() {

        if (glfwWindowShouldClose(window)) {

            System.out.println("Render loop ended");

            gpuSimulation.stop();
            cleanup();
        }
        
        // Update FPS calculation
        updateFPS();
        openGlUI.drawUI();
        glfwSwapBuffers(window);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glfwPollEvents();
    }

    // Initialization Methods
   
    private String getStartupInfo() {
        String info = "";
        if (!openGlUI.isFontLoaded()) {
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

    private void cleanup() {
        // Cleanup font resources

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
    public long getWindow() {
        return window;
    }

    public double getFPS() {
        return fps;
    }

    public boolean getShowCrosshair() {
        return openGlUI.showCrosshair;
    }
    public void setShowCrosshair(boolean showCrosshair) {
        openGlUI.showCrosshair = showCrosshair;
    }

    public GPUSimulation.State getState() {
        return gpuSimulation.state;
    }
    public void setState(GPUSimulation.State state) {
        this.gpuSimulation.state = state;
    }

    public String getPerformanceText() {
        return gpuSimulation.getPerformanceText();
    }
} 