package com.grumbo.simulation;

import java.nio.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.ConcurrentLinkedQueue;


public class GPUSimulation {
    

    private BarnesHut barnesHut;
    private Render render;
    private OpenGLWindow openGlWindow;

    private final ConcurrentLinkedQueue<GPUCommands.GPUCommand> commandQueue;

    private ArrayList<Planet> planets;

    public enum State {
        LOADING,
        RUNNING,
        PAUSED,
        FRAME_ADVANCE,
        STOPPED
    }


    private boolean debug = true;
    public State state = State.FRAME_ADVANCE;
    public GPUSimulation() {

        this(createDiskSimulation(), Render.RenderMode.IMPOSTOR_SPHERES_WITH_GLOW, true);
    }

    public GPUSimulation(ArrayList<Planet> planets, Render.RenderMode renderMode, boolean debug) {
        this.openGlWindow = new OpenGLWindow(this);
        this.planets = planets;
        this.commandQueue = new ConcurrentLinkedQueue<>();
        this.barnesHut = new BarnesHut(this,debug);
        this.render = new Render(this,renderMode,debug);
        this.debug = debug;
    }

    public static ArrayList<Planet> createJumboSimulation() {
        ArrayList<Planet> planets = new ArrayList<>();
        Planet jumbo = new Planet(0,0,0,0,0,0,100000f,1);
        Planet jumbo2 = new Planet(0,0,-1000,0,0,0,100000f,1);
        planets.add(jumbo);
        planets.add(jumbo2);
        return planets;
    }

    public static ArrayList<Planet> createDiskSimulation() {
        ArrayList<Planet> planets = new ArrayList<>();
        float[] radius = {100, 100000};
        float[] mRange = {1000, 12000};
        float[] densityRange = {1, 1};
        //Planet center = new Planet(1,0,0,0,0,0,100000);
        //planets.addAll(Planet.makeNewRandomDisk(1_000_000, radius, mRange, (float)(java.lang.Math.PI/2), true, true, center));
        //planets.addAll(Planet.makeNewRandomDisk(1_000_000, radius, mRange, (float)(0), false, true, 100000));
        planets.addAll(Planet.makeNewRandomDisk(500_000, radius, mRange, densityRange, new float[] {0,1000,0}, new float[] {0,0,0},0, 10000000f,1f,0.98f,0.4f,false, true));
        Planet jumbo = new Planet(0,0,0,0,0,0,1000000000f,1);
        planets.add(jumbo);
        //planets.addAll(Planet.makeNewRandomDisk(500_000, radius, mRange, new float[] {0,0,0}, new float[] {0,0,0}, (float)(java.lang.Math.PI/2), 10000000f,0.98f,0.9f,0.9f,false, true));
        //planets.addAll(Planet.makeNewRandomDisk(1_000_000, radius, mRange, (float)(0.2), false, true, 100000));
        //planets.add(center);

        return planets;
    }

    

    public static ArrayList<Planet> createBoxSimulation() {
        ArrayList<Planet> planets = new ArrayList<>();
        float[] xRange = {-4000, 4000};
        float[] yRange = {-4000, 4000};
        float[] zRange = {-4000, 4000};
        float[] xVRange = {-0, 0};
        float[] yVRange = {-0, 0};
        float[] zVRange = {-0, 0};
        float[] mRange = {10, 10000};
        float[] densityRange = {1, 1};
        Planet center = new Planet(0, 0, 0, 0, 0, 0, 10000);
        //Planet center2 = new Planet(100,0,0,0,0,0,10);
        planets = Planet.makeNewRandomBox(1_000_000, xRange, yRange, zRange, xVRange, yVRange, zVRange, mRange, densityRange);
        planets.add(center);
        //planets.add(center2);

        return planets;
    }

    public static ArrayList<Planet> collisionTest() {
        ArrayList<Planet> newPlanets = new ArrayList<>();
        int numAlive = 1000;
        for (int i = 0; i < numAlive; i++) {
            newPlanets.add(new Planet((float)(100*java.lang.Math.random()), (float)(100*java.lang.Math.random()), (float)(100*java.lang.Math.random()), 0, 0, 0, 100));
        }

        Collections.shuffle(newPlanets);
        return newPlanets;
    }

    public static ArrayList<Planet> twoPlanets() {
        ArrayList<Planet> newPlanets = new ArrayList<>();
        newPlanets.add(new Planet(0, 0, 0, 0, 0, 0, 100));
        newPlanets.add(new Planet(20, 0, 0, 0, 0, 0, 100));
        newPlanets.add(new Planet(-20, 0, 0, 0, 0, 0, 100));
        return newPlanets;
    }



    
    private void init() {
        openGlWindow.init();
        barnesHut.init();
        render.init();
    }

    public void run() {
        init();
        while (state != State.STOPPED) {
            step();
            openGlWindow.step();
        }
        cleanupEmbedded();
    }

    public void step() {
        processCommands();
        if (state == State.RUNNING) {
            barnesHut.step();
            render.render(barnesHut.getOutputSSBO(), state);
        }

        if (state == State.PAUSED) {
            render.render(barnesHut.getOutputSSBO(), state);
        }

        if (state == State.FRAME_ADVANCE) {
            barnesHut.step();
            render.render(barnesHut.getOutputSSBO(), state);
            state = State.PAUSED;
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

    public void setCameraToClip(FloatBuffer cameraToClip) {
        render.setCameraToClip(cameraToClip);
    }

    public void setModelView(FloatBuffer modelView) {
        render.setModelView(modelView);
    }

    // Expose nodes SSBO for regions rendering
    public com.grumbo.gpu.SSBO barnesHutNodesSSBO() {
        return barnesHut.getNodesSSBO();
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

    public void toggleRegions() {
        render.showRegions = !render.showRegions;
    }

    public void pause() {
        state = State.PAUSED;
    }

    public void resume() {
        state = State.RUNNING;
    }

    public void stop() {
        state = State.STOPPED;
    }

    public void frameAdvance() {
        state = State.FRAME_ADVANCE;
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