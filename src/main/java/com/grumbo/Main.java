package com.grumbo;

/**
 * Main - Application Entry Point
 * =============================
 * Creates and starts the GravitySimulator, which manages the simulation and UI.
 */

 //Known issues:
 // - Cant put chunk size too low or else it will freeze or lose correct velocity of planets
public class Main {
    public static void main(String[] args) {
        WindowGravity3D window = new WindowGravity3D();
        window.start();
    }
}
