package com.grumbo.simulation;

import org.lwjgl.*;
import org.joml.Matrix4f;

import com.grumbo.gpu.*;
import java.nio.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

import static org.lwjgl.opengl.GL43C.*;

public class GPUSimulation {
    

    private BarnesHut barnesHut;
    private Render render;


    private Render.RenderMode renderMode;
    private final ConcurrentLinkedQueue<GPUCommands.GPUCommand> commandQueue;

    private ArrayList<Planet> planets;

    private boolean debug;
    private OpenGLWindow openGlWindow;
    public GPUSimulation(OpenGLWindow openGlWindow, ArrayList<Planet> planets, Render.RenderMode renderMode, boolean debug) {
        this.planets = planets;
        this.debug = debug;
        this.renderMode = renderMode;
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

        if (state == OpenGLWindow.State.FRAME_ADVANCE && openGlWindow.advanceFrame()) {
            barnesHut.step();
            render.render(barnesHut.getOutputSSBO(), state);
            openGlWindow.setAdvanceFrame(false);
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

    public void setMvp(FloatBuffer mvp) {
        render.setMvp(mvp);
    }
    public void setCameraPos(float x, float y, float z) {
        render.setCameraPos(x, y, z);
    }
    public void setDistanceRange(float nearDist, float farDist) {
        render.setDistanceRange(nearDist, farDist);
    }

    public void uploadPlanetsData(List<Planet> planets) {
        barnesHut.uploadPlanetsData(planets);
    }
    public void resizeBuffersAndUpload(List<Planet> planets) {
        barnesHut.resizeBuffersAndUpload(planets);
    }
    





    /* --------- Cleanup --------- */
    public void cleanupEmbedded() {

        barnesHut.cleanup();
        render.cleanup();
        // Minimal cleanup of GL objects created in embedded mode

        
    }

    
    
    private void checkGLError(String operation) {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            System.err.println("OpenGL Error after " + operation + ": " + error);
        }
    }


    public ArrayList<Planet> getPlanets() {
        return planets;
    }
    



    
}