package com.grumbo.gpu;

import java.io.IOException;

/**
 * VertexShader is a class that represents a vertex shader.
 * It is used to load and compile a vertex shader in a RenderProgram.
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public class VertexShader extends GLSLShader {


    /**
     * Constructor for the VertexShader class.
     * @param shaderName the name of the shader to load
     */
    public VertexShader(String shaderName) {
        super(shaderName, GLSLShader.ShaderType.VERTEX_SHADER);
    }


    /**
     * Gets the source code of the vertex shader. Nearly identical to the fragment shader, except for the file path.
     * @param shaderName the name of the shader to get the source code of
     * @return the source code of the vertex shader
     */
    @Override
    public String getSource(String shaderName) {
        try {
            String source = hashtagIncludeShaders("src/main/resources/shaders/render/" + shaderName + "/"+ shaderName + ".vert");
            source = source.replaceAll("(?s)//For compute shaders:.*?//End for compute shaders", "//Removed compute shader code here");
            source = source.replaceAll("buffer", "readonly buffer");
            return source;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read vertex shader: " + e.getMessage());
        }
    }
    
}
