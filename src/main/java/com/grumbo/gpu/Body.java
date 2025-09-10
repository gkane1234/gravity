package com.grumbo.gpu;

import java.util.Arrays;
import java.nio.IntBuffer;
/**
 * Java Analog to the Body struct in the shader code:
 * struct Body { vec4 posMass; vec4 velDensity; vec4 color; };
 * 
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public class Body {
    public static final int POS_MASS_OFFSET = 0;
    public static final int VEL_DENSITY_OFFSET = 4;
    public static final int COLOR_OFFSET = 8;

    public static final int STRUCT_SIZE = 12; // in bytes
    public static final int HEADER_SIZE = 0;

    private float[] posMass;
    private float[] velDensity;
    private float[] color;
    
    public static final VariableType[] bodyTypes = new VariableType[] { 
        VariableType.FLOAT, VariableType.FLOAT, VariableType.FLOAT, VariableType.FLOAT, 
        VariableType.FLOAT, VariableType.FLOAT, VariableType.FLOAT, VariableType.FLOAT,
        VariableType.FLOAT, VariableType.FLOAT, VariableType.FLOAT, VariableType.FLOAT };

    /**
     * Constructor for the Body class.
     * @param posMass the position and mass of the body (x,y,z,mass)
     * @param velDensity the velocity and density of the body (xVel,yVel,zVel,density)
     * @param color the color of the body (r,g,b,a)
     */
    public Body(float[] posMass, float[] velDensity, float[] color) {
        this.posMass = posMass;
        this.velDensity = velDensity;
        this.color = color;
    }

    /**
     * Returns the generic empty body.
     * @return a dead body
     */
    public static Body deadBody() {
        return new Body(new float[4], new float[4], new float[4]);
    }

    /**
     * Reads a body from an int buffer that is taken from the SSBO
     * @param buffer the buffer to get the body from
     * @param index the index of the body in the buffer
     * @return the body
     */
    public static Body fromBuffer(IntBuffer buffer, int index) {
        float[] posMass = new float[4];
        float[] velDensity = new float[4];
        float[] color = new float[4];

        for (int i = 0; i < 4; i++) {
            posMass[i] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + POS_MASS_OFFSET + i));
            velDensity[i] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + VEL_DENSITY_OFFSET + i));
            color[i] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + COLOR_OFFSET + i));
        }
        return new Body(posMass, velDensity, color);
    }

    /**
     * Returns a string of a range of bodies in the buffer.
     * @param buffer the buffer to get the bodies from
     * @param start the start index of the bodies
     * @param end the end index of the bodies
     * @return the string of all the bodies
     */
    public static String getBodies(IntBuffer buffer, int start, int end) {
        String bodiesString = "";
        for (int i = start; i < end; i++) {
            bodiesString += Body.fromBuffer(buffer, i).toString() + "\n";
        }
        return bodiesString;
    }

    @Override
    public String toString() {
        return "Body [posMass=" + Arrays.toString(posMass) + ", velDensity=" + Arrays.toString(velDensity) + ", color=" + Arrays.toString(color) + "]";
    }
}
