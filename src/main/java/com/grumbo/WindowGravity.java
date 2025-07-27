package com.grumbo;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;

/**
 * WindowGravity - Main Application Window & Input Handler
 * ======================================================
 * Creates the main window, handles user input, and manages the simulation loop.
 * Now works with GravitySimulator as the primary controller that contains GravityFrame.
 */
public class WindowGravity extends JFrame implements KeyListener {
    
    public long tickSpeed;
    private static final double ZSTEP = 100;
    public GravitySimulator simulator;
    
    public WindowGravity() throws Exception {
        super("Gravity Simulator Menu V. 1.0.0");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(Global.width, Global.height);
        setVisible(true);
        
        // Create the gravity simulator (which contains the render frame)
        this.simulator = new GravitySimulator();
        add(simulator.getRenderFrame());
        
        // Add window listener to properly shut down threads when window closes
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.out.println("Shutting down thread pool...");
                simulator.shutdown();
                System.exit(0);
            }
        });
        
        // Add initial planets through the simulator
        simulator.addPlanetToCorrectChunk(new Planet(-8, 0, 1.0, 0, 100));
        simulator.addPlanetToCorrectChunk(new Planet(8, 0, -1, 0, 100));
        
        simulator.addPlanetsToCorrectChunk(Planet.makeNew(10000, 
            new double[] {-1000000, 1000000}, new double[] {-1000000, 1000000},
            new double[] {-10, 10}, new double[] {-10, 10}, new double[] {1000000, 2000000}));
         
        simulator.getRenderFrame().setVisible(true);
        addKeyListener(this);
        setVisible(true);
        
        tickSpeed = (long)(1);
        long wait = 0;
        
        // Main simulation loop
        while(true) {
            long b4 = System.currentTimeMillis();
            
            // Run physics simulation (which triggers rendering)
            simulator.chunkTick(wait);
            
            wait = (tickSpeed - (System.currentTimeMillis() - b4));
            if (wait > 0) {
                try {

                    Thread.sleep(wait/1000);
                } catch (InterruptedException e) {
                    // Interrupted sleep - continue
                }
            }
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        if (e.getKeyChar() == '=') {
            simulator.changeZoom(Global.zoom * 1.1);
        }
        else if (e.getKeyChar() == '-') {
            simulator.changeZoom(Global.zoom / 1.1);
        }
        else if (e.getKeyChar() == 'w') {
            simulator.moveCamera(new double[] {0, ZSTEP * Global.zoom});
        }
        else if (e.getKeyChar() == 'a') {
            simulator.moveCamera(new double[] {ZSTEP * Global.zoom, 0});
        }
        else if (e.getKeyChar() == 's') {
            simulator.moveCamera(new double[] {0, -ZSTEP * Global.zoom});
        }
        else if (e.getKeyChar() == 'd') {
            simulator.moveCamera(new double[] {-ZSTEP * Global.zoom, 0});
        }
        else if (e.getKeyChar() == 'p' || e.getKeyChar() == 'P') {
            // Toggle performance stats through the simulator
            simulator.showPerformanceStats = !simulator.showPerformanceStats;
            
            System.out.println("Performance stats " + (simulator.showPerformanceStats ? "enabled" : "disabled"));
            
            // Reset counters when toggling on
            if (simulator.showPerformanceStats) {
                simulator.resetPerformanceCounters();
                System.out.println("Performance counters reset");
                System.out.println("Using " + Global.numThreads + " threads for physics calculations");
            }
        }
        else if (e.getKeyChar() == 'i' || e.getKeyChar() == 'I') {
            // Display detailed performance information
            System.out.println("\n" + simulator.getDisplayPerformanceStats() + "\n");
        }
        else if (e.getKeyChar() == 'f') {
            simulator.toggleFollow();
        }
        else if (e.getKeyChar() == '[') {
            incrementWait(false);
            System.out.println(tickSpeed);
        }
        else if (e.getKeyChar() == ']') {
            incrementWait(true);
            System.out.println(tickSpeed);
        }
    }

    /**
     * Adjusts simulation speed (tick delay)
     */
    private boolean incrementWait(boolean inc) {
        double log = Math.log10(tickSpeed);
        
        if (inc) {
            int digits = (int)(Math.floor(log));
            tickSpeed += Math.pow(10, digits);
            if (tickSpeed == 0) tickSpeed += 1;
        }
        else {
            if (tickSpeed == 0) return false;
            
            int digits = (int)(Math.floor(log) + (log > Math.floor(log) ? 0 : -1));
            tickSpeed -= Math.pow(10, digits);
        }
        
        return true;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        // Not implemented
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // Not implemented
    }
}
