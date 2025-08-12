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
    private int vao; // points VAO

    // Simulation params
    private static final int WORK_GROUP_SIZE = 128;

    private int computeProgram;
    private int renderProgram; // points program
    private int impostorProgram; // point-sprite impostor spheres
    private int sphereProgram;   // instanced mesh spheres

    private int ssboA;
    private int ssboB;
    private int srcSSBO;
    private int dstSSBO;
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

        // Create compute shader
        computeProgram = glCreateProgram();
        int computeShader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(computeShader, getComputeShaderSource());
        glCompileShader(computeShader);
        checkShader(computeShader);
        glAttachShader(computeProgram, computeShader);
        glLinkProgram(computeProgram);
        checkProgram(computeProgram);

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

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

        srcSSBO = ssboA;
        dstSSBO = ssboB;

        glEnable(GL_PROGRAM_POINT_SIZE);

        // Initialize sphere mesh VAO/VBO/IBO
        rebuildSphereMesh();
    }

    public void step() {
        processCommands();
        stepSimulation();
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
        if (renderProgram != 0) glDeleteProgram(renderProgram);
        if (impostorProgram != 0) glDeleteProgram(impostorProgram);
        if (sphereProgram != 0) glDeleteProgram(sphereProgram);
        if (ssboA != 0) glDeleteBuffers(ssboA);
        if (ssboB != 0) glDeleteBuffers(ssboB);
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
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssboA);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, data);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }

    // Resizes SSBOs to fit new count, then uploads
    public void resizeBuffersAndUpload(List<Planet> newPlanets) {
        this.planets = new ArrayList<>(newPlanets);
        int bytes = this.planets.size() * 12 * Float.BYTES;

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssboA);
        glBufferData(GL_SHADER_STORAGE_BUFFER, bytes, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssboB);
        glBufferData(GL_SHADER_STORAGE_BUFFER, bytes, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

        FloatBuffer data = packPlanets(this.planets);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssboA);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, data);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

        srcSSBO = ssboA;
        dstSSBO = ssboB;
    }

    private void stepSimulation() {
        glUseProgram(computeProgram);

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, srcSSBO);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, dstSSBO);

        glUniform1f(glGetUniformLocation(computeProgram, "dt"), (float)Settings.getInstance().getDt());
        glUniform1ui(glGetUniformLocation(computeProgram, "numBodies"), planets.size());
        glUniform1f(glGetUniformLocation(computeProgram, "softening"), (float)Settings.getInstance().getSoftening());

        int numGroups = (planets.size() + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
        glDispatchCompute(numGroups, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

        // swap SSBOs
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

    // ----------------- SHADERS -----------------

    private String getComputeShaderSource() {
        try {
            return Files.readString(Paths.get("src/main/resources/computeshader.glsl"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read compute shader: " + e.getMessage());
        }
    }

    private String getVertexShaderSource() {
        try {
            return Files.readString(Paths.get("src/main/resources/vertexshader.glsl"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read vertex shader: " + e.getMessage());
        }
    }

    private String getFragmentShaderSource() {
        try {
            return Files.readString(Paths.get("src/main/resources/fragmentshader.glsl"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fragment shader: " + e.getMessage());
        }
    }

    private String getImpostorVertexShaderSource() {
        try {
            return Files.readString(Paths.get("src/main/resources/impostor_vertex.glsl"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read impostor vertex shader: " + e.getMessage());
        }
    }

    private String getImpostorFragmentShaderSource() {
        try {
            return Files.readString(Paths.get("src/main/resources/impostor_fragment.glsl"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read impostor fragment shader: " + e.getMessage());
        }
    }

    private String getSphereVertexShaderSource() {
        try {
            return Files.readString(Paths.get("src/main/resources/sphere_vertex.glsl"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read sphere vertex shader: " + e.getMessage());
        }
    }

    private String getSphereFragmentShaderSource() {
        try {
            return Files.readString(Paths.get("src/main/resources/sphere_fragment.glsl"));
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
