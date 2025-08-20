package com.grumbo.gpu;

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
    public interface valueFunction<T> {
        T getValue();
    }

    public Uniform(String name, valueFunction<T> value, boolean unsigned) {
        this.name = name;
        this.value = value;
        this.unsigned = unsigned;
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
}