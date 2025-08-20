package com.grumbo.gpu;

import static org.lwjgl.opengl.GL43C.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.grumbo.simulation.GPUSimulation;

public class ComputeShader {
    private int program;
    private int computeShader;
    private Uniform[] uniforms;
    private String[] ssboNames;
    private xWorkGroupsFunction xWorkGroupsFunction;
    private GPUSimulation gpuSimulation;
    public interface xWorkGroupsFunction {
        int getXWorkGroups();
    }

    public ComputeShader(int program, String kernelName, Uniform[] uniforms, String[] ssboNames, xWorkGroupsFunction xWorkGroupsFunction, GPUSimulation gpuSimulation) {
        this.program = program;
        this.computeShader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(computeShader, insertDefineAfterVersion(getComputeShaderSource(), kernelName));
        glCompileShader(computeShader);
        checkShader(computeShader);
        glAttachShader(program, computeShader);
        glLinkProgram(program);
        checkProgram(program);
        this.uniforms = uniforms;
        this.ssboNames = ssboNames;
        this.xWorkGroupsFunction = xWorkGroupsFunction;
        this.gpuSimulation = gpuSimulation;
    }

    public ComputeShader(String kernelName, GPUSimulation gpuSimulation) {
        this(glCreateProgram(), kernelName, null, null, null, gpuSimulation);
    }

    public void debug(int numOutputs) {
        for (String ssboName : ssboNames) {
            System.out.println(ssboName + ":");
            System.out.println(gpuSimulation.ssbos.get(ssboName).getData(0, numOutputs));
        }
    }

    public void runDebug() {
        runDebug(10);
    }

    public void runDebug(int numOutputs) {
        System.out.println("Debugging compute shader: " + program);
        System.out.println("SSBOs before run:");
        debug(numOutputs);
        run();
        System.out.println("SSBOs after run:");
        debug(numOutputs);
    }


    public void run() {
        glUseProgram(program);
        if (uniforms != null) {
            for (Uniform uniform : uniforms) {
                if (uniform.getValue() instanceof Integer) {
                    if (uniform.isUnsigned()) {
                        glUniform1ui(glGetUniformLocation(program, uniform.getName()), (Integer)uniform.getValue());
                    } else {
                        glUniform1i(glGetUniformLocation(program, uniform.getName()), (Integer)uniform.getValue());
                    }
                } else if (uniform.getValue() instanceof Float) {
                    glUniform1f(glGetUniformLocation(program, uniform.getName()), (Float)uniform.getValue());
                } else if (uniform.getValue() instanceof Boolean) {
                    glUniform1i(glGetUniformLocation(program, uniform.getName()), (Boolean)uniform.getValue() ? 1 : 0);
                }
                else {
                    throw new RuntimeException("Uniform type not supported: " + uniform.getValue().getClass());
                }
            }
        }
        if (ssboNames != null) {
            for (String ssboName : ssboNames) {
                gpuSimulation.ssbos.get(ssboName).bind();
            }
        }
        System.out.println("Dispatching compute shader: "  +program+ " with " + xWorkGroupsFunction.getXWorkGroups() + " work groups");
        glDispatchCompute(xWorkGroupsFunction.getXWorkGroups(), 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

    }

    public void setUniforms(Uniform[] uniforms) {
        this.uniforms = uniforms;
    }

    public void setSSBOs(String ssboName) {
        this.ssboNames = new String[] { ssboName };
    }
    public void setSSBOs(String[] ssboNames) {
        this.ssboNames = ssboNames;
    }
    public void setXWorkGroupsFunction(xWorkGroupsFunction xWorkGroupsFunction) {
        this.xWorkGroupsFunction = xWorkGroupsFunction;
    }
    public void getUniforms(Uniform[] uniforms) {
        this.uniforms = uniforms;
    }
    public void getSSBOs(String[] ssboNames) {
        this.ssboNames = ssboNames;
    }

    public void delete() {
        glDeleteProgram(program);
        glDeleteShader(computeShader);
    }


    public static  void checkShader(int shader) {
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            System.err.println("Shader compilation failed: " + glGetShaderInfoLog(shader));
        }
    }

    public static void checkProgram(int program) {
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            System.err.println("Program linking failed: " + glGetProgramInfoLog(program));
        }
    }
    public static String getComputeShaderSource() {
        // Entry point shader that includes all shared code and phase kernels
        String entryPath = "src/main/resources/shaders/compute/bh_main.glsl";
        try {
            return preprocessShader(entryPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read Barnes-Hut compute shader: " + e.getMessage());
        }
    }

    private static String preprocessShader(String filePath) throws IOException {
        // Very small preprocessor supporting lines of the form: #include "relative/path.glsl"
        Path path = Paths.get(filePath);
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(path)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#include\"") || trimmed.startsWith("#include \"")) {
                int start = trimmed.indexOf('"');
                int end = trimmed.lastIndexOf('"');
                if (start >= 0 && end > start) {
                    String includeRel = trimmed.substring(start + 1, end);
                    Path includePath = path.getParent().resolve(includeRel);
                    out.append(preprocessShader(includePath.toString()));
                    out.append('\n');
                    continue;
                }
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    public static String insertDefineAfterVersion(String shaderSource, String defineValue) {
        // Insert the define after the initial preamble (#version and any #extension lines)
        int insertPos = 0;
        int pos = 0;
        while (pos < shaderSource.length()) {
            int lineEnd = shaderSource.indexOf('\n', pos);
            if (lineEnd == -1) lineEnd = shaderSource.length();
            String line = shaderSource.substring(pos, lineEnd).trim();
            if (line.startsWith("#version") || line.startsWith("#extension")) {
                insertPos = lineEnd + 1; // include newline
                pos = lineEnd + 1;
                continue;
            }
            break;
        }
        String defineLine = "#define " + defineValue + "\n";
        return shaderSource.substring(0, insertPos) + defineLine + shaderSource.substring(insertPos);
    }
}

