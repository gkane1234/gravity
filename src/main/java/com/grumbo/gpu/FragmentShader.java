package com.grumbo.gpu;

import static org.lwjgl.opengl.GL43C.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FragmentShader extends GLSLShader {



    public FragmentShader(String shaderName) {
        super(shaderName, GLSLShader.ShaderType.FRAGMENT_SHADER);
    }

    @Override
    public String getSource(String shaderName) {
        try {
            return Files.readString(Paths.get("src/main/resources/shaders/render/" + shaderName + "/"+ shaderName + ".frag"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fragment shader: " + e.getMessage());
        }
    }
}
