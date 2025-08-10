package com.grumbo;

/**
 * GravitySimulator3D - Integrates 3D OpenGL rendering with the gravity simulation
 * =================================================================================
 * This class runs both the physics simulation and 3D rendering in parallel threads.
 */

 //Get performance stats
 //Draw chunk boundaries
 //Draw tails
 //Draw mouse location
public class WindowGravity3D {
    
    private GravitySimulator simulator;
    private OpenGLWindow renderer;
    private boolean running = false;
    private Thread physicsThread;
    
    public WindowGravity3D() {
        // Create the physics simulator without UI
        simulator = createHeadlessSimulator();
        
        // Create the 3D renderer with reference to simulator
        renderer = new OpenGLWindow(simulator);
    }
    
    private GravitySimulator createHeadlessSimulator() {
        // Create simulator in headless mode (no 2D UI)
        return new GravitySimulator(this);
    }
    
    public void start() {
        running = true;
        
        // Start physics simulation in a separate thread with proper timing
        physicsThread = new Thread(() -> {
            System.out.println("Starting headless physics simulation...");
            runPhysicsLoop();
        });
        physicsThread.setDaemon(true);
        physicsThread.start();
        
        // Run 3D rendering on main thread (required for OpenGL)
        System.out.println("Starting 3D renderer...");
        renderer.run();
        
        // Stop physics when renderer closes
        running = false;
        if (physicsThread != null) {
            physicsThread.interrupt();
        }
        if (simulator != null) {
            simulator.shutdown();
        }
    }
    
    private void runPhysicsLoop() {
        long targetFrameTime = 16; // ~60 FPS (16ms per frame)
        
        while (running && !Thread.currentThread().isInterrupted()) {
            long frameStart = System.currentTimeMillis();
            
            try {
                // Run one physics tick
                if (simulator.isNotPaused()) {
                    simulator.chunkPhysicsTick();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            // Maintain consistent timing
            long frameTime = System.currentTimeMillis() - frameStart;
            long sleepTime = targetFrameTime - frameTime;
            
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        System.out.println("Physics loop ended");
    }
    
    public void stop() {
        running = false;
        if (simulator != null) {
            simulator.stop();
        }
        if (physicsThread != null) {
            physicsThread.interrupt();
        }
    }
} 