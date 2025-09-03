package com.grumbo.simulation;

import com.grumbo.simulation.GPUSimulation;

import java.nio.IntBuffer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import com.grumbo.gpu.SSBO;
import com.grumbo.gpu.Body;

import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL43C.*;


public class Render {
    public enum RenderMode {
        OFF,
        POINTS,
        IMPOSTOR_SPHERES,
        MESH_SPHERES,
    }

    public boolean showRegions = true;

    // Render Programs
    private int renderProgram; // points program
    private int impostorProgram; // point-sprite impostor spheres
    private int sphereProgram;   // instanced mesh spheres
    private int regionsProgram; // regions
    
    //Render Uniforms
    private int vao; // for points and impostors
    private int uMvpLocation;           // for points
    private int uModelViewLocationImpostor;   // for impostors
    private int uMvpLocationSphere;     // for mesh spheres
    private int uSphereRadiusScaleLoc;  // mass->radius scale
    private int uCameraPosLocSphere;
    private int uImpostorPointScaleLoc; // impostor scale (world radius scale)
    private int uImpostorCameraPosLoc;
    private int uImpostorCameraFrontLoc;
    private int uImpostorFovYLoc;
    private int uImpostorAspectLoc;
    private int uImpostorPassLoc;
    private int uImpostorProjLoc;
    private int uMvpLocationRegions;
    private int uNodeStartIndexLoc;
    private int uMinMaxDepthLoc;

    private int regionsVao;
    private int regionsVbo;
    private int regionsEbo;
    
    // Cached matrices
    private FloatBuffer cameraToClipMatrix;

    // Mesh sphere resources
    private int sphereVao = 0;
    private int sphereVbo = 0; // positions (and normals interleaved optional later)
    private int sphereIbo = 0;
    private int sphereIndexCount = 0;
    private int sphereStacks = 8;
    private int sphereSlices = 8;

    // Impostor config
    private float impostorPointScale = 1f; // mass to pixel size scale
    private int maxMeshInstances = 500000000;
    private float sphereRadiusScale = Settings.getInstance().getDensity(); // radius = sqrt(mass) * scale


    private GPUSimulation gpuSimulation;
    private RenderMode renderMode;
    private boolean debug;

    public Render(GPUSimulation gpuSimulation, RenderMode renderMode, boolean debug) {
        this.gpuSimulation = gpuSimulation;
        this.renderMode = renderMode;
        this.debug = debug;
    }

    public void init() {
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
        uModelViewLocationImpostor = glGetUniformLocation(impostorProgram, "uModelView");
        uImpostorPointScaleLoc = glGetUniformLocation(impostorProgram, "uPointScale");
        uImpostorCameraPosLoc = glGetUniformLocation(impostorProgram, "uCameraPos");
        uImpostorCameraFrontLoc = glGetUniformLocation(impostorProgram, "uCameraFront");
        uImpostorFovYLoc = glGetUniformLocation(impostorProgram, "uFovY");
        uImpostorAspectLoc = glGetUniformLocation(impostorProgram, "uAspect");
        uImpostorPassLoc = glGetUniformLocation(impostorProgram, "uPass");
        uImpostorProjLoc = glGetUniformLocation(impostorProgram, "uProj");

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
        glEnable(GL_PROGRAM_POINT_SIZE);
        // Initialize sphere mesh VAO/VBO/IBO
        vao = glGenVertexArrays();
        rebuildSphereMesh();

        // Initialize regions program
        regionsProgram = glCreateProgram();
        int rvs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(rvs, getRegionsVertexShaderSource());
        glCompileShader(rvs);
        checkShader(rvs);
        int rfs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(rfs, getRegionsFragmentShaderSource());
        glCompileShader(rfs);
        checkShader(rfs);
        glAttachShader(regionsProgram, rvs);
        glAttachShader(regionsProgram, rfs);
        glLinkProgram(regionsProgram);
        checkProgram(regionsProgram);
        uMvpLocationRegions = glGetUniformLocation(regionsProgram, "uMVP");
        uNodeStartIndexLoc = glGetUniformLocation(regionsProgram, "uNodeStartIndex");
        uMinMaxDepthLoc = glGetUniformLocation(regionsProgram, "uMinMaxDepth");
        regionsVao = glGenVertexArrays();
        regionsVbo = glGenBuffers();
        regionsEbo = glGenBuffers();
        rebuildRegionsMesh();

    }

        /* --------- Rendering --------- */
        public void render(SSBO bodiesOutSSBO, OpenGLWindow.State state) {
            if (renderMode == RenderMode.OFF) return;
            switch (renderMode) {
                case POINTS: renderPoints(bodiesOutSSBO); break;
                case IMPOSTOR_SPHERES: renderImpostorSpheres(bodiesOutSSBO); break;
                case MESH_SPHERES: renderMeshSpheres(bodiesOutSSBO); break;
                default: break;
            }
            if (showRegions) renderRegions(gpuSimulation.barnesHutNodesSSBO());
        }

        private void bindWithCorrectOffset(SSBO bodiesOutSSBO) {
            int RENDERING_SSBO_OFFSET = 16;
            glBindBufferRange(GL_SHADER_STORAGE_BUFFER,
                SSBO.BODIES_IN_SSBO_BINDING,
                bodiesOutSSBO.getBufferLocation(),
                RENDERING_SSBO_OFFSET,
                (long)gpuSimulation.numBodies() * Body.STRUCT_SIZE * Float.BYTES);
        }
    
        public void renderPoints(SSBO bodiesOutSSBO     ) {
            // Do not clear or swap; caller owns window
            glUseProgram(renderProgram);
            // MVP will be sent by caller before rendering
            bindWithCorrectOffset(bodiesOutSSBO);
            glBindVertexArray(vao);
            glDrawArrays(GL_POINTS, 0, gpuSimulation.numBodies());
            glUseProgram(0);
        }
    
        public void renderImpostorSpheres(SSBO bodiesOutSSBO) {
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f); // black background
            glClear(GL_DEPTH_BUFFER_BIT);
            glEnable(GL_DEPTH_TEST);
            glDepthMask(true);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            renderImpostorSpheresPass(bodiesOutSSBO, 0);
            // glEnable    (GL_DEPTH_TEST);
            // glDepthMask(true);

            // glBlendFunc(GL_ONE, GL_ONE);
            // renderImpostorSpheresPass(bodiesOutSSBO, 1);

            glUseProgram(0);
        }


        private void renderImpostorSpheresPass(SSBO bodiesOutSSBO, int pass) {
            glUseProgram(impostorProgram);
            glUniform1f(uImpostorPointScaleLoc, impostorPointScale);
            glUniform3f(uImpostorCameraPosLoc, Settings.getInstance().getCameraPos().x, Settings.getInstance().getCameraPos().y, Settings.getInstance().getCameraPos().z);
            glUniform3f(uImpostorCameraFrontLoc, Settings.getInstance().getCameraFront().x, Settings.getInstance().getCameraFront().y, Settings.getInstance().getCameraFront().z);
            glUniform1f(uImpostorFovYLoc, (float)Math.toRadians(Settings.getInstance().getFov()));
            glUniform1i(uImpostorPassLoc, pass);
            float aspect = (float)Settings.getInstance().getWidth() / (float)Settings.getInstance().getHeight();
            glUniform1f(uImpostorAspectLoc, aspect);
            // cameraToClipMatrix is set by caller via setCameraToClip
            bindWithCorrectOffset(bodiesOutSSBO);
            glBindVertexArray(vao);
            glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, gpuSimulation.numBodies());

        }
    
        public void renderMeshSpheres(SSBO bodiesOutSSBO) {
            if (sphereVao == 0 || sphereIndexCount == 0) return;
            glUseProgram(sphereProgram);
            bindWithCorrectOffset(bodiesOutSSBO);
            glBindVertexArray(sphereVao);
            glUniform1f(uSphereRadiusScaleLoc, sphereRadiusScale);
            // Distance/color uniforms
            glUniform3f(uCameraPosLocSphere, Settings.getInstance().getCameraPos().x, Settings.getInstance().getCameraPos().y, Settings.getInstance().getCameraPos().z);
            int instanceCount = Math.min(gpuSimulation.numBodies(), maxMeshInstances);
            glDrawElementsInstanced(GL_TRIANGLES, sphereIndexCount, GL_UNSIGNED_INT, 0L, instanceCount);
            glBindVertexArray(0);
            glUseProgram(0);
        }

        public void renderRegions(SSBO NodesSSBO) {
            glDisable(GL_DEPTH_TEST);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glDepthMask(false);
            glUseProgram(regionsProgram);
            // Bind nodes SSBO at binding 4
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, NodesSSBO.getBufferLocation());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, SSBO.NODES_SSBO_BINDING, NodesSSBO.getBufferLocation());
            // Start after leaves
            glUniform1i(uNodeStartIndexLoc, gpuSimulation.numBodies());
            glUniform2i(uMinMaxDepthLoc, Settings.getInstance().getMinDepth(), Settings.getInstance().getMaxDepth());

            glBindVertexArray(regionsVao);
            int instanceCount = Math.max(0, gpuSimulation.numBodies() - 1);
            if (instanceCount > 0) {
                glDrawElementsInstanced(GL_TRIANGLES, 36, GL_UNSIGNED_INT, 0L, instanceCount);
            }
            glBindVertexArray(0);
            glUseProgram(0);
            glDepthMask(true);
            glEnable(GL_DEPTH_TEST);
        }


    public void setMvp(java.nio.FloatBuffer mvp4x4ColumnMajor) {
        // Called by the window each frame to set camera transform 
        glUseProgram(renderProgram);
        glUniformMatrix4fv(uMvpLocation, false, mvp4x4ColumnMajor);
        glUseProgram(0);

        glUseProgram(sphereProgram);
        glUniformMatrix4fv(uMvpLocationSphere, false, mvp4x4ColumnMajor);
        glUseProgram(0);

        glUseProgram(regionsProgram);
        glUniformMatrix4fv(uMvpLocationRegions, false, mvp4x4ColumnMajor);
        glUseProgram(0);
    }
    
    public void setCameraToClip(java.nio.FloatBuffer cameraToClip4x4ColumnMajor) {
        // Mirror MVP handling: set on impostor program when provided
        this.cameraToClipMatrix = cameraToClip4x4ColumnMajor;
        glUseProgram(impostorProgram);
        glUniformMatrix4fv(uImpostorProjLoc, false, cameraToClip4x4ColumnMajor);
        glUseProgram(0);
    }

    public void setModelView(java.nio.FloatBuffer modelView4x4ColumnMajor) {
        glUseProgram(impostorProgram);
        glUniformMatrix4fv(uModelViewLocationImpostor, false, modelView4x4ColumnMajor);
        glUseProgram(0);
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

    public RenderMode getRenderMode() {
        return this.renderMode;
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

    /* --------- Regions --------- */

    private void rebuildRegionsMesh() {
        float[] cubeVertices = {
            -0.5f, -0.5f,  0.5f,
            0.5f, -0.5f,  0.5f,
            0.5f,  0.5f,  0.5f,
           -0.5f,  0.5f,  0.5f,
       
           // Back face
           -0.5f, -0.5f, -0.5f,
            0.5f, -0.5f, -0.5f,
            0.5f,  0.5f, -0.5f,
           -0.5f,  0.5f, -0.5f
        };

        int[] cubeIndices = {
            // Front face
            0, 1, 2,
            2, 3, 0,

            // Right face
            1, 5, 6,
            6, 2, 1,

            // Back face
            5, 4, 7,
            7, 6, 5,

            // Left face
            4, 0, 3,
            3, 7, 4,

            // Top face
            3, 2, 6,
            6, 7, 3,

            // Bottom face
            4, 5, 1,
            1, 0, 4
        };
        if (regionsVao == 0) regionsVao = glGenVertexArrays();
        if (regionsVbo == 0) regionsVbo = glGenBuffers();
        if (regionsEbo == 0) regionsEbo = glGenBuffers();
        glBindVertexArray(regionsVao);

        // Vertex buffer
        glBindBuffer(GL_ARRAY_BUFFER, regionsVbo);
        glBufferData(GL_ARRAY_BUFFER, cubeVertices, GL_STATIC_DRAW);

        // Index buffer
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, regionsEbo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, cubeIndices, GL_STATIC_DRAW);

        // Vertex attribute (pos)
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0L);

        glBindVertexArray(0);


    }
    private String getRegionsVertexShaderSource() {
        try {
            return Files.readString(Paths.get("src/main/resources/shaders/regions/regions_vertex.glsl"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read regions vertex shader: " + e.getMessage());
        }
    }
    private String getRegionsFragmentShaderSource() {
        try {
            return Files.readString(Paths.get("src/main/resources/shaders/regions/regions_fragment.glsl"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read regions fragment shader: " + e.getMessage());
        }
    }

    /* --------- Cleanup --------- */
    public void cleanup() {
        if (renderProgram != 0) glDeleteProgram(renderProgram);
        if (impostorProgram != 0) glDeleteProgram(impostorProgram);
        if (sphereProgram != 0) glDeleteProgram(sphereProgram);
        
        if (vao != 0) glDeleteVertexArrays(vao);
        if (sphereVao != 0) glDeleteVertexArrays(sphereVao);
        if (sphereVbo != 0) glDeleteBuffers(sphereVbo);
        if (sphereIbo != 0) glDeleteBuffers(sphereIbo);
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
