package com.grumbo;

/**
 * Main - Application Entry Point
 * =============================
 * Creates and starts the GravitySimulator, which manages the simulation and UI.
 */
public class Main {
    public static void main(String[] args) {
        GravitySimulator simulator = new GravitySimulator();
        simulator.start();
    }
}
