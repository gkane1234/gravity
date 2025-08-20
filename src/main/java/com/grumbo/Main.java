package com.grumbo;

import com.grumbo.simulation.OpenGLWindow;

/**
 * Main - Application Entry Point
 * =============================
 * Creates and starts the GravitySimulator, which manages the simulation and UI.
 */

 //Known issues:
 // Make different types have the correct ui
 // Make these specific types of UI work with the UI map that is used
 // Fix string problem when setting a new value, actually fix all the problems wiht making sure that input is comprehensible
 
 // - Cant put chunk size too low or else it will freeze or lose correct velocity of planets
public class Main {
    public static void main(String[] args) {
        OpenGLWindow window = new OpenGLWindow();
        window.run();
    }
}
