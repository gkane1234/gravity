package com.grumbo.simulation;

import org.lwjgl.*;

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
    // Simulation params
    //To change these, you need to also change their definitions in the compute shader
    private static final int WORK_GROUP_SIZE = 128;
    private static final int NUM_RADIX_BUCKETS = 16; // 2^RADIX_BITS where RADIX_BITS=4

    // SSBO Bindings
    private static final int BODIES_IN_SSBO_BINDING = 0;
    private static final int BODIES_OUT_SSBO_BINDING = 1;
    private static final int MORTON_IN_SSBO_BINDING = 2;
    private static final int INDEX_IN_SSBO_BINDING = 3;
    private static final int NODES_SSBO_BINDING = 4;
    private static final int AABB_SSBO_BINDING = 5;
    private static final int WG_HIST_SSBO_BINDING = 6;
    private static final int WG_SCANNED_SSBO_BINDING = 7;
    private static final int GLOBAL_BASE_SSBO_BINDING = 8;
    private static final int BUCKET_TOTALS_SSBO_BINDING = 9;
    private static final int MORTON_OUT_SSBO_BINDING = 10;
    private static final int INDEX_OUT_SSBO_BINDING = 11;
    private static final int WORK_QUEUE_SSBO_BINDING = 12;
    private static final int MERGE_QUEUE_SSBO_BINDING = 13;

    // These can be freely changed here
    private static final int PROPAGATE_NODES_ITERATIONS = 30;
    private static final boolean DEBUG_BARNES_HUT = true; // Note debugging will slow down the simulation, inconsistently


    public enum RenderMode {
        OFF,
        POINTS,
        IMPOSTOR_SPHERES,
        MESH_SPHERES
    }

    // Uniforms
    public Map<String, Uniform<?>> uniforms;
    private Uniform<Integer> numWorkGroupsUniform;
    private Uniform<Float> softeningUniform;
    private Uniform<Float> thetaUniform;
    private Uniform<Float> dtUniform;
    private Uniform<Integer> numBodiesUniform;
    private Uniform<Float> elasticityUniform;
    private Uniform<Float> densityUniform;
    private Uniform<Float> restitutionUniform;
    private Uniform<Boolean> collisionUniform;
    private Uniform<Integer> mergeQueueTailUniform;
    private Uniform<Integer> passShiftUniform;

    // Uniform local variables
    private int passShift;

    // Compute Shaders
    private ComputeShader computeAABBKernel;
    private ComputeShader collapseAABBKernel;
    private ComputeShader mortonKernel;
    private ComputeShader radixSortHistogramKernel;
    private ComputeShader radixSortParallelScanKernel;
    private ComputeShader radixSortExclusiveScanKernel;
    private ComputeShader radixSortScatterKernel;
    private ComputeShader buildBinaryRadixTreeKernel;
    private ComputeShader initLeavesKernel;
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
    private SSBO MORTON_IN_SSBO;
    private SSBO INDEX_IN_SSBO;
    private SSBO CURRENT_MORTON_IN_SSBO;
    private SSBO CURRENT_MORTON_OUT_SSBO;
    private SSBO CURRENT_INDEX_IN_SSBO;
    private SSBO CURRENT_INDEX_OUT_SSBO;
    private SSBO NODES_SSBO;
    private SSBO AABB_SSBO;
    private SSBO WG_HIST_SSBO;
    private SSBO WG_SCANNED_SSBO;
    private SSBO GLOBAL_BASE_SSBO;
    private SSBO BUCKET_TOTALS_SSBO;
    private SSBO MORTON_OUT_SSBO;
    private SSBO INDEX_OUT_SSBO;
    private SSBO WORK_QUEUE_SSBO;
    private SSBO MERGE_QUEUE_SSBO;

    // Render Programs
    private int renderProgram; // points program
    private int impostorProgram; // point-sprite impostor spheres
    private int sphereProgram;   // instanced mesh spheres

    //Render Uniforms
    private int vao; // for points and impostors
    private int uMvpLocation;           // for points
    private int uMvpLocationImpostor;   // for impostors
    private int uMvpLocationSphere;     // for mesh spheres
    private int uSphereRadiusScaleLoc;  // mass->radius scale
    private int uCameraPosLocSphere;
    private int uNearDistLocSphere;
    private int uFarDistLocSphere;
    private int uImpostorPointScaleLoc; // impostor scale

    // Mesh sphere resources
    private int sphereVao = 0;
    private int sphereVbo = 0; // positions (and normals interleaved optional later)
    private int sphereIbo = 0;
    private int sphereIndexCount = 0;
    private int sphereStacks = 8;
    private int sphereSlices = 16;

    // Impostor config
    private float impostorPointScale = 2.0f; // mass to pixel size scale
    private int maxMeshInstances = 500000;
    private float sphereRadiusScale = Settings.getInstance().getDensity(); // radius = sqrt(mass) * scale

    // Distance-based color/brightness params (defaults)
    private float cameraX = 0f, cameraY = 0f, cameraZ = 0f;
    private float nearDist = 0f;
    private float farDist = 10000f;

    //Debug variables
    private long aabbTime;
    private long computeAABBTime;
    private long collapseAABBTime;
    private long mortonTime;
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
    public String debugString;


    private RenderMode renderMode;
    private final ConcurrentLinkedQueue<GPUCommands.GPUCommand> commandQueue;

    private ArrayList<Planet> planets;

    public GPUSimulation(ArrayList<Planet> planets) {
        this.planets = planets;
        this.renderMode = RenderMode.MESH_SPHERES;
        this.commandQueue = new ConcurrentLinkedQueue<>();
    }
    
    public void init() {
        initCompute();
        initRender();
    }

    public void step() {
        processCommands();
        stepBarnesHut();
    }
    
    public void processCommands() {
        GPUCommands.GPUCommand cmd;
        while ((cmd = commandQueue.poll()) != null) {
            cmd.run(this);
        }
    }
 
    private void stepBarnesHut() {

        computeAABB();

        generateMortonCodes();

        radixSort();

        buildBinaryRadixTree();

        computeCOMAndLocation();

        computeForce();

        if (DEBUG_BARNES_HUT) {
            debugString = printProfiling();
        }
    }

    /* --------- Initialization --------- */

    public void initCompute() {
        initComputeSSBOs();
        initComputeUniforms();
        initComputeShaders();
    }

    public void initRender() {
        
        // Create Render Shaders

        // Create render shader (points)
        renderProgram = glCreateProgram();
        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, getVertexShaderSource());
        glCompileShader(vs);
        checkShader(vs);
        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, getFragmentShaderSource());
        glCompileShader(fs);
        checkShader(fs);
        glAttachShader(renderProgram, vs);
        glAttachShader(renderProgram, fs);
        glLinkProgram(renderProgram);
        checkProgram(renderProgram);

        // Cache uniform locations
        uMvpLocation = glGetUniformLocation(renderProgram, "uMVP");

        // Create impostor program
        impostorProgram = glCreateProgram();
        int ivs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(ivs, getImpostorVertexShaderSource());
        glCompileShader(ivs);
        checkShader(ivs);
        int ifs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(ifs, getImpostorFragmentShaderSource());
        glCompileShader(ifs);
        checkShader(ifs);
        glAttachShader(impostorProgram, ivs);
        glAttachShader(impostorProgram, ifs);
        glLinkProgram(impostorProgram);
        checkProgram(impostorProgram);
        uMvpLocationImpostor = glGetUniformLocation(impostorProgram, "uMVP");
        uImpostorPointScaleLoc = glGetUniformLocation(impostorProgram, "uPointScale");

        // Create mesh sphere program
        sphereProgram = glCreateProgram();
        int svs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(svs, getSphereVertexShaderSource());
        glCompileShader(svs);
        checkShader(svs);
        int sfs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(sfs, getSphereFragmentShaderSource());
        glCompileShader(sfs);
        checkShader(sfs);
        glAttachShader(sphereProgram, svs);
        glAttachShader(sphereProgram, sfs);
        glLinkProgram(sphereProgram);
        checkProgram(sphereProgram);
        uMvpLocationSphere = glGetUniformLocation(sphereProgram, "uMVP");
        uSphereRadiusScaleLoc = glGetUniformLocation(sphereProgram, "uRadiusScale");
        uCameraPosLocSphere = glGetUniformLocation(sphereProgram, "uCameraPos");
        uNearDistLocSphere = glGetUniformLocation(sphereProgram, "uNearDist");
        uFarDistLocSphere = glGetUniformLocation(sphereProgram, "uFarDist");

        glEnable(GL_PROGRAM_POINT_SIZE);
        // Initialize sphere mesh VAO/VBO/IBO
        rebuildSphereMesh();
    }

    private void initComputeSSBOs() {
        
        //Create SSBOs

        ssbos = new HashMap<>();

        

        FIXED_BODIES_IN_SSBO = new SSBO(BODIES_IN_SSBO_BINDING, () -> {
            return packPlanets(planets);
        }, "FIXED_BODIES_IN_SSBO", Body.STRUCT_SIZE, Body.bodyTypes);
        ssbos.put(FIXED_BODIES_IN_SSBO.getName(), FIXED_BODIES_IN_SSBO);

        FIXED_BODIES_OUT_SSBO = new SSBO(BODIES_OUT_SSBO_BINDING, () -> {
            return planets.size() * Body.STRUCT_SIZE * Float.BYTES;
        }, "FIXED_BODIES_OUT_SSBO", Body.STRUCT_SIZE, Body.bodyTypes);
        ssbos.put(FIXED_BODIES_OUT_SSBO.getName(), FIXED_BODIES_OUT_SSBO);

        MORTON_IN_SSBO = new SSBO(MORTON_IN_SSBO_BINDING, () -> {
            return planets.size() * Long.BYTES;
        }, "MORTON_IN_SSBO", 1, new Type[] { Type.UINT64 });
        ssbos.put(MORTON_IN_SSBO.getName(), MORTON_IN_SSBO);

        INDEX_IN_SSBO = new SSBO(INDEX_IN_SSBO_BINDING, () -> {
            return planets.size() * Integer.BYTES;
        }, "INDEX_IN_SSBO", 1, new Type[] { Type.UINT });
        ssbos.put(INDEX_IN_SSBO.getName(), INDEX_IN_SSBO);

        NODES_SSBO = new SSBO(NODES_SSBO_BINDING, () -> {
            return (2 * planets.size() - 1) * Node.STRUCT_SIZE * Integer.BYTES;
        }, "NODES_SSBO", Node.STRUCT_SIZE, Node.nodeTypes);
        ssbos.put(NODES_SSBO.getName(), NODES_SSBO);

        AABB_SSBO = new SSBO(AABB_SSBO_BINDING, () -> {
            return numGroups() * 2 * 4 * Float.BYTES;
        }, "AABB_SSBO", 8, new Type[] { Type.FLOAT, Type.FLOAT, Type.FLOAT, Type.FLOAT, Type.FLOAT, Type.FLOAT, Type.FLOAT, Type.FLOAT });
        ssbos.put(AABB_SSBO.getName(), AABB_SSBO);

        WG_HIST_SSBO = new SSBO(WG_HIST_SSBO_BINDING, () -> {
            return numGroups() * NUM_RADIX_BUCKETS * Integer.BYTES;
        }, "WG_HIST_SSBO", 1, new Type[] { Type.UINT });
        ssbos.put(WG_HIST_SSBO.getName(), WG_HIST_SSBO);

        WG_SCANNED_SSBO = new SSBO(WG_SCANNED_SSBO_BINDING, () -> {
            return numGroups() * NUM_RADIX_BUCKETS * Integer.BYTES;
        }, "WG_SCANNED_SSBO", 1, new Type[] { Type.UINT });
        ssbos.put(WG_SCANNED_SSBO.getName(), WG_SCANNED_SSBO);

        GLOBAL_BASE_SSBO = new SSBO(GLOBAL_BASE_SSBO_BINDING, () -> {
            return NUM_RADIX_BUCKETS * Integer.BYTES;
        }, "GLOBAL_BASE_SSBO", 1, new Type[] { Type.UINT });
        ssbos.put(GLOBAL_BASE_SSBO.getName(), GLOBAL_BASE_SSBO);

        BUCKET_TOTALS_SSBO = new SSBO(BUCKET_TOTALS_SSBO_BINDING, () -> {
            return NUM_RADIX_BUCKETS * Integer.BYTES;
        }, "BUCKET_TOTALS_SSBO", 1, new Type[] { Type.UINT });
        ssbos.put(BUCKET_TOTALS_SSBO.getName(), BUCKET_TOTALS_SSBO);

        MORTON_OUT_SSBO = new SSBO(MORTON_OUT_SSBO_BINDING, () -> {
            return planets.size() * Long.BYTES;
        }, "MORTON_OUT_SSBO", 1, new Type[] { Type.UINT64 });
        ssbos.put(MORTON_OUT_SSBO.getName(), MORTON_OUT_SSBO);

        INDEX_OUT_SSBO = new SSBO(INDEX_OUT_SSBO_BINDING, () -> {
            return planets.size() * Integer.BYTES;
        }, "INDEX_OUT_SSBO", 1, new Type[] { Type.UINT });
        ssbos.put(INDEX_OUT_SSBO.getName(), INDEX_OUT_SSBO);

        WORK_QUEUE_SSBO = new SSBO(WORK_QUEUE_SSBO_BINDING, () -> {
            return (2 + planets.size()) * Integer.BYTES;
        }, "WORK_QUEUE_SSBO", 1, new Type[] { Type.UINT });
        ssbos.put(WORK_QUEUE_SSBO.getName(), WORK_QUEUE_SSBO);

        MERGE_QUEUE_SSBO = new SSBO(MERGE_QUEUE_SSBO_BINDING, () -> {
            return planets.size() * Integer.BYTES;
        }, "MERGE_QUEUE_SSBO", 2, new Type[] { Type.UINT, Type.UINT });
        ssbos.put(MERGE_QUEUE_SSBO.getName(), MERGE_QUEUE_SSBO);


        FIXED_BODIES_IN_SSBO.createBuffer();

        FIXED_BODIES_OUT_SSBO.createBuffer();
        MORTON_IN_SSBO.createBuffer();
        INDEX_IN_SSBO.createBuffer();
        NODES_SSBO.createBuffer();
        AABB_SSBO.createBuffer();
        WG_HIST_SSBO.createBuffer();
        WG_SCANNED_SSBO.createBuffer();
        GLOBAL_BASE_SSBO.createBuffer();
        BUCKET_TOTALS_SSBO.createBuffer();
        MORTON_OUT_SSBO.createBuffer();
        INDEX_OUT_SSBO.createBuffer();
        WORK_QUEUE_SSBO.createBuffer();
        MERGE_QUEUE_SSBO.createBuffer();

        // Create Swapping SSBOs
        SWAPPING_BODIES_IN_SSBO = FIXED_BODIES_IN_SSBO;
        ssbos.put(SWAPPING_BODIES_IN_SSBO.getName(), SWAPPING_BODIES_IN_SSBO);
        SWAPPING_BODIES_OUT_SSBO = FIXED_BODIES_OUT_SSBO;
        ssbos.put(SWAPPING_BODIES_OUT_SSBO.getName(), SWAPPING_BODIES_OUT_SSBO);
        CURRENT_MORTON_IN_SSBO = MORTON_IN_SSBO;
        ssbos.put(CURRENT_MORTON_IN_SSBO.getName(), CURRENT_MORTON_IN_SSBO);
        CURRENT_MORTON_OUT_SSBO = MORTON_OUT_SSBO;
        ssbos.put(CURRENT_MORTON_OUT_SSBO.getName(), CURRENT_MORTON_OUT_SSBO);
        CURRENT_INDEX_IN_SSBO = INDEX_IN_SSBO;
        ssbos.put(CURRENT_INDEX_IN_SSBO.getName(), CURRENT_INDEX_IN_SSBO);
        CURRENT_INDEX_OUT_SSBO = INDEX_OUT_SSBO;
        ssbos.put(CURRENT_INDEX_OUT_SSBO.getName(), CURRENT_INDEX_OUT_SSBO);
    }

    private void initComputeUniforms() {
         //Create uniforms
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
        numBodiesUniform = new Uniform<Integer>("numBodies", () -> {
            return planets.size();
        }, true);
        elasticityUniform = new Uniform<Float>("elasticity", () -> {
            return (float)Settings.getInstance().getElasticity();
        });
        densityUniform = new Uniform<Float>("density", () -> {
            return (float)Settings.getInstance().getDensity();
        });
        restitutionUniform = new Uniform<Float>("restitution", () -> {
            return (float)Settings.getInstance().getRestitution();
        });
        collisionUniform = new Uniform<Boolean>("collision", () -> {
            return Settings.getInstance().isCollision();
        });
        passShiftUniform = new Uniform<Integer>("passShift", () -> {
            return passShift;
        });
        //TODO
        mergeQueueTailUniform = new Uniform<Integer>("mergeQueueTail", () -> {
            return 0;
        }, true);
        
    }

    private void initComputeShaders() {
        //Compute AABB Kernel
        computeAABBKernel = new ComputeShader("KERNEL_COMPUTE_AABB", this);
        computeAABBKernel.setUniforms(new Uniform[] { numBodiesUniform });
        computeAABBKernel.setSSBOs(new String[] { SWAPPING_BODIES_IN_SSBO.getName(), AABB_SSBO.getName() });
        computeAABBKernel.setXWorkGroupsFunction(() -> { return numGroups(); });

        //Collapse AABB Kernel
        collapseAABBKernel = new ComputeShader("KERNEL_COLLAPSE_AABB", this);
        collapseAABBKernel.setUniforms(new Uniform[] { numWorkGroupsUniform, numBodiesUniform });
        collapseAABBKernel.setSSBOs(new String[] { AABB_SSBO.getName() });
        collapseAABBKernel.setXWorkGroupsFunction(() -> { return 1; });

        //Morton Kernel
        mortonKernel = new ComputeShader("KERNEL_MORTON", this);
        mortonKernel.setUniforms(new Uniform[] { numBodiesUniform });
        mortonKernel.setSSBOs(new String[] { SWAPPING_BODIES_IN_SSBO.getName(), MORTON_IN_SSBO.getName(), INDEX_IN_SSBO.getName(), AABB_SSBO.getName() });
        mortonKernel.setXWorkGroupsFunction(() -> { return numGroups(); });

        //Radix Sort Histogram Kernel
        radixSortHistogramKernel = new ComputeShader("KERNEL_RADIX_HIST", this);
        radixSortHistogramKernel.setUniforms(new Uniform[] { numBodiesUniform, passShiftUniform, numWorkGroupsUniform });
        radixSortHistogramKernel.setSSBOs(new String[] { CURRENT_MORTON_IN_SSBO.getName(), CURRENT_INDEX_IN_SSBO.getName(), WG_HIST_SSBO.getName() });
        radixSortHistogramKernel.setXWorkGroupsFunction(() -> { return numGroups(); });

        //Radix Sort Parallel Scan Kernel
        radixSortParallelScanKernel = new ComputeShader("KERNEL_RADIX_PARALLEL_SCAN", this);
        radixSortParallelScanKernel.setUniforms(new Uniform[] { numBodiesUniform, numWorkGroupsUniform });
        radixSortParallelScanKernel.setSSBOs(new String[] { WG_HIST_SSBO.getName(), WG_SCANNED_SSBO.getName(), GLOBAL_BASE_SSBO.getName() });
        radixSortParallelScanKernel.setXWorkGroupsFunction(() -> { return NUM_RADIX_BUCKETS; });

        //Radix Sort Exclusive Scan Kernel
        radixSortExclusiveScanKernel = new ComputeShader("KERNEL_RADIX_EXCLUSIVE_SCAN", this);
        radixSortExclusiveScanKernel.setUniforms(new Uniform[] { numBodiesUniform, numWorkGroupsUniform });
        radixSortExclusiveScanKernel.setSSBOs(new String[] { BUCKET_TOTALS_SSBO.getName(), GLOBAL_BASE_SSBO.getName() });
        radixSortExclusiveScanKernel.setXWorkGroupsFunction(() -> { return 1; });

        //Radix Sort Scatter Kernel
        radixSortScatterKernel = new ComputeShader("KERNEL_RADIX_SCATTER", this);
        radixSortScatterKernel.setUniforms(new Uniform[] { numBodiesUniform, passShiftUniform,numWorkGroupsUniform });
        radixSortScatterKernel.setSSBOs(new String[] { CURRENT_MORTON_IN_SSBO.getName(), CURRENT_INDEX_IN_SSBO.getName(), CURRENT_MORTON_OUT_SSBO.getName(), CURRENT_INDEX_OUT_SSBO.getName(), BUCKET_TOTALS_SSBO.getName(), GLOBAL_BASE_SSBO.getName() });
        radixSortScatterKernel.setXWorkGroupsFunction(() -> { return numGroups(); });

        //Build Binary Radix Tree Kernel
        buildBinaryRadixTreeKernel = new ComputeShader("KERNEL_BUILD_BINARY_RADIX_TREE", this);
        buildBinaryRadixTreeKernel.setUniforms(new Uniform[] { numBodiesUniform });
        buildBinaryRadixTreeKernel.setSSBOs(new String[] { MORTON_IN_SSBO.getName(), INDEX_IN_SSBO.getName(), NODES_SSBO.getName() });
        buildBinaryRadixTreeKernel.setXWorkGroupsFunction(() -> {
            int numInternalNodes = planets.size() - 1;
            int internalNodeGroups = (numInternalNodes + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
            return internalNodeGroups;
        });

        //Init Leaves Kernel
        initLeavesKernel = new ComputeShader("KERNEL_INIT_LEAVES", this);
        initLeavesKernel.setUniforms(new Uniform[] { numBodiesUniform });
        initLeavesKernel.setSSBOs(new String[] { SWAPPING_BODIES_IN_SSBO.getName(), MORTON_IN_SSBO.getName(), INDEX_IN_SSBO.getName(), NODES_SSBO.getName(), WORK_QUEUE_SSBO.getName() });
        initLeavesKernel.setXWorkGroupsFunction(() -> {
            return numGroups(); });

        //Propagate Nodes Kernel
        propagateNodesKernel = new ComputeShader("KERNEL_PROPAGATE_NODES", this);
        propagateNodesKernel.setSSBOs(new String[] { NODES_SSBO.getName(), WORK_QUEUE_SSBO.getName() });
        propagateNodesKernel.setXWorkGroupsFunction(() -> {
            int maxPossibleNodes = planets.size() - 1; // Internal nodes
            int workGroups = (maxPossibleNodes + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
            return workGroups;
        });

        //Compute Force Kernel
        computeForceKernel = new ComputeShader("KERNEL_COMPUTE_FORCE", this);
        computeForceKernel.setUniforms(
            new Uniform[] { numBodiesUniform,
                            softeningUniform,
                            thetaUniform,
                            dtUniform,
                            elasticityUniform,
                            densityUniform,
                            restitutionUniform,
                            collisionUniform });
        computeForceKernel.setSSBOs(new String[] { NODES_SSBO.getName(), WORK_QUEUE_SSBO.getName(), SWAPPING_BODIES_IN_SSBO.getName(), SWAPPING_BODIES_OUT_SSBO.getName() });
        computeForceKernel.setXWorkGroupsFunction(() -> {
            return numGroups();
        });

        //Merge Bodies Kernel
        mergeBodiesKernel = new ComputeShader("KERNEL_MERGE_BODIES", this);
        mergeBodiesKernel.setUniforms(new Uniform[] { mergeQueueTailUniform });
        mergeBodiesKernel.setSSBOs(new String[] { MERGE_QUEUE_SSBO.getName(), SWAPPING_BODIES_IN_SSBO.getName() });
        mergeBodiesKernel.setXWorkGroupsFunction(() -> {
            return 1;
        });

        //Debug Kernel
        debugKernel = new ComputeShader("KERNEL_DEBUG", this);
        debugKernel.setUniforms(new Uniform[] { numWorkGroupsUniform });
        debugKernel.setSSBOs(new String[] { AABB_SSBO.getName() });
        debugKernel.setXWorkGroupsFunction(() -> {
            return numGroups();
        });

        
    }
    
    /* --------- Rendering --------- */
    public void render() {
        if (renderMode == RenderMode.OFF) return;
        switch (renderMode) {
            case POINTS: renderPoints(); break;
            case IMPOSTOR_SPHERES: renderImpostorSpheres(); break;
            case MESH_SPHERES: renderMeshSpheres(); break;
        }
    }

    public void renderPoints() {
        // Do not clear or swap; caller owns window
        glUseProgram(renderProgram);
        // MVP will be sent by caller before rendering
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, BODIES_IN_SSBO_BINDING, SWAPPING_BODIES_OUT_SSBO.getBufferLocation());
        glBindVertexArray(vao);
        glDrawArrays(GL_POINTS, 0, planets.size());
        glUseProgram(0);
    }

    public void renderImpostorSpheres() {
        glUseProgram(impostorProgram);
        glUniform1f(uImpostorPointScaleLoc, impostorPointScale);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, BODIES_IN_SSBO_BINDING, SWAPPING_BODIES_OUT_SSBO.getBufferLocation());
        glBindVertexArray(vao);
        glDrawArrays(GL_POINTS, 0, planets.size());
        glUseProgram(0);
    }

    public void renderMeshSpheres() {
        if (sphereVao == 0 || sphereIndexCount == 0) return;
        glUseProgram(sphereProgram);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, BODIES_IN_SSBO_BINDING, SWAPPING_BODIES_OUT_SSBO.getBufferLocation());
        glBindVertexArray(sphereVao);
        glUniform1f(uSphereRadiusScaleLoc, sphereRadiusScale);
        // Distance/color uniforms
        glUniform3f(uCameraPosLocSphere, cameraX, cameraY, cameraZ);
        glUniform1f(uNearDistLocSphere, nearDist);
        glUniform1f(uFarDistLocSphere, farDist);
        int instanceCount = Math.min(planets.size(), maxMeshInstances);
        glDrawElementsInstanced(GL_TRIANGLES, sphereIndexCount, GL_UNSIGNED_INT, 0L, instanceCount);
        glBindVertexArray(0);
        glUseProgram(0);
    }
 
    /* --------- Barnes-Hut --------- */

    private void computeAABB() {
        long computeAABBStartTime = 0;
        long collapseAABBStartTime = 0;

        if (DEBUG_BARNES_HUT) {
            computeAABBStartTime = System.nanoTime();
        }

        computeAABBKernel.run();

        if (DEBUG_BARNES_HUT) {
            glFinish(); 
            computeAABBTime += System.nanoTime() - computeAABBStartTime;
            collapseAABBStartTime = System.nanoTime();
        }

        collapseAABBKernel.run();

        if (DEBUG_BARNES_HUT) {
            glFinish();
            collapseAABBTime += System.nanoTime() - collapseAABBStartTime;
            aabbTime = computeAABBTime + collapseAABBTime;
        }

    }

    private void generateMortonCodes() {
        if (DEBUG_BARNES_HUT) {
            mortonTime = System.nanoTime();
        }

        mortonKernel.run();

        if (DEBUG_BARNES_HUT) {
            glFinish();
            mortonTime += System.nanoTime() - mortonTime;
        }

    }

    private void radixSort() {



        int numPasses = (int)Math.ceil(63.0 / 4.0); // 16 passes for 63-bit Morton codes
        
        // Current input/output morton and index buffers
        CURRENT_MORTON_IN_SSBO.setBufferLocation(MORTON_IN_SSBO.getBufferLocation());
        CURRENT_INDEX_IN_SSBO.setBufferLocation(INDEX_IN_SSBO.getBufferLocation());
        CURRENT_MORTON_OUT_SSBO.setBufferLocation(MORTON_OUT_SSBO.getBufferLocation());
        CURRENT_INDEX_OUT_SSBO.setBufferLocation(INDEX_OUT_SSBO.getBufferLocation());

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

            if (DEBUG_BARNES_HUT) {
                radixSortHistogramStartTime = System.nanoTime();
            }

            // Phase 1: Histogram
            radixSortHistogramKernel.run();

            if (DEBUG_BARNES_HUT) {
                glFinish();
                radixSortHistogramTime += System.nanoTime() - radixSortHistogramStartTime;
                radixSortScanParallelStartTime = System.nanoTime();
            }

            // Phase 2: Scan
            radixSortParallelScanKernel.run();

            if (DEBUG_BARNES_HUT) {
                glFinish();
                radixSortScanParallelTime += System.nanoTime() - radixSortScanParallelStartTime;
                radixSortScanExclusiveStartTime = System.nanoTime();
            }

            radixSortExclusiveScanKernel.run();
            

            if (DEBUG_BARNES_HUT) {
                glFinish();
                radixSortScanExclusiveTime += System.nanoTime() - radixSortScanExclusiveStartTime;
                radixSortScatterStartTime = System.nanoTime();
            }

            // Phase 3: Scatter
            radixSortScatterKernel.run();

            if (DEBUG_BARNES_HUT) {
                glFinish();
                radixSortScatterTime += System.nanoTime() - radixSortScatterStartTime;
            }
            

            // Swap input/output buffers for next pass
            int tempMortonIn = CURRENT_MORTON_IN_SSBO.getBufferLocation();
            int tempIndexIn = CURRENT_INDEX_IN_SSBO.getBufferLocation();
            CURRENT_MORTON_IN_SSBO.setBufferLocation(CURRENT_MORTON_OUT_SSBO.getBufferLocation());
            CURRENT_INDEX_IN_SSBO.setBufferLocation(CURRENT_INDEX_OUT_SSBO.getBufferLocation());
            CURRENT_MORTON_OUT_SSBO.setBufferLocation(tempMortonIn);
            CURRENT_INDEX_OUT_SSBO.setBufferLocation(tempIndexIn);
        }
        // After final pass, sorted data is in currentMortonIn/currentIndexIn
        // Update our "current" buffers to point to the final sorted data
        MORTON_IN_SSBO.setBufferLocation(CURRENT_MORTON_IN_SSBO.getBufferLocation());
        INDEX_IN_SSBO.setBufferLocation(CURRENT_INDEX_IN_SSBO.getBufferLocation());
        if (DEBUG_BARNES_HUT) {
            radixSortTime = radixSortHistogramTime + radixSortScanParallelTime + radixSortScanExclusiveTime + radixSortScatterTime;
        }

    }

    private void buildBinaryRadixTree() {
        if (DEBUG_BARNES_HUT) {
            buildTreeTime = System.nanoTime();
        }
        buildBinaryRadixTreeKernel.run();
        if (DEBUG_BARNES_HUT) {
            glFinish();
            buildTreeTime = System.nanoTime() - buildTreeTime;
        }
    }
    
    private void computeCOMAndLocation() {
        if (DEBUG_BARNES_HUT) {
            initLeavesTime = System.nanoTime();
        }
        // Reset work queue head/tail to 0 before init
        WORK_QUEUE_SSBO.bind();
        ByteBuffer initQ = BufferUtils.createByteBuffer(2 * Integer.BYTES);
        initQ.putInt(0).putInt(0).flip();
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, initQ);
        SSBO.unBind();

        initLeavesKernel.run();

        if (DEBUG_BARNES_HUT) {
            glFinish();
            initLeavesTime = System.nanoTime() - initLeavesTime;
            propagateNodesTime = System.nanoTime();
        }

        for (int i = 0; i < PROPAGATE_NODES_ITERATIONS; i++) {
            propagateNodesKernel.run();
        }
        if (DEBUG_BARNES_HUT) {
            glFinish();
            propagateNodesTime = System.nanoTime() - propagateNodesTime;
            computeCOMAndLocationTime = initLeavesTime + propagateNodesTime;
        }
    }

    private void computeForce() {
        if (DEBUG_BARNES_HUT) {
            computeForceTime = System.nanoTime();
        }

        computeForceKernel.run();
        if (DEBUG_BARNES_HUT) {
            glFinish();
            computeForceTime = System.nanoTime() - computeForceTime;
        }

        // Swap source and destination buffers for next iteration
        int tmpIn = SWAPPING_BODIES_IN_SSBO.getBufferLocation();
        SWAPPING_BODIES_IN_SSBO.setBufferLocation(SWAPPING_BODIES_OUT_SSBO.getBufferLocation());
        SWAPPING_BODIES_OUT_SSBO.setBufferLocation(tmpIn);
    }
    
    /* --------- Helper functions --------- */
        
    public void enqueue(GPUCommands.GPUCommand command) {
        if (command != null) {
            commandQueue.offer(command);
        }
    }

    private int numGroups() {
        return (planets.size() + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
    }

    public FloatBuffer packPlanets(List<Planet> planets) {
            // Packs planet data to float buffer: pos(x,y,z), mass, vel(x,y,z), pad, color(r,g,b,a)
        int count = planets.size();
        
        FloatBuffer buf = BufferUtils.createFloatBuffer(count * 12);
        for (int i = 0; i < count; i++) {
            Planet p = planets.get(i);
            buf.put((float)p.x).put((float)p.y).put((float)p.z).put((float)p.mass);
            buf.put((float)p.xVelocity).put((float)p.yVelocity).put((float)p.zVelocity).put(0f);
            java.awt.Color c = p.getColor();
            float cr = c != null ? (c.getRed() / 255f) : 1.0f;
            float cg = c != null ? (c.getGreen() / 255f) : 1.0f;
            float cb = c != null ? (c.getBlue() / 255f) : 1.0f;
            buf.put(cr).put(cg).put(cb).put(1.0f);
        }
        buf.flip();
        return buf;
    }


    public void uploadPlanetsData(List<Planet> newPlanets) {
        // Assumes buffers are already correctly sized
        this.planets = new ArrayList<>(newPlanets);
        FloatBuffer data = packPlanets(this.planets);
        glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_WRITE_ONLY);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, data);
        SSBO.unBind();
    }

    public void resizeBuffersAndUpload(List<Planet> newPlanets) {
        // Resizes SSBOs to fit new count, then uploads
        this.planets = new ArrayList<>(newPlanets);

        // Resize body buffers
        FIXED_BODIES_IN_SSBO.createBuffer();
        FIXED_BODIES_OUT_SSBO.createBuffer();
        
        // Resize Barnes-Hut auxiliary buffers
        MORTON_IN_SSBO.createBuffer();
        INDEX_IN_SSBO.createBuffer();
        NODES_SSBO.createBuffer();
        AABB_SSBO.createBuffer();
        WG_HIST_SSBO.createBuffer();
        WG_SCANNED_SSBO.createBuffer();
        GLOBAL_BASE_SSBO.createBuffer();
        MORTON_OUT_SSBO.createBuffer();
        INDEX_OUT_SSBO.createBuffer();
        WORK_QUEUE_SSBO.createBuffer();
        MERGE_QUEUE_SSBO.createBuffer();

        MERGE_QUEUE_SSBO.bind();

        FloatBuffer data = packPlanets(this.planets);
        FIXED_BODIES_IN_SSBO.bind();
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, data);
        SSBO.unBind();

        SWAPPING_BODIES_IN_SSBO = FIXED_BODIES_IN_SSBO;
        SWAPPING_BODIES_OUT_SSBO = FIXED_BODIES_OUT_SSBO;
        CURRENT_MORTON_IN_SSBO = MORTON_IN_SSBO;
        CURRENT_INDEX_IN_SSBO = INDEX_IN_SSBO;
        CURRENT_MORTON_OUT_SSBO = MORTON_OUT_SSBO;
        CURRENT_INDEX_OUT_SSBO = INDEX_OUT_SSBO;
    }

   
    public void setMvp(java.nio.FloatBuffer mvp4x4ColumnMajor) {
        // Called by the window each frame to set camera transform 
        glUseProgram(renderProgram);
        glUniformMatrix4fv(uMvpLocation, false, mvp4x4ColumnMajor);
        glUseProgram(0);
        glUseProgram(impostorProgram);
        glUniformMatrix4fv(uMvpLocationImpostor, false, mvp4x4ColumnMajor);
        glUseProgram(0);
        glUseProgram(sphereProgram);
        glUniformMatrix4fv(uMvpLocationSphere, false, mvp4x4ColumnMajor);
        glUseProgram(0);
    }
    
    /* --------- Distance-based coloring configuration -------- */
    public void setCameraPos(float x, float y, float z) {
            this.cameraX = x;
            this.cameraY = y;
            this.cameraZ = z;
        }
    
    public void setDistanceRange(float nearDist, float farDist) {
            this.nearDist = nearDist;
            this.farDist = Math.max(farDist, nearDist + 0.0001f);
        }
    
    /* --------- SHADERS --------- */

    private String getVertexShaderSource() {
        try {
            return Files.readString(Paths.get("src/main/resources/shaders/points/vertexshader.glsl"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read vertex shader: " + e.getMessage());
        }
    }

    private String getFragmentShaderSource() {
        try {
            return Files.readString(Paths.get("src/main/resources/shaders/points/fragmentshader.glsl"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fragment shader: " + e.getMessage());
        }
    }

    private String getImpostorVertexShaderSource() {
        try {
            return Files.readString(Paths.get("src/main/resources/shaders/imposter/impostor_vertex.glsl"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read impostor vertex shader: " + e.getMessage());
        }
    }

    private String getImpostorFragmentShaderSource() {
        try {
            return Files.readString(Paths.get("src/main/resources/shaders/imposter/impostor_fragment.glsl"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read impostor fragment shader: " + e.getMessage());
        }
    }

    private String getSphereVertexShaderSource() {
        try {
            return Files.readString(Paths.get("src/main/resources/shaders/spheres/sphere_vertex.glsl"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read sphere vertex shader: " + e.getMessage());
        }
    }

    private String getSphereFragmentShaderSource() {
        try {
            return Files.readString(Paths.get("src/main/resources/shaders/spheres/sphere_fragment.glsl"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read sphere fragment shader: " + e.getMessage());
        }
    }

    /* --------- Sphere mesh generation --------- */
    public void setSphereDetail(int stacks, int slices) {
        if (stacks < 3) stacks = 3;
        if (slices < 3) slices = 3;
        this.sphereStacks = stacks;
        this.sphereSlices = slices;
        rebuildSphereMesh();
    }

    public void setRenderMode(RenderMode mode) {
        if (mode != null) this.renderMode = mode;
    }

    public void setImpostorPointScale(float scale) {
        this.impostorPointScale = scale <= 0 ? 1.0f : scale;
    }


    public void setSphereRadiusScale(float scale) {
        this.sphereRadiusScale = scale;
    }

    public void setMaxMeshInstances(int max) {
        this.maxMeshInstances = Math.max(1, max);
    }

    private void rebuildSphereMesh() {
        // Dispose previous
        if (sphereVao != 0) glDeleteVertexArrays(sphereVao);
        if (sphereVbo != 0) glDeleteBuffers(sphereVbo);
        if (sphereIbo != 0) glDeleteBuffers(sphereIbo);
        sphereVao = glGenVertexArrays();
        sphereVbo = glGenBuffers();
        sphereIbo = glGenBuffers();

        FloatBuffer positions = generateSpherePositions(sphereStacks, sphereSlices);
        IntBuffer indices = generateSphereIndices(sphereStacks, sphereSlices);
        sphereIndexCount = indices.remaining();

        glBindVertexArray(sphereVao);
        glBindBuffer(GL_ARRAY_BUFFER, sphereVbo);
        glBufferData(GL_ARRAY_BUFFER, positions, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0L);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, sphereIbo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private FloatBuffer generateSpherePositions(int stacks, int slices) {
        int vertexCount = (stacks + 1) * (slices + 1);
        FloatBuffer buf = BufferUtils.createFloatBuffer(vertexCount * 3);
        for (int i = 0; i <= stacks; i++) {
            double v = (double) i / stacks;
            double phi = v * Math.PI; // 0..PI
            double sinPhi = Math.sin(phi);
            double cosPhi = Math.cos(phi);
            for (int j = 0; j <= slices; j++) {
                double u = (double) j / slices;
                double theta = u * 2.0 * Math.PI; // 0..2PI
                double sinTheta = Math.sin(theta);
                double cosTheta = Math.cos(theta);
                float x = (float) (cosTheta * sinPhi);
                float y = (float) (cosPhi);
                float z = (float) (sinTheta * sinPhi);
                buf.put(x).put(y).put(z);
            }
        }
        buf.flip();
        return buf;
    }

    private IntBuffer generateSphereIndices(int stacks, int slices) {
        int quadCount = stacks * slices;
        IntBuffer idx = BufferUtils.createIntBuffer(quadCount * 6);
        int vertsPerRow = slices + 1;
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                int i0 = i * vertsPerRow + j;
                int i1 = i0 + 1;
                int i2 = i0 + vertsPerRow;
                int i3 = i2 + 1;
                // two triangles per quad
                idx.put(i0).put(i2).put(i1);
                idx.put(i1).put(i2).put(i3);
            }
        }
        idx.flip();
        return idx;
    }

    /* --------- Cleanup --------- */
    public void cleanupEmbedded() {
        // Minimal cleanup of GL objects created in embedded mode
        if (computeAABBKernel != null) computeAABBKernel.delete();
        if (mortonKernel != null) mortonKernel.delete();
        if (radixSortHistogramKernel != null) radixSortHistogramKernel.delete();
        if (radixSortParallelScanKernel != null) radixSortParallelScanKernel.delete();
        if (radixSortExclusiveScanKernel != null) radixSortExclusiveScanKernel.delete();
        if (radixSortScatterKernel != null) radixSortScatterKernel.delete();
        if (buildBinaryRadixTreeKernel != null) buildBinaryRadixTreeKernel.delete();
        if (initLeavesKernel != null) initLeavesKernel.delete();
        if (propagateNodesKernel != null) propagateNodesKernel.delete();
        if (computeForceKernel != null) computeForceKernel.delete();
        if (renderProgram != 0) glDeleteProgram(renderProgram);
        if (impostorProgram != 0) glDeleteProgram(impostorProgram);
        if (sphereProgram != 0) glDeleteProgram(sphereProgram);
        if (debugKernel != null) debugKernel.delete();
        if (FIXED_BODIES_IN_SSBO != null) FIXED_BODIES_IN_SSBO.delete();
        if (FIXED_BODIES_OUT_SSBO != null) FIXED_BODIES_OUT_SSBO.delete();
        if (MORTON_IN_SSBO != null) MORTON_IN_SSBO.delete();
        if (INDEX_IN_SSBO != null) INDEX_IN_SSBO.delete();
        if (NODES_SSBO != null) NODES_SSBO.delete();
        if (AABB_SSBO != null) AABB_SSBO.delete();
        if (WG_HIST_SSBO != null) WG_HIST_SSBO.delete();
        if (WG_SCANNED_SSBO != null) WG_SCANNED_SSBO.delete();
        if (MORTON_OUT_SSBO != null) MORTON_OUT_SSBO.delete();
        if (INDEX_OUT_SSBO != null) INDEX_OUT_SSBO.delete();
        if (WORK_QUEUE_SSBO != null) WORK_QUEUE_SSBO.delete();
        if (MERGE_QUEUE_SSBO != null) MERGE_QUEUE_SSBO.delete();
        if (vao != 0) glDeleteVertexArrays(vao);
        if (sphereVao != 0) glDeleteVertexArrays(sphereVao);
        if (sphereVbo != 0) glDeleteBuffers(sphereVbo);
        if (sphereIbo != 0) glDeleteBuffers(sphereIbo);
    }

    
    /* --------- Debugging --------- */
    private String printProfiling() {
        long totalTime = aabbTime + mortonTime + radixSortTime + buildTreeTime + propagateNodesTime + computeForceTime;
        long percentAABB = (aabbTime * 100) / totalTime;
        long percentAABBCompute = (computeAABBTime * 100) / totalTime;
        long percentCollapseAABB = (collapseAABBTime * 100) / totalTime;
        long percentMorton = (mortonTime * 100) / totalTime;
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

        final long oneMillion = 1_000_000;
        return aabbTime/oneMillion + " ms (" + percentAABB + "%)" +":AABB\n" + 
               "\t" + computeAABBTime/oneMillion + " ms (" + percentAABBCompute + "%)" +":Compute\n" + 
               "\t" + collapseAABBTime/oneMillion + " ms (" + percentCollapseAABB + "%)" +":Collapse\n" + 
               mortonTime/oneMillion + " ms (" + percentMorton + "%)" +":Morton\n" +
               radixSortTime/oneMillion + " ms (" + percentRadixSort + "%)" +":Radix Sort\n" +
               "\t" + radixSortHistogramTime/oneMillion + " ms (" + percentRadixSortHistogram + "%)" +":Histogram\n" +
               "\t" + radixSortScanParallelTime/oneMillion + " ms (" + percentRadixSortParallelScan + "%)" +":Parallel Scan\n" +
               "\t" + radixSortScanExclusiveTime/oneMillion + " ms (" + percentRadixSortExclusiveScan + "%)" +":Exclusive Scan\n" +
               "\t" + radixSortScatterTime/oneMillion + " ms (" + percentRadixSortScatter + "%)" +":Scatter\n" +
               buildTreeTime/oneMillion + " ms (" + percentBuildTree + "%)" +":Build Tree\n" +
               "\t" + initLeavesTime/oneMillion + " ms (" + percentInitLeaves + "%)" +":Init Leaves\n" +
               "\t" + propagateNodesTime/oneMillion + " ms (" + percentPropagateNodes + "%)" +":Propagate Nodes\n" +
               "\t" + computeCOMAndLocationTime/oneMillion + " ms (" + percentComputeCOMAndLocation + "%)" +":Compute COM and Location\n" +
               propagateNodesTime/oneMillion + " ms (" + percentPropagateNodes + "%)" +":COM\n" +
               computeForceTime/oneMillion + " ms (" + percentComputeForce + "%)" +":Force\n" +
               totalTime/oneMillion + " ms" +":Total\n";
    }
    
    private void checkGLError(String operation) {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            System.err.println("OpenGL Error after " + operation + ": " + error);
        }
    }
    
    private void debugAABB() {
        AABB_SSBO.bind();
        ByteBuffer bb = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        FloatBuffer fb = bb.asFloatBuffer();

        // 7) Print the first few AABBs
        for (int i = 0; i < Math.min(8, planets.size()); ++i) {
            float minx = fb.get(); float miny = fb.get(); float minz = fb.get(); float pad0 = fb.get();
            float maxx = fb.get(); float maxy = fb.get(); float maxz = fb.get(); float pad1 = fb.get();
            System.out.printf("DEBUG AABB %d: min=(%f,%f,%f) max=(%f,%f,%f) pad0=%f pad1=%f%n",
                            i, minx, miny, minz, maxx, maxy, maxz, pad0, pad1);
        }

        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        SSBO.unBind();
        
        
    }
    
    private float[][] oldComputeAABB() {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        
        SWAPPING_BODIES_IN_SSBO.bind();
        ByteBuffer buffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        FloatBuffer bodyData = buffer.asFloatBuffer();
        
        for (int i = 0; i < planets.size(); i++) {
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
        MORTON_IN_SSBO.bind();
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
    MORTON_IN_SSBO.bind();
    ByteBuffer mortonBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
    IntBuffer mortonData = mortonBuffer.asIntBuffer();
    glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
    INDEX_IN_SSBO.bind();
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
    GLOBAL_BASE_SSBO.bind();
    ByteBuffer globalBaseBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
    IntBuffer globalBaseData = globalBaseBuffer.asIntBuffer();
    glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
    BUCKET_TOTALS_SSBO.bind();
    ByteBuffer bucketTotalsBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
    IntBuffer bucketTotalsData = bucketTotalsBuffer.asIntBuffer();
    glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
    MORTON_OUT_SSBO.bind();
    ByteBuffer mortonOutBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
    IntBuffer mortonOutData = mortonOutBuffer.asIntBuffer();
    glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
    INDEX_OUT_SSBO.bind();
    ByteBuffer indexOutBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
    IntBuffer indexOutData = indexOutBuffer.asIntBuffer();
    glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
    for (int i = 0; i < Math.min(10, planets.size()); i++) {
        int morton = mortonData.capacity() > i ? mortonData.get(i) : -1;   
        int index = indexData.capacity() > i ? indexData.get(i) : -1;
        int wgHist = wgHistData.capacity() > i ? wgHistData.get(i) : -1;
        int wgScanned = wgScannedData.capacity() > i ? wgScannedData.get(i) : -1;
        int globalBase = globalBaseData.capacity() > i ? globalBaseData.get(i) : -1;
        int bucketTotals = bucketTotalsData.capacity() > i ? bucketTotalsData.get(i) : -1;
        int mortonOut = mortonOutData.capacity() > i ? mortonOutData.get(i) : -1;
        int indexOut = indexOutData.capacity() > i ? indexOutData.get(i) : -1;
        System.out.printf("  [%d]: morton=%d index=%d wgHist=%d wgScanned=%d globalBase=%d bucketTotals=%d mortonOut=%d indexOut=%d\n", i, morton, index, wgHist, wgScanned, globalBase, bucketTotals, mortonOut, indexOut);
    }
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

}

    private void debugSorting() {
        System.out.println("=== RADIX SORT RESULTS ===");
            // Read sorted Morton codes
            MORTON_IN_SSBO.bind();
            ByteBuffer mortonBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            IntBuffer mortonData = mortonBuffer.asIntBuffer();
            System.out.println("Sorted Morton codes:");
            for (int i = 0; i < Math.min(100, planets.size()); i++) {
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
            INDEX_OUT_SSBO.bind();
            ByteBuffer indexBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            IntBuffer indexData = indexBuffer.asIntBuffer();
            System.out.println("Sorted body indices:");
            for (int i = 0; i < Math.min(100, planets.size()); i++) {
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
            System.out.println("=== STUCK NODE ANALYSIS ===");
            NODES_SSBO.bind();
            ByteBuffer nodeBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            IntBuffer nodeData = nodeBuffer.asIntBuffer();
            
            int numLeaves = planets.size();
            int totalNodes = 2 * planets.size() - 1;
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
            System.out.println("Tree nodes:");
            
            //int numLeaves = planets.size();
            //int numInternalNodes = planets.size() - 1;
            //int totalNodes = 2 * planets.size() - 1;
            
            // Show first few leaf nodes (indices 0 to numBodies-1)

            System.out.println(Node.getNodes(nodeData, 0, Math.min(20, numLeaves)));
            
            // Show internal nodes from halfway point (indices numBodies to 2*numBodies-2)
            System.out.println(Node.getNodes(nodeData, numLeaves, Math.min(numLeaves + 20, totalNodes)));

            glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            verifyTreeStructure(nodeData, numLeaves, totalNodes);
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
    
    /* --------- Shader checking --------- */
    public static void checkShader(int shader) {
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            System.err.println("Shader compilation failed: " + glGetShaderInfoLog(shader));
        }
    }

    public static void checkProgram(int program) {
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            System.err.println("Program linking failed: " + glGetProgramInfoLog(program));
        }
    }



    
}
