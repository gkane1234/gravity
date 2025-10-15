package com.grumbo.gpu;

import static org.lwjgl.opengl.GL43C.*;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * SSBO class for creating SSBO objects on the GPU
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public class SSBO {

    // Precision for floating point numbers
    private static final int PRECISION = 2; 

    // SSBO Bindings set in common.glsl:

    // layout(std430, binding = 0)  buffer SimulationValues       { ... } sim;
    // layout(std430, binding = 1)  buffer BodiesIn               { Body bodies[]; } srcB;
    // layout(std430, binding = 2)  buffer BodiesOut              { Body bodies[]; } dstB;
    // layout(std430, binding = 3)  buffer ParentsAndLocks        { uint parentsAndLocks[]; };
    // layout(std430, binding = 4)  buffer InternalNodes          { Node internalNodes[]; };
    // layout(std430, binding = 5)  buffer InternalNodesAABB      { NodeAABB internalNodesAABB[]; };
    // layout(std430, binding = 6)  buffer MortonInOut            { mortonInOut mortonInOut[]; };
    // layout(std430, binding = 7)  buffer IndexInOut             { uint indexInOut[]; };
    // layout(std430, binding = 8)  buffer WorkQueueInOut         { uint headIn; uint headOut; uint tailIn; uint tailOut; uint itemsInOut[]; };
    // layout(std430, binding = 9)  buffer RadixWGHist            { uint wgHist[]; };
    // layout(std430, binding = 10) buffer RadixWGScanned         { uint wgScanned[]; };
    // layout(std430, binding = 11) buffer RadixBucketTotalsAndAABB { uint bucketTotals[NUM_BUCKETS]; uint globalBase[NUM_BUCKETS]; };
    // layout(std430, binding = 12) buffer MergeTasks             { uint mergeTasksHead; uint mergeTasksTail; uvec2 mergeTasks[]; };

    public static final int SIMULATION_VALUES_BINDING = 0;
    public static final int BODIES_IN_BINDING = 1;
    public static final int BODIES_OUT_BINDING = 2;
    public static final int PARENTS_AND_LOCKS_BINDING = 3;
    public static final int INTERNAL_NODES_BINDING = 4;
    public static final int INTERNAL_NODES_AABB_BINDING = 5;
    public static final int MORTON_IN_OUT_BINDING = 6;
    public static final int INDEX_IN_OUT_BINDING = 7;
    public static final int WORK_QUEUE_IN_OUT_BINDING = 8;
    public static final int RADIX_WG_HIST_BINDING = 9;
    public static final int RADIX_WG_SCANNED_BINDING = 10;
    public static final int RADIX_BUCKET_TOTALS_AND_AABB_BINDING = 11;
    public static final int MERGE_TASKS_BINDING = 12;


    // Buffer location of the SSBO
    private int bufferLocation;
    private ByteBuffer bufferCache;
    // Binding of the SSBO
    private final int bufferBinding;
    // Function to get the size of the SSBO, this is used by default to size the SSBO
    private sizeFunction sizeFunction;
    // Function to set the data of the SSBO, this is used if the size function is null
    private dataFunction dataFunction;
    // Name of the SSBO
    private String name;
    private int size;

    // Types of the fields of the struct
    private GLSLVariable SSBOLayout;
    private HashMap<String, GLSLVariable> SSBOLayoutMap;
    private HashMap<String, Integer> SSBOByteOffsets;
    private Map<Integer, Integer[]> ProgramBindingRanges;
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
    public SSBO(int binding, sizeFunction sizeFunction, dataFunction dataFunction, String name, GLSLVariable SSBOLayout) {
        this.bufferBinding = binding;
        this.sizeFunction = sizeFunction;
        this.dataFunction = dataFunction;
        this.name = name;
        this.bufferLocation = glGenBuffers();
        this.SSBOLayout = SSBOLayout;
        this.SSBOLayoutMap = new HashMap<>();
        this.SSBOByteOffsets = new HashMap<>();
        this.ProgramBindingRanges = new HashMap<>();
        addToSSBOLayoutMap(SSBOLayout, 0);
    }
    /**
     * Adds the variable to the SSBOLayoutMap.
     * @param variable the variable to add
     * @param baseOffset the base offset of the variable
     */
    private void addToSSBOLayoutMap(GLSLVariable variable, int baseOffset) {
        SSBOLayoutMap.put(variable.name, variable);
        SSBOByteOffsets.put(variable.name, baseOffset);
        if (variable.dataType == VariableType.STRUCT) {
            int internalOffset = 0;
            for (GLSLVariable subVariable : variable.variables) {
                addToSSBOLayoutMap(subVariable, baseOffset + internalOffset);
                internalOffset += subVariable.byteSize * subVariable.size;
            }
        }
    }
    /**
     * Constructor for the SSBO class
     * @param binding the binding of the SSBO given by OpenGL
     * @param sizeFunction the function to get the size of the SSBO
     * @param name the name of the SSBO
     */
    public SSBO(int binding, sizeFunction size, String name) {
        this(binding, size, null, name, null);
    }
    /**
     * Constructor for the SSBO class
     * @param binding the binding of the SSBO given by OpenGL
     * @param dataFunction the function to set the data of the SSBO
     * @param name the name of the SSBO
     */
    public SSBO(int binding, dataFunction dataFunction, String name) {
        this(binding, null, dataFunction, name, null);
    }
    /**
     * Constructor for the SSBO class
     * @param binding the binding of the SSBO given by OpenGL
     * @param sizeFunction the function to get the size of the SSBO
     * @param name the name of the SSBO
     * @param structSize the size of the struct in this SSBO in bytes
     * @param types the types of the fields of the struct
     */
    public SSBO(int binding, sizeFunction size, String name, GLSLVariable SSBOLayout) {
        this(binding, size, null, name, SSBOLayout);
    }
    /**
     * Constructor for the SSBO class
     * @param binding the binding of the SSBO given by OpenGL
     * @param dataFunction the function to set the data of the SSBO
     * @param name the name of the SSBO
     * @param structSize the size of the struct in this SSBO in bytes
     * @param types the types of the fields of the struct
     */
    public SSBO(int binding, dataFunction dataFunction, String name, GLSLVariable SSBOLayout) {
        this(binding, null, dataFunction, name, SSBOLayout);
    }


    /**
     * Sets the SSBOLayout.
     * @param SSBOLayout the SSBOLayout
     */
    public void setSSBOLayout(GLSLVariable SSBOLayout) {
        this.SSBOLayout = SSBOLayout;
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
    public void bind(int startIndex, int endIndex) {
        glBindBufferRange(GL_SHADER_STORAGE_BUFFER, bufferBinding, bufferLocation, startIndex, endIndex);
    }

    /**
     * Binds the SSBO.
     */
    public void bind() {
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, bufferBinding, bufferLocation);
    }

    /**
     * Binds the SSBO to a program.
     * @param program the program to bind the SSBO to
     */
    public void bind(int program) {
        if (ProgramBindingRanges.containsKey(program)) {
            bind(ProgramBindingRanges.get(program)[0], ProgramBindingRanges.get(program)[1]);
        } else {
            bind();
        }
    }


    /**
     * Sets the program binding range.
     * @param program the program to set the binding range for
     * @param startIndex the start index of the binding range
     * @param endIndex the end index of the binding range
     */
    public void setProgramBindingRange(int program, int startIndex, int endIndex) {
        ProgramBindingRanges.put(program, new Integer[] {startIndex, endIndex});
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
            size = sizeFunction.getSize();
            glBufferData(GL_SHADER_STORAGE_BUFFER, size, GL_DYNAMIC_COPY);

        } else {
            ByteBuffer data = dataFunction.setData();
            size = data.capacity();
            glBufferData(GL_SHADER_STORAGE_BUFFER, data, GL_DYNAMIC_COPY);
        }
        unbind();
    }

    /**
     * Gets the buffer data from the SSBO
     * @return the buffer data from the SSBO
     */
    public ByteBuffer getBuffer() {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, bufferLocation);
        if (bufferCache == null) {
            refreshCache();
        }
        return bufferCache;
    }

    public int getSize() {
        return size;
    }

    /**
     * Refreshes the cache.
     */
    public void refreshCache() {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, bufferLocation);
        ByteBuffer mapped = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        mapped.order(java.nio.ByteOrder.nativeOrder());
        ByteBuffer copy = ByteBuffer.allocateDirect(mapped.remaining()).order(java.nio.ByteOrder.nativeOrder());
        copy.put(mapped);
        copy.flip();
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        bufferCache = copy;
    }



    /**
     * Gets the data from the SSBO and attempts to format it using structSize and types
     * @param startIndex the index of the first data to get
     * @param endIndex the index of the last data to get
     * @return the data from the SSBO
     */
    public String getDataAsString(String variableName, int startIndex, int endIndex, boolean updateCache) {
        GLSLVariable variable = SSBOLayoutMap.get(variableName);
        int baseOffset = SSBOByteOffsets.getOrDefault(variableName, 0);
        if (updateCache) {
            refreshCache();
        }
        ByteBuffer buf = getBuffer();
        return variable.getDataAsStringAt(buf, baseOffset, startIndex, endIndex);
    }
    /**
     * Gets the data as a string.
     * @param variableName the name of the variable
     * @param startIndex the start index of the data
     * @param endIndex the end index of the data
     * @return the data as a string
     */
    public String getDataAsString(String variableName, int startIndex, int endIndex) {
        return getDataAsString(variableName, startIndex, endIndex, true);
    }

    /**
     * Gets the data as a string.
     * @param variableNames the names of the variables
     * @param startIndex the start index of the data
     * @param endIndex the end index of the data
     * @return the data as a string
     */
    public String getDataAsString(String[] variableNames, int startIndex, int endIndex, boolean updateCache) {
        StringBuilder sb = new StringBuilder();
        for (String variableName : variableNames) {
            sb.append(getDataAsString(variableName, startIndex, endIndex, updateCache));
        }
        return sb.toString();
    }
    /**
     * Gets the data as a string.
     * @param variableNames the names of the variables
     * @param startIndex the start index of the data
     * @param endIndex the end index of the data
     * @return the data as a string
     */
    public String getDataAsString(String[] variableNames, int startIndex, int endIndex) {
        return getDataAsString(variableNames, startIndex, endIndex, true);
    }

    /**
     * Gets the data as a string.
     * @param variableName the name of the variable
     * @param updateCache whether to update the cache
     * @return the data as a string
     */
    public String getDataAsString(String variableName, boolean updateCache) {
        return getDataAsString(variableName, 0, SSBOLayoutMap.get(variableName).size, updateCache);
    }

    /**
     * Gets the data as a string.
     * @param variableName the name of the variable
     * @return the data as a string
     */
    public String getDataAsString(String variableName) {
        return getDataAsString(variableName, true);
    }


    /**
     * Gets the data as an object.
     * @param variableName the name of the variable
     * @param startIndex the start index of the data
     * @param endIndex the end index of the data
     * @param updateCache whether to update the cache
     * @return the data as an object
     */
    public Object[] getData(String variableName, int startIndex, int endIndex, boolean updateCache) {
        GLSLVariable variable = SSBOLayoutMap.get(variableName);
        if (updateCache) {
            refreshCache();
        }
        ByteBuffer buffer = getBuffer();
        int count = endIndex - startIndex;
        Object[] out = new Object[count];
        int baseOffset = SSBOByteOffsets.getOrDefault(variableName, 0);
        for (int i = 0; i < count; i++) {
            Object[] row = variable.getDataAt(buffer, baseOffset, startIndex + i);
            if (variable.dataType != VariableType.STRUCT && row.length == 1) {
                out[i] = row[0];
            } else {
                out[i] = row;
            }
        }
        return out;
    }

    /**
     * Gets the data as an object.
     * @param variableName the name of the variable
     * @param startIndex the start index of the data
     * @param endIndex the end index of the data
     * @return the data as an object
     */
    public Object[] getData(String variableName, int startIndex, int endIndex) {
        return getData(variableName, startIndex, endIndex, true);
    }


    /**
     * Gets the data as an object.
     * @param variableName the name of the variable
     * @param updateCache whether to update the cache
     * @return the data as an object
     */
    public Object[] getData(String variableName, boolean updateCache) {
        int size = SSBOLayoutMap.get(variableName).size;
        return getData(variableName, 0, size, updateCache);
    }

    /**
     * Gets the data as an object.
     * @param variableName the name of the variable
     * @return the data as an object
     */
    public Object[] getData(String variableName) {
        return getData(variableName, true);
    } 
    /**
     * Gets the data as an integer.
     * @param variableName the name of the variable
     * @param updateCache whether to update the cache
     * @return the data as an integer
     */
    public Integer getIntegerData(String variableName, boolean updateCache) {
        return (Integer)getData(variableName, 0, 1, updateCache)[0];
    }
    /**
     * Gets the data as an integer.
     * @param variableName the name of the variable
     * @return the data as an integer
     */
    public Integer getIntegerData(String variableName) {
        return getIntegerData(variableName, true);
    }

    /**
     * Gets the data as a long.
     * @param variableName the name of the variable
     * @param updateCache whether to update the cache
     * @return the data as a long
     */
    public Long getLongData(String variableName, boolean updateCache) {
        return (Long)getData(variableName, 0, 1, updateCache)[0];
    }
    /**
     * Gets the data as a long.
     * @param variableName the name of the variable
     * @return the data as a long
     */
    public Long getLongData(String variableName) {
        return getLongData(variableName, true);
    }

    /**
     * Gets the data as a float.
     * @param variableName the name of the variable
     * @param updateCache whether to update the cache
     * @return the data as a float
     */
    public Float getFloatData(String variableName, boolean updateCache) {
        return (Float)getData(variableName, 0, 1, updateCache)[0];
    }
    /**
     * Gets the data as a float.
     * @param variableName the name of the variable
     * @return the data as a float
     */
    public Float getFloatData(String variableName) {
        return getFloatData(variableName, true);
    }

    /**
     * Gets the data as an object.
     * @param variableNames the names of the variables
     * @param startIndex the start index of the data
     * @param endIndex the end index of the data
     * @param updateCache whether to update the cache
     * @return the data as an object
     */
    public Object[][] getData(String[] variableNames, int startIndex, int endIndex, boolean updateCache) {
        List<Object[]> data = new ArrayList<>();
        for (int i = 0; i < variableNames.length; i++) {
            if (getData(variableNames[i], startIndex, endIndex, updateCache) != null) {
                data.add(getData(variableNames[i], startIndex, endIndex, updateCache));
            }
        }
        return data.toArray(new Object[0][]);
    }

    /**
     * Gets the data as an object.
     * @param variableNames the names of the variables
     * @param updateCache whether to update the cache
     * @return the data as an object
     */
    public Object[][] getData(String[] variableNames, boolean updateCache) {
        return getData(variableNames, 0, SSBOLayoutMap.get(variableNames[0]).size, updateCache);
    }

    /**
     * Gets the data as an object.
     * @param variableNames the names of the variables
     * @return the data as an object
     */
    public Object[][] getData(String[] variableNames) {
        return getData(variableNames, true);
    }




    /**
     * Gets the data as an object.
     * @param variableNames the names of the variables
     * @param startIndex the start index of the data
     * @param endIndex the end index of the data
     * @return the data as an object
     */
    public Object[][] getData(String[] variableNames, int startIndex, int endIndex) {
        return getData(variableNames, startIndex, endIndex, true);
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