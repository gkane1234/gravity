package com.grumbo.gpu;

import java.io.IOException;


public class VertexShader extends GLSLShader {


    public VertexShader(String shaderName) {
        super(shaderName, GLSLShader.ShaderType.VERTEX_SHADER);
    }


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
