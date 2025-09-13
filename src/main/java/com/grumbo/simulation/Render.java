package com.grumbo.simulation;

import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL43.*;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.system.MemoryStack.*;
import org.joml.Vector3f;
import org.joml.Matrix4f;

import com.grumbo.gpu.SSBO;
import com.grumbo.gpu.Body;

public class Render {
    public enum RenderMode {
        OFF,
        POINTS,
        IMPOSTOR_SPHERES,
        IMPOSTOR_SPHERES_WITH_GLOW,
        MESH_SPHERES
    }
    public boolean showRegions = false;

    // Render Programs
    private int pointsProgram; // points program
    private int impostorProgram; // point-sprite impostor spheres
    private int sphereProgram;   // instanced mesh spheres
    private int regionsProgram; // regions
    
    //Render Uniforms
    private int vao; // for points and impostors
    private int uPointsMvpLocation;           // for points
    private int uImpostorModelViewLocation;   // for impostors
    private int uSphereMvpLocation;     // for mesh spheres
    private int uSphereRadiusScaleLocation;  // mass->radius scale
    private int uSphereCameraPosLocation;
    private int uImpostorPointScaleLocation; // impostor scale (world radius scale)
    private int uImpostorCameraPosLocation;
    private int uImpostorCameraFrontLocation;
    private int uImpostorFovYLocation;
    private int uImpostorAspectLocation;
    private int uImpostorPassLocation;
    private int uImpostorProjLocation;
    private int uRegionsMvpLocation;
    private int uRegionsMinMaxDepthLocation;

    private int regionsVao;
    private int regionsVbo;
    private int regionsEbo;

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


    private void createProgram(String renderScheme, int program) {
        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, getVertexShaderSource(renderScheme));
        glCompileShader(vs);
        checkShader(vs);
        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, getFragmentShaderSource(renderScheme));
        glCompileShader(fs);
        checkShader(fs);
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        checkProgram(program);

    }

    public void init() {
        // Create points render program
        pointsProgram = glCreateProgram();
        createProgram("points", pointsProgram);
        checkGLError("pointsProgram");

        // Cache uniform locations
        uPointsMvpLocation = glGetUniformLocation(pointsProgram, "uMVP");

        // Create impostor render program
        impostorProgram = glCreateProgram();
        createProgram("impostor", impostorProgram);
        checkGLError("impostorProgram");
        // Cache uniform locations
        uImpostorModelViewLocation = glGetUniformLocation(impostorProgram, "uModelView");
        uImpostorPointScaleLocation = glGetUniformLocation(impostorProgram, "uPointScale");
        uImpostorCameraPosLocation = glGetUniformLocation(impostorProgram, "uCameraPos");
        uImpostorCameraFrontLocation = glGetUniformLocation(impostorProgram, "uCameraFront");
        uImpostorFovYLocation = glGetUniformLocation(impostorProgram, "uFovY");
        uImpostorAspectLocation = glGetUniformLocation(impostorProgram, "uAspect");
        uImpostorPassLocation = glGetUniformLocation(impostorProgram, "uPass");
        uImpostorProjLocation = glGetUniformLocation(impostorProgram, "uProj");

        // Create mesh sphere render program
        sphereProgram = glCreateProgram();
        createProgram("sphere", sphereProgram);
        checkGLError("sphereProgram");
        // Cache uniform locations
        uSphereMvpLocation = glGetUniformLocation(sphereProgram, "uMVP");
        uSphereRadiusScaleLocation = glGetUniformLocation(sphereProgram, "uRadiusScale");
        uSphereCameraPosLocation = glGetUniformLocation(sphereProgram, "uCameraPos");

        // Enable point size
        glEnable(GL_PROGRAM_POINT_SIZE);

        // Initialize sphere mesh VAO/VBO/IBO
        vao = glGenVertexArrays();
        rebuildSphereMesh();

        // Initialize regions program
        regionsProgram = glCreateProgram();
        createProgram("regions", regionsProgram);
 
        // Cache uniform locations
        uRegionsMvpLocation = glGetUniformLocation(regionsProgram, "uMVP");
        uRegionsMinMaxDepthLocation = glGetUniformLocation(regionsProgram, "uMinMaxDepth");

        // Initialize regions VAO/VBO/EBO
        regionsVao = glGenVertexArrays();
        regionsVbo = glGenBuffers();
        regionsEbo = glGenBuffers();

        rebuildRegionsMesh();

        checkGLError("init");

    }

        /* --------- Rendering --------- */

        public void render(SSBO bodiesOutSSBO, GPUSimulation.State state) {
            checkGLError("before render");
            if (renderMode == RenderMode.OFF) return;
            getCameraView();
            switch (renderMode) {
                case POINTS: renderPoints(bodiesOutSSBO); break;
                case IMPOSTOR_SPHERES: renderImpostorSpheres(bodiesOutSSBO); break;
                case IMPOSTOR_SPHERES_WITH_GLOW: renderImpostorSpheres(bodiesOutSSBO); break;
                case MESH_SPHERES: renderMeshSpheres(bodiesOutSSBO); break;
                default: break;
            }
            if (showRegions) renderRegions(gpuSimulation.barnesHutNodesSSBO(), gpuSimulation.barnesHutValuesSSBO());
            checkGLError("after render");
        }

        private void bindWithCorrectOffset(SSBO bodiesOutSSBO) {
            int RENDERING_SSBO_OFFSET = Body.HEADER_SIZE;
            glBindBufferRange(GL_SHADER_STORAGE_BUFFER,
                SSBO.BODIES_IN_SSBO_BINDING,
                bodiesOutSSBO.getBufferLocation(),
                RENDERING_SSBO_OFFSET,
                (long)gpuSimulation.numBodies() * Body.STRUCT_SIZE * Float.BYTES);
        }
    
        public void renderPoints(SSBO bodiesOutSSBO     ) {
            // Do not clear or swap; caller owns window
            glUseProgram(pointsProgram);
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

            if (renderMode == RenderMode.IMPOSTOR_SPHERES_WITH_GLOW) {
                glEnable    (GL_DEPTH_TEST);
                glDepthMask(false);

                glBlendFunc(GL_ONE, GL_ONE);
                renderImpostorSpheresPass(bodiesOutSSBO, 1);

                glDepthMask(true);
            }

            glUseProgram(0);
        }


        private void renderImpostorSpheresPass(SSBO bodiesOutSSBO, int pass) {
            glUseProgram(impostorProgram);
            glUniform1f(uImpostorPointScaleLocation, impostorPointScale);
            glUniform3f(uImpostorCameraPosLocation, Settings.getInstance().getCameraPos().x, Settings.getInstance().getCameraPos().y, Settings.getInstance().getCameraPos().z);
            glUniform3f(uImpostorCameraFrontLocation, Settings.getInstance().getCameraFront().x, Settings.getInstance().getCameraFront().y, Settings.getInstance().getCameraFront().z);
            glUniform1f(uImpostorFovYLocation, (float)Math.toRadians(Settings.getInstance().getFov()));
            glUniform1i(uImpostorPassLocation, pass);
            float aspect = (float)Settings.getInstance().getWidth() / (float)Settings.getInstance().getHeight();
            glUniform1f(uImpostorAspectLocation, aspect);
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
            glUniform1f(uSphereRadiusScaleLocation, sphereRadiusScale);
            // Distance/color uniforms
            glUniform3f(uSphereCameraPosLocation, Settings.getInstance().getCameraPos().x, Settings.getInstance().getCameraPos().y, Settings.getInstance().getCameraPos().z);
            int instanceCount = Math.min(gpuSimulation.numBodies(), maxMeshInstances);
            glDrawElementsInstanced(GL_TRIANGLES, sphereIndexCount, GL_UNSIGNED_INT, 0L, instanceCount);
            glBindVertexArray(0);
            glUseProgram(0);
        }

        public void renderRegions(SSBO NodesSSBO, SSBO ValuesSSBO) {
            glDisable(GL_DEPTH_TEST);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glDepthMask(false);
            glUseProgram(regionsProgram);
            // Bind nodes SSBO at binding 4
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, NodesSSBO.getBufferLocation());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, SSBO.INTERNAL_NODES_SSBO_BINDING, NodesSSBO.getBufferLocation());
            // Start after leaves
            
            glUniform2i(uRegionsMinMaxDepthLocation, Settings.getInstance().getMinDepth(), Settings.getInstance().getMaxDepth());

            glBindBuffer(GL_SHADER_STORAGE_BUFFER, ValuesSSBO.getBufferLocation());

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


        
    private void getCameraView() {
        // Get camera position from Settings
        Vector3f eye = new Vector3f(Settings.getInstance().getCameraPos());
        Vector3f center = new Vector3f(eye).add(Settings.getInstance().getCameraFront());
        Vector3f up = new Vector3f(Settings.getInstance().getCameraUp());
        float fov = Settings.getInstance().getFov();
        float aspect = (float) Settings.getInstance().getWidth() / (float) Settings.getInstance().getHeight();
        float near = Settings.getInstance().getNearPlane();
        float far = Settings.getInstance().getFarPlane();

        Matrix4f proj = new Matrix4f().perspective((float) java.lang.Math.toRadians(fov), aspect, near, far);
        Matrix4f view = new Matrix4f().lookAt(eye, center, up);
        Matrix4f mvp = new Matrix4f(proj).mul(view);

        try (MemoryStack stack = stackPush()) {
            FloatBuffer mvpBuf = stack.mallocFloat(16);
            mvp.get(mvpBuf);
            setMvp(mvpBuf);
            FloatBuffer projBuf = stack.mallocFloat(16);
            proj.get(projBuf);
            setCameraToClip(projBuf);
            FloatBuffer viewBuf = stack.mallocFloat(16);
            view.get(viewBuf);
            setModelView(viewBuf);
        }
    }




    public void setMvp(java.nio.FloatBuffer mvp4x4ColumnMajor) {
        // Called by the window each frame to set camera transform 
        glUseProgram(pointsProgram);
        glUniformMatrix4fv(uPointsMvpLocation, false, mvp4x4ColumnMajor);
        glUseProgram(sphereProgram);
        glUniformMatrix4fv(uSphereMvpLocation, false, mvp4x4ColumnMajor);
        glUseProgram(regionsProgram);
        glUniformMatrix4fv(uRegionsMvpLocation, false, mvp4x4ColumnMajor);
        glUseProgram(0);
    }
    
    public void setCameraToClip(java.nio.FloatBuffer cameraToClip4x4ColumnMajor) {
        // Mirror MVP handling: set on impostor program when provided
        glUseProgram(impostorProgram);
        glUniformMatrix4fv(uImpostorProjLocation, false, cameraToClip4x4ColumnMajor);
        glUseProgram(0);
    }

    public void setModelView(java.nio.FloatBuffer modelView4x4ColumnMajor) {
        glUseProgram(impostorProgram);
        glUniformMatrix4fv(uImpostorModelViewLocation, false, modelView4x4ColumnMajor);
        glUseProgram(0);
    }

    /* --------- SHADERS --------- */

    private String getVertexShaderSource(String renderScheme) {
        try {
            return Files.readString(Paths.get("src/main/resources/shaders/render/" + renderScheme + "/"+ renderScheme + ".vert"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read vertex shader: " + e.getMessage());
        }
    }

    private String getFragmentShaderSource(String renderScheme) {
        try {
            return Files.readString(Paths.get("src/main/resources/shaders/render/" + renderScheme + "/"+ renderScheme + ".frag"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fragment shader: " + e.getMessage());
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

    /* --------- Cleanup --------- */
    public void cleanup() {
        if (pointsProgram != 0) glDeleteProgram(pointsProgram);
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
    /**
     * Check for OpenGL errors.
     */
    private void checkGLError(String operation) {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            System.err.println("OpenGL Error after " + operation + ": " + error);
        }
    }
}