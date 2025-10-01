package com.grumbo.gpu;

import java.util.Arrays;
import static org.lwjgl.opengl.GL43C.*;

/**
 * RenderProgram is a class that represents a render program.
 * It is used to load and compile a render program on the GPU with a vertex and fragment shader.
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public class RenderProgram extends GLSLProgram {

    private GLSLMesh.MeshType mesh;
    private int numObjects;
    /**
     * Constructor for the RenderProgram class.
     * @param programName the name of the program
     * @param program the program
     * @param vertexShader the vertex shader
     * @param fragmentShader the fragment shader
     * @param uniforms the uniforms
     * @param ssbos the SSBOs
     * @param mesh the mesh
     * @param numObjects the number of objects
     */
    public RenderProgram(String programName, int program, VertexShader vertexShader, FragmentShader fragmentShader, Uniform<?>[] uniforms, SSBO[] ssbos, GLSLMesh.MeshType mesh, int numObjects) {
        super(programName, program, Arrays.asList(vertexShader, fragmentShader), uniforms, ssbos);
        this.mesh = mesh;
        this.numObjects = numObjects;
    }


    /**
     * Constructor for the RenderProgram class.
     * @param programName the name of the program
     * @param vertexShader the vertex shader
     * @param fragmentShader the fragment shader
     * @param uniforms the uniforms
     * @param ssbos the SSBOs
     * @param mesh the mesh
     * @param numObjects the number of objects
     */
    public RenderProgram(String programName, VertexShader vertexShader, FragmentShader fragmentShader, Uniform<?>[] uniforms, SSBO[] ssbos, GLSLMesh.MeshType mesh, int numObjects) {
        this(programName, glCreateProgram(), vertexShader, fragmentShader, uniforms, ssbos, mesh, numObjects);
    }

    /**
     * Constructor for the RenderProgram class.
     * @param programName the name of the program
     * @param mesh the mesh
     * @param numObjects the number of objects
     */
    public RenderProgram(String programName, GLSLMesh.MeshType mesh, int numObjects) {
        this(programName, glCreateProgram(), new VertexShader(programName), new FragmentShader(programName), null, null, mesh, numObjects);
    }

    /**
     * Sets the number of objects.
     * @param numObjects the number of objects
     */
    public void setNumObjects(int numObjects) {
        this.numObjects = numObjects;
    }


    /**
     * Runs the program. Draws the objects with the mesh.
     * If the mesh has just a single VAO, it will draw the objects with glDrawArraysInstanced.
     * If the mesh has an IBO, it will draw the objects with glDrawElementsInstanced.
     */
    @Override
    public void runProgram() {
        if (mesh.isSimple()) {
            glBindVertexArray(mesh.getMesh().vao); 
            glDrawArraysInstanced(mesh.getGLSLBeginMode(), 0, mesh.getMesh().indexCount, Math.min(numObjects, GPU.MAX_RENDER_INSTANCES));
            glUseProgram(0);
        } else {
            glBindVertexArray(mesh.getMesh().vao); 
            glDrawElementsInstanced(mesh.getGLSLBeginMode(), mesh.getMesh().indexCount, GL_UNSIGNED_INT, 0L, Math.min(numObjects, GPU.MAX_RENDER_INSTANCES));
            glUseProgram(0);
        }
    }

}
