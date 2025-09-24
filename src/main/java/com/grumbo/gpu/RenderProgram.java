package com.grumbo.gpu;

import java.util.Arrays;
import static org.lwjgl.opengl.GL43C.*;

public class RenderProgram extends GLSLProgram {

    public RenderProgram(String programName, int program, VertexShader vertexShader, FragmentShader fragmentShader, Uniform<?>[] uniforms, SSBO[] ssbos) {
        super(programName, program, Arrays.asList(vertexShader, fragmentShader), uniforms, ssbos);
    }


    public RenderProgram(String programName, VertexShader vertexShader, FragmentShader fragmentShader, Uniform<?>[] uniforms, SSBO[] ssbos) {
        this(programName, glCreateProgram(), vertexShader, fragmentShader, uniforms, ssbos);
    }

    public RenderProgram(String programName) {
        this(programName, glCreateProgram(), new VertexShader(programName), new FragmentShader(programName), null, null);
    }





    
    @Override
    public void runProgram() {
        
    }

}
