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


    // Uniforms
    public Map<String, Uniform<?>> uniforms;
    private Uniform<Integer> numWorkGroupsUniform;
    private Uniform<Float> softeningUniform;
    private Uniform<Float> thetaUniform;
    private Uniform<Float> dtUniform;
    private Uniform<Float> elasticityUniform;
    private Uniform<Float> densityUniform;
    private Uniform<Float> restitutionUniform;
    private Uniform<Boolean> collisionUniform;
    private Uniform<Integer> passShiftUniform;
    private Uniform<Integer> resetKernelFirstPassUniform;

    // Uniform local variables
    private int radixSortPassShift;
    private int COMPropagationPassNumber;
    private boolean resetKernelFirstPass;

    // Compute Shaders
    private List<ComputeShader> computeShaders;
    private ComputeShader initKernel; // bh_init.comp
    private ComputeShader resetKernel; // bh_reset.comp
    private ComputeShader mortonKernel; // bh_morton.comp
    private ComputeShader deadCountKernel; // bh_dead.comp
    private ComputeShader deadExclusiveScanKernel; // bh_dead.comp
    private ComputeShader deadScatterKernel; // bh_dead.comp
    private ComputeShader radixSortHistogramKernel; // bh_radix.comp
    private ComputeShader radixSortParallelScanKernel; // bh_radix.comp
    private ComputeShader radixSortExclusiveScanKernel; // bh_radix.comp
    private ComputeShader radixSortScatterKernel; // bh_radix.comp
    private ComputeShader buildBinaryRadixTreeKernel; // bh_tree.comp
    private ComputeShader initLeavesKernel; // bh_reduce.comp
    private ComputeShader propagateNodesKernel; // bh_reduce.comp
    private ComputeShader computeForceKernel; // bh_force.comp
    private ComputeShader mergeBodiesKernel; // bh_merge.comp
    private ComputeShader debugKernel; // bh_debug.comp

    // SSBOs

    // public static final int NODES_SSBO_BINDING = 0;
    // public static final int SIMULATION_VALUES_SSBO_BINDING = 1;
    // public static final int BODIES_IN_SSBO_BINDING = 2;
    // public static final int BODIES_OUT_SSBO_BINDING = 3;
    // public static final int MORTON_IN_SSBO_BINDING = 4;
    // public static final int MORTON_OUT_SSBO_BINDING = 5;
    // public static final int INDEX_IN_SSBO_BINDING = 6;
    // public static final int INDEX_OUT_SSBO_BINDING = 7;
    // public static final int WORK_QUEUE_IN_SSBO_BINDING = 8;
    // public static final int WORK_QUEUE_OUT_SSBO_BINDING = 9;
    // public static final int RADIX_WG_HIST_SSBO_BINDING = 10;
    // public static final int RADIX_WG_SCANNED_SSBO_BINDING = 11;
    // public static final int RADIX_BUCKET_TOTALS_SSBO_BINDING = 12;
    // public static final int MERGE_QUEUE_SSBO_BINDING = 13;
    // public static final int MERGE_BODY_LOCKS_SSBO_BINDING = 14;
    // public static final int DEBUG_SSBO_BINDING = 15;
    public Map<String, SSBO> ssbos;
    private SSBO NODES_SSBO;
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
    private SSBO DEBUG_SSBO;

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
        long nodesSize = Node.STRUCT_SIZE * Integer.BYTES * (2L * numBodies() - 1L);
        long maxBlock = glGetInteger(GL_MAX_SHADER_STORAGE_BLOCK_SIZE);
        System.out.println("Node space left: " + (maxBlock - nodesSize));
        System.out.println("Max block: " + maxBlock);

        if (nodesSize > maxBlock) {

            throw new RuntimeException("Not enough node space (your simulation is too large) Approximate max bodies: " + (maxBlock / (2*Node.STRUCT_SIZE * Integer.BYTES)));
        }

        checkGLError("before initComputeSSBOs");
        
        //this is a list of all the SSBOs that are used in the algorithm.
        ssbos = new HashMap<>();

        //These are the fixed SSBOs that point to the bodies in and out buffers.
        System.out.println("numBodies: " + numBodies());
        FIXED_BODIES_IN_SSBO = new SSBO(SSBO.BODIES_IN_SSBO_BINDING, () -> {
            return numBodies()*Body.STRUCT_SIZE*Float.BYTES;
        }, "FIXED_BODIES_IN_SSBO", Body.STRUCT_SIZE, Body.bodyTypes, Body.HEADER_SIZE, null);
        ssbos.put(FIXED_BODIES_IN_SSBO.getName(), FIXED_BODIES_IN_SSBO);

        FIXED_BODIES_OUT_SSBO = new SSBO(SSBO.BODIES_OUT_SSBO_BINDING, () -> {
            return numBodies()*Body.STRUCT_SIZE*Float.BYTES;
        }, "FIXED_BODIES_OUT_SSBO", Body.STRUCT_SIZE, Body.bodyTypes, Body.HEADER_SIZE, null);
        ssbos.put(FIXED_BODIES_OUT_SSBO.getName(), FIXED_BODIES_OUT_SSBO);

        //These are the fixed SSBOs that point to the morton and index buffers.
        //They are intialized with the correct sizes.
        FIXED_MORTON_IN_SSBO = new SSBO(SSBO.MORTON_IN_SSBO_BINDING, () -> {
            return numBodies() * Long.BYTES;
        }, "FIXED_MORTON_IN_SSBO", 1, new VariableType[] { VariableType.UINT64 });
        ssbos.put(FIXED_MORTON_IN_SSBO.getName(), FIXED_MORTON_IN_SSBO);

        FIXED_INDEX_IN_SSBO = new SSBO(SSBO.INDEX_IN_SSBO_BINDING, () -> {
            return numBodies() * Integer.BYTES;
        }, "FIXED_INDEX_IN_SSBO", 1, new VariableType[] { VariableType.UINT });
        ssbos.put(FIXED_INDEX_IN_SSBO.getName(), FIXED_INDEX_IN_SSBO);

        NODES_SSBO = new SSBO(SSBO.NODES_SSBO_BINDING, () -> {
            return (2 * numBodies() - 1) * Node.STRUCT_SIZE * Integer.BYTES;
        }, "NODES_SSBO", Node.STRUCT_SIZE, Node.nodeTypes);
        ssbos.put(NODES_SSBO.getName(), NODES_SSBO);
        //This is the SSBO that holds values that are used in different shaders
        SIMULATION_VALUES_SSBO = new SSBO(SSBO.SIMULATION_VALUES_SSBO_BINDING, () -> {
            return packValues();
        }, "VALUES_SSBO", 1, new VariableType[] { VariableType.UINT });
        ssbos.put(SIMULATION_VALUES_SSBO.getName(), SIMULATION_VALUES_SSBO);

        //This is the SSBO that holds the histogram of the radix sort.
        RADIX_WG_HIST_SSBO = new SSBO(SSBO.RADIX_WG_HIST_SSBO_BINDING, () -> {
            return numGroups() * (1+NUM_RADIX_BUCKETS) * Integer.BYTES;
        }, "WG_HIST_SSBO", 1, new VariableType[] { VariableType.UINT });
        ssbos.put(RADIX_WG_HIST_SSBO.getName(), RADIX_WG_HIST_SSBO);

        //This is the SSBO that holds the scanned histogram of the radix sort.
        RADIX_WG_SCANNED_SSBO = new SSBO(SSBO.RADIX_WG_SCANNED_SSBO_BINDING, () -> {
            return Integer.BYTES + numGroups() * (1+NUM_RADIX_BUCKETS) * Integer.BYTES + Integer.BYTES;
        }, "WG_SCANNED_SSBO", 1, new VariableType[] { VariableType.UINT }, 4, new VariableType[] { VariableType.UINT });
        ssbos.put(RADIX_WG_SCANNED_SSBO.getName(), RADIX_WG_SCANNED_SSBO);

        //This is the SSBO that holds the total number of bodies in each bucket of the radix sort.
        RADIX_BUCKET_TOTALS_SSBO = new SSBO(SSBO.RADIX_BUCKET_TOTALS_SSBO_BINDING, () -> {
            return NUM_RADIX_BUCKETS * Integer.BYTES * 2;
        }, "BUCKET_TOTALS_SSBO", 1, new VariableType[] { VariableType.UINT });
        ssbos.put(RADIX_BUCKET_TOTALS_SSBO.getName(), RADIX_BUCKET_TOTALS_SSBO);

        //These are the fixed SSBOs that point to the morton and index buffers after the radix sort.
        //They are intialized with the correct sizes.
        FIXED_MORTON_OUT_SSBO = new SSBO(SSBO.MORTON_OUT_SSBO_BINDING, () -> {
            return numBodies() * Long.BYTES;
        }, "FIXED_MORTON_OUT_SSBO", 1, new VariableType[] { VariableType.UINT64 });
        ssbos.put(FIXED_MORTON_OUT_SSBO.getName(), FIXED_MORTON_OUT_SSBO);

        FIXED_INDEX_OUT_SSBO = new SSBO(SSBO.INDEX_OUT_SSBO_BINDING, () -> {
            return numBodies() * Integer.BYTES;
        }, "FIXED_INDEX_OUT_SSBO", 1, new VariableType[] { VariableType.UINT });
        ssbos.put(FIXED_INDEX_OUT_SSBO.getName(), FIXED_INDEX_OUT_SSBO);

        //This is the SSBO that holds the work queue.
        //It is intialized with the correct sizes.
        FIXED_PROPAGATE_WORK_QUEUE_IN_SSBO = new SSBO(SSBO.PROPAGATE_WORK_QUEUE_IN_SSBO_BINDING, () -> {
            return (4 + numBodies()) * Integer.BYTES;
        }, "WORK_QUEUE_SSBO", 1, new VariableType[] { VariableType.UINT }, 16, new VariableType[] { VariableType.UINT, VariableType.UINT, VariableType.UINT, VariableType.UINT });
        ssbos.put(FIXED_PROPAGATE_WORK_QUEUE_IN_SSBO.getName(), FIXED_PROPAGATE_WORK_QUEUE_IN_SSBO);

        //This is the SSBO that holds the work queue for the second pass.
        //It is intialized with the correct sizes.
        FIXED_PROPAGATE_WORK_QUEUE_OUT_SSBO = new SSBO(SSBO.PROPAGATE_WORK_QUEUE_OUT_SSBO_BINDING, () -> {
            return (4 + numBodies()) * Integer.BYTES;
        }, "WORK_QUEUE_B_SSBO", 1, new VariableType[] { VariableType.UINT }, 16, new VariableType[] { VariableType.UINT, VariableType.UINT, VariableType.UINT, VariableType.UINT });
        ssbos.put(FIXED_PROPAGATE_WORK_QUEUE_OUT_SSBO.getName(), FIXED_PROPAGATE_WORK_QUEUE_OUT_SSBO);

        MERGE_QUEUE_SSBO = new SSBO(SSBO.MERGE_QUEUE_SSBO_BINDING, () -> {
            return Math.max(2*Integer.BYTES, 2*Integer.BYTES+numBodies() * 2 * Integer.BYTES);
        }, "MERGE_QUEUE_SSBO", 2, new VariableType[] { VariableType.UINT, VariableType.UINT }, 8, new VariableType[] {VariableType.UINT});
        ssbos.put(MERGE_QUEUE_SSBO.getName(), MERGE_QUEUE_SSBO);

        DEBUG_SSBO = new SSBO(SSBO.DEBUG_SSBO_BINDING, () -> {
            return 100 * Integer.BYTES + 100 * Float.BYTES;
        }, "DEBUG_SSBO", 1, new VariableType[] { VariableType.FLOAT }, 100, new VariableType[] { VariableType.UINT });
        ssbos.put(DEBUG_SSBO.getName(), DEBUG_SSBO);

        MERGE_BODY_LOCKS_SSBO = new SSBO(SSBO.MERGE_BODY_LOCKS_SSBO_BINDING, () -> {
            return numBodies() * Integer.BYTES;
        }, "BODY_LOCKS_SSBO", 1, new VariableType[] { VariableType.UINT });
        ssbos.put(MERGE_BODY_LOCKS_SSBO.getName(), MERGE_BODY_LOCKS_SSBO);

        checkGLError("after initComputeSSBOs");

        for (SSBO ssbo : ssbos.values()) {
            System.out.println("Creating buffer for " + ssbo.getName());
            ssbo.createBufferData();
            checkGLError("after createBufferData for " + ssbo.getName());
        }

        checkGLError("after createBufferData");

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
        uniforms.put("density", densityUniform);
        uniforms.put("restitution", restitutionUniform);
        uniforms.put("collision", collisionUniform);
        uniforms.put("passShift", passShiftUniform);
        uniforms.put("firstPass", resetKernelFirstPassUniform);

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

        densityUniform = new Uniform<Float>("density", () -> {
            return (float)Settings.getInstance().getDensity();
        }, VariableType.FLOAT);

        restitutionUniform = new Uniform<Float>("restitution", () -> {
            return 0.2f;
        }, VariableType.FLOAT);

        passShiftUniform = new Uniform<Integer>("passShift", () -> {
            return radixSortPassShift;
        }, VariableType.UINT);

        collisionUniform = new Uniform<Boolean>("collision", () -> {
            return false;
        }, VariableType.BOOL);

        resetKernelFirstPassUniform = new Uniform<Integer>("firstPass", () -> {
            return resetKernelFirstPass ? 1 : 0;
        }, VariableType.UINT);

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
        mortonKernel = new ComputeShader("KERNEL_MORTON", this);
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
            "DEBUG_SSBO"
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
            "DEBUG_SSBO",
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
            "DEBUG_SSBO"
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
        radixSortParallelScanKernel = new ComputeShader("KERNEL_RADIX_PARALLEL_SCAN", this);
        radixSortParallelScanKernel.setUniforms(new Uniform[] {
            numWorkGroupsUniform
        });
        radixSortParallelScanKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "WG_HIST_SSBO",
            "WG_SCANNED_SSBO",
            "BUCKET_TOTALS_SSBO",
            "SWAPPING_BODIES_IN_SSBO"
        });
        radixSortParallelScanKernel.setXWorkGroupsFunction(() -> {
            return NUM_RADIX_BUCKETS;
        });
        computeShaders.add(radixSortParallelScanKernel);    
        radixSortExclusiveScanKernel = new ComputeShader("KERNEL_RADIX_EXCLUSIVE_SCAN", this);
        radixSortExclusiveScanKernel.setUniforms(new Uniform[] {
            numWorkGroupsUniform
        });
        radixSortExclusiveScanKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "BUCKET_TOTALS_SSBO",
            "SWAPPING_BODIES_IN_SSBO"
        });
        radixSortExclusiveScanKernel.setXWorkGroupsFunction(() -> {
            return NUM_RADIX_BUCKETS;
        });
        computeShaders.add(radixSortExclusiveScanKernel);
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


        buildBinaryRadixTreeKernel = new ComputeShader("KERNEL_BUILD_BINARY_RADIX_TREE", this);
        buildBinaryRadixTreeKernel.setUniforms(new Uniform[] {

        });
        
        buildBinaryRadixTreeKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "SWAPPING_MORTON_IN_SSBO",
            "SWAPPING_INDEX_IN_SSBO",
            "NODES_SSBO",
            "SWAPPING_BODIES_IN_SSBO"
        });

        buildBinaryRadixTreeKernel.setXWorkGroupsFunction(() -> {
            int numInternalNodes = numBodies() - 1;
            int internalNodeGroups = (numInternalNodes + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
            return internalNodeGroups;
        });
        computeShaders.add(buildBinaryRadixTreeKernel);
        //Compute COM and Location Kernels

        initLeavesKernel = new ComputeShader("KERNEL_INIT_LEAVES", this);

        initLeavesKernel.setUniforms(new Uniform[] {

        }); 

        initLeavesKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "SWAPPING_BODIES_IN_SSBO",
            "NODES_SSBO",
            "SWAPPING_MORTON_IN_SSBO",
            "SWAPPING_INDEX_IN_SSBO",
            "WORK_QUEUE_SSBO",
        });

        initLeavesKernel.setXWorkGroupsFunction(() -> {
            return numGroups();
        });
        computeShaders.add(initLeavesKernel);
        resetKernel = new ComputeShader("KERNEL_RESET", this);

        resetKernel.setUniforms(new Uniform[] {
            resetKernelFirstPassUniform
        });

        resetKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "WORK_QUEUE_SSBO",
            "MERGE_QUEUE_SSBO",
            "SWAPPING_BODIES_IN_SSBO",
            "SWAPPING_BODIES_OUT_SSBO"
        });

        resetKernel.setXWorkGroupsFunction(() -> {
            return 1;
        });
        computeShaders.add(resetKernel);
        propagateNodesKernel = new ComputeShader("KERNEL_PROPAGATE_NODES", this);

        propagateNodesKernel.setUniforms(new Uniform[] {
        });

        propagateNodesKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "NODES_SSBO",
            "WORK_QUEUE_SSBO",
            "WORK_QUEUE_B_SSBO",
            "SWAPPING_BODIES_IN_SSBO",
            "DEBUG_SSBO"
            
        });
        propagateNodesKernel.setXWorkGroupsFunction(() -> {
            //int maxPossibleNodes = Math.max(3*WORK_GROUP_SIZE+1,(int)((numBodies() - 1)));
            int maxPossibleNodes = Math.max(3*WORK_GROUP_SIZE+1,(int)((numBodies() - 1)/Math.pow(2,COMPropagationPassNumber)));
            int workGroups = (maxPossibleNodes + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
            return workGroups;
        });
        computeShaders.add(propagateNodesKernel);
        computeForceKernel = new ComputeShader("KERNEL_COMPUTE_FORCE", this);

        computeForceKernel.setUniforms(new Uniform[] {
            thetaUniform,
            dtUniform,
            elasticityUniform,
            densityUniform,
        });

        computeForceKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "SWAPPING_BODIES_IN_SSBO",
            "SWAPPING_BODIES_OUT_SSBO",
            "NODES_SSBO",
            "SWAPPING_INDEX_IN_SSBO",
            "MERGE_QUEUE_SSBO",
        });

        computeForceKernel.setXWorkGroupsFunction(() -> {
            return numGroups();
        });
        computeShaders.add(computeForceKernel);
        mergeBodiesKernel = new ComputeShader("KERNEL_MERGE", this);
        mergeBodiesKernel.setUniforms(new Uniform[] {
            
        });
        mergeBodiesKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "SWAPPING_BODIES_IN_SSBO",
            "SWAPPING_BODIES_OUT_SSBO",
            "MERGE_QUEUE_SSBO",
            "DEBUG_SSBO",
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
            "DEBUG_SSBO",
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
            checkGLError("rendering");
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
        }
        resetKernelFirstPass = true;
        resetKernel.run();
        if (debug) {
            checkGLError("resetValuesPass");
            glFinish();
            resetTime = System.nanoTime() - resetStartTime;
        }
    }

    /**
     * Decrement the number of dead bodies from the total number of bodies. In bh_reset.comp
     */
    private void decrementDeadBodies() {
        long decrementDeadBodiesStartTime = 0;
        if (debug) {
            decrementDeadBodiesStartTime = System.nanoTime();
        }
        resetKernelFirstPass = false;
        resetKernel.run();
        if (debug) {
            checkGLError("decrementDeadBodiesPass");
            glFinish();
            decrementDeadBodiesTime = System.nanoTime() - decrementDeadBodiesStartTime;
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
        }
        deadCountKernel.run();
        if (debug) {
            checkGLError("deadCount");
            glFinish();
            deadCountTime = System.nanoTime() - deadCountStartTime;
            deadExclusiveScanStartTime = System.nanoTime();
        }
        deadExclusiveScanKernel.run();
        if (debug) {
            checkGLError("deadExclusiveScan");
            glFinish();
            deadExclusiveScanTime = System.nanoTime() - deadExclusiveScanStartTime;
            deadScatterStartTime = System.nanoTime();
        }
        deadScatterKernel.run();
        if (debug) {
            checkGLError("deadScatter");
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
        }

        mortonKernel.run();
        if (debug) {
            checkGLError("generateMortonCodes");
            glFinish();
            mortonTime = System.nanoTime() - mortonTime;
        }

    }

    /**
     * Radix sort the morton codes. In bh_radix.comp
     */
    private void radixSort() {
        int numPasses = (int)Math.ceil(63.0 / 4.0);
        
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
            }

            // Phase 1: Histogram
            radixSortHistogramKernel.run();

            if (debug) {
                checkGLError("radixSortHistogramPass" + pass);
                glFinish();
                radixSortHistogramTime += System.nanoTime() - radixSortHistogramStartTime;
                radixSortScanParallelStartTime = System.nanoTime();
            }

            // Phase 2: Scan
            radixSortParallelScanKernel.run();
            if (debug) {
                checkGLError("radixSortParallelScanPass" + pass);
                glFinish();
                radixSortScanParallelTime += System.nanoTime() - radixSortScanParallelStartTime;
                radixSortScanExclusiveStartTime = System.nanoTime();
            }

            radixSortExclusiveScanKernel.run();

            if (debug) {
                checkGLError("radixSortExclusiveScanPass" + pass);
                glFinish();
                radixSortScanExclusiveTime += System.nanoTime() - radixSortScanExclusiveStartTime;
                radixSortScatterStartTime = System.nanoTime();
            }

            // Phase 3: Scatter
            radixSortScatterKernel.run();
            if (debug) {
                checkGLError("radixSortScatterPass" + pass);
                glFinish();
                radixSortScatterTime += System.nanoTime() - radixSortScatterStartTime;
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
        }
        buildBinaryRadixTreeKernel.run();
        if (debug) {
            checkGLError("buildBinaryRadixTree");
            glFinish();
            buildTreeTime = System.nanoTime() - buildTreeTime;
        }
    }
    
    /**
     * Compute the center of mass and location of the nodes in the tree. In bh_reduce_more_efficient.comp
     */
    private void computeCOMAndLocation() {
        if (debug) {
            initLeavesTime = System.nanoTime();
        }

        initLeavesKernel.run();

        if (debug) {
            checkGLError("initLeaves");
            glFinish();
            initLeavesTime = System.nanoTime() - initLeavesTime;
            propagateNodesTime = System.nanoTime();
        }
        int lastThreads = 0;

        for (COMPropagationPassNumber = 0; COMPropagationPassNumber < PROPAGATE_NODES_ITERATIONS; COMPropagationPassNumber++) {
            propagateNodesKernel.run();
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
            checkGLError("propagateNodes");
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
        }

        computeForceKernel.run();
        if (debug) {
            checkGLError("computeForce");
            glFinish();
            computeForceTime = System.nanoTime() - computeForceTime;
        }
    }

    /**
     * Merge the bodies, leaving empty bodies where they are. In bh_merge.comp
     */
    private void mergeBodies() {
        if (debug) {
            mergeBodiesTime = System.nanoTime();
        }
        mergeBodiesKernel.run();
        if (debug) {
            checkGLError("mergeBodies");
            glFinish();
            mergeBodiesTime = System.nanoTime() - mergeBodiesTime;
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
    public SSBO getNodesSSBO() {
        return NODES_SSBO;
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
        ByteBuffer buf = BufferUtils.createByteBuffer(12*4);
        buf.putInt(numBodies());
        buf.putInt(numBodies());
        buf.putInt(0);
        buf.putInt(0);
        buf.putFloat(bounds[0][0]).putFloat(bounds[0][1]).putFloat(bounds[0][2]).putInt(0);
        buf.putFloat(bounds[1][0]).putFloat(bounds[1][1]).putFloat(bounds[1][2]).putInt(0);
        buf.flip();
        return buf;
    }

    /**
     * Upload the planet data to the GPU.
     */
    public void uploadPlanetsData(PlanetGenerator planetGenerator, SSBO bodiesSSBO) {

        checkGLError("before uploadPlanetsData");

        // Assumes buffers are already correctly sized
        bodiesSSBO.bind();
    
        int offset = 0;
        System.out.print("Uploading planet data:");
        double percentUploaded = 0;
        int displayProgress = 25;
        while (planetGenerator.hasNext()) {
            percentUploaded = (int)((double)planetGenerator.planetsGenerated/planetGenerator.getNumPlanets()*100);
            if (percentUploaded % displayProgress == 0) {
                System.out.print(" "+percentUploaded+"%");
            }
            List<Planet> planets = planetGenerator.nextChunk();
            ByteBuffer data = Body.packPlanets(planets);
            glBufferSubData(GL_SHADER_STORAGE_BUFFER, offset, data);
            offset += data.capacity();
        }

        System.out.println(" 100%");

        checkGLError("after uploadPlanetsData");

    
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
     * Check for OpenGL errors.
     */
    private void checkGLError(String operation) {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            System.err.println("OpenGL Error after " + operation + ": " + error);
        }
    }

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
     * Debug the tree.
     */
    private void debugTree() {
        
        // Debug: Output buffer data
            System.out.println("=== FINAL TREE WITH COM DATA ===");
            
            // First check for stuck nodes with readyChildren < 2
            NODES_SSBO.bind();
            ByteBuffer nodeBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            IntBuffer nodeData = nodeBuffer.asIntBuffer();
            glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            
            int numLeaves = numBodies();
            int totalNodes = 2 * numBodies() - 1;

            // boolean foundStuckNodes = false;
            
            // for (int i = numLeaves; i < totalNodes; i++) { // Check internal nodes only
            //     int offset = i * Node.STRUCT_SIZE;
            //     if (offset + Node.STRUCT_SIZE - 1 < nodeData.capacity()) {
            //         int readyChildren = nodeData.get(offset + Node.READY_CHILDREN_OFFSET);
            //         if (readyChildren < 2) {
            //             foundStuckNodes = true;
            //             int childA = nodeData.get(offset + Node.CHILD_A_OFFSET);
            //             int childB = nodeData.get(offset + Node.CHILD_B_OFFSET);
            //             int parentId = nodeData.get(offset + Node.PARENT_ID_OFFSET);
            //             float mass = Float.intBitsToFloat(nodeData.get(offset + Node.COM_MASS_OFFSET));
                        
            //             // System.out.printf("STUCK Node[%d]: ready=%d childA=%d childB=%d parent=%d mass=%.3f\n", 
            //             //     i, readyChildren, 
            //             //     childA == 0xFFFFFFFF ? -1 : childA,
            //             //     childB == 0xFFFFFFFF ? -1 : childB,
            //             //     parentId == 0xFFFFFFFF ? -1 : parentId,
            //             //     mass);
                            
            //             // Check children status
            //             if (childA != 0xFFFFFFFF && childA < totalNodes) {
            //                 int childAOffset = childA * Node.STRUCT_SIZE;
            //                 if (childAOffset + Node.STRUCT_SIZE - 1 < nodeData.capacity()) {
            //                     int childAReady = childA < numLeaves ? 1 : nodeData.get(childAOffset + Node.READY_CHILDREN_OFFSET); // Leaves are always ready
            //                     float childAMass = Float.intBitsToFloat(nodeData.get(childAOffset + Node.COM_MASS_OFFSET));
            //                     System.out.printf("  Child A[%d]: ready=%d mass=%.3f%s\n", 
            //                         childA, childAReady, childAMass, childA < numLeaves ? " (leaf)" : "");
            //                 }
            //             }
            //             if (childB != 0xFFFFFFFF && childB < totalNodes) {
            //                 int childBOffset = childB * Node.STRUCT_SIZE;
            //                 if (childBOffset + Node.STRUCT_SIZE - 1 < nodeData.capacity()) {
            //                     int childBReady = childB < numLeaves ? 1 : nodeData.get(childBOffset + Node.READY_CHILDREN_OFFSET); // Leaves are always ready
            //                     float childBMass = Float.intBitsToFloat(nodeData.get(childBOffset + Node.COM_MASS_OFFSET));
            //                     System.out.printf("  Child B[%d]: ready=%d mass=%.3f%s\n", 
            //                         childB, childBReady, childBMass, childB < numLeaves ? " (leaf)" : "");
            //                 }
            //             }
            //         }
            //     }
            // }
            
            // if (!foundStuckNodes) {
            //     System.out.println("No stuck nodes found - all internal nodes have readyChildren >= 2");
            // }
            
            // glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            
            // System.out.println("Tree nodes:");
            // // Read tree nodes
            // glBindBuffer(GL_SHADER_STORAGE_BUFFER, NODES_SSBO);
        
            // Read tree nodes
            //ByteBuffer nodeBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            //IntBuffer nodeData = nodeBuffer.asIntBuffer();
            //System.out.println("Tree nodes:");
            
            //int numLeaves = planets.size();
            //int numInternalNodes = planets.size() - 1;
            //int totalNodes = 2 * planets.size() - 1;
            
            // Show first few leaf nodes (indices 0 to numBodies-1)

            //System.out.println(Node.getNodes(nodeData, 0, Math.min(20, numLeaves)));
            
            // Show internal nodes from halfway point (indices numBodies to 2*numBodies-2)
            //System.out.println(Node.getNodes(nodeData, numLeaves, Math.min(numLeaves + 20, totalNodes)));

            //glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            verifyTreeStructure(nodeData, numLeaves, totalNodes);
            NODES_SSBO.unbind();
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
