package com.grumbo.gpu;

import static org.lwjgl.opengl.GL43C.*;
import java.nio.ByteBuffer;
/**
 * VariableType enum for the types of GLSL variables that can be used emulated in java.
 * Includes upload to shader function. 
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public enum VariableType {
    FLOAT(Float.class),
    INT(Integer.class),
    UINT(Integer.class, (value) -> (Integer) value >= 0),
    BOOL(Boolean.class),
    UINT64(Long.class),
    STRUCT(GLSLVariable.class),
    PADDING(Void.class);
    
    //The precision of the variable type.
    private static final int PRECISION = 5;

    public final Class<?> javaClass;
    public final validator v;
    /**
     * Validator interface for the variable type.
     * Used for specific conditions that are not covered by the variable type. (i.e unsigned)
     * @param value the value to validate
     * @return true if the value is valid, false otherwise
     */
    private interface validator {
        boolean validate(Object value);
        
    }
    /**
     * Constructor for the variable type.
     * @param javaClass the java class of the variable type
     */
    VariableType(Class<?> javaClass) {
        this(javaClass, null);
    }

    /**
     * Constructor for the variable type.
     * @param javaClass the java class of the variable type
     * @param v the validator for the variable type
     */
    VariableType(Class<?> javaClass, validator v) {
        this.javaClass = javaClass;
        this.v = v;
    }
    /**
     * Uploads the value to the shader. Used in Uniform class.
     * @param program the program to upload the value to
     * @param name the name of the uniform
     * @param value the value to upload
     */
    public void uploadToShader(int program, String name, Object value) {
        if (!javaClass.isInstance(value)) {
            throw new IllegalArgumentException("Uniform type mismatch: " + javaClass.getSimpleName() + " != " + value.getClass().getSimpleName());
        }
        if (v != null && !v.validate(value)) {
            throw new IllegalArgumentException("Validator failed for value: " + value);
        }
        switch (this) {
            case INT:
                glUniform1i(glGetUniformLocation(program, name), (Integer) value);
                break;
            case UINT:
                glUniform1ui(glGetUniformLocation(program, name), (Integer) value);
                break;
            case FLOAT:
                glUniform1f(glGetUniformLocation(program, name), (Float) value);
                break;
            case BOOL:
                glUniform1ui(glGetUniformLocation(program, name), (Boolean) value ? 1 : 0);
                break;
            case UINT64:
                long longValue = (Long) value;
                int high = (int) (longValue >>> 32);
                int low = (int) longValue;
                glUniform2ui(glGetUniformLocation(program, name), high, low);
                break;
            default:
                throw new IllegalArgumentException("Invalid type: " + this);
        }
    }

    /**
     * Gets the size of the variable type in bytes
     * @param type the variable type
     * @return the size of the variable type in bytes
     */
    public int getSize() {
        switch (this) {
            case FLOAT:
                return 4;
            case INT:
                return 4;
            case UINT:
                return 4;
            case BOOL:
                return 1;
            case UINT64:
                return 8;
            case PADDING:
                return 4;
            default:
                throw new IllegalArgumentException("Invalid type: " + this);
        }
    }
    /**
     * Gets the data from the buffer.
     * @param buffer the buffer to get the data from
     * @param byteOffset the byte offset of the data
     * @return the data
     */
    public Object getData(ByteBuffer buffer, int byteOffset) {
        switch (this) {
            case FLOAT:
                return Float.intBitsToFloat(buffer.getInt(byteOffset));
            case UINT:
                return buffer.getInt(byteOffset);
            case INT:
                return buffer.getInt(byteOffset);
            case BOOL:
                return buffer.get(byteOffset) == 1;
            case UINT64:
                return buffer.getLong(byteOffset);
            case PADDING:
                return null;
            default:
                throw new IllegalArgumentException("Invalid type: " + this);
        }
    }
    /**
     * Gets the data as a string.
     * @param buffer the buffer to get the data from
     * @param byteOffset the byte offset of the data
     * @return the data as a string
     */
    public String getDataAsString(ByteBuffer buffer, int byteOffset) {
        switch (this) {
            case FLOAT:
                return String.format("%."+PRECISION+"f", Float.intBitsToFloat(buffer.getInt(byteOffset)));
            case UINT:
                return String.format("%d", buffer.getInt(byteOffset));
            case INT:
                return String.format("%d", buffer.getInt(byteOffset));
            case BOOL:
                return String.format("%b", buffer.get(byteOffset) == 1);
            case UINT64:
                return String.format("%d", buffer.getLong(byteOffset));
            case PADDING:
                return "PADDING";
            default:
                throw new IllegalArgumentException("Invalid type: " + this);
        }
    }
}