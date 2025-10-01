package com.grumbo.gpu;

import static org.lwjgl.opengl.GL43C.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import java.util.Map;
import java.util.HashMap;

/**
 * GLSLMesh is a class that represents a mesh.
 * It is used to load and compile a mesh in a RenderProgram.
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public class GLSLMesh {


    public static Map<String, GLSLMesh> meshes = new HashMap<>();

    /**
     * MeshType is an enum that represents the type of mesh.
     * It is used to load and compile each mesh for the specific render programs.
     * @author Grumbo
     * @version 1.0
     * @since 1.0
     */
    public enum MeshType {
        SPHERE("sphere", false, GL_TRIANGLES),
        REGIONS("regions", false, GL_TRIANGLES),
        POINTS("points", true, GL_POINTS),
        IMPOSTOR("impostor", true, GL_TRIANGLE_STRIP);

        private String name;
        private boolean simple;
        private int GLSLBeginMode;

        /**
         * Gets the name of the mesh.
         * @return the name of the mesh
         */
        public String getName() {
            return name;
        }

        /**
         * Checks if the mesh is simple.
         * @return true if the mesh is simple, false otherwise
         */
        public boolean isSimple() {
            return simple;
        }

        /**
         * Gets the GLSL begin mode of the mesh.
         * @return the GLSL begin mode of the mesh
         */
        public int getGLSLBeginMode() {
            return GLSLBeginMode;
        }

        /**
         * Gets the mesh.
         * @return the mesh
         */
        public GLSLMesh getMesh() {
            return meshes.get(getName());
        }

        /**
         * Constructor for the MeshType class.
         * @param name the name of the mesh
         * @param simple true if the mesh is simple, false otherwise
         * @param GLSLBeginMode the GLSL begin mode of the mesh
         */
        private MeshType(String name, boolean simple, int GLSLBeginMode) {
            this.name = name;
            meshes.put(getName(), new GLSLMesh(getName()));
            this.simple = simple;
            this.GLSLBeginMode = GLSLBeginMode;
        }
    }

    public int vao;
    public int vbo;
    public int ibo;
    public String name;
    public int indexCount;
    /**
     * Constructor for the GLSLMesh class.
     * @param vao the VAO of the mesh
     * @param vbo the VBO of the mesh
     * @param ibo the IBO of the mesh
     * @param name the name of the mesh
     * @param indexCount the index count of the mesh
     */
    private GLSLMesh(int vao, int vbo, int ibo, String name, int indexCount) {
        this.vao = vao;
        this.vbo = vbo;
        this.ibo = ibo;
        this.name = name;
        this.indexCount = indexCount;
    }

    /**
     * Constructor for the GLSLMesh class.
     * @param name the name of the mesh
     */
    private GLSLMesh(String name) {
        this(0, 0, 0, name, 0);
    }

    /**
     * Constructor for the GLSLMesh class.
     * @param vao the VAO of the mesh
     * @param vbo the VBO of the mesh
     * @param ibo the IBO of the mesh
     * @param name the name of the mesh
     */
    private GLSLMesh(int vao, int vbo, int ibo, String name) {
        this(vao, vbo, ibo, name, 0);
    }


    /**
     * Reinitializes the mesh.
     * @param meshType the type of mesh
     * @return the mesh
     */
    public static GLSLMesh reInitializeMesh(MeshType meshType) {

        if (meshType.getMesh().vao == 0) {
            int vao = glGenVertexArrays();
            int vbo = 0;
            int ibo = 0;
            if (!meshType.isSimple()) {
                vbo = glGenBuffers();
                ibo = glGenBuffers();
            }
            meshes.put(meshType.getName(), new GLSLMesh(vao, vbo, ibo, meshType.getName()));
        }
        else {
            GLSLMesh mesh = meshType.getMesh();
            glDeleteVertexArrays(mesh.vao);
            glDeleteBuffers(mesh.vbo);
            glDeleteBuffers(mesh.ibo);
            mesh.vao = 0;
            mesh.vbo = 0;
            mesh.ibo = 0;
            reInitializeMesh(meshType);
        }

        switch (meshType) {
            case SPHERE:
                buildSphereMesh();
                break;
            case REGIONS:
                buildRegionsMesh();
                break;
            case POINTS:
                buildPointsMesh();
                break;
            case IMPOSTOR:
                buildImpostorMesh();
                break;
        }

        return meshType.getMesh();
    }

    /**
     * Gets the name of the mesh.
     * @return the name of the mesh
     */
    public String getName() {
        return name;
    }

    /**
     * Rebuilds the sphere mesh.
     */
    private static void buildSphereMesh() {

        GLSLMesh mesh = meshes.get("sphere");

        int stacks = 8;
        int slices = 8;
        

        FloatBuffer positions = generateSpherePositions(stacks, slices);
        IntBuffer indices = generateSphereIndices(stacks, slices);
        mesh.indexCount = indices.remaining();

        glBindVertexArray(mesh.vao);
        glBindBuffer(GL_ARRAY_BUFFER, mesh.vbo);
        glBufferData(GL_ARRAY_BUFFER, positions, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0L);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, mesh.ibo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    /**
     * Generates the sphere positions for the sphere mesh.
     * @param stacks the stacks
     * @param slices the slices
     * @return the sphere positions
     */
    private static FloatBuffer generateSpherePositions(int stacks, int slices) {
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
     * Generates the sphere indices for the sphere mesh.
     * @param stacks the stacks
     * @param slices the slices
     * @return the sphere indices
     */
    private static IntBuffer generateSphereIndices(int stacks, int slices) {
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
    private static void buildRegionsMesh() {
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
        GLSLMesh mesh = meshes.get("regions");
        mesh.indexCount = cubeIndices.length;
        glBindVertexArray(mesh.vao);

        // Vertex buffer
        glBindBuffer(GL_ARRAY_BUFFER, mesh.vbo);
        glBufferData(GL_ARRAY_BUFFER, cubeVertices, GL_STATIC_DRAW);

        // Index buffer
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, mesh.ibo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, cubeIndices, GL_STATIC_DRAW);

        // Vertex attribute (pos)
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0L);

        glBindVertexArray(0);


    }

    /**
     * Builds the points mesh.
     */
    private static void buildPointsMesh() {
        GLSLMesh mesh = meshes.get("points");
        mesh.indexCount = 1;
    }

    /**
     * Builds the impostor mesh.
     */
    private static void buildImpostorMesh() {
        GLSLMesh mesh = meshes.get("impostor");
        mesh.indexCount = 4;
    }
}
