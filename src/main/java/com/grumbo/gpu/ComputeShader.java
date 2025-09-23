package com.grumbo.gpu;

import static org.lwjgl.opengl.GL43C.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.grumbo.simulation.BoundedBarnesHut;
import com.grumbo.debug.Debug;

/**
 * ComputeShader is a class that represents a compute shader program.
 * It is used to dispatch compute shaders to the GPU.
 * It is also used to upload uniforms and SSBOs to the GPU.
 * It is also used to run the compute shader.
 */
public class ComputeShader extends Shader {
    
    private static Debug debug = new Debug("ComputeShader");

;



    /**
     * Constructor for the ComputeShader class.
     * @param program the int id of the program given by glCreateProgram()
     * @param kernelName the name of the kernel to run (defined in the compute shader)
     * @param uniforms the uniforms to upload to the shader
     * @param ssboNames the SSBOs to bind to the shader
     * @param xWorkGroupsFunction the function that returns the number of work groups to dispatch
     * @param BoundedBarnesHut the BoundedBarnesHut simulation object
     */
    public ComputeShader(int program, String kernelName, Uniform<?>[] uniforms, String[] ssboNames, xWorkGroupsFunction xWorkGroupsFunction, BoundedBarnesHut boundedBarnesHut) {
        super(program, kernelName, uniforms, ssboNames, xWorkGroupsFunction);
    }

    /**
     * Constructor for the ComputeShader class.
     * This constructor creates a new program and attaches the compute shader to it.
     * @param kernelName the name of the kernel to run (defined in the compute shader)
     * @param BoundedBarnesHut the BoundedBarnesHut simulation object
     */
    public ComputeShader(String kernelName, BoundedBarnesHut boundedBarnesHut) {
        this(glCreateProgram(), kernelName, null, null, null, boundedBarnesHut);
    }


    /**
     * Gets the compute shader source.
     * @param kernelName the name of the kernel to get the source for
     * @return the compute shader source
     */
    @Override
    public String getSource(String kernelName) {
        return insertDefineAfterVersion(getComputeShaderSource(), kernelName);
    }


    
    /**
     * Gets the compute shader source.
     * @return the compute shader source
     */
    private static String getComputeShaderSource() {
        // Entry point shader that includes all shared code and phase kernels
        String entryPath = "src/main/resources/shaders/compute/bh_main.comp";
        try {
            String source = hashtagIncludeShaders(entryPath);
            debug.addToDebugString(source);
            return source;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read Barnes-Hut compute shader: " + e.getMessage());
        }
    }



    /**
     * Inserts the define after the version and extension lines in order to load the appropriate compute shader.
     * @param shaderSource the shader source to insert the define after
     * @param defineValue the define to insert (e.g. KERNEL_INIT)
     * @return the shader source with the define inserted
     */
    private static String insertDefineAfterVersion(String shaderSource, String defineValue) {
        // Insert the define after the initial preamble (#version and any #extension lines)
        int insertPos = 0;
        int pos = 0;
        while (pos < shaderSource.length()) {
            int lineEnd = shaderSource.indexOf('\n', pos);
            if (lineEnd == -1) lineEnd = shaderSource.length();
            String line = shaderSource.substring(pos, lineEnd).trim();
            if (line.startsWith("#version") || line.startsWith("#extension")) {
                insertPos = lineEnd + 1; // include newline
                pos = lineEnd + 1;
                continue;
            }
            break;
        }
        String defineLine = "#define " + defineValue + "\n";
        return shaderSource.substring(0, insertPos) + defineLine + shaderSource.substring(insertPos);
    }






}