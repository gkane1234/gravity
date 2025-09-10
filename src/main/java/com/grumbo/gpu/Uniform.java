package com.grumbo.gpu;

import static org.lwjgl.opengl.GL43C.*;

import java.util.Arrays;

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
    private String name;
    private valueFunction<T> value;
    private boolean unsigned = false;


    private static final Class<?>[] ALLOWED_TYPES = new Class<?>[] { Integer.class, Float.class, Boolean.class, Long.class };
    public interface valueFunction<T> {
        T getValue();
    }

    public Uniform(String name, valueFunction<T> value, boolean unsigned) {
        this.name = name;
        this.value = value;
        this.unsigned = unsigned;
        if (value != null && value.getValue() != null) {
            Class<?> actualType = value.getValue().getClass();
            if (!Arrays.asList(ALLOWED_TYPES).contains(actualType)) {
                throw new IllegalArgumentException("Invalid type: " + actualType);
            }
        }
        
    }

    public Uniform(String name, valueFunction<T> value) {
        this(name, value, false);
    }
    
    public void setValue(valueFunction<T> value) {
        this.value = value;
    }

    public T getValue() {
        return value.getValue();
    }

    public String getName() {
        return name;
    }

    public boolean isUnsigned() {
        return unsigned;
    }

    public void uploadToShader(int program) {
        //System.out.println("Uploading " + name + " to shader " + program + " with value " + getValue());
        if (getValue().getClass().equals(Integer.class)) {
            if (unsigned) {
                glUniform1ui(glGetUniformLocation(program, name), (Integer) getValue());
            } else {
                glUniform1i(glGetUniformLocation(program, name), (Integer) getValue());
            }
        } else if (getValue().getClass().equals(Float.class)) {
            glUniform1f(glGetUniformLocation(program, name), (Float) getValue());
        } else if (getClass().equals(Boolean.class)) {
            glUniform1i(glGetUniformLocation(program, name), (Boolean) getValue() ? 1 : 0);
        }
    }
}