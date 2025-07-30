package com.grumbo;

import org.lwjgl.*;
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

        // Make the window visible
        glfwShowWindow(window);
    }
    
    private void setupCallbacks() {
        // Key callback
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE)
                glfwSetWindowShouldClose(window, true);
            
            ui.updateKeys(key, action);
        });
        
        // Mouse button callback for mouse wheel button
        glfwSetMouseButtonCallback(window, (window, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_MIDDLE) {
                mouseWheelPressed = (action == GLFW_PRESS);
                if (mouseWheelPressed) {
                    // Reset mouse tracking when starting to rotate
                    firstMouse = true;
                }
            }
        });
        
        // Mouse callback for camera rotation (only when mouse wheel is pressed)
        glfwSetCursorPosCallback(window, (window, xpos, ypos) -> {
            if (!mouseWheelPressed) return; // Only rotate when mouse wheel button is pressed
            
            if (firstMouse) {
                lastX = xpos;
                lastY = ypos;
                firstMouse = false;
            }

            double xoffset = xpos - lastX;
            double yoffset = lastY - ypos;
            lastX = xpos;
            lastY = ypos;

            xoffset *= mouseRotationSensitivity;
            yoffset *= mouseRotationSensitivity;

            yaw += xoffset;
            pitch += yoffset;

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
        System.out.println("Press SPACE to toggle between test cube and planets");

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

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
        
        System.out.println("Render loop ended");
    }

    
    private void processMovement() {
        Vector3f moveDirection = new Vector3f();
        
        // W/S: Move up/down (Y axis)
        //if (ui.keyEvents.get(ui.keyEvents.indexOf(GLFW.GLFW_KEY_W)).pressed) moveDirection.y += 1.0f; // Move up
        //if (ui.keyEvents.get(ui.keyEvents.indexOf(GLFW.GLFW_KEY_S)).pressed) moveDirection.y -= 1.0f; // Move down
        
        // A/D: Move left/right (X axis, but relative to camera orientation)
        Vector3f right = new Vector3f(cameraFront).cross(cameraUp).normalize();
        //if (ui.keyEvents.get(ui.keyEvents.indexOf(GLFW.GLFW_KEY_A)).pressed) moveDirection.sub(right); // Move left
        //if (ui.keyEvents.get(ui.keyEvents.indexOf(GLFW.GLFW_KEY_D)).pressed) moveDirection.add(right); // Move right

        if (moveDirection.length() > 0) {
            moveDirection.normalize();
            cameraPos.add(moveDirection.mul(moveSpeed * wasdSensitivity)); // Use WASD sensitivity
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
        
        // Simple camera setup
        Vector3f eye = new Vector3f(cameraPos);
        Vector3f center = new Vector3f(cameraPos).add(cameraFront);
        Vector3f up = new Vector3f(cameraUp);
        
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
        
        // Get thread-safe snapshot of chunks and planets
        ArrayList<Chunk> chunks = simulator.listOfChunks.getChunks(); // Already returns a copy
        
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
                
                // Scale radius for visibility
                float scaledRadius = radius * 100; // Scale up for visibility
                
                // Draw sphere at planet position
                drawSphere(x, y, z, scaledRadius, 12);
                
                glPopMatrix();
            }
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
    
    private void drawCube() {
        glBegin(GL_QUADS);
        
        // Front face (red)
        glColor3f(1.0f, 0.0f, 0.0f);
        glVertex3f(-0.5f, -0.5f,  0.5f);
        glVertex3f( 0.5f, -0.5f,  0.5f);
        glVertex3f( 0.5f,  0.5f,  0.5f);
        glVertex3f(-0.5f,  0.5f,  0.5f);
        
        // Back face (green)
        glColor3f(0.0f, 1.0f, 0.0f);
        glVertex3f(-0.5f, -0.5f, -0.5f);
        glVertex3f(-0.5f,  0.5f, -0.5f);
        glVertex3f( 0.5f,  0.5f, -0.5f);
        glVertex3f( 0.5f, -0.5f, -0.5f);
        
        // Top face (blue)
        glColor3f(0.0f, 0.0f, 1.0f);
        glVertex3f(-0.5f,  0.5f, -0.5f);
        glVertex3f(-0.5f,  0.5f,  0.5f);
        glVertex3f( 0.5f,  0.5f,  0.5f);
        glVertex3f( 0.5f,  0.5f, -0.5f);
        
        // Bottom face (yellow)
        glColor3f(1.0f, 1.0f, 0.0f);
        glVertex3f(-0.5f, -0.5f, -0.5f);
        glVertex3f( 0.5f, -0.5f, -0.5f);
        glVertex3f( 0.5f, -0.5f,  0.5f);
        glVertex3f(-0.5f, -0.5f,  0.5f);
        
        // Right face (magenta)
        glColor3f(1.0f, 0.0f, 1.0f);
        glVertex3f( 0.5f, -0.5f, -0.5f);
        glVertex3f( 0.5f,  0.5f, -0.5f);
        glVertex3f( 0.5f,  0.5f,  0.5f);
        glVertex3f( 0.5f, -0.5f,  0.5f);
        
        // Left face (cyan)
        glColor3f(0.0f, 1.0f, 1.0f);
        glVertex3f(-0.5f, -0.5f, -0.5f);
        glVertex3f(-0.5f, -0.5f,  0.5f);
        glVertex3f(-0.5f,  0.5f,  0.5f);
        glVertex3f(-0.5f,  0.5f, -0.5f);
        
        glEnd();
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
           // glVertex3f(screenPosLast[0], screenPosLast[1], screenPosNext[0], screenPosNext[1]);
           // tailLast = tailNext;
            }
        }
    }
} 