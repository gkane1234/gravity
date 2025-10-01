package com.grumbo.gpu;

import com.grumbo.debug.Debug;

import java.io.IOException;

public class ComputeShader extends GLSLShader {
    
    private static Debug debug = new Debug("ComputeShader");

    /**
     * Constructor for the ComputeShader class.
     * @param kernelName the name of the kernel to run (defined in the compute shader)
     */
    public ComputeShader(String kernelName) {
        super(kernelName, GLSLShader.ShaderType.COMPUTE_SHADER);
    }

    /**
     * Gets the compute shader source.
     * @param programName the name of the program to get the source for
     * @return the compute shader source
     */
    @Override
    public String getSource(String programName) {
        String source = insertDefineAfterVersion(getComputeShaderSource(), programName);
        source = source.replaceAll("(?s)//For render shaders:.*?//End for render shaders", "//Removed render shader code here");
        return source;
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
     * @param defineValue the define to insert (e.g. COMPUTE_INIT)
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
