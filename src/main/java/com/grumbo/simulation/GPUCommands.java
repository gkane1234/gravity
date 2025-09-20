package com.grumbo.simulation;

/**
 * GPUCommands class for creating GPU commands to be executed on the GPU with command queue structure.
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public final class GPUCommands {

    // Command queue processed on GL thread each frame
    @FunctionalInterface
    public interface GPUCommand {
        void run(GPUSimulation sim);
    }

    private GPUCommands() {}
    /**
     * Updates the dt.
     * @param newDt the new dt
     * @return the GPU command to update the dt
     */
    public static GPUCommand updateDt(float newDt) {
        return sim -> Settings.getInstance().setDt(newDt);
    }

    /**
     * Updates the softening.
     * @param newSoftening the new softening
     * @return the GPU command to update the softening
     */
    public static GPUCommand updateSoftening(float newSoftening) {
        return sim -> Settings.getInstance().setSoftening(newSoftening);
    }


    // // Re-upload existing planet data (same size)
    // public static GPUCommand uploadPlanetData(java.util.List<Planet> planets) {
    //     return sim -> sim.uploadPlanetsData(planets);
    // }

    // // Resize SSBOs when count changes, then upload
    // public static GPUCommand resizeAndUploadPlanets(java.util.List<Planet> planets) {
    //     return sim -> sim.resizeBuffersAndUpload(planets);
    // }

}
