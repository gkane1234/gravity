package com.grumbo.simulation;

import com.grumbo.gpu.*;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import java.nio.IntBuffer;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import com.grumbo.debug.Debug;

import com.grumbo.simulation.GPUSimulation;


import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL43C.*;
/**
 * BoundedBarnesHut is the main class for the Barnes-Hut algorithm.
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
public class BoundedBarnesHut {

    // Simulation params
    //To change these, you need to also change their definitions in the compute shader
    private static final int WORK_GROUP_SIZE = 256;
    private static final int RADIX_BITS = 4;
    private static final int NUM_RADIX_BUCKETS = (int)Math.pow(2, RADIX_BITS); // 16 when RADIX_BITS=4

    // These can be freely changed here
    private static final int PROPAGATE_NODES_ITERATIONS = 64;

    private static final int NUM_DEBUG_OUTPUTS = 100;


    // Uniforms
    public Map<String, Uniform<?>> uniforms;
    private Uniform<Integer> numWorkGroupsUniform;
    private Uniform<Float> softeningUniform;
    private Uniform<Float> thetaUniform;
    private Uniform<Float> dtUniform;
    private Uniform<Float> elasticityUniform;
    private Uniform<Float> restitutionUniform;
    private Uniform<Integer> collisionMergingOrNeitherUniform;
    private Uniform<Integer> passShiftUniform;
    private Uniform<Boolean> resetValuesOrDecrementDeadBodiesUniform;
    private Uniform<Boolean> wrapAroundUniform;

    // Uniform local variables
    private int radixSortPassShift;
    private int COMPropagationPassNumber;
    private boolean resetValuesOrDecrementDeadBodies;
    // Compute Shaders
    private List<ComputeShader> computeShaders;
    private ComputeShader initKernel; // bh_init.comp
    private ComputeShader updateKernel; // bh_update.comp
    private ComputeShader mortonKernel; // bh_morton.comp
    private ComputeShader deadCountKernel; // bh_dead.comp
    private ComputeShader deadExclusiveScanKernel; // bh_dead.comp
    private ComputeShader deadScatterKernel; // bh_dead.comp
    private ComputeShader radixSortHistogramKernel; // bh_radix.comp
    private ComputeShader radixSortBucketScanKernel; // bh_radix.comp
    private ComputeShader radixSortGlobalScanKernel; // bh_radix.comp
    private ComputeShader radixSortScatterKernel; // bh_radix.comp
    private ComputeShader buildBinaryRadixTreeKernel; // bh_tree.comp
    private ComputeShader initLeavesKernel; // bh_reduce.comp
    private ComputeShader propagateNodesKernel; // bh_reduce.comp
    private ComputeShader computeForceKernel; // bh_force.comp
    private ComputeShader mergeBodiesKernel; // bh_merge.comp
    private ComputeShader debugKernel; // bh_debug.comp

    // SSBOs

    // layout(std430, binding = 0)  buffer LeafNodes          { Node leafNodes[]; };
    // layout(std430, binding = 1)  buffer InternalNodes      { Node internalNodes[]; };
    // layout(std430, binding = 2)  buffer SimulationValues   { uint numBodies; uint initialNumBodies; uint justDied; uint justMerged; AABB bounds; uint uintDebug[100]; float floatDebug[100]; } sim;
    // layout(std430, binding = 3)  buffer BodiesIn           { Body bodies[]; } srcB;
    // layout(std430, binding = 4)  buffer BodiesOut          { Body bodies[]; } dstB;
    // layout(std430, binding = 5)  buffer MortonIn           { uint64_t mortonIn[]; };
    // layout(std430, binding = 6)  buffer MortonOut          { uint64_t mortonOut[]; };
    // layout(std430, binding = 7)  buffer IndexIn            { uint indexIn[]; };
    // layout(std430, binding = 8)  buffer IndexOut           { uint indexOut[]; };
    // layout(std430, binding = 9)  buffer WorkQueueIn        { uint headIn; uint tailIn; uint itemsIn[]; };
    // layout(std430, binding = 10)  buffer WorkQueueOut       { uint headOut; uint tailOut; uint itemsOut[]; };
    // layout(std430, binding = 11) buffer RadixWGHist        { uint wgHist[];      };
    // layout(std430, binding = 12) buffer RadixWGScanned     { uint wgScanned[];   };
    // layout(std430, binding = 13) buffer RadixBucketTotals  { uint bucketTotals[NUM_BUCKETS]; uint globalBase[NUM_BUCKETS];};
    // layout(std430, binding = 14) buffer MergeQueue         { uint mergeQueueHead; uint mergeQueueTail; uvec2 mergeQueue[];};
    // layout(std430, binding = 15) buffer MergeBodyLocks     { uint bodyLocks[]; };
    public Map<String, SSBO> ssbos;
    private SSBO LEAF_NODES_SSBO;
    private SSBO INTERNAL_NODES_SSBO;
    private SSBO SIMULATION_VALUES_SSBO;
    private SSBO FIXED_BODIES_IN_SSBO;
    private SSBO FIXED_BODIES_OUT_SSBO;
    private SSBO FIXED_MORTON_IN_SSBO;
    private SSBO FIXED_MORTON_OUT_SSBO;
    private SSBO FIXED_INDEX_IN_SSBO;
    private SSBO FIXED_INDEX_OUT_SSBO;
    private SSBO FIXED_PROPAGATE_WORK_QUEUE_IN_SSBO;
    private SSBO FIXED_PROPAGATE_WORK_QUEUE_OUT_SSBO;
    private SSBO RADIX_WG_HIST_SSBO;
    private SSBO RADIX_WG_SCANNED_SSBO;
    private SSBO RADIX_BUCKET_TOTALS_SSBO;
    private SSBO MERGE_QUEUE_SSBO;
    private SSBO MERGE_BODY_LOCKS_SSBO;

    private SSBO SWAPPING_BODIES_IN_SSBO;
    private SSBO SWAPPING_BODIES_OUT_SSBO;
    private SSBO SWAPPING_MORTON_IN_SSBO;
    private SSBO SWAPPING_MORTON_OUT_SSBO;
    private SSBO SWAPPING_INDEX_IN_SSBO;
    private SSBO SWAPPING_INDEX_OUT_SSBO;
    private SSBO SWAPPING_PROPAGATE_WORK_QUEUE_IN_SSBO;
    private SSBO SWAPPING_PROPAGATE_WORK_QUEUE_OUT_SSBO;
    
    //Debug variables
    private List<Long> computeShaderDebugTimes;
    private long renderingTime;
    private long resetTime;
    private long decrementDeadBodiesTime;
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
     * Constructor for BoundedBarnesHut.
     * @param gpuSimulation The GPU simulation.
     * @param debug Whether to debug the algorithm. (This slows down the simulation to get accurate timing of each step)
     * @param bounds The bounds of the simulation.
     */
    public BoundedBarnesHut(GPUSimulation gpuSimulation, boolean debug, float[][] bounds) {
        this.gpuSimulation = gpuSimulation;
        this.debug = debug;
        this.bounds = bounds;
        this.steps = 0;
    }

    private int numBodies() {
        return gpuSimulation.numBodies();
    }

    public int getSteps() {
        return steps;
    }

    /**
     * Step the simulation.
     */
    public void step() {
        // If debugging, check the time taken to render the simulation. Which takes place after the algorithm is run.
        if (debug) {
            renderingTimeCheck();
        }


        //gpuSimulation.updateCurrentBodies();
        //System.out.println(SWAPPING_BODIES_IN_SSBO.getData(0, 10));
        // Reset various values for the queues and death counting.
        resetValues();

        // Partition the dead bodies to the end of the array.
        partitionDeadBodies();

        // Swap the morton and index buffers. This is where the bodies were partitioned to.
        swapMortonAndIndexBuffers();

        // Decrements the number of dead bodies from the total number of bodies.
        decrementDeadBodies();

        // Generate the morton codes for the alive bodies.
        generateMortonCodes();
        
        // Radix sort the morton codes. This swaps the morton and index buffers for each radix sort pass.
        radixSort();

        // Build the binary radix tree.
        buildBinaryRadixTree();
 
        // Compute the center of mass and location of the nodes in the tree.
        computeCOMAndLocation();

        // Compute the force on each body using the tree.
        computeForce();

        System.out.println(SIMULATION_VALUES_SSBO.getDataAsString("uintDebug"));

        // Merge the bodies, leaving empty bodies where they are.
        mergeBodies();


        // Swap the body buffers.
        swapBodyBuffers();


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
        initComputeSSBOs();
        initComputeSwappingBuffers();
        initComputeUniforms();
        initComputeShaders();
        // Set up the initial index array.
        initKernel.run();
    }

    /**
     * Initialize the SSBOs.
     * Gives the SSBOs their correct sizes or data functions, and 
     * the general layout of the SSBOs.
     */
    private void initComputeSSBOs() {
        // Compute sizes (use long to avoid overflow)
        long nodesSize = Node.STRUCT_SIZE * Integer.BYTES * (long) (numBodies());
        long maxBlock = glGetInteger(GL_MAX_SHADER_STORAGE_BLOCK_SIZE);
        System.out.println("Node space left: " + (maxBlock - nodesSize));
        System.out.println("Max block: " + maxBlock);

        if (nodesSize > maxBlock) {

            throw new RuntimeException("Not enough node space (your simulation is too large: " + numBodies() + ") Approximate max bodies: " + (maxBlock / (Node.STRUCT_SIZE * Integer.BYTES)));
        }

        GPUSimulation.checkGLError("before initComputeSSBOs");
        
        //this is a list of all the SSBOs that are used in the algorithm.
        ssbos = new HashMap<>();

        //These are the fixed SSBOs that point to the bodies in and out buffers.
        System.out.println("numBodies: " + numBodies());
        FIXED_BODIES_IN_SSBO = new SSBO(SSBO.BODIES_IN_SSBO_BINDING, () -> {
            return numBodies()*Body.STRUCT_SIZE*Float.BYTES;
        }, "FIXED_BODIES_IN_SSBO", new GLSLVariable(Body.bodyStruct,"BodiesIn",numBodies()));
        ssbos.put(FIXED_BODIES_IN_SSBO.getName(), FIXED_BODIES_IN_SSBO);

        FIXED_BODIES_OUT_SSBO = new SSBO(SSBO.BODIES_OUT_SSBO_BINDING, () -> {
            return numBodies()*Body.STRUCT_SIZE*Float.BYTES;
        }, "FIXED_BODIES_OUT_SSBO", new GLSLVariable(Body.bodyStruct,"BodiesOut",numBodies()));
        ssbos.put(FIXED_BODIES_OUT_SSBO.getName(), FIXED_BODIES_OUT_SSBO);

        //These are the fixed SSBOs that point to the morton and index buffers.
        //They are intialized with the correct sizes.
        FIXED_MORTON_IN_SSBO = new SSBO(SSBO.MORTON_IN_SSBO_BINDING, () -> {
            return numBodies() * Long.BYTES;
        }, "FIXED_MORTON_IN_SSBO", new GLSLVariable(VariableType.UINT64,"MortonIn", numBodies()));
        ssbos.put(FIXED_MORTON_IN_SSBO.getName(), FIXED_MORTON_IN_SSBO);

        FIXED_INDEX_IN_SSBO = new SSBO(SSBO.INDEX_IN_SSBO_BINDING, () -> {
            return numBodies() * Integer.BYTES;
        }, "FIXED_INDEX_IN_SSBO", new GLSLVariable(VariableType.UINT,"IndexIn", numBodies()));
        ssbos.put(FIXED_INDEX_IN_SSBO.getName(), FIXED_INDEX_IN_SSBO);

        LEAF_NODES_SSBO = new SSBO(SSBO.LEAF_NODES_SSBO_BINDING, () -> {
            return numBodies() * Node.STRUCT_SIZE * Integer.BYTES;
        }, "LEAF_NODES_SSBO", new GLSLVariable(Node.nodeStruct,"LeafNodes", numBodies()));
        ssbos.put(LEAF_NODES_SSBO.getName(), LEAF_NODES_SSBO);

        INTERNAL_NODES_SSBO = new SSBO(SSBO.INTERNAL_NODES_SSBO_BINDING, () -> {
            return (numBodies() - 1) * Node.STRUCT_SIZE * Integer.BYTES;
        }, "INTERNAL_NODES_SSBO", new GLSLVariable(Node.nodeStruct,"InternalNodes", numBodies() - 1));
        ssbos.put(INTERNAL_NODES_SSBO.getName(), INTERNAL_NODES_SSBO);

        //This is the SSBO that holds values that are used in different shaders
        SIMULATION_VALUES_SSBO = new SSBO(SSBO.SIMULATION_VALUES_SSBO_BINDING, () -> {
            return packValues();
        }, "VALUES_SSBO", new GLSLVariable(new GLSLVariable[] {
            new GLSLVariable(VariableType.UINT,"numBodies", 1), 
            new GLSLVariable(VariableType.UINT,"initialNumBodies", 1), 
            new GLSLVariable(VariableType.UINT,"justDied", 1), 
            new GLSLVariable(VariableType.UINT,"merged", 1), 
            new GLSLVariable(VariableType.UINT,"outOfBounds", 1), 
            new GLSLVariable(VariableType.UINT,"pad0", 1), 
            new GLSLVariable(VariableType.UINT,"pad1", 1), 
            new GLSLVariable(VariableType.UINT,"pad2", 1), 
            new GLSLVariable(new GLSLVariable[] {
                new GLSLVariable(VariableType.FLOAT,"minCorner", 3), new GLSLVariable(VariableType.PADDING),
                new GLSLVariable(VariableType.FLOAT,"maxCorner", 3), new GLSLVariable(VariableType.PADDING)},"bounds"), 
            new GLSLVariable(VariableType.UINT,"uintDebug", 100), 
            new GLSLVariable(VariableType.FLOAT,"floatDebug", 100)},"SimulationValues"));
        ssbos.put(SIMULATION_VALUES_SSBO.getName(), SIMULATION_VALUES_SSBO);

        //This is the SSBO that holds the histogram of the radix sort.
        RADIX_WG_HIST_SSBO = new SSBO(SSBO.RADIX_WG_HIST_SSBO_BINDING, () -> {
            return numGroups() * (NUM_RADIX_BUCKETS) * Integer.BYTES;
        }, "WG_HIST_SSBO", new GLSLVariable(VariableType.UINT,"WGHist", numGroups() * (NUM_RADIX_BUCKETS)));
        ssbos.put(RADIX_WG_HIST_SSBO.getName(), RADIX_WG_HIST_SSBO);

        //This is the SSBO that holds the scanned histogram of the radix sort.
        RADIX_WG_SCANNED_SSBO = new SSBO(SSBO.RADIX_WG_SCANNED_SSBO_BINDING, () -> {
            return Integer.BYTES + numGroups() * (NUM_RADIX_BUCKETS) * Integer.BYTES + Integer.BYTES;
        }, "WG_SCANNED_SSBO", new GLSLVariable(VariableType.UINT,"WGScanned", numGroups() * (NUM_RADIX_BUCKETS) + 1));
        ssbos.put(RADIX_WG_SCANNED_SSBO.getName(), RADIX_WG_SCANNED_SSBO);

        //This is the SSBO that holds the total number of bodies in each bucket of the radix sort.
        RADIX_BUCKET_TOTALS_SSBO = new SSBO(SSBO.RADIX_BUCKET_TOTALS_SSBO_BINDING, () -> {
            return NUM_RADIX_BUCKETS * Integer.BYTES * 2;
        }, "BUCKET_TOTALS_SSBO", new GLSLVariable(new GLSLVariable[] {
            new GLSLVariable(VariableType.UINT,"BucketTotals", NUM_RADIX_BUCKETS), 
            new GLSLVariable(VariableType.UINT,"GlobalBase", NUM_RADIX_BUCKETS)}));
        ssbos.put(RADIX_BUCKET_TOTALS_SSBO.getName(), RADIX_BUCKET_TOTALS_SSBO);

        //These are the fixed SSBOs that point to the morton and index buffers after the radix sort.
        //They are intialized with the correct sizes.
        FIXED_MORTON_OUT_SSBO = new SSBO(SSBO.MORTON_OUT_SSBO_BINDING, () -> {
            return numBodies() * Long.BYTES;
        }, "FIXED_MORTON_OUT_SSBO", new GLSLVariable(VariableType.UINT64,"MortonOut", numBodies()));
        ssbos.put(FIXED_MORTON_OUT_SSBO.getName(), FIXED_MORTON_OUT_SSBO);

        FIXED_INDEX_OUT_SSBO = new SSBO(SSBO.INDEX_OUT_SSBO_BINDING, () -> {
            return numBodies() * Integer.BYTES;
        }, "FIXED_INDEX_OUT_SSBO", new GLSLVariable(VariableType.UINT,"IndexOut", numBodies()));
        ssbos.put(FIXED_INDEX_OUT_SSBO.getName(), FIXED_INDEX_OUT_SSBO);

        //This is the SSBO that holds the work queue.
        //It is intialized with the correct sizes.
        FIXED_PROPAGATE_WORK_QUEUE_IN_SSBO = new SSBO(SSBO.PROPAGATE_WORK_QUEUE_IN_SSBO_BINDING, () -> {
            return (4 + numBodies()) * Integer.BYTES;
        }, "FIXED_PROPAGATE_WORK_QUEUE_IN_SSBO", new GLSLVariable(new GLSLVariable[] {
            new GLSLVariable(VariableType.UINT,"HeadIn", 1), 
            new GLSLVariable(VariableType.UINT,"TailIn", 1), 
            new GLSLVariable(VariableType.UINT,"ItemsIn", numBodies())}));
        ssbos.put(FIXED_PROPAGATE_WORK_QUEUE_IN_SSBO.getName(), FIXED_PROPAGATE_WORK_QUEUE_IN_SSBO);

        //This is the SSBO that holds the work queue for the second pass.
        //It is intialized with the correct sizes.
        FIXED_PROPAGATE_WORK_QUEUE_OUT_SSBO = new SSBO(SSBO.PROPAGATE_WORK_QUEUE_OUT_SSBO_BINDING, () -> {
            return (4 + numBodies()) * Integer.BYTES;
        }, "FIXED_PROPAGATE_WORK_QUEUE_OUT_SSBO", new GLSLVariable(new GLSLVariable[] {
            new GLSLVariable(VariableType.UINT,"HeadOut", 1), 
            new GLSLVariable(VariableType.UINT,"TailOut", 1), 
            new GLSLVariable(VariableType.UINT,"ItemsOut", numBodies())}));
        ssbos.put(FIXED_PROPAGATE_WORK_QUEUE_OUT_SSBO.getName(), FIXED_PROPAGATE_WORK_QUEUE_OUT_SSBO);

        MERGE_QUEUE_SSBO = new SSBO(SSBO.MERGE_QUEUE_SSBO_BINDING, () -> {
            return Math.max(2*Integer.BYTES, 2*Integer.BYTES+numBodies() * 2 * Integer.BYTES);
        }, "MERGE_QUEUE_SSBO", new GLSLVariable(new GLSLVariable[] {
            new GLSLVariable(VariableType.UINT,"MergeQueueHead", 1), 
            new GLSLVariable(VariableType.UINT,"MergeQueueTail", 1), 
            new GLSLVariable(VariableType.UINT,"MergeQueue", numBodies() * 2)}));
        ssbos.put(MERGE_QUEUE_SSBO.getName(), MERGE_QUEUE_SSBO);

        MERGE_BODY_LOCKS_SSBO = new SSBO(SSBO.MERGE_BODY_LOCKS_SSBO_BINDING, () -> {
            return numBodies() * Integer.BYTES;
        }, "BODY_LOCKS_SSBO", new GLSLVariable(VariableType.UINT,"BodyLocks", numBodies()));
        ssbos.put(MERGE_BODY_LOCKS_SSBO.getName(), MERGE_BODY_LOCKS_SSBO);

        GPUSimulation.checkGLError("after initComputeSSBOs");

        for (SSBO ssbo : ssbos.values()) {
            System.out.println("Creating buffer for " + ssbo.getName());
            ssbo.createBufferData();
            GPUSimulation.checkGLError("after createBufferData for " + ssbo.getName());
        }

        GPUSimulation.checkGLError("after createBufferData");

        uploadPlanetsData(gpuSimulation.getPlanetGenerator(), FIXED_BODIES_IN_SSBO);




    }
    /**
     * Reinitialize the compute SSBOs and swapping buffers.
     */
    public void reInitComputeSSBOsAndSwappingBuffers() {
        for (SSBO ssbo : ssbos.values()) {
            ssbo.createBufferData();
        }
        initComputeSwappingBuffers();
        uploadPlanetsData(gpuSimulation.getPlanetGenerator(), FIXED_BODIES_IN_SSBO);
    }


    /**
     * Initialize the uniforms. These are defined in bh_common.comp for the most part.
     */
    private void initComputeUniforms() {
        uniforms = new HashMap<>();
        uniforms.put("numWorkGroups", numWorkGroupsUniform);
        uniforms.put("softening", softeningUniform);
        uniforms.put("theta", thetaUniform);
        uniforms.put("dt", dtUniform);
        uniforms.put("elasticity", elasticityUniform);
        uniforms.put("restitution", restitutionUniform);
        uniforms.put("collisionMergingOrNeither", collisionMergingOrNeitherUniform);
        uniforms.put("passShift", passShiftUniform);
        uniforms.put("resetValuesOrDecrementDeadBodies", resetValuesOrDecrementDeadBodiesUniform);
        uniforms.put("wrapAround", wrapAroundUniform);

        numWorkGroupsUniform = new Uniform<Integer>("numWorkGroups", () -> {
            return numGroups();
        }, VariableType.UINT);

        softeningUniform = new Uniform<Float>("softening", () -> {
            return Settings.getInstance().getSoftening();
        }, VariableType.FLOAT);

        thetaUniform = new Uniform<Float>("theta", () -> {
            return Settings.getInstance().getTheta();
        }, VariableType.FLOAT);

        dtUniform = new Uniform<Float>("dt", () -> {
            return Settings.getInstance().getDt();
        }, VariableType.FLOAT);

        elasticityUniform = new Uniform<Float>("elasticity", () -> {
            return (float)Settings.getInstance().getElasticity();
        }, VariableType.FLOAT);

        restitutionUniform = new Uniform<Float>("restitution", () -> {
            return 0.2f;
        }, VariableType.FLOAT);

        passShiftUniform = new Uniform<Integer>("passShift", () -> {
            return radixSortPassShift;
        }, VariableType.UINT);

        collisionMergingOrNeitherUniform = new Uniform<Integer>("collisionMergingOrNeither", () -> {
            String selected = Settings.getInstance().getCollisionMergingOrNeither();
            switch (selected) {
                case "none":
                    return 0;
                case "merge":
                    return 2;
                case "collision":
                    return 1;
                default:
                    return 0;
            }
        }, VariableType.UINT);

        resetValuesOrDecrementDeadBodiesUniform = new Uniform<Boolean>("resetValuesOrDecrementDeadBodies", () -> {
            return resetValuesOrDecrementDeadBodies ? true : false;
        }, VariableType.BOOL);

        wrapAroundUniform = new Uniform<Boolean>("wrapAround", () -> {
            return Settings.getInstance().isWrapAround();
        }, VariableType.BOOL);

    }

    /**
     * Initialize the compute shaders. The names are defined in bh_main.comp. For more information on the shaders, see the glsl code in the shaders folder.
     */
    private void initComputeShaders() {

        computeShaders = new ArrayList<>();
        initKernel = new ComputeShader("KERNEL_INIT", this);
        initKernel.setUniforms(new Uniform[] {
        });
        initKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "SWAPPING_INDEX_IN_SSBO",
            "FIXED_BODIES_IN_SSBO",
            "FIXED_BODIES_OUT_SSBO"
        });
        initKernel.setXWorkGroupsFunction(() -> {
            return numGroups();
        });
        computeShaders.add(initKernel);
        mortonKernel = new ComputeShader("KERNEL_MORTON_ENCODE", this);
        mortonKernel.setUniforms(new Uniform[] {
        });
        mortonKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "SWAPPING_MORTON_IN_SSBO",
            "SWAPPING_BODIES_IN_SSBO",
            "SWAPPING_INDEX_IN_SSBO",

        });
        mortonKernel.setXWorkGroupsFunction(() -> {
            return numGroups();
        });
        computeShaders.add(mortonKernel);   
        deadCountKernel = new ComputeShader("KERNEL_DEAD_COUNT", this);
        deadCountKernel.setUniforms(new Uniform[] {
            numWorkGroupsUniform
        });
        deadCountKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "WG_HIST_SSBO",
            "SWAPPING_BODIES_IN_SSBO",
            "SWAPPING_BODIES_OUT_SSBO",
            "SWAPPING_INDEX_IN_SSBO",
            "SWAPPING_MORTON_IN_SSBO",
        });
        deadCountKernel.setXWorkGroupsFunction(() -> {
            return numGroups();
        });
        
        computeShaders.add(deadCountKernel);
        deadExclusiveScanKernel = new ComputeShader("KERNEL_DEAD_EXCLUSIVE_SCAN", this);
        deadExclusiveScanKernel.setUniforms(new Uniform[] {
            numWorkGroupsUniform
        });
        deadExclusiveScanKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "WG_HIST_SSBO",
            "WG_SCANNED_SSBO",
            "SWAPPING_BODIES_IN_SSBO",
            "SWAPPING_INDEX_IN_SSBO",
            "SWAPPING_MORTON_IN_SSBO",
        });
        deadExclusiveScanKernel.setXWorkGroupsFunction(() -> {
            return 1;
        });
        
        computeShaders.add(deadExclusiveScanKernel);
        deadScatterKernel = new ComputeShader("KERNEL_DEAD_SCATTER", this);
        deadScatterKernel.setUniforms(new Uniform[] {
            numWorkGroupsUniform
        });
        deadScatterKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "WG_SCANNED_SSBO",
            "SWAPPING_BODIES_IN_SSBO",
            "SWAPPING_INDEX_IN_SSBO",
            "SWAPPING_MORTON_IN_SSBO",
            "SWAPPING_MORTON_OUT_SSBO",
            "SWAPPING_INDEX_OUT_SSBO",
        });
        deadScatterKernel.setXWorkGroupsFunction(() -> {
            return numGroups();
        });
        
        computeShaders.add(deadScatterKernel);
        radixSortHistogramKernel = new ComputeShader("KERNEL_RADIX_HIST", this);
        radixSortHistogramKernel.setUniforms(new Uniform[] {

            passShiftUniform,
            numWorkGroupsUniform
        });
        radixSortHistogramKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "SWAPPING_MORTON_IN_SSBO",
            "SWAPPING_INDEX_IN_SSBO",
            "WG_HIST_SSBO",
            "SWAPPING_BODIES_IN_SSBO"
        });
        radixSortHistogramKernel.setXWorkGroupsFunction(() -> {
            return numGroups();
        });
        
        computeShaders.add(radixSortHistogramKernel);
        radixSortBucketScanKernel = new ComputeShader("KERNEL_RADIX_BUCKET_SCAN", this);
        radixSortBucketScanKernel.setUniforms(new Uniform[] {
            numWorkGroupsUniform
        });
        radixSortBucketScanKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "WG_HIST_SSBO",
            "WG_SCANNED_SSBO",
            "BUCKET_TOTALS_SSBO",
            "SWAPPING_BODIES_IN_SSBO"
        });
        radixSortBucketScanKernel.setXWorkGroupsFunction(() -> {
            return NUM_RADIX_BUCKETS;
        });
        
        computeShaders.add(radixSortBucketScanKernel);    
        radixSortGlobalScanKernel = new ComputeShader("KERNEL_RADIX_GLOBAL_SCAN", this);
        radixSortGlobalScanKernel.setUniforms(new Uniform[] {
            numWorkGroupsUniform
        });
        radixSortGlobalScanKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "BUCKET_TOTALS_SSBO",
            "SWAPPING_BODIES_IN_SSBO"
        });
        radixSortGlobalScanKernel.setXWorkGroupsFunction(() -> {
            return NUM_RADIX_BUCKETS;
        });
        
        computeShaders.add(radixSortGlobalScanKernel);
        radixSortScatterKernel = new ComputeShader("KERNEL_RADIX_SCATTER", this);

        radixSortScatterKernel.setUniforms(new Uniform[] {
            passShiftUniform,
            numWorkGroupsUniform
        });

        radixSortScatterKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "SWAPPING_MORTON_IN_SSBO",
            "SWAPPING_INDEX_IN_SSBO",
            "WG_SCANNED_SSBO",
            "BUCKET_TOTALS_SSBO",
            "SWAPPING_MORTON_OUT_SSBO",
            "SWAPPING_INDEX_OUT_SSBO",
            "SWAPPING_BODIES_IN_SSBO"
        });

        radixSortScatterKernel.setXWorkGroupsFunction(() -> {
            return numGroups();
        });
        
        computeShaders.add(radixSortScatterKernel); 


        buildBinaryRadixTreeKernel = new ComputeShader("KERNEL_TREE_BUILD_BINARY_RADIX_TREE", this);
        buildBinaryRadixTreeKernel.setUniforms(new Uniform[] {

        });
        
        buildBinaryRadixTreeKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "SWAPPING_MORTON_IN_SSBO",
            "SWAPPING_INDEX_IN_SSBO",
            "INTERNAL_NODES_SSBO",
            "LEAF_NODES_SSBO",
            "SWAPPING_BODIES_IN_SSBO"
        });

        buildBinaryRadixTreeKernel.setXWorkGroupsFunction(() -> {
            int numInternalNodes = numBodies() - 1;
            int internalNodeGroups = (numInternalNodes + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
            return internalNodeGroups;
        });
        
        computeShaders.add(buildBinaryRadixTreeKernel);
        //Compute COM and Location Kernels

        initLeavesKernel = new ComputeShader("KERNEL_TREE_INIT_LEAVES", this);

        initLeavesKernel.setUniforms(new Uniform[] {

        }); 

        initLeavesKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "SWAPPING_BODIES_IN_SSBO",
            "INTERNAL_NODES_SSBO",
            "LEAF_NODES_SSBO",
            "SWAPPING_MORTON_IN_SSBO",
            "SWAPPING_INDEX_IN_SSBO",
            "SWAPPING_PROPAGATE_WORK_QUEUE_IN_SSBO",
        });

        initLeavesKernel.setXWorkGroupsFunction(() -> {
            return numGroups();
        });
        computeShaders.add(initLeavesKernel);
        updateKernel = new ComputeShader("KERNEL_UPDATE", this);

        updateKernel.setUniforms(new Uniform[] {
            resetValuesOrDecrementDeadBodiesUniform
        });

        updateKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "SWAPPING_PROPAGATE_WORK_QUEUE_IN_SSBO",
            "MERGE_QUEUE_SSBO",
            "SWAPPING_BODIES_IN_SSBO",
            "SWAPPING_BODIES_OUT_SSBO"
        });

        updateKernel.setXWorkGroupsFunction(() -> {
            return 1;
        });
        computeShaders.add(updateKernel);
        propagateNodesKernel = new ComputeShader("KERNEL_TREE_PROPAGATE_NODES", this);

        propagateNodesKernel.setUniforms(new Uniform[] {
        });

        propagateNodesKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "INTERNAL_NODES_SSBO",
            "LEAF_NODES_SSBO",
            "SWAPPING_PROPAGATE_WORK_QUEUE_IN_SSBO",
            "SWAPPING_PROPAGATE_WORK_QUEUE_OUT_SSBO",
            "SWAPPING_BODIES_IN_SSBO",

            
        });
        propagateNodesKernel.setXWorkGroupsFunction(() -> {
            int maxPossibleNodes = Math.max(4*WORK_GROUP_SIZE,(int)((numBodies() - 1)/Math.pow(2,COMPropagationPassNumber)));
            int workGroups = (maxPossibleNodes + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
            return workGroups;
        });
        computeShaders.add(propagateNodesKernel);
        computeForceKernel = new ComputeShader("KERNEL_FORCE_COMPUTE", this);

        computeForceKernel.setUniforms(new Uniform[] {
            thetaUniform,
            dtUniform,
            elasticityUniform,
            wrapAroundUniform,
            softeningUniform,
            collisionMergingOrNeitherUniform,
        });

        computeForceKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "SWAPPING_BODIES_IN_SSBO",
            "SWAPPING_BODIES_OUT_SSBO",
            "INTERNAL_NODES_SSBO",
            "LEAF_NODES_SSBO",
            "SWAPPING_INDEX_IN_SSBO",
            "MERGE_QUEUE_SSBO"
        });

        computeForceKernel.setXWorkGroupsFunction(() -> {
            return numGroups();
        });
        computeShaders.add(computeForceKernel);
        mergeBodiesKernel = new ComputeShader("KERNEL_MERGE_BODIES", this);
        mergeBodiesKernel.setUniforms(new Uniform[] {
            
        });
        mergeBodiesKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "SWAPPING_BODIES_IN_SSBO",
            "SWAPPING_BODIES_OUT_SSBO",
            "MERGE_QUEUE_SSBO",
            "BODY_LOCKS_SSBO",
        });
        mergeBodiesKernel.setXWorkGroupsFunction(() -> {
            return numGroups();
        });
        computeShaders.add(mergeBodiesKernel);
        debugKernel = new ComputeShader("KERNEL_DEBUG", this);

        debugKernel.setUniforms(new Uniform[] {

        });

        debugKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "SWAPPING_MORTON_IN_SSBO",
            "SWAPPING_INDEX_IN_SSBO",
            "SWAPPING_BODIES_IN_SSBO",
        });

        debugKernel.setXWorkGroupsFunction(() -> {
            return numGroups();
        });

        computeShaders.add(debugKernel);
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
            if (updateKernel.isPreDebugSelected()) {
                updateKernel.setPreDebugString("Reseting values"+SIMULATION_VALUES_SSBO.getDataAsString("SimulationValues"));
            }

        }
        resetValuesOrDecrementDeadBodies = true;
        updateKernel.run();
        if (debug) {
            GPUSimulation.checkGLError("resetValuesPass");
            glFinish();
            resetTime = System.nanoTime() - resetStartTime;
            if (updateKernel.isPostDebugSelected()) {
                updateKernel.setPostDebugString("Reset values"+SIMULATION_VALUES_SSBO.getDataAsString("SimulationValues"));
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
            if (updateKernel.isPreDebugSelected()) {
                updateKernel.addToPreDebugString("Decrementing dead bodies"+SIMULATION_VALUES_SSBO.getDataAsString("SimulationValues"));
            }
        }
        resetValuesOrDecrementDeadBodies = false;
        updateKernel.run();
        if (debug) {
            GPUSimulation.checkGLError("decrementDeadBodiesPass");
            glFinish();
            decrementDeadBodiesTime = System.nanoTime() - decrementDeadBodiesStartTime;
            if (updateKernel.isPostDebugSelected()) {
                updateKernel.addToPostDebugString("Decremented dead bodies"+SIMULATION_VALUES_SSBO.getDataAsString("SimulationValues"));
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
            if (deadCountKernel.isPreDebugSelected()) {
                deadCountKernel.setPreDebugString("Counting dead bodies"+SIMULATION_VALUES_SSBO.getDataAsString("SimulationValues"));
            }
        }
        deadCountKernel.run();
        if (debug) {
            GPUSimulation.checkGLError("deadCount");
            if (deadCountKernel.isPostDebugSelected()) {
                deadCountKernel.setPostDebugString("Counted dead bodies"+SIMULATION_VALUES_SSBO.getDataAsString("SimulationValues"));
            }
            glFinish();
            deadCountTime = System.nanoTime() - deadCountStartTime;
            deadExclusiveScanStartTime = System.nanoTime();
            if (deadExclusiveScanKernel.isPreDebugSelected()) {
                deadExclusiveScanKernel.setPreDebugString("Scanning dead bodies"+SIMULATION_VALUES_SSBO.getDataAsString("SimulationValues"));
            }
        }
        deadExclusiveScanKernel.run();
        if (debug) {
            GPUSimulation.checkGLError("deadExclusiveScan");
            if (deadExclusiveScanKernel.isPostDebugSelected()) {
                deadExclusiveScanKernel.setPostDebugString("Scanned dead bodies"+SIMULATION_VALUES_SSBO.getDataAsString("SimulationValues"));
            }
            glFinish();
            deadExclusiveScanTime = System.nanoTime() - deadExclusiveScanStartTime;
            deadScatterStartTime = System.nanoTime();
            if (deadScatterKernel.isPreDebugSelected()) {
                deadScatterKernel.setPreDebugString("Scattering dead bodies"+SIMULATION_VALUES_SSBO.getDataAsString("SimulationValues"));
            }
        }
        deadScatterKernel.run();
        if (debug) {
            GPUSimulation.checkGLError("deadScatter");
            if (deadScatterKernel.isPostDebugSelected()) {
                deadScatterKernel.setPostDebugString("Scattered dead bodies"+SIMULATION_VALUES_SSBO.getDataAsString("SimulationValues"));
            }
            glFinish();
            deadScatterTime = System.nanoTime() - deadScatterStartTime;
            deadTime = deadCountTime + deadExclusiveScanTime + deadScatterTime;
        }

    }

    /**
     * Generate the morton codes for the alive bodies. In bh_morton.comp
     */
    private void generateMortonCodes() {
        if (debug) {
            mortonTime = System.nanoTime();
            if (mortonKernel.isPreDebugSelected()) {
                mortonKernel.setPreDebugString("Generating morton codes"+SWAPPING_MORTON_IN_SSBO.getDataAsString("MortonIn",0,NUM_DEBUG_OUTPUTS));
            }
        }

        mortonKernel.run();
        if (debug) {
            GPUSimulation.checkGLError("generateMortonCodes");
            if (mortonKernel.isPostDebugSelected()) {
                mortonKernel.setPostDebugString("Generated morton codes"+SWAPPING_MORTON_IN_SSBO.getDataAsString("MortonIn",0,NUM_DEBUG_OUTPUTS));
            }
            glFinish();
            mortonTime = System.nanoTime() - mortonTime;
        }

    }

    /**
     * Radix sort the morton codes. In bh_radix.comp
     */
    private void radixSort() {
        int numPasses = (int)Math.ceil(63.0 / RADIX_BITS);
        
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
                if (radixSortHistogramKernel.isPreDebugSelected()) {
                    radixSortHistogramKernel.addToPreDebugString("Histograming morton codes Pass "+pass+": "+RADIX_WG_HIST_SSBO.getDataAsString("WGHist",0,NUM_DEBUG_OUTPUTS)+"\n");
                }
            }

            // Phase 1: Histogram
            radixSortHistogramKernel.run();

            if (debug) {
                GPUSimulation.checkGLError("radixSortHistogramPass" + pass);
                glFinish();
                radixSortHistogramTime += System.nanoTime() - radixSortHistogramStartTime;
                radixSortScanParallelStartTime = System.nanoTime();
                if (radixSortHistogramKernel.isPostDebugSelected()) {
                    radixSortHistogramKernel.addToPostDebugString("Histogramed morton codes Pass "+pass+": "+RADIX_WG_HIST_SSBO.getDataAsString("WGHist",0,NUM_DEBUG_OUTPUTS)+"\n");
                }
                if (radixSortBucketScanKernel.isPreDebugSelected()) {
                    radixSortBucketScanKernel.addToPreDebugString("Scanning morton codes Pass "+pass+": "+RADIX_WG_SCANNED_SSBO.getDataAsString("WGScanned",0,NUM_DEBUG_OUTPUTS)+"\n");
                }
            }

            // Phase 2: Scan
            radixSortBucketScanKernel.run();
            if (debug) {
                GPUSimulation.checkGLError("radixSortBucketScanPass" + pass);
                glFinish();
                radixSortScanParallelTime += System.nanoTime() - radixSortScanParallelStartTime;
                radixSortScanExclusiveStartTime = System.nanoTime();
                if (radixSortBucketScanKernel.isPostDebugSelected()) {
                    radixSortBucketScanKernel.addToPostDebugString("Scanned morton codes Pass "+pass+": "+RADIX_WG_SCANNED_SSBO.getDataAsString("WGScanned",0,NUM_DEBUG_OUTPUTS)+"\n");
                }
                if (radixSortGlobalScanKernel.isPreDebugSelected()) {
                    radixSortGlobalScanKernel.addToPreDebugString("Exclusive scanning morton codes Pass "+pass+": "+RADIX_WG_SCANNED_SSBO.getDataAsString("WGScanned",0,NUM_DEBUG_OUTPUTS)+"\n");
                }
            }

            radixSortGlobalScanKernel.run();

            if (debug) {
                GPUSimulation.checkGLError("radixSortGlobalScanPass" + pass);
                glFinish();
                radixSortScanExclusiveTime += System.nanoTime() - radixSortScanExclusiveStartTime;
                radixSortScatterStartTime = System.nanoTime();
                if (radixSortGlobalScanKernel.isPostDebugSelected()) {
                    radixSortGlobalScanKernel.addToPostDebugString("Exclusive scanned morton codes Pass "+pass+": "+RADIX_WG_SCANNED_SSBO.getDataAsString("WGScanned",0,NUM_DEBUG_OUTPUTS)+"\n");
                }
                if (radixSortScatterKernel.isPreDebugSelected()) {
                    radixSortScatterKernel.addToPreDebugString("Scattering morton codes Pass "+pass+": "+RADIX_BUCKET_TOTALS_SSBO.getDataAsString("BucketTotals",0,NUM_DEBUG_OUTPUTS)+"\n");
                }
            }

            // Phase 3: Scatter
            radixSortScatterKernel.run();
            if (debug) {
                GPUSimulation.checkGLError("radixSortScatterPass" + pass);
                glFinish();
                radixSortScatterTime += System.nanoTime() - radixSortScatterStartTime;
                if (radixSortScatterKernel.isPostDebugSelected()) {
                    radixSortScatterKernel.addToPostDebugString("Scattered morton codes Pass "+pass+": "+RADIX_WG_SCANNED_SSBO.getDataAsString("WGScanned",0,NUM_DEBUG_OUTPUTS)+"\n");
                }
            }
            

            swapMortonAndIndexBuffers();
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
            if (buildBinaryRadixTreeKernel.isPreDebugSelected()) {
                buildBinaryRadixTreeKernel.setPreDebugString("Building binary radix tree"+INTERNAL_NODES_SSBO.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n"+LEAF_NODES_SSBO.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n");
            }
        }
        buildBinaryRadixTreeKernel.run();
        if (debug) {
            GPUSimulation.checkGLError("buildBinaryRadixTree");
            glFinish();
            buildTreeTime = System.nanoTime() - buildTreeTime;
            if (buildBinaryRadixTreeKernel.isPostDebugSelected()) {
                buildBinaryRadixTreeKernel.setPostDebugString("Built binary radix tree"+INTERNAL_NODES_SSBO.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n"+LEAF_NODES_SSBO.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n");
            }
        }
    }
    
    /**
     * Compute the center of mass and location of the nodes in the tree. In bh_reduce_more_efficient.comp
     */
    private void computeCOMAndLocation() {
        if (debug) {
            initLeavesTime = System.nanoTime();
            if (initLeavesKernel.isPreDebugSelected()) {
                initLeavesKernel.setPreDebugString("Computing center of mass and location of leaf nodes in the tree"+INTERNAL_NODES_SSBO.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n"+LEAF_NODES_SSBO.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n");
            }
        }

        initLeavesKernel.run();

        if (debug) {
            GPUSimulation.checkGLError("initLeaves");
            glFinish();
            initLeavesTime = System.nanoTime() - initLeavesTime;
            if (initLeavesKernel.isPostDebugSelected()) {
                initLeavesKernel.setPostDebugString("Computed center of mass and location of leaf nodes in the tree"+INTERNAL_NODES_SSBO.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n"+LEAF_NODES_SSBO.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n");
            }
            if (propagateNodesKernel.isPreDebugSelected()) {
                propagateNodesKernel.setPreDebugString("Propagating nodes in the tree"+INTERNAL_NODES_SSBO.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n"+LEAF_NODES_SSBO.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n");
            }
            propagateNodesTime = System.nanoTime();

        }
        int lastThreads = 0;

        for (COMPropagationPassNumber = 0; COMPropagationPassNumber < PROPAGATE_NODES_ITERATIONS; COMPropagationPassNumber++) {
            if (debug) {
                if (propagateNodesKernel.isPreDebugSelected()) {
                    propagateNodesKernel.addToPreDebugString("Propagating nodes in the tree Pass "+COMPropagationPassNumber+": "+INTERNAL_NODES_SSBO.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n"+LEAF_NODES_SSBO.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n");
                }
            }
            propagateNodesKernel.run();
            if (debug) {
                GPUSimulation.checkGLError("propagateNodesPass" + COMPropagationPassNumber);
                glFinish();
                if (propagateNodesKernel.isPostDebugSelected()) {
                    propagateNodesKernel.addToPostDebugString("Propagated nodes in the tree Pass "+COMPropagationPassNumber+": "+INTERNAL_NODES_SSBO.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n"+LEAF_NODES_SSBO.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n");
                }
            }
            swapPropagateWorkQueueBuffers();
            //int workedThreads =DEBUG_SSBO.getHeaderAsInts()[1];

            // System.out.println(Node.getTree(NODES_SSBO.getBuffer().asIntBuffer(), numBodies(), 10));
            //System.out.println("Operations last interation:"+passes+" : "  + (workedThreads-lastThreads) + " : using " + Math.max(1,(int)((numBodies() - 1)/Math.pow(2, passes))) + "  threads");
            //System.out.println(WORK_QUEUE_SSBO.getHeader());
            //System.out.println(WORK_QUEUE_B_SSBO.getHeader());
            // lastThreads = workedThreads;

            // try {
            //     System.in.read();
            // } catch (Exception e) {
            //     // Ignore exception
            // }

        }
        
        if (debug) {
            if (propagateNodesKernel.isPostDebugSelected()) {
                propagateNodesKernel.setPostDebugString("Propagated nodes in the tree"+INTERNAL_NODES_SSBO.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n"+LEAF_NODES_SSBO.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n");
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
            if (computeForceKernel.isPreDebugSelected()) {
                computeForceKernel.setPreDebugString("Computing force on each body: "+SWAPPING_BODIES_IN_SSBO.getDataAsString("BodiesIn",0,NUM_DEBUG_OUTPUTS)+"\n" + SWAPPING_BODIES_OUT_SSBO.getDataAsString("BodiesOut",0,NUM_DEBUG_OUTPUTS)+"\n");// + INTERNAL_NODES_SSBO.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n" + LEAF_NODE
            }
        }

        computeForceKernel.run();
        if (debug) {
            GPUSimulation.checkGLError("computeForce");
            glFinish();
            computeForceTime = System.nanoTime() - computeForceTime;
            if (computeForceKernel.isPostDebugSelected()) {
                computeForceKernel.setPostDebugString("Computed force on each body: "+SWAPPING_BODIES_IN_SSBO.getDataAsString("BodiesIn",0,NUM_DEBUG_OUTPUTS)+"\n" + SWAPPING_BODIES_OUT_SSBO.getDataAsString("BodiesOut",0,NUM_DEBUG_OUTPUTS)+"\n");// + INTERNAL_NODES_SSBO.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n" + LEAF_NODES_SSBO.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n");
            }
        }
    }

    /**
     * Merge the bodies, leaving empty bodies where they are. In bh_merge.comp
     */
    private void mergeBodies() {
        if (debug) {
            mergeBodiesTime = System.nanoTime();
            if (mergeBodiesKernel.isPreDebugSelected()) {
                mergeBodiesKernel.setPreDebugString("Merging bodies: "+MERGE_QUEUE_SSBO.getDataAsString("MergeQueue",0,NUM_DEBUG_OUTPUTS)+"\n" + SWAPPING_BODIES_OUT_SSBO.getDataAsString("BodiesOut",0,NUM_DEBUG_OUTPUTS)+"\n");// + INTERNAL_NODES_SSBO.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n" + LEAF_NODES_SSBO.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n");
            }
        }
        mergeBodiesKernel.run();
        if (debug) {
            GPUSimulation.checkGLError("mergeBodies");
            glFinish();
            mergeBodiesTime = System.nanoTime() - mergeBodiesTime;
            if (mergeBodiesKernel.isPostDebugSelected()) {
                mergeBodiesKernel.setPostDebugString("Merged bodies: "+MERGE_QUEUE_SSBO.getDataAsString("MergeQueue",0,NUM_DEBUG_OUTPUTS)+"\n" + SWAPPING_BODIES_OUT_SSBO.getDataAsString("BodiesOut",0,NUM_DEBUG_OUTPUTS)+"\n");// + INTERNAL_NODES_SSBO.getDataAsString("InternalNodes",0,NUM_DEBUG_OUTPUTS)+"\n" + LEAF_NODES_SSBO.getDataAsString("LeafNodes",0,NUM_DEBUG_OUTPUTS)+"\n");
            }
        }
    }

    /**
     * Initialize the compute swapping buffers.
     */
    private void initComputeSwappingBuffers() {
        // Create Swapping SSBOs.
        // These are used as a double buffer for bodies 
        SWAPPING_BODIES_IN_SSBO = FIXED_BODIES_IN_SSBO;
        SWAPPING_BODIES_IN_SSBO.setName("SWAPPING_BODIES_IN_SSBO");
        ssbos.put(SWAPPING_BODIES_IN_SSBO.getName(), SWAPPING_BODIES_IN_SSBO);
        SWAPPING_BODIES_OUT_SSBO = FIXED_BODIES_OUT_SSBO;
        SWAPPING_BODIES_OUT_SSBO.setName("SWAPPING_BODIES_OUT_SSBO");
        ssbos.put(SWAPPING_BODIES_OUT_SSBO.getName(), SWAPPING_BODIES_OUT_SSBO);

        // These exist to do the radix sort and dead body paritioning.

        SWAPPING_MORTON_IN_SSBO = FIXED_MORTON_IN_SSBO;
        SWAPPING_MORTON_IN_SSBO.setName("SWAPPING_MORTON_IN_SSBO");
        ssbos.put(SWAPPING_MORTON_IN_SSBO.getName(), SWAPPING_MORTON_IN_SSBO);
        SWAPPING_MORTON_OUT_SSBO = FIXED_MORTON_OUT_SSBO;
        SWAPPING_MORTON_OUT_SSBO.setName("SWAPPING_MORTON_OUT_SSBO");
        ssbos.put(SWAPPING_MORTON_OUT_SSBO.getName(), SWAPPING_MORTON_OUT_SSBO);
        SWAPPING_INDEX_IN_SSBO = FIXED_INDEX_IN_SSBO;
        SWAPPING_INDEX_IN_SSBO.setName("SWAPPING_INDEX_IN_SSBO");
        ssbos.put(SWAPPING_INDEX_IN_SSBO.getName(), SWAPPING_INDEX_IN_SSBO);
        SWAPPING_INDEX_OUT_SSBO = FIXED_INDEX_OUT_SSBO;
        SWAPPING_INDEX_OUT_SSBO.setName("SWAPPING_INDEX_OUT_SSBO");
        ssbos.put(SWAPPING_INDEX_OUT_SSBO.getName(), SWAPPING_INDEX_OUT_SSBO);

        // These are used for the work queue for propagating node data up the tree.
        SWAPPING_PROPAGATE_WORK_QUEUE_IN_SSBO = FIXED_PROPAGATE_WORK_QUEUE_IN_SSBO;
        SWAPPING_PROPAGATE_WORK_QUEUE_IN_SSBO.setName("SWAPPING_PROPAGATE_WORK_QUEUE_IN_SSBO");
        ssbos.put(SWAPPING_PROPAGATE_WORK_QUEUE_IN_SSBO.getName(), SWAPPING_PROPAGATE_WORK_QUEUE_IN_SSBO);
        SWAPPING_PROPAGATE_WORK_QUEUE_OUT_SSBO = FIXED_PROPAGATE_WORK_QUEUE_OUT_SSBO;
        SWAPPING_PROPAGATE_WORK_QUEUE_OUT_SSBO.setName("SWAPPING_PROPAGATE_WORK_QUEUE_OUT_SSBO");
        ssbos.put(SWAPPING_PROPAGATE_WORK_QUEUE_OUT_SSBO.getName(), SWAPPING_PROPAGATE_WORK_QUEUE_OUT_SSBO);
    }
    
    /**
     * Swap the body buffers.
     */
    private void swapBodyBuffers() {
        // Swap source and destination buffers for next iteration
        int tmpIn = SWAPPING_BODIES_IN_SSBO.getBufferLocation();
        SWAPPING_BODIES_IN_SSBO.setBufferLocation(SWAPPING_BODIES_OUT_SSBO.getBufferLocation());
        SWAPPING_BODIES_OUT_SSBO.setBufferLocation(tmpIn);
    }

    /**
     * Swap the morton and index buffers.
     */
    private void swapMortonAndIndexBuffers() {
        // Swap input/output buffers for next pass of radix sort and the one pass of dead body paritioning.
        int tempMortonIn = SWAPPING_MORTON_IN_SSBO.getBufferLocation();
        int tempIndexIn = SWAPPING_INDEX_IN_SSBO.getBufferLocation();
        SWAPPING_MORTON_IN_SSBO.setBufferLocation(SWAPPING_MORTON_OUT_SSBO.getBufferLocation());
        SWAPPING_INDEX_IN_SSBO.setBufferLocation(SWAPPING_INDEX_OUT_SSBO.getBufferLocation());
        SWAPPING_MORTON_OUT_SSBO.setBufferLocation(tempMortonIn);
        SWAPPING_INDEX_OUT_SSBO.setBufferLocation(tempIndexIn);
    }

    /**
     * Swap the propagate work queue buffers.
     */
    private void swapPropagateWorkQueueBuffers() {
        int tmpIn = SWAPPING_PROPAGATE_WORK_QUEUE_IN_SSBO.getBufferLocation();
        SWAPPING_PROPAGATE_WORK_QUEUE_IN_SSBO.setBufferLocation(SWAPPING_PROPAGATE_WORK_QUEUE_OUT_SSBO.getBufferLocation());
        SWAPPING_PROPAGATE_WORK_QUEUE_OUT_SSBO.setBufferLocation(tmpIn);
    }

    /**
     * Get the output body buffer.
     */
    public SSBO getOutputSSBO() {
        return SWAPPING_BODIES_IN_SSBO;
    }

    /**
     * Get the nodes buffer.
     */
    public SSBO getLeafNodesSSBO() {
        return LEAF_NODES_SSBO;
    }

    /**
     * Get the nodes buffer.
     */
    public SSBO getInternalNodesSSBO() {
        return INTERNAL_NODES_SSBO;
    }

    /**
     * Get the values buffer.
     */
    public SSBO getValuesSSBO() {
        return SIMULATION_VALUES_SSBO;
    }

    /**
     * Get the number of work groups required for the given number of bodies.
     */
    private int numGroups() {
        return (numBodies() + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
    }


    /**
     * Pack the values to a float buffer.
     */
    public ByteBuffer packValues() {

        //layout(std430, binding = 1) buffer SimulationValues { uint numBodies; uint initialNumBodies; uint justDied; uint justMerged; AABB bounds; } sim;
        ByteBuffer buf = BufferUtils.createByteBuffer(8*Integer.BYTES+8*Float.BYTES+100*Integer.BYTES+100*Float.BYTES);
        buf.putInt(numBodies()); // numBodies
        buf.putInt(numBodies()); // initialNumBodies
        buf.putInt(0); // justDied
        buf.putInt(0); // merged
        buf.putInt(0); // outOfBounds
        buf.putInt(0); // pad0
        buf.putInt(0); // pad1
        buf.putInt(0); // pad2
        buf.putFloat(bounds[0][0]).putFloat(bounds[0][1]).putFloat(bounds[0][2]).putInt(0); // bounds
        buf.putFloat(bounds[1][0]).putFloat(bounds[1][1]).putFloat(bounds[1][2]).putInt(0); // bounds
    
        // uintDebug[100]
        for (int i = 0; i < 100; i++) buf.putInt(0);
    
        // floatDebug[100]
        for (int i = 0; i < 100; i++) buf.putFloat(0f);
    
        buf.flip();
        return buf;
    }

    /**
     * Upload the planet data to the GPU.
     */
    public void uploadPlanetsData(PlanetGenerator planetGenerator, SSBO bodiesSSBO) {

        GPUSimulation.checkGLError("before uploadPlanetsData");

        // Assumes buffers are already correctly sized
        bodiesSSBO.bind();
    
        int offset = 0;
        System.out.print("Uploading planet data:");
        double percentUploaded = 0;
        int displayProgress = 5;
        int lastDisplayed = -1;
        while (planetGenerator.hasNext()) {
            percentUploaded = (int)((double)planetGenerator.planetsGenerated/planetGenerator.getNumPlanets()*100);
            if (percentUploaded % displayProgress == 0 && percentUploaded != lastDisplayed) {
                lastDisplayed = (int)percentUploaded;
                System.out.print(" "+percentUploaded+"%");
            }
            List<Planet> planets = planetGenerator.nextChunk();
            ByteBuffer data = Body.packPlanets(planets);
            glBufferSubData(GL_SHADER_STORAGE_BUFFER, offset, data);
            offset += data.capacity();
        }

        System.out.println(" 100%");

        GPUSimulation.checkGLError("after uploadPlanetsData");

    
        bodiesSSBO.unbind();
    }


    /* --------- Cleanup --------- */
    /**
     * Cleanup the compute shaders and SSBOs.
     */
    public void cleanup() {
        for (ComputeShader shader : computeShaders) {
            shader.delete();
        }
        for (SSBO ssbo : ssbos.values()) {
            ssbo.delete();
        }
    }

    /* --------- Debugging --------- */


    /**
     * Print the profiling information.
     */
    private String printProfiling() {
        long totalTime = mortonTime + radixSortTime + buildTreeTime + propagateNodesTime + computeForceTime + deadTime + renderingTime + resetTime + mergeBodiesTime;
        long percentRendering = (renderingTime * 100) / totalTime;
        long percentReset = (resetTime * 100) / totalTime;
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
               totalTime/oneMillion + " ms" +":Total\n";
    }


    /**
     * Check the morton codes debug.
     */
    private void checkMortonCodes(SSBO mortonBuffer, boolean print) {
        boolean correctPartitioning = true;
        boolean correctSorting = true;
        long[] mortonCodes = new long[numBodies()];
        LongBuffer mortonBufferLong = mortonBuffer.getBuffer().asLongBuffer();
        long[] sortedMortonCodes = new long[numBodies()];
        for (int i = 0; i < numBodies(); i++) {
            mortonCodes[i] = mortonBufferLong.get(i);
            sortedMortonCodes[i] = mortonCodes[i];
            if (print) {
                System.out.println("Morton Code: " + i + " " + mortonCodes[i]);
            }
        }
        Arrays.sort(sortedMortonCodes);
        int deadIndex = 0;
        while (deadIndex < numBodies() && sortedMortonCodes[deadIndex] == -1) {
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
        FIXED_MORTON_IN_SSBO.bind();
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
        FIXED_MORTON_IN_SSBO.bind();
        ByteBuffer mortonBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer mortonData = mortonBuffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        SWAPPING_INDEX_IN_SSBO.bind();
        ByteBuffer indexBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer indexData = indexBuffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        RADIX_WG_HIST_SSBO.bind();
        ByteBuffer wgHistBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer wgHistData = wgHistBuffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        RADIX_WG_SCANNED_SSBO.bind();
        ByteBuffer wgScannedBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer wgScannedData = wgScannedBuffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        RADIX_BUCKET_TOTALS_SSBO.bind();
        ByteBuffer bucketTotalsBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer bucketTotalsData = bucketTotalsBuffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        FIXED_MORTON_OUT_SSBO.bind();
        ByteBuffer mortonOutBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer mortonOutData = mortonOutBuffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        FIXED_INDEX_OUT_SSBO.bind();
        ByteBuffer indexOutBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer indexOutData = indexOutBuffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        for (int i = 0; i < Math.min(10, numBodies()); i++) {
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
            FIXED_MORTON_IN_SSBO.bind();
            ByteBuffer mortonBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            IntBuffer mortonData = mortonBuffer.asIntBuffer();
            System.out.println("Sorted Morton codes:");
            for (int i = 0; i < Math.min(100, numBodies()); i++) {
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
            FIXED_INDEX_OUT_SSBO.bind();
            ByteBuffer indexBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            IntBuffer indexData = indexBuffer.asIntBuffer();
            System.out.println("Sorted body indices:");
            for (int i = 0; i < Math.min(100, numBodies()); i++) {
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
