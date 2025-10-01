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

    // Impostor config
    public float impostorPointScale = 1f; // mass to pixel size scale
    public float sphereRadiusScale = 1f;
    public float minImpostorSize = Settings.getInstance().getMinImpostorSize();
    public boolean glowPass = false;
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
     * Initializes anything additional to what is done in GPU.initRenderPrograms().
     */
    public void init() {

        //Nothing additional to do here.

    }

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
        if (Settings.getInstance().isShowRegions() && gpuSimulation.getSteps() > 0) renderRegions();
        GPUSimulation.checkGLError("after render");
    }


    /**
     * Renders the points.
     */
    public void renderPoints() {
        GPU.RENDER_POINTS.run();
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
        glowPass = false;
        GPU.RENDER_IMPOSTOR.run();

        if (renderMode == RenderMode.IMPOSTOR_SPHERES_WITH_GLOW) {
            //set up appropriate blending for additive glow
            glEnable(GL_DEPTH_TEST);
            glDepthMask(false);
            glBlendFunc(GL_ONE, GL_ONE);
            glowPass = true;
            GPU.RENDER_IMPOSTOR.run();

            glDepthMask(true);
        }

        glUseProgram(0);
    }

    /**
     * Renders the mesh spheres.
     */
    public void renderMeshSpheres() {
        GPU.RENDER_SPHERE.run();
    }

    /**
     * Renders the regions.
     */
    public void renderRegions() {
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        GPU.RENDER_REGIONS.run();

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
}