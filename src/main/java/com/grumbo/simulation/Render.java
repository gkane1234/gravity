package com.grumbo.simulation;

import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL43.*;
import org.joml.Vector3f;
import org.joml.Matrix4f;

import com.grumbo.gpu.*;

/**
 * The Render class is responsible for rendering the simulation.
 * The render mode for the bodies is either off, points, impostor spheres, impostor spheres with glow, or mesh spheres.
 * There is also rendering for regions
 */
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
    private RenderProgram pointsProgram; // points program
    private RenderProgram impostorProgram; // point-sprite impostor spheres
    private RenderProgram sphereProgram;   // instanced mesh spheres
    private RenderProgram regionsProgram; // regions
    
    //Render Uniforms
    private int vao; // for points and impostors

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
    public static float impostorPointScale = 1f; // mass to pixel size scale
    public static int maxMeshInstances = 500000000;
    public static float sphereRadiusScale = Settings.getInstance().getDensity(); // radius = sqrt(mass) * scale
    public static int pass = 0;
    private GPUSimulation gpuSimulation;
    private RenderMode renderMode;
    private boolean debug;
    /**
     * Constructor for the Render class.
     * @param gpuSimulation the GPU simulation
     * @param renderMode the render mode
     * @param debug whether to debug the render
     */
    public Render(GPUSimulation gpuSimulation, RenderMode renderMode, boolean debug) {
        this.gpuSimulation = gpuSimulation;
        this.renderMode = renderMode;
        this.debug = debug;
    }




    /**
     * Initializes the different rendering programs.
     */
    public void init() {

        // Create points render program
        pointsProgram = new RenderProgram("points");
        pointsProgram.setUniforms(new Uniform[] {
            GPU.UNIFORM_MVP
        });
        pointsProgram.setSSBOs(new SSBO[] {
            GPU.SSBO_SWAPPING_BODIES_OUT,
        });
        GPUSimulation.checkGLError("pointsProgram");



        // Create impostor render program
        impostorProgram = new RenderProgram("impostor");
        impostorProgram.setUniforms(new Uniform[] {
            GPU.UNIFORM_POINT_SCALE,
            GPU.UNIFORM_CAMERA_POS,
            GPU.UNIFORM_CAMERA_FRONT,
            GPU.UNIFORM_FOV_Y,
            GPU.UNIFORM_ASPECT,
            GPU.UNIFORM_PASS,
            GPU.UNIFORM_PROJ,
            GPU.UNIFORM_MODEL_VIEW,
        });
        impostorProgram.setSSBOs(new SSBO[] {
            GPU.SSBO_SWAPPING_BODIES_OUT,
        });
        GPUSimulation.checkGLError("impostorProgram");

        // Create mesh sphere render program
        sphereProgram = new RenderProgram("sphere");
        sphereProgram.setUniforms(new Uniform[] {
            GPU.UNIFORM_MVP,
            GPU.UNIFORM_RADIUS_SCALE,
            GPU.UNIFORM_CAMERA_POS,
        });
        sphereProgram.setSSBOs(new SSBO[] {
            GPU.SSBO_SWAPPING_BODIES_OUT,
        });
        GPUSimulation.checkGLError("sphereProgram");

        // Enable point size
        glEnable(GL_PROGRAM_POINT_SIZE);

        // Initialize sphere mesh VAO/VBO/IBO
        vao = glGenVertexArrays();
        rebuildSphereMesh();

        // Initialize regions program
        regionsProgram = new RenderProgram("regions");
        regionsProgram.setUniforms(new Uniform[] {
            GPU.UNIFORM_MVP,
            GPU.UNIFORM_MIN_MAX_DEPTH,
        });
        regionsProgram.setSSBOs(new SSBO[] {
            GPU.SSBO_INTERNAL_NODES,
            GPU.SSBO_SIMULATION_VALUES,
        });
        GPUSimulation.checkGLError("regionsProgram");


        // Initialize regions VAO/VBO/EBO
        regionsVao = glGenVertexArrays();
        regionsVbo = glGenBuffers();
        regionsEbo = glGenBuffers();

        rebuildRegionsMesh();

        GPUSimulation.checkGLError("init");

    }


        /* --------- Rendering --------- */
    /**
     * Renders the simulation.
     * @param state the state of the simulation
     */
    public void render(GPUSimulation.State state) {
        GPUSimulation.checkGLError("before render");
        if (renderMode == RenderMode.OFF) return;
        // Get the camera view
        switch (renderMode) {
            case POINTS: renderPoints(); break;
            case IMPOSTOR_SPHERES: renderImpostorSpheres(); break;
            case IMPOSTOR_SPHERES_WITH_GLOW: renderImpostorSpheres(); break;
            case MESH_SPHERES: renderMeshSpheres(); break;
            default: break;
        }
        // Render the regions
        if (showRegions && gpuSimulation.getSteps() > 0) renderRegions(gpuSimulation.barnesHutNodesSSBO(), gpuSimulation.barnesHutValuesSSBO());
        GPUSimulation.checkGLError("after render");
    }


    /**
     * Renders the points.
     */
    public void renderPoints() {
        pointsProgram.run();
        glBindVertexArray(vao);
        glDrawArrays(GL_POINTS, 0, gpuSimulation.initialNumBodies());
        glUseProgram(0);
    }

    /**
     * Renders impostor spheres in two passes, one for the sphere and one for the glow.
     */
    public void renderImpostorSpheres() {
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f); // black background
        glClear(GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        renderImpostorSpheresPass(0);

        if (renderMode == RenderMode.IMPOSTOR_SPHERES_WITH_GLOW) {
            //set up appropriate blending for additive glow
            glEnable    (GL_DEPTH_TEST);
            glDepthMask(false);
            glBlendFunc(GL_ONE, GL_ONE);
            renderImpostorSpheresPass(1);

            glDepthMask(true);
        }

        glUseProgram(0);
    }


    /**
     * Renders the impostor spheres pass.
     * @param bodiesOutSSBO the bodies out SSBO
     * @param pass the pass
     */
    private void renderImpostorSpheresPass(int pass) {
        Render.pass = pass;
        impostorProgram.run();
        glBindVertexArray(vao);
        glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, gpuSimulation.initialNumBodies());
        glUseProgram(0);

    }

    /**
     * Renders the mesh spheres.
     * @param bodiesOutSSBO the bodies out SSBO
     */
    public void renderMeshSpheres() {
        if (sphereVao == 0 || sphereIndexCount == 0) return;
        sphereProgram.run();
        glBindVertexArray(sphereVao);
        int instanceCount = Math.min(gpuSimulation.initialNumBodies(), maxMeshInstances);
        glDrawElementsInstanced(GL_TRIANGLES, sphereIndexCount, GL_UNSIGNED_INT, 0L, instanceCount);
        glBindVertexArray(0);
        glUseProgram(0);
    }

    /**
     * Renders the regions.
     * @param NodesSSBO the nodes SSBO
     * @param ValuesSSBO the values SSBO
     */
    public void renderRegions(SSBO NodesSSBO, SSBO ValuesSSBO) {
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        regionsProgram.run();

        glBindVertexArray(regionsVao);
        int instanceCount = Math.max(0, gpuSimulation.initialNumBodies() - 1);
        if (instanceCount > 0) {
            glDrawElementsInstanced(GL_TRIANGLES, 36, GL_UNSIGNED_INT, 0L, instanceCount);
        }
        glBindVertexArray(0);
        glUseProgram(0);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }



    /**
     * Gets the MVP matrix.
     * @return the MVP matrix
     */
    public static Matrix4f getMVP() {
        Matrix4f proj = projMatrix();
        Matrix4f view = viewMatrix();
        Matrix4f mvp = new Matrix4f(proj).mul(view);
        return mvp;
    }
    /**
     * Gets the camera to clip matrix.
     * @return the camera to clip matrix
     */
    public static Matrix4f projMatrix() {
        float fov = Settings.getInstance().getFov();
        float aspect = (float) Settings.getInstance().getWidth() / (float) Settings.getInstance().getHeight();
        float near = Settings.getInstance().getNearPlane();
        float far = Settings.getInstance().getFarPlane();

        Matrix4f proj = new Matrix4f().perspective((float) java.lang.Math.toRadians(fov), aspect, near, far);
        return proj;
    }
    /**
     * Gets the view matrix.
     * @return the view matrix
     */
    public static Matrix4f viewMatrix() {

        // Get camera position from Settings
        Vector3f eye = new Vector3f(Settings.getInstance().getCameraPos());
        Vector3f center = new Vector3f(eye).add(Settings.getInstance().getCameraFront());
        Vector3f up = new Vector3f(Settings.getInstance().getCameraUp());
        
        Matrix4f view = new Matrix4f().lookAt(eye, center, up); 
        return view;
    }




       




    /* --------- Sphere mesh generation --------- */
    /**
     * Sets the sphere detail.
     * @param stacks the stacks
     * @param slices the slices
     */
    public void setSphereDetail(int stacks, int slices) {
        if (stacks < 3) stacks = 3;
        if (slices < 3) slices = 3;
        this.sphereStacks = stacks;
        this.sphereSlices = slices;
        rebuildSphereMesh();
    }

    /**
     * Sets the render mode.
     * @param mode the render mode
     */
    public void setRenderMode(RenderMode mode) {
        if (mode != null) this.renderMode = mode;
    }

    /**
     * Gets the render mode.
     * @return the render mode
     */
    public RenderMode getRenderMode() {
        return this.renderMode;
    }

    /**
     * Sets the impostor point scale.
     * @param scale the impostor point scale
     */
    public void setImpostorPointScale(float scale) {
        this.impostorPointScale = scale <= 0 ? 1.0f : scale;
    }


    /**
     * Sets the sphere radius scale.
     * @param scale the sphere radius scale
     */
    public void setSphereRadiusScale(float scale) {
        this.sphereRadiusScale = scale;
    }

    /**
     * Sets the max mesh instances.
     * @param max the max mesh instances
     */
    public void setMaxMeshInstances(int max) {
        this.maxMeshInstances = Math.max(1, max);
    }

    /**
     * Rebuilds the sphere mesh.
     */
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

    /**
     * Generates the sphere positions.
     * @param stacks the stacks
     * @param slices the slices
     * @return the sphere positions
     */
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

    /**
     * Generates the sphere indices.
     * @param stacks the stacks
     * @param slices the slices
     * @return the sphere indices
     */
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

    /**
     * Rebuilds the regions mesh.
     */
    private void rebuildRegionsMesh() {
        //Creates a cube mesh
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

        //Creates a cube mesh
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
        pointsProgram.delete();
        impostorProgram.delete();
        sphereProgram.delete();
        regionsProgram.delete();
        
        if (vao != 0) glDeleteVertexArrays(vao);
        if (sphereVao != 0) glDeleteVertexArrays(sphereVao);
        if (sphereVbo != 0) glDeleteBuffers(sphereVbo);
        if (sphereIbo != 0) glDeleteBuffers(sphereIbo);
    }

    /* --------- Shader checking --------- */
    /**
     * Checks if the shader compiled successfully.
     * @param shader the shader to check
     */
    public static void checkShader(int shader) {
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            System.err.println("Shader compilation failed: " + glGetShaderInfoLog(shader));
        }
    }

    /**
     * Checks if the program linked successfully.
     * @param program the program to check
     */
    public static void checkProgram(int program) {
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            System.err.println("Program linking failed: " + glGetProgramInfoLog(program));
        }
    }

}