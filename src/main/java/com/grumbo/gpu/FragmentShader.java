package com.grumbo.gpu;

import java.io.IOException;
/**
 * FragmentShader is a class that represents a fragment shader.
 * It is used to load and compile a fragment shader in a RenderProgram.
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public class FragmentShader extends GLSLShader {
    /**
     * Constructor for the FragmentShader class.
     * @param shaderName the name of the shader to load
     */
    public FragmentShader(String shaderName) {
        super(shaderName, GLSLShader.ShaderType.FRAGMENT_SHADER);
    }

    /**
     * Gets the source code of the fragment shader. This code is nearly identical to the vertex shader, except for the file path.
     * @param shaderName the name of the shader to get the source code of
     * @return the source code of the fragment shader
     */
    @Override
    public String getSource(String shaderName) {
        try {
            String source = hashtagIncludeShaders("src/main/resources/shaders/render/" + shaderName + "/"+ shaderName + ".frag");
            source = source.replaceAll("(?s)//For compute shaders:.*?//End for compute shaders", "//Removed compute shader code here");
            source = source.replaceAll("buffer", "readonly buffer");
            return source;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fragment shader: " + e.getMessage());
        }
    } 
}
