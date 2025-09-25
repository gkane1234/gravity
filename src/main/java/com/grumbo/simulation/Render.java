package com.grumbo.simulation;

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
        MESH_SPHERES;


        public static RenderMode fromString(String renderMode) {
            switch (renderMode) {
                case "off": return OFF;
                case "points": return POINTS;
                case "imp": return IMPOSTOR_SPHERES;
                case "impGlow": return IMPOSTOR_SPHERES_WITH_GLOW;
                case "mesh": return MESH_SPHERES;
            }
            return OFF;
        }
    }
    public boolean showRegions = false;

    // Render Programs
    private RenderProgram pointsProgram; // points program
    private RenderProgram impostorProgram; // point-sprite impostor spheres
    private RenderProgram sphereProgram;   // instanced mesh spheres
    private RenderProgram regionsProgram; // regions
    

    // Impostor config
    public float impostorPointScale = 1f; // mass to pixel size scale
    public float sphereRadiusScale = 1f;
    public int pass = 0;
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

        for (GLSLMesh.MeshType mesh : GLSLMesh.MeshType.values()) {
            GLSLMesh.reInitializeMesh(mesh);
        }

        // Create points render program
        pointsProgram = new RenderProgram("points", GLSLMesh.MeshType.POINTS, gpuSimulation.initialNumBodies());
        pointsProgram.setUniforms(new Uniform[] {
            GPU.UNIFORM_MVP
        });
        pointsProgram.setSSBOs(new SSBO[] {
            GPU.SSBO_SWAPPING_BODIES_IN,
        });
        GPUSimulation.checkGLError("pointsProgram");



        // Create impostor render program
        impostorProgram = new RenderProgram("impostor", GLSLMesh.MeshType.IMPOSTOR, gpuSimulation.initialNumBodies());
        RenderProgram.checkProgram(impostorProgram.getProgram());
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
            GPU.SSBO_SWAPPING_BODIES_IN,
            GPU.SSBO_SIMULATION_VALUES
        });
        GPUSimulation.checkGLError("impostorProgram");

        // Create mesh sphere render program
        sphereProgram = new RenderProgram("sphere", GLSLMesh.MeshType.SPHERE, gpuSimulation.initialNumBodies());
        sphereProgram.setUniforms(new Uniform[] {
            GPU.UNIFORM_MVP,
            GPU.UNIFORM_RADIUS_SCALE,
            GPU.UNIFORM_CAMERA_POS,
        });
        sphereProgram.setSSBOs(new SSBO[] {
            GPU.SSBO_SWAPPING_BODIES_IN,
        });
        GPUSimulation.checkGLError("sphereProgram");

        // Enable point size
        glEnable(GL_PROGRAM_POINT_SIZE);


        // Initialize regions program
        regionsProgram = new RenderProgram("regions", GLSLMesh.MeshType.REGIONS, gpuSimulation.initialNumBodies()-1);
        regionsProgram.setUniforms(new Uniform[] {
            GPU.UNIFORM_MVP,
            GPU.UNIFORM_MIN_MAX_DEPTH,
        });
        regionsProgram.setSSBOs(new SSBO[] {
            GPU.SSBO_INTERNAL_NODES,
            GPU.SSBO_SIMULATION_VALUES,
        });
        GPUSimulation.checkGLError("regionsProgram");

        GPUSimulation.checkGLError("init");

    }


        /* --------- Rendering --------- */
    /**
     * Renders the simulation.
     * @param state the state of the simulation
     */
    public void render(GPUSimulation.State state) {
        GPUSimulation.checkGLError("before render");
        
        // Get the camera view
        switch (RenderMode.fromString(Settings.getInstance().getRenderMode())) {
            case OFF: return;
            case POINTS: renderPoints(); break;
            case IMPOSTOR_SPHERES: renderImpostorSpheres(); break;
            case IMPOSTOR_SPHERES_WITH_GLOW: renderImpostorSpheres(); break;
            case MESH_SPHERES: renderMeshSpheres(); break;
            default: break;
        }
        // Render the regions
        if (showRegions && gpuSimulation.getSteps() > 0) renderRegions();
        GPUSimulation.checkGLError("after render");
    }


    /**
     * Renders the points.
     */
    public void renderPoints() {
        pointsProgram.run();
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
        pass = 0;
        impostorProgram.run();

        if (renderMode == RenderMode.IMPOSTOR_SPHERES_WITH_GLOW) {
            //set up appropriate blending for additive glow
            glEnable    (GL_DEPTH_TEST);
            glDepthMask(false);
            glBlendFunc(GL_ONE, GL_ONE);
            pass = 1;
            impostorProgram.run();

            glDepthMask(true);
        }

        glUseProgram(0);
    }

    /**
     * Renders the mesh spheres.
     */
    public void renderMeshSpheres() {
        sphereProgram.run();
    }

    /**
     * Renders the regions.
     */
    public void renderRegions() {
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        regionsProgram.run();

        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }



    /**
     * Gets the MVP matrix.
     * @return the MVP matrix
     */
    public Matrix4f getMVP() {
        Matrix4f proj = projMatrix();
        Matrix4f view = viewMatrix();
        Matrix4f mvp = new Matrix4f(proj).mul(view);
        return mvp;
    }
    /**
     * Gets the camera to clip matrix.
     * @return the camera to clip matrix
     */
    public Matrix4f projMatrix() {
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
    public Matrix4f viewMatrix() {

        // Get camera position from Settings
        Vector3f eye = new Vector3f(Settings.getInstance().getCameraPos());
        Vector3f center = new Vector3f(eye).add(Settings.getInstance().getCameraFront());
        Vector3f up = new Vector3f(Settings.getInstance().getCameraUp());
        
        Matrix4f view = new Matrix4f().lookAt(eye, center, up); 
        return view;
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


    

    /* --------- Cleanup --------- */
    public void cleanup() {
        pointsProgram.delete();
        impostorProgram.delete();
        sphereProgram.delete();
        regionsProgram.delete();
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