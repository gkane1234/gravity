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

            System.out.println("Launch: " + config.bodyCount + " bodies, " + config.squareBounds + " bounds");
            if (config.suggestedSettings != null) {
                System.out.println("Preset: " + config.suggestedSettings);
            }

            GPUSimulation gpuSimulation = new GPUSimulation(
                config.generator,
                config.squareBounds,
                Render.RenderMode.fromString(Settings.getInstance().getRenderMode()),
                false,
                config.suggestedSettings
            );
            System.out.println("Bodies: " + config.bodyCount + ", squareBounds: " + config.squareBounds);
            gpuSimulation.run();
            System.out.println("Returned to setup menu.");
        }
    }
}
