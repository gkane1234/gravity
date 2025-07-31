package com.grumbo;

import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;
import org.joml.*;

import java.nio.*;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class OpenGLWindow {

    // The window handle
    private long window;
    
    // Window dimensions
    private int width = 1000;
    private int height = 1000;
    
    // Camera variables
    private Vector3f cameraPos = new Vector3f(0.0f, 0.0f, 100.0f);
    private Vector3f cameraFront = new Vector3f(0.0f, 0.0f, -1.0f);
    private Vector3f cameraUp = new Vector3f(0.0f, 1.0f, 0.0f);
    
    // Mouse variables
    private double lastX = width / 2.0;
    private double lastY = height / 2.0;
    private float yaw = -90.0f;
    private float pitch = 0.0f;
    private boolean firstMouse = true;
    private boolean mouseWheelPressed = false; // Track mouse wheel button state
    
    // WASD movement variables
    private int keyPressed = 0;
    private float moveSpeed = 5.0f; // Movement speed
    
    // Sensitivity variables
    private float wasdSensitivity = 0.1f; // WASD movement sensitivity
    private float mouseWheelSensitivity = 20.0f; // Mouse wheel Z movement sensitivity
    private float mouseRotationSensitivity = 0.2f; // Mouse rotation sensitivity (2x faster)
    
    // Mouse wheel for Z movement
    private float scrollOffset = 0.0f;
    
    // Reference to gravity simulator
    private GravitySimulator simulator;
    private GravityUI ui;

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

        // Create the window
        window = glfwCreateWindow(width, height, "Gravity Simulator 3D", NULL, NULL);
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
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                // Toggle cursor mode on escape - for debugging/menu access
                int currentMode = glfwGetInputMode(window, GLFW_CURSOR);
                if (currentMode == GLFW_CURSOR_DISABLED) {
                    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                } else {
                    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                    firstMouse = true; // Reset mouse tracking
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

            xoffset *= mouseRotationSensitivity;
            yoffset *= mouseRotationSensitivity;

            yaw += xoffset;
            pitch += yoffset;

            // Constrain pitch to prevent camera flipping
            if (pitch > 89.0f)
                pitch = 89.0f;
            if (pitch < -89.0f)
                pitch = -89.0f;

            updateCameraDirection();
        });
        
        // Mouse wheel callback for Z movement
        glfwSetScrollCallback(window, (window, xoffset, yoffset) -> {
            scrollOffset += yoffset * 0.1f;
            // Move in the direction the camera is facing
            Vector3f moveDirection = new Vector3f(cameraFront).mul((float)(yoffset * mouseWheelSensitivity));
            cameraPos.add(moveDirection);
        });
        
        // Window resize callback
        glfwSetFramebufferSizeCallback(window, (window, width, height) -> {
            this.width = width;
            this.height = height;
            glViewport(0, 0, width, height);
        });
    }
    
    private void updateCameraDirection() {
        Vector3f direction = new Vector3f();
        direction.x = (float)(java.lang.Math.cos(java.lang.Math.toRadians(yaw)) * java.lang.Math.cos(java.lang.Math.toRadians(pitch)));
        direction.y = (float)java.lang.Math.sin(java.lang.Math.toRadians(pitch));
        direction.z = (float)(java.lang.Math.sin(java.lang.Math.toRadians(yaw)) * java.lang.Math.cos(java.lang.Math.toRadians(pitch)));
        cameraFront = direction.normalize();
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
        System.out.println("Initial camera position: " + cameraPos.x + ", " + cameraPos.y + ", " + cameraPos.z);
        System.out.println("Initial zoom: " + Settings.getInstance().getZoom());
        System.out.println("Initial shift: " + java.util.Arrays.toString(Settings.getInstance().getShift()));
        System.out.println("Camera front: " + cameraFront.x + ", " + cameraFront.y + ", " + cameraFront.z);

        // Enable depth testing for 3D
        glEnable(GL_DEPTH_TEST);
        
        // Set the clear color to dark gray
        glClearColor(0.2f, 0.2f, 0.2f, 1.0f);

        System.out.println("Starting render loop...");
        
        while (!glfwWindowShouldClose(window)) {
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

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
        
        System.out.println("Render loop ended");
    }

    
    private void processMovement() {
        // Handle relative camera movement based on key states
        Vector3f moveDirection = new Vector3f();
        float moveSpeed = 5.0f * wasdSensitivity;
        
        // Calculate right vector (perpendicular to forward and up)
        Vector3f right = new Vector3f(cameraFront).cross(cameraUp).normalize();
        
        // Check key states and calculate relative movement
        for (GravityUI.KeyEvent event : ui.keyEvents) {
            if (event.pressed) {
                switch (event.key) {
                    case GLFW.GLFW_KEY_W: // Forward in camera direction
                        moveDirection.add(new Vector3f(cameraFront).mul(moveSpeed));
                        break;
                    case GLFW.GLFW_KEY_S: // Backward from camera direction
                        moveDirection.sub(new Vector3f(cameraFront).mul(moveSpeed));
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
            cameraPos.add(moveDirection);
            
            // Update Settings to reflect new camera position
            Settings.getInstance().setShift(new double[] {cameraPos.x, cameraPos.y, cameraPos.z});
        }
        
        // Run other UI key functions (non-movement controls)
        for (GravityUI.KeyEvent event : ui.keyEvents) {
            if (event.pressed && event.key != GLFW.GLFW_KEY_W && event.key != GLFW.GLFW_KEY_A && 
                event.key != GLFW.GLFW_KEY_S && event.key != GLFW.GLFW_KEY_D && 
                event.key != GLFW.GLFW_KEY_Q && event.key != GLFW.GLFW_KEY_E) {
                event.action.run();
            }
        }
    }
    
    private void setupProjection() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        
        // Use a simpler perspective setup
        float fov = 45.0f;
        float aspect = (float) width / (float) height;
        float near = 0.1f;
        float far = 1000000.0f; // Increase far plane for large simulations
        
        // Simple perspective matrix
        float fH = (float) java.lang.Math.tan(java.lang.Math.toRadians(fov) / 2.0) * near;
        float fW = fH * aspect;
        
        glFrustum(-fW, fW, -fH, fH, near, far);
    }
    
    private void setupCamera() {
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        
        // Get camera position from Settings
        Vector3f eye = new Vector3f(cameraPos);
        Vector3f center;
        Vector3f up = new Vector3f(cameraUp);
        
        // Handle follow mode
        if (Settings.getInstance().isFollow() && simulator != null) {
            // Get reference point from simulator
            int[] reference = simulator.getReference(true);
            center = new Vector3f(reference[0], reference[1], reference[2]);
        } else {
            // Look in the direction the camera is facing
            center = new Vector3f(eye).add(cameraFront);
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
        if (simulator == null) {
            // Draw a single test planet at origin
            glPushMatrix();
            glColor3f(0.0f, 0.5f, 1.0f); // Blue
            drawSphere(0, 0, 0, 50.0f, 16); // radius 50, 16 segments
            glPopMatrix();
            return;
        }
        
        // Get the render buffer read lock for thread-safe access
        ReentrantReadWriteLock.ReadLock readLock = simulator.getRenderBufferReadLock();
        readLock.lock();
        try {
            // Get thread-safe snapshot of chunks and planets from render buffer
            ArrayList<Chunk> chunks = simulator.getRenderBuffer().getChunks(); // Already returns a copy
            int chunkC = 0;
            System.out.println("Drawing " + chunks.size() + " chunks");
            for (Chunk chunk : chunks) {
                if (chunkC<10) {
                    System.out.print("Chunk: " + chunk.center.x + ", " + chunk.center.y + ", " + chunk.center.z + " has " + chunk.planets.size() + " planets");
                }

                chunkC++;
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
                    
                    // Scale radius for visibility
                    float scaledRadius = radius * 100; // Scale up for visibility
                    
                    // Draw sphere at planet position
                    drawSphere(x, y, z, scaledRadius, 12);
                    
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
        glOrtho(0, width, height, 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        // Disable depth testing for crosshair
        glDisable(GL_DEPTH_TEST);
        
        // Set crosshair color (white)
        glColor3f(1.0f, 1.0f, 1.0f);
        glLineWidth(2.0f);
        
        // Calculate center of screen
        float centerX = width / 2.0f;
        float centerY = height / 2.0f;
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
    
    public int[] getScreenLocation(double simX, double simY) {
		int[] followLocation = simulator.getReference(Settings.getInstance().isFollow());
		int screenWidth = width;
		int screenHeight = height;
		
		// Convert simulation coordinates to screen coordinates
		return new int[] {
			(int)(Settings.getInstance().getZoom() * (simX - followLocation[0]) + (screenWidth/2 + Settings.getInstance().getShift()[0])),
			(int)(Settings.getInstance().getZoom() * (simY - followLocation[1]) + (screenHeight/2 + Settings.getInstance().getShift()[1]))
		};
	}

	public int[] getSimulationLocation(double screenX, double screenY) {
		int[] followLocation = simulator.getReference(Settings.getInstance().isFollow());
		int screenWidth = width;
		int screenHeight = height;
		
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