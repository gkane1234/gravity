package com.grumbo.simulation;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import com.grumbo.UI.OpenGLUI;

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


    
    private OpenGLUI openGlUI;
    
    // Settings panel state

    
    // FPS monitoring

    private double lastTime = 0.0;
    private int frameCount = 0;
    private double fps = 0.0;
    

    public GPUSimulation gpuSimulation;
    private ArrayList<Planet> planets;

    public enum State {
        LOADING,
        RUNNING,
        PAUSED,
        FRAME_ADVANCE
    }
    public State state = State.FRAME_ADVANCE;

    private boolean advanceFrame = false;
    // FPS limiting using GPU swap interval
    private int maxFPS = 60; // Default max FPS

    private boolean debug = true;
    public OpenGLWindow() {
        planets = createBoxSimulation();
        
        //planets = Planet.mergeOverlappingPlanets(planets);
        //planets.add(new Planet(0, 0, 0, 0, 0, 0, 10_000_000));


    }

    public ArrayList<Planet> createBoxSimulation() {
        ArrayList<Planet> planets = new ArrayList<>();
        float[] xRange = {-4000, 4000};
        float[] yRange = {-4000, 4000};
        float[] zRange = {-4000, 4000};
        float[] xVRange = {-0, 0};
        float[] yVRange = {-0, 0};
        float[] zVRange = {-0, 0};
        float[] mRange = {10, 100};
        float[] radius = {100, 1000};
        Planet center = new Planet(0, 0, 0, 0, 0, 0, 10000);
        //Planet center2 = new Planet(100,0,0,0,0,0,10);
        planets = Planet.makeNew(100_000, xRange, yRange, zRange, xVRange, yVRange, zVRange, mRange);
        planets.add(center);
        //planets.add(center2);

        return planets;
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


        // Make the OpenGL context current
        glfwMakeContextCurrent(window);

        // Set initial FPS limit
        setMaxFPS(maxFPS);

        // Set cursor to disabled (hidden and locked to window)
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        // Make the window visible
        glfwShowWindow(window);

        GL.createCapabilities();

        // Initialize UI
        openGlUI = new OpenGLUI(this);
        

        System.out.println(getStartupInfo());
        
        // Initialize GPU simulation
        gpuSimulation = new GPUSimulation(this, planets, Render.RenderMode.MESH_SPHERES, debug);
        gpuSimulation.init();
        

        // Enable depth testing for 3D
        //glEnable(GL_DEPTH_TEST);
        
    }

    private void loop() {
        System.out.println("Starting render loop...");
        
        // Initialize FPS timing
        lastTime = glfwGetTime();
        
        while (!glfwWindowShouldClose(window)) {
            // Update FPS calculation
            updateFPS();

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);


            // Set up camera view
            getCameraView();


            gpuSimulation.step(state);
            
            // Draw crosshair
            drawCrosshair();

            openGlUI.drawUI();
           

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

    public void updateCameraDirection() {
        Vector3f direction = new Vector3f();
        direction.x = (float)(java.lang.Math.cos(java.lang.Math.toRadians(Settings.getInstance().getYaw())) * java.lang.Math.cos(java.lang.Math.toRadians(Settings.getInstance().getPitch())));
        direction.y = (float)java.lang.Math.sin(java.lang.Math.toRadians(Settings.getInstance().getPitch()));
        direction.z = (float)(java.lang.Math.sin(java.lang.Math.toRadians(Settings.getInstance().getYaw())) * java.lang.Math.cos(java.lang.Math.toRadians(Settings.getInstance().getPitch())));
        Settings.getInstance().setCameraFront(direction.normalize());
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

    // Drawing Methods
    
    private void getCameraView() {
        // Get camera position from Settings
        Vector3f eye = new Vector3f(Settings.getInstance().getCameraPos());
        Vector3f center = new Vector3f(eye).add(Settings.getInstance().getCameraFront());
        Vector3f up = new Vector3f(Settings.getInstance().getCameraUp());
        float fov = Settings.getInstance().getFov();
        float aspect = (float) Settings.getInstance().getWidth() / (float) Settings.getInstance().getHeight();
        float near = Settings.getInstance().getNearPlane();
        float far = Settings.getInstance().getFarPlane();

        Matrix4f proj = new Matrix4f().perspective((float) java.lang.Math.toRadians(fov), aspect, near, far);
        Matrix4f view = new Matrix4f().lookAt(eye, center, up);
        Matrix4f mvp = new Matrix4f(proj).mul(view);


        try (MemoryStack stack = stackPush()) {
            FloatBuffer mvpBuf = stack.mallocFloat(16);
            mvp.get(mvpBuf);
            gpuSimulation.setMvp(mvpBuf);
        }

        gpuSimulation.setCameraPos(eye.x, eye.y, eye.z);
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
    public void setAdvanceFrame(boolean advanceFrame) {
        this.advanceFrame = advanceFrame;
    }
    public double getFPS() {
        return fps;
    }

    public boolean advanceFrame() {
        return advanceFrame;
    }

    public String getPerformanceText() {
        return gpuSimulation.getPerformanceText();
    }
} 