package com.grumbo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * GravitySimulator - Main Physics Engine & Simulation Controller
 * =============================================================
 * Primary controller that manages both physics calculations and rendering.
 * Contains GravityFrame for rendering and delegates display operations to it.
 */
public class GravitySimulator {
    
    public int numPlanets;
    public ArrayList<Chunk> listOfChunks;
    public int time;
    public long wait;
    
    // Performance profiling variables
    public long forceCalcTime = 0;
    public long positionUpdateTime = 0;
    public long physicsTime = 0;
    public long frameCount = 0;
    
    // Performance display toggle
    public boolean showPerformanceStats = false;
    
    // Reference to the rendering frame
    private GravityFrame renderFrame;

    // Multithreading infrastructure
    private ExecutorService threadPool;
    
    // Force accumulation for thread safety
    private ThreadLocal<Map<Planet, double[]>> threadLocalForces;
    
    public GravitySimulator() {
        listOfChunks = new ArrayList<Chunk>();
        this.wait = 0;
        this.time = 0;
        
        // Initialize thread pool - use number of CPU cores
        this.threadPool = Executors.newFixedThreadPool(Global.numThreads);
        
        // Initialize thread-local force accumulation
        this.threadLocalForces = ThreadLocal.withInitial(HashMap::new);
        
        // Create the rendering frame
        this.renderFrame = new GravityFrame(this);
    }
    
    /**
     * Shuts down the thread pool gracefully
     */
    public void shutdown() {
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Gets the render frame for external access
     */
    public GravityFrame getRenderFrame() {
        return renderFrame;
    }
    
    /**
     * Updates zoom level in Global settings
     */
    public void changeZoom(double newZoom) {
        Global.zoom = newZoom;
    }
    
    /**
     * Toggles camera follow mode
     */
    public void toggleFollow() {
        Global.follow = !Global.follow;
        Global.shift = new double[]{0, 0};
    }
    
    /**
     * Moves camera position
     */
    public void moveCamera(double[] ds) {
        Global.shift[0] += ds[0];
        Global.shift[1] += ds[1];
    }
    
    /**
     * Main simulation step - calculates forces and updates positions using multiple threads
     */
    public void chunkTick(long wait) throws Exception {
        System.out.println("chunk tick");
        long physicsStartTime = showPerformanceStats ? System.nanoTime() : 0;
        
        time++;
        this.wait = wait;
        
        Chunk.counterSame.set(0);
        Chunk.counterDiff.set(0);
        Chunk.counterCom.set(0);
        
        try {
            // FORCE CALCULATION PHASE (Multithreaded)
            System.out.println("force calc");
            long forceStartTime = showPerformanceStats ? System.nanoTime() : 0;
            
            calculateAttraction();
            System.out.println("force calc done");
            
            if (showPerformanceStats) {
                long forceEndTime = System.nanoTime();
                forceCalcTime = (forceEndTime - forceStartTime);
            }
            
            // POSITION UPDATE PHASE (Multithreaded movement, sequential chunk management)
            long positionStartTime = showPerformanceStats ? System.nanoTime() : 0;
            
            moveAndUpdateChunks();
            System.out.println("move and update chunks");
            if (showPerformanceStats) {

                long physicsEndTime = System.nanoTime();
                physicsTime = (physicsEndTime - physicsStartTime);

                long positionEndTime = System.nanoTime();
                positionUpdateTime = (positionEndTime - positionStartTime);
                

                
                frameCount++;
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("Physics calculation was interrupted", e);
        }
        
        // Trigger rendering after physics update
        System.out.println("repaint");
        renderFrame.repaint();

        System.out.println(numPlanets);
    }
    
    /**
     * Calculates gravitational forces between all chunk pairs using multiple threads with force accumulation
     */
    private void calculateAttraction() throws InterruptedException {
        int numChunks = listOfChunks.size();
        if (numChunks == 0) return;
        
        // Calculate total number of chunk pair interactions
        int totalInteractions = (numChunks * (numChunks + 1)) / 2;
        
        if (totalInteractions <= 0) return;
        
        // Collect all thread-local force maps for reduction
        ConcurrentHashMap<Thread, Map<Planet, double[]>> allThreadForces = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(totalInteractions);
        System.out.println("latch");
        // Create tasks for each chunk pair interaction
        for (int chunk1 = 0; chunk1 < numChunks; chunk1++) {
            for (int chunk2 = chunk1; chunk2 < numChunks; chunk2++) {
                final int c1 = chunk1;
                final int c2 = chunk2;
                
                threadPool.submit(() -> {
                    try {
                        // Get thread-local force accumulator
                        Map<Planet, double[]> forceAccumulator = threadLocalForces.get();
                        forceAccumulator.clear(); // Reset for this calculation
                        
                        // Store this thread's force map for later reduction
                        allThreadForces.put(Thread.currentThread(), forceAccumulator);
                        
                        Chunk one = listOfChunks.get(c1);
                        Chunk two = listOfChunks.get(c2);
                        
                        // Use force accumulation instead of direct planet modification
                        one.attractWithAccumulation(two, forceAccumulator);
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }
        
        // Wait for all force calculations to complete
        latch.await();
        
        // Reduction phase: combine all thread-local forces and apply to planets
        applyAccumulatedForces(allThreadForces);
    }
    
    /**
     * Reduces all thread-local force accumulations and applies them to planets
     */
    private void applyAccumulatedForces(Map<Thread, Map<Planet, double[]>> allThreadForces) {
        Map<Planet, double[]> totalForces = new HashMap<>();
        
        // Combine forces from all threads
        for (Map<Planet, double[]> threadForces : allThreadForces.values()) {
            for (Map.Entry<Planet, double[]> entry : threadForces.entrySet()) {
                Planet planet = entry.getKey();
                double[] force = entry.getValue();
                
                totalForces.computeIfAbsent(planet, k -> new double[2]);
                totalForces.get(planet)[0] += force[0]; // x component
                totalForces.get(planet)[1] += force[1]; // y component
            }
        }
        
        // Apply total forces to planets (thread-safe because sequential)
        for (Map.Entry<Planet, double[]> entry : totalForces.entrySet()) {
            Planet planet = entry.getKey();
            double[] totalForce = entry.getValue();
            
            planet.xVResidual += totalForce[0];
            planet.yVResidual += totalForce[1];
        }
    }
    
    /**
     * Updates planet positions and manages chunk assignments using multiple threads
     */
    @SuppressWarnings("unlikely-arg-type")
    private void moveAndUpdateChunks() throws InterruptedException {
        int numChunks = listOfChunks.size();
        if (numChunks == 0) return;
        
        // Phase 1: Parallel movement calculations
        CountDownLatch movementLatch = new CountDownLatch(numChunks);
        
        for (int chunk = 0; chunk < numChunks; chunk++) {
            final int chunkIndex = chunk;
            
            threadPool.submit(() -> {
                try {
                    Chunk c = listOfChunks.get(chunkIndex);
                    c.move(); // Move all planets in this chunk
                } finally {
                    movementLatch.countDown();
                }
            });
        }
        
        // Wait for all movement calculations to complete
        movementLatch.await();
        
        // Phase 2: Sequential chunk reassignment (to avoid race conditions)
        // We need to process chunks in reverse order to safely remove empty chunks
        for (int chunk = listOfChunks.size() - 1; chunk >= 0; chunk--) {
            Chunk c = listOfChunks.get(chunk);
            ArrayList<Planet> planets = c.planets;
            
            // Check each planet to see if it needs to move to a different chunk
            for (int planet = planets.size() - 1; planet >= 0; planet--) {
                double[] planetChunk = getPlanetChunkCenter(planets.get(planet));

                if (!c.equals(planetChunk)) {
                    addPlanetToCorrectChunk(planets.get(planet));
                    c.removePlanet(planet);
                    numPlanets--;
                }
            }
            
            // Remove empty chunks
            if (planets.size() == 0) {
                listOfChunks.remove(chunk);
            }
        }
    }
    
    /**
     * Adds a planet to the appropriate chunk based on its position
     */
    public void addPlanetToCorrectChunk(Planet o) {
        System.out.println(numPlanets);
        double[] center = getPlanetChunkCenter(o);
        
        @SuppressWarnings("unlikely-arg-type")
        int indexOfChunk = findIndexOf(center);

        numPlanets++;
        
        if (indexOfChunk == -1) { // create new chunk
            listOfChunks.add(new Chunk(o, center, Global.chunkSize));
            return;
        }
        
        Chunk c = listOfChunks.get(indexOfChunk);
        c.addPlanet(o);
        listOfChunks.set(indexOfChunk, c);
        
    }
    
    /**
     * Adds multiple planets to their appropriate chunks
     */
    public void addPlanetsToCorrectChunk(Planet[] planets) {
        for (Planet o : planets) {
            addPlanetToCorrectChunk(o);
        }
    }
    
    /**
     * Finds the index of a chunk with the given center coordinates
     */
    private int findIndexOf(double[] center) {
        for (int i = 0; i < listOfChunks.size(); i++) {
            if (listOfChunks.get(i).equals(center)) return i;
        }
        return -1;
    }
    
    /**
     * Calculates which chunk center a planet should belong to
     */
    private double[] getPlanetChunkCenter(Planet o) {
        double chunkX = Math.floor(o.x / Global.chunkSize + 0.5);
        double chunkY = Math.floor(o.y / Global.chunkSize + 0.5);
        return new double[] {chunkX, chunkY};
    }
    
    /**
     * Gets reference planet for camera following
     */
    public int[] getReference(boolean follow) {
        if (!follow) return new int[] {0, 0};
        
        if (listOfChunks.size() > 0 && listOfChunks.get(0).planets.size() > 0) {
            Planet g = listOfChunks.get(0).planets.get(0);
            return new int[] {(int) g.x, (int) g.y};
        }
        return new int[] {0, 0};
    }
    
    /**
     * Resets performance tracking counters
     */
    public void resetPerformanceCounters() {
        forceCalcTime = 0;
        positionUpdateTime = 0;
        physicsTime = 0;
        frameCount = 0;
        
        // Reset chunk timing counters
        Chunk.counterSame.set(0);
        Chunk.counterDiff.set(0);
        Chunk.counterCom.set(0);
        
        // Also reset render timing
        renderFrame.totalRenderTime = 0;
    }
    
    
    
    /**
     * Gets comprehensive performance statistics formatted for display
     */
    public String getDisplayPerformanceStats() {
        if (frameCount == 0) return "No performance data available";
        
        double forceTime = forceCalcTime  / 1_000_000.0;
        double positionTime = positionUpdateTime / 1_000_000.0;
        double physicsTime = this.physicsTime / 1_000_000.0;
        double renderTime = renderFrame.totalRenderTime / 1_000_000.0;
        
        double forcePercentage = physicsTime > 0 ? forceTime / physicsTime * 100 : 0;
        double updatePercentage = physicsTime > 0 ? positionTime / physicsTime * 100 : 0;
        
        double totalTimeMs = physicsTime + renderTime;
        double fps = totalTimeMs > 0 ? 1000.0 / totalTimeMs : 0;
        
        // Calculate thread utilization
        int chunkPairs = listOfChunks.size() > 0 ? (listOfChunks.size() * (listOfChunks.size() + 1)) / 2 : 0;
        double threadUtilization = chunkPairs > 0 ? Math.min(100.0, (double)chunkPairs / Global.numThreads * 100) : 0;
        
        return String.format(
            "=== PERFORMANCE STATS (MULTITHREADED) ===\n" +
            "Threads: %d cores\n" +
            "Physics: %.2f ms\n" +
            "  - Forces: %.2f ms (%.1f%%) [PARALLEL]\n" +
            "  - Updates: %.2f ms (%.1f%%) [HYBRID]\n" +
            "Render: %.2f ms\n" +
            "Chunk Timing Breakdown:\n" +
            "  - Same Chunk: %d ms\n" +
            "  - Different Chunks: %d ms\n" +
            "  - Center of Mass: %d ms\n" +
            "Physics/Render Ratio: %.2f:1\n" +
            "Chunks: %d | Planets: %d\n" +
            "Frame Rate: %.1f FPS (%.2f ms/frame)\n" +
            "Thread Utilization: ~%.1f%% (%d tasks)\n" +
            "Frames Processed: %d\n" +
            "Controls: 'p' toggle | 'i' detailed console",
            Global.numThreads,
            physicsTime,
            forceTime, forcePercentage,
            positionTime, updatePercentage,
            renderTime,
            Chunk.counterSame.get(),
            Chunk.counterDiff.get(),
            Chunk.counterCom.get(),
            renderTime > 0 ? physicsTime / renderTime : 0,
            listOfChunks.size(), numPlanets,
            fps, totalTimeMs,
            threadUtilization, chunkPairs,
            frameCount
        );

    }
} 