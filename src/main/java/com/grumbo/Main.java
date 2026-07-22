package com.grumbo;

import com.grumbo.simulation.GPUSimulation;
import com.grumbo.simulation.Render;
import com.grumbo.simulation.Settings;
import com.grumbo.simulation.SimulationSetup;
import com.grumbo.ui.SetupScreen;

/**
 * Main - Application Entry Point
 * =============================
 * Setup menu -> simulation -> back to setup when the sim window closes.
 * Quit from the setup menu exits the app.
 */
public class Main {
    public static void main(String[] args) {
        while (true) {
            SimulationSetup.LaunchConfig config = SetupScreen.run();
            if (config == null) {
                System.out.println("Exiting.");
                return;
            }

            config.suggestedSettings.apply(Settings.getInstance());
            System.out.println("Suggested settings: " + config.suggestedSettings);

            GPUSimulation gpuSimulation = new GPUSimulation(
                config.generator,
                config.squareBounds,
                Render.RenderMode.IMPOSTOR_SPHERES_WITH_GLOW,
                false
            );
            System.out.println("Bodies: " + config.bodyCount + ", squareBounds: " + config.squareBounds);
            gpuSimulation.run();
            System.out.println("Returned to setup menu.");
        }
    }
}
