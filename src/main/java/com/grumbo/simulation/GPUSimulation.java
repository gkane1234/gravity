package com.grumbo.simulation;

import java.nio.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.io.BufferedWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.grumbo.record.Recording;


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

    // Recording
    private Recording recording;
    private boolean isRecording = false;
    private int recordFrameIndex = 0;
    private int initialBodyCount = 0;
    private Path recordDir;
    private BufferedWriter recordMetaWriter;

    public GPUSimulation() {

        ArrayList<Planet> planets = createSeveralDisksAroundAnotherDiskSimulation();
        Render.RenderMode renderMode = Render.RenderMode.IMPOSTOR_SPHERES_WITH_GLOW;
        boolean debug = true;

        this.openGlWindow = new OpenGLWindow(this); 
        this.planets = planets;
        this.initialBodyCount = planets.size();

        this.barnesHut = new BarnesHut(this,debug);
        this.render = new Render(this,renderMode,debug);
        this.debug = debug;
        this.commandQueue = new ConcurrentLinkedQueue<>();
    }

    public GPUSimulation(ArrayList<Planet> planets, Render.RenderMode renderMode, boolean debug) {
        this.openGlWindow = new OpenGLWindow(this);
        this.planets = planets;
        this.commandQueue = new ConcurrentLinkedQueue<>();
        this.barnesHut = new BarnesHut(this,debug);
        this.render = new Render(this,renderMode,debug);
        this.debug = debug;
        this.initialBodyCount = planets.size();
    }

    public static ArrayList<Planet> createJumboSimulation() {
        ArrayList<Planet> planets = new ArrayList<>();
        Planet jumbo = new Planet(0,0,0,0,0,0,100000f,10);
        Planet jumbo2 = new Planet(0,0,-1000,0,0,0,100000f,1);
        planets.add(jumbo);
        planets.add(jumbo2);
        return planets;
    }

    public static ArrayList<Planet> createSeveralDisksSimulation() {
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
        return Planet.createSeveralDisks(numDisks, numPlanetsRange, radiusRangeLow, stellarDensityRange, mRange, densityRange, centerX, centerY, centerZ, relativeVelocityX, relativeVelocityY, relativeVelocityZ, phiRange, centerMassRange, centerDensityRange, adherenceToPlaneRange, orbitalFactor, giveOrbitalVelocity);
    }

    public static ArrayList<Planet> createSeveralDisksAroundAnotherDiskSimulation() {

        int numDisks = 20;
        int[] numPlanetsRange = {25000, 75000};
        float[] radiusRangeLow = {100, 100};
        float[] stellarDensityRange = {5f, 15f};
        float[] mRange = {100, 1200};
        float[] densityRange = {1f, 1f};
        float[] centerX = {0, 300000};
        float[] centerY = {0, 300000};
        float[] centerZ = {0, 300000};
        float[] relativeVelocityX = {-10, 10};
        float[] relativeVelocityY = {-10, 10};
        float[] relativeVelocityZ = {-10, 10};
        float[] phiRange = {0, (float)Math.PI};
        float[] centerMassRange = {10000f, 1000000f};
        float[] centerDensityRange = {10f, 10f};
        float[] adherenceToPlaneRange = {0.8f,1f};
        float orbitalFactor = 1f;
        boolean giveOrbitalVelocity = true;


        int centerDiskPlanets = 600_000;
        float[] centerDiskRadius = {100, 50000};
        float[] centerDiskLocation = {(centerX[0]+centerX[1])/2, (centerY[0]+centerY[1])/2, (centerZ[0]+centerZ[1])/2};
        float[] centerDiskRelativeVelocity = {0, 0, 0};
        float centerDiskPhi = (float)(Math.PI/2);
        float centerDiskCenterMass = 100000000f;
        float centerDiskCenterDensity = 1f;
        float centerDiskAdherenceToPlane = 0.99f;
        float centerDiskOrbitalFactor = 1f;
        boolean centerDiskGiveOrbitalVelocity = true;

        ArrayList<Planet> planets = new ArrayList<>();
        planets.addAll(Planet.makeNewRandomDisk(centerDiskPlanets, centerDiskRadius, mRange, densityRange, centerDiskLocation, centerDiskRelativeVelocity, centerDiskPhi, centerDiskCenterMass, centerDiskCenterDensity, centerDiskAdherenceToPlane, centerDiskOrbitalFactor, false,centerDiskGiveOrbitalVelocity));
        planets.addAll(Planet.createSeveralDisks(numDisks, numPlanetsRange, radiusRangeLow, stellarDensityRange, mRange, densityRange, centerX, centerY, centerZ, relativeVelocityX, relativeVelocityY, relativeVelocityZ, phiRange, centerMassRange, centerDensityRange, adherenceToPlaneRange, orbitalFactor, giveOrbitalVelocity));
        return planets;
    }
    



    public static ArrayList<Planet> createDiskSimulation() {
        ArrayList<Planet> planets = new ArrayList<>();
        float[] radius = {100, 100000};
        float[] mRange = {10, 120};
        float[] densityRange = {0.1f, 0.1f};
        //Planet center = new Planet(1,0,0,0,0,0,100000);
        //planets.addAll(Planet.makeNewRandomDisk(1_000_000, radius, mRange, (float)(java.lang.Math.PI/2), true, true, center));
        planets.addAll(Planet.makeNewRandomDisk(1_000_00, radius, mRange, densityRange, 
                    new float[] {0,0,0}, new float[] {0.1f,0,0},(float)(-java.lang.Math.PI*Math.random()),
                     100000000f,100f,
                     0.98f,1f,false, true));
        planets.addAll(Planet.makeNewRandomDisk(1_000_00, radius, mRange, densityRange, 
                    new float[] {0,100000,0}, new float[] {0,0.1f,0},(float)(java.lang.Math.PI*Math.random()), 
                    100000000f,100f,
                    0.98f,1f,false, true));
        planets.addAll(Planet.makeNewRandomDisk(1_000_00, radius, mRange, densityRange, 
        new float[] {0,-100000,0}, new float[] {0.1f,-1f,0},(float)(-java.lang.Math.PI*Math.random()),
            100000000f,100f,
            0.98f,1f,false, true));
        planets.addAll(Planet.makeNewRandomDisk(1_000_00, radius, mRange, densityRange, 
                    new float[] {0,100000,100000}, new float[] {0,0.1f,-0.3f},(float)(java.lang.Math.PI*Math.random()), 
                    100000000f,100f,
                    0.98f,1f,false, true));
        //Planet jumbo = new Planet(0,0,0,0,0,0,10000000f,1);
        //planets.add(jumbo);
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
            captureIfRecording();
        }

        if (state == State.PAUSED) {
            render.render(barnesHut.getOutputSSBO(), state);
        }

        if (state == State.FRAME_ADVANCE) {
            barnesHut.step();
            render.render(barnesHut.getOutputSSBO(), state);
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
        return planets.size();
    }

    public int currentBodies() { //Note: this is EXTREMELY slow.
        if (barnesHut == null || barnesHut.getOutputSSBO() == null) {
            return 0;
        }
        return barnesHut.getOutputSSBO().getHeaderAsInts()[0];
        
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

        barnesHut.cleanup();
        render.cleanup();
        // Minimal cleanup of GL objects created in embedded mode
        if (isRecording) {
            stopRecording();
        }

        
    }

    public ArrayList<Planet> getPlanets() {
        return planets;
    }

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
            recordMetaWriter.write("bodies " + initialBodyCount);
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