package com.grumbo.gpu;

import java.io.IOException;

public class FragmentShader extends GLSLShader {
    public FragmentShader(String shaderName) {
        super(shaderName, GLSLShader.ShaderType.FRAGMENT_SHADER);
    }

    @Override
    public String getSource(String shaderName) {
        try {
            return hashtagIncludeShaders("src/main/resources/shaders/render/" + shaderName + "/"+ shaderName + ".frag");
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fragment shader: " + e.getMessage());
        }
    } 
}
