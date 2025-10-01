package com.grumbo.gpu;

import static org.lwjgl.opengl.GL43C.*;



/**
 * ComputeShader is a class that represents a compute shader program.
 * It is used to dispatch compute shaders to the GPU.
 * It is also used to upload uniforms and SSBOs to the GPU.
 * It is also used to run the compute shader.
 */
public class ComputeProgram extends GLSLProgram {
    private xWorkGroupsFunction xWorkGroupsFunction;


    /**
     * xWorkGroupsFunction is a function that returns the number of work groups to dispatch.
     */
    public interface xWorkGroupsFunction {
        int getXWorkGroups();
    }

    /**
     * Constructor for the ComputeShader class.
     * @param program the int id of the program given by glCreateProgram()
     * @param kernelName the name of the kernel to run (defined in the compute shader)
     * @param uniforms the uniforms to upload to the shader
     * @param ssboNames the SSBOs to bind to the shader
     * @param xWorkGroupsFunction the function that returns the number of work groups to dispatch
     */
    public ComputeProgram(String kernelName, int program, Uniform<?>[] uniforms, SSBO[] ssbos, xWorkGroupsFunction xWorkGroupsFunction) {
        super(kernelName, program, null, uniforms, ssbos);
        this.xWorkGroupsFunction = xWorkGroupsFunction;
        this.addShader(new ComputeShader(kernelName));
    }

    /**
     * Constructor for the ComputeShader class.
     * This constructor creates a new program and attaches the compute shader to it.
     * @param kernelName the name of the kernel to run (defined in the compute shader)
     */
    public ComputeProgram(String kernelName) {
        this(kernelName, glCreateProgram(), null, null, null);
    }

    /**
     * Runs the compute program, adds a memory barrier, and uses glUseProgram(0).
     */
    @Override
    public void runProgram() {
        glDispatchCompute(xWorkGroupsFunction.getXWorkGroups(), 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glUseProgram(0);
    }

    /**
     * Sets the function that returns the number of work groups to dispatch.
     * @param xWorkGroupsFunction the function to set
     */
    public void setXWorkGroupsFunction(xWorkGroupsFunction xWorkGroupsFunction) {
        this.xWorkGroupsFunction = xWorkGroupsFunction;
    }

}