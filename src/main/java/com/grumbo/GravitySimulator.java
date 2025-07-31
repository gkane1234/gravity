package com.grumbo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * GravitySimulator - Main Physics Engine & Simulation Controller
 * =============================================================
 * Primary controller that manages both physics calculations and rendering.
 * Creates and manages both WindowGravity and GravityFrame.
 */
public class GravitySimulator {
    
    public int numPlanets;
    public ChunkList listOfChunks;
    
    // Double-buffered physics system
    private PhysicsBuffer physicsBuffer;
    
    // Performance profiling variables
    public long forceCalcTime = 0;
    public long positionUpdateTime = 0;
    public long physicsTime = 0;
    public long frameCount = 0;
    
    // Performance display toggle
    public boolean showPerformanceStats = false;
    public boolean drawChunkBorders = false;
    

    // Multithreading infrastructure
    private ExecutorService threadPool;
    
    // Force accumulation for thread safety
    private ThreadLocal<Map<Planet, double[]>> threadLocalForces;
    //private ThreadLocal<ArrayList<Planet>> threadLocalChunklessPlanets;
    
    private volatile boolean running = true;

    
    public GravitySimulator(WindowGravity3D window) {
        // Initialize thread-safe physics system
        this.physicsBuffer = new PhysicsBuffer();
        this.listOfChunks = physicsBuffer.getBuffer();
        
        // Initialize thread pool - use number of CPU cores
        this.threadPool = Executors.newFixedThreadPool(Settings.getInstance().getNumThreads());
        
        // Initialize thread-local force accumulation
        this.threadLocalForces = ThreadLocal.withInitial(HashMap::new);

        // Add initial planets
        setupInitialPlanets();

    }
    
    private void setupInitialPlanets() {
        // Add initial planets to the buffer
        Planet[] initialPlanets = Planet.makeNew(1000, 
            new double[] {-100, 100}, new double[] {-100, 100}, new double[] {-100, 100},
            new double[] {-1, 1}, new double[] {-1, 1}, new double[] {-1, 1}, new double[] {1, 2});
        
        // Add to buffer
        addPlanetsToCorrectChunk(initialPlanets);
    }
    
    public void stop() {
        running = false;
        // Cancel any pending tasks
        if (threadPool != null) {
            threadPool.shutdownNow();
        }
    }
    
    /**
     * Main simulation step - calculates forces and updates positions using multiple threads
     */
    public void chunkPhysicsTick() throws Exception {
        long physicsStartTime = showPerformanceStats ? System.nanoTime() : 0;
        
        Chunk.counterSame.set(0);
        Chunk.counterDiff.set(0);
        Chunk.counterCom.set(0);
        
        try {
            // Get write lock for physics calculations
            ReentrantReadWriteLock.WriteLock writeLock = physicsBuffer.getWriteLock();
            writeLock.lock();
            try {
                // Update listOfChunks to point to the buffer
                listOfChunks = physicsBuffer.getBuffer();
            
            // FORCE CALCULATION PHASE (Multithreaded)
            long forceStartTime = showPerformanceStats ? System.nanoTime() : 0;
            
            calculateAttraction();

            if (showPerformanceStats) {
                long forceEndTime = System.nanoTime();
                forceCalcTime = (forceEndTime - forceStartTime);
            }
            
            // POSITION UPDATE PHASE (Multithreaded movement, sequential chunk management)
            long positionStartTime = showPerformanceStats ? System.nanoTime() : 0;
            
            moveAndUpdateChunks();

            if (showPerformanceStats) {
                long physicsEndTime = System.nanoTime();
                physicsTime = (physicsEndTime - physicsStartTime);

                long positionEndTime = System.nanoTime();
                positionUpdateTime = (positionEndTime - positionStartTime);
                
                frameCount++;
            }
            
                // Mark physics update as complete
                physicsBuffer.updateComplete();
            } finally {
                writeLock.unlock();
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("Physics calculation was interrupted", e);
        }

    }
    
    /**
     * Calculates gravitational forces between all chunk pairs using multiple threads with force accumulation
     */
    private void calculateAttraction() throws InterruptedException {
        int numChunks = listOfChunks.getNumChunks();
        if (numChunks == 0) return;
        
        // Calculate total number of chunk pair interactions
        int totalInteractions = (numChunks * (numChunks + 1)) / 2;
        
        if (totalInteractions <= 0) System.out.println("No interactions"+numChunks);
        
        // Collect all thread-local force maps for reduction
        ConcurrentHashMap<Thread, Map<Planet, double[]>> allThreadForces = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(totalInteractions);

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
                        
                        Chunk one = listOfChunks.getChunk(c1);
                        Chunk two = listOfChunks.getChunk(c2);
                        
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
     * Takes all thread-local force accumulations and applies them to planets
     */
    private void applyAccumulatedForces(Map<Thread, Map<Planet, double[]>> allThreadForces) {
        
        // Combine forces from all threads
        for (Map<Planet, double[]> threadForces : allThreadForces.values()) {
            for (Map.Entry<Planet, double[]> entry : threadForces.entrySet()) {
                Planet planet = entry.getKey();
                double[] force = entry.getValue();
                planet.xVResidual += force[0];
                planet.yVResidual += force[1];
                planet.zVResidual += force[2];
            }
        }
        
    }
    
    /**
     * Updates planet positions and manages chunk assignments using multiple threads
     */
    @SuppressWarnings("unlikely-arg-type")
    private void moveAndUpdateChunks() throws InterruptedException {
        int numChunks = listOfChunks.getNumChunks();
        if (numChunks == 0) return;
        
        // Phase 1: Parallel movement calculations
        CountDownLatch movementLatch = new CountDownLatch(numChunks);
        
        for (int chunk = 0; chunk < numChunks; chunk++) {
            final int chunkIndex = chunk;
            
            threadPool.submit(() -> {
                try {
                    Chunk c = listOfChunks.getChunk(chunkIndex);
                    c.moveAllPlanets();
                } finally {
                    movementLatch.countDown();
                }
            });
        }

        // Wait for all movement calculations to complete
        movementLatch.await();



        
        // Remove empty chunks. Needs to be done in reverse order. Done on one thread.

        // First, collect all planets that need to be moved
        ArrayList<Planet> planetsToMove = new ArrayList<>();
        for (Chunk c : listOfChunks.getChunks()) {
            
            for (int i = c.planets.size() - 1; i >= 0; i--) {
                Planet p = c.planets.get(i);
                if (p.updateChunkCenter()) {
                    planetsToMove.add(p);
                    c.removePlanet(i);
                    numPlanets--;
                }
            }
        }

        // Then move all collected planets to their correct chunks
        for (Planet p : planetsToMove) {
            addPlanetToCorrectChunk(p);
        }

        // Finally remove empty chunks
        for (int chunk = listOfChunks.getNumChunks() - 1; chunk >= 0; chunk--) {
            Chunk c = listOfChunks.getChunk(chunk);

            if (c.planets.size() == 0) {
                //System.out.println("Removing empty chunk at " + c.center.x + "," + c.center.y + "," + c.center.z + " and number of planets " + c.planets.size()+" and index "+chunk+" chunk object "+c);
                listOfChunks.removeChunk(c.center);
            }
        }

    }
    
    /**
     * Adds a planet to the appropriate chunk based on its position
     */
    public void addPlanetToCorrectChunk(Planet o) {

        int indexOfChunk = findIndexOf(o);

        numPlanets++;
        
        if (indexOfChunk==-1) { // create new chunk
            listOfChunks.addChunkWithPlanet(o);
            return;
        }
        
        Chunk c = listOfChunks.getChunk(indexOfChunk);
        c.addPlanet(o);
        
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
    private int findIndexOf(Planet o) {
        return listOfChunks.getChunkIndex(o.chunkCenter);
    }

    public void updateChunkSize(double newChunkSize) {
        ArrayList<Planet> planets = new ArrayList<>();
        System.out.println("Updating chunk size to " + newChunkSize);
        for (Chunk c : listOfChunks.getChunks()) {
            for (Planet p : c.planets) {
                planets.add(p);
            }
            
        }
        listOfChunks = new ChunkList();
        for (Planet p : planets) {
            addPlanetToCorrectChunk(p);
        }
        System.out.println("Chunk size updated to " + newChunkSize);
    }

    
    /**
     * Gets reference planet for camera following
     */
    public int[] getReference(boolean follow) {
        if (!follow) return new int[] {0, 0, 0};
        
        if (listOfChunks.getNumChunks() > 0 && listOfChunks.getChunk(0).planets.size() > 0) {
            Planet g = listOfChunks.getChunk(0).planets.get(0);
            return new int[] {(int) g.x, (int) g.y, (int) g.z};
        }
        return new int[] {0, 0, 0};
    }
        /**
     * Shuts down the thread pool gracefully
     */
    public void shutdown() {
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
            try {
                // Wait longer for tasks to complete
                if (!threadPool.awaitTermination(2, TimeUnit.SECONDS)) {
                    // Force shutdown if tasks don't complete
                    threadPool.shutdownNow();
                    // Wait again for tasks to respond to interrupt
                    if (!threadPool.awaitTermination(2, TimeUnit.SECONDS)) {
                        System.err.println("Thread pool did not terminate");
                    }
                }
            } catch (InterruptedException e) {
                // Re-cancel if current thread also interrupted
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Get the buffer for safe reading during rendering
     */
    public ChunkList getRenderBuffer() {
        return physicsBuffer.getBuffer();
    }
    
    /**
     * Get the read lock for the buffer
     */
    public ReentrantReadWriteLock.ReadLock getRenderBufferReadLock() {
        return physicsBuffer.getReadLock();
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
        //window.frame.totalRenderTime = 0;
    }
    
    
    
    /**
     * Gets comprehensive performance statistics formatted for display
     */
    public String getDisplayPerformanceStats() {
        if (frameCount == 0) return "No performance data available";
        
        double forceTime = forceCalcTime  / 1_000_000.0;
        double positionTime = positionUpdateTime / 1_000_000.0;
        double physicsTime = this.physicsTime / 1_000_000.0;
        //double renderTime = window.frame.totalRenderTime / 1_000_000.0;
        
        double forcePercentage = physicsTime > 0 ? forceTime / physicsTime * 100 : 0;
        double updatePercentage = physicsTime > 0 ? positionTime / physicsTime * 100 : 0;
        
        double totalTimeMs = physicsTime;
        double fps = totalTimeMs > 0 ? 1000.0 / totalTimeMs : 0;
        
        // Calculate thread utilization
        int chunkPairs = listOfChunks.getNumChunks() > 0 ? (listOfChunks.getNumChunks() * (listOfChunks.getNumChunks() + 1)) / 2 : 0;
        double threadUtilization = chunkPairs > 0 ? Math.min(100.0, (double)chunkPairs / Settings.getInstance().getNumThreads() * 100) : 0;
        
        return String.format(
            "=== PERFORMANCE STATS (MULTITHREADED) ===\n" +
            "Threads: %d cores\n" +
            "Physics: %.2f ms\n" +
            "  - Forces: %.2f ms (%.1f%%) [PARALLEL]\n" +
            "  - Updates: %.2f ms (%.1f%%) [HYBRID]\n" +
            //"Render: %.2f ms\n" +
            "Chunk Timing Breakdown:\n" +
            "  - Same Chunk: %d ms\n" +
            "  - Different Chunks: %d ms\n" +
            "  - Center of Mass: %d ms\n" +
            "Physics/Render Ratio: %.2f:1\n" +
            "Chunks: %d | Planets: %d\n" +
            "Frame Rate: %.1f FPS (%.2f ms/frame)\n" +
            "Thread Utilization: ~%.1f%% (%d tasks)\n" +
            "Frames Processed: %d\n" +
            "Controls: 'p' toggle | 'i' detailed console | 'b' toggle chunk borders",
            Settings.getInstance().getNumThreads(),
            physicsTime,
            forceTime, forcePercentage,
            positionTime, updatePercentage,
            //renderTime,
            Chunk.counterSame.get(),
            Chunk.counterDiff.get(),
            Chunk.counterCom.get(),
            //renderTime > 0 ? physicsTime / renderTime : 0,
            listOfChunks.getNumChunks(), numPlanets,
            fps, totalTimeMs,
            threadUtilization, chunkPairs,
            frameCount
        );

    }
} 