package com.grumbo.gpu;

import java.util.Arrays;
import java.nio.IntBuffer;

public class Body {

    //struct Body { vec4 posMass; vec4 velPad; vec4 color; };

    public static final int POS_MASS_OFFSET = 0;
    public static final int VEL_DENSITY_OFFSET = 4;
    public static final int COLOR_OFFSET = 8;

    public static final int STRUCT_SIZE = 12;
    public static final int HEADER_SIZE = 16;
    public static final VariableType[] headerTypes = new VariableType[] { 
        VariableType.UINT, VariableType.UINT, VariableType.UINT, VariableType.UINT };

    private float[] posMass;
    private float[] velDensity;
    private float[] color;
    
    public static final VariableType[] bodyTypes = new VariableType[] { 
        VariableType.FLOAT, VariableType.FLOAT, VariableType.FLOAT, VariableType.FLOAT, 
        VariableType.FLOAT, VariableType.FLOAT, VariableType.FLOAT, VariableType.FLOAT,
        VariableType.FLOAT, VariableType.FLOAT, VariableType.FLOAT, VariableType.FLOAT };


    public Body(float[] posMass, float[] velDensity, float[] color) {
        this.posMass = posMass;
        this.velDensity = velDensity;
        this.color = color;
    }

    public static Body deadBody() {
        return new Body(new float[4], new float[4], new float[4]);
    }

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
