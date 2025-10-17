package com.grumbo.gpu;

import static org.lwjgl.opengl.GL43C.*;

import java.util.List;
import java.util.ArrayList;

import com.grumbo.debug.Debug;

/**
 * GLSLProgram is a class that represents a GLSL program.
 * It is used to load and compile a program in a RenderProgram or ComputeProgram.
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public abstract class GLSLProgram {
    int program;
    List<GLSLShader> shaders;
    private Uniform<?>[] uniforms;
    private SSBO[] ssbos;
    private Debug preDebug;
    private Debug postDebug;
    private String programName;


    


    /**
     * Constructor for the GLSLProgram class.
     * @param programName the name of the program
     * @param program the program
     * @param shaders the shaders
     * @param uniforms the uniforms
     * @param ssboNames the SSBOs
     */
    public GLSLProgram(String programName, int program, List<GLSLShader> shaders, Uniform<?>[] uniforms, SSBO[] ssbos) {
        this.program = program;
        this.shaders = shaders;
        this.uniforms = uniforms;
        this.ssbos = ssbos;
        this.programName = programName;
        this.preDebug = new Debug("PRE " + programName);
        this.postDebug = new Debug("POST " + programName);

        if (shaders == null) {
            this.shaders = new ArrayList<>();
        } else {
            for (GLSLShader shader : shaders) {
                addShader(shader);
            }
            this.shaders = shaders;
        }
    }
    
    /**
     * Adds a shader to the program.
     * @param shader the shader to add
     */
    public void addShader(GLSLShader shader) {
        glAttachShader(program, shader.getShader());
        glLinkProgram(program);
        checkProgram(program);
    }


    /**
     * Runs the program. Uses the runProgram() method to run the program after uploading the uniforms and SSBOs.
     * NOTE: Does not use glUseProgram(0) after running the program.
     */
    public void run() {
        glUseProgram(program);
        uploadUnformsAndGetSSBOs();
        runProgram();
    }

    /**
     * Uploads the uniforms and gets the SSBOs.
     */
    public void uploadUnformsAndGetSSBOs() {
        if (uniforms != null) {
            for (Uniform<?> uniform : uniforms) {
                uniform.uploadToShader(program);
            }
        }
        if (ssbos != null) {
            for (SSBO ssbo : ssbos) {
                ssbo.bind();
            }
        }
    }

    /**
     * Runs the program. Can be overritten by subclasses.
     */
    public void runProgram() {
        // Do nothing
    }


    /**
     * Gets the program.
     * @return the program
     */
    public int getProgram() {
        return program;
    }




    /**
     * Checks if the program linked successfully.
     * @param program the program to check
     */
    public static void checkProgram(int program) {
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            System.err.println("Program "+ program + " linking failed: " + glGetProgramInfoLog(program));
            throw new RuntimeException("Program linking failed");
        }
    }

    /**
     * Gets the name of the program.
     * @return the name of the program
     */
    public String getProgramName() {
        return programName;
    }

    /**
     * Sets the uniforms for the program.
     * @param uniforms the uniforms to set
     */
    public void setUniforms(Uniform<?>[] uniforms) {
        this.uniforms = uniforms;
    }


    /**
     * Sets the SSBOs for the program.
     * @param ssboNames the names of the SSBOs to set
     */
    public void setSSBOs(SSBO[] ssbos) {
        this.ssbos = ssbos;
    }

    /**
     * Sets the SSBO binding range, if an SSBO is to be used with an offset
     * @param ssbo the SSBO to set the binding range of
     * @param startIndex the start index of the binding range
     * @param endIndex the end index of the binding range
     */
    public void setSSBOBindinRange(SSBO ssbo, int startIndex, int endIndex) {
        ssbo.bind(startIndex, endIndex);
    }

    /**
     * Gets the uniforms for the program.
     * @return the uniforms
     */
    public Uniform<?>[] getUniforms() {
        return uniforms;
    }

    /**
     * Gets the SSBOs for the program.
     * @return the SSBOs
     */
    public SSBO[] getSSBOs() {
        return ssbos;
    }

    /**
     * Deletes the program.
     */
    public void delete() {
        glDeleteProgram(program);
        for (GLSLShader shader : shaders) {
            glDeleteShader(shader.getShader());
        }
    }

    /**
     * Checks if the pre debug is selected.
     * @return true if the pre debug is selected, false otherwise
     */
    public boolean isPreDebugSelected() {
        return this.preDebug.isSelected();
    }
    
    /**
     * Checks if the post debug is selected.
     * @return true if the post debug is selected, false otherwise
     */
    public boolean isPostDebugSelected() {
        return this.postDebug.isSelected();
    }

    /**
     * Sets the pre debug string.
     * @param preDebug the pre debug string
     */
    public void setPreDebugString(String preDebug) {
        if (isPreDebugSelected()) {
            this.preDebug.setDebugString(preDebug);
        }

    }
    
    /**
     * Sets the post debug string.
     * @param postDebug the post debug string
     */
    public void setPostDebugString(String postDebug) {
        if (isPostDebugSelected()) {
            this.postDebug.setDebugString(postDebug);
        }
    }

    /**
     * Adds to the pre debug string.
     * @param preDebug the pre debug string
     */
    public void addToPreDebugString(String preDebug) {
        if (isPreDebugSelected()) {
            this.preDebug.addToDebugString(preDebug);
        }
    }
    
    /**
     * Adds to the post debug string.
     * @param postDebug the post debug string
     */
    public void addToPostDebugString(String postDebug) {
        if (isPostDebugSelected()) {
            this.postDebug.addToDebugString(postDebug);
        }
    }

    /**
     * Clears the pre debug string.
     */
    public void clearPreDebug() {
        if (isPreDebugSelected()) {
            this.preDebug.clearDebugString();
        }
    }
    
    /**
     * Clears the post debug string.
     */
    public void clearPostDebug() {
        if (isPostDebugSelected()) {
            this.postDebug.clearDebugString();
        }
    }
    
}
