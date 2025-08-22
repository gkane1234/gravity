package com.grumbo.gpu;

import static org.lwjgl.opengl.GL43C.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;


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
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, bufferLocation);
        ByteBuffer buffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);

        if (buffer == null) {
            glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
            return "Error: Could not map buffer";
        }
        
        buffer.order(java.nio.ByteOrder.nativeOrder());
        String result = name+"\n";
        

        //Assume it is integers
        if (STRUCT_SIZE == -1) {
            int[] data = new int[buffer.capacity()/4];
            IntBuffer intBuffer = buffer.asIntBuffer();
            intBuffer.get(data);
            for (int i = 0; i < data.length; i++) {
                result += String.format("%d", data[i]);
                if (i < data.length - 1) {
                    result += "\n";
                }
            }
            glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0); // Unbind when done
            return result+"\n";
        }

        // Calculate total struct size in bytes
        int structSizeInBytes = 0;
        for (int i = 0; i < types.length; i++) {
            structSizeInBytes += VariableType.getSize(types[i]);
        }

        // Start reading from the correct byte offset
        int startByteOffset = startIndex * structSizeInBytes;
        
        // Iterate through each struct
        for (int structIndex = startIndex; structIndex < endIndex; structIndex++) {
            result += String.format("Index: %d\t (", structIndex);
            
            // Current byte position within the buffer
            int currentByteOffset = startByteOffset + (structIndex - startIndex) * structSizeInBytes;
            
            // Read each field in the struct
            for (int fieldIndex = 0; fieldIndex < types.length; fieldIndex++) {
                VariableType type = types[fieldIndex];

                //System.out.println(buffer.get(currentByteOffset)+" "+Float.intBitsToFloat(buffer.getInt(currentByteOffset)));
                
                result += String.format("%d:", fieldIndex);
                
                if (type == VariableType.FLOAT) {
                    int intValue = buffer.getInt(currentByteOffset);
                    result += String.format("%.2f", Float.intBitsToFloat(intValue));
                    currentByteOffset += VariableType.getSize(type);    
                } else if (type == VariableType.INT) {
                    result += String.valueOf(buffer.getInt(currentByteOffset));
                    currentByteOffset += VariableType.getSize(type);
                } else if (type == VariableType.UINT) {
                    // For unsigned int, we need to handle it properly
                    int value = buffer.getInt(currentByteOffset);
                    result += String.valueOf(value);
                    currentByteOffset += VariableType.getSize(type);
                } else if (type == VariableType.BOOL) {
                    result += String.format(buffer.get(currentByteOffset) == 1 ? "T" : "F");
                    currentByteOffset += VariableType.getSize(type);
                } else if (type == VariableType.UINT64) {
                    result += String.format("%d", buffer.getLong(currentByteOffset));
                    currentByteOffset += VariableType.getSize(type);
                } else if (type == VariableType.PADDING) {
                    result += "PADDING";
                    currentByteOffset += VariableType.getSize(type);
                }
                
                if (fieldIndex < types.length - 1) {
                    result += ", ";
                }
            }
            result += ")";
            result += "\n";
        }
        
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0); // Unbind when done
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