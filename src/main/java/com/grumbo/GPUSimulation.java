package com.grumbo;

import org.lwjgl.*;

import java.nio.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.opengl.GL43C.*;




public class GPUSimulation {
    private int vao; // points VAO

    private long lastFrameFence = 0L;

    // Simulation params
    private static final int WORK_GROUP_SIZE = 128;
    private static final int PROPAGATE_NODES_ITERATIONS = 30;
    private static final int NUM_RADIX_BUCKETS = 16;
    private static final boolean DEBUG_BARNES_HUT = true; // Note debugging will slow down the simulation, inconsistently
    private static final int DEBUG_FRAME_INTERVAL = 6000; // Show debug every N frames
    private int frameCounter = 0;

    private int computeAABBKernelProgram;
    private int collapseAABBKernelProgram;
    private int mortonKernelProgram;
    private int radixSortHistogramKernelProgram;
    private int radixSortParallelScanKernelProgram;
    private int radixSortExclusiveScanKernelProgram;
    private int radixSortScatterKernelProgram;
    private int buildBinaryRadixTreeKernelProgram;
    private int initLeavesKernelProgram;
    private int propagateNodesKernelProgram;
    private int computeForceKernelProgram;
    private int debugKernelProgram;
    private int renderProgram; // points program
    private int impostorProgram; // point-sprite impostor spheres
    private int sphereProgram;   // instanced mesh spheres

    private int SWAPPING_BODIES_IN_SSBO;
    private int SWAPPING_BODIES_OUT_SSBO;

    private int FIXED_BODIES_IN_SSBO;
    private final int BODIES_IN_SSBO_BINDING = 0;
    private int FIXED_BODIES_OUT_SSBO;
    private final int BODIES_OUT_SSBO_BINDING = 1;
    private int MORTON_KEYS_SSBO;
    private final int MORTON_KEYS_SSBO_BINDING = 2;
    private int INDICES_SSBO;
    private final int INDICES_SSBO_BINDING = 3;
    private int NODES_SSBO;
    private final int NODES_SSBO_BINDING = 4;
    private int AABB_SSBO;
    private final int AABB_SSBO_BINDING = 5;
    private int WG_HIST_SSBO;
    private final int WG_HIST_SSBO_BINDING = 6;
    private int WG_SCANNED_SSBO;
    private final int WG_SCANNED_SSBO_BINDING = 7;
    private int GLOBAL_BASE_SSBO;
    private final int GLOBAL_BASE_SSBO_BINDING = 8;
    private int BUCKET_TOTALS_SSBO;
    private final int BUCKET_TOTALS_SSBO_BINDING = 9;
    private int MORTON_OUT_SSBO;
    private final int MORTON_OUT_SSBO_BINDING = 10;
    private int INDEX_OUT_SSBO;
    private final int INDEX_OUT_SSBO_BINDING = 11;
    private int WORK_QUEUE_SSBO;
    private final int WORK_QUEUE_SSBO_BINDING = 12;



    private int uMvpLocation;           // for points
    private int uMvpLocationImpostor;   // for impostors
    private int uMvpLocationSphere;     // for mesh spheres
    private int uSphereRadiusScaleLoc;  // mass->radius scale
    private int uCameraPosLocSphere;
    private int uNearDistLocSphere;
    private int uFarDistLocSphere;
    private int uImpostorPointScaleLoc; // impostor scale


    //Debug variables
    private long aabbTime = 0;
    private long computeAABBTime = 0;
    private long collapseAABBTime = 0;
    private long mortonTime = 0;
    private long radixSortTime = 0;
    private long radixSortHistogramTime = 0;
    private long radixSortScanTime = 0;
    private long radixSortScatterTime = 0;
    private long propagateNodesTime = 0;
    private long buildTreeTime = 0;
    private long computeForceTime = 0;
    public String debugString = "";




    private ArrayList<Planet> planets;

    public enum RenderMode {
        OFF,
        POINTS,
        IMPOSTOR_SPHERES,
        MESH_SPHERES
    }




    private RenderMode renderMode = RenderMode.MESH_SPHERES;

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


    public GPUSimulation(ArrayList<Planet> planets) {
        this.planets = planets;
    }

    private final ConcurrentLinkedQueue<GPUCommands.GPUCommand> commandQueue = new ConcurrentLinkedQueue<>();

    public void enqueue(GPUCommands.GPUCommand command) {
        if (command != null) {
            commandQueue.offer(command);
        }
    }

    public void processCommands() {
        GPUCommands.GPUCommand cmd;
        while ((cmd = commandQueue.poll()) != null) {
            cmd.run(this);
        }
    }

    public void createAndAttachComputeShader(int program, String kernelName) {
        int computeShader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(computeShader, insertDefineAfterVersion(getComputeShaderSource(), kernelName));
        glCompileShader(computeShader);
        checkShader(computeShader);
        glAttachShader(program, computeShader);
        glLinkProgram(program);
        checkProgram(program);
    }

    public void initWithCurrentContext() {
        // Create and bind a dummy VAO (required for core profile draws)
        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        // Create compute shaders

        //Compute AABB Kernel
        computeAABBKernelProgram = glCreateProgram();
        createAndAttachComputeShader(computeAABBKernelProgram, "KERNEL_COMPUTE_AABB");

        //Morton Kernel
        mortonKernelProgram = glCreateProgram();
        createAndAttachComputeShader(mortonKernelProgram, "KERNEL_MORTON");

        //Radix Sort Histogram Kernel
        radixSortHistogramKernelProgram = glCreateProgram();
        createAndAttachComputeShader(radixSortHistogramKernelProgram, "KERNEL_RADIX_HIST");

        //Radix Sort Parallel Scan Kernel
        radixSortParallelScanKernelProgram = glCreateProgram();
        createAndAttachComputeShader(radixSortParallelScanKernelProgram, "KERNEL_RADIX_PARALLEL_SCAN");

        //Radix Sort Exclusive Scan Kernel
        radixSortExclusiveScanKernelProgram = glCreateProgram();
        createAndAttachComputeShader(radixSortExclusiveScanKernelProgram, "KERNEL_RADIX_EXCLUSIVE_SCAN");

        //Radix Sort Scatter Kernel
        radixSortScatterKernelProgram = glCreateProgram();
        createAndAttachComputeShader(radixSortScatterKernelProgram, "KERNEL_RADIX_SCATTER");

        //Build Binary Radix Tree Kernel
        buildBinaryRadixTreeKernelProgram = glCreateProgram();
        createAndAttachComputeShader(buildBinaryRadixTreeKernelProgram, "KERNEL_BUILD_BINARY_RADIX_TREE");

        //Init Leaves Kernel
        initLeavesKernelProgram = glCreateProgram();
        createAndAttachComputeShader(initLeavesKernelProgram, "KERNEL_INIT_LEAVES");

        //Propagate Nodes Kernel
        propagateNodesKernelProgram = glCreateProgram();
        createAndAttachComputeShader(propagateNodesKernelProgram, "KERNEL_PROPAGATE_NODES");

        //Collapse AABB Kernel
        collapseAABBKernelProgram = glCreateProgram();
        createAndAttachComputeShader(collapseAABBKernelProgram, "KERNEL_COLLAPSE_AABB");

        //Compute Force Kernel
        computeForceKernelProgram = glCreateProgram();
        createAndAttachComputeShader(computeForceKernelProgram, "KERNEL_COMPUTE_FORCE");

        //Debug Kernel
        debugKernelProgram = glCreateProgram();
        createAndAttachComputeShader(debugKernelProgram, "KERNEL_DEBUG");

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

        // Create SSBOs
        FIXED_BODIES_IN_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, FIXED_BODIES_IN_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, packPlanets(planets), GL_DYNAMIC_COPY);

        FIXED_BODIES_OUT_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, FIXED_BODIES_OUT_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, planets.size() * 12 * Float.BYTES, GL_DYNAMIC_COPY);

        // Create Barnes-Hut auxiliary SSBOs
        int numBodies = planets.size();
        int numWorkGroups = numGroups();
        int NUM_BUCKETS = 16; // 2^RADIX_BITS where RADIX_BITS=4
        
        MORTON_KEYS_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, MORTON_KEYS_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Long.BYTES, GL_DYNAMIC_COPY);
        
        INDICES_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, INDICES_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Integer.BYTES, GL_DYNAMIC_COPY);
        
        NODES_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, NODES_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, (2 * numBodies - 1) * Node.STRUCT_SIZE * Integer.BYTES, GL_DYNAMIC_COPY); // 16 uints per node (2 vec4s + 6 uints + 2 padding)

        // AABB ping-pong buffers (vec3 pairs per workgroup)
        AABB_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, AABB_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numWorkGroups * 2 * 4 * Float.BYTES, GL_DYNAMIC_COPY);
        
        WG_HIST_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, WG_HIST_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numWorkGroups * NUM_BUCKETS * Integer.BYTES, GL_DYNAMIC_COPY);
        
        WG_SCANNED_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, WG_SCANNED_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numWorkGroups * NUM_BUCKETS * Integer.BYTES, GL_DYNAMIC_COPY);
        
        GLOBAL_BASE_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, GLOBAL_BASE_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, NUM_BUCKETS * Integer.BYTES, GL_DYNAMIC_COPY);

        BUCKET_TOTALS_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, BUCKET_TOTALS_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, NUM_BUCKETS * Integer.BYTES, GL_DYNAMIC_COPY);
        
        MORTON_OUT_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, MORTON_OUT_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Long.BYTES, GL_DYNAMIC_COPY);
        
        INDEX_OUT_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, INDEX_OUT_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Integer.BYTES, GL_DYNAMIC_COPY);
        
        
        // Work queue buffer: head (uint), tail (uint), items[numBodies] (uint)
        WORK_QUEUE_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, WORK_QUEUE_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, (2 + numBodies) * Integer.BYTES, GL_DYNAMIC_COPY);

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

        SWAPPING_BODIES_IN_SSBO = FIXED_BODIES_IN_SSBO;
        SWAPPING_BODIES_OUT_SSBO = FIXED_BODIES_OUT_SSBO;

        glEnable(GL_PROGRAM_POINT_SIZE);

        // Initialize sphere mesh VAO/VBO/IBO
        rebuildSphereMesh();
    }

    private void debug() {
                // 1) Print IDs so we can be sure
        System.out.println("AABB_OUT_SSBO id = " + AABB_SSBO + " bindingIndex = " + AABB_SSBO_BINDING);

        // 2) Bind program & buffers exactly like the shader expects
        glUseProgram(debugKernelProgram);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, AABB_SSBO_BINDING, AABB_SSBO);
        glUniform1ui(glGetUniformLocation(debugKernelProgram, "numWorkGroups"), numGroups());

        // 3) Set uniforms
        int loc = glGetUniformLocation(debugKernelProgram, "debugCount");
        glUniform1ui(loc, (int)numGroups()); // or the number you want to test

        // 4) Dispatch
        glDispatchCompute(numGroups(), 1, 1);

        // 5) Ensure writes finished
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glFinish(); // ok for debug

        // 6) Readback the buffer contents
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, AABB_SSBO);
        ByteBuffer bb = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        FloatBuffer fb = bb.asFloatBuffer();

        // 7) Print the first few AABBs
        for (int i = 0; i < 8; ++i) {
            float minx = fb.get(); float miny = fb.get(); float minz = fb.get(); float pad0 = fb.get();
            float maxx = fb.get(); float maxy = fb.get(); float maxz = fb.get(); float pad1 = fb.get();
            System.out.printf("DEBUG AABB %d: min=(%f,%f,%f) max=(%f,%f,%f) pad0=%f pad1=%f%n",
                            i, minx, miny, minz, maxx, maxy, maxz, pad0, pad1);
        }
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }

    public void step() {
        processCommands();
        //debug();
        stepBarnesHut();
    }

    public void renderPoints() {
        // Do not clear or swap; caller owns window
        glUseProgram(renderProgram);
        // MVP will be sent by caller before rendering
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, BODIES_IN_SSBO_BINDING, SWAPPING_BODIES_OUT_SSBO);
        glBindVertexArray(vao);
        glDrawArrays(GL_POINTS, 0, planets.size());
        glUseProgram(0);
    }

    public void renderImpostorSpheres() {
        glUseProgram(impostorProgram);
        glUniform1f(uImpostorPointScaleLoc, impostorPointScale);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, BODIES_IN_SSBO_BINDING, SWAPPING_BODIES_OUT_SSBO);
        glBindVertexArray(vao);
        glDrawArrays(GL_POINTS, 0, planets.size());
        glUseProgram(0);
    }

    public void renderMeshSpheres() {
        if (sphereVao == 0 || sphereIndexCount == 0) return;
        glUseProgram(sphereProgram);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, BODIES_IN_SSBO_BINDING, SWAPPING_BODIES_OUT_SSBO);
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

    public void render() {
        if (renderMode == RenderMode.OFF) return;
        switch (renderMode) {
            case POINTS: renderPoints(); break;
            case IMPOSTOR_SPHERES: renderImpostorSpheres(); break;
            case MESH_SPHERES: renderMeshSpheres(); break;
        }
    }

    // Called by the window each frame to set camera transform
    public void setMvp(java.nio.FloatBuffer mvp4x4ColumnMajor) {
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

    // -------- Distance-based coloring configuration --------
    public void setCameraPos(float x, float y, float z) {
        this.cameraX = x;
        this.cameraY = y;
        this.cameraZ = z;
    }

    public void setDistanceRange(float nearDist, float farDist) {
        this.nearDist = nearDist;
        this.farDist = Math.max(farDist, nearDist + 0.0001f);
    }


    public void cleanupEmbedded() {
        // Minimal cleanup of GL objects created in embedded mode
        if (computeAABBKernelProgram != 0) glDeleteProgram(computeAABBKernelProgram);
        if (mortonKernelProgram != 0) glDeleteProgram(mortonKernelProgram);
        if (radixSortHistogramKernelProgram != 0) glDeleteProgram(radixSortHistogramKernelProgram);
        if (radixSortParallelScanKernelProgram != 0) glDeleteProgram(radixSortParallelScanKernelProgram);
        if (radixSortExclusiveScanKernelProgram != 0) glDeleteProgram(radixSortExclusiveScanKernelProgram);
        if (radixSortScatterKernelProgram != 0) glDeleteProgram(radixSortScatterKernelProgram);
        if (buildBinaryRadixTreeKernelProgram != 0) glDeleteProgram(buildBinaryRadixTreeKernelProgram);
        if (initLeavesKernelProgram != 0) glDeleteProgram(initLeavesKernelProgram);
        if (propagateNodesKernelProgram != 0) glDeleteProgram(propagateNodesKernelProgram);
        if (computeForceKernelProgram != 0) glDeleteProgram(computeForceKernelProgram);
        if (renderProgram != 0) glDeleteProgram(renderProgram);
        if (impostorProgram != 0) glDeleteProgram(impostorProgram);
        if (sphereProgram != 0) glDeleteProgram(sphereProgram);
        if (debugKernelProgram != 0) glDeleteProgram(debugKernelProgram);
        if (FIXED_BODIES_IN_SSBO != 0) glDeleteBuffers(FIXED_BODIES_IN_SSBO);
        if (FIXED_BODIES_OUT_SSBO != 0) glDeleteBuffers(FIXED_BODIES_OUT_SSBO);
        if (MORTON_KEYS_SSBO != 0) glDeleteBuffers(MORTON_KEYS_SSBO);
        if (INDICES_SSBO != 0) glDeleteBuffers(INDICES_SSBO);
        if (NODES_SSBO != 0) glDeleteBuffers(NODES_SSBO);
        if (AABB_SSBO != 0) glDeleteBuffers(AABB_SSBO);
        if (WG_HIST_SSBO != 0) glDeleteBuffers(WG_HIST_SSBO);
        if (WG_SCANNED_SSBO != 0) glDeleteBuffers(WG_SCANNED_SSBO);
        if (MORTON_OUT_SSBO != 0) glDeleteBuffers(MORTON_OUT_SSBO);
        if (INDEX_OUT_SSBO != 0) glDeleteBuffers(INDEX_OUT_SSBO);
        if (WORK_QUEUE_SSBO != 0) glDeleteBuffers(WORK_QUEUE_SSBO);
        if (vao != 0) glDeleteVertexArrays(vao);
        if (sphereVao != 0) glDeleteVertexArrays(sphereVao);
        if (sphereVbo != 0) glDeleteBuffers(sphereVbo);
        if (sphereIbo != 0) glDeleteBuffers(sphereIbo);
    }

    // Packs planet data to float buffer: pos(x,y,z), mass, vel(x,y,z), pad, color(r,g,b,a)
    public FloatBuffer packPlanets(List<Planet> planets) {
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

    // Assumes buffers are already correctly sized
    public void uploadPlanetsData(List<Planet> newPlanets) {
        this.planets = new ArrayList<>(newPlanets);
        FloatBuffer data = packPlanets(this.planets);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, FIXED_BODIES_IN_SSBO);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, data);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }

    // Resizes SSBOs to fit new count, then uploads
    public void resizeBuffersAndUpload(List<Planet> newPlanets) {
        this.planets = new ArrayList<>(newPlanets);
        int bytes = this.planets.size() * 12 * Float.BYTES;
        int numBodies = this.planets.size();
        int numWorkGroups = numGroups();
        int NUM_BUCKETS = 16;

        // Resize body buffers
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, FIXED_BODIES_IN_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, bytes, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, FIXED_BODIES_OUT_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, bytes, GL_DYNAMIC_COPY);
        
        // Resize Barnes-Hut auxiliary buffers
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, MORTON_KEYS_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Long.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, INDICES_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Integer.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, NODES_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, (2 * numBodies - 1) * Node.STRUCT_SIZE * Integer.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, AABB_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numWorkGroups * 2 * 4 * Float.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, WG_HIST_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numWorkGroups * NUM_BUCKETS * Integer.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, WG_SCANNED_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numWorkGroups * NUM_BUCKETS * Integer.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, GLOBAL_BASE_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, NUM_BUCKETS * Integer.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, MORTON_OUT_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Long.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, INDEX_OUT_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Integer.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, WORK_QUEUE_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, (2 + numBodies) * Integer.BYTES, GL_DYNAMIC_COPY);

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

        FloatBuffer data = packPlanets(this.planets);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, FIXED_BODIES_IN_SSBO);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, data);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

        SWAPPING_BODIES_IN_SSBO = FIXED_BODIES_IN_SSBO;
        SWAPPING_BODIES_OUT_SSBO = FIXED_BODIES_OUT_SSBO;
    }
    private int numGroups() {
        return (planets.size() + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
    }

    private void stepBarnesHut() {
        long aabbStartTime = 0;
        long mortonStartTime = 0;
        long radixSortStartTime = 0;
        long buildBinaryRadixTreeStartTime = 0;
        long computeCOMAndLocationStartTime = 0;
        long computeForceStartTime = 0;

        int numGroups = numGroups();
        if (DEBUG_BARNES_HUT) {
            aabbTime = 0;
            computeAABBTime = 0;
            collapseAABBTime = 0;
            mortonTime = 0;
            radixSortTime = 0;
            radixSortHistogramTime = 0;
            radixSortScanTime = 0;
            radixSortScatterTime = 0;
            buildTreeTime = 0;
            propagateNodesTime = 0;
            computeForceTime = 0;

        }

        //Compute AABB
        if (DEBUG_BARNES_HUT) {
            //System.out.println("0. Computing AABB...");
            aabbStartTime = System.nanoTime();
        }
        computeAABB(numGroups, DEBUG_BARNES_HUT);

        if (DEBUG_BARNES_HUT) {
            glFinish();
            long aabbEndTime = System.nanoTime();
            long aabbDuration = (aabbEndTime - aabbStartTime);
            aabbTime += aabbDuration;
            //System.out.println("AABB took " + aabbDuration + " nanoseconds");
            
        }
        //Generate Morton Codes

        if (DEBUG_BARNES_HUT) {
            //System.out.println("1. Generating Morton codes...");
            mortonStartTime = System.nanoTime();
        }

        generateMortonCodes(numGroups, DEBUG_BARNES_HUT);


        if (DEBUG_BARNES_HUT) {
            glFinish();
            long mortonEndTime = System.nanoTime();
            long mortonDuration = (mortonEndTime - mortonStartTime);
            mortonTime += mortonDuration;
            //System.out.println("Morton codes took " + mortonDuration + " nanoseconds");
        }

        //Radix Sort
        if (DEBUG_BARNES_HUT) {
            //System.out.println("2. Radix sorting...");
            radixSortStartTime = System.nanoTime();
        }

        radixSort(numGroups, DEBUG_BARNES_HUT);

        if (DEBUG_BARNES_HUT) {
            glFinish();
            long radixSortEndTime = System.nanoTime();
            long radixSortDuration = (radixSortEndTime - radixSortStartTime);
            radixSortTime += radixSortDuration;
            //System.out.println("Radix sort took " + radixSortDuration + " nanoseconds");
        }

        //Generate Binary Radix Tree
        if (DEBUG_BARNES_HUT) {
            //System.out.println("3. Building binary radix tree...");
            buildBinaryRadixTreeStartTime = System.nanoTime();
        }

        buildBinaryRadixTree(numGroups, DEBUG_BARNES_HUT);

        if (DEBUG_BARNES_HUT) {
            glFinish();
            long buildBinaryRadixTreeEndTime = System.nanoTime();
            long buildBinaryRadixTreeDuration = (buildBinaryRadixTreeEndTime - buildBinaryRadixTreeStartTime);
            buildTreeTime += buildBinaryRadixTreeDuration;
           // System.out.println("Build binary radix tree took " + buildBinaryRadixTreeDuration + " nanoseconds");
        }

        //Compute COM and Location
        if (DEBUG_BARNES_HUT) {
            //System.out.println("4. Computing center-of-mass...");
            computeCOMAndLocationStartTime = System.nanoTime();
        }

        computeCOMAndLocation(numGroups, DEBUG_BARNES_HUT);

        if (DEBUG_BARNES_HUT) {
            glFinish();
            long computeCOMAndLocationEndTime = System.nanoTime();
            long computeCOMAndLocationDuration = (computeCOMAndLocationEndTime - computeCOMAndLocationStartTime);
            propagateNodesTime += computeCOMAndLocationDuration;
            //System.out.println("Compute center-of-mass took " + computeCOMAndLocationDuration + " nanoseconds");
        }

        //Compute Force
        if (DEBUG_BARNES_HUT) {
            //System.out.println("5. Computing forces...");
            computeForceStartTime = System.nanoTime();
        }

        computeForce(numGroups, DEBUG_BARNES_HUT);

        if (DEBUG_BARNES_HUT) {
            glFinish();
            long computeForceEndTime = System.nanoTime();
            long computeForceDuration = (computeForceEndTime - computeForceStartTime);
            computeForceTime += computeForceDuration;
            //System.out.println("Compute forces took " + computeForceDuration + " nanoseconds");
        }

        // if (DEBUG_BARNES_HUT) {
        //     System.out.println("=== Barnes-Hut Complete ===\n");
        //     long endTime = System.nanoTime();
        //     long duration = (endTime - startTime);
        //     //System.out.println("Barnes-Hut took " + duration + " nanoseconds");
        // }

        if (DEBUG_BARNES_HUT) {
            glFinish();
            debugString = printProfiling();
        }
    }

    private String printProfiling() {
        long totalTime = aabbTime + mortonTime + radixSortTime + buildTreeTime + propagateNodesTime + computeForceTime;
        long percentAABB = (aabbTime * 100) / totalTime;
        long percentAABBCompute = (computeAABBTime * 100) / totalTime;
        long percentCollapseAABB = (collapseAABBTime * 100) / totalTime;
        long percentMorton = (mortonTime * 100) / totalTime;
        long percentRadixSort = (radixSortTime * 100) / totalTime;
        long percentRadixSortHistogram = (radixSortHistogramTime * 100) / totalTime;
        long percentRadixSortScan = (radixSortScanTime * 100) / totalTime;
        long percentRadixSortScatter = (radixSortScatterTime * 100) / totalTime;
        long percentBuildTree = (buildTreeTime * 100) / totalTime;
        long percentPropagateNodes = (propagateNodesTime * 100) / totalTime;
        long percentComputeForce = (computeForceTime * 100) / totalTime;

        final long oneMillion = 1_000_000;
        return aabbTime/oneMillion + " ms (" + percentAABB + "%)" +":AABB\n" + 
               "\t" + computeAABBTime/oneMillion + " ms (" + percentAABBCompute + "%)" +":Compute\n" + 
               "\t" + collapseAABBTime/oneMillion + " ms (" + percentCollapseAABB + "%)" +":Collapse\n" + 
               mortonTime/oneMillion + " ms (" + percentMorton + "%)" +":Morton\n" +
               radixSortTime/oneMillion + " ms (" + percentRadixSort + "%)" +":Radix Sort\n" +
               "\t" + radixSortHistogramTime/oneMillion + " ms (" + percentRadixSortHistogram + "%)" +":Histogram\n" +
               "\t" + radixSortScanTime/oneMillion + " ms (" + percentRadixSortScan + "%)" +":Scan\n" +
               "\t" + radixSortScatterTime/oneMillion + " ms (" + percentRadixSortScatter + "%)" +":Scatter\n" +
               buildTreeTime/oneMillion + " ms (" + percentBuildTree + "%)" +":Build Tree\n" +
               propagateNodesTime/oneMillion + " ms (" + percentPropagateNodes + "%)" +":COM\n" +
               computeForceTime/oneMillion + " ms (" + percentComputeForce + "%)" +":Force\n" +
               totalTime/oneMillion + " ms" +":Total\n";
    }

    private String getNodes(int start, int end) {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, NODES_SSBO);
        ByteBuffer buffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer nodeData = buffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        return Node.getNodes(nodeData, start, end);
    }

    private String getBodies(int start, int end) {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, SWAPPING_BODIES_IN_SSBO);
        ByteBuffer buffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer bodyData = buffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

        return Body.getBodies(bodyData, start, end);
        
        




    }

    private float[][] oldComputeAABB() {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, SWAPPING_BODIES_IN_SSBO);
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
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        
        float[][] raabb = new float[][] {
            {minX, minY, minZ},
            {maxX, maxY, maxZ}
        };
        return raabb;
    }

    private void computeAABB(int numGroupsBodies, boolean debug) {
        long aabbComputeStartTime = 0;
        long collapseAABBStartTime = 0;

        if (debug) {
             aabbComputeStartTime = System.nanoTime();
        }


        // Pass 1: bodies -> aabbOut (one AABB per workgroup)
        glUseProgram(computeAABBKernelProgram);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, BODIES_IN_SSBO_BINDING, SWAPPING_BODIES_IN_SSBO);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, AABB_SSBO_BINDING, AABB_SSBO);
        glUniform1ui(glGetUniformLocation(computeAABBKernelProgram, "numBodies"), planets.size());
        glUniform1ui(glGetUniformLocation(computeAABBKernelProgram, "numWorkGroups"), numGroups());
        glDispatchCompute(numGroups(), 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

        if (debug) {
            glFinish(); 
            computeAABBTime += System.nanoTime() - aabbComputeStartTime;
             collapseAABBStartTime = System.nanoTime();
        }

 
        glUseProgram(collapseAABBKernelProgram);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, AABB_SSBO_BINDING, AABB_SSBO);
        glUniform1ui(glGetUniformLocation(collapseAABBKernelProgram, "numWorkGroups"), numGroups());
        glDispatchCompute(1, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

        if (debug) {
            glFinish();
            collapseAABBTime += System.nanoTime() - collapseAABBStartTime;
        }

    }

    private void debugAABB() {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, AABB_SSBO);
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
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        
        
    }

    private void generateMortonCodes(int numGroups, boolean debug) {
        glUseProgram(mortonKernelProgram);
        
        // Bind bodies (input) and morton/index (output)
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, BODIES_IN_SSBO_BINDING, SWAPPING_BODIES_IN_SSBO);      // bodies input
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, MORTON_KEYS_SSBO_BINDING, MORTON_KEYS_SSBO);   // morton keys output
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, INDICES_SSBO_BINDING, INDICES_SSBO);    // indices output
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, AABB_SSBO_BINDING, AABB_SSBO);   // AABB input (from computeAABB)
        
        // Set uniforms
        glUniform1ui(glGetUniformLocation(mortonKernelProgram, "numBodies"), planets.size());
        
        glDispatchCompute(numGroups, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

    }

    private void debugMortonCodes() {
        
            // Read morton codes from GPU buffer
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, MORTON_KEYS_SSBO);
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
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        }
    private void debugRadixSort() {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, MORTON_KEYS_SSBO);
        ByteBuffer mortonBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer mortonData = mortonBuffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, INDICES_SSBO);
        ByteBuffer indexBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer indexData = indexBuffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, WG_HIST_SSBO);
        ByteBuffer wgHistBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer wgHistData = wgHistBuffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, WG_SCANNED_SSBO);
        ByteBuffer wgScannedBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer wgScannedData = wgScannedBuffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, GLOBAL_BASE_SSBO);
        ByteBuffer globalBaseBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer globalBaseData = globalBaseBuffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, BUCKET_TOTALS_SSBO);
        ByteBuffer bucketTotalsBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer bucketTotalsData = bucketTotalsBuffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, MORTON_OUT_SSBO);
        ByteBuffer mortonOutBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        IntBuffer mortonOutData = mortonOutBuffer.asIntBuffer();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, INDEX_OUT_SSBO);
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

    private void radixSort(int numGroups, boolean debug) {



        int numPasses = (int)Math.ceil(63.0 / 4.0); // 16 passes for 63-bit Morton codes
        
        // Current input/output morton and index buffers
        int currentMortonIn = MORTON_KEYS_SSBO;
        int currentIndexIn = INDICES_SSBO;
        int currentMortonOut = MORTON_OUT_SSBO;
        int currentIndexOut = INDEX_OUT_SSBO;

        radixSortHistogramTime = 0;
        radixSortScanTime = 0;
        radixSortScatterTime = 0;

        long radixSortHistogramStartTime = 0;
        long radixSortScanStartTime = 0;
        long radixSortScatterStartTime = 0;
        
        for (int pass = 0; pass < numPasses; pass++) {
            //System.out.println("Radix sort pass " + pass);
            //debugRadixSort();
            
            int passShift = pass * 4; // 4 bits per pass
            //if (DEBUG_BARNES_HUT) System.out.println("Radix sort pass " + pass + " with shift " + passShift);
            radixSortHistogramStartTime = System.nanoTime();

            // Phase 1: Histogram
            glUseProgram(radixSortHistogramKernelProgram);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, MORTON_KEYS_SSBO_BINDING, currentMortonIn);    // morton input
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, INDICES_SSBO_BINDING, currentIndexIn);     // index input
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, WG_HIST_SSBO_BINDING, WG_HIST_SSBO);         // workgroup histograms
            
            glUniform1ui(glGetUniformLocation(radixSortHistogramKernelProgram, "numBodies"), planets.size());
            glUniform1ui(glGetUniformLocation(radixSortHistogramKernelProgram, "passShift"), passShift);
            glUniform1ui(glGetUniformLocation(radixSortHistogramKernelProgram, "numWorkGroups"), numGroups);
            
            glDispatchCompute(numGroups, 1, 1);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

            

            if (debug) {
                glFinish();
                radixSortHistogramTime += System.nanoTime() - radixSortHistogramStartTime;
                radixSortScanStartTime = System.nanoTime();
            }

            

            //System.out.println("Radix sort histogram");
            //debugRadixSort();


            // Phase 2: Scan
            glUseProgram(radixSortParallelScanKernelProgram);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, WG_HIST_SSBO_BINDING, WG_HIST_SSBO);         // workgroup histograms input
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, WG_SCANNED_SSBO_BINDING, WG_SCANNED_SSBO);      // scanned per-wg bases output
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, GLOBAL_BASE_SSBO_BINDING, GLOBAL_BASE_SSBO);     // global bucket bases output
            
            glUniform1ui(glGetUniformLocation(radixSortParallelScanKernelProgram, "numBodies"), planets.size());
            glUniform1ui(glGetUniformLocation(radixSortParallelScanKernelProgram, "numWorkGroups"), numGroups);
            
            glDispatchCompute(NUM_RADIX_BUCKETS, 1, 1);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

            //System.out.println("Radix sort parallel scan");
           // debugRadixSort();

            glUseProgram(radixSortExclusiveScanKernelProgram);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, BUCKET_TOTALS_SSBO_BINDING, BUCKET_TOTALS_SSBO);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, GLOBAL_BASE_SSBO_BINDING, GLOBAL_BASE_SSBO);

            glUniform1ui(glGetUniformLocation(radixSortExclusiveScanKernelProgram, "numBodies"), planets.size());
            glUniform1ui(glGetUniformLocation(radixSortExclusiveScanKernelProgram, "numWorkGroups"), numGroups);

            glDispatchCompute(1, 1, 1);

            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

            if (debug) {
                glFinish();
                radixSortScanTime += System.nanoTime() - radixSortScanStartTime;
                radixSortScatterStartTime = System.nanoTime();
            }

            //System.out.println("Radix sort exclusive scan");
            //debugRadixSort();


            // Phase 3: Scatter
            glUseProgram(radixSortScatterKernelProgram);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, MORTON_KEYS_SSBO_BINDING, currentMortonIn);    // morton input
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, INDICES_SSBO_BINDING, currentIndexIn);     // index input
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, WG_SCANNED_SSBO_BINDING, WG_SCANNED_SSBO);      // scanned bases
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, GLOBAL_BASE_SSBO_BINDING, GLOBAL_BASE_SSBO);     // global bases
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, MORTON_OUT_SSBO_BINDING, currentMortonOut);   // morton output
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, INDEX_OUT_SSBO_BINDING, currentIndexOut);    // index output
            
            glUniform1ui(glGetUniformLocation(radixSortScatterKernelProgram, "numBodies"), planets.size());
            glUniform1ui(glGetUniformLocation(radixSortScatterKernelProgram, "passShift"), passShift);
            glUniform1ui(glGetUniformLocation(radixSortScatterKernelProgram, "numWorkGroups"), numGroups);
            
            glDispatchCompute(numGroups, 1, 1);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

            if (debug) {
                glFinish();
                radixSortScatterTime += System.nanoTime() - radixSortScatterStartTime;
            }
            
            //System.out.println("Radix sort scatter");
            //debugRadixSort();

            // Swap input/output buffers for next pass
            int tempMorton = currentMortonIn;
            int tempIndex = currentIndexIn;
            currentMortonIn = currentMortonOut;
            currentIndexIn = currentIndexOut;
            currentMortonOut = tempMorton;
            currentIndexOut = tempIndex;
        }



        // Debug: Output buffer data
          //  debugSorting();
        
        // After final pass, sorted data is in currentMortonIn/currentIndexIn
        // Update our "current" buffers to point to the final sorted data
        MORTON_KEYS_SSBO = currentMortonIn;
        INDICES_SSBO = currentIndexIn;

    }

    private void buildBinaryRadixTree(int numGroups, boolean debug) {
        glUseProgram(buildBinaryRadixTreeKernelProgram);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, MORTON_KEYS_SSBO_BINDING, MORTON_KEYS_SSBO);   // morton keys (sorted)
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, INDICES_SSBO_BINDING, INDICES_SSBO);    // body indices (sorted)
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, NODES_SSBO_BINDING, NODES_SSBO);    // tree nodes output
        
        glUniform1ui(glGetUniformLocation(buildBinaryRadixTreeKernelProgram, "numBodies"), planets.size());
        
        // Dispatch numBodies-1 threads (one per internal node)
        int numInternalNodes = planets.size() - 1;
        int internalNodeGroups = (numInternalNodes + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
        
        
        glDispatchCompute(internalNodeGroups, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);


    }
    
    private void computeCOMAndLocation(int numGroups, boolean debug) {
        glUseProgram(initLeavesKernelProgram);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, BODIES_IN_SSBO_BINDING, SWAPPING_BODIES_IN_SSBO);      // bodies input  
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, MORTON_KEYS_SSBO_BINDING, MORTON_KEYS_SSBO);   // morton keys (for reference)
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, INDICES_SSBO_BINDING, INDICES_SSBO);    // sorted body indices
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, NODES_SSBO_BINDING, NODES_SSBO);    // nodes to initialize
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, WORK_QUEUE_SSBO_BINDING, WORK_QUEUE_SSBO); // work queue buffer
        
        // Reset work queue head/tail to 0 before init
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, WORK_QUEUE_SSBO);
        ByteBuffer initQ = BufferUtils.createByteBuffer(2 * Integer.BYTES);
        initQ.putInt(0).putInt(0).flip();
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, initQ);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

        glUniform1ui(glGetUniformLocation(initLeavesKernelProgram, "numBodies"), planets.size());
        glDispatchCompute(numGroups, 1, 1); // One thread per body (leaf node)
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

        //debugTree();


        for (int i = 0; i < PROPAGATE_NODES_ITERATIONS; i++) {
            glUseProgram(propagateNodesKernelProgram);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, NODES_SSBO_BINDING, NODES_SSBO);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, WORK_QUEUE_SSBO_BINDING, WORK_QUEUE_SSBO);
            
            // Dispatch with enough threads to process all potential work items efficiently
            // Each thread will process multiple items in round-robin fashion
            int maxPossibleNodes = planets.size() - 1; // Internal nodes
            int workGroups = (maxPossibleNodes + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
            glDispatchCompute(workGroups, 1, 1);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        }
    }

    private void computeForce(int numGroups, boolean debug) {
        checkGLError("Before compute force");
        glUseProgram(computeForceKernelProgram);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, BODIES_IN_SSBO_BINDING, SWAPPING_BODIES_IN_SSBO);
        checkGLError("Bodies in SSBO bind");      // bodies input
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, BODIES_OUT_SSBO_BINDING, SWAPPING_BODIES_OUT_SSBO);
        checkGLError("Bodies out SSBO bind");      // bodies output
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, NODES_SSBO_BINDING, NODES_SSBO);
        checkGLError("Nodes SSBO bind");    // tree nodes
        
        
        // Set uniforms
        float theta = (float)Settings.getInstance().getTheta();
        float dt = (float)Settings.getInstance().getDt();
        glUniform1ui(glGetUniformLocation(computeForceKernelProgram, "numBodies"), planets.size());
        glUniform1f(glGetUniformLocation(computeForceKernelProgram, "theta"), theta);
        glUniform1f(glGetUniformLocation(computeForceKernelProgram, "dt"), dt);
        glUniform1f(glGetUniformLocation(computeForceKernelProgram, "elasticity"), (float)Settings.getInstance().getElasticity());
        glUniform1f(glGetUniformLocation(computeForceKernelProgram, "density"), (float)Settings.getInstance().getDensity());

        glDispatchCompute(numGroups, 1, 1); // One thread per body

        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

        
        // Swap source and destination buffers for next iteration
        int tmp = SWAPPING_BODIES_IN_SSBO;
        SWAPPING_BODIES_IN_SSBO = SWAPPING_BODIES_OUT_SSBO;
        SWAPPING_BODIES_OUT_SSBO = tmp;
    }
    



    private void checkShader(int shader) {
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("Shader compile error: " + glGetShaderInfoLog(shader));
        }
    }

    private void checkProgram(int program) {
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Program link error: " + glGetProgramInfoLog(program));
        }
    }
    
    private void checkGLError(String operation) {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            System.err.println("OpenGL Error after " + operation + ": " + error);
        }
    }

    // ----------------- SHADERS -----------------

    private String getComputeShaderSource() {
        try {
            return Files.readString(Paths.get("src/main/resources/shaders/compute/barneshut.glsl"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read Barnes-Hut compute shader: " + e.getMessage());
        }
    }

    private String insertDefineAfterVersion(String shaderSource, String defineValue) {
        // Find the first newline after #version
        int versionEnd = shaderSource.indexOf('\n');
        if (versionEnd == -1) {
            // No newline found, just append
            return shaderSource + "\n#define " + defineValue + "\n";
        }
        
        // Insert the define after the version line
        return shaderSource.substring(0, versionEnd + 1) + 
               "#define " + defineValue + "\n" + 
               shaderSource.substring(versionEnd + 1);
    }

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

    // --------- Sphere mesh generation ---------
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

    private void debugSorting() {
        System.out.println("=== RADIX SORT RESULTS ===");
            // Read sorted Morton codes
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, MORTON_KEYS_SSBO);
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
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, INDEX_OUT_SSBO);
            ByteBuffer indexBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            IntBuffer indexData = indexBuffer.asIntBuffer();
            System.out.println("Sorted body indices:");
            for (int i = 0; i < Math.min(100, planets.size()); i++) {
                int bodyIndex = indexData.get(i);
                System.out.printf("  [%d]: %d\n", i, bodyIndex);
            }
            glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

    }

    private void debugTree() {
        
        // Debug: Output buffer data
            System.out.println("=== FINAL TREE WITH COM DATA ===");
            
            // First check for stuck nodes with readyChildren < 2
            System.out.println("=== STUCK NODE ANALYSIS ===");
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, NODES_SSBO);
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
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, NODES_SSBO);
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

    
    /**
     * Verifies the integrity of the Barnes-Hut tree structure
     * Checks parent-child relationships, detects cycles, orphans, and structural issues
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
