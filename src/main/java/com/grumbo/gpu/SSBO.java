package com.grumbo.gpu;

import static org.lwjgl.opengl.GL43C.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;


public class SSBO {


    private static final int PRECISION = 2; 

        // SSBO Bindings
    public static final int BODIES_IN_SSBO_BINDING = 0;
    public static final int BODIES_OUT_SSBO_BINDING = 1;
    public static final int MORTON_IN_SSBO_BINDING = 2;
    public static final int INDEX_IN_SSBO_BINDING = 3;
    public static final int NODES_SSBO_BINDING = 4;
    public static final int VALUES_SSBO_BINDING = 5;
    public static final int WG_HIST_SSBO_BINDING = 6;
    public static final int WG_SCANNED_SSBO_BINDING = 7;
    public static final int BUCKET_TOTALS_SSBO_BINDING = 8;
    public static final int MORTON_OUT_SSBO_BINDING = 9;
    public static final int INDEX_OUT_SSBO_BINDING = 10;
    public static final int WORK_QUEUE_SSBO_BINDING = 11;
    public static final int MERGE_QUEUE_SSBO_BINDING = 12;
    public static final int DEBUG_SSBO_BINDING = 13;


    private int bufferLocation;
    private final int bufferBinding;
    private sizeFunction sizeFunction;
    private dataFunction dataFunction;
    private String name;
    private int STRUCT_SIZE;
    private VariableType[] types;
    private int headerSize;
    private VariableType[] headerTypes;
    public interface sizeFunction {
        int getSize();
    }
    public interface dataFunction {
        ByteBuffer setData();
    }

    public static void unBind() {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, 0);
    }

    public SSBO(int binding, sizeFunction sizeFunction, dataFunction dataFunction, String name, int structSize, VariableType[] types, int headerSize, VariableType[] headerTypes) {
        this.bufferBinding = binding;
        this.sizeFunction = sizeFunction;
        this.dataFunction = dataFunction;
        this.name = name;
        this.bufferLocation = glGenBuffers();
        this.STRUCT_SIZE = structSize;
        this.types = types;
        this.headerSize = headerSize;
        this.headerTypes = headerTypes;
    }

    public SSBO(int binding, sizeFunction size, String name) {
        this(binding, size, null, name, -1, null, 0, null);
    }

    public SSBO(int binding, dataFunction dataFunction, String name) {
        this(binding, null, dataFunction, name, -1, null, 0, null);
    }

    public SSBO(int binding, sizeFunction size, String name, int structSize, VariableType[] types) {
        this(binding, size, null, name, structSize, types, 0, null);
    }

    public SSBO(int binding, dataFunction dataFunction, String name, int structSize, VariableType[] types) {
        this(binding, dataFunction, name, structSize, types, 0, null);
    }


    public SSBO(int binding, sizeFunction size, String name, int structSize, VariableType[] types, int headerSize, VariableType[] headerTypes) {
        this(binding, size, null, name, structSize, types, headerSize, headerTypes);
    }

    public SSBO(int binding, dataFunction dataFunction, String name, int structSize, VariableType[] types, int headerSize, VariableType[] headerTypes) {
        this(binding, null, dataFunction, name, structSize, types, headerSize, headerTypes);
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
        if (sizeFunction != null) {
            glBufferData(GL_SHADER_STORAGE_BUFFER, sizeFunction.getSize(), GL_DYNAMIC_COPY);
        } else {
            glBufferData(GL_SHADER_STORAGE_BUFFER, dataFunction.setData(), GL_DYNAMIC_COPY);
        }
        unbind();
    }

    public ByteBuffer getBuffer() {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, bufferLocation);
        ByteBuffer buffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        buffer.order(java.nio.ByteOrder.nativeOrder());
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0); // Unbind when done
        return buffer;
    }

    public String getHeader() {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, bufferLocation);
        ByteBuffer buffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        buffer.order(java.nio.ByteOrder.nativeOrder());
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0); // Unbind when done
        String result = "Header for "+name+"\n(";
        result += readEachField(buffer, 0, headerTypes);
        return result;
    }

    public int[] getHeaderAsInts() {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, bufferLocation);
        ByteBuffer buffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        buffer.order(java.nio.ByteOrder.nativeOrder());
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0); // Unbind when done
        int[] data = new int[(buffer.capacity()/4)-headerSize];
        IntBuffer intBuffer = buffer.asIntBuffer();
        intBuffer.get(data);
        return data;
    }

    public ByteBuffer getRawData() {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, bufferLocation);
        ByteBuffer buffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        buffer.order(java.nio.ByteOrder.nativeOrder());
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        return buffer;
    }


    public String getData(int startIndex, int endIndex) {
        ByteBuffer buffer = getRawData();
        String result = name+"\n";
        

        //Assume it is integers
        if (STRUCT_SIZE == -1) {
            int[] data = new int[(buffer.capacity()/4)-headerSize];
            IntBuffer intBuffer = buffer.asIntBuffer();
            intBuffer.get(data);
            for (int i = 0; i < data.length; i++) {
                result += String.format("%d", data[i]);
                if (i < data.length - 1) {
                    result += "\n";
                }
            }
            return result+"\n";
        }

        // Calculate total struct size in bytes
        int structSizeInBytes = 0;
        for (int i = 0; i < types.length; i++) {
            structSizeInBytes += VariableType.getSize(types[i]);
        }

        // Start reading from the correct byte offset
        int startByteOffset = startIndex * structSizeInBytes + headerSize;
        
        // Iterate through each struct
        for (int structIndex = startIndex; structIndex < endIndex; structIndex++) {
            result += String.format("Index: %d\t (", structIndex);
            
            // Current byte position within the buffer
            int currentByteOffset = startByteOffset + (structIndex - startIndex) * structSizeInBytes;
            
            result += readEachField(buffer, currentByteOffset, types);
        }
        
        return result;
    }

    private String readEachField(ByteBuffer buffer, int currentByteOffset, VariableType[] types) {
        String result = "";
        // Read each field in the struct
        for (int fieldIndex = 0; fieldIndex < types.length; fieldIndex++) {
            VariableType type = types[fieldIndex];

            //System.out.println(buffer.get(currentByteOffset)+" "+Float.intBitsToFloat(buffer.getInt(currentByteOffset)));
            
            result += String.format("%d:", fieldIndex);
            
            if (type == VariableType.FLOAT) {
                int intValue = buffer.getInt(currentByteOffset);
                result += String.format("%."+PRECISION+"f", Float.intBitsToFloat(intValue));
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