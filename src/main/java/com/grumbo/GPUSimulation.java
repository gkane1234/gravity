package com.grumbo;

import org.lwjgl.*;

import java.nio.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.Set;
import java.util.HashSet;

import static org.lwjgl.opengl.GL43C.*;


public class GPUSimulation {
    private static final int NODE_STRUCT_SIZE=16;  // GPU adds 2 padding uints for alignment   
    private int vao; // points VAO

    // Simulation params
    private static final int WORK_GROUP_SIZE = 128;
    private static final boolean DEBUG_BARNES_HUT = true; // Set to false to disable debug output
    private static final int DEBUG_FRAME_INTERVAL = 6000; // Show debug every N frames
    private int frameCounter = 0;

    private int computeProgram;
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

    private int ssboA;
    private int ssboB;
    private int srcSSBO;
    private int dstSSBO;
    
    // Barnes-Hut auxiliary SSBOs
    private int mortonSSBO;        // binding 2: morton keys
    private int indexSSBO;         // binding 3: body indices  
    private int nodesSSBO;         // binding 4: tree nodes
    private int wgHistSSBO;        // binding 5: workgroup histograms
    private int wgScannedSSBO;     // binding 6: scanned per-workgroup bases
    private int globalBaseSSBO;    // binding 7: global bucket bases
    private int mortonOutSSBO;     // binding 8: morton output
    private int indexOutSSBO;      // binding 9: index output
    private int rootNodeSSBO;      // binding 10: root node ID
    private int debugSSBO;         // binding 11: debug status buffer
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
    


    private RenderMode renderMode = RenderMode.POINTS;

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
        ssboA = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssboA);
        glBufferData(GL_SHADER_STORAGE_BUFFER, packPlanets(planets), GL_DYNAMIC_COPY);

        ssboB = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssboB);
        glBufferData(GL_SHADER_STORAGE_BUFFER, planets.size() * 12 * Float.BYTES, GL_DYNAMIC_COPY);

        // Create Barnes-Hut auxiliary SSBOs
        int numBodies = planets.size();
        int numWorkGroups = (numBodies + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
        int NUM_BUCKETS = 16; // 2^RADIX_BITS where RADIX_BITS=4
        
        mortonSSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, mortonSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Long.BYTES, GL_DYNAMIC_COPY);
        
        indexSSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, indexSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Integer.BYTES, GL_DYNAMIC_COPY);
        
        nodesSSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, nodesSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, (2 * numBodies - 1) * NODE_STRUCT_SIZE * Integer.BYTES, GL_DYNAMIC_COPY); // 16 uints per node (2 vec4s + 6 uints + 2 padding)
        
        wgHistSSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, wgHistSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numWorkGroups * NUM_BUCKETS * Integer.BYTES, GL_DYNAMIC_COPY);
        
        wgScannedSSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, wgScannedSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numWorkGroups * NUM_BUCKETS * Integer.BYTES, GL_DYNAMIC_COPY);
        
        globalBaseSSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, globalBaseSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, NUM_BUCKETS * Integer.BYTES, GL_DYNAMIC_COPY);
        
        mortonOutSSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, mortonOutSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Long.BYTES, GL_DYNAMIC_COPY);
        
        indexOutSSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, indexOutSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Integer.BYTES, GL_DYNAMIC_COPY);
        
        rootNodeSSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, rootNodeSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, Integer.BYTES, GL_DYNAMIC_COPY); // Single uint
        
        debugSSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, debugSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, (2 * numBodies - 1) * Integer.BYTES, GL_DYNAMIC_COPY); // One uint per node

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

        srcSSBO = ssboA;
        dstSSBO = ssboB;

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
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, srcSSBO);
        glBindVertexArray(vao);
        glDrawArrays(GL_POINTS, 0, planets.size());
        glUseProgram(0);
    }

    public void renderImpostorSpheres() {
        glUseProgram(impostorProgram);
        glUniform1f(uImpostorPointScaleLoc, impostorPointScale);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, srcSSBO);
        glBindVertexArray(vao);
        glDrawArrays(GL_POINTS, 0, planets.size());
        glUseProgram(0);
    }

    public void renderMeshSpheres() {
        if (sphereVao == 0 || sphereIndexCount == 0) return;
        glUseProgram(sphereProgram);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, srcSSBO);
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
        if (computeProgram != 0) glDeleteProgram(computeProgram);
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
        if (ssboA != 0) glDeleteBuffers(ssboA);
        if (ssboB != 0) glDeleteBuffers(ssboB);
        if (mortonSSBO != 0) glDeleteBuffers(mortonSSBO);
        if (indexSSBO != 0) glDeleteBuffers(indexSSBO);
        if (nodesSSBO != 0) glDeleteBuffers(nodesSSBO);
        if (wgHistSSBO != 0) glDeleteBuffers(wgHistSSBO);
        if (wgScannedSSBO != 0) glDeleteBuffers(wgScannedSSBO);
        if (globalBaseSSBO != 0) glDeleteBuffers(globalBaseSSBO);
        if (mortonOutSSBO != 0) glDeleteBuffers(mortonOutSSBO);
        if (indexOutSSBO != 0) glDeleteBuffers(indexOutSSBO);
        if (rootNodeSSBO != 0) glDeleteBuffers(rootNodeSSBO);
        if (debugSSBO != 0) glDeleteBuffers(debugSSBO);
        if (vao != 0) glDeleteVertexArrays(vao);
        if (sphereVao != 0) glDeleteVertexArrays(sphereVao);
        if (sphereVbo != 0) glDeleteBuffers(sphereVbo);
        if (sphereIbo != 0) glDeleteBuffers(sphereIbo);
    }

    // Packs planet data to float buffer: pos(x,y,z), mass, vel(x,y,z), pad, color(r,g,b,a)
    public FloatBuffer packPlanets(List<Planet> planets) {
        int count = planets.size();
        System.out.println("DEBUG: packPlanets() called with " + count + " planets");
        
        FloatBuffer buf = BufferUtils.createFloatBuffer(count * 12);
        for (int i = 0; i < count; i++) {
            Planet p = planets.get(i);
            if (i < 3) { // Debug first few planets
                System.out.printf("  Pack Planet[%d]: mass=%.3f\n", i, p.mass);
            }
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
        System.out.println("DEBUG: uploadPlanetsData() called");
        this.planets = new ArrayList<>(newPlanets);
        FloatBuffer data = packPlanets(this.planets);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssboA);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, data);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        System.out.println("DEBUG: Buffer uploaded to GPU");
    }

    // Resizes SSBOs to fit new count, then uploads
    public void resizeBuffersAndUpload(List<Planet> newPlanets) {
        this.planets = new ArrayList<>(newPlanets);
        int bytes = this.planets.size() * 12 * Float.BYTES;
        int numBodies = this.planets.size();
        int numWorkGroups = (numBodies + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
        int NUM_BUCKETS = 16;

        // Resize body buffers
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssboA);
        glBufferData(GL_SHADER_STORAGE_BUFFER, bytes, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssboB);
        glBufferData(GL_SHADER_STORAGE_BUFFER, bytes, GL_DYNAMIC_COPY);
        
        // Resize Barnes-Hut auxiliary buffers
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, mortonSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Long.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, indexSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Integer.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, nodesSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, (2 * numBodies - 1) * NODE_STRUCT_SIZE * Integer.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, wgHistSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numWorkGroups * NUM_BUCKETS * Integer.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, wgScannedSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numWorkGroups * NUM_BUCKETS * Integer.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, globalBaseSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, NUM_BUCKETS * Integer.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, mortonOutSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Long.BYTES, GL_DYNAMIC_COPY);
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, indexOutSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, numBodies * Integer.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, rootNodeSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, Integer.BYTES, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, debugSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, (2 * numBodies - 1) * Integer.BYTES, GL_DYNAMIC_COPY);

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

        FloatBuffer data = packPlanets(this.planets);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssboA);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, data);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

        srcSSBO = ssboA;
        dstSSBO = ssboB;
    }

    private void stepBarnesHut() {
        frameCounter++;
        boolean debugThisFrame = DEBUG_BARNES_HUT && (frameCounter % DEBUG_FRAME_INTERVAL == 0);
        
        int numGroups = (planets.size() + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
        if (debugThisFrame) {
            System.out.println("=== Barnes-Hut Step (Frame " + frameCounter + ") ===");
            System.out.println("Bodies: " + planets.size() + ", WorkGroups: " + numGroups);
            System.out.println("DEBUG: planets list size = " + planets.size());
        }
        
        //Generate Morton Codes
        if (debugThisFrame) System.out.println("1. Generating Morton codes...");
        generateMortonCodes(numGroups, debugThisFrame);

        //Radix Sort
        if (debugThisFrame) System.out.println("2. Radix sorting...");
        radixSort(numGroups, debugThisFrame);
        
        //Generate Binary Radix Tree
        if (debugThisFrame) System.out.println("3. Building binary radix tree...");
        buildBinaryRadixTree(numGroups, debugThisFrame);

        //Compute COM and Location
        if (debugThisFrame) System.out.println("4. Computing center-of-mass...");
        computeCOMAndLocation(numGroups, debugThisFrame);

        //Compute Force
        if (debugThisFrame) System.out.println("5. Computing forces...");
        computeForce(numGroups, debugThisFrame);
        
        if (debugThisFrame) System.out.println("=== Barnes-Hut Complete ===\n");
    }

    private void generateMortonCodes(int numGroups, boolean debug) {
        glUseProgram(mortonKernelProgram);
        
        // Bind bodies (input) and morton/index (output)
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, srcSSBO);      // bodies input
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, dstSSBO);      // bodies output (unused)
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, mortonSSBO);   // morton keys output
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, indexSSBO);    // indices output
        
        // Compute AABB from planet positions
        float[] aabbMin = computeAABB()[0];
        float[] aabbMax = computeAABB()[1];
        
        if (debug) {
            System.out.printf("   AABB: min(%.3f,%.3f,%.3f) max(%.3f,%.3f,%.3f)\n", 
                aabbMin[0], aabbMin[1], aabbMin[2], aabbMax[0], aabbMax[1], aabbMax[2]);
            float[] extent = {aabbMax[0] - aabbMin[0], aabbMax[1] - aabbMin[1], aabbMax[2] - aabbMin[2]};
            System.out.printf("   Extent: (%.3f,%.3f,%.3f)\n", extent[0], extent[1], extent[2]);
        }
        
        // Set uniforms
        glUniform1ui(glGetUniformLocation(mortonKernelProgram, "numBodies"), planets.size());
        glUniform3fv(glGetUniformLocation(mortonKernelProgram, "aabbMin"), aabbMin);
        glUniform3fv(glGetUniformLocation(mortonKernelProgram, "aabbMax"), aabbMax);
        
        glDispatchCompute(numGroups, 1, 1);
        if (debug) checkGLError("Morton codes dispatch");
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        if (debug) checkGLError("Morton codes barrier");
    }
    
    private float[][] computeAABB() {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, srcSSBO);
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
        
        return new float[][] {
            {minX, minY, minZ},
            {maxX, maxY, maxZ}
        };
    }

    private void radixSort(int numGroups, boolean debug) {
        int numPasses = (int)Math.ceil(63.0 / 4.0); // 16 passes for 63-bit Morton codes
        
        // Current input/output morton and index buffers
        int currentMortonIn = mortonSSBO;
        int currentIndexIn = indexSSBO;
        int currentMortonOut = mortonOutSSBO;
        int currentIndexOut = indexOutSSBO;

        for (int pass = 0; pass < numPasses; pass++) {
            int passShift = pass * 4; // 4 bits per pass
            
            // Phase 1: Histogram
            glUseProgram(radixSortHistogramKernelProgram);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, currentMortonIn);    // morton input
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, currentIndexIn);     // index input
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 5, wgHistSSBO);         // workgroup histograms
            
            glUniform1ui(glGetUniformLocation(radixSortHistogramKernelProgram, "numBodies"), planets.size());
            glUniform1ui(glGetUniformLocation(radixSortHistogramKernelProgram, "passShift"), passShift);
            glUniform1ui(glGetUniformLocation(radixSortHistogramKernelProgram, "numWorkGroups"), numGroups);
            
            glDispatchCompute(numGroups, 1, 1);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
            
            // Phase 2: Scan
            glUseProgram(radixSortScanKernelProgram);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 5, wgHistSSBO);         // workgroup histograms input
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 6, wgScannedSSBO);      // scanned per-wg bases output
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 7, globalBaseSSBO);     // global bucket bases output
            
            glUniform1ui(glGetUniformLocation(radixSortScanKernelProgram, "numBodies"), planets.size());
            glUniform1ui(glGetUniformLocation(radixSortScanKernelProgram, "numWorkGroups"), numGroups);
            
            glDispatchCompute(1, 1, 1); // Single invocation
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
            
            // Phase 3: Scatter
            glUseProgram(radixSortScatterKernelProgram);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, currentMortonIn);    // morton input
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, currentIndexIn);     // index input
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 6, wgScannedSSBO);      // scanned bases
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 7, globalBaseSSBO);     // global bases
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 8, currentMortonOut);   // morton output
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 9, currentIndexOut);    // index output
            
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
        mortonSSBO = currentMortonIn;
        indexSSBO = currentIndexIn;

        
        // Debug: Output buffer data
        if (debug) {
            System.out.println("=== RADIX SORT RESULTS ===");
            
            // Read sorted Morton codes
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, mortonSSBO);
            ByteBuffer mortonBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            LongBuffer mortonData = mortonBuffer.asLongBuffer();
            System.out.println("Sorted Morton codes:");
            for (int i = 0; i < Math.min(100, planets.size()); i++) {
                long morton = mortonData.get(i);
                System.out.printf("  [%d]: 0x%016X (%d)\n", i, morton, morton);
            }
            Set<Long> s = new HashSet<Long>();
            for (int i = 0; i < mortonData.capacity(); i++) {
                s.add(mortonData.get(i));
            }
            System.out.println(mortonData.capacity() + " " + s.size());
            glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            
            // Read sorted body indices
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, indexSSBO);
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
    }

    private void buildBinaryRadixTree(int numGroups, boolean debug) {
        glUseProgram(buildBinaryRadixTreeKernelProgram);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, mortonSSBO);   // morton keys (sorted)
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, indexSSBO);    // body indices (sorted)
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, nodesSSBO);    // tree nodes output
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 10, rootNodeSSBO); // root node ID output
        
        glUniform1ui(glGetUniformLocation(buildBinaryRadixTreeKernelProgram, "numBodies"), planets.size());
        
        // Dispatch numBodies-1 threads (one per internal node)
        int numInternalNodes = planets.size() - 1;
        int internalNodeGroups = (numInternalNodes + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
        
        if (debug) {
            System.out.println("   Tree construction - internal nodes: " + numInternalNodes + ", groups: " + internalNodeGroups);
        }
        
        glDispatchCompute(internalNodeGroups, 1, 1);
        if (debug) checkGLError("Tree construction dispatch");
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        if (debug) checkGLError("Tree construction barrier");

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        
        
    }

    private void computeCOMAndLocation(int numGroups, boolean debug) {
        // Phase 1: Initialize leaf nodes with body data
        if (debug) {
            System.out.println("   DEBUG: Checking Planet objects mass values:");
            for (int i = 0; i < Math.min(10, planets.size()); i++) {
                Planet p = planets.get(i);
                System.out.printf("     Planet[%d]: pos(%.3f,%.3f,%.3f) mass=%.3f\n", 
                    i, p.x, p.y, p.z, p.mass);
            }
            
            System.out.println("   DEBUG: Checking body data in GPU buffer:");
            // Check first few bodies in srcSSBO
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, srcSSBO);
            ByteBuffer bodyBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            FloatBuffer bodyData = bodyBuffer.asFloatBuffer();
            for (int i = 0; i < Math.min(10, planets.size()); i++) {
                int offset = i * 12; // 12 floats per body (pos+mass, vel+pad, color)
                float x = bodyData.get(offset + 0);
                float y = bodyData.get(offset + 1);
                float z = bodyData.get(offset + 2);
                float mass = bodyData.get(offset + 3);
                System.out.printf("     GPU Body[%d]: pos(%.3f,%.3f,%.3f) mass=%.3f\n", i, x, y, z, mass);
            }
            glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        }
        
        glUseProgram(initLeavesKernelProgram);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, srcSSBO);      // bodies input  
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, mortonSSBO);   // morton keys (for reference)
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, indexSSBO);    // sorted body indices
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, nodesSSBO);    // nodes to initialize
        
        glUniform1ui(glGetUniformLocation(initLeavesKernelProgram, "numBodies"), planets.size());
        glDispatchCompute(numGroups, 1, 1); // One thread per body (leaf node)
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        
        if (debug) {
            System.out.println("   DEBUG: Checking sorted body indices:");
            // Check what indices the shader will use
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, indexSSBO);
            ByteBuffer indexBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            IntBuffer indexData = indexBuffer.asIntBuffer();
            for (int i = 0; i < Math.min(5, planets.size()); i++) {
                int bodyIdx = indexData.get(i);
                System.out.printf("     index[%d] = %d\n", i, bodyIdx);
            }
            glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
            
            System.out.println("   After leaf initialization:");
            // Check if leaf nodes got proper data
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, nodesSSBO);
            ByteBuffer nodeBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            IntBuffer nodeData = nodeBuffer.asIntBuffer();
            for (int i = 0; i < Math.min(5, planets.size()); i++) { // Check first 5 leaf nodes
                int offset = i * NODE_STRUCT_SIZE;
                float mass = Float.intBitsToFloat(nodeData.get(offset + 3));
                int parentId = nodeData.get(offset + 13);
                System.out.printf("     Leaf[%d]: mass=%.3f parent=%d\n", i, mass, 
                    parentId == 0xFFFFFFFF ? -1 : parentId);
            }
            glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        }
        
        // Phase 2: Bottom-up propagation of internal nodes (multiple passes)
        int numInternalNodes = planets.size() - 1;
        int internalNodeGroups = (numInternalNodes + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
        
        for (int pass = 0; pass < 100; pass++) { // Max 10 passes for convergence
            glUseProgram(propagateNodesKernelProgram);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, nodesSSBO);    // nodes array
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 11, debugSSBO);   // debug status array
            
            glUniform1ui(glGetUniformLocation(propagateNodesKernelProgram, "numBodies"), planets.size());
            glDispatchCompute(internalNodeGroups, 1, 1); // One thread per internal node
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        }

        // Debug: Output buffer data
        if (debug) {
            System.out.println("=== FINAL TREE WITH COM DATA ===");
            
            // First check for stuck nodes with readyChildren < 2
            System.out.println("=== STUCK NODE ANALYSIS ===");
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, nodesSSBO);
            ByteBuffer nodeBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            IntBuffer nodeData = nodeBuffer.asIntBuffer();
            
            int numLeaves = planets.size();
            int totalNodes = 2 * planets.size() - 1;
            boolean foundStuckNodes = false;
            
            for (int i = numLeaves; i < totalNodes; i++) { // Check internal nodes only
                int offset = i * NODE_STRUCT_SIZE;
                if (offset + NODE_STRUCT_SIZE - 1 < nodeData.capacity()) {
                    int readyChildren = nodeData.get(offset + 12);
                    if (readyChildren < 2) {
                        foundStuckNodes = true;
                        int childA = nodeData.get(offset + 8);
                        int childB = nodeData.get(offset + 9);
                        int parentId = nodeData.get(offset + 13);
                        float mass = Float.intBitsToFloat(nodeData.get(offset + 3));
                        
                        System.out.printf("STUCK Node[%d]: ready=%d childA=%d childB=%d parent=%d mass=%.3f\n", 
                            i, readyChildren, 
                            childA == 0xFFFFFFFF ? -1 : childA,
                            childB == 0xFFFFFFFF ? -1 : childB,
                            parentId == 0xFFFFFFFF ? -1 : parentId,
                            mass);
                            
                        // Check children status
                        if (childA != 0xFFFFFFFF && childA < totalNodes) {
                            int childAOffset = childA * NODE_STRUCT_SIZE;
                            if (childAOffset + NODE_STRUCT_SIZE - 1 < nodeData.capacity()) {
                                int childAReady = childA < numLeaves ? 1 : nodeData.get(childAOffset + 12); // Leaves are always ready
                                float childAMass = Float.intBitsToFloat(nodeData.get(childAOffset + 3));
                                System.out.printf("  Child A[%d]: ready=%d mass=%.3f%s\n", 
                                    childA, childAReady, childAMass, childA < numLeaves ? " (leaf)" : "");
                            }
                        }
                        if (childB != 0xFFFFFFFF && childB < totalNodes) {
                            int childBOffset = childB * NODE_STRUCT_SIZE;
                            if (childBOffset + NODE_STRUCT_SIZE - 1 < nodeData.capacity()) {
                                int childBReady = childB < numLeaves ? 1 : nodeData.get(childBOffset + 12); // Leaves are always ready
                                float childBMass = Float.intBitsToFloat(nodeData.get(childBOffset + 3));
                                System.out.printf("  Child B[%d]: ready=%d mass=%.3f%s\n", 
                                    childB, childBReady, childBMass, childB < numLeaves ? " (leaf)" : "");
                            }
                        }
                    }
                }
            }
            
            if (!foundStuckNodes) {
                System.out.println("No stuck nodes found - all internal nodes have readyChildren >= 2");
            }
            
            glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            
            System.out.println("Tree nodes:");
            // Read tree nodes
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, nodesSSBO);
        
            // Read tree nodes
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, nodesSSBO);
            //ByteBuffer nodeBuffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            //IntBuffer nodeData = nodeBuffer.asIntBuffer();
            System.out.println("Tree nodes:");
            // Node structure: 16 uints per node (with GPU padding)
            // [0-3]: comMass (4 floats as uints)
            // [4-7]: centerSize (4 floats as uints) 
            // [8]: childA
            // [9]: childB
            // [10]: firstBody
            // [11]: bodyCount
            // [12]: readyChildren
            // [13]: parentId
            // [14-15]: padding (GPU alignment)
            
            //int numLeaves = planets.size();
            //int numInternalNodes = planets.size() - 1;
            //int totalNodes = 2 * planets.size() - 1;
            
            // Show first few leaf nodes (indices 0 to numBodies-1)
            for (int i = 0; i < Math.min(100, numLeaves); i++) {
                int offset = i * NODE_STRUCT_SIZE;
                if (offset + NODE_STRUCT_SIZE - 1 < nodeData.capacity()) {
                    // Debug: Show raw data for first few nodes
                    if (i < 3) {
                        System.out.printf("   Raw data for Leaf[%d] at offset %d:\n", i, offset);
                        for (int j = 0; j < NODE_STRUCT_SIZE; j++) {
                            int rawVal = nodeData.get(offset + j);
                            float asFloat = Float.intBitsToFloat(rawVal);
                            String fieldName = "";
                            if (j >= 0 && j <= 3) fieldName = " (comMass)";
                            else if (j >= 4 && j <= 7) fieldName = " (centerSize)";
                            else if (j == 8) fieldName = " (childA)";
                            else if (j == 9) fieldName = " (childB)";
                            else if (j == 10) fieldName = " (firstBody)";
                            else if (j == 11) fieldName = " (bodyCount)";
                            else if (j == 12) fieldName = " (readyChildren)";
                            else if (j == 13) fieldName = " (parentId)";
                            else fieldName = " (padding?)";
                            System.out.printf("     [%d]: 0x%08X = %d = %.3f%s\n", j, rawVal, rawVal, asFloat, fieldName);
                        }
                    }
                    
                    float comX = Float.intBitsToFloat(nodeData.get(offset + 0));
                    float comY = Float.intBitsToFloat(nodeData.get(offset + 1));
                    float comZ = Float.intBitsToFloat(nodeData.get(offset + 2));
                    float mass = Float.intBitsToFloat(nodeData.get(offset + 3));
                    float centerX = Float.intBitsToFloat(nodeData.get(offset + 4));
                    float centerY = Float.intBitsToFloat(nodeData.get(offset + 5));
                    float centerZ = Float.intBitsToFloat(nodeData.get(offset + 6));
                    float halfSize = Float.intBitsToFloat(nodeData.get(offset + 7));
                    int childA = nodeData.get(offset + 8);
                    int childB = nodeData.get(offset + 9);
                    int firstBody = nodeData.get(offset + 10);
                    int bodyCount = nodeData.get(offset + 11);
                    int readyChildren = nodeData.get(offset + 12);
                    int parentId = nodeData.get(offset + 13);
                    
                    System.out.printf("  Leaf[%d]: COM(%.3f,%.3f,%.3f) mass=%.3f center(%.3f,%.3f,%.3f) size=%.3f childA=%d childB=%d firstBody=%d bodyCount=%d ready=%d parent=%d\n",
                        i, comX, comY, comZ, mass, centerX, centerY, centerZ, halfSize, 
                        childA == 0xFFFFFFFF ? -1 : childA,
                        childB == 0xFFFFFFFF ? -1 : childB,
                        firstBody, bodyCount, readyChildren,
                        parentId == 0xFFFFFFFF ? -1 : parentId);
                }
            }
            
            // Show internal nodes from halfway point (indices numBodies to 2*numBodies-2)
            int internalStartIdx = numLeaves;
            int halfwayInternal = internalStartIdx + (numInternalNodes / 2);
            for (int i = numLeaves; i < Math.min(halfwayInternal + 100, totalNodes); i++) {
                int offset = i * NODE_STRUCT_SIZE;
                if (offset + NODE_STRUCT_SIZE - 1 < nodeData.capacity()) {
                    float comX = Float.intBitsToFloat(nodeData.get(offset + 0));
                    float comY = Float.intBitsToFloat(nodeData.get(offset + 1));
                    float comZ = Float.intBitsToFloat(nodeData.get(offset + 2));
                    float mass = Float.intBitsToFloat(nodeData.get(offset + 3));
                    float centerX = Float.intBitsToFloat(nodeData.get(offset + 4));
                    float centerY = Float.intBitsToFloat(nodeData.get(offset + 5));
                    float centerZ = Float.intBitsToFloat(nodeData.get(offset + 6));
                    float halfSize = Float.intBitsToFloat(nodeData.get(offset + 7));
                    int childA = nodeData.get(offset + 8);
                    int childB = nodeData.get(offset + 9);
                    int firstBody = nodeData.get(offset + 10);
                    int bodyCount = nodeData.get(offset + 11);
                    int readyChildren = nodeData.get(offset + 12);
                    int parentId = nodeData.get(offset + 13);
                    
                    // Highlight problematic nodes
                    String marker = "";
                    if (readyChildren < 2) marker += " **STUCK**";
                    if (readyChildren > 3) marker += " **CORRUPTED**";
                    if (mass == 0.0f) marker += " **NO_MASS**";
                    if (comX == 0.0f && comY == 0.0f && comZ == 0.0f && mass > 0.0f) marker += " **NO_COM**";
                    
                    System.out.printf("  Internal[%d]: COM(%.3f,%.3f,%.3f) mass=%.3f center(%.3f,%.3f,%.3f) size=%.3f childA=%d childB=%d firstBody=%d bodyCount=%d ready=%d parent=%d%s\n",
                        i, comX, comY, comZ, mass, centerX, centerY, centerZ, halfSize, 
                        childA == 0xFFFFFFFF ? -1 : childA,
                        childB == 0xFFFFFFFF ? -1 : childB,
                        firstBody, bodyCount, readyChildren,
                        parentId == 0xFFFFFFFF ? -1 : parentId, marker);
                }
                // Verify tree structure integrity
            
            }

            glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            verifyTreeStructure(nodeData, numLeaves, totalNodes);
            
            
        }
    }

    private void computeForce(int numGroups, boolean debug) {
        glUseProgram(computeForceKernelProgram);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, srcSSBO);      // bodies input
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, dstSSBO);      // bodies output
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, nodesSSBO);    // tree nodes
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 10, rootNodeSSBO); // root node ID input
        
        // Set uniforms
        float theta = (float)Settings.getInstance().getTheta();
        float dt = (float)Settings.getInstance().getDt();
        glUniform1ui(glGetUniformLocation(computeForceKernelProgram, "numBodies"), planets.size());
        glUniform1f(glGetUniformLocation(computeForceKernelProgram, "theta"), theta);
        glUniform1f(glGetUniformLocation(computeForceKernelProgram, "dt"), dt);
        
        if (debug) {
            System.out.println("   Force computation - theta: " + theta + ", dt: " + dt + ", bodies: " + planets.size());
        }
        
        glDispatchCompute(numGroups, 1, 1); // One thread per body
        if (debug) checkGLError("Force computation dispatch");
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        if (debug) checkGLError("Force computation barrier");
        
        // Swap source and destination buffers for next iteration
        int tmp = srcSSBO;
        srcSSBO = dstSSBO;
        dstSSBO = tmp;
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
            int offset = i * NODE_STRUCT_SIZE;
            if (offset + NODE_STRUCT_SIZE - 1 >= nodeData.capacity()) continue;
            
            int parentId = nodeData.get(offset + 13);
            
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
                int childA = nodeData.get(offset + 8);
                int childB = nodeData.get(offset + 9);
                
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
            int offset = i * NODE_STRUCT_SIZE;
            if (offset + NODE_STRUCT_SIZE - 1 >= nodeData.capacity()) continue;
            
            int childA = nodeData.get(offset + 8);
            int childB = nodeData.get(offset + 9);
            
            // Check childA relationship
            if (childA != 0xFFFFFFFF && childA < totalNodes) {
                int childAOffset = childA * NODE_STRUCT_SIZE;
                if (childAOffset + NODE_STRUCT_SIZE - 1 < nodeData.capacity()) {
                    int childAParent = nodeData.get(childAOffset + 13);
                    if (childAParent != i) {
                        System.out.printf("ERROR: Internal[%d] claims childA=%d, but Node[%d] has parent=%d\n", 
                            i, childA, childA, childAParent == 0xFFFFFFFF ? -1 : childAParent);
                        hasErrors = true;
                    }
                }
            }
            
            // Check childB relationship
            if (childB != 0xFFFFFFFF && childB < totalNodes) {
                int childBOffset = childB * NODE_STRUCT_SIZE;
                if (childBOffset + NODE_STRUCT_SIZE - 1 < nodeData.capacity()) {
                    int childBParent = nodeData.get(childBOffset + 13);
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
                int offset = i * NODE_STRUCT_SIZE;
                if (offset + NODE_STRUCT_SIZE - 1 < nodeData.capacity()) {
                    int parentId = nodeData.get(offset + 13);
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
            int offset = i * NODE_STRUCT_SIZE;
            if (offset + NODE_STRUCT_SIZE - 1 >= nodeData.capacity()) continue;
            
            int childA = nodeData.get(offset + 8);
            int childB = nodeData.get(offset + 9);
            
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
