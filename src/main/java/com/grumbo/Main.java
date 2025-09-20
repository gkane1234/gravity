package com.grumbo;

import com.grumbo.simulation.GPUSimulation;

/**
 * Main - Application Entry Point
 * =============================
 * Creates and starts the GravitySimulator, which manages the simulation and UI.
 * The Simulation is started by calling the GPUSimulation.run() method.
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */


public class Main {
    public static void main(String[] args) {
        GPUSimulation gpuSimulation = new GPUSimulation();
        gpuSimulation.run();
        
    }
    
}
