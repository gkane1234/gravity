package com.grumbo.gpu;

import java.util.Arrays;
import static org.lwjgl.opengl.GL43C.*;

public class RenderProgram extends GLSLProgram {

    private GLSLMesh.MeshType mesh;
    private int numObjects;
    public RenderProgram(String programName, int program, VertexShader vertexShader, FragmentShader fragmentShader, Uniform<?>[] uniforms, SSBO[] ssbos, GLSLMesh.MeshType mesh, int numObjects) {
        super(programName, program, Arrays.asList(vertexShader, fragmentShader), uniforms, ssbos);
        this.mesh = mesh;
        this.numObjects = numObjects;
    }


    public RenderProgram(String programName, VertexShader vertexShader, FragmentShader fragmentShader, Uniform<?>[] uniforms, SSBO[] ssbos, GLSLMesh.MeshType mesh, int numObjects) {
        this(programName, glCreateProgram(), vertexShader, fragmentShader, uniforms, ssbos, mesh, numObjects);
    }

    public RenderProgram(String programName, GLSLMesh.MeshType mesh, int numObjects) {
        this(programName, glCreateProgram(), new VertexShader(programName), new FragmentShader(programName), null, null, mesh, numObjects);
    }

    public void setNumObjects(int numObjects) {
        this.numObjects = numObjects;
    }


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
