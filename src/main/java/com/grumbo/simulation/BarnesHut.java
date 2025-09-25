package com.grumbo.simulation;

import com.grumbo.gpu.*;
import java.util.Map;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import java.nio.IntBuffer;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.opengl.GL43C.*;
/**
 * BarnesHut is the main class for the Barnes-Hut algorithm.
 * It is responsible for initializing the compute shaders and SSBOs,
 * and for running the algorithm.
 * 
 * The algorithm is implemented entirely in compute shaders, this code just organizes
 * and runs the compute shaders with the correct uniforms, SSBOs, and number of work groups.
 * 
 * After everything is initialzied, the algorithm works broadly in 7 steps:
 * 1. Parition empty bodies to the end the array (and cull out of bounds bodies)
 * 2. Generate morton codes for the alive bodies.
 * 3. Radix sort the morton codes.
 * 4. Build a binary radix tree using the sotrted morton codes.
 * 5. Compute the center of mass and location of the nodes in the tree.
 * 6. Compute the force on each body using the tree.
 * 7. Merge the bodies, leaving empty bodies where they are.
 */
public class BarnesHut {
    private static final int NUM_DEBUG_OUTPUTS = 100;


    // Uniforms
    public Map<String, Uniform<?>> uniforms;


    // Uniform local variables
    public int radixSortPassShift;
    public int COMPropagationPassNumber;
    public boolean resetValuesOrDecrementDeadBodies;

    
    public Map<String, SSBO> ssbos;

    
    //Debug variables
    private List<Long> computeShaderDebugTimes;
    private long renderingTime;
    private long resetTime;
    private long decrementDeadBodiesTime;
    private long mortonAABBupdateBoundsTime;
    private long mortonAABBRepopulateBoundsTime;
    private long mortonAABBCollapseBoundsTime;
    private long mortonCodeGenerationTime;
    private long mortonTime;
    private long deadTime;
    private long deadCountTime;
    private long deadExclusiveScanTime;
    private long deadScatterTime;
    private long radixSortTime;
    private long radixSortHistogramTime;
    private long radixSortScanParallelTime;
    private long radixSortScanExclusiveTime;
    private long radixSortScatterTime;
    private long computeCOMAndLocationTime;
    private long initLeavesTime;
    private long propagateNodesTime;
    private long buildTreeTime;
    private long computeForceTime;
    private long mergeBodiesTime;

    public String debugString;
    private GPUSimulation gpuSimulation;
    private float[][] bounds;


    private boolean debug;

    private int steps;

    /**
     * Constructor for BarnesHut.
     * @param gpuSimulation The GPU simulation.
     * @param debug Whether to debug the algorithm. (This slows down the simulation to get accurate timing of each step)
     * @param bounds The bounds of the simulation.
     */
    public BarnesHut(GPUSimulation gpuSimulation, boolean debug, float[][] bounds) {
        this.gpuSimulation = gpuSimulation;
        this.debug = debug;
        this.bounds = bounds;
        this.steps = 0;
    }

    private int initialNumBodies() {
        return gpuSimulation.initialNumBodies();
    }

    public int getSteps() {
        return steps;
    }

    /**
     * Step the simulation.
     */
    public void step() {

        String dynamicOrStatic = Settings.getInstance().getDynamic();

         // If debugging, check the time taken to render the simulation. Which takes place after the algorithm is run.
         if (debug) {
            renderingTimeCheck();
        }



        // Reset various values for the queues and death counting.
        resetValues();

        // Partition the dead bodies to the end of the array.
        partitionDeadBodies();

        // Swap the morton and index buffers. This is where the bodies were partitioned to.
        GPU.swapMortonAndIndexBuffers();

        // Decrements the number of dead bodies from the total number of bodies.
        decrementDeadBodies();

        if (dynamicOrStatic.equals("dynamic")) {
            // Update the bounds of the simulation.
            updateBounds();
        }

        // Generate the morton codes for the alive bodies.
        generateMortonCodes();
        
        // Radix sort the morton codes. This swaps the morton and index buffers for each radix sort pass.
        radixSort();

        // Build the binary radix tree.
        buildBinaryRadixTree();
 
        // Compute the center of mass and location of the nodes in the tree.
        computeCOMAndLocation();

        // Compute the force on each body using the tree.
        // If bounded, OOB bodies are either killed or wraped around in here
        computeForce();


        // Merge the bodies, leaving empty bodies where they are.
        mergeBodies();


        // Swap the body buffers.
        GPU.swapBodyBuffers();


            if (debug) {
            debugString = printProfiling();
        }
        this.steps++;

    }

    /* --------- Initialization --------- */
    /**
     * Initialize the compute shaders and SSBOs, and set up the initial index array.
     */
    public void init() {
        GPU.KERNEL_INIT.run();
    }


    /* --------- Barnes-Hut --------- */
    /**
     * Check the time taken to render the simulation. Which takes place after the algorithm is run, and can be done by waiting for glFinish() before running the algorithm again.
     */
    private void renderingTimeCheck() {
        if (debug) {
            renderingTime = System.nanoTime();
            //If an error occured in rendering it will be caught here.
            GPUSimulation.checkGLError("rendering");
            glFinish();
            renderingTime = System.nanoTime() - renderingTime;
        }
    }

    /**
     * Reset the values for the next iteration. In bh_reset.comp
     */
    private void resetValues() {
        long resetStartTime = 0;
        if (debug) {
            resetStartTime = System.nanoTime();
            if (GPU.KERNEL_UPDATE.isPreDebugSelected()) {
                GPU.KERNEL_UPDATE.setPreDebugString("Reseting values"+GPU.SSBO_SIMULATION_VALUES.getDataAsString("SimulationValues"));
            }

        }
        resetValuesOrDecrementDeadBodies = true;
        GPU.KERNEL_UPDATE.run();
        if (debug) {
            GPUSimulation.checkGLError("resetValuesPass");
            glFinish();
            resetTime = System.nanoTime() - resetStartTime;
            if (GPU.KERNEL_UPDATE.isPostDebugSelected()) {
                GPU.KERNEL_UPDATE.setPostDebugString("Reset values"+GPU.SSBO_SIMULATION_VALUES.getDataAsString("SimulationValues"));
            }
        }
    }

    /**
     * Decrement the number of dead bodies from the total number of bodies. In bh_reset.comp
     */
    private void decrementDeadBodies() {
        long decrementDeadBodiesStartTime = 0;
        if (debug) {
            decrementDeadBodiesStartTime = System.nanoTime();
            if (GPU.KERNEL_UPDATE.isPreDebugSelected()) {
                GPU.KERNEL_UPDATE.addToPreDebugString("Decrementing dead bodies"+GPU.SSBO_SIMULATION_VALUES.getDataAsString("SimulationValues"));
            }
        }
        resetValuesOrDecrementDeadBodies = false;
        GPU.KERNEL_UPDATE.run();
        if (debug) {
            GPUSimulation.checkGLError("decrementDeadBodiesPass");
            glFinish();
            decrementDeadBodiesTime = System.nanoTime() - decrementDeadBodiesStartTime;
            if (GPU.KERNEL_UPDATE.isPostDebugSelected()) {
                GPU.KERNEL_UPDATE.addToPostDebugString("Decremented dead bodies"+GPU.SSBO_SIMULATION_VALUES.getDataAsString("SimulationValues"));
            }
        }
    }

    /**
     * Partition the dead bodies to the end of the array. In bh_dead.comp
     */
    private void partitionDeadBodies() {
        long deadCountStartTime = 0;
        long deadExclusiveScanStartTime = 0;
        long deadScatterStartTime = 0;

        if (debug) {
            deadCountStartTime = System.nanoTime();
            if (GPU.KERNEL_DEAD_COUNT.isPreDebugSelected()) {
                GPU.KERNEL_DEAD_COUNT.setPreDebugString("Counting dead bodies"+GPU.SSBO_SIMULATION_VALUES.getDataAsString("SimulationValues"));
            }
        }
        GPU.KERNEL_DEAD_COUNT.run();
        if (debug) {
            GPUSimulation.checkGLError("deadCount");
            if (GPU.KERNEL_DEAD_COUNT.isPostDebugSelected()) {
                GPU.KERNEL_DEAD_COUNT.setPostDebugString("Counted dead bodies"+GPU.SSBO_SIMULATION_VALUES.getDataAsString("SimulationValues"));
            }
            glFinish();
            deadCountTime = System.nanoTime() - deadCountStartTime;
            deadExclusiveScanStartTime = System.nanoTime();
            if (GPU.KERNEL_DEAD_EXCLUSIVE_SCAN.isPreDebugSelected()) {
                GPU.KERNEL_DEAD_EXCLUSIVE_SCAN.setPreDebugString("Scanning dead bodies"+GPU.SSBO_SIMULATION_VALUES.getDataAsString("SimulationValues"));
            }
        }
        GPU.KERNEL_DEAD_EXCLUSIVE_SCAN.run();
        if (debug) {
            GPUSimulation.checkGLError("deadExclusiveScan");
            if (GPU.KERNEL_DEAD_EXCLUSIVE_SCAN.isPostDebugSelected()) {
                GPU.KERNEL_DEAD_EXCLUSIVE_SCAN.setPostDebugString("Scanned dead bodies"+GPU.SSBO_SIMULATION_VALUES.getDataAsString("SimulationValues"));
            }
            glFinish();
            deadExclusiveScanTime = System.nanoTime() - deadExclusiveScanStartTime;
            deadScatterStartTime = System.nanoTime();
            if (GPU.KERNEL_DEAD_SCATTER.isPreDebugSelected()) {
                GPU.KERNEL_DEAD_SCATTER.setPreDebugString("Scattering dead bodies"+GPU.SSBO_SIMULATION_VALUES.getDataAsString("SimulationValues"));
            }
        }
        GPU.KERNEL_DEAD_SCATTER.run();
        if (debug) {
            GPUSimulation.checkGLError("deadScatter");
            if (GPU.KERNEL_DEAD_SCATTER.isPostDebugSelected()) {
                GPU.KERNEL_DEAD_SCATTER.setPostDebugString("Scattered dead bodies"+GPU.SSBO_SIMULATION_VALUES.getDataAsString("SimulationValues"));
            }
            glFinish();
            deadScatterTime = System.nanoTime() - deadScatterStartTime;
            deadTime = deadCountTime + deadExclusiveScanTime + deadScatterTime;
        }

    }

    /**
     * Update the bounds of the simulation. In bh_morton.comp
     */
    private void updateBounds() {
        if (debug) {
            mortonAABBRepopulateBoundsTime = System.nanoTime();
            if (GPU.KERNEL_MORTON_AABB_REPOPULATE.isPreDebugSelected()) {
                GPU.KERNEL_MORTON_AABB_REPOPULATE.addToPreDebugString("Updated bounds"+GPU.SSBO_SWAPPING_MORTON_IN.getDataAsString("MortonIn",0,NUM_DEBUG_OUTPUTS));
            }
        }
        GPU.KERNEL_MORTON_AABB_REPOPULATE.run();
        if (debug) {
            GPUSimulation.checkGLError("MortonAABBRepopulateKernel");
            glFinish();
            if (GPU.KERNEL_MORTON_AABB_REPOPULATE.isPostDebugSelected()) {
                GPU.KERNEL_MORTON_AABB_REPOPULATE.addToPostDebugString("Updated bounds"+GPU.SSBO_SWAPPING_MORTON_IN.getDataAsString("MortonIn",0,NUM_DEBUG_OUTPUTS));
            }
            mortonAABBRepopulateBoundsTime = System.nanoTime() - mortonAABBRepopulateBoundsTime;
        }

        if (debug) {
            if (GPU.KERNEL_MORTON_AABB_COLLAPSE.isPreDebugSelected()) {
                GPU.KERNEL_MORTON_AABB_COLLAPSE.addToPreDebugString("Updated bounds"+GPU.SSBO_SWAPPING_MORTON_IN.getDataAsString("MortonIn",0,NUM_DEBUG_OUTPUTS));
            }
            mortonAABBCollapseBoundsTime = System.nanoTime();
        }
        GPU.KERNEL_MORTON_AABB_COLLAPSE.run();

        if (debug) {
            GPUSimulation.checkGLError("MortonAABBCollapseKernel");
            glFinish();
            if (GPU.KERNEL_MORTON_AABB_COLLAPSE.isPostDebugSelected()) {
                GPU.KERNEL_MORTON_AABB_COLLAPSE.addToPostDebugString("Updated bounds"+GPU.SSBO_SWAPPING_MORTON_IN.getDataAsString("MortonIn",0,NUM_DEBUG_OUTPUTS));
            }
            mortonAABBCollapseBoundsTime = System.nanoTime() - mortonAABBCollapseBoundsTime;
            mortonAABBupdateBoundsTime = mortonAABBRepopulateBoundsTime + mortonAABBCollapseBoundsTime;
        }


    }

    /**
     * Generate the morton codes for the alive bodies. In bh_morton.comp
     */
    private void generateMortonCodes() {
        if (debug) {
            mortonCodeGenerationTime = System.nanoTime();
            if (GPU.KERNEL_MORTON_ENCODE.isPreDebugSelected()) {
                GPU.KERNEL_MORTON_ENCODE.addToPreDebugString("Generating morton codes"+GPU.SSBO_SWAPPING_MORTON_IN.getDataAsString("MortonIn",0,NUM_DEBUG_OUTPUTS));
            }
        }

        GPU.KERNEL_MORTON_ENCODE.run();
        if (debug) {
            GPUSimulation.checkGLError("generateMortonCodes");
            if (GPU.KERNEL_MORTON_ENCODE.isPostDebugSelected()) {
                GPU.KERNEL_MORTON_ENCODE.addToPostDebugString("Generated morton codes"+GPU.SSBO_SWAPPING_MORTON_IN.getDataAsString("MortonIn",0,NUM_DEBUG_OUTPUTS));
            }
            glFinish();
            mortonCodeGenerationTime = System.nanoTime() - mortonCodeGenerationTime;
            mortonTime = mortonCodeGenerationTime + mortonAABBupdateBoundsTime;
        }

    }

    /**
     * Radix sort the morton codes. In bh_radix.comp
     */
    private void radixSort() {
        int numPasses = (int)Math.ceil(63.0 / GPU.RADIX_BITS);
        
        radixSortHistogramTime = 0;
        radixSortScanParallelTime = 0;
        radixSortScanExclusiveTime = 0;
        radixSortScatterTime = 0;

        long radixSortHistogramStartTime = 0;
        long radixSortScanParallelStartTime = 0;
        long radixSortScanExclusiveStartTime = 0;
        long radixSortScatterStartTime = 0;

        radixSortPassShift = 0;
        
        for (int pass = 0; pass < numPasses; pass++) {
            
            radixSortPassShift = pass * 4; // 4 bits per pass

            if (debug) {
                radixSortHistogramStartTime = System.nanoTime();
                if (GPU.KERNEL_RADIX_HISTOGRAM.isPreDebugSelected()) {
                    GPU.KERNEL_RADIX_HISTOGRAM.addToPreDebugString("Histograming morton codes Pass "+pass+": "+GPU.SSBO_RADIX_WG_HIST.getDataAsString("WGHist",0,NUM_DEBUG_OUTPUTS)+"\n");
                }
            }

            // Phase 1: Histogram
            GPU.KERNEL_RADIX_HISTOGRAM.run();

            if (debug) {
                GPUSimulation.checkGLError("radixSortHistogramPass" + pass);
                glFinish();
                radixSortHistogramTime += System.nanoTime() - radixSortHistogramStartTime;
                radixSortScanParallelStartTime = System.nanoTime();
                if (GPU.KERNEL_RADIX_HISTOGRAM.isPostDebugSelected()) {
                    GPU.KERNEL_RADIX_HISTOGRAM.addToPostDebugString("Histogramed morton codes Pass "+pass+": "+GPU.SSBO_RADIX_WG_HIST.getDataAsString("WGHist",0,NUM_DEBUG_OUTPUTS)+"\n");
                }
                if (GPU.KERNEL_RADIX_BUCKET_SCAN.isPreDebugSelected()) {
                    GPU.KERNEL_RADIX_BUCKET_SCAN.addToPreDebugString("Scanning morton codes Pass "+pass+": "+GPU.SSBO_RADIX_WG_SCANNED.getDataAsString("WGScanned",0,NUM_DEBUG_OUTPUTS)+"\n");
                }
            }

            // Phase 2: Scan
            GPU.KERNEL_RADIX_BUCKET_SCAN.run();
            if (debug) {
                GPUSimulation.checkGLError("radixSortBucketScanPass" + pass);
                glFinish();
                radixSortScanParallelTime += System.nanoTime() - radixSortScanParallelStartTime;
                radixSortScanExclusiveStartTime = System.nanoTime();
                if (GPU.KERNEL_RADIX_BUCKET_SCAN.isPostDebugSelected()) {
                    GPU.KERNEL_RADIX_BUCKET_SCAN.addToPostDebugString("Scanned morton codes Pass "+pass+": "+GPU.SSBO_RADIX_WG_SCANNED.getDataAsString("WGScanned",0,NUM_DEBUG_OUTPUTS)+"\n");
                }
                if (GPU.KERNEL_RADIX_GLOBAL_SCAN.isPreDebugSelected()) {
                    GPU.KERNEL_RADIX_GLOBAL_SCAN.addToPreDebugString("Exclusive scanning morton codes Pass "+pass+": "+GPU.SSBO_RADIX_WG_SCANNED.getDataAsString("WGScanned",0,NUM_DEBUG_OUTPUTS)+"\n");
                }
            }

            GPU.KERNEL_RADIX_GLOBAL_SCAN.run();

            if (debug) {
                GPUSimulation.checkGLError("radixSortGlobalScanPass" + pass);
                glFinish();
                radixSortScanExclusiveTime += System.nanoTime() - radixSortScanExclusiveStartTime;
                radixSortScatterStartTime = System.nanoTime();
                if (GPU.KERNEL_RADIX_GLOBAL_SCAN.isPostDebugSelected()) {
                    GPU.KERNEL_RADIX_GLOBAL_SCAN.addToPostDebugString("Exclusive scanned morton codes Pass "+pass+": "+GPU.SSBO_RADIX_WG_SCANNED.getDataAsString("WGScanned",0,NUM_DEBUG_OUTPUTS)+"\n");
                }
                if (GPU.KERNEL_RADIX_SCATTER.isPreDebugSelected()) {
                    GPU.KERNEL_RADIX_SCATTER.addToPreDebugString("Scattering morton codes Pass "+pass+": "+GPU.SSBO_RADIX_BUCKET_TOTALS.getDataAsString("BucketTotals",0,NUM_DEBUG_OUTPUTS)+"\n");
                }
            }

            // Phase 3: Scatter
            GPU.KERNEL_RADIX_SCATTER.run();
            if (debug) {
                GPUSimulation.checkGLError("radixSortScatterPass" + pass);
                glFinish();
                radixSortScatterTime += System.nanoTime() - radixSortScatterStartTime;
                if (GPU.KERNEL_RADIX_SCATTER.isPostDebugSelected()) {
                    GPU.KERNEL_RADIX_SCATTER.addToPostDebugString("Scattered morton codes Pass "+pass+": "+GPU.SSBO_RADIX_WG_SCANNED.getDataAsString("WGScanned",0,NUM_DEBUG_OUTPUTS)+"\n");
                }
            }
            

            GPU.swapMortonAndIndexBuffers();
        }

        if (debug) {
            radixSortTime = radixSortHistogramTime + radixSortScanParallelTime + radixSortScanExclusiveTime + radixSortScatterTime;
        }
    }

    /**
     * Build the binary radix tree. In bh_tree.comp
     */
    private void buildBinaryRadixTree() {
        if (debug) {
            buildTreeTime = System.nanoTime();
            if (GPU.KERNEL_TREE_BUILD.isPreDebugSelected()) {
                GPU.KERNEL_TREE_BUILD.setPreDebugString("Building binary radix tree"+GPU.SSBO_INTERNAL_NODES.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n"+GPU.SSBO_LEAF_NODES.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n");
            }
        }
        GPU.KERNEL_TREE_BUILD.run();
        if (debug) {
            GPUSimulation.checkGLError("buildBinaryRadixTree");
            glFinish();
            buildTreeTime = System.nanoTime() - buildTreeTime;
            if (GPU.KERNEL_TREE_BUILD.isPostDebugSelected()) {
                GPU.KERNEL_TREE_BUILD.setPostDebugString("Built binary radix tree"+GPU.SSBO_INTERNAL_NODES.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n"+GPU.SSBO_LEAF_NODES.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n");
            }
        }
    }
    
    /**
     * Compute the center of mass and location of the nodes in the tree. In bh_reduce_more_efficient.comp
     */
    private void computeCOMAndLocation() {
        if (debug) {
            initLeavesTime = System.nanoTime();
            if (GPU.KERNEL_TREE_INIT_LEAVES.isPreDebugSelected()) {
                GPU.KERNEL_TREE_INIT_LEAVES.setPreDebugString("Computing center of mass and location of leaf nodes in the tree"+GPU.SSBO_INTERNAL_NODES.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n"+GPU.SSBO_LEAF_NODES.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n");
            }
        }

        GPU.KERNEL_TREE_INIT_LEAVES.run();

        if (debug) {
            GPUSimulation.checkGLError("initLeaves");
            glFinish();
            initLeavesTime = System.nanoTime() - initLeavesTime;
            if (GPU.KERNEL_TREE_INIT_LEAVES.isPostDebugSelected()) {
                GPU.KERNEL_TREE_INIT_LEAVES.setPostDebugString("Computed center of mass and location of leaf nodes in the tree"+GPU.SSBO_INTERNAL_NODES.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n"+GPU.SSBO_LEAF_NODES.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n");
            }
            if (GPU.KERNEL_TREE_PROPAGATE_NODES.isPreDebugSelected()) {
                GPU.KERNEL_TREE_PROPAGATE_NODES.setPreDebugString("Propagating nodes in the tree"+GPU.SSBO_INTERNAL_NODES.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n"+GPU.SSBO_LEAF_NODES.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n");
            }
            propagateNodesTime = System.nanoTime();

        }
        int lastThreads = 0;

        for (COMPropagationPassNumber = 0; COMPropagationPassNumber < GPU.PROPAGATE_NODES_ITERATIONS; COMPropagationPassNumber++) {
            if (debug) {
                if (GPU.KERNEL_TREE_PROPAGATE_NODES.isPreDebugSelected()) {
                    GPU.KERNEL_TREE_PROPAGATE_NODES.addToPreDebugString("Propagating nodes in the tree Pass "+COMPropagationPassNumber+": "+GPU.SSBO_INTERNAL_NODES.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n"+GPU.SSBO_LEAF_NODES.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n");
                }
            }
            GPU.KERNEL_TREE_PROPAGATE_NODES.run();
            if (debug) {
                GPUSimulation.checkGLError("propagateNodesPass" + COMPropagationPassNumber);
                glFinish();
                if (GPU.KERNEL_TREE_PROPAGATE_NODES.isPostDebugSelected()) {
                    GPU.KERNEL_TREE_PROPAGATE_NODES.addToPostDebugString("Propagated nodes in the tree Pass "+COMPropagationPassNumber+": "+GPU.SSBO_INTERNAL_NODES.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n"+GPU.SSBO_LEAF_NODES.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n");
                }
            }
            GPU.swapPropagateWorkQueueBuffers();

        }
        
        if (debug) {
            if (GPU.KERNEL_TREE_PROPAGATE_NODES.isPostDebugSelected()) {
                GPU.KERNEL_TREE_PROPAGATE_NODES.setPostDebugString("Propagated nodes in the tree"+GPU.SSBO_INTERNAL_NODES.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n"+GPU.SSBO_LEAF_NODES.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n");
            }
            GPUSimulation.checkGLError("propagateNodes");
            glFinish();
            propagateNodesTime = System.nanoTime() - propagateNodesTime;
            computeCOMAndLocationTime = initLeavesTime + propagateNodesTime;
        }
    }

    /**
     * Compute the force on each body using the tree. In bh_force.comp
     */
    private void computeForce() {
        if (debug) {
            computeForceTime = System.nanoTime();
            if (GPU.KERNEL_FORCE_COMPUTE.isPreDebugSelected()) {
                GPU.KERNEL_FORCE_COMPUTE.setPreDebugString("Computing force on each body: "+GPU.SSBO_SWAPPING_BODIES_IN.getDataAsString("BodiesIn",0,NUM_DEBUG_OUTPUTS)+"\n" + GPU.SSBO_SWAPPING_BODIES_OUT.getDataAsString("BodiesOut",0,NUM_DEBUG_OUTPUTS)+"\n"+GPU.SSBO_LEAF_NODES.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n"+GPU.SSBO_INTERNAL_NODES.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n");// + INTERNAL_NODES_SSBO.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n" + LEAF_NODE
            }
        }


        GPU.KERNEL_FORCE_COMPUTE.run();
        if (debug) {
            GPUSimulation.checkGLError("computeForce");
            glFinish();
            computeForceTime = System.nanoTime() - computeForceTime;
            if (GPU.KERNEL_FORCE_COMPUTE.isPostDebugSelected()) {
                GPU.KERNEL_FORCE_COMPUTE.setPostDebugString("Computing force on each body: "+GPU.SSBO_SWAPPING_BODIES_IN.getDataAsString("BodiesIn",0,NUM_DEBUG_OUTPUTS)+"\n" + GPU.SSBO_SWAPPING_BODIES_OUT.getDataAsString("BodiesOut",0,NUM_DEBUG_OUTPUTS)+"\n"+GPU.SSBO_LEAF_NODES.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n"+GPU.SSBO_INTERNAL_NODES.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n");
            }
        }
    }

    /**
     * Merge the bodies, leaving empty bodies where they are. In bh_merge.comp
     */
    private void mergeBodies() {
        if (debug) {
            mergeBodiesTime = System.nanoTime();
            if (GPU.KERNEL_MERGE_BODIES.isPreDebugSelected()) {
                GPU.KERNEL_MERGE_BODIES.setPreDebugString("Merging bodies: "+GPU.SSBO_MERGE_QUEUE.getDataAsString("MergeQueue",0,NUM_DEBUG_OUTPUTS)+"\n" + GPU.SSBO_SWAPPING_BODIES_OUT.getDataAsString("BodiesOut",0,NUM_DEBUG_OUTPUTS)+"\n");// + INTERNAL_NODES_SSBO.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n" + LEAF_NODES_SSBO.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n");
            }
        }
        GPU.KERNEL_MERGE_BODIES.run();
        if (debug) {
            GPUSimulation.checkGLError("mergeBodies");
            glFinish();
            mergeBodiesTime = System.nanoTime() - mergeBodiesTime;
            if (GPU.KERNEL_MERGE_BODIES.isPostDebugSelected()) {
                GPU.KERNEL_MERGE_BODIES.setPostDebugString("Merged bodies: "+GPU.SSBO_MERGE_QUEUE.getDataAsString("MergeQueue",0,NUM_DEBUG_OUTPUTS)+"\n" + GPU.SSBO_SWAPPING_BODIES_OUT.getDataAsString("BodiesOut",0,NUM_DEBUG_OUTPUTS)+"\n");// + INTERNAL_NODES_SSBO.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n" + LEAF_NODES_SSBO.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n");
            }
        }
    }




    /**
     * Gets the bounds of the simulation.
     * @return the bounds of the simulation.
     */
    public float[][] getBounds() {
        return bounds;
    }



    /* --------- Debugging --------- */


    /**
     * Print the profiling information.
     */
    private String printProfiling() {
        long totalTime = mortonTime + radixSortTime + buildTreeTime + propagateNodesTime + computeForceTime + deadTime + renderingTime + resetTime + mergeBodiesTime;
        long percentRendering = (renderingTime * 100) / totalTime;
        long percentReset = (resetTime * 100) / totalTime;
        long percentDecrementDeadBodies = (decrementDeadBodiesTime * 100) / totalTime;
        long percentRepopulateBounds = (mortonAABBRepopulateBoundsTime * 100) / totalTime;
        long percentCollapseBounds = (mortonAABBCollapseBoundsTime * 100) / totalTime;
        long percentUpdateBounds = (mortonAABBupdateBoundsTime * 100) / totalTime;
        long percentMortonCodeGeneration = (mortonCodeGenerationTime * 100) / totalTime;
        long percentMorton = (mortonTime * 100) / totalTime;
        long percentDead = (deadTime * 100) / totalTime;
        long percentDeadCount = (deadCountTime * 100) / totalTime;
        long percentDeadExclusiveScan = (deadExclusiveScanTime * 100) / totalTime;
        long percentDeadScatter = (deadScatterTime * 100) / totalTime;
        long percentRadixSort = (radixSortTime * 100) / totalTime;
        long percentRadixSortHistogram = (radixSortHistogramTime * 100) / totalTime;
        long percentRadixSortParallelScan = (radixSortScanParallelTime * 100) / totalTime;
        long percentRadixSortExclusiveScan = (radixSortScanExclusiveTime * 100) / totalTime;
        long percentRadixSortScatter = (radixSortScatterTime * 100) / totalTime;
        long percentBuildTree = (buildTreeTime * 100) / totalTime;
        long percentComputeCOMAndLocation = (computeCOMAndLocationTime * 100) / totalTime;
        long percentInitLeaves = (initLeavesTime * 100) / totalTime;
        long percentPropagateNodes = (propagateNodesTime * 100) / totalTime;
        long percentComputeForce = (computeForceTime * 100) / totalTime;
        long percentMergeBodies = (mergeBodiesTime * 100) / totalTime;
        final long oneMillion = 1_000_000;
        return renderingTime/oneMillion + " ms (" + percentRendering + "%)" +":Rendering\n" + 
               resetTime/oneMillion + " ms (" + percentReset + "%)" +":Reset\n" + 
               mortonTime/oneMillion + " ms (" + percentMorton + "%)" +":Morton\n" +
               "\t" + mortonAABBupdateBoundsTime/oneMillion + " ms (" + percentUpdateBounds + "%)" +":Update Bounds\n" +
               "\t\t" + mortonAABBRepopulateBoundsTime/oneMillion + " ms (" + percentRepopulateBounds + "%)" +":Repopulate Bounds\n" +
               "\t\t" + mortonAABBCollapseBoundsTime/oneMillion + " ms (" + percentCollapseBounds + "%)" +":Collapse Bounds\n" +
               "\t" + mortonCodeGenerationTime/oneMillion + " ms (" + percentMortonCodeGeneration + "%)" +":Morton Code Generation\n" +
               deadTime/oneMillion + " ms (" + percentDead + "%)" +":Dead\n" +
               "\t" + deadCountTime/oneMillion + " ms (" + percentDeadCount + "%)" +":Count\n" +
               "\t" + deadExclusiveScanTime/oneMillion + " ms (" + percentDeadExclusiveScan + "%)" +":Exclusive Scan\n" +
               "\t" + deadScatterTime/oneMillion + " ms (" + percentDeadScatter + "%)" +":Scatter\n" +
               radixSortTime/oneMillion + " ms (" + percentRadixSort + "%)" +":Radix Sort\n" +
               "\t" + radixSortHistogramTime/oneMillion + " ms (" + percentRadixSortHistogram + "%)" +":Histogram\n" +
               "\t" + radixSortScanParallelTime/oneMillion + " ms (" + percentRadixSortParallelScan + "%)" +":Parallel Scan\n" +
               "\t" + radixSortScanExclusiveTime/oneMillion + " ms (" + percentRadixSortExclusiveScan + "%)" +":Exclusive Scan\n" +
               "\t" + radixSortScatterTime/oneMillion + " ms (" + percentRadixSortScatter + "%)" +":Scatter\n" +
               buildTreeTime/oneMillion + " ms (" + percentBuildTree + "%)" +":Build Tree\n" +
               computeCOMAndLocationTime/oneMillion + " ms (" + percentComputeCOMAndLocation + "%)" +":Fill Tree\n" +
               "\t" + initLeavesTime/oneMillion + " ms (" + percentInitLeaves + "%)" +":Init Leaves\n" +
               "\t" + propagateNodesTime/oneMillion + " ms (" + percentPropagateNodes + "%)" +":Propagate Nodes\n" +
               computeForceTime/oneMillion + " ms (" + percentComputeForce + "%)" +":Force\n" +
               mergeBodiesTime/oneMillion + " ms (" + percentMergeBodies + "%)" +":Merge Bodies\n" +
               totalTime/oneMillion + " ms" +":Total\n" ;
               //"Simulation Bounds: " + SIMULATION_VALUES_SSBO.getDataAsString("bounds")+"\n" +
               //"X Bounds: " + SIMULATION_VALUES_SSBO.getData("minCorner")[0]+" "+SIMULATION_VALUES_SSBO.getData("maxCorner")[0]+"\n" +
               //"Y Bounds: " + SIMULATION_VALUES_SSBO.getData("minCorner")[1]+" "+SIMULATION_VALUES_SSBO.getData("maxCorner")[1]+"\n" +
               //"Z Bounds: " + SIMULATION_VALUES_SSBO.getData("minCorner")[2]+" "+SIMULATION_VALUES_SSBO.getData("maxCorner")[2]+"\n" +
               //"Bodies: " + (SIMULATION_VALUES_SSBO.getIntegerData("numBodies"));
    }


    /**
     * Check the morton codes debug.
     */
    private void checkMortonCodes(SSBO mortonBuffer, boolean print) {
        boolean correctPartitioning = true;
        boolean correctSorting = true;
        long[] mortonCodes = new long[initialNumBodies()];
        LongBuffer mortonBufferLong = mortonBuffer.getBuffer().asLongBuffer();
        long[] sortedMortonCodes = new long[initialNumBodies()];
        for (int i = 0; i < initialNumBodies(); i++) {
            mortonCodes[i] = mortonBufferLong.get(i);
            sortedMortonCodes[i] = mortonCodes[i];
            if (print) {
                System.out.println("Morton Code: " + i + " " + mortonCodes[i]);
            }
        }
        Arrays.sort(sortedMortonCodes);
        int deadIndex = 0;
        while (deadIndex < initialNumBodies() && sortedMortonCodes[deadIndex] == -1) {
            deadIndex++;
        }
        for (int i = 0; i < mortonCodes.length-deadIndex; i++) {
            if (mortonCodes[i] != sortedMortonCodes[i+deadIndex]) {
                correctSorting = false;
            }
            if (mortonCodes[i] == -1) {
                correctPartitioning = false;
            }
        }

        System.out.println("Morton Codes are correctly partitioned: " + correctPartitioning);
        System.out.println("Morton Codes are correctly sorted: " + correctSorting);
    }

    /**
     * Debug the morton codes.
     */
    private void debugMortonCodes() {
        
        // Read morton codes from GPU buffer
        GPU.SSBO_SWAPPING_MORTON_IN.bind();
        ByteBuffer mortonBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        LongBuffer mortonData = mortonBuffer.asLongBuffer();
        HashSet<Long> mortonSet = new HashSet<>();
        
        for (int i = 0; i < Math.min(10, mortonData.capacity()); i++) {
            long morton = mortonData.get(i);   
            System.out.printf("  [%d]: %d\n", i, morton);
        }

        for (int i = 0; i < mortonData.capacity(); i++) {
            if (mortonSet.contains(mortonData.get(i))) {
                System.out.println("Duplicate morton code: " + mortonData.get(i));
            }
            mortonSet.add(mortonData.get(i));
        }

        System.out.println("Non-unique morton codes: " + (mortonData.capacity() - mortonSet.size()));
        
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        SSBO.unBind();
    }
    
    /**
     * Debug the radix sort.
     */
    private void debugRadixSort() {
        GPU.SSBO_SWAPPING_MORTON_IN.bind();
        ByteBuffer mortonBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer mortonData = mortonBuffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        GPU.SSBO_SWAPPING_INDEX_IN.bind();
        ByteBuffer indexBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer indexData = indexBuffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        GPU.SSBO_RADIX_WG_HIST.bind();
        ByteBuffer wgHistBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer wgHistData = wgHistBuffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        GPU.SSBO_RADIX_WG_SCANNED.bind();
        ByteBuffer wgScannedBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer wgScannedData = wgScannedBuffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        GPU.SSBO_RADIX_BUCKET_TOTALS.bind();
        ByteBuffer bucketTotalsBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer bucketTotalsData = bucketTotalsBuffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        GPU.SSBO_SWAPPING_MORTON_OUT.bind();
        ByteBuffer mortonOutBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer mortonOutData = mortonOutBuffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        GPU.SSBO_SWAPPING_INDEX_OUT.bind();
        ByteBuffer indexOutBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer indexOutData = indexOutBuffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        for (int i = 0; i < Math.min(10, initialNumBodies()); i++) {
            int morton = mortonData.capacity() > i ? mortonData.get(i) : -1;   
            int index = indexData.capacity() > i ? indexData.get(i) : -1;
            int wgHist = wgHistData.capacity() > i ? wgHistData.get(i) : -1;
            int wgScanned = wgScannedData.capacity() > i ? wgScannedData.get(i) : -1;
            int bucketTotals = bucketTotalsData.capacity() > i ? bucketTotalsData.get(i) : -1;
            int mortonOut = mortonOutData.capacity() > i ? mortonOutData.get(i) : -1;
            int indexOut = indexOutData.capacity() > i ? indexOutData.get(i) : -1;
            System.out.printf("  [%d]: morton=%d index=%d wgHist=%d wgScanned=%d bucketTotals=%d mortonOut=%d indexOut=%d\n", i, morton, index, wgHist, wgScanned, bucketTotals, mortonOut, indexOut);
        }
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

    }

    /**
     * Debug the radix sort.
     */
    private void debugSorting() {
        System.out.println("=== RADIX SORT RESULTS ===");
            // Read sorted Morton codes
            GPU.SSBO_SWAPPING_MORTON_IN.bind();
            ByteBuffer mortonBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            IntBuffer mortonData = mortonBuffer.asIntBuffer();
            System.out.println("Sorted Morton codes:");
            for (int i = 0; i < Math.min(100, initialNumBodies()); i++) {
                int morton = mortonData.get(i);
                System.out.printf("  [%d]: %d\n", i, morton);
            }
            Set<Integer> s = new HashSet<Integer>();
            for (int i = 0; i < mortonData.capacity(); i++) {
                s.add(mortonData.get(i));
            }
            System.out.println(mortonData.capacity() + " " + s.size());
            glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            
            // Read sorted body indices
            GPU.SSBO_SWAPPING_INDEX_IN.bind();
            ByteBuffer indexBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            IntBuffer indexData = indexBuffer.asIntBuffer();
            System.out.println("Sorted body indices:");
            for (int i = 0; i < Math.min(100, initialNumBodies()); i++) {
                int bodyIndex = indexData.get(i);
                System.out.printf("  [%d]: %d\n", i, bodyIndex);
            }
            glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            
            SSBO.unBind();

    }



    /**
     * Verify the tree structure.
     */
    private void verifyTreeStructure(IntBuffer nodeData, int numLeaves, int totalNodes) {
        System.out.println("\n=== TREE STRUCTURE VERIFICATION ===");
        
        boolean hasErrors = false;
        int rootCount = 0;
        int[] childCount = new int[totalNodes]; // How many children each node claims to have
        boolean[] hasParent = new boolean[totalNodes]; // Which nodes have a parent pointing to them
        
        // Pass 1: Collect all parent-child relationships
        for (int i = 0; i < totalNodes; i++) {
            int offset = i * Node.STRUCT_SIZE;
            if (offset + Node.STRUCT_SIZE - 1 >= nodeData.capacity()) continue;
            
            int parentId = nodeData.get(offset + Node.PARENT_ID_OFFSET);
            
            // Check for root nodes
            if (parentId == 0xFFFFFFFF) {
                rootCount++;
                if (i < numLeaves) {
                    System.out.printf("ERROR: Leaf[%d] claims to be root!\n", i);
                    hasErrors = true;
                }
            } else {
                if (parentId >= totalNodes || parentId < 0) {
                    System.out.printf("ERROR: Node[%d] has invalid parent %d (should be 0-%d)\n", 
                        i, parentId, totalNodes-1);
                    hasErrors = true;
                } else {
                    hasParent[i] = true;
                }
            }
            
            // For internal nodes, check children
            if (i >= numLeaves) {
                int childA = nodeData.get(offset + Node.CHILD_A_OFFSET);
                int childB = nodeData.get(offset + Node.CHILD_B_OFFSET);
                
                if (childA != 0xFFFFFFFF) {
                    if (childA >= totalNodes || childA < 0) {
                        System.out.printf("ERROR: Internal[%d] has invalid childA %d\n", i, childA);
                        hasErrors = true;
                    } else {
                        childCount[i]++;
                    }
                }
                
                if (childB != 0xFFFFFFFF) {
                    if (childB >= totalNodes || childB < 0) {
                        System.out.printf("ERROR: Internal[%d] has invalid childB %d\n", i, childB);
                        hasErrors = true;
                    } else {
                        childCount[i]++;
                    }
                }
            }
        }
        
        // Check root count
        if (rootCount == 0) {
            System.out.println("ERROR: No root node found!");
            hasErrors = true;
        } else if (rootCount > 1) {
            System.out.printf("ERROR: Multiple root nodes found (%d)!\n", rootCount);
            hasErrors = true;
        }
        
        // Pass 2: Verify bidirectional parent-child relationships
        for (int i = numLeaves; i < totalNodes; i++) {
            int offset = i * Node.STRUCT_SIZE;
            if (offset + Node.STRUCT_SIZE - 1 >= nodeData.capacity()) continue;
            
            int childA = nodeData.get(offset + Node.CHILD_A_OFFSET);
            int childB = nodeData.get(offset + Node.CHILD_B_OFFSET);
            
            // Check childA relationship
            if (childA != 0xFFFFFFFF && childA < totalNodes) {
                int childAOffset = childA * Node.STRUCT_SIZE;
                if (childAOffset + Node.STRUCT_SIZE - 1 < nodeData.capacity()) {
                    int childAParent = nodeData.get(childAOffset + Node.PARENT_ID_OFFSET);
                    if (childAParent != i) {
                        System.out.printf("ERROR: Internal[%d] claims childA=%d, but Node[%d] has parent=%d\n", 
                            i, childA, childA, childAParent == 0xFFFFFFFF ? -1 : childAParent);
                        hasErrors = true;
                    }
                }
            }
            
            // Check childB relationship
            if (childB != 0xFFFFFFFF && childB < totalNodes) {
                int childBOffset = childB * Node.STRUCT_SIZE;
                if (childBOffset + Node.STRUCT_SIZE - 1 < nodeData.capacity()) {
                    int childBParent = nodeData.get(childBOffset + Node.PARENT_ID_OFFSET);
                    if (childBParent != i) {
                        System.out.printf("ERROR: Internal[%d] claims childB=%d, but Node[%d] has parent=%d\n", 
                            i, childB, childB, childBParent == 0xFFFFFFFF ? -1 : childBParent);
                        hasErrors = true;
                    }
                }
            }
            
            // Check that internal nodes have exactly 2 children
            if (childCount[i] != 2) {
                System.out.printf("ERROR: Internal[%d] has %d children (should be 2)\n", i, childCount[i]);
                hasErrors = true;
            }
        }
        
        // Check for orphaned nodes (except root)
        for (int i = 0; i < totalNodes; i++) {
            if (!hasParent[i]) {
                int offset = i * Node.STRUCT_SIZE;
                if (offset + Node.STRUCT_SIZE - 1 < nodeData.capacity()) {
                    int parentId = nodeData.get(offset + Node.PARENT_ID_OFFSET);
                    if (parentId != 0xFFFFFFFF) {
                        System.out.printf("ERROR: Node[%d] claims parent=%d but no node claims it as child\n", 
                            i, parentId);
                        hasErrors = true;
                    }
                }
            }
        }
        
        // Check for duplicate children
        boolean[] usedAsChild = new boolean[totalNodes];
        for (int i = numLeaves; i < totalNodes; i++) {
            int offset = i * Node.STRUCT_SIZE;
            if (offset + Node.STRUCT_SIZE - 1 >= nodeData.capacity()) continue;
            
            int childA = nodeData.get(offset + Node.CHILD_A_OFFSET);
            int childB = nodeData.get(offset + Node.CHILD_B_OFFSET);
            
            if (childA != 0xFFFFFFFF && childA < totalNodes) {
                if (usedAsChild[childA]) {
                    System.out.printf("ERROR: Node[%d] is claimed as child by multiple parents!\n", childA);
                    hasErrors = true;
                } else {
                    usedAsChild[childA] = true;
                }
            }
            
            if (childB != 0xFFFFFFFF && childB < totalNodes) {
                if (usedAsChild[childB]) {
                    System.out.printf("ERROR: Node[%d] is claimed as child by multiple parents!\n", childB);
                    hasErrors = true;
                } else {
                    usedAsChild[childB] = true;
                }
            }
            
            // Check for self-reference
            if (childA == i || childB == i) {
                System.out.printf("ERROR: Internal[%d] has itself as a child!\n", i);
                hasErrors = true;
            }
        }
        
        // Summary
        if (hasErrors) {
            System.out.println("❌ TREE STRUCTURE VERIFICATION FAILED - Errors found above");
        } else {
            System.out.println("✅ TREE STRUCTURE VERIFICATION PASSED - No structural errors found");
        }
        
        System.out.printf("Root nodes: %d, Total nodes: %d (Leaves: %d, Internal: %d)\n", 
            rootCount, totalNodes, numLeaves, totalNodes - numLeaves);
        System.out.println("=== END VERIFICATION ===\n");
    }
}
