package com.grumbo.gpu;

import static org.lwjgl.opengl.GL43C.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class SSBO {

        // SSBO Bindings
    public static final int BODIES_IN_SSBO_BINDING = 0;
    public static final int BODIES_OUT_SSBO_BINDING = 1;
    public static final int MORTON_IN_SSBO_BINDING = 2;
    public static final int INDEX_IN_SSBO_BINDING = 3;
    public static final int NODES_SSBO_BINDING = 4;
    public static final int AABB_SSBO_BINDING = 5;
    public static final int WG_HIST_SSBO_BINDING = 6;
    public static final int WG_SCANNED_SSBO_BINDING = 7;
    public static final int GLOBAL_BASE_SSBO_BINDING = 8;
    public static final int BUCKET_TOTALS_SSBO_BINDING = 9;
    public static final int MORTON_OUT_SSBO_BINDING = 10;
    public static final int INDEX_OUT_SSBO_BINDING = 11;
    public static final int WORK_QUEUE_SSBO_BINDING = 12;
    public static final int MERGE_QUEUE_SSBO_BINDING = 13;

    private int bufferLocation;
    private final int bufferBinding;
    private sizeFunction size;
    private dataFunction dataFunction;
    private String name;
    private int STRUCT_SIZE;
    private VariableType[] types;
    public interface sizeFunction {
        int getSize();
    }
    public interface dataFunction {
        FloatBuffer setData();
    }

    public static void unBind() {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, 0);
    }

    public SSBO(int binding, sizeFunction size, String name) {
        this(binding, size, name, -1, null);
    }

    public SSBO(int binding, sizeFunction size, String name, int structSize, VariableType[] types) {
        this.bufferBinding = binding;
        this.size = size;
        this.name = name;
        this.bufferLocation = glGenBuffers();
        this.STRUCT_SIZE = structSize;
        this.types = types;
    }

    public SSBO(int binding, dataFunction dataFunction, String name, int structSize, VariableType[] types) {
        this.bufferBinding = binding;
        this.size = null;
        this.dataFunction = dataFunction;
        this.name = name;
        this.bufferLocation = glGenBuffers();
        this.STRUCT_SIZE = structSize;
        this.types = types;
    }

    public void setStructSize(int structSize) {
        this.STRUCT_SIZE = structSize;
    }

    public void setTypes(VariableType[] types) {
        this.types = types;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void bind() {
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, bufferBinding, bufferLocation);
    }
    public void unbind() {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }

    public void createBuffer() {
        bind();
        if (size != null) {
            glBufferData(GL_SHADER_STORAGE_BUFFER, size.getSize(), GL_DYNAMIC_COPY);
        } else {
            glBufferData(GL_SHADER_STORAGE_BUFFER, dataFunction.setData(), GL_DYNAMIC_COPY);
        }
        unbind();
    }

    public String getData(int startIndex, int endIndex) {
        ByteBuffer buffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        int start = startIndex*STRUCT_SIZE;
        int end = Math.min(endIndex*STRUCT_SIZE, buffer.capacity());
        byte[] data = new byte[end - start];
        buffer.get(data, start, end - start);
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        String result = "";

        if (STRUCT_SIZE == -1) {
            for (int i = 0; i < data.length; i++) {
                result += String.format("%d", data[i]);
                if (i < data.length - 1) {
                    result += ", ";
                }
            }
            return result;
        }



        for (int i = start; i < end; i+=STRUCT_SIZE) {
            result += String.format("Index: %d\t (", i/STRUCT_SIZE);
            int loc = 0;
            for (int j = 0; j < STRUCT_SIZE; j++) {
                int index = i + j;
                VariableType type = types[loc];
                result += String.format("%d:", index);
                if (types != null) {
                    if (type == VariableType.FLOAT) {
                        result += String.format("%.2f", Float.intBitsToFloat(data[index]));
                    } else if (type == VariableType.INT) {
                        result += String.format("%d", data[index]);
                    } else if (type == VariableType.UINT) {
                        result += String.format("%d", data[index]);
                    } else if (type == VariableType.BOOL) {
                        result += String.format(data[index] == 1 ? "T" : "F");
                    } else if (type == VariableType.UINT64) {
                        result += String.format("%d", (long)data[index] << 32l | (long)data[index+1]);
                        j++;
                        loc--;
                    } else if (type == VariableType.PADDING) {
                        result += "PADDING";
                    }
                    loc++;
                } else {
                    result += String.format("%d", data[index]);
                }
                if (j < STRUCT_SIZE - 1) {
                    result += ", ";
                }
            }
            result += ")";
            result += "\n";
        }
        return result;
    }


    
    public int getBinding() {
        return bufferBinding;
    }

    public void setBufferLocation(int location) {
        this.bufferLocation = location;
    }

    public int getBufferLocation() {
        return bufferLocation;
    }

    public void delete() {
        glDeleteBuffers(bufferLocation);
    }

    public String getName() {
        return name;
    }
}