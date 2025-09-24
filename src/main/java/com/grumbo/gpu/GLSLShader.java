package com.grumbo.gpu;

import com.grumbo.debug.Debug;
import static org.lwjgl.opengl.GL43C.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class GLSLShader {
    private int shader;
    private String shaderName;

    


    public enum ShaderType {
        VERTEX_SHADER,
        FRAGMENT_SHADER,
        COMPUTE_SHADER;

        public int getShaderType() {
            switch (this) {
                case VERTEX_SHADER: return GL_VERTEX_SHADER;
                case FRAGMENT_SHADER: return GL_FRAGMENT_SHADER;
                case COMPUTE_SHADER: return GL_COMPUTE_SHADER;
                default: throw new IllegalArgumentException("Invalid shader type: " + this);
            }
        }
    }

    /**
     * Constructor for the GLSLShader class.
     * @param shaderName the name of the shader
     * @param shaderType the type of the shader
     */
    public GLSLShader(String shaderName, ShaderType shaderType) {
        this.shader = glCreateShader(shaderType.getShaderType());
        glShaderSource(shader, getSource(shaderName));
        glCompileShader(shader);
        checkShader(shader);
        this.shaderName = shaderName;
        
    }


    /**
     * Gets the shader.
     * @return the shader
     */
    public int getShader() {
        return shader;
    }






    /**
     * Gets the shader source.
     * @param kernelName the name of the kernel to get the source for
     * @return the shader source
     */
    public abstract String getSource(String kernelName);

    
    /**
     * Gets the name of the shader.
     * @return the name of the shader
     */
    public String getName() {
        return shaderName;
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
     * Used by getSource() to add the appropriate #include source code.
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
