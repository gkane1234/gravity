package com.grumbo.simulation;

public final class GPUCommands {

    // Command queue processed on GL thread each frame
    @FunctionalInterface
    public interface GPUCommand {
        void run(GPUSimulation sim);
    }

    
    private GPUCommands() {}

    public static GPUCommand updateDt(float newDt) {
        return sim -> Settings.getInstance().setDt(newDt);
    }

    public static GPUCommand updateSoftening(float newSoftening) {
        return sim -> Settings.getInstance().setSoftening(newSoftening);
    }


    // Re-upload existing planet data (same size)
    public static GPUCommand uploadPlanetData(java.util.List<Planet> planets) {
        return sim -> sim.uploadPlanetsData(planets);
    }

    // Resize SSBOs when count changes, then upload
    public static GPUCommand resizeAndUploadPlanets(java.util.List<Planet> planets) {
        return sim -> sim.resizeBuffersAndUpload(planets);
    }

}
