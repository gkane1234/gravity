package com.grumbo;

import com.grumbo.simulation.GPUSimulation;

/**
 * Main - Application Entry Point
 * =============================
 * Creates and starts the GravitySimulator, which manages the simulation and UI.
 */

 //Known issues:
 // galaxy generation seems to tear itself apart sometimes
 // the name of fixed ssbos is overwritten by the swapping ssbos
public class Main {
    public static void main(String[] args) {
        GPUSimulation gpuSimulation = new GPUSimulation();
        gpuSimulation.run();
        
    }
    
}
