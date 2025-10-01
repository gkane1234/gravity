package com.grumbo.gpu;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

import static org.lwjgl.opengl.GL43C.*;
import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector2i;

import com.grumbo.simulation.Render;
import com.grumbo.simulation.GPUSimulation;
import com.grumbo.simulation.PlanetGenerator;
import com.grumbo.simulation.Planet;
import com.grumbo.simulation.Settings;
import com.grumbo.simulation.BarnesHut;
import com.grumbo.simulation.UnitSet;

/**
 * GPU is a class that represents the data and programs on the GPU.
 * It is used to initialize the GPU and the compute and render programs. 
 * As well as initializing the uniforms and SSBOs, and uploading data to the GPU.
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public class GPU {

    // Simulation params
    //To change these, you need to also change their definitions in the compute shader
    public static final int WORK_GROUP_SIZE = 256;
    public static final int RADIX_BITS = 4;
    public static final int NUM_RADIX_BUCKETS = (int)Math.pow(2, RADIX_BITS); // 16 when RADIX_BITS=4
    public static final int MAX_RENDER_INSTANCES = 5_000_000;


    // These can be freely changed here
    public static final int PROPAGATE_NODES_ITERATIONS = 64;
    public static Map<String, Uniform<?>> UNIFORMS;
    public static Map<String, SSBO> SSBOS;
    public static Map<String, ComputeProgram> COMPUTE_PROGRAMS;
    public static Map<String, RenderProgram> RENDER_PROGRAMS;
    //public static Map<String, VertexShader> VERTEX_SHADERS;
    //public static Map<String, FragmentShader> FRAGMENT_SHADERS;

    private static int initialNumBodies;

    // SSBOs

    // layout(std430, binding = 0)  buffer LeafNodes          { Node leafNodes[]; };
    // layout(std430, binding = 1)  buffer InternalNodes      { Node internalNodes[]; };
    // layout(std430, binding = 2)  buffer SimulationValues   { uint numBodies; uint initialNumBodies; uint justDied; uint justMerged; AABB bounds; uint uintDebug[100]; float floatDebug[100]; } sim;
    // layout(std430, binding = 3)  buffer BodiesIn           { Body bodies[]; } srcB;
    // layout(std430, binding = 4)  buffer BodiesOut          { Body bodies[]; } dstB;
    // layout(std430, binding = 5)  buffer MortonIn           { uint64_t mortonIn[]; };
    // layout(std430, binding = 6)  buffer MortonOut          { uint64_t mortonOut[]; };
    // layout(std430, binding = 7)  buffer IndexIn            { uint indexIn[]; };
    // layout(std430, binding = 8)  buffer IndexOut           { uint indexOut[]; };
    // layout(std430, binding = 9)  buffer WorkQueueIn        { uint headIn; uint tailIn; uint itemsIn[]; };
    // layout(std430, binding = 10)  buffer WorkQueueOut       { uint headOut; uint tailOut; uint itemsOut[]; };
    // layout(std430, binding = 11) buffer RadixWGHist        { uint wgHist[];      };
    // layout(std430, binding = 12) buffer RadixWGScanned     { uint wgScanned[];   };
    // layout(std430, binding = 13) buffer RadixBucketTotals  { uint bucketTotals[NUM_BUCKETS]; uint globalBase[NUM_BUCKETS];};
    // layout(std430, binding = 14) buffer MergeTasks         { uint mergeTasksHead; uint mergeTasksTail; uvec2 mergeTasks[];};
    // layout(std430, binding = 15) buffer MergeBodyLocks     { uint bodyLocks[]; };

    public static SSBO SSBO_LEAF_NODES;
    public static SSBO SSBO_INTERNAL_NODES;
    public static SSBO SSBO_SIMULATION_VALUES;
    public static SSBO SSBO_FIXED_BODIES_IN;
    public static SSBO SSBO_FIXED_BODIES_OUT;
    public static SSBO SSBO_FIXED_MORTON_IN;
    public static SSBO SSBO_FIXED_MORTON_OUT;
    public static SSBO SSBO_FIXED_INDEX_IN;
    public static SSBO SSBO_FIXED_INDEX_OUT;
    public static SSBO SSBO_FIXED_TREE_WORK_QUEUE_IN;
    public static SSBO SSBO_FIXED_TREE_WORK_QUEUE_OUT;
    public static SSBO SSBO_RADIX_WG_HIST;
    public static SSBO SSBO_RADIX_WG_SCANNED;
    public static SSBO SSBO_RADIX_BUCKET_TOTALS;
    public static SSBO SSBO_MERGE_QUEUE;
    public static SSBO SSBO_MERGE_BODY_LOCKS;

    public static SSBO SSBO_SWAPPING_BODIES_IN;
    public static SSBO SSBO_SWAPPING_BODIES_OUT;
    public static SSBO SSBO_SWAPPING_MORTON_IN;
    public static SSBO SSBO_SWAPPING_MORTON_OUT;
    public static SSBO SSBO_SWAPPING_INDEX_IN;
    public static SSBO SSBO_SWAPPING_INDEX_OUT;
    public static SSBO SSBO_SWAPPING_TREE_WORK_QUEUE_IN;
    public static SSBO SSBO_SWAPPING_TREE_WORK_QUEUE_OUT;

    // Compute Programs
    public static ComputeProgram COMPUTE_INIT; // bh_init.comp
    public static ComputeProgram COMPUTE_UPDATE; // bh_update.comp
    public static ComputeProgram COMPUTE_MORTON_AABB_REPOPULATE; // bh_morton.comp
    public static ComputeProgram COMPUTE_MORTON_AABB_COLLAPSE; // bh_morton.comp
    public static ComputeProgram COMPUTE_MORTON_ENCODE; // bh_morton.comp
    public static ComputeProgram COMPUTE_DEAD_COUNT; // bh_dead.comp
    public static ComputeProgram COMPUTE_DEAD_EXCLUSIVE_SCAN; // bh_dead.comp
    public static ComputeProgram COMPUTE_DEAD_SCATTER; // bh_dead.comp
    public static ComputeProgram COMPUTE_RADIX_HISTOGRAM; // bh_radix.comp
    public static ComputeProgram COMPUTE_RADIX_BUCKET_SCAN; // bh_radix.comp
    public static ComputeProgram COMPUTE_RADIX_GLOBAL_SCAN; // bh_radix.comp
    public static ComputeProgram COMPUTE_RADIX_SCATTER; // bh_radix.comp
    public static ComputeProgram COMPUTE_TREE_BUILD; // bh_tree.comp
    public static ComputeProgram COMPUTE_TREE_INIT_LEAVES; // bh_reduce.comp
    public static ComputeProgram COMPUTE_TREE_PROPAGATE_NODES; // bh_reduce.comp
    public static ComputeProgram COMPUTE_FORCE_COMPUTE; // bh_force.comp
    public static ComputeProgram COMPUTE_MERGE_BODIES; // bh_merge.comp
    public static ComputeProgram COMPUTE_DEBUG; // bh_debug.comp

    // Compute Uniforms
    public static Uniform<Float> UNIFORM_CAMERA_SCALE;
    public static Uniform<Integer> UNIFORM_NUM_WORK_GROUPS;
    public static Uniform<Float> UNIFORM_SOFTENING;
    public static Uniform<Float> UNIFORM_THETA;
    public static Uniform<Float> UNIFORM_DT;
    public static Uniform<Float> UNIFORM_ELASTICITY;
    public static Uniform<Float> UNIFORM_RESTITUTION;
    public static Uniform<Integer> UNIFORM_MERGING_COLLISION_OR_NEITHER;
    public static Uniform<Integer> UNIFORM_PASS_SHIFT;
    public static Uniform<Boolean> UNIFORM_RESET_VALUES_OR_DECREMENT_DEAD_BODIES;
    public static Uniform<Boolean> UNIFORM_WRAP_AROUND;
    public static Uniform<Integer> UNIFORM_STATIC_OR_DYNAMIC;




    // Render Uniforms
    public static Uniform<Matrix4f> UNIFORM_MVP;
    public static Uniform<Float> UNIFORM_POINT_SCALE;
    public static Uniform<Vector3f> UNIFORM_CAMERA_POS;
    public static Uniform<Vector3f> UNIFORM_CAMERA_FRONT;
    public static Uniform<Float> UNIFORM_FOV_Y;
    public static Uniform<Float> UNIFORM_ASPECT;
    public static Uniform<Integer> UNIFORM_PASS;
    public static Uniform<Matrix4f> UNIFORM_PROJ;
    public static Uniform<Matrix4f> UNIFORM_MODEL_VIEW;
    public static Uniform<Float> UNIFORM_RADIUS_SCALE;
    public static Uniform<Vector2i> UNIFORM_MIN_MAX_DEPTH;
    public static Uniform<Integer> UNIFORM_RELATIVE_TO;


    // Render Programs
    public static RenderProgram RENDER_POINTS; // points program
    public static RenderProgram RENDER_IMPOSTOR; // point-sprite impostor spheres
    public static RenderProgram RENDER_SPHERE;   // instanced mesh spheres
    public static RenderProgram RENDER_REGIONS; // regions


    
    /**
     * Initializes the GPU data and programs for the given GPU simulation.
     * @param gpuSimulation the GPU simulation
     */
    public static void initGPU(GPUSimulation gpuSimulation) {

        BarnesHut barnesHut = gpuSimulation.getBarnesHut();
        Render render = gpuSimulation.getRender();
        float[][] bounds = gpuSimulation.getBarnesHut().getBounds();
        PlanetGenerator planetGenerator = gpuSimulation.getPlanetGenerator();
        GPU.initialNumBodies = gpuSimulation.initialNumBodies();
        UnitSet units = gpuSimulation.getUnitSet();

        initComputeUniforms(barnesHut);

        initComputeSSBOs(planetGenerator, bounds, units);
        initComputeSwappingBuffers();
        initComputePrograms(barnesHut);
        initRenderUniforms(render);
        initRenderPrograms(render);
    }


    /**
     * Initialize the SSBOs.
     * Gives the SSBOs their correct sizes or data functions, and 
     * the general layout of the SSBOs.
     * @param planetGenerator the planet generator
     * @param bounds the bounds of the simulation
     * @param units the units of the simulation
     */
    private static void initComputeSSBOs(PlanetGenerator planetGenerator, float[][] bounds, UnitSet units) {
        // Compute sizes (use long to avoid overflow)
        long nodesSize = Node.STRUCT_SIZE * Integer.BYTES * (long) (numBodies());
        long maxBlock = glGetInteger(GL_MAX_SHADER_STORAGE_BLOCK_SIZE);
        System.out.println("Node space left: " + (maxBlock - nodesSize));
        System.out.println("Max block: " + maxBlock);

        if (nodesSize > maxBlock) {

            throw new RuntimeException("Not enough node space (your simulation is too large: " + numBodies() + ") Approximate max bodies: " + (maxBlock / (Node.STRUCT_SIZE * Integer.BYTES)));
        }

        GPUSimulation.checkGLError("before initComputeSSBOs");
        
        //this is a list of all the SSBOs that are used in the algorithm.
        GPU.SSBOS = new HashMap<>();

        //These are the fixed SSBOs that point to the bodies in and out buffers.
        System.out.println("numBodies: " + numBodies());
        SSBO_FIXED_BODIES_IN = new SSBO(SSBO.BODIES_IN_BINDING, () -> {
            return numBodies()*Body.STRUCT_SIZE*Float.BYTES;
        }, "SSBO_FIXED_BODIES_IN", new GLSLVariable(Body.bodyStruct,"BodiesIn",numBodies()));
        GPU.SSBOS.put(SSBO_FIXED_BODIES_IN.getName(), SSBO_FIXED_BODIES_IN);

        SSBO_FIXED_BODIES_OUT = new SSBO(SSBO.BODIES_OUT_BINDING, () -> {
            return numBodies()*Body.STRUCT_SIZE*Float.BYTES;
        }, "SSBO_FIXED_BODIES_OUT", new GLSLVariable(Body.bodyStruct,"BodiesOut",numBodies()));
        GPU.SSBOS.put(SSBO_FIXED_BODIES_OUT.getName(), SSBO_FIXED_BODIES_OUT);

        //These are the fixed SSBOs that point to the morton and index buffers.
        //They are intialized with the correct sizes.
        SSBO_FIXED_MORTON_IN = new SSBO(SSBO.MORTON_IN_BINDING, () -> {
            return numBodies() * Long.BYTES;
        }, "SSBO_FIXED_MORTON_IN", new GLSLVariable(VariableType.UINT64,"MortonIn", numBodies()));
        GPU.SSBOS.put(SSBO_FIXED_MORTON_IN.getName(), SSBO_FIXED_MORTON_IN);

        SSBO_FIXED_INDEX_IN = new SSBO(SSBO.INDEX_IN_BINDING, () -> {
            return numBodies() * Integer.BYTES;
        }, "SSBO_FIXED_INDEX_IN", new GLSLVariable(VariableType.UINT,"IndexIn", numBodies()));
        GPU.SSBOS.put(SSBO_FIXED_INDEX_IN.getName(), SSBO_FIXED_INDEX_IN);

        SSBO_LEAF_NODES = new SSBO(SSBO.LEAF_NODES_BINDING, () -> {
            return numBodies() * Node.STRUCT_SIZE * Integer.BYTES;
        }, "SSBO_LEAF_NODES", new GLSLVariable(Node.nodeStruct,"LeafNodes", numBodies()));
        GPU.SSBOS.put(SSBO_LEAF_NODES.getName(), SSBO_LEAF_NODES);

        SSBO_INTERNAL_NODES = new SSBO(SSBO.INTERNAL_NODES_BINDING, () -> {
            return (numBodies() - 1) * Node.STRUCT_SIZE * Integer.BYTES;
        }, "SSBO_INTERNAL_NODES", new GLSLVariable(Node.nodeStruct,"InternalNodes", numBodies() - 1));
        GPU.SSBOS.put(SSBO_INTERNAL_NODES.getName(), SSBO_INTERNAL_NODES);

        //This is the SSBO that holds values that are used in different shaders
        SSBO_SIMULATION_VALUES = new SSBO(SSBO.SIMULATION_VALUES_BINDING, () -> {
            return packValues(numBodies(), bounds, units);
        },"SSBO_SIMULATION_VALUES", new GLSLVariable(new GLSLVariable[] {
            new GLSLVariable(VariableType.UINT,"numBodies", 1), 
            new GLSLVariable(VariableType.UINT,"initialNumBodies", 1), 
            new GLSLVariable(VariableType.UINT,"justDied", 1), 
            new GLSLVariable(VariableType.UINT,"merged", 1), 
            new GLSLVariable(VariableType.UINT,"outOfBounds", 1), 
            new GLSLVariable(VariableType.UINT,"pad0", 1), 
            new GLSLVariable(VariableType.UINT,"pad1", 1), 
            new GLSLVariable(VariableType.UINT,"pad2", 1), 
            new GLSLVariable(new GLSLVariable[] {
                new GLSLVariable(VariableType.FLOAT,"minCorner", 3), new GLSLVariable(VariableType.PADDING),
                new GLSLVariable(VariableType.FLOAT,"maxCorner", 3), new GLSLVariable(VariableType.PADDING)},"bounds"), 
            new GLSLVariable(new GLSLVariable[] {
                new GLSLVariable(VariableType.FLOAT,"mass", 1), 
                new GLSLVariable(VariableType.FLOAT,"density", 1), 
                new GLSLVariable(VariableType.FLOAT,"len", 1), 
                new GLSLVariable(VariableType.FLOAT,"time", 1), 
                new GLSLVariable(VariableType.FLOAT,"gravitationalConstant", 1), 
                new GLSLVariable(VariableType.FLOAT,"bodyLengthInSimulationLengthsConstant", 1), 
                new GLSLVariable(VariableType.PADDING),
                new GLSLVariable(VariableType.PADDING)}, "units"),
            new GLSLVariable(VariableType.UINT,"uintDebug", 100), 
            new GLSLVariable(VariableType.FLOAT,"floatDebug", 100)},"SimulationValues"));
        GPU.SSBOS.put(SSBO_SIMULATION_VALUES.getName(), SSBO_SIMULATION_VALUES);

        //This is the SSBO that holds the histogram of the radix sort.
        SSBO_RADIX_WG_HIST = new SSBO(SSBO.RADIX_WG_HIST_BINDING, 
            () -> {return numGroups() * (NUM_RADIX_BUCKETS) * Integer.BYTES;}, 
            "SSBO_WG_HIST", new GLSLVariable(VariableType.UINT,"WGHist", numGroups() * (NUM_RADIX_BUCKETS)));
        GPU.SSBOS.put(SSBO_RADIX_WG_HIST.getName(), SSBO_RADIX_WG_HIST);

        //This is the SSBO that holds the scanned histogram of the radix sort.
        SSBO_RADIX_WG_SCANNED = new SSBO(SSBO.RADIX_WG_SCANNED_BINDING, () -> {
            return Integer.BYTES + numGroups() * (NUM_RADIX_BUCKETS) * Integer.BYTES + Integer.BYTES;
        }, "SSBO_WG_SCANNED", new GLSLVariable(VariableType.UINT,"WGScanned", numGroups() * (NUM_RADIX_BUCKETS) + 1));
        GPU.SSBOS.put(SSBO_RADIX_WG_SCANNED.getName(), SSBO_RADIX_WG_SCANNED);

        //This is the SSBO that holds the total number of bodies in each bucket of the radix sort.
        SSBO_RADIX_BUCKET_TOTALS = new SSBO(SSBO.RADIX_BUCKET_TOTALS_BINDING, () -> {
            return NUM_RADIX_BUCKETS * Integer.BYTES * 2;
        }, "SSBO_BUCKET_TOTALS", new GLSLVariable(new GLSLVariable[] {
            new GLSLVariable(VariableType.UINT,"BucketTotals", NUM_RADIX_BUCKETS), 
            new GLSLVariable(VariableType.UINT,"GlobalBase", NUM_RADIX_BUCKETS)}));
        GPU.SSBOS.put(SSBO_RADIX_BUCKET_TOTALS.getName(), SSBO_RADIX_BUCKET_TOTALS);

        //These are the fixed SSBOs that point to the morton and index buffers after the radix sort.
        //They are intialized with the correct sizes.
        SSBO_FIXED_MORTON_OUT = new SSBO(SSBO.MORTON_OUT_BINDING, () -> {
            return numBodies() * Long.BYTES;
        }, "SSBO_FIXED_MORTON_OUT", new GLSLVariable(VariableType.UINT64,"MortonOut", numBodies()));
        GPU.SSBOS.put(SSBO_FIXED_MORTON_OUT.getName(), SSBO_FIXED_MORTON_OUT);

        SSBO_FIXED_INDEX_OUT = new SSBO(SSBO.INDEX_OUT_BINDING, () -> {
            return numBodies() * Integer.BYTES;
        }, "SSBO_FIXED_INDEX_OUT", new GLSLVariable(VariableType.UINT,"IndexOut", numBodies()));
        GPU.SSBOS.put(SSBO_FIXED_INDEX_OUT.getName(), SSBO_FIXED_INDEX_OUT);

        //This is the SSBO that holds the work queue.
        //It is intialized with the correct sizes.
        SSBO_FIXED_TREE_WORK_QUEUE_IN = new SSBO(SSBO.PROPAGATE_WORK_QUEUE_IN_BINDING, () -> {
            return (4 + numBodies()) * Integer.BYTES;
        }, "SSBO_FIXED_TREE_WORK_QUEUE_IN", new GLSLVariable(new GLSLVariable[] {
            new GLSLVariable(VariableType.UINT,"HeadIn", 1), 
            new GLSLVariable(VariableType.UINT,"TailIn", 1), 
            new GLSLVariable(VariableType.UINT,"ItemsIn", numBodies())}));
        GPU.SSBOS.put(SSBO_FIXED_TREE_WORK_QUEUE_IN.getName(), SSBO_FIXED_TREE_WORK_QUEUE_IN);

        //This is the SSBO that holds the work queue for the second pass.
        //It is intialized with the correct sizes.
        SSBO_FIXED_TREE_WORK_QUEUE_OUT = new SSBO(SSBO.PROPAGATE_WORK_QUEUE_OUT_BINDING, () -> {
            return (4 + numBodies()) * Integer.BYTES;
        }, "SSBO_FIXED_TREE_WORK_QUEUE_OUT", new GLSLVariable(new GLSLVariable[] {
            new GLSLVariable(VariableType.UINT,"HeadOut", 1), 
            new GLSLVariable(VariableType.UINT,"TailOut", 1), 
            new GLSLVariable(VariableType.UINT,"ItemsOut", numBodies())}));
        GPU.SSBOS.put(SSBO_FIXED_TREE_WORK_QUEUE_OUT.getName(), SSBO_FIXED_TREE_WORK_QUEUE_OUT);

        SSBO_MERGE_QUEUE = new SSBO(SSBO.MERGE_QUEUE_BINDING, () -> {
            return Math.max(2*Integer.BYTES, 2*Integer.BYTES+numBodies() * 2 * Integer.BYTES);
        }, "SSBO_MERGE_QUEUE", new GLSLVariable(new GLSLVariable[] {
            new GLSLVariable(VariableType.UINT,"MergeTasksHead", 1), 
            new GLSLVariable(VariableType.UINT,"MergeTasksTail", 1), 
            new GLSLVariable(VariableType.UINT,"MergeTasks", numBodies() * 2)}));
        GPU.SSBOS.put(SSBO_MERGE_QUEUE.getName(), SSBO_MERGE_QUEUE);

        SSBO_MERGE_BODY_LOCKS = new SSBO(SSBO.MERGE_BODY_LOCKS_BINDING, () -> {
            return numBodies() * Integer.BYTES;
        }, "SSBO_BODY_LOCKS", new GLSLVariable(VariableType.UINT,"BodyLocks", numBodies()));
        GPU.SSBOS.put(SSBO_MERGE_BODY_LOCKS.getName(), SSBO_MERGE_BODY_LOCKS);

        GPUSimulation.checkGLError("after initComputeSSBOs");

        for (SSBO ssbo : GPU.SSBOS.values()) {
            System.out.println("Creating buffer for " + ssbo.getName());
            ssbo.createBufferData();
            GPUSimulation.checkGLError("after createBufferData for " + ssbo.getName());
        }

        GPUSimulation.checkGLError("after createBufferData");

        uploadPlanetsData(planetGenerator, SSBO_FIXED_BODIES_IN);
        




    }

        /**
     * Initialize the compute swapping buffers.
     * Used to swap the bodies, morton, and index buffers.
     */
    private static void initComputeSwappingBuffers() {
        // Create Swapping SSBOs.
        // These are used as a double buffer for bodies 
        SSBO_SWAPPING_BODIES_IN = SSBO_FIXED_BODIES_IN;
        SSBO_SWAPPING_BODIES_IN.setName("SSBO_SWAPPING_BODIES_IN");
        GPU.SSBOS.put(SSBO_SWAPPING_BODIES_IN.getName(), SSBO_SWAPPING_BODIES_IN);
        SSBO_SWAPPING_BODIES_OUT = SSBO_FIXED_BODIES_OUT;
        SSBO_SWAPPING_BODIES_OUT.setName("SSBO_SWAPPING_BODIES_OUT");
        GPU.SSBOS.put(SSBO_SWAPPING_BODIES_OUT.getName(), SSBO_SWAPPING_BODIES_OUT);

        // These exist to do the radix sort and dead body paritioning.

        SSBO_SWAPPING_MORTON_IN = SSBO_FIXED_MORTON_IN;
        SSBO_SWAPPING_MORTON_IN.setName("SSBO_SWAPPING_MORTON_IN");
        GPU.SSBOS.put(SSBO_SWAPPING_MORTON_IN.getName(), SSBO_SWAPPING_MORTON_IN);
        SSBO_SWAPPING_MORTON_OUT = SSBO_FIXED_MORTON_OUT;
        SSBO_SWAPPING_MORTON_OUT.setName("SSBO_SWAPPING_MORTON_OUT");
        GPU.SSBOS.put(SSBO_SWAPPING_MORTON_OUT.getName(), SSBO_SWAPPING_MORTON_OUT);
        SSBO_SWAPPING_INDEX_IN = SSBO_FIXED_INDEX_IN;
        SSBO_SWAPPING_INDEX_IN.setName("SSBO_SWAPPING_INDEX_IN");
        GPU.SSBOS.put(SSBO_SWAPPING_INDEX_IN.getName(), SSBO_SWAPPING_INDEX_IN);
        SSBO_SWAPPING_INDEX_OUT = SSBO_FIXED_INDEX_OUT;
        SSBO_SWAPPING_INDEX_OUT.setName("SSBO_SWAPPING_INDEX_OUT");
        GPU.SSBOS.put(SSBO_SWAPPING_INDEX_OUT.getName(), SSBO_SWAPPING_INDEX_OUT);

        // These are used for the work queue for propagating node data up the tree.
        SSBO_SWAPPING_TREE_WORK_QUEUE_IN = SSBO_FIXED_TREE_WORK_QUEUE_IN;
        SSBO_SWAPPING_TREE_WORK_QUEUE_IN.setName("SSBO_SWAPPING_TREE_WORK_QUEUE_IN");
        GPU.SSBOS.put(SSBO_SWAPPING_TREE_WORK_QUEUE_IN.getName(), SSBO_SWAPPING_TREE_WORK_QUEUE_IN);
        SSBO_SWAPPING_TREE_WORK_QUEUE_OUT = SSBO_FIXED_TREE_WORK_QUEUE_OUT;
        SSBO_SWAPPING_TREE_WORK_QUEUE_OUT.setName("SSBO_SWAPPING_TREE_WORK_QUEUE_OUT");
        GPU.SSBOS.put(SSBO_SWAPPING_TREE_WORK_QUEUE_OUT.getName(), SSBO_SWAPPING_TREE_WORK_QUEUE_OUT);
    }

    /**
     * Initialize the compute uniforms. These are defined in bh_common.comp for the most part.
     * @param barnesHut the Barnes-Hut object
     */
    private static void initComputeUniforms(BarnesHut barnesHut) {
        //Initialize the uniforms.
        GPU.UNIFORMS = new HashMap<>();

        UNIFORM_CAMERA_SCALE = new Uniform<Float>("cameraScale", () -> {
            return Settings.getInstance().getCameraScale();
        }, VariableType.FLOAT);

        GPU.UNIFORMS.put(UNIFORM_CAMERA_SCALE.getName(), UNIFORM_CAMERA_SCALE);

        UNIFORM_NUM_WORK_GROUPS = new Uniform<Integer>("numWorkGroups", () -> {
            return numGroups();
        }, VariableType.UINT);

        GPU.UNIFORMS.put(UNIFORM_NUM_WORK_GROUPS.getName(), UNIFORM_NUM_WORK_GROUPS);

        UNIFORM_SOFTENING = new Uniform<Float>("softening", () -> {
            return Settings.getInstance().getSoftening();
        }, VariableType.FLOAT);

        GPU.UNIFORMS.put(UNIFORM_SOFTENING.getName(), UNIFORM_SOFTENING);

        UNIFORM_THETA = new Uniform<Float>("theta", () -> {
            return Settings.getInstance().getTheta();
        }, VariableType.FLOAT);

        GPU.UNIFORMS.put(UNIFORM_THETA.getName(), UNIFORM_THETA);

        UNIFORM_DT = new Uniform<Float>("dt", () -> {
            return Settings.getInstance().getDt();
        }, VariableType.FLOAT);

        GPU.UNIFORMS.put(UNIFORM_DT.getName(), UNIFORM_DT);

        UNIFORM_ELASTICITY = new Uniform<Float>("elasticity", () -> {
            return (float)Settings.getInstance().getElasticity();
        }, VariableType.FLOAT);

        GPU.UNIFORMS.put(UNIFORM_ELASTICITY.getName(), UNIFORM_ELASTICITY);

        UNIFORM_RESTITUTION = new Uniform<Float>("restitution", () -> {
            return 0.2f;
        }, VariableType.FLOAT);

        GPU.UNIFORMS.put(UNIFORM_RESTITUTION.getName(), UNIFORM_RESTITUTION);

        UNIFORM_PASS_SHIFT = new Uniform<Integer>("passShift", () -> {
            return barnesHut.radixSortPassShift;
        }, VariableType.UINT);

        GPU.UNIFORMS.put(UNIFORM_PASS_SHIFT.getName(), UNIFORM_PASS_SHIFT);

        UNIFORM_MERGING_COLLISION_OR_NEITHER = new Uniform<Integer>("mergingCollisionOrNeither", () -> {
            return Settings.getInstance().getSelectedIndexMergingCollisionOrNeither();
        }, VariableType.UINT);

        GPU.UNIFORMS.put(UNIFORM_MERGING_COLLISION_OR_NEITHER.getName(), UNIFORM_MERGING_COLLISION_OR_NEITHER);

        UNIFORM_RESET_VALUES_OR_DECREMENT_DEAD_BODIES = new Uniform<Boolean>("resetValuesOrDecrementDeadBodies", () -> {
            return barnesHut.resetValuesOrDecrementDeadBodies ? true : false;
        }, VariableType.BOOL);

        GPU.UNIFORMS.put(UNIFORM_RESET_VALUES_OR_DECREMENT_DEAD_BODIES.getName(), UNIFORM_RESET_VALUES_OR_DECREMENT_DEAD_BODIES);

        UNIFORM_WRAP_AROUND = new Uniform<Boolean>("wrapAround", () -> {
            return Settings.getInstance().isWrapAround();
        }, VariableType.BOOL);

        GPU.UNIFORMS.put(UNIFORM_WRAP_AROUND.getName(), UNIFORM_WRAP_AROUND);

        UNIFORM_STATIC_OR_DYNAMIC = new Uniform<Integer>("staticOrDynamic", () -> {
            return Settings.getInstance().getSelectedIndexDynamic();
        }, VariableType.UINT);

        GPU.UNIFORMS.put(UNIFORM_STATIC_OR_DYNAMIC.getName(), UNIFORM_STATIC_OR_DYNAMIC);

    }

    /**
     * Initialize the compute shaders. The names are defined in bh_main.comp. For more information on the shaders, see the glsl code in the shaders folder.
     * @param barnesHut the Barnes-Hut object
     */
    private static void initComputePrograms(BarnesHut barnesHut) {

        GPU.COMPUTE_PROGRAMS = new HashMap<>();
        COMPUTE_INIT = new ComputeProgram("COMPUTE_INIT");
        COMPUTE_INIT.setUniforms(new Uniform[] {
        });
        COMPUTE_INIT.setSSBOs(new SSBO[] {
            GPU.SSBO_SIMULATION_VALUES,
            GPU.SSBO_SWAPPING_INDEX_IN,
            GPU.SSBO_FIXED_BODIES_IN,
            GPU.SSBO_FIXED_BODIES_OUT
        });
        COMPUTE_INIT.setXWorkGroupsFunction(() -> {
            return numGroups();
        });
        GPU.COMPUTE_PROGRAMS.put(COMPUTE_INIT.getProgramName(), COMPUTE_INIT);
        COMPUTE_MORTON_AABB_REPOPULATE = new ComputeProgram("COMPUTE_MORTON_AABB_REPOPULATE");
        COMPUTE_MORTON_AABB_REPOPULATE.setUniforms(new Uniform[] {

        });
        COMPUTE_MORTON_AABB_REPOPULATE.setSSBOs(new SSBO[] {
            GPU.SSBO_SIMULATION_VALUES,
            GPU.SSBO_SWAPPING_BODIES_IN,
            GPU.SSBO_SWAPPING_INDEX_IN,
            GPU.SSBO_INTERNAL_NODES,
        });
        COMPUTE_MORTON_AABB_REPOPULATE.setXWorkGroupsFunction(() -> {
            return numGroups();
        });
        GPU.COMPUTE_PROGRAMS.put(COMPUTE_MORTON_AABB_REPOPULATE.getProgramName(), COMPUTE_MORTON_AABB_REPOPULATE);
        COMPUTE_MORTON_AABB_COLLAPSE = new ComputeProgram("COMPUTE_MORTON_AABB_COLLAPSE");
        COMPUTE_MORTON_AABB_COLLAPSE.setUniforms(new Uniform[] {
            UNIFORM_NUM_WORK_GROUPS
        });
        COMPUTE_MORTON_AABB_COLLAPSE.setSSBOs(new SSBO[] {
            GPU.SSBO_SIMULATION_VALUES,
            GPU.SSBO_SWAPPING_BODIES_IN,
            GPU.SSBO_SWAPPING_INDEX_IN,
            GPU.SSBO_INTERNAL_NODES,
        });
        COMPUTE_MORTON_AABB_COLLAPSE.setXWorkGroupsFunction(() -> {
            return 1;
        });
        GPU.COMPUTE_PROGRAMS.put(COMPUTE_MORTON_AABB_COLLAPSE.getProgramName(), COMPUTE_MORTON_AABB_COLLAPSE);
        COMPUTE_MORTON_ENCODE = new ComputeProgram("COMPUTE_MORTON_ENCODE");
        COMPUTE_MORTON_ENCODE.setUniforms(new Uniform[] {
        });
        COMPUTE_MORTON_ENCODE.setSSBOs(new SSBO[] {
            GPU.SSBO_SIMULATION_VALUES,
            GPU.SSBO_SWAPPING_MORTON_IN,
            GPU.SSBO_SWAPPING_BODIES_IN,
            GPU.SSBO_SWAPPING_INDEX_IN,

        });
        COMPUTE_MORTON_ENCODE.setXWorkGroupsFunction(() -> {
            return numGroups();
        });
        GPU.COMPUTE_PROGRAMS.put(COMPUTE_MORTON_ENCODE.getProgramName(), COMPUTE_MORTON_ENCODE);   
        COMPUTE_DEAD_COUNT = new ComputeProgram("COMPUTE_DEAD_COUNT");
        COMPUTE_DEAD_COUNT.setUniforms(new Uniform[] {
            UNIFORM_NUM_WORK_GROUPS
        });
        COMPUTE_DEAD_COUNT.setSSBOs(new SSBO[] {
            GPU.SSBO_SIMULATION_VALUES,
            GPU.SSBO_RADIX_WG_HIST,
            GPU.SSBO_SWAPPING_BODIES_IN,
            GPU.SSBO_SWAPPING_BODIES_OUT,
            GPU.SSBO_SWAPPING_INDEX_IN,
            GPU.SSBO_SWAPPING_MORTON_IN,
        });
        COMPUTE_DEAD_COUNT.setXWorkGroupsFunction(() -> {
            return numGroups();
        });
        
        GPU.COMPUTE_PROGRAMS.put(COMPUTE_DEAD_COUNT.getProgramName(), COMPUTE_DEAD_COUNT);
        COMPUTE_DEAD_EXCLUSIVE_SCAN = new ComputeProgram("COMPUTE_DEAD_EXCLUSIVE_SCAN");
        COMPUTE_DEAD_EXCLUSIVE_SCAN.setUniforms(new Uniform[] {
            UNIFORM_NUM_WORK_GROUPS
        });
        COMPUTE_DEAD_EXCLUSIVE_SCAN.setSSBOs(new SSBO[] {
            GPU.SSBO_SIMULATION_VALUES,
            GPU.SSBO_RADIX_WG_HIST,
            GPU.SSBO_RADIX_WG_SCANNED,
            GPU.SSBO_SWAPPING_BODIES_IN,
            GPU.SSBO_SWAPPING_INDEX_IN,
            GPU.SSBO_SWAPPING_MORTON_IN,
        });
        COMPUTE_DEAD_EXCLUSIVE_SCAN.setXWorkGroupsFunction(() -> {
            return 1;
        });
        
        GPU.COMPUTE_PROGRAMS.put(COMPUTE_DEAD_EXCLUSIVE_SCAN.getProgramName(), COMPUTE_DEAD_EXCLUSIVE_SCAN);
        COMPUTE_DEAD_SCATTER = new ComputeProgram("COMPUTE_DEAD_SCATTER");
        COMPUTE_DEAD_SCATTER.setUniforms(new Uniform[] {
            UNIFORM_NUM_WORK_GROUPS
        });
        COMPUTE_DEAD_SCATTER.setSSBOs(new SSBO[] {
            GPU.SSBO_SIMULATION_VALUES,
            GPU.SSBO_RADIX_WG_SCANNED,
            GPU.SSBO_SWAPPING_BODIES_IN,
            GPU.SSBO_SWAPPING_INDEX_IN,
            GPU.SSBO_SWAPPING_MORTON_IN,
            GPU.SSBO_SWAPPING_MORTON_OUT,
            GPU.SSBO_SWAPPING_INDEX_OUT,
        });
        COMPUTE_DEAD_SCATTER.setXWorkGroupsFunction(() -> {
            return numGroups();
        });
        
        GPU.COMPUTE_PROGRAMS.put(COMPUTE_DEAD_SCATTER.getProgramName(), COMPUTE_DEAD_SCATTER);
        COMPUTE_RADIX_HISTOGRAM = new ComputeProgram("COMPUTE_RADIX_HIST");
        COMPUTE_RADIX_HISTOGRAM.setUniforms(new Uniform[] {

            UNIFORM_PASS_SHIFT,
            UNIFORM_NUM_WORK_GROUPS
        });
        COMPUTE_RADIX_HISTOGRAM.setSSBOs(new SSBO[] {
            GPU.SSBO_SIMULATION_VALUES,
            GPU.SSBO_SWAPPING_MORTON_IN,
            GPU.SSBO_SWAPPING_INDEX_IN,
            GPU.SSBO_RADIX_WG_HIST,
            GPU.SSBO_SWAPPING_BODIES_IN,
        });
        COMPUTE_RADIX_HISTOGRAM.setXWorkGroupsFunction(() -> {
            return numGroups();
        });
        
        GPU.COMPUTE_PROGRAMS.put(COMPUTE_RADIX_HISTOGRAM.getProgramName(), COMPUTE_RADIX_HISTOGRAM);
        COMPUTE_RADIX_BUCKET_SCAN = new ComputeProgram("COMPUTE_RADIX_BUCKET_SCAN");
        COMPUTE_RADIX_BUCKET_SCAN.setUniforms(new Uniform[] {
            UNIFORM_NUM_WORK_GROUPS
        });
        COMPUTE_RADIX_BUCKET_SCAN.setSSBOs(new SSBO[] {
            GPU.SSBO_SIMULATION_VALUES,
            GPU.SSBO_RADIX_WG_HIST,
            GPU.SSBO_RADIX_WG_SCANNED,
            GPU.SSBO_RADIX_BUCKET_TOTALS,
            GPU.SSBO_SWAPPING_BODIES_IN,
        });
        COMPUTE_RADIX_BUCKET_SCAN.setXWorkGroupsFunction(() -> {
            return NUM_RADIX_BUCKETS;
        });
        
        GPU.COMPUTE_PROGRAMS.put(COMPUTE_RADIX_BUCKET_SCAN.getProgramName(), COMPUTE_RADIX_BUCKET_SCAN);    
        COMPUTE_RADIX_GLOBAL_SCAN = new ComputeProgram("COMPUTE_RADIX_GLOBAL_SCAN");
        COMPUTE_RADIX_GLOBAL_SCAN.setUniforms(new Uniform[] {
            UNIFORM_NUM_WORK_GROUPS
        });
        COMPUTE_RADIX_GLOBAL_SCAN.setSSBOs(new SSBO[] {
            GPU.SSBO_SIMULATION_VALUES,
            GPU.SSBO_RADIX_BUCKET_TOTALS,
            GPU.SSBO_SWAPPING_BODIES_IN,
        });
        COMPUTE_RADIX_GLOBAL_SCAN.setXWorkGroupsFunction(() -> {
            return NUM_RADIX_BUCKETS;
        });
        
        GPU.COMPUTE_PROGRAMS.put(COMPUTE_RADIX_GLOBAL_SCAN.getProgramName(), COMPUTE_RADIX_GLOBAL_SCAN);
        COMPUTE_RADIX_SCATTER = new ComputeProgram("COMPUTE_RADIX_SCATTER");

        COMPUTE_RADIX_SCATTER.setUniforms(new Uniform[] {
            UNIFORM_PASS_SHIFT,
            UNIFORM_NUM_WORK_GROUPS
        });

        COMPUTE_RADIX_SCATTER.setSSBOs(new SSBO[] {
            GPU.SSBO_SIMULATION_VALUES,
            GPU.SSBO_SWAPPING_MORTON_IN,
            GPU.SSBO_SWAPPING_INDEX_IN,
            GPU.SSBO_RADIX_WG_SCANNED,
            GPU.SSBO_RADIX_BUCKET_TOTALS,
            GPU.SSBO_SWAPPING_MORTON_OUT,
            GPU.SSBO_SWAPPING_INDEX_OUT,
            GPU.SSBO_SWAPPING_BODIES_IN,
        });

        COMPUTE_RADIX_SCATTER.setXWorkGroupsFunction(() -> {
            return numGroups();
        });
        
        GPU.COMPUTE_PROGRAMS.put(COMPUTE_RADIX_SCATTER.getProgramName(), COMPUTE_RADIX_SCATTER); 


        COMPUTE_TREE_BUILD = new ComputeProgram("COMPUTE_TREE_BUILD");
        COMPUTE_TREE_BUILD.setUniforms(new Uniform[] {

        });
        
        COMPUTE_TREE_BUILD.setSSBOs(new SSBO[] {
            GPU.SSBO_SIMULATION_VALUES,
            GPU.SSBO_SWAPPING_MORTON_IN,
            GPU.SSBO_SWAPPING_INDEX_IN,
            GPU.SSBO_INTERNAL_NODES,
            GPU.SSBO_LEAF_NODES,
            GPU.SSBO_SWAPPING_BODIES_IN,
        });

        COMPUTE_TREE_BUILD.setXWorkGroupsFunction(() -> {
            int numInternalNodes = numBodies() - 1;
            int internalNodeGroups = (numInternalNodes + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
            return internalNodeGroups;
        });
        
        GPU.COMPUTE_PROGRAMS.put(COMPUTE_TREE_BUILD.getProgramName(), COMPUTE_TREE_BUILD);
        //Compute COM and Location Kernels

        COMPUTE_TREE_INIT_LEAVES = new ComputeProgram("COMPUTE_TREE_INIT_LEAVES");

        COMPUTE_TREE_INIT_LEAVES.setUniforms(new Uniform[] {

        }); 

        COMPUTE_TREE_INIT_LEAVES.setSSBOs(new SSBO[] {
            GPU.SSBO_SIMULATION_VALUES,
            GPU.SSBO_SWAPPING_BODIES_IN,
            GPU.SSBO_INTERNAL_NODES,
            GPU.SSBO_LEAF_NODES,
            GPU.SSBO_SWAPPING_MORTON_IN,
            GPU.SSBO_SWAPPING_INDEX_IN,
            GPU.SSBO_SWAPPING_TREE_WORK_QUEUE_IN,
        });

        COMPUTE_TREE_INIT_LEAVES.setXWorkGroupsFunction(() -> {
            return numGroups();
        });
        GPU.COMPUTE_PROGRAMS.put(COMPUTE_TREE_INIT_LEAVES.getProgramName(), COMPUTE_TREE_INIT_LEAVES);
        COMPUTE_UPDATE = new ComputeProgram("COMPUTE_UPDATE");

        COMPUTE_UPDATE.setUniforms(new Uniform[] {
            UNIFORM_RESET_VALUES_OR_DECREMENT_DEAD_BODIES
        });

        COMPUTE_UPDATE.setSSBOs(new SSBO[] {
            GPU.SSBO_SIMULATION_VALUES,
            GPU.SSBO_SWAPPING_TREE_WORK_QUEUE_IN,
            GPU.SSBO_MERGE_QUEUE,
            GPU.SSBO_SWAPPING_BODIES_IN,
            GPU.SSBO_SWAPPING_BODIES_OUT
        });

        COMPUTE_UPDATE.setXWorkGroupsFunction(() -> {
            return 1;
        });
        GPU.COMPUTE_PROGRAMS.put(COMPUTE_UPDATE.getProgramName(), COMPUTE_UPDATE);
        COMPUTE_TREE_PROPAGATE_NODES = new ComputeProgram("COMPUTE_TREE_PROPAGATE_NODES");

        COMPUTE_TREE_PROPAGATE_NODES.setUniforms(new Uniform[] {
        });

        COMPUTE_TREE_PROPAGATE_NODES.setSSBOs(new SSBO[] {
            GPU.SSBO_SIMULATION_VALUES,
            GPU.SSBO_INTERNAL_NODES,
            GPU.SSBO_LEAF_NODES,
            GPU.SSBO_SWAPPING_TREE_WORK_QUEUE_IN,
            GPU.SSBO_SWAPPING_TREE_WORK_QUEUE_OUT,
            GPU.SSBO_SWAPPING_BODIES_IN,

            
        });
        COMPUTE_TREE_PROPAGATE_NODES.setXWorkGroupsFunction(() -> {
            int maxPossibleNodes = Math.max(4*WORK_GROUP_SIZE,(int)((numBodies() - 1)/Math.pow(2,barnesHut.COMPropagationPassNumber)));
            int workGroups = (maxPossibleNodes + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
            return workGroups;
        });
        GPU.COMPUTE_PROGRAMS.put(COMPUTE_TREE_PROPAGATE_NODES.getProgramName(), COMPUTE_TREE_PROPAGATE_NODES);
        COMPUTE_FORCE_COMPUTE = new ComputeProgram("COMPUTE_FORCE_COMPUTE");

        COMPUTE_FORCE_COMPUTE.setUniforms(new Uniform[] {
            UNIFORM_THETA,
            UNIFORM_DT,
            UNIFORM_ELASTICITY,
            UNIFORM_WRAP_AROUND,
            UNIFORM_SOFTENING,
            UNIFORM_MERGING_COLLISION_OR_NEITHER,
            UNIFORM_STATIC_OR_DYNAMIC,
        });

        COMPUTE_FORCE_COMPUTE.setSSBOs(new SSBO[] {
            GPU.SSBO_SIMULATION_VALUES,
            GPU.SSBO_SWAPPING_BODIES_IN,
            GPU.SSBO_SWAPPING_BODIES_OUT,
            GPU.SSBO_INTERNAL_NODES,
            GPU.SSBO_LEAF_NODES,
            GPU.SSBO_SWAPPING_INDEX_IN,
            GPU.SSBO_MERGE_QUEUE
        });

        COMPUTE_FORCE_COMPUTE.setXWorkGroupsFunction(() -> {
            return numGroups();
        });
        GPU.COMPUTE_PROGRAMS.put(COMPUTE_FORCE_COMPUTE.getProgramName(), COMPUTE_FORCE_COMPUTE);
        COMPUTE_MERGE_BODIES = new ComputeProgram("COMPUTE_MERGE_BODIES");
        COMPUTE_MERGE_BODIES.setUniforms(new Uniform[] {
            
        });
        COMPUTE_MERGE_BODIES.setSSBOs(new SSBO[] {
            GPU.SSBO_SIMULATION_VALUES,
            GPU.SSBO_SWAPPING_BODIES_IN,
            GPU.SSBO_SWAPPING_BODIES_OUT,
            GPU.SSBO_MERGE_QUEUE,
            GPU.SSBO_MERGE_BODY_LOCKS,
        });
        COMPUTE_MERGE_BODIES.setXWorkGroupsFunction(() -> {
            return numGroups();
        });
        GPU.COMPUTE_PROGRAMS.put(COMPUTE_MERGE_BODIES.getProgramName(), COMPUTE_MERGE_BODIES);
        COMPUTE_DEBUG = new ComputeProgram("COMPUTE_DEBUG");

        COMPUTE_DEBUG.setUniforms(new Uniform[] {

        });

        COMPUTE_DEBUG.setSSBOs(new SSBO[] {
            GPU.SSBO_SIMULATION_VALUES,
            GPU.SSBO_SWAPPING_MORTON_IN,
            GPU.SSBO_SWAPPING_INDEX_IN,
            GPU.SSBO_SWAPPING_BODIES_IN,
        });

        COMPUTE_DEBUG.setXWorkGroupsFunction(() -> {
            return numGroups();
        });

        GPU.COMPUTE_PROGRAMS.put(COMPUTE_DEBUG.getProgramName(), COMPUTE_DEBUG);
    }

    private static void initRenderUniforms(Render render) {
        GPU.UNIFORM_MVP = new Uniform<Matrix4f>("uMVP", () -> {
            return render.getMVP();
        }, VariableType.MAT4); 
        GPU.UNIFORMS.put(UNIFORM_MVP.getName(), UNIFORM_MVP);

        GPU.UNIFORM_POINT_SCALE = new Uniform<Float>("uPointScale", () -> {
            return render.impostorPointScale;
        }, VariableType.FLOAT);
        GPU.UNIFORMS.put(UNIFORM_POINT_SCALE.getName(), UNIFORM_POINT_SCALE);

        GPU.UNIFORM_CAMERA_POS = new Uniform<Vector3f>("uCameraPos", () -> {
            return Settings.getInstance().getCameraPos();
        }, VariableType.VEC3F);
        GPU.UNIFORMS.put(UNIFORM_CAMERA_POS.getName(), UNIFORM_CAMERA_POS);

        GPU.UNIFORM_CAMERA_FRONT = new Uniform<Vector3f>("uCameraFront", () -> {
            return Settings.getInstance().getCameraFront();
        }, VariableType.VEC3F);
        GPU.UNIFORMS.put(UNIFORM_CAMERA_FRONT.getName(), UNIFORM_CAMERA_FRONT);

        GPU.UNIFORM_FOV_Y = new Uniform<Float>("uFovY", () -> {
            return (float)Math.toRadians(Settings.getInstance().getFov());
        }, VariableType.FLOAT);
        GPU.UNIFORMS.put(UNIFORM_FOV_Y.getName(), UNIFORM_FOV_Y);

        GPU.UNIFORM_ASPECT = new Uniform<Float>("uAspect", () -> {
            return (float)Settings.getInstance().getWidth() / (float)Settings.getInstance().getHeight();
        }, VariableType.FLOAT);
        GPU.UNIFORMS.put(UNIFORM_ASPECT.getName(), UNIFORM_ASPECT);

        GPU.UNIFORM_PASS = new Uniform<Integer>("uPass", () -> {
            return render.glowPass ? 1 : 0;
        }, VariableType.INT);
        GPU.UNIFORMS.put(UNIFORM_PASS.getName(), UNIFORM_PASS);

        GPU.UNIFORM_PROJ = new Uniform<Matrix4f>("uProj", () -> {
            return render.projMatrix();
        }, VariableType.MAT4);
        GPU.UNIFORMS.put(UNIFORM_PROJ.getName(), UNIFORM_PROJ);

        GPU.UNIFORM_MODEL_VIEW = new Uniform<Matrix4f>("uModelView", () -> {
            return render.viewMatrix();
        }, VariableType.MAT4);
        GPU.UNIFORMS.put(UNIFORM_MODEL_VIEW.getName(), UNIFORM_MODEL_VIEW);

        GPU.UNIFORM_RADIUS_SCALE = new Uniform<Float>("uRadiusScale", () -> {
            return render.sphereRadiusScale;
        }, VariableType.FLOAT);
        GPU.UNIFORMS.put(UNIFORM_RADIUS_SCALE.getName(), UNIFORM_RADIUS_SCALE);

        GPU.UNIFORM_CAMERA_POS = new Uniform<Vector3f>("uCameraPos", () -> {
            return Settings.getInstance().getCameraPos();
        }, VariableType.VEC3F);
        GPU.UNIFORMS.put(UNIFORM_CAMERA_POS.getName(), UNIFORM_CAMERA_POS);

        GPU.UNIFORM_RELATIVE_TO = new Uniform<Integer>("uRelativeTo", () -> {
            int relativeTo = Settings.getInstance().getRelativeTo();
            if (relativeTo < 0) {
                return 0xFFFFFFFF;
            }
            return relativeTo;
        }, VariableType.UINT);
        GPU.UNIFORMS.put(UNIFORM_RELATIVE_TO.getName(), UNIFORM_RELATIVE_TO);

        
        GPU.UNIFORM_MIN_MAX_DEPTH = new Uniform<Vector2i>("uMinMaxDepth", () -> {
            return new Vector2i(Settings.getInstance().getMinDepth(), Settings.getInstance().getMaxDepth());
        }, VariableType.VEC2I);
        GPU.UNIFORMS.put(UNIFORM_MIN_MAX_DEPTH.getName(), UNIFORM_MIN_MAX_DEPTH);
    }

    private static void initRenderPrograms(Render render) {
        GPU.RENDER_PROGRAMS = new HashMap<>();
        for (GLSLMesh.MeshType mesh : GLSLMesh.MeshType.values()) {
            GLSLMesh.reInitializeMesh(mesh);
        }

        // Create points render program
        GPU.RENDER_POINTS = new RenderProgram("points", GLSLMesh.MeshType.POINTS, GPU.initialNumBodies);
        GPU.RENDER_POINTS.setUniforms(new Uniform[] {
            GPU.UNIFORM_MVP,
            GPU.UNIFORM_CAMERA_SCALE,
        });
        GPU.RENDER_POINTS.setSSBOs(new SSBO[] {
            GPU.SSBO_SWAPPING_BODIES_IN,
            GPU.SSBO_SIMULATION_VALUES
        });
        GPU.RENDER_PROGRAMS.put(GPU.RENDER_POINTS.getProgramName(), GPU.RENDER_POINTS);
        GPUSimulation.checkGLError("GPU.RENDER_POINTS");

        // Create impostor render program
        GPU.RENDER_IMPOSTOR = new RenderProgram("impostor", GLSLMesh.MeshType.IMPOSTOR, GPU.initialNumBodies);
        RenderProgram.checkProgram(GPU.RENDER_IMPOSTOR.getProgram());
        GPU.RENDER_IMPOSTOR.setUniforms(new Uniform[] {
            GPU.UNIFORM_POINT_SCALE,
            GPU.UNIFORM_CAMERA_POS,
            GPU.UNIFORM_CAMERA_FRONT,
            GPU.UNIFORM_FOV_Y,
            GPU.UNIFORM_ASPECT,
            GPU.UNIFORM_PASS,
            GPU.UNIFORM_PROJ,
            GPU.UNIFORM_MODEL_VIEW,
            GPU.UNIFORM_CAMERA_SCALE,
            GPU.UNIFORM_RELATIVE_TO,
        });
        GPU.RENDER_IMPOSTOR.setSSBOs(new SSBO[] {
            GPU.SSBO_SWAPPING_BODIES_IN,
            GPU.SSBO_SIMULATION_VALUES

        });
        GPU.RENDER_PROGRAMS.put(GPU.RENDER_IMPOSTOR.getProgramName(), GPU.RENDER_IMPOSTOR);
        GPUSimulation.checkGLError("RENDER_IMPOSTOR");

        // Create mesh sphere render program
        GPU.RENDER_SPHERE = new RenderProgram("sphere", GLSLMesh.MeshType.SPHERE, GPU.initialNumBodies);
        GPU.RENDER_SPHERE.setUniforms(new Uniform[] {
            GPU.UNIFORM_MVP,
            GPU.UNIFORM_RADIUS_SCALE,
            GPU.UNIFORM_CAMERA_POS,
            GPU.UNIFORM_CAMERA_SCALE,
            GPU.UNIFORM_RELATIVE_TO,
        });
        GPU.RENDER_SPHERE.setSSBOs(new SSBO[] {
            GPU.SSBO_SWAPPING_BODIES_IN,
        });
        GPU.RENDER_PROGRAMS.put(GPU.RENDER_SPHERE.getProgramName(), GPU.RENDER_SPHERE);
        GPUSimulation.checkGLError("GPU.RENDER_SPHERE");

        // Enable point size
        glEnable(GL_PROGRAM_POINT_SIZE);


        // Initialize regions program
        GPU.RENDER_REGIONS = new RenderProgram("regions", GLSLMesh.MeshType.REGIONS, GPU.initialNumBodies-1);
        GPU.RENDER_REGIONS.setUniforms(new Uniform[] {
            GPU.UNIFORM_MVP,
            GPU.UNIFORM_MIN_MAX_DEPTH,
            GPU.UNIFORM_CAMERA_SCALE,
            GPU.UNIFORM_RELATIVE_TO,
        });
        GPU.RENDER_REGIONS.setSSBOs(new SSBO[] {
            GPU.SSBO_INTERNAL_NODES,
            GPU.SSBO_SIMULATION_VALUES,
        });
        GPU.RENDER_PROGRAMS.put(GPU.RENDER_REGIONS.getProgramName(), GPU.RENDER_REGIONS);
        GPUSimulation.checkGLError("GPU.RENDER_REGIONS");

        GPUSimulation.checkGLError("init");
    }

        
    /**
     * Swap the body buffers.
     */
    public static void swapBodyBuffers() {
        // Swap source and destination buffers for next iteration
        int tmpIn = SSBO_SWAPPING_BODIES_IN.getBufferLocation();
        SSBO_SWAPPING_BODIES_IN.setBufferLocation(SSBO_SWAPPING_BODIES_OUT.getBufferLocation());
        SSBO_SWAPPING_BODIES_OUT.setBufferLocation(tmpIn);
    }
    /**
     * Swaps the morton and index buffers.
     */
    public static void swapMortonAndIndexBuffers() {
        // Swap input/output buffers for next pass of radix sort and the one pass of dead body paritioning.
        int tempMortonIn = SSBO_SWAPPING_MORTON_IN.getBufferLocation();
        int tempIndexIn = SSBO_SWAPPING_INDEX_IN.getBufferLocation();
        SSBO_SWAPPING_MORTON_IN.setBufferLocation(SSBO_SWAPPING_MORTON_OUT.getBufferLocation());
        SSBO_SWAPPING_INDEX_IN.setBufferLocation(SSBO_SWAPPING_INDEX_OUT.getBufferLocation());
        SSBO_SWAPPING_MORTON_OUT.setBufferLocation(tempMortonIn);
        SSBO_SWAPPING_INDEX_OUT.setBufferLocation(tempIndexIn);
    }

    /**
     * Swaps the propagate work queue buffers.
     */
    public static void swapPropagateWorkQueueBuffers() {
        int tmpIn = SSBO_SWAPPING_TREE_WORK_QUEUE_IN.getBufferLocation();
        SSBO_SWAPPING_TREE_WORK_QUEUE_IN.setBufferLocation(SSBO_SWAPPING_TREE_WORK_QUEUE_OUT.getBufferLocation());
        SSBO_SWAPPING_TREE_WORK_QUEUE_OUT.setBufferLocation(tmpIn);
    }


    /**
     * Packs the values to a float buffer.
     * @param numBodies the number of bodies
     * @param bounds the bounds of the simulation
     * @param units the units of the simulation
     * @return the packed values
     */
    public static ByteBuffer packValues(int numBodies, float[][] bounds, UnitSet units) {

        // new GLSLVariable(VariableType.UINT,"numBodies", 1), 
        //     new GLSLVariable(VariableType.UINT,"initialNumBodies", 1), 
        //     new GLSLVariable(VariableType.UINT,"justDied", 1), 
        //     new GLSLVariable(VariableType.UINT,"merged", 1), 
        //     new GLSLVariable(VariableType.UINT,"outOfBounds", 1), 
        //     new GLSLVariable(VariableType.UINT,"pad0", 1), 
        //     new GLSLVariable(VariableType.UINT,"pad1", 1), 
        //     new GLSLVariable(VariableType.UINT,"pad2", 1), 
        //     new GLSLVariable(new GLSLVariable[] {
        //         new GLSLVariable(VariableType.FLOAT,"minCorner", 3), new GLSLVariable(VariableType.PADDING),
        //         new GLSLVariable(VariableType.FLOAT,"maxCorner", 3), new GLSLVariable(VariableType.PADDING)},"bounds"), 
        //     new GLSLVariable(new GLSLVariable[] {
        //         new GLSLVariable(VariableType.FLOAT,"mass", 1), 
        //         new GLSLVariable(VariableType.FLOAT,"density", 1), 
        //         new GLSLVariable(VariableType.FLOAT,"len", 1), 
        //         new GLSLVariable(VariableType.FLOAT,"time", 1), 
        //         new GLSLVariable(VariableType.FLOAT,"gravitationalConstant", 1), 
        //         new GLSLVariable(VariableType.FLOAT,"bodyLengthInSimulationLengthsConstant", 1), 
        //         new GLSLVariable(VariableType.PADDING),
        //         new GLSLVariable(VariableType.PADDING)}, "units"),
        //     new GLSLVariable(VariableType.UINT,"uintDebug", 100), 
        //     new GLSLVariable(VariableType.FLOAT,"floatDebug", 100)},"SimulationValues"));

        //layout(std430, binding = 1) buffer SimulationValues { uint numBodies; uint initialNumBodies; uint justDied; uint justMerged; AABB bounds; } sim;
        ByteBuffer buf = BufferUtils.createByteBuffer(8*Integer.BYTES+16*Float.BYTES+100*Integer.BYTES+100*Float.BYTES);
        buf.putInt(numBodies); // numBodies
        buf.putInt(numBodies); // initialNumBodies
        buf.putInt(0); // justDied
        buf.putInt(0); // merged
        buf.putInt(0); // outOfBounds
        buf.putInt(0); // pad0
        buf.putInt(0); // pad1
        buf.putInt(0); // pad2
        buf.putFloat(bounds[0][0]).putFloat(bounds[0][1]).putFloat(bounds[0][2]).putInt(0); // bounds
        buf.putFloat(bounds[1][0]).putFloat(bounds[1][1]).putFloat(bounds[1][2]).putInt(0); // bounds
        buf.putFloat((float)units.mass()).putFloat((float)units.density()).putFloat((float)units.len()).putFloat((float)units.time()).putFloat(0).putFloat(0).putInt(0).putInt(0); // units
    
        // uintDebug[100]
        for (int i = 0; i < 100; i++) buf.putInt(0);
    
        // floatDebug[100]
        for (int i = 0; i < 100; i++) buf.putFloat(0f);
    
        buf.flip();
        return buf;
    }

    /**
     * Get the number of work groups required for the given number of bodies.
     * @return the number of work groups
     */
    private static int numGroups() {
        return (numBodies() + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
    }

    /**
     * Get the number of bodies.
     * @return the number of bodies
     */
    public static int numBodies() {
        return initialNumBodies;
    }

    /**
     * Upload the planet data to the GPU.
     * @param planetGenerator the planet generator
     * @param bodiesSSBO the SSBO to upload the data to
     */
    public static void uploadPlanetsData(PlanetGenerator planetGenerator, SSBO bodiesSSBO) {

        GPUSimulation.checkGLError("before uploadPlanetsData");

        // Assumes buffers are already correctly sized
        bodiesSSBO.bind();
    
        int offset = 0;
        System.out.print("Uploading planet data:");
        double percentUploaded = 0;
        int displayProgress = 5;
        int lastDisplayed = -1;
        while (planetGenerator.hasNext()) {
            percentUploaded = (int)((double)planetGenerator.planetsGenerated/planetGenerator.getNumPlanets()*100);
            if (percentUploaded % displayProgress == 0 && percentUploaded != lastDisplayed) {
                lastDisplayed = (int)percentUploaded;
                System.out.print(" "+percentUploaded+"%");
            }
            List<Planet> planets = planetGenerator.nextChunk();
            ByteBuffer data = Body.packPlanets(planets);
            glBufferSubData(GL_SHADER_STORAGE_BUFFER, offset, data);
            offset += data.capacity();
        }

        System.out.println(" 100%");

        GPUSimulation.checkGLError("after uploadPlanetsData");

    
        bodiesSSBO.unbind();
    }



    /* --------- Cleanup --------- */
    /**
     * Cleanup the shaders and SSBOs.
     */
    public static void cleanup() {
        for (ComputeProgram program : GPU.COMPUTE_PROGRAMS.values()) {
            program.delete();
        }
        for (RenderProgram program : GPU.RENDER_PROGRAMS.values()) {
                program.delete();
        }
        for (RenderProgram program : GPU.RENDER_PROGRAMS.values()) {
            program.delete();
        }
        for (SSBO ssbo : GPU.SSBOS.values()) {
            ssbo.delete();
        }
    }
}
