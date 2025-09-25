package com.grumbo.gpu;

import java.io.IOException;


public class VertexShader extends GLSLShader {


    public VertexShader(String shaderName) {
        super(shaderName, GLSLShader.ShaderType.VERTEX_SHADER);
    }


    @Override
    public String getSource(String shaderName) {
        try {
            return hashtagIncludeShaders("src/main/resources/shaders/render/" + shaderName + "/"+ shaderName + ".vert");
        } catch (IOException e) {
            throw new RuntimeException("Failed to read vertex shader: " + e.getMessage());
        }
    }
    
}
