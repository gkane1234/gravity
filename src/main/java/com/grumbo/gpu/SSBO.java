package com.grumbo.gpu;

import static org.lwjgl.opengl.GL43C.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * SSBO class for creating SSBO objects on the GPU
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public class SSBO {

    // Precision for floating point numbers
    private static final int PRECISION = 2; 

    // SSBO Bindings set in bh_common.comp
    public static final int BODIES_IN_SSBO_BINDING = 0;
    public static final int BODIES_OUT_SSBO_BINDING = 1;
    public static final int FIXED_MORTON_IN_SSBO_BINDING = 2;
    public static final int FIXED_INDEX_IN_SSBO_BINDING = 3;
    public static final int NODES_SSBO_BINDING = 4;
    public static final int VALUES_SSBO_BINDING = 5;
    public static final int WG_HIST_SSBO_BINDING = 6;
    public static final int WG_SCANNED_SSBO_BINDING = 7;
    public static final int BUCKET_TOTALS_SSBO_BINDING = 8;
    public static final int FIXED_MORTON_OUT_SSBO_BINDING = 9;
    public static final int FIXED_INDEX_OUT_SSBO_BINDING = 10;
    public static final int WORK_QUEUE_SSBO_BINDING = 11;
    public static final int MERGE_QUEUE_SSBO_BINDING = 12;
    public static final int DEBUG_SSBO_BINDING = 13;
    public static final int WORK_QUEUE_B_SSBO_BINDING = 14;
    public static final int BODY_LOCKS_SSBO_BINDING = 15;


    // Buffer location of the SSBO
    private int bufferLocation;
    // Binding of the SSBO
    private final int bufferBinding;
    // Function to get the size of the SSBO, this is used by default to size the SSBO
    private sizeFunction sizeFunction;
    // Function to set the data of the SSBO, this is used if the size function is null
    private dataFunction dataFunction;
    // Name of the SSBO
    private String name;
    // Size of the struct in this SSBO in bytes
    private int STRUCT_SIZE;
    // Types of the fields of the struct
    private VariableType[] types;
    // Size of the header in this SSBO in bytes
    private int headerSize;
    // Types of the fields of the header
    private VariableType[] headerTypes;
    /**
     * Function to get the size of the SSBO, this or the data function is used.
     */
    public interface sizeFunction {
        int getSize();
    }
    /**
     * Function to set the data of the SSBO, this or the size function is used.
     */
    public interface dataFunction {
        ByteBuffer setData();
    }

    /**
     * Unbinds the SSBO 
     */
    public static void unBind() {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, 0);
    }
    /**
     * Constructor for the SSBO class
     * @param binding the binding of the SSBO set in bh_common.comp
     * @param sizeFunction the function to get the size of the SSBO. This is used by default to size the SSBO
     * @param dataFunction the function to set the data of the SSBO. This is used if the size function is null
     * @param name the name of the SSBO
     * @param structSize the size of the structin this SSBO in bytes
     * @param types the types of the fields of the struct (note that this does not support different arrays stored in the same SSBO)
     * @param headerSize the size of the header in this SSBO in bytes
     * @param headerTypes the types of the fields of the header
     */
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
    /**
     * Constructor for the SSBO class
     * @param binding the binding of the SSBO given by OpenGL
     * @param sizeFunction the function to get the size of the SSBO
     * @param name the name of the SSBO
     */
    public SSBO(int binding, sizeFunction size, String name) {
        this(binding, size, null, name, -1, null, 0, null);
    }
    /**
     * Constructor for the SSBO class
     * @param binding the binding of the SSBO given by OpenGL
     * @param dataFunction the function to set the data of the SSBO
     * @param name the name of the SSBO
     */
    public SSBO(int binding, dataFunction dataFunction, String name) {
        this(binding, null, dataFunction, name, -1, null, 0, null);
    }
    /**
     * Constructor for the SSBO class
     * @param binding the binding of the SSBO given by OpenGL
     * @param sizeFunction the function to get the size of the SSBO
     * @param name the name of the SSBO
     * @param structSize the size of the struct in this SSBO in bytes
     * @param types the types of the fields of the struct
     */
    public SSBO(int binding, sizeFunction size, String name, int structSize, VariableType[] types) {
        this(binding, size, null, name, structSize, types, 0, null);
    }
    /**
     * Constructor for the SSBO class
     * @param binding the binding of the SSBO given by OpenGL
     * @param dataFunction the function to set the data of the SSBO
     * @param name the name of the SSBO
     * @param structSize the size of the struct in this SSBO in bytes
     * @param types the types of the fields of the struct
     */
    public SSBO(int binding, dataFunction dataFunction, String name, int structSize, VariableType[] types) {
        this(binding, dataFunction, name, structSize, types, 0, null);
    }

    /**
     * Constructor for the SSBO class
     * @param binding the binding of the SSBO given by OpenGL
     * @param sizeFunction the function to get the size of the SSBO
     * @param name the name of the SSBO
     * @param structSize the size of the struct in this SSBO in bytes
     * @param types the types of the fields of the struct
     */
    public SSBO(int binding, sizeFunction size, String name, int structSize, VariableType[] types, int headerSize, VariableType[] headerTypes) {
        this(binding, size, null, name, structSize, types, headerSize, headerTypes);
    }
    /**
     * Constructor for the SSBO class
     * @param binding the binding of the SSBO given by OpenGL
     * @param dataFunction the function to set the data of the SSBO
     * @param name the name of the SSBO
     * @param structSize the size of the struct in this SSBO in bytes
     * @param types the types of the fields of the struct
     */
    public SSBO(int binding, dataFunction dataFunction, String name, int structSize, VariableType[] types, int headerSize, VariableType[] headerTypes) {
        this(binding, null, dataFunction, name, structSize, types, headerSize, headerTypes);
    }


    /**
     * Sets the size of the struct in this SSBO in bytes
     * @param structSize the size of the struct in this SSBO in bytes
     */
    public void setStructSize(int structSize) {
        this.STRUCT_SIZE = structSize;
    }

    /**
     * Sets the types of the fields of the struct
     * @param types the types of the fields of the struct
     */
    public void setTypes(VariableType[] types) {
        this.types = types;
    }

    /**
     * Sets the name of the SSBO
     * @param name the name of the SSBO
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Binds the SSBO
     */
    public void bind() {
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, bufferBinding, bufferLocation);
    }
    /**
     * Unbinds the SSBO
     */
    public void unbind() {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }

    /**
     * Creates the buffer for the SSBO
     */
    public void createBufferData() {
        bind();
        if (sizeFunction != null) {
            glBufferData(GL_SHADER_STORAGE_BUFFER, sizeFunction.getSize(), GL_DYNAMIC_COPY);
        } else {
            glBufferData(GL_SHADER_STORAGE_BUFFER, dataFunction.setData(), GL_DYNAMIC_COPY);
        }
        unbind();
    }

    /**
     * Gets the buffer data from the SSBO
     * @return the buffer data from the SSBO
     */
    public ByteBuffer getBuffer() {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, bufferLocation);
        ByteBuffer buffer = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        buffer.order(java.nio.ByteOrder.nativeOrder());
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0); // Unbind when done
        return buffer;
    }

    /**
     * Gets the header data from the SSBO
     * @return the header data from the SSBO
     */
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

    /**
     * Gets the header data from the SSBO as an array of integers
     * @return the header data from the SSBO as an array of integers
     */
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


    /**
     * Gets the data from the SSBO and attempts to format it using structSize and types
     * @param startIndex the index of the first data to get
     * @param endIndex the index of the last data to get
     * @return the data from the SSBO
     */
    public String getData(int startIndex, int endIndex) {
        ByteBuffer buffer = getBuffer();
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
    /**
     * Reads each field in the struct using types
     * @param buffer the buffer to read the fields from
     * @param currentByteOffset the current byte offset in the buffer
     * @param types the types of the fields in the struct
     * @return the string representation of the fields
     */
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


    
    /**
     * Gets the binding of the SSBO
     * @return the binding of the SSBO
     */
    public int getBinding() {
        return bufferBinding;
    }

    /**
     * Sets the buffer location of the SSBO
     * @param location the buffer location of the SSBO
     */
    public void setBufferLocation(int location) {
        this.bufferLocation = location;
    }

    /**
     * Gets the buffer location of the SSBO
     * @return the buffer location of the SSBO
     */
    public int getBufferLocation() {
        return bufferLocation;
    }

    /**
     * Deletes the SSBO
     */
    public void delete() {
        glDeleteBuffers(bufferLocation);
    }

    /**
     * Gets the name of the SSBO
     * @return the name of the SSBO
     */
    public String getName() {
        return name;
    }
}