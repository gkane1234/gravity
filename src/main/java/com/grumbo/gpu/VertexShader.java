package com.grumbo.gpu;

import static org.lwjgl.opengl.GL43C.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class VertexShader extends GLSLShader {


    public VertexShader(String shaderName) {
        super(shaderName, GLSLShader.ShaderType.VERTEX_SHADER);
    }


    @Override
    public String getSource(String shaderName) {
        try {
            return Files.readString(Paths.get("src/main/resources/shaders/render/" + shaderName + "/"+ shaderName + ".vert"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read vertex shader: " + e.getMessage());
        }
    }
    
}
