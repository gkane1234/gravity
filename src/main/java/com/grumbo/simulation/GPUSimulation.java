package com.grumbo.simulation;

import java.nio.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.io.BufferedWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.grumbo.debug.Debug;
import com.grumbo.record.Recording;
import com.grumbo.gpu.SSBO;
import static org.lwjgl.opengl.GL43.*;


public class GPUSimulation {
    
    private BoundedBarnesHut boundedBarnesHut;
    private Render render;
    private OpenGLWindow openGlWindow;

    private final ConcurrentLinkedQueue<GPUCommands.GPUCommand> commandQueue;

    private PlanetGenerator planetGenerator;

    public enum State {
        LOADING,
        RUNNING,
        PAUSED,
        FRAME_ADVANCE,
        STOPPED
    }

    private boolean debug = true;
    public State state = State.PAUSED;

    // Recording
    private Recording recording;
    private boolean isRecording = false;
    private int recordFrameIndex = 0;
    private int initialbodiesContained = 0;
    private Path recordDir;
    private BufferedWriter recordMetaWriter;

    private int currentBodies = 0;
    private int merged = 0;
    private int outOfBounds = 0;

    public GPUSimulation() {

        PlanetGenerator planetGenerator = collisionTest();
        System.out.println("Planet generator num planets: " + planetGenerator.getNumPlanets());


        Render.RenderMode renderMode = Render.RenderMode.IMPOSTOR_SPHERES_WITH_GLOW;
        boolean debug = true;

        this.openGlWindow = new OpenGLWindow(this); 
        this.planetGenerator = planetGenerator;
        this.initialbodiesContained = planetGenerator.getNumPlanets();
        float boundSize = 350_000;
        float[][] bounds = new float[][] {{-boundSize, -boundSize, -boundSize}, {boundSize, boundSize, boundSize}};

        this.boundedBarnesHut = new BoundedBarnesHut(this,debug,bounds);
        this.render = new Render(this,renderMode,debug);
        this.debug = debug;
        this.commandQueue = new ConcurrentLinkedQueue<>();
    }

    public GPUSimulation(ArrayList<Planet> planets, Render.RenderMode renderMode, boolean debug) {
        this.openGlWindow = new OpenGLWindow(this);
        this.planetGenerator = new PlanetGenerator(planets);
        this.commandQueue = new ConcurrentLinkedQueue<>();
        float[][] bounds = new float[][] {{-10000, -10000, -10000}, {10000, 10000, 10000}};
        this.boundedBarnesHut = new BoundedBarnesHut(this,debug,bounds);
        this.render = new Render(this,renderMode,debug);
        this.debug = debug;
        this.initialbodiesContained = planets.size();
    }

    public static PlanetGenerator createJumboSimulation() {
        ArrayList<Planet> planets = new ArrayList<>();
        Planet jumbo = new Planet(0,0,0,0,0,0,100000f,10);
        Planet jumbo2 = new Planet(0,0,-1000,0,0,0,100000f,1);
        planets.add(jumbo);
        planets.add(jumbo2);
        return new PlanetGenerator(planets);
    }

    public static PlanetGenerator createSeveralDisksSimulation() {
        int numDisks = 10;
        int[] numPlanetsRange = {100000, 500000};
        float[] radiusRangeLow = {100, 100};
        float[] stellarDensityRange = {0.05f, 0.1f};
        float[] mRange = {100, 1200};
        float[] densityRange = {1f, 1f};
        float[] centerX = {0, 100000};
        float[] centerY = {0, 100000};
        float[] centerZ = {0, 100000};
        float[] relativeVelocityX = {-100, 100};
        float[] relativeVelocityY = {-100, 100};
        float[] relativeVelocityZ = {-100, 100};
        float[] phiRange = {0, (float)Math.PI};
        float[] centerMassRange = {10000f, 1000000f};
        float[] centerDensityRange = {10f, 10f};
        float[] adherenceToPlaneRange = {0.8f,1f};
        float orbitalFactor = 1f;
        boolean giveOrbitalVelocity = true;
        return PlanetGenerator.createSeveralDisks(numDisks, numPlanetsRange, radiusRangeLow, stellarDensityRange, mRange, densityRange, centerX, centerY, centerZ, relativeVelocityX, relativeVelocityY, relativeVelocityZ, phiRange, centerMassRange, centerDensityRange, adherenceToPlaneRange, orbitalFactor, giveOrbitalVelocity);
    }

    public static PlanetGenerator createSeveralDisksAroundAnotherDiskSimulation() {

        int numDisks = 110;
        int[] numPlanetsRange = {250_000,250_000};
        float[] radiusRangeLow = {100, 100};
        float[] stellarDensityRange = {5f, 15f};
        float[] mRange = {100, 1200};
        float[] densityRange = {1f, 1f};
        float[] centerX = {-300000, 300000};
        float[] centerY = {-300000, 300000};
        float[] centerZ = {-300000, 300000};
        float[] relativeVelocityX = {-100, 100};
        float[] relativeVelocityY = {-100, 100};
        float[] relativeVelocityZ = {-100, 100};
        float[] phiRange = {0, (float)Math.PI};
        float[] centerMassRange = {10000f, 1000000f};
        float[] centerDensityRange = {10f, 10f};
        float[] adherenceToPlaneRange = {0.95f,1f};
        float orbitalFactor = 1f;
        boolean giveOrbitalVelocity = true;


        int centerDiskPlanets = 6_000_000;
        float[] centerDiskRadius = {100, 50000};
        float[] centerDiskLocation = {(centerX[0]+centerX[1])/2, (centerY[0]+centerY[1])/2, (centerZ[0]+centerZ[1])/2};
        float[] centerDiskRelativeVelocity = {0, 0, 0};
        float centerDiskPhi = (float)(Math.PI/2);
        float centerDiskCenterMass = 1000000000f;
        float centerDiskCenterDensity = 10f;
        float centerDiskAdherenceToPlane = 0.99f;
        float centerDiskOrbitalFactor = 1f;
        boolean centerDiskGiveOrbitalVelocity = true;

        PlanetGenerator pg = PlanetGenerator.makeNewRandomDisk(centerDiskPlanets, centerDiskRadius, mRange, densityRange, centerDiskLocation, centerDiskRelativeVelocity, centerDiskPhi, centerDiskCenterMass, centerDiskCenterDensity, centerDiskAdherenceToPlane, centerDiskOrbitalFactor, false,centerDiskGiveOrbitalVelocity);
        
        pg= new PlanetGenerator(pg, PlanetGenerator.createSeveralDisks(numDisks, numPlanetsRange, radiusRangeLow, stellarDensityRange, mRange, densityRange, centerX, centerY, centerZ, relativeVelocityX, relativeVelocityY, relativeVelocityZ, phiRange, centerMassRange, centerDensityRange, adherenceToPlaneRange, orbitalFactor, giveOrbitalVelocity));
        return pg;
    }
    



    // public static PlanetGenerator createDiskSimulation() {
    //     PlanetGenerator pg = new PlanetGenerator();
    //     float[] radius = {100, 100000};
    //     float[] mRange = {10, 120};
    //     float[] densityRange = {0.1f, 0.1f};
    //     //Planet center = new Planet(1,0,0,0,0,0,100000);
    //     //planets.addAll(Planet.makeNewRandomDisk(1_000_000, radius, mRange, (float)(java.lang.Math.PI/2), true, true, center));
    //     pg.add(PlanetGenerator.makeNewRandomDisk(1_000_00, radius, mRange, densityRange, 
    //                 new float[] {0,0,0}, new float[] {0.1f,0,0},(float)(-java.lang.Math.PI*Math.random()),
    //                  100000000f,100f,
    //                  0.98f,1f,false, true));
    //     pg.add(PlanetGenerator.makeNewRandomDisk(1_000_00, radius, mRange, densityRange, 
    //                 new float[] {0,100000,0}, new float[] {0,0.1f,0},(float)(java.lang.Math.PI*Math.random()), 
    //                 100000000f,100f,
    //                 0.98f,1f,false, true));
    //     pg.add(PlanetGenerator.makeNewRandomDisk(1_000_00, radius, mRange, densityRange, 
    //     new float[] {0,-100000,0}, new float[] {0.1f,-1f,0},(float)(-java.lang.Math.PI*Math.random()),
    //         100000000f,100f,
    //         0.98f,1f,false, true));
    //     pg.add(PlanetGenerator.makeNewRandomDisk(1_000_00, radius, mRange, densityRange, 
    //                 new float[] {0,100000,100000}, new float[] {0,0.1f,-0.3f},(float)(java.lang.Math.PI*Math.random()), 
    //                 100000000f,100f,
    //                 0.98f,1f,false, true));
    //     return pg;
    // }

    // public static PlanetGenerator createBoxSimulation() {
    //     PlanetGenerator pg = new PlanetGenerator();
    //     float[] xRange = {-40000, 40000};
    //     float[] yRange = {-40000, 40000};
    //     float[] zRange = {-40000, 40000};
    //     float[] xVRange = {-0, 0};
    //     float[] yVRange = {-0, 0};
    //     float[] zVRange = {-0, 0};
    //     float[] mRange = {10, 10000};
    //     float[] densityRange = {1, 1};
    //     Planet center = new Planet(0, 0, 0, 0, 0, 0, 10000);
    //     //Planet center2 = new Planet(100,0,0,0,0,0,10);
    //     pg.add(PlanetGenerator.makeNewRandomBox(30, xRange, yRange, zRange, xVRange, yVRange, zVRange, mRange, densityRange));
    //     pg.add(center);
    //     //planets.add(center2);

    //     return pg;
    // }

    public static PlanetGenerator collisionTest() {
        ArrayList<Planet> newPlanets = new ArrayList<>();
        int numAlive = 30_000_000;
        for (int i = 0; i < numAlive; i++) {
            newPlanets.add(new Planet((float)(10000*java.lang.Math.random()), (float)(10000*java.lang.Math.random()), (float)(10000*java.lang.Math.random()), 0, 0, 0, 100));
        }

        Collections.shuffle(newPlanets);
        return new PlanetGenerator(newPlanets);
    }

    public static PlanetGenerator twoPlanets() {
        ArrayList<Planet> newPlanets = new ArrayList<>();
        newPlanets.add(new Planet(0, 0, 0, 0, 0, 0, 100));
        newPlanets.add(new Planet(20, 0, 0, 0, 0, 0, 100));
        newPlanets.add(new Planet(-20, 0, 0, 0, 0, 0, 100));
        return new PlanetGenerator(newPlanets);
    }

    private void init() {
        openGlWindow.init();
        boundedBarnesHut.init();
        render.init();
    }

        /**
     * Check for OpenGL errors.
     */
    private void checkGLError(String operation) {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            System.err.println("OpenGL Error after " + operation + ": " + error);
        }
    }

    private int frame = 0;

    public void run() {
        init();
        while (state != State.STOPPED) {
            step();
            openGlWindow.step();
            checkGLError("after openGlWindow.step");
            Debug.addDebugToFile(frame);
            frame++;
        }
        cleanupEmbedded();
    }

    public void step() {
        checkGLError("before step");
        processCommands();
        checkGLError("after processCommands");
        if (state == State.RUNNING) {
            boundedBarnesHut.step();
            checkGLError("after boundedBarnesHut.step");
            render.render(boundedBarnesHut.getOutputSSBO(), state);
            checkGLError("after render.render");
            captureIfRecording();
        }

        if (state == State.PAUSED) {
            render.render(boundedBarnesHut.getOutputSSBO(), state);
            checkGLError("after render.render");
        }

        if (state == State.FRAME_ADVANCE) {
            checkGLError("after boundedBarnesHut.step");
            boundedBarnesHut.step();
            render.render(boundedBarnesHut.getOutputSSBO(), state);
            checkGLError("after render.render");
            captureIfRecording();
            state = State.PAUSED;
        }
    }

    private void captureIfRecording() {
        if (!isRecording || recording == null || !recording.isRunning()) return;
        recording.capture(recordFrameIndex);
        if (recordMetaWriter != null) {
            try {
                recordMetaWriter.write("frame " + recordFrameIndex);
                recordMetaWriter.newLine();
                recordMetaWriter.flush();
            } catch (IOException e) {
                System.err.println("Failed writing recording metadata: " + e.getMessage());
            }
        }
        recordFrameIndex++;
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
        return planetGenerator.getNumPlanets();
    }



    public void updateCurrentBodies() { //Note: this is EXTREMELY slow.

        if (boundedBarnesHut == null || boundedBarnesHut.getValuesSSBO() == null) {
            currentBodies = numBodies();

            return;
        }
        SSBO valuesSSBO = boundedBarnesHut.getValuesSSBO();
        valuesSSBO.refreshCache();
        Object[][] header = valuesSSBO.getData(new String[] {
            "numBodies",
            "merged",
            "outOfBounds"
        });

        System.out.println("Header: " + Arrays.toString(header));

        currentBodies = (Integer)header[0][0];
        merged = (Integer)header[1][0];
        outOfBounds = (Integer)header[2][0];

    }

    public int currentBodies() { 
        return currentBodies;
    }

    public int merged() {
        return merged;
    }

    public int outOfBounds() {
        return outOfBounds;
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
        return boundedBarnesHut.getInternalNodesSSBO();
    }

    public com.grumbo.gpu.SSBO barnesHutValuesSSBO() {
        return boundedBarnesHut.getValuesSSBO();
    }


    // public void uploadPlanetsData(PlanetGenerator planetGenerator) {
    //     boundedBarnesHut.uploadPlanetsData(planetGenerator);
    // }

    public void resizeBuffersAndUpload(PlanetGenerator planetGenerator) {
        this.planetGenerator = planetGenerator;
        boundedBarnesHut.reInitComputeSSBOsAndSwappingBuffers();
    }


    public String getPerformanceText() {
        return boundedBarnesHut.debugString;
    }

    public int getSteps() {
        return boundedBarnesHut.getSteps();
    }

    public PlanetGenerator getPlanetGenerator() {
        return planetGenerator;
    }

    public void toggleRegions() {
        render.showRegions = !render.showRegions;
    }

    public void toggleCrosshair() {
        openGlWindow.setShowCrosshair(!openGlWindow.getShowCrosshair());
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

        boundedBarnesHut.cleanup();
        render.cleanup();
        // Minimal cleanup of GL objects created in embedded mode
        if (isRecording) {
            stopRecording();
        }

        
    }

    // public ArrayList<Planet> getPlanets() {
    //     return planets;
    // }

    /* --------- Recording control --------- */
    public void toggleRecording() {
        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        try {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            recordDir = Path.of("captures", "rec_" + ts);
            Files.createDirectories(recordDir);
            int width = Settings.getInstance().getWidth();
            int height = Settings.getInstance().getHeight();
            recording = new Recording();
            recording.start(recordDir, width, height, true, "frame", false, true, 2);
            recordFrameIndex = 0;
            Path meta = recordDir.resolve("metadata.txt");
            recordMetaWriter = Files.newBufferedWriter(meta, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            recordMetaWriter.write("bodies " + initialbodiesContained);
            recordMetaWriter.newLine();
            recordMetaWriter.write("frames");
            recordMetaWriter.newLine();
            recordMetaWriter.flush();
            isRecording = true;
            System.out.println("Recording started: " + recordDir.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to start recording: " + e.getMessage());
        }
    }

    private void stopRecording() {
        isRecording = false;
        if (recording != null && recording.isRunning()) {
            recording.stop();
        }
        if (recordMetaWriter != null) {
            try { recordMetaWriter.flush(); recordMetaWriter.close(); } catch (IOException ignored) {}
            recordMetaWriter = null;
        }
        System.out.println("Recording stopped.");
    }

    
    
}