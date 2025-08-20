package com.grumbo.gpu;

import java.util.Arrays;
import java.nio.IntBuffer;

public class Body {

    //struct Body { vec4 posMass; vec4 velPad; vec4 color; };

    public static final int POS_MASS_OFFSET = 0;
    public static final int VEL_PAD_OFFSET = 4;
    public static final int COLOR_OFFSET = 8;

    public static final int STRUCT_SIZE = 12;

    public static final Type[] bodyTypes = new Type[] { Type.FLOAT, Type.FLOAT, Type.FLOAT, Type.FLOAT, Type.FLOAT, Type.FLOAT, Type.FLOAT, Type.FLOAT, Type.FLOAT, Type.FLOAT, Type.FLOAT, Type.FLOAT };

    private float[] posMass;
    private float[] velPad;
    private float[] color;

    public Body(float[] posMass, float[] velPad, float[] color) {
        this.posMass = posMass;
        this.velPad = velPad;
        this.color = color;
    }

    public static Body fromBuffer(IntBuffer buffer, int index) {
        float[] posMass = new float[4];
        float[] velPad = new float[4];
        float[] color = new float[4];

        for (int i = 0; i < 4; i++) {
            posMass[i] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + POS_MASS_OFFSET + i));
            velPad[i] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + VEL_PAD_OFFSET + i));
            color[i] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + COLOR_OFFSET + i));
        }
        return new Body(posMass, velPad, color);
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
        return "Body [posMass=" + Arrays.toString(posMass) + ", velPad=" + Arrays.toString(velPad) + ", color=" + Arrays.toString(color) + "]";
    }
}
