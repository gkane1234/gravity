package com.grumbo.simulation;

import java.nio.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;


import static org.lwjgl.opengl.GL43C.*;

public class GPUSimulation {
    

    private BarnesHut barnesHut;
    private Render render;



    private final ConcurrentLinkedQueue<GPUCommands.GPUCommand> commandQueue;

    private ArrayList<Planet> planets;

    private OpenGLWindow openGlWindow;
    public GPUSimulation(OpenGLWindow openGlWindow, ArrayList<Planet> planets, Render.RenderMode renderMode, boolean debug) {
        this.planets = planets;
        this.commandQueue = new ConcurrentLinkedQueue<>();
        this.barnesHut = new BarnesHut(this,debug);
        this.render = new Render(this,renderMode,debug);
        this.openGlWindow = openGlWindow;
    }
    
    public void init() {
        barnesHut.init();
        render.init();
    }

    public void step(OpenGLWindow.State state) {
        processCommands();
        if (state == OpenGLWindow.State.RUNNING) {
            barnesHut.step();
            render.render(barnesHut.getOutputSSBO(), state);
        }

        if (state == OpenGLWindow.State.PAUSED) {
            render.render(barnesHut.getOutputSSBO(), state);
        }

        if (state == OpenGLWindow.State.FRAME_ADVANCE) {
            if (openGlWindow.advanceFrame()) {
                barnesHut.step();
                openGlWindow.setAdvanceFrame(false);
            }
            render.render(barnesHut.getOutputSSBO(), state);
        }
    }
    
    public void processCommands() {
        GPUCommands.GPUCommand cmd;
        while ((cmd = commandQueue.poll()) != null) {
            cmd.run(this);
        }
    }
 
    
    /* --------- Helper functions --------- */
        
    public void enqueue(GPUCommands.GPUCommand command) {
        if (command != null) {
            commandQueue.offer(command);
        }
    }

    public int numBodies() {
        return planets.size();
    }



    public int currentBodies() {
        return 0;
        /*
        if (barnesHut == null || barnesHut.getOutputSSBO() == null) {
            return 0;
        }
        return barnesHut.getOutputSSBO().getHeaderAsInts()[0];
        */
    }

    public void setMvp(FloatBuffer mvp) {
        render.setMvp(mvp);
    }

    public void uploadPlanetsData(List<Planet> planets) {
        barnesHut.uploadPlanetsData(planets);
    }
    public void resizeBuffersAndUpload(List<Planet> planets) {
        barnesHut.resizeBuffersAndUpload(planets);
    }

    public String getPerformanceText() {
        return barnesHut.debugString;
    }
    





    /* --------- Cleanup --------- */
    public void cleanupEmbedded() {

        barnesHut.cleanup();
        render.cleanup();
        // Minimal cleanup of GL objects created in embedded mode

        
    }

    
    



    public ArrayList<Planet> getPlanets() {
        return planets;
    }
    



    
}