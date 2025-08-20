package com.grumbo.gpu;

import static org.lwjgl.opengl.GL43C.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;

public class SSBO {
    private int bufferLocation;
    private int bufferBinding;
    private sizeFunction size;
    private dataFunction dataFunction;
    private String name;
    private int STRUCT_SIZE;
    private Type[] types;
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

    public SSBO(int binding, sizeFunction size, String name, int structSize, Type[] types) {
        this.bufferBinding = binding;
        this.size = size;
        this.name = name;
        this.bufferLocation = glGenBuffers();
        this.STRUCT_SIZE = structSize;
        this.types = types;
    }

    public SSBO(int binding, dataFunction dataFunction, String name, int structSize, Type[] types) {
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

    public void setTypes(Type[] types) {
        this.types = types;
    }

    public void setBufferLocation(int location) {
        this.bufferLocation = location;
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
                Type type = types[loc];
                result += String.format("%d:", index);
                if (types != null) {
                    if (type == Type.FLOAT) {
                        result += String.format("%.2f", Float.intBitsToFloat(data[index]));
                    } else if (type == Type.INT) {
                        result += String.format("%d", data[index]);
                    } else if (type == Type.UINT) {
                        result += String.format("%d", data[index]);
                    } else if (type == Type.BOOL) {
                        result += String.format(data[index] == 1 ? "T" : "F");
                    } else if (type == Type.UINT64) {
                        result += String.format("%d", (long)data[index] << 32l | (long)data[index+1]);
                        j++;
                        loc--;
                    } else if (type == Type.PADDING) {
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

    public void setBinding(int binding) {
        this.bufferBinding = binding;
    }
    
    public int getBinding() {
        return bufferBinding;
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
