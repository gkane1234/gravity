package com.grumbo.gpu;

import java.io.IOException;

public class FragmentShader extends GLSLShader {
    public FragmentShader(String shaderName) {
        super(shaderName, GLSLShader.ShaderType.FRAGMENT_SHADER);
    }

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
