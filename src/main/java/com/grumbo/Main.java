package com.grumbo;

import com.grumbo.simulation.GPUSimulation;

/**
 * Main - Application Entry Point
 * =============================
 * Creates and starts the GravitySimulator, which manages the simulation and UI.
 */

 //Features to add:
 // Make a debug txt file that can output lots of data about the simulation with very specific parameters

 //Known issues:
 // galaxy generation seems to tear itself apart sometimes
 // the name of fixed ssbo objects is overwritten by the swapping ssbos
 // Radix sort dispatches with too many workgroups when the number of bodies goes down which matters a lot because of the amount of inop threads
 // Most shaders dispatch with too many workgroups when the number of bodies goes down
 // # merged, # lost to oob not calculated correctly at times (sometimes #lost is negative)
 // Radix bits cannot be changed from 4
public class Main {
    public static void main(String[] args) {
        GPUSimulation gpuSimulation = new GPUSimulation();
        gpuSimulation.run();
        
    }
    
}
