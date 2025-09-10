package com.grumbo.simulation;

import com.grumbo.gpu.*;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;

import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL43C.*;

public class BarnesHut {

    // Simulation params
    //To change these, you need to also change their definitions in the compute shader
    private static final int WORK_GROUP_SIZE = 256;
    private static final int NUM_RADIX_BUCKETS = 16; // 2^RADIX_BITS where RADIX_BITS=4

    // These can be freely changed here
    private static final int PROPAGATE_NODES_ITERATIONS = 64;
    private boolean debug;

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
    private Uniform<Boolean> firstPassUniform;

    // Uniform local variables
    private int passShift;

    // Compute Shaders
    private List<ComputeShader> computeShaders;
    private ComputeShader initKernel;
    private ComputeShader mortonKernel;
    private ComputeShader deadCountKernel;
    private ComputeShader deadExclusiveScanKernel;
    private ComputeShader deadScatterKernel;
    private ComputeShader radixSortHistogramKernel;
    private ComputeShader radixSortParallelScanKernel;
    private ComputeShader radixSortExclusiveScanKernel;
    private ComputeShader radixSortDeadExclusiveScanKernel;
    private ComputeShader radixSortScatterKernel;
    private ComputeShader radixSortDeadScatterKernel;
    private ComputeShader buildBinaryRadixTreeKernel;
    private ComputeShader initLeavesKernel;
    private ComputeShader resetKernel;
    private ComputeShader propagateNodesKernel;
    private ComputeShader computeForceKernel;
    private ComputeShader mergeBodiesKernel;
    private ComputeShader debugKernel;

    // SSBOs
    public Map<String, SSBO> ssbos;
    private SSBO FIXED_BODIES_IN_SSBO;
    private SSBO FIXED_BODIES_OUT_SSBO;
    private SSBO SWAPPING_BODIES_IN_SSBO;
    private SSBO SWAPPING_BODIES_OUT_SSBO;
    private SSBO FIXED_MORTON_IN_SSBO;
    private SSBO FIXED_INDEX_IN_SSBO;
    private SSBO SWAPPING_MORTON_IN_SSBO;
    private SSBO SWAPPING_MORTON_OUT_SSBO;
    private SSBO SWAPPING_INDEX_IN_SSBO;
    private SSBO SWAPPING_INDEX_OUT_SSBO;
    private SSBO NODES_SSBO;
    private SSBO VALUES_SSBO;
    private SSBO WG_HIST_SSBO;
    private SSBO WG_SCANNED_SSBO;
    private SSBO BUCKET_TOTALS_SSBO;
    private SSBO FIXED_MORTON_OUT_SSBO;
    private SSBO FIXED_INDEX_OUT_SSBO;
    private SSBO WORK_QUEUE_SSBO;
    private SSBO MERGE_QUEUE_SSBO;
    private SSBO DEBUG_SSBO;
    
    //Debug variables
    private List<Long> computeShaderDebugTimes;
    private long renderingTime;
    private long resetTime;
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

    private boolean firstPass = true;

    public BarnesHut(GPUSimulation gpuSimulation, boolean debug, float[][] bounds) {
        this.gpuSimulation = gpuSimulation;
        this.debug = debug;
        this.bounds = bounds;
    }
    
    public void step() {
        //System.out.println(FIXED_BODIES_IN_SSBO.getHeader());
        renderingCheck();

        resetQueues(true);


        partitionDeadBodies();


        swapMortonBuffers();
        resetQueues(false);
        generateMortonCodes();
        
        radixSort();
       
        buildBinaryRadixTree();

        computeCOMAndLocation();

        computeForce();
        mergeBodies();

        swapBodyBuffers();

            if (debug) {
            debugString = printProfiling();
        }

    }

    /* --------- Initialization --------- */

    public void init() {
        initComputeSSBOs();
        initComputeUniforms();
        initComputeShaders();
        initKernel.run();

        //System.out.println(FIXED_BODIES_IN_SSBO.getData(0, 10));
    }

    private void initComputeSSBOs() {
        
        //Create SSBOs

        ssbos = new HashMap<>();

        FIXED_BODIES_IN_SSBO = new SSBO(SSBO.BODIES_IN_SSBO_BINDING, () -> {
            return packPlanets(gpuSimulation.getPlanets());
        }, "FIXED_BODIES_IN_SSBO", Body.STRUCT_SIZE, Body.bodyTypes, Body.HEADER_SIZE, null);
        ssbos.put(FIXED_BODIES_IN_SSBO.getName(), FIXED_BODIES_IN_SSBO);

        FIXED_BODIES_OUT_SSBO = new SSBO(SSBO.BODIES_OUT_SSBO_BINDING, () -> {
            return packPlanets(gpuSimulation.getPlanets());
        }, "FIXED_BODIES_OUT_SSBO", Body.STRUCT_SIZE, Body.bodyTypes, Body.HEADER_SIZE, null);
        ssbos.put(FIXED_BODIES_OUT_SSBO.getName(), FIXED_BODIES_OUT_SSBO);

        FIXED_MORTON_IN_SSBO = new SSBO(SSBO.FIXED_MORTON_IN_SSBO_BINDING, () -> {
            return gpuSimulation.numBodies() * Long.BYTES;
        }, "FIXED_MORTON_IN_SSBO", 1, new VariableType[] { VariableType.UINT64 });
        ssbos.put(FIXED_MORTON_IN_SSBO.getName(), FIXED_MORTON_IN_SSBO);

        FIXED_INDEX_IN_SSBO = new SSBO(SSBO.FIXED_INDEX_IN_SSBO_BINDING, () -> {
            return gpuSimulation.numBodies() * Integer.BYTES;
        }, "FIXED_INDEX_IN_SSBO", 1, new VariableType[] { VariableType.UINT });
        ssbos.put(FIXED_INDEX_IN_SSBO.getName(), FIXED_INDEX_IN_SSBO);

        NODES_SSBO = new SSBO(SSBO.NODES_SSBO_BINDING, () -> {
            return (2 * gpuSimulation.numBodies() - 1) * Node.STRUCT_SIZE * Integer.BYTES;
        }, "NODES_SSBO", Node.STRUCT_SIZE, Node.nodeTypes);
        ssbos.put(NODES_SSBO.getName(), NODES_SSBO);

        VALUES_SSBO = new SSBO(SSBO.VALUES_SSBO_BINDING, () -> {
            return packValues();
        }, "VALUES_SSBO", 1, new VariableType[] { VariableType.UINT });
        ssbos.put(VALUES_SSBO.getName(), VALUES_SSBO);

        WG_HIST_SSBO = new SSBO(SSBO.WG_HIST_SSBO_BINDING, () -> {
            return numGroups() * (1+NUM_RADIX_BUCKETS) * Integer.BYTES;
        }, "WG_HIST_SSBO", 1, new VariableType[] { VariableType.UINT });
        ssbos.put(WG_HIST_SSBO.getName(), WG_HIST_SSBO);

        WG_SCANNED_SSBO = new SSBO(SSBO.WG_SCANNED_SSBO_BINDING, () -> {
            return Integer.BYTES + numGroups() * (1+NUM_RADIX_BUCKETS) * Integer.BYTES + Integer.BYTES;
        }, "WG_SCANNED_SSBO", 1, new VariableType[] { VariableType.UINT }, 4, new VariableType[] { VariableType.UINT });
        ssbos.put(WG_SCANNED_SSBO.getName(), WG_SCANNED_SSBO);

        BUCKET_TOTALS_SSBO = new SSBO(SSBO.BUCKET_TOTALS_SSBO_BINDING, () -> {
            return NUM_RADIX_BUCKETS * Integer.BYTES * 2;
        }, "BUCKET_TOTALS_SSBO", 1, new VariableType[] { VariableType.UINT });
        ssbos.put(BUCKET_TOTALS_SSBO.getName(), BUCKET_TOTALS_SSBO);

        FIXED_MORTON_OUT_SSBO = new SSBO(SSBO.FIXED_MORTON_OUT_SSBO_BINDING, () -> {
            return gpuSimulation.numBodies() * Long.BYTES;
        }, "FIXED_MORTON_OUT_SSBO", 1, new VariableType[] { VariableType.UINT64 });
        ssbos.put(FIXED_MORTON_OUT_SSBO.getName(), FIXED_MORTON_OUT_SSBO);

        FIXED_INDEX_OUT_SSBO = new SSBO(SSBO.FIXED_INDEX_OUT_SSBO_BINDING, () -> {
            return gpuSimulation.numBodies() * Integer.BYTES;
        }, "FIXED_INDEX_OUT_SSBO", 1, new VariableType[] { VariableType.UINT });
        ssbos.put(FIXED_INDEX_OUT_SSBO.getName(), FIXED_INDEX_OUT_SSBO);

        WORK_QUEUE_SSBO = new SSBO(SSBO.WORK_QUEUE_SSBO_BINDING, () -> {
            return (4 + gpuSimulation.numBodies()) * Integer.BYTES;
        }, "WORK_QUEUE_SSBO", 1, new VariableType[] { VariableType.UINT }, 16, new VariableType[] { VariableType.UINT, VariableType.UINT, VariableType.UINT, VariableType.UINT });
        ssbos.put(WORK_QUEUE_SSBO.getName(), WORK_QUEUE_SSBO);

        MERGE_QUEUE_SSBO = new SSBO(SSBO.MERGE_QUEUE_SSBO_BINDING, () -> {
            return Math.max(2*Integer.BYTES, 8+gpuSimulation.numBodies() * 2 * Integer.BYTES);
        }, "MERGE_QUEUE_SSBO", 2, new VariableType[] { VariableType.UINT, VariableType.UINT }, 8, new VariableType[] {VariableType.UINT});
        ssbos.put(MERGE_QUEUE_SSBO.getName(), MERGE_QUEUE_SSBO);

        DEBUG_SSBO = new SSBO(SSBO.DEBUG_SSBO_BINDING, () -> {
            return 100 * Integer.BYTES + 100 * Float.BYTES;
        }, "DEBUG_SSBO", 1, new VariableType[] { VariableType.FLOAT }, 100, new VariableType[] { VariableType.UINT });
        ssbos.put(DEBUG_SSBO.getName(), DEBUG_SSBO);

        for (SSBO ssbo : ssbos.values()) {
            System.out.println("Creating buffer for " + ssbo.getName());
            ssbo.createBuffer();
        }

        initializeSwappingBuffers();
    }

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
        numWorkGroupsUniform = new Uniform<Integer>("numWorkGroups", () -> {
            return numGroups();
        }, true);
        softeningUniform = new Uniform<Float>("softening", () -> {
            return Settings.getInstance().getSoftening();
        });
        thetaUniform = new Uniform<Float>("theta", () -> {
            return Settings.getInstance().getTheta();
        });
        dtUniform = new Uniform<Float>("dt", () -> {
            return Settings.getInstance().getDt();
        });
        elasticityUniform = new Uniform<Float>("elasticity", () -> {
            return (float)Settings.getInstance().getElasticity();
        });
        densityUniform = new Uniform<Float>("density", () -> {
            return (float)Settings.getInstance().getDensity();
        });
        restitutionUniform = new Uniform<Float>("restitution", () -> {
            return 0.2f;
        });
        passShiftUniform = new Uniform<Integer>("passShift", () -> {
            return passShift;
        }, true);
        collisionUniform = new Uniform<Boolean>("collision", () -> {
            return false;
        });
        firstPassUniform = new Uniform<Boolean>("firstPass", () -> {
            return firstPass;
        });
    }

    private void initComputeShaders() {

        computeShaders = new ArrayList<>();
        initKernel = new ComputeShader("KERNEL_INIT", this);
        initKernel.setUniforms(new Uniform[] {
        });
        initKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "SWAPPING_INDEX_IN_SSBO"
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
        radixSortDeadExclusiveScanKernel = new ComputeShader("KERNEL_RADIX_DEAD_EXCLUSIVE_SCAN", this);
        radixSortDeadExclusiveScanKernel.setUniforms(new Uniform[] {
            numWorkGroupsUniform
        });
        radixSortDeadExclusiveScanKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "WG_HIST_SSBO",
            "WG_SCANNED_SSBO"
        });
        radixSortDeadExclusiveScanKernel.setXWorkGroupsFunction(() -> {
            return 1;
        });
        computeShaders.add(radixSortDeadExclusiveScanKernel);
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

        radixSortDeadScatterKernel = new ComputeShader("KERNEL_RADIX_DEAD_SCATTER", this);
        radixSortDeadScatterKernel.setUniforms(new Uniform[] {
            numWorkGroupsUniform
        });
        radixSortDeadScatterKernel.setSSBOs(new String[] {
            "VALUES_SSBO",
            "WG_SCANNED_SSBO",
            "SWAPPING_INDEX_IN_SSBO",
            "SWAPPING_MORTON_IN_SSBO",
            "SWAPPING_BODIES_IN_SSBO",
            "SWAPPING_MORTON_OUT_SSBO",
            "SWAPPING_INDEX_OUT_SSBO"
        });
        radixSortDeadScatterKernel.setXWorkGroupsFunction(() -> {
            return numGroups();
        });
        computeShaders.add(radixSortDeadScatterKernel);

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
            int numInternalNodes = gpuSimulation.numBodies() - 1;
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
            firstPassUniform
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
            "SWAPPING_BODIES_IN_SSBO",
            "DEBUG_SSBO"
        });

        propagateNodesKernel.setXWorkGroupsFunction(() -> {
            int maxPossibleNodes = gpuSimulation.numBodies() - 1; // Internal nodes
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
        });
        mergeBodiesKernel.setXWorkGroupsFunction(() -> {
            return 1;
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

    private void renderingCheck() {
        if (debug) {
            renderingTime = System.nanoTime();
            checkGLError("rendering");
            glFinish();
            renderingTime = System.nanoTime() - renderingTime;
        }
    }

    private void resetQueues(boolean first) {
        long resetStartTime = 0;
        if (debug) {
            resetStartTime = System.nanoTime();
        }
        firstPass = first;
        resetKernel.run();
        if (debug) {
            checkGLError("resetQueues");
            glFinish();
            resetTime = System.nanoTime() - resetStartTime;
        }
    }

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

    private void radixSort() {
        int numPasses = (int)Math.ceil(63.0 / 4.0); // 16 passes for 63-bit Morton codes
        
        radixSortHistogramTime = 0;
        radixSortScanParallelTime = 0;
        radixSortScanExclusiveTime = 0;
        radixSortScatterTime = 0;

        long radixSortHistogramStartTime = 0;
        long radixSortScanParallelStartTime = 0;
        long radixSortScanExclusiveStartTime = 0;
        long radixSortScatterStartTime = 0;

        passShift = 0;
        
        for (int pass = 0; pass < numPasses; pass++) {
            
            passShift = pass * 4; // 4 bits per pass

            if (debug) {
                radixSortHistogramStartTime = System.nanoTime();
            }

            // Phase 1: Histogram
            radixSortHistogramKernel.run();

            if (debug) {
                checkGLError("radixSortHistogram");
                glFinish();
                radixSortHistogramTime += System.nanoTime() - radixSortHistogramStartTime;
                radixSortScanParallelStartTime = System.nanoTime();
            }

            // Phase 2: Scan
            radixSortParallelScanKernel.run();
            if (debug) {
                checkGLError("radixSortParallelScan");
                glFinish();
                radixSortScanParallelTime += System.nanoTime() - radixSortScanParallelStartTime;
                radixSortScanExclusiveStartTime = System.nanoTime();
            }

            radixSortExclusiveScanKernel.run();

            if (debug) {
                checkGLError("radixSortExclusiveScan");
                glFinish();
                radixSortScanExclusiveTime += System.nanoTime() - radixSortScanExclusiveStartTime;
                radixSortScatterStartTime = System.nanoTime();
            }

            // Phase 3: Scatter
            radixSortScatterKernel.run();
            if (debug) {
                checkGLError("radixSortScatter");
                glFinish();
                radixSortScatterTime += System.nanoTime() - radixSortScatterStartTime;
            }
            

            swapMortonBuffers();
        }

        if (debug) {
            radixSortTime = radixSortHistogramTime + radixSortScanParallelTime + radixSortScanExclusiveTime + radixSortScatterTime;
        }
    }

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

        for (int i = 0; i < PROPAGATE_NODES_ITERATIONS; i++) {
            propagateNodesKernel.run();
            System.out.println(Arrays.toString(DEBUG_SSBO.getHeaderAsInts()));

        }
        
        if (debug) {
            checkGLError("propagateNodes");
            glFinish();
            propagateNodesTime = System.nanoTime() - propagateNodesTime;
            computeCOMAndLocationTime = initLeavesTime + propagateNodesTime;
        }
    }

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

    private void initializeSwappingBuffers() {
        // Create Swapping SSBOs
        SWAPPING_BODIES_IN_SSBO = FIXED_BODIES_IN_SSBO;
        SWAPPING_BODIES_IN_SSBO.setName("SWAPPING_BODIES_IN_SSBO");
        ssbos.put(SWAPPING_BODIES_IN_SSBO.getName(), SWAPPING_BODIES_IN_SSBO);
        SWAPPING_BODIES_OUT_SSBO = FIXED_BODIES_OUT_SSBO;
        SWAPPING_BODIES_OUT_SSBO.setName("SWAPPING_BODIES_OUT_SSBO");
        ssbos.put(SWAPPING_BODIES_OUT_SSBO.getName(), SWAPPING_BODIES_OUT_SSBO);
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
    }

    private void swapBodyBuffers() {
        // Swap source and destination buffers for next iteration
        int tmpIn = SWAPPING_BODIES_IN_SSBO.getBufferLocation();
        SWAPPING_BODIES_IN_SSBO.setBufferLocation(SWAPPING_BODIES_OUT_SSBO.getBufferLocation());
        SWAPPING_BODIES_OUT_SSBO.setBufferLocation(tmpIn);
    }

    private void swapMortonBuffers() {
        // Swap input/output buffers for next pass
        int tempMortonIn = SWAPPING_MORTON_IN_SSBO.getBufferLocation();
        int tempIndexIn = SWAPPING_INDEX_IN_SSBO.getBufferLocation();
        SWAPPING_MORTON_IN_SSBO.setBufferLocation(SWAPPING_MORTON_OUT_SSBO.getBufferLocation());
        SWAPPING_INDEX_IN_SSBO.setBufferLocation(SWAPPING_INDEX_OUT_SSBO.getBufferLocation());
        SWAPPING_MORTON_OUT_SSBO.setBufferLocation(tempMortonIn);
        SWAPPING_INDEX_OUT_SSBO.setBufferLocation(tempIndexIn);
    }

    public SSBO getOutputSSBO() {
        return SWAPPING_BODIES_IN_SSBO;
    }

    public SSBO getNodesSSBO() {
        return NODES_SSBO;
    }

    public SSBO getValuesSSBO() {
        return VALUES_SSBO;
    }
    private int numGroups() {
        return (gpuSimulation.numBodies() + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
    }

    public ByteBuffer packPlanets(List<Planet> planets) {
        // Packs planet data to float buffer: pos(x,y,z), mass, vel(x,y,z), pad, color(r,g,b,a)
        int numBodies = planets.size();
        
        ByteBuffer buf = BufferUtils.createByteBuffer((numBodies * Body.STRUCT_SIZE)*4+Body.HEADER_SIZE);
        for (int i = 0; i < numBodies; i++) {
            Planet p = planets.get(i);
            buf.putFloat(p.position.x).putFloat(p.position.y).putFloat(p.position.z).putFloat(p.mass);
            buf.putFloat(p.velocity.x).putFloat(p.velocity.y).putFloat(p.velocity.z).putFloat(p.density);
            java.awt.Color c = p.getColor();
            float cr = c != null ? (c.getRed() / 255f) : 1.0f;
            float cg = c != null ? (c.getGreen() / 255f) : 1.0f;
            float cb = c != null ? (c.getBlue() / 255f) : 1.0f;
            buf.putFloat(cr).putFloat(cg).putFloat(cb).putFloat(1.0f);
        }
        buf.flip();
        return buf;
    }
    public ByteBuffer packValues() {

        //layout(std430, binding = 5) buffer Values { uint numBodies; uint initialNumBodies; uint pad0; uint pad1; AABB bounds; } sim;
        ByteBuffer buf = BufferUtils.createByteBuffer(12*4);
        buf.putInt(gpuSimulation.numBodies());
        buf.putInt(gpuSimulation.numBodies());
        buf.putInt(0);
        buf.putInt(0);
        buf.putFloat(bounds[0][0]).putFloat(bounds[0][1]).putFloat(bounds[0][2]).putInt(0);
        buf.putFloat(bounds[1][0]).putFloat(bounds[1][1]).putFloat(bounds[1][2]).putInt(0);
        buf.flip();
        return buf;
    }

    public void uploadPlanetsData(List<Planet> newPlanets) {
        // Assumes buffers are already correctly sized
        ByteBuffer data = packPlanets(newPlanets);
        glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_WRITE_ONLY);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, data);
        SSBO.unBind();
    }

    public void resizeBuffersAndUpload(List<Planet> newPlanets) {
        // Resizes SSBOs to fit new count, then uploads


        
        // Resize Barnes-Hut auxiliary buffers
        for (SSBO ssbo : ssbos.values()) {
            ssbo.createBuffer();
        }

        SWAPPING_BODIES_IN_SSBO = FIXED_BODIES_IN_SSBO;
        SWAPPING_BODIES_OUT_SSBO = FIXED_BODIES_OUT_SSBO;
        SWAPPING_MORTON_IN_SSBO = FIXED_MORTON_IN_SSBO;
        SWAPPING_INDEX_IN_SSBO = FIXED_INDEX_IN_SSBO;
        SWAPPING_MORTON_OUT_SSBO = FIXED_MORTON_OUT_SSBO;
        SWAPPING_INDEX_OUT_SSBO = FIXED_INDEX_OUT_SSBO;
    }

    /* --------- Cleanup --------- */

    public void cleanup() {
        for (ComputeShader shader : computeShaders) {
            shader.delete();
        }
        for (SSBO ssbo : ssbos.values()) {
            ssbo.delete();
        }
    }

    /* --------- Debugging --------- */

    private void checkGLError(String operation) {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            System.err.println("OpenGL Error after " + operation + ": " + error);
        }
    }
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


    private void checkMortonCodes(SSBO mortonBuffer, boolean print) {
        boolean correctPartitioning = true;
        boolean correctSorting = true;
        long[] mortonCodes = new long[gpuSimulation.numBodies()];
        LongBuffer mortonBufferLong = mortonBuffer.getBuffer().asLongBuffer();
        long[] sortedMortonCodes = new long[gpuSimulation.numBodies()];
        for (int i = 0; i < gpuSimulation.numBodies(); i++) {
            mortonCodes[i] = mortonBufferLong.get(i);
            sortedMortonCodes[i] = mortonCodes[i];
            if (print) {
                System.out.println("Morton Code: " + i + " " + mortonCodes[i]);
            }
        }
        Arrays.sort(sortedMortonCodes);
        int deadIndex = 0;
        while (deadIndex < gpuSimulation.numBodies() && sortedMortonCodes[deadIndex] == -1) {
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
    
    private float[][] oldComputeAABB() {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        
        SWAPPING_BODIES_IN_SSBO.bind();
        ByteBuffer buffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        FloatBuffer bodyData = buffer.asFloatBuffer();
        
        for (int i = 0; i < gpuSimulation.numBodies(); i++) {
            int offset = i * 12; // 12 floats per body (pos+mass, vel+pad, color)
            float x = bodyData.get(offset + 0);
            float y = bodyData.get(offset + 1);
            float z = bodyData.get(offset + 2);
            
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        SSBO.unBind();
        
        float[][] raabb = new float[][] {
            {minX, minY, minZ},
            {maxX, maxY, maxZ}
        };
        return raabb;
    }

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
    
    private void debugRadixSort() {
    FIXED_MORTON_IN_SSBO.bind();
    ByteBuffer mortonBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
    IntBuffer mortonData = mortonBuffer.asIntBuffer();
    glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
    SWAPPING_INDEX_IN_SSBO.bind();
    ByteBuffer indexBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
    IntBuffer indexData = indexBuffer.asIntBuffer();
    glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
    WG_HIST_SSBO.bind();
    ByteBuffer wgHistBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
    IntBuffer wgHistData = wgHistBuffer.asIntBuffer();
    glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
    WG_SCANNED_SSBO.bind();
    ByteBuffer wgScannedBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
    IntBuffer wgScannedData = wgScannedBuffer.asIntBuffer();
    glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
    BUCKET_TOTALS_SSBO.bind();
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
    for (int i = 0; i < Math.min(10, gpuSimulation.numBodies()); i++) {
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

    private void debugSorting() {
        System.out.println("=== RADIX SORT RESULTS ===");
            // Read sorted Morton codes
            FIXED_MORTON_IN_SSBO.bind();
            ByteBuffer mortonBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            IntBuffer mortonData = mortonBuffer.asIntBuffer();
            System.out.println("Sorted Morton codes:");
            for (int i = 0; i < Math.min(100, gpuSimulation.numBodies()); i++) {
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
            for (int i = 0; i < Math.min(100, gpuSimulation.numBodies()); i++) {
                int bodyIndex = indexData.get(i);
                System.out.printf("  [%d]: %d\n", i, bodyIndex);
            }
            glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            
            SSBO.unBind();

    }

    private void debugTree() {
        
        // Debug: Output buffer data
            System.out.println("=== FINAL TREE WITH COM DATA ===");
            
            // First check for stuck nodes with readyChildren < 2
            NODES_SSBO.bind();
            ByteBuffer nodeBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            IntBuffer nodeData = nodeBuffer.asIntBuffer();
            glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            
            int numLeaves = gpuSimulation.numBodies();
            int totalNodes = 2 * gpuSimulation.numBodies() - 1;

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
