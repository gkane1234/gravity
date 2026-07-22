package com.grumbo.gpu;

import static org.lwjgl.opengl.GL43C.*;
import java.io.IOException;

/**
 * GLSLShader is a class that represents a GLSL shader.
 * It is used to load and compile a compute, fragment, or vertex shader.
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public abstract class GLSLShader {
    private static final String SHADERS_ROOT = "shaders/";

    private int shader;
    private String shaderName;

    /**
     * ShaderType is an enum that represents the type of shader.
     * @author Grumbo
     * @version 1.0
     * @since 1.0
     */
    public enum ShaderType {
        VERTEX_SHADER,
        FRAGMENT_SHADER,
        COMPUTE_SHADER;

        /**
         * Gets the shader type.
         * @return the shader type
         */
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
        String source = getSource(shaderName);
        glShaderSource(shader, source);
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
     * Gets the shader source. Implemented by subclasses.
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
     * @param shader the shader to check if it compiled successfully
     */
    public static void checkShader(int shader) {
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            System.err.println("Shader compilation failed: " + log);
            throw new RuntimeException("Shader compilation failed: " + log);
        }
    }

    /**
     * Loads a shader and recursively expands {@code #include "path"} directives.
     * Paths are relative to the classpath {@code shaders/} root
     * (e.g. {@code compute/bh_main.comp} or {@code common/common.glsl}).
     *
     * @param shaderRelativePath path under {@code shaders/}
     * @return the shader with includes expanded
     * @throws IOException if the shader cannot be read
     */
    protected static String hashtagIncludeShaders(String shaderRelativePath) throws IOException {
        String relative = normalizeShaderRelative(shaderRelativePath);
        String content = ResourceLoader.readText(SHADERS_ROOT + relative);
        StringBuilder out = new StringBuilder();
        for (String line : content.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#include\"") || trimmed.startsWith("#include \"")) {
                int start = trimmed.indexOf('"');
                int end = trimmed.lastIndexOf('"');
                if (start >= 0 && end > start) {
                    String includeRel = trimmed.substring(start + 1, end);
                    out.append(hashtagIncludeShaders(includeRel));
                    out.append('\n');
                    continue;
                }
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private static String normalizeShaderRelative(String path) {
        String p = path.replace('\\', '/');
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.startsWith("src/main/resources/shaders/")) {
            p = p.substring("src/main/resources/shaders/".length());
        } else if (p.startsWith("shaders/")) {
            p = p.substring("shaders/".length());
        }
        return p;
    }
}
