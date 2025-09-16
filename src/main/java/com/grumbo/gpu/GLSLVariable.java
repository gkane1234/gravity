package com.grumbo.gpu;

import java.nio.ByteBuffer;

public class GLSLVariable{
    /**
     * VariableType enum for the types of variables that can be used in the shader.
     * Includes upload to shader function. 
     * @author Grumbo
     * @version 1.0
     * @since 1.0
     */
    

    public VariableType dataType;
    public GLSLVariable[] variables;
    public String name;
    public int size;
    public int byteSize;
    private GLSLVariable(VariableType dataType, GLSLVariable[] variables, String name, int size, int byteSize) {
        this.dataType = dataType;
        this.name = name;
        this.size = size;
        this.variables = variables;
        this.byteSize = byteSize;
    }
    public GLSLVariable(VariableType dataType, String name, int size) {
        this(dataType, new GLSLVariable[] {null}, name, size, dataType.getSize());
    }
    public GLSLVariable(VariableType dataType, String name) {
        this(dataType, name, 1);
    }
    public GLSLVariable(VariableType dataType) {
        this(dataType, dataType.name(), 1);
    }

    public GLSLVariable(GLSLVariable variable, String name, int size) {
        this(new GLSLVariable[] {variable}, name, size);
    }
    public GLSLVariable(GLSLVariable variable, int size) {
        this(new GLSLVariable[] {variable}, variable.name, size);
    }
    public GLSLVariable(GLSLVariable variable) {
        this(new GLSLVariable[] {variable}, variable.name, 1);
    }

    public GLSLVariable(GLSLVariable[] variables) {
        this(variables, variables[0].name, 1);
    }
    public GLSLVariable(GLSLVariable[] variables, String name) {
        this(variables, name, 1);
    }
    public GLSLVariable(GLSLVariable[] variables, String name, int size) {
        this(VariableType.STRUCT, variables, name, size, -1);
        byteSize = 0;
        for (GLSLVariable variable : variables) {
            byteSize += variable.size*variable.byteSize;
        }
    }

    public String getDataAsString(ByteBuffer buffer, int index,boolean includeName) {
        validateIndex(index);
        int baseOffset = index * byteSize;
        if (dataType != VariableType.STRUCT) {
            return String.format("%s %d: %s", includeName ? name+" " : "", index, dataType.getDataAsString(buffer, baseOffset));
        }
        return readSingleAsString(buffer, baseOffset, includeName);
    }

    public String getDataAsString(ByteBuffer buffer, int index) {
        return getDataAsString(buffer, index, true);
    }

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

    public Object[] getData(ByteBuffer buffer, int index) {
        validateIndex(index);
        int baseOffset = index * byteSize;
        if (dataType != VariableType.STRUCT) {
            return new Object[] { dataType.getData(buffer, baseOffset) };
        }
        return (Object[]) readSingle(buffer, baseOffset);
    }

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

    public Object[] getDataAt(ByteBuffer buffer, int baseByteOffset, int index) {
        validateIndex(index);
        int baseOffset = baseByteOffset + index * byteSize;
        if (dataType != VariableType.STRUCT) {
            return new Object[] { dataType.getData(buffer, baseOffset) };
        }
        return (Object[]) readSingle(buffer, baseOffset);
    }

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

    public String getDataAsStringAt(ByteBuffer buffer, int baseByteOffset, int index, boolean includeName) {
        validateIndex(index);
        int baseOffset = baseByteOffset + index * byteSize;
        if (dataType != VariableType.STRUCT) {
            return String.format("%s %d: %s", includeName ? name+" " : "", index, dataType.getDataAsString(buffer, baseOffset));
        }
        return readSingleAsString(buffer, baseOffset, includeName);
    }

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
    private void validateIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException("Index out of range: " + index + " for variable: " + name + " with size: " + size);
        }
    }
}