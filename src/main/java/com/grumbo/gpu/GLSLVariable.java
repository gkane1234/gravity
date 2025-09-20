package com.grumbo.gpu;

import java.nio.ByteBuffer;

/**
 * GLSLVariable class for creating GLSL variable emulations in java.
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public class GLSLVariable{

    //The type of the variable if it is a primitive type
    public VariableType dataType;
    //The variables that make up the variable if it is a struct.
    public GLSLVariable[] variables;
    //The name of the variable.
    public String name;
    //The array size of the variable.
    public int size;
    //The byte size of the variable.
    public int byteSize;
    /**
     * Constructor for the GLSLVariable class.
     * @param dataType the type of the variable if it is a primitive type -- this is used by default
     * @param variables the variables that make up the variable if it is a struct -- this is used if dataType is null
     * @param name the name of the variable.
     * @param size the array size of the variable.
     * @param byteSize the byte size of the variable.
     */
    private GLSLVariable(VariableType dataType, GLSLVariable[] variables, String name, int size, int byteSize) {
        this.dataType = dataType;
        this.name = name;
        this.size = size;
        this.variables = variables;
        this.byteSize = byteSize;
    }
    /**
     * Constructor for the GLSLVariable class.
     * @param dataType the type of the variable if it is a primitive type
     * @param name the name of the variable.
     * @param size the array size of the variable.
     */
    public GLSLVariable(VariableType dataType, String name, int size) {
        this(dataType, new GLSLVariable[] {null}, name, size, dataType.getSize());
    }
    /**
     * Constructor for the GLSLVariable class.
     * @param dataType the type of the variable if it is a primitive type
     * @param name the name of the variable.
     */
    public GLSLVariable(VariableType dataType, String name) {
        this(dataType, name, 1);
    }
    /**
     * Constructor for the GLSLVariable class.
     * @param dataType the type of the variable if it is a primitive type
     */
    public GLSLVariable(VariableType dataType) {
        this(dataType, dataType.name(), 1);
    }

    /**
     * Constructor for the GLSLVariable class. Creates an array of a given size of the given struct
     * @param variable the variable to make the array
     * @param name the name of the variable.
     * @param size the array size of the variable.
     */
    public GLSLVariable(GLSLVariable variable, String name, int size) {
        this(new GLSLVariable[] {variable}, name, size);
    }
    /**
     * Constructor for the GLSLVariable class. Creates an array of a given size of the given struct
     * @param variable the variable to make the array
     * @param size the array size of the variable.
     */
    public GLSLVariable(GLSLVariable variable, int size) {
        this(new GLSLVariable[] {variable}, variable.name, size);
    }
    /**
     * Constructor for the GLSLVariable class. Creates a singlton array of the given struct
     * @param variable the variable to make the array
     */
    public GLSLVariable(GLSLVariable variable) {
        this(new GLSLVariable[] {variable}, variable.name, 1);
    }

    /**
     * Constructor for the GLSLVariable class. Creates a struct of the given variables
     * @param variables the variables to make the struct
     */
    public GLSLVariable(GLSLVariable[] variables) {
        this(variables, variables[0].name, 1);
    }
    /**
     * Constructor for the GLSLVariable class. Creates a struct of the given variables
     * @param variables the variables to make the struct
     * @param name the name of the variable.
     */
    public GLSLVariable(GLSLVariable[] variables, String name) {
        this(variables, name, 1);
    }
    /**
     * Constructor for the GLSLVariable class. Creates an array of the struct of the given variables
     * @param variables the variables to make the struct
     * @param name the name of the variable.
     * @param size the array size of the variable.
     */
    public GLSLVariable(GLSLVariable[] variables, String name, int size) {
        this(VariableType.STRUCT, variables, name, size, -1);
        byteSize = 0;
        for (GLSLVariable variable : variables) {
            byteSize += variable.size*variable.byteSize;
        }
    }

    /**
     * Gets the primitive or struct as a string.
     * @param buffer the buffer to get the data from
     * @param index the index of the data
     * @param includeName whether to include the name of the variable
     * @return the data as a string
     */
    public String getDataAsString(ByteBuffer buffer, int index,boolean includeName) {
        validateIndex(index);
        int baseOffset = index * byteSize;
        if (dataType != VariableType.STRUCT) {
            return String.format("%s %d: %s", includeName ? name+" " : "", index, dataType.getDataAsString(buffer, baseOffset));
        }
        return readSingleAsString(buffer, baseOffset, includeName);
    }

    /**
     * Gets the primitive or struct as a string.
     * @param buffer the buffer to get the data from
     * @param index the index of the data
     * @return the data as a string
     */
    public String getDataAsString(ByteBuffer buffer, int index) {
        return getDataAsString(buffer, index, true);
    }

    /**
     * Gets the array of primitives or structs as a string and includes the name of the variable.
     * @param buffer the buffer to get the data from
     * @param startIndex the start index of the data
     * @param endIndex the end index of the data
     * @return the data as a string
     */
    public String getDataAsString(ByteBuffer buffer, int startIndex, int endIndex) {
        int[] validatedIndices = validateIndexRange(startIndex, endIndex);
        startIndex = validatedIndices[0];
        endIndex = validatedIndices[1];
        StringBuilder sb = new StringBuilder();
        sb.append(name+" ");
        for (int i = startIndex; i < endIndex; i++) {
            sb.append(getDataAsString(buffer, i, false));
            sb.append(", ");
        }
        return sb.toString();
    }

    /**
     * Gets the primitive or struct as an object.
     * @param buffer the buffer to get the data from
     * @param index the index of the data
     * @return the data as an object
     */
    public Object[] getData(ByteBuffer buffer, int index) {
        validateIndex(index);
        int baseOffset = index * byteSize;
        if (dataType != VariableType.STRUCT) {
            return new Object[] { dataType.getData(buffer, baseOffset) };
        }
        return (Object[]) readSingle(buffer, baseOffset);
    }

    /**
     * Gets the array of primitives or structs as an object.
     * @param buffer the buffer to get the data from
     * @param startIndex the start index of the data
     * @param endIndex the end index of the data
     * @return the data as an object
     */
    public Object[][] getData(ByteBuffer buffer, int startIndex, int endIndex) {
        int[] validatedIndices = validateIndexRange(startIndex, endIndex);
        startIndex = validatedIndices[0];
        endIndex = validatedIndices[1];
        Object[][] data = new Object[endIndex - startIndex][];
        for (int i = startIndex; i < endIndex; i++) {
            data[i - startIndex] = getData(buffer, i);
        }
        return data;
    }

    /**
     * Gets the primitive or struct as an object at specific byte offset.
     * Returns multiple objects if the variable is a struct.
     * @param buffer the buffer to get the data from
     * @param baseByteOffset the base byte offset of the data
     * @param index the index of the data
     * @return the data as an object
     */
    public Object[] getDataAt(ByteBuffer buffer, int baseByteOffset, int index) {
        validateIndex(index);
        int baseOffset = baseByteOffset + index * byteSize;
        if (dataType != VariableType.STRUCT) {
            return new Object[] { dataType.getData(buffer, baseOffset) };
        }
        return (Object[]) readSingle(buffer, baseOffset);
    }

    /**
     * Gets the array of primitives or structs as an object at specific byte offset.
     * @param buffer the buffer to get the data from
     * @param baseByteOffset the base byte offset of the data
     * @param startIndex the start index of the data
     * @param endIndex the end index of the data
     * @return the data as an object
     */
    public Object[][] getDataAt(ByteBuffer buffer, int baseByteOffset, int startIndex, int endIndex) {
        int[] validatedIndices = validateIndexRange(startIndex, endIndex);
        startIndex = validatedIndices[0];
        endIndex = validatedIndices[1];
        Object[][] data = new Object[endIndex - startIndex][];
        for (int i = startIndex; i < endIndex; i++) {
            data[i - startIndex] = getDataAt(buffer, baseByteOffset, i);
        }
        return data;
    }

    /**
     * Gets the primitive or struct as a string at specific byte offset.
     * @param buffer the buffer to get the data from
     * @param baseByteOffset the base byte offset of the data
     * @param index the index of the data
     * @param includeName whether to include the name of the variable
     * @return the data as a string
     */
    public String getDataAsStringAt(ByteBuffer buffer, int baseByteOffset, int index, boolean includeName) {
        validateIndex(index);
        int baseOffset = baseByteOffset + index * byteSize;
        if (dataType != VariableType.STRUCT) {
            return String.format("%s %d: %s", includeName ? name+" " : "", index, dataType.getDataAsString(buffer, baseOffset));
        }
        return readSingleAsString(buffer, baseOffset, includeName);
    }

    /**
     * Gets the array of primitives or structs as a string at specific byte offset.
     * @param buffer the buffer to get the data from
     * @param baseByteOffset the base byte offset of the data
     * @param startIndex the start index of the data
     * @param endIndex the end index of the data
     * @return the data as a string
     */
    public String getDataAsStringAt(ByteBuffer buffer, int baseByteOffset, int startIndex, int endIndex) {
        int[] validatedIndices = validateIndexRange(startIndex, endIndex);
        startIndex = validatedIndices[0];
        endIndex = validatedIndices[1];
        StringBuilder sb = new StringBuilder();
        sb.append(name+" ");
        for (int i = startIndex; i < endIndex; i++) {
            sb.append(i+": ");
            sb.append(getDataAsStringAt(buffer, baseByteOffset, i, false));
            sb.append(", ");
        }
        return sb.toString();
    }

    /**
     * Reads the primitive or struct as an object with proper byte offset handling.
     * @param buffer the buffer to get the data from
     * @param byteOffset the byte offset of the data
     * @return the data as an object
     */
    private Object readSingle(ByteBuffer buffer, int byteOffset) {
        if (dataType != VariableType.STRUCT) {
            return dataType.getData(buffer, byteOffset);
        }
        Object[] result = new Object[variables.length];
        int internalOffset = 0;
        for (int i = 0; i < variables.length; i++) {
            GLSLVariable child = variables[i];
            int childBase = byteOffset + internalOffset;
            if (child.size == 1) {
                if (child.dataType != VariableType.STRUCT) {
                    result[i] = child.dataType.getData(buffer, childBase);
                } else {
                    result[i] = child.readSingle(buffer, childBase);
                }
            } else {
                Object[] arrayValues = new Object[child.size];
                for (int k = 0; k < child.size; k++) {
                    int elementOffset = childBase + k * child.byteSize;
                    if (child.dataType != VariableType.STRUCT) {
                        arrayValues[k] = child.dataType.getData(buffer, elementOffset);
                    } else {
                        arrayValues[k] = child.readSingle(buffer, elementOffset);
                    }
                }
                result[i] = arrayValues;
            }
            internalOffset += child.byteSize * child.size;
        }
        return result;
    }

    /**
     * Reads the primitive or struct as a string with proper byte offset handling.
     * @param buffer the buffer to get the data from
     * @param byteOffset the byte offset of the data
     * @param includeName whether to include the name of the variable
     * @return the data as a string
     */
    private String readSingleAsString(ByteBuffer buffer, int byteOffset, boolean includeName) {
        if (dataType != VariableType.STRUCT) {
            if (byteOffset + dataType.getSize() > buffer.limit()) {
                return String.format("%s<OOB>", includeName ? name+" " : "");
            }
            return String.format("%s%s", includeName ? name+" " : "", dataType.getDataAsString(buffer, byteOffset));
        }
        if (byteOffset + byteSize > buffer.limit()) {
            return String.format("%s<OOB struct>", includeName ? name+" " : "");
        }
        StringBuilder sb = new StringBuilder();
        if (includeName) sb.append(name+" ");
        int internalOffset = 0;
        for (int i = 0; i < variables.length; i++) {
            GLSLVariable child = variables[i];
            int childBase = byteOffset + internalOffset;
            if (child.size == 1) {
                if (child.dataType != VariableType.STRUCT) {
                    sb.append(child.name+" ");
                    if (childBase + child.dataType.getSize() > buffer.limit()) {
                        sb.append("<OOB>");
                    } else {
                        sb.append(child.dataType.getDataAsString(buffer, childBase));
                    }
                } else {
                    sb.append(child.readSingleAsString(buffer, childBase, true));
                }
            } else {
                sb.append(child.name+" [");
                for (int k = 0; k < child.size; k++) {
                    int elementOffset = childBase + k * child.byteSize;
                    if (child.dataType != VariableType.STRUCT) {
                        if (elementOffset + child.dataType.getSize() > buffer.limit()) {
                            sb.append("<OOB>");
                            if (k < child.size - 1) sb.append(", ");
                            break;
                        }
                        sb.append(child.dataType.getDataAsString(buffer, elementOffset));
                    } else {
                        if (elementOffset + child.byteSize > buffer.limit()) {
                            sb.append("<OOB struct>");
                            if (k < child.size - 1) sb.append(", ");
                            break;
                        }
                        sb.append(child.readSingleAsString(buffer, elementOffset, false));
                    }
                    if (k < child.size - 1) sb.append(", ");
                }
                sb.append("]");
            }
            if (i < variables.length - 1) sb.append(", ");
            internalOffset += child.byteSize * child.size;
        }
        return sb.toString();
    }

    /**
     * Validates and returns the index range.
     * If the index is out of range, the index is clamped to the start or end of the array.
     * If the start index is greater than the end index, an exception is thrown.
     * @param startIndex the start index of the data    
     * @param endIndex the end index of the data
     * @return the validated index range
     * @throws IllegalArgumentException if the start index is greater than the end index
     */
    private int[] validateIndexRange(int startIndex, int endIndex) {
        try {
            validateIndex(startIndex);
        } catch (IllegalArgumentException e) {
            startIndex = 0;
        }
        try {
            validateIndex(endIndex-1);
        } catch (IllegalArgumentException e) {
            endIndex = size-1;
        }
        if (startIndex > endIndex) {
            throw new IllegalArgumentException("Start index must be less than end index for variable: " + name);
        }
        return new int[] {startIndex, endIndex};
    }
    /**
     * Validates the index.
     * If the index is out of range, an IllegalArgumentException is thrown.
     * @param index the index to validate
     * @throws IllegalArgumentException if the index is out of range
     */
    private void validateIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException("Index out of range: " + index + " for variable: " + name + " with size: " + size);
        }
    }
}