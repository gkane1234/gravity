package com.grumbo.gpu;

/**
 * Uniform class for creating uniforms to be uploaded to the GPU.
 * The generic type T must be of VariableType.
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public class Uniform<T> {

    // uniform float softening;
    // uniform float theta;
    // uniform float dt;
    // uniform uint numBodies;
    // uniform float elasticity;
    // uniform float density;
    // uniform float restitution;
    // uniform bool collision;
    // uniform uint numWorkGroups; // number of workgroups dispatched for histogram/scatter
    // uniform uint passShift;
    // Name of the uniform
    private String name;
    // Function to get the value of the uniform
    private ValueFunction<T> value;
    private VariableType type;
    /**
     * Function to get the value of the uniform
     * @param <T> the type of the value
     */
    public interface ValueFunction<T> {
        T getValue();
    }

    /**
     * Constructor for the Uniform class
     * @param name the name of the uniform
     * @param value the function to get the value of the uniform
     * @param unsigned whether the uniform is unsigned
     */
    public Uniform(String name, ValueFunction<T> value, VariableType type) {
        this.name = name;
        this.type = type;
        this.value = value;

        T sample = value.getValue();
        if (!type.javaClass.isInstance(sample)) {
            throw new IllegalArgumentException(
                "Uniform type mismatch: " + type.javaClass.getSimpleName() + " != " + sample.getClass().getSimpleName());
        }
        
    }
    /**
     * Sets the value of the uniform
     * @param value the function to get the value of the uniform
     */
    public void setValue(ValueFunction<T> value) {
        this.value = value;
    }

    /**
     * Gets the value of the uniform
     * @return the value of the uniform
     */
    public T getValue() {
        return value.getValue();
    }

    /**
     * Gets the name of the uniform
     * @return the name of the uniform
     */
    public String getName() {
        return name;
    }

    /**
     * Uploads the uniform to the shader.
     * @param program the program to upload the uniform to
     */
    public void uploadToShader(int program) {
        type.uploadToShader(program, name, getValue());
    }
}