package com.grumbo.gpu;

import static org.lwjgl.opengl.GL43C.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.grumbo.simulation.BarnesHut;

/**
 * ComputeShader is a class that represents a compute shader program.
 * It is used to dispatch compute shaders to the GPU.
 * It is also used to upload uniforms and SSBOs to the GPU.
 * It is also used to run the compute shader.
 */
public class ComputeShader {
    private int program;
    private int computeShader;
    private Uniform<?>[] uniforms;
    private String[] ssboNames;
    private xWorkGroupsFunction xWorkGroupsFunction;
    private BarnesHut barnesHut;

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
     * @param barnesHut the BarnesHut simulation object
     */
    public ComputeShader(int program, String kernelName, Uniform<?>[] uniforms, String[] ssboNames, xWorkGroupsFunction xWorkGroupsFunction, BarnesHut barnesHut) {
        this.program = program;
        this.computeShader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(computeShader, getSource(kernelName));
        glCompileShader(computeShader);
        checkShader(computeShader);
        glAttachShader(program, computeShader);
        glLinkProgram(program);
        checkProgram(program);
        this.uniforms = uniforms;
        this.ssboNames = ssboNames;
        this.xWorkGroupsFunction = xWorkGroupsFunction;
        this.barnesHut = barnesHut;
    }

    /**
     * Constructor for the ComputeShader class.
     * This constructor creates a new program and attaches the compute shader to it.
     * @param kernelName the name of the kernel to run (defined in the compute shader)
     * @param barnesHut the BarnesHut simulation object
     */
    public ComputeShader(String kernelName, BarnesHut barnesHut) {
        this(glCreateProgram(), kernelName, null, null, null, barnesHut);
    }

    /**
     * Debugs the SSBOs after the compute shader has run.
     * @param numOutputs the number of outputs to print from each SSBO
     */
    public void debug(int numOutputs) {
        for (String ssboName : ssboNames) {
            System.out.println(ssboName + ":");
            System.out.println(barnesHut.ssbos.get(ssboName).getData(0, numOutputs));
        }
    }

    /**
     * Runs the compute shader and debugs the SSBOs after the compute shader has run, with 10 outputs from each SSBO.
     */
    public void runDebug() {
        runDebug(10);
    }

    /**
     * Runs the compute shader and debugs the SSBOs before and after the compute shader has run.
     * @param numOutputs the number of outputs to print from each SSBO
     */
    public void runDebug(int numOutputs) {
        System.out.println("Debugging compute shader: " + program);
        System.out.println("SSBOs before run:");
        debug(numOutputs);
        run();
        System.out.println("SSBOs after run:");
        debug(numOutputs);
    }

    /**
     * Runs the compute shader and adds a memory barrier.
     */
    public void run() {
        runBarrierless();
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

    }

    /**
     * Runs the compute shader and does not add a memory barrier.
     */
    public void runBarrierless() {
        glUseProgram(program);
        if (uniforms != null) {
            for (Uniform<?> uniform : uniforms) {
               
                uniform.uploadToShader(program);
            }
        }
        if (ssboNames != null) {
            for (String ssboName : ssboNames) {

                barnesHut.ssbos.get(ssboName).bind();
            }
        }
        
        glDispatchCompute(xWorkGroupsFunction.getXWorkGroups(), 1, 1);

    }

    /**
     * Sets the uniforms for the compute shader.
     * @param uniforms the uniforms to set
     */
    public void setUniforms(Uniform<?>[] uniforms) {
        this.uniforms = uniforms;
    }


    /**
     * Sets the SSBOs for the compute shader.
     * @param ssboNames the names of the SSBOs to set
     */
    public void setSSBOs(String[] ssboNames) {
        this.ssboNames = ssboNames;
    }

    /**
     * Sets the function that returns the number of work groups to dispatch.
     * @param xWorkGroupsFunction the function to set
     */
    public void setXWorkGroupsFunction(xWorkGroupsFunction xWorkGroupsFunction) {
        this.xWorkGroupsFunction = xWorkGroupsFunction;
    }

    /**
     * Gets the uniforms for the compute shader.
     * @return the uniforms
     */
    public Uniform<?>[] getUniforms() {
        return uniforms;
    }

    /**
     * Gets the SSBOs for the compute shader.
     * @return the SSBOs
     */
    public String[] getSSBOs() {
        return ssboNames;
    }

    /**
     * Deletes the compute shader.
     */
    public void delete() {
        glDeleteProgram(program);
        glDeleteShader(computeShader);
    }

    /**
     * Checks if the shader compiled successfully.
     * @param shader the shader to check
     */
    public static  void checkShader(int shader) {
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

    /**
     * Gets the compute shader source.
     * @param kernelName the name of the kernel to get the source for
     * @return the compute shader source
     */
    public static String getSource(String kernelName) {
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
            return includeShaders(entryPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read Barnes-Hut compute shader: " + e.getMessage());
        }
    }

    /**
     * Used by getComputeShaderSource() to add the appropriate #include source code.
     * @param filePath the path to the shader
     * @return the shader with the appropriate #include source code
     * @throws IOException if the shader cannot be read
     */
    private static String includeShaders(String filePath) throws IOException {
        // Very small preprocessor supporting lines of the form: #include "relative/path.glsl"
        Path path = Paths.get(filePath);
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(path)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#include\"") || trimmed.startsWith("#include \"")) {
                int start = trimmed.indexOf('"');
                int end = trimmed.lastIndexOf('"');
                if (start >= 0 && end > start) {
                    String includeRel = trimmed.substring(start + 1, end);
                    Path includePath = path.getParent().resolve(includeRel);
                    out.append(includeShaders(includePath.toString()));
                    out.append('\n');
                    continue;
                }
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

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