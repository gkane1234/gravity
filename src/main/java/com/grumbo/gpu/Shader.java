package com.grumbo.gpu;

import com.grumbo.debug.Debug;
import static org.lwjgl.opengl.GL43C.*;
import com.grumbo.gpu.GPU;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public abstract class Shader {
    private int program;
    private int shader;
    private Uniform<?>[] uniforms;
    private String[] ssboNames;
    private Debug preDebug;
    private Debug postDebug;
    private String kernelName;
    private xWorkGroupsFunction xWorkGroupsFunction;

    public Shader(int program, String kernelName, Uniform<?>[] uniforms, String[] ssboNames, xWorkGroupsFunction xWorkGroupsFunction) {
        this.program = program;
        this.shader = glCreateShader(GL_COMPUTE_SHADER);
        String source = getSource(kernelName);
        // String[] lines = source.split("\n");
        // String[] firstTenLines = Arrays.copyOfRange(lines, 0, Math.min(1000, lines.length));
        // System.out.println(Arrays.toString(firstTenLines));
        // try {
        //     System.in.read();
        // } catch (IOException e) {
        //     e.printStackTrace();
        // }
        glShaderSource(shader, getSource(kernelName));
        glCompileShader(shader);
        checkShader(shader);
        glAttachShader(program, shader);
        glLinkProgram(program);
        checkProgram(program);
        this.uniforms = uniforms;
        this.ssboNames = ssboNames;
        this.xWorkGroupsFunction = xWorkGroupsFunction;
        this.preDebug = new Debug("PRE " + kernelName);
        this.postDebug = new Debug("POST " + kernelName);
        this.kernelName = kernelName;
        
    }


    /**
     * Debugs the SSBOs after the shader has run.
     * @param numOutputs the number of outputs to print from each SSBO
     */
    public void debug(int numOutputs) {
        for (String ssboName : ssboNames) {
            System.out.println(ssboName + ":");
            System.out.println(GPU.SSBOS.get(ssboName).getDataAsString(ssboName, 0, numOutputs, true));
        }
    }

    public boolean isPreDebugSelected() {
        return this.preDebug.isSelected();
    }
    
    public boolean isPostDebugSelected() {
        return this.postDebug.isSelected();
    }

    public void setPreDebugString(String preDebug) {
        if (isPreDebugSelected()) {
            this.preDebug.setDebugString(preDebug);
        }

    }
    
    public void setPostDebugString(String postDebug) {
        if (isPostDebugSelected()) {
            this.postDebug.setDebugString(postDebug);
        }
    }

    public void addToPreDebugString(String preDebug) {
        if (isPreDebugSelected()) {
            this.preDebug.addToDebugString(preDebug);
        }
    }
    
    public void addToPostDebugString(String postDebug) {
        if (isPostDebugSelected()) {
            this.postDebug.addToDebugString(postDebug);
        }
    }

    public void clearPreDebug() {
        if (isPreDebugSelected()) {
            this.preDebug.clearDebugString();
        }
    }
    
    public void clearPostDebug() {
        if (isPostDebugSelected()) {
            this.postDebug.clearDebugString();
        }
    }



    /**
     * Runs the shader and debugs the SSBOs after the shader has run, with 10 outputs from each SSBO.
     */
    public void runDebug() {
        runDebug(10);
    }
    /**
     * Runs the shader and debugs the SSBOs before and after the shader has run.
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
     * Runs the shader and adds a memory barrier.
     */
    public void run() {
        runBarrierless();
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

    }

    /**
     * Runs the shader and does not add a memory barrier.
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
                GPU.SSBOS.get(ssboName).bind();
            }
        }
        
        glDispatchCompute(xWorkGroupsFunction.getXWorkGroups(), 1, 1);

    }
    /**
     * Gets the name of the shader.
     * @return the name of the shader
     */
    public String getName() {
        return kernelName;
    }

    /**
     * xWorkGroupsFunction is a function that returns the number of work groups to dispatch.
     */
    public interface xWorkGroupsFunction {
        int getXWorkGroups();
    }

    /**
     * Sets the function that returns the number of work groups to dispatch.
     * @param xWorkGroupsFunction the function to set
     */
    public void setXWorkGroupsFunction(xWorkGroupsFunction xWorkGroupsFunction) {
        this.xWorkGroupsFunction = xWorkGroupsFunction;
    }

    /**
     * Sets the uniforms for the shader.
     * @param uniforms the uniforms to set
     */
    public void setUniforms(Uniform<?>[] uniforms) {
        this.uniforms = uniforms;
    }


    /**
     * Sets the SSBOs for the shader.
     * @param ssboNames the names of the SSBOs to set
     */
    public void setSSBOs(String[] ssboNames) {
        this.ssboNames = ssboNames;
    }

    /**
     * Gets the uniforms for the shader.
     * @return the uniforms
     */
    public Uniform<?>[] getUniforms() {
        return uniforms;
    }

    /**
     * Gets the SSBOs for the shader.
     * @return the SSBOs
     */
    public String[] getSSBOs() {
        return ssboNames;
    }

    /**
     * Deletes the shader.
     */
    public void delete() {
        glDeleteProgram(program);
        glDeleteShader(shader);
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
    public abstract String getSource(String kernelName);


        /**
     * Used by getComputeShaderSource() to add the appropriate #include source code.
     * @param filePath the path to the shader
     * @return the shader with the appropriate #include source code
     * @throws IOException if the shader cannot be read
     */
    protected static String hashtagIncludeShaders(String filePath) throws IOException {
        // Very small preprocessor supporting lines of the form: #include "compute/path.comp" or #include "render/impostor/path.vert"
        Path path = Paths.get(filePath);
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(path)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#include\"") || trimmed.startsWith("#include \"")) {
                int start = trimmed.indexOf('"');
                int end = trimmed.lastIndexOf('"');
                if (start >= 0 && end > start) {
                    String includeRel = trimmed.substring(start + 1, end);
                    Path includePath = path.getParent().getParent().resolve(includeRel);
                    out.append(hashtagIncludeShaders(includePath.toString()));
                    out.append('\n');
                    continue;
                }
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

}
