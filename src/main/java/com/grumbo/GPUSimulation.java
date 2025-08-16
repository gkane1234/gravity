package com.grumbo;

import org.lwjgl.*;

import java.nio.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;

import static org.lwjgl.opengl.GL43C.*;


public class GPUSimulation {
    private static final int NODE_STRUCT_SIZE=16;  // GPU adds 2 padding uints for alignment   
    private int vao; // points VAO

    private long lastFrameFence = 0L;

    // Simulation params
    private static final int WORK_GROUP_SIZE = 128;
    private static final boolean DEBUG_BARNES_HUT = true; // Set to false to disable debug output
    private static final int DEBUG_FRAME_INTERVAL = 6000; // Show debug every N frames
    private int frameCounter = 0;

    private int computeAABBKernelProgram;
    private int collapseAABBKernelProgram;
    private int mortonKernelProgram;
    private int radixSortHistogramKernelProgram;
    private int radixSortScanKernelProgram;
    private int radixSortScatterKernelProgram;
    private int buildBinaryRadixTreeKernelProgram;
    private int initLeavesKernelProgram;
    private int propagateNodesKernelProgram;
    private int computeForceKernelProgram;
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
    private int AABB_IN_SSBO;
    private final int AABB_IN_SSBO_BINDING = 5;
    private int AABB_OUT_SSBO;
    private final int AABB_OUT_SSBO_BINDING = 6;
    private int WG_HIST_SSBO;
    private final int WG_HIST_SSBO_BINDING = 7;
    private int WG_SCANNED_SSBO;
    private final int WG_SCANNED_SSBO_BINDING = 8;
    private int GLOBAL_BASE_SSBO;
    private final int GLOBAL_BASE_SSBO_BINDING = 9;
    private int MORTON_OUT_SSBO;
    private final int MORTON_OUT_SSBO_BINDING = 10;
    private int INDEX_OUT_SSBO;
    private final int INDEX_OUT_SSBO_BINDING = 11;
    private int ROOT_NODE_SSBO;
    private final int ROOT_NODE_SSBO_BINDING = 12;
    private int WORK_QUEUE_SSBO;
    private final int WORK_QUEUE_SSBO_BINDING = 13;



    private int uMvpLocation;           // for points
    private int uMvpLocationImpostor;   // for impostors
    private int uMvpLocationSphere;     // for mesh spheres
    private int uSphereRadiusScaleLoc;  // mass->radius scale
    private int uCameraPosLocSphere;
    private int uNearDistLocSphere;
    private int uFarDistLocSphere;
    private int uImpostorPointScaleLoc; // impostor scale



    private ArrayList<Planet> planets;

    public enum RenderMode {
        OFF,
        POINTS,
        IMPOSTOR_SPHERES,
        MESH_SPHERES
    }

    private float[][] aabb;
    // GPU timer queries (non-blocking)
    private int aabbFirstPassQuery = 0;
    private int aabbCollapseQuery = 0;



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

    private float sphereRadiusScale = 0.3f; // radius = sqrt(mass) * scale

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

    public void initWithCurrentContext() {
        // Create and bind a dummy VAO (required for core profile draws)
        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        // Create compute shaders

        //Compute AABB Kernel

        computeAABBKernelProgram = glCreateProgram();
        int computeAABBKernelShader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(computeAABBKernelShader, insertDefineAfterVersion(getComputeShaderSource(), "KERNEL_COMPUTE_AABB"));
        glCompileShader(computeAABBKernelShader);
        checkShader(computeAABBKernelShader);
        glAttachShader(computeAABBKernelProgram, computeAABBKernelShader);
        glLinkProgram(computeAABBKernelProgram);
        checkProgram(computeAABBKernelProgram);

        //Morton Kernel
        mortonKernelProgram = glCreateProgram();
        int mortonKernelShader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(mortonKernelShader, insertDefineAfterVersion(getComputeShaderSource(), "KERNEL_MORTON"));
        glCompileShader(mortonKernelShader);
        checkShader(mortonKernelShader);
        glAttachShader(mortonKernelProgram, mortonKernelShader);
        glLinkProgram(mortonKernelProgram);
        checkProgram(mortonKernelProgram);

        //Radix Sort Histogram Kernel
        radixSortHistogramKernelProgram = glCreateProgram();
        int radixSortHistogramKernelShader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(radixSortHistogramKernelShader, insertDefineAfterVersion(getComputeShaderSource(), "KERNEL_RADIX_HIST"));
        glCompileShader(radixSortHistogramKernelShader);
        checkShader(radixSortHistogramKernelShader);
        glAttachShader(radixSortHistogramKernelProgram, radixSortHistogramKernelShader);
        glLinkProgram(radixSortHistogramKernelProgram);
        checkProgram(radixSortHistogramKernelProgram);

        //Radix Sort Scan Kernel
        radixSortScanKernelProgram = glCreateProgram();
        int radixSortScanKernelShader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(radixSortScanKernelShader, insertDefineAfterVersion(getComputeShaderSource(), "KERNEL_RADIX_SCAN"));
        glCompileShader(radixSortScanKernelShader);
        checkShader(radixSortScanKernelShader);
        glAttachShader(radixSortScanKernelProgram, radixSortScanKernelShader);
        glLinkProgram(radixSortScanKernelProgram);
        checkProgram(radixSortScanKernelProgram);

        //Radix Sort Scatter Kernel
        radixSortScatterKernelProgram = glCreateProgram();
        int radixSortScatterKernelShader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(radixSortScatterKernelShader, insertDefineAfterVersion(getComputeShaderSource(), "KERNEL_RADIX_SCATTER"));
        glCompileShader(radixSortScatterKernelShader);
        checkShader(radixSortScatterKernelShader);
        glAttachShader(radixSortScatterKernelProgram, radixSortScatterKernelShader);
        glLinkProgram(radixSortScatterKernelProgram);
        checkProgram(radixSortScatterKernelProgram);

        //Build Binary Radix Tree Kernel
        buildBinaryRadixTreeKernelProgram = glCreateProgram();
        int buildBinaryRadixTreeKernelShader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(buildBinaryRadixTreeKernelShader, insertDefineAfterVersion(getComputeShaderSource(), "KERNEL_BUILD_BINARY_RADIX_TREE"));
        glCompileShader(buildBinaryRadixTreeKernelShader);
        checkShader(buildBinaryRadixTreeKernelShader);
        glAttachShader(buildBinaryRadixTreeKernelProgram, buildBinaryRadixTreeKernelShader);
        glLinkProgram(buildBinaryRadixTreeKernelProgram);
        checkProgram(buildBinaryRadixTreeKernelProgram);

        //Init Leaves Kernel
        initLeavesKernelProgram = glCreateProgram();
        int initLeavesKernelShader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(initLeavesKernelShader, insertDefineAfterVersion(getComputeShaderSource(), "KERNEL_INIT_LEAVES"));
        glCompileShader(initLeavesKernelShader);
        checkShader(initLeavesKernelShader);
        glAttachShader(initLeavesKernelProgram, initLeavesKernelShader);
        glLinkProgram(initLeavesKernelProgram);
        checkProgram(initLeavesKernelProgram);

        //Propagate Nodes Kernel
        propagateNodesKernelProgram = glCreateProgram();
        int propagateNodesKernelShader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(propagateNodesKernelShader, insertDefineAfterVersion(getComputeShaderSource(), "KERNEL_PROPAGATE_NODES"));
        glCompileShader(propagateNodesKernelShader);
        checkShader(propagateNodesKernelShader);
        glAttachShader(propagateNodesKernelProgram, propagateNodesKernelShader);
        glLinkProgram(propagateNodesKernelProgram);
        checkProgram(propagateNodesKernelProgram);

        //Collapse AABB Kernel
        collapseAABBKernelProgram = glCreateProgram();
        int collapseAABBKernelShader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(collapseAABBKernelShader, insertDefineAfterVersion(getComputeShaderSource(), "KERNEL_COLLAPSE_AABB"));
        glCompileShader(collapseAABBKernelShader);
        checkShader(collapseAABBKernelShader);
        glAttachShader(collapseAABBKernelProgram, collapseAABBKernelShader);
        glLinkProgram(collapseAABBKernelProgram);
        checkProgram(collapseAABBKernelProgram);

        //Compute Force Kernel
        computeForceKernelProgram = glCreateProgram();
        int computeForceKernelShader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(computeForceKernelShader, insertDefineAfterVersion(getComputeShaderSource(), "KERNEL_COMPUTE_FORCE"));
        glCompileShader(computeForceKernelShader);
        checkShader(computeForceKernelShader);
        glAttachShader(computeForceKernelProgram, computeForceKernelShader);
        glLinkProgram(computeForceKernelProgram);
        checkProgram(computeForceKernelProgram);

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
        int aabbGroups = (numBodies + (2 * WORK_GROUP_SIZE) - 1) / (2 * WORK_GROUP_SIZE);
        int NUM_BUCKETS = 16; // 2^RADIX_BITS where RADIX_BITS=4
        
        MORTON_KEYS_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, MORTON_KEYS_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Integer.BYTES, GL_DYNAMIC_COPY);
        
        INDICES_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, INDICES_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Integer.BYTES, GL_DYNAMIC_COPY);
        
        NODES_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, NODES_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, (2 * numBodies - 1) * NODE_STRUCT_SIZE * Integer.BYTES, GL_DYNAMIC_COPY); // 16 uints per node (2 vec4s + 6 uints + 2 padding)

        // AABB ping-pong buffers (vec3 pairs per workgroup)
        AABB_IN_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, AABB_IN_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, aabbGroups * 2 * 3 * Float.BYTES, GL_DYNAMIC_COPY);
        AABB_OUT_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, AABB_OUT_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, aabbGroups * 2 * 3 * Float.BYTES, GL_DYNAMIC_COPY);
        
        WG_HIST_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, WG_HIST_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numWorkGroups * NUM_BUCKETS * Integer.BYTES, GL_DYNAMIC_COPY);
        
        WG_SCANNED_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, WG_SCANNED_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numWorkGroups * NUM_BUCKETS * Integer.BYTES, GL_DYNAMIC_COPY);
        
        GLOBAL_BASE_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, GLOBAL_BASE_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, NUM_BUCKETS * Integer.BYTES, GL_DYNAMIC_COPY);
        
        MORTON_OUT_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, MORTON_OUT_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Integer.BYTES, GL_DYNAMIC_COPY);
        
        INDEX_OUT_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, INDEX_OUT_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Integer.BYTES, GL_DYNAMIC_COPY);
        
        ROOT_NODE_SSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ROOT_NODE_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, Integer.BYTES, GL_DYNAMIC_COPY); // Single uint
        
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

    public void step() {
        processCommands();
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
        if (radixSortScanKernelProgram != 0) glDeleteProgram(radixSortScanKernelProgram);
        if (radixSortScatterKernelProgram != 0) glDeleteProgram(radixSortScatterKernelProgram);
        if (buildBinaryRadixTreeKernelProgram != 0) glDeleteProgram(buildBinaryRadixTreeKernelProgram);
        if (initLeavesKernelProgram != 0) glDeleteProgram(initLeavesKernelProgram);
        if (propagateNodesKernelProgram != 0) glDeleteProgram(propagateNodesKernelProgram);
        if (computeForceKernelProgram != 0) glDeleteProgram(computeForceKernelProgram);
        if (renderProgram != 0) glDeleteProgram(renderProgram);
        if (impostorProgram != 0) glDeleteProgram(impostorProgram);
        if (sphereProgram != 0) glDeleteProgram(sphereProgram);
        if (FIXED_BODIES_IN_SSBO != 0) glDeleteBuffers(FIXED_BODIES_IN_SSBO);
        if (FIXED_BODIES_OUT_SSBO != 0) glDeleteBuffers(FIXED_BODIES_OUT_SSBO);
        if (MORTON_KEYS_SSBO != 0) glDeleteBuffers(MORTON_KEYS_SSBO);
        if (INDICES_SSBO != 0) glDeleteBuffers(INDICES_SSBO);
        if (NODES_SSBO != 0) glDeleteBuffers(NODES_SSBO);
        if (AABB_IN_SSBO != 0) glDeleteBuffers(AABB_IN_SSBO);
        if (AABB_OUT_SSBO != 0) glDeleteBuffers(AABB_OUT_SSBO);
        if (WG_HIST_SSBO != 0) glDeleteBuffers(WG_HIST_SSBO);
        if (WG_SCANNED_SSBO != 0) glDeleteBuffers(WG_SCANNED_SSBO);
        if (MORTON_OUT_SSBO != 0) glDeleteBuffers(MORTON_OUT_SSBO);
        if (INDEX_OUT_SSBO != 0) glDeleteBuffers(INDEX_OUT_SSBO);
        if (ROOT_NODE_SSBO != 0) glDeleteBuffers(ROOT_NODE_SSBO);
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
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Integer.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, INDICES_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Integer.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, NODES_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, (2 * numBodies - 1) * NODE_STRUCT_SIZE * Integer.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, AABB_IN_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numWorkGroups * 2 * 3 * Float.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, AABB_OUT_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numWorkGroups * 2 * 3 * Float.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, WG_HIST_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numWorkGroups * NUM_BUCKETS * Integer.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, WG_SCANNED_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numWorkGroups * NUM_BUCKETS * Integer.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, GLOBAL_BASE_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, NUM_BUCKETS * Integer.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, MORTON_OUT_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Integer.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, INDEX_OUT_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Integer.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ROOT_NODE_SSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, Integer.BYTES, GL_DYNAMIC_COPY);
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
        int numGroups = numGroups();
        if (DEBUG_BARNES_HUT) {
            System.out.println("=== Barnes-Hut Step (Frame " + frameCounter + ") ===");
            System.out.println("Bodies: " + planets.size() + ", WorkGroups: " + numGroups);
            System.out.println("DEBUG: planets list size = " + planets.size());
        }
        //Profiling 
        long startTime = System.nanoTime();
        //Compute AABB
        if (DEBUG_BARNES_HUT) System.out.println("0. Computing AABB...");
        long aabbStartTime = System.nanoTime();

        computeAABB(numGroups, DEBUG_BARNES_HUT);
        glFinish();
        long aabbEndTime = System.nanoTime();
        long aabbDuration = (aabbEndTime - aabbStartTime);
        if (DEBUG_BARNES_HUT) System.out.println("AABB took " + aabbDuration + " nanoseconds");
        
        //Generate Morton Codes
        if (DEBUG_BARNES_HUT) System.out.println("1. Generating Morton codes...");
        long mortonStartTime = System.nanoTime();

        generateMortonCodes(numGroups, DEBUG_BARNES_HUT);
        glFinish();
        long mortonEndTime = System.nanoTime();
        long mortonDuration = (mortonEndTime - mortonStartTime);
        if (DEBUG_BARNES_HUT) System.out.println("Morton codes took " + mortonDuration + " nanoseconds");

        //Radix Sort
        if (DEBUG_BARNES_HUT) System.out.println("2. Radix sorting...");
        long radixSortStartTime = System.nanoTime();

        radixSort(numGroups, DEBUG_BARNES_HUT);
        glFinish();
        long radixSortEndTime = System.nanoTime();
        long radixSortDuration = (radixSortEndTime - radixSortStartTime);
        if (DEBUG_BARNES_HUT) System.out.println("Radix sort took " + radixSortDuration + " nanoseconds");
        
        //Generate Binary Radix Tree
        if (DEBUG_BARNES_HUT) System.out.println("3. Building binary radix tree...");
        long buildBinaryRadixTreeStartTime = System.nanoTime();

        buildBinaryRadixTree(numGroups, DEBUG_BARNES_HUT);
        glFinish();
        long buildBinaryRadixTreeEndTime = System.nanoTime();
        long buildBinaryRadixTreeDuration = (buildBinaryRadixTreeEndTime - buildBinaryRadixTreeStartTime);
        if (DEBUG_BARNES_HUT) System.out.println("Build binary radix tree took " + buildBinaryRadixTreeDuration + " nanoseconds");

        //Compute COM and Location
        if (DEBUG_BARNES_HUT) System.out.println("4. Computing center-of-mass...");
        long computeCOMAndLocationStartTime = System.nanoTime();

        computeCOMAndLocation(numGroups, DEBUG_BARNES_HUT);
        glFinish();
        long computeCOMAndLocationEndTime = System.nanoTime();
        long computeCOMAndLocationDuration = (computeCOMAndLocationEndTime - computeCOMAndLocationStartTime);
        if (DEBUG_BARNES_HUT) System.out.println("Compute center-of-mass took " + computeCOMAndLocationDuration + " nanoseconds");

        //Compute Force
        if (DEBUG_BARNES_HUT) System.out.println("5. Computing forces...");
        long computeForceStartTime = System.nanoTime();

        computeForce(numGroups, DEBUG_BARNES_HUT);
        glFinish();
        long computeForceEndTime = System.nanoTime();
        long computeForceDuration = (computeForceEndTime - computeForceStartTime);
        if (DEBUG_BARNES_HUT) System.out.println("Compute forces took " + computeForceDuration + " nanoseconds");
        long clearQueueStartTime = System.nanoTime();
        if (DEBUG_BARNES_HUT) {
            System.out.println("=== Barnes-Hut Step (Frame " + frameCounter + ") ===");
        }
        glFinish();
        if (DEBUG_BARNES_HUT) {
            System.out.println("Clear queue took " + (System.nanoTime() - clearQueueStartTime) + " nanoseconds");
        }
        if (DEBUG_BARNES_HUT) System.out.println("=== Barnes-Hut Complete ===\n");
        long endTime = System.nanoTime();
        long duration = (endTime - startTime);
        System.out.println("Barnes-Hut took " + duration + " nanoseconds");

        lastFrameFence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);

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



        // Pass 1: bodies -> aabbOut (one AABB per workgroup)
        glUseProgram(computeAABBKernelProgram);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, BODIES_IN_SSBO_BINDING, SWAPPING_BODIES_IN_SSBO);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, AABB_OUT_SSBO_BINDING, AABB_OUT_SSBO);
        int groups = (planets.size() + (2 * WORK_GROUP_SIZE) - 1) / (2 * WORK_GROUP_SIZE);
        glUniform1ui(glGetUniformLocation(computeAABBKernelProgram, "numBodies"), planets.size());
        glUniform1ui(glGetUniformLocation(computeAABBKernelProgram, "numWorkGroups"), groups);
        if (aabbFirstPassQuery == 0) aabbFirstPassQuery = glGenQueries();
        glBeginQuery(GL_TIME_ELAPSED, aabbFirstPassQuery);
        glDispatchCompute(groups, 1, 1);
        glEndQuery(GL_TIME_ELAPSED);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

        // Iterative collapse: ping-pong aabbIn/aabbOut until 1 AABB remains
        boolean ping = false; // false: in=OUT, out=IN; true: in=IN, out=OUT
        int currentCount = groups; // number of AABBs (pairs of vec3)
        int lastOutBuf = AABB_OUT_SSBO; // first pass wrote to OUT


        while (currentCount > 1) {
            glUseProgram(collapseAABBKernelProgram);
            // Bind input/output according to ping
            int inBuf = ping ? AABB_IN_SSBO : AABB_OUT_SSBO;  // toggle input between IN/OUT
            int outBuf = ping ? AABB_OUT_SSBO : AABB_IN_SSBO; // toggle output between OUT/IN
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, AABB_IN_SSBO_BINDING, inBuf);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, AABB_OUT_SSBO_BINDING, outBuf);

            int nextGroups = (currentCount + (2 * WORK_GROUP_SIZE) - 1) / (2 * WORK_GROUP_SIZE);
            glUniform1ui(glGetUniformLocation(collapseAABBKernelProgram, "numBodies"), currentCount);
            glUniform1ui(glGetUniformLocation(collapseAABBKernelProgram, "numWorkGroups"), nextGroups);
            glDispatchCompute(nextGroups, 1, 1);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

            currentCount = nextGroups;
            lastOutBuf = outBuf;
            ping = !ping;
        }
        AABB_OUT_SSBO = lastOutBuf == AABB_OUT_SSBO ? AABB_IN_SSBO : AABB_OUT_SSBO;
        AABB_IN_SSBO = lastOutBuf;
    }

    private void generateMortonCodes(int numGroups, boolean debug) {
        glUseProgram(mortonKernelProgram);
        
        // Bind bodies (input) and morton/index (output)
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, BODIES_IN_SSBO_BINDING, SWAPPING_BODIES_IN_SSBO);      // bodies input
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, BODIES_OUT_SSBO_BINDING, SWAPPING_BODIES_OUT_SSBO);      // bodies output (unused)
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, MORTON_KEYS_SSBO_BINDING, MORTON_KEYS_SSBO);   // morton keys output
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, INDICES_SSBO_BINDING, INDICES_SSBO);    // indices output
        
        // Set uniforms
        glUniform1ui(glGetUniformLocation(mortonKernelProgram, "numBodies"), planets.size());
        
        glDispatchCompute(numGroups, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
    }

  

    private void radixSort(int numGroups, boolean debug) {
        int numPasses = (int)Math.ceil(63.0 / 4.0); // 16 passes for 63-bit Morton codes
        
        // Current input/output morton and index buffers
        int currentMortonIn = MORTON_KEYS_SSBO;
        int currentIndexIn = INDICES_SSBO;
        int currentMortonOut = MORTON_OUT_SSBO;
        int currentIndexOut = INDEX_OUT_SSBO;

        for (int pass = 0; pass < numPasses; pass++) {
            int passShift = pass * 4; // 4 bits per pass
            
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
            
            // Phase 2: Scan
            glUseProgram(radixSortScanKernelProgram);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, WG_HIST_SSBO_BINDING, WG_HIST_SSBO);         // workgroup histograms input
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, WG_SCANNED_SSBO_BINDING, WG_SCANNED_SSBO);      // scanned per-wg bases output
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, GLOBAL_BASE_SSBO_BINDING, GLOBAL_BASE_SSBO);     // global bucket bases output
            
            glUniform1ui(glGetUniformLocation(radixSortScanKernelProgram, "numBodies"), planets.size());
            glUniform1ui(glGetUniformLocation(radixSortScanKernelProgram, "numWorkGroups"), numGroups);
            
            glDispatchCompute(1, 1, 1); // Single invocation
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
            
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
            
            // Swap input/output buffers for next pass
            int tempMorton = currentMortonIn;
            int tempIndex = currentIndexIn;
            currentMortonIn = currentMortonOut;
            currentIndexIn = currentIndexOut;
            currentMortonOut = tempMorton;
            currentIndexOut = tempIndex;
        }
        
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
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ROOT_NODE_SSBO_BINDING, ROOT_NODE_SSBO); // root node ID output
        
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

        // Dispatch persistent kernel that processes entire queue without CPU intervention
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

    private void computeForce(int numGroups, boolean debug) {
        checkGLError("Before compute force");
        glUseProgram(computeForceKernelProgram);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, BODIES_IN_SSBO_BINDING, SWAPPING_BODIES_IN_SSBO);
        checkGLError("Bodies in SSBO bind");      // bodies input
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, BODIES_OUT_SSBO_BINDING, SWAPPING_BODIES_OUT_SSBO);
        checkGLError("Bodies out SSBO bind");      // bodies output
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, NODES_SSBO_BINDING, NODES_SSBO);
        checkGLError("Nodes SSBO bind");    // tree nodes
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ROOT_NODE_SSBO_BINDING, ROOT_NODE_SSBO);
        checkGLError("Root node SSBO bind"); // root node ID input
        
        // Set uniforms
        float theta = (float)Settings.getInstance().getTheta();
        float dt = (float)Settings.getInstance().getDt();
        glUniform1ui(glGetUniformLocation(computeForceKernelProgram, "numBodies"), planets.size());
        glUniform1f(glGetUniformLocation(computeForceKernelProgram, "theta"), theta);
        glUniform1f(glGetUniformLocation(computeForceKernelProgram, "dt"), dt);

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


    
}
