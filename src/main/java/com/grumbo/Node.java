package com.grumbo;

import java.util.Arrays;
import java.nio.IntBuffer;

public class Node {

    // struct Node {
    //     vec4 comMass;      // xyz = center of mass, w = total mass
    //     vec3 aabbMin;
    //     vec3 aabbMax;
    //     uint childA;       // left child index
    //     uint childB;       // right child index
    //     uint firstBody;    // optional: leaf body index or start
    //     uint bodyCount;    // optional: number of bodies in leaf
    //     uint readyChildren; // atomic counter: 0, 1, or 2
    //     uint parentId;     // parent node index (0xFFFFFFFF for root)
    //   };
    // -----

    public static final int STRUCT_SIZE = 20;

    private float[] comMass;
    private float[] aabbMin;
    private float[] aabbMax;
    private int childA;
    private int childB;
    private int firstBody;
    private int bodyCount;
    private int readyChildren;
    private int parentId;
    private boolean isLeaf;

    public Node(float[] comMass, float[] aabbMin, float[] aabbMax, int childA, int childB, int firstBody, int bodyCount, int readyChildren, int parentId, boolean isLeaf) {
        this.comMass = comMass;
        this.aabbMin = aabbMin;
        this.aabbMax = aabbMax;
        this.childA = childA;
        this.childB = childB;
        this.firstBody = firstBody;
        this.bodyCount = bodyCount;
        this.readyChildren = readyChildren;
        this.parentId = parentId;
        this.isLeaf = isLeaf;
    }
    public static Node[] fromBuffer(IntBuffer buffer) {
        int numNodes = buffer.capacity() / STRUCT_SIZE;
        Node[] nodes = new Node[numNodes];
        for (int i = 0; i < numNodes; i++) {
            nodes[i] = new Node(buffer, i);
        }
        return nodes;
    }
    public static Node[] fromBuffer(IntBuffer buffer, int startIndex, int endIndex) {
        Node[] nodes = new Node[endIndex - startIndex];
        for (int i = startIndex; i < endIndex; i++) {
            nodes[i - startIndex] = new Node(buffer, i);
        }
        return nodes;
    }

    public Node(IntBuffer buffer, int index) {
        this.comMass = new float[4];
        this.aabbMin = new float[3];
        this.aabbMax = new float[3];

        this.comMass[0] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + 0));
        this.comMass[1] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + 1));
        this.comMass[2] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + 2));
        this.comMass[3] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + 3));

        this.aabbMin[0] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + 4));
        this.aabbMin[1] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + 5));
        this.aabbMin[2] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + 6));
        //padding

        this.aabbMax[0] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + 8));
        this.aabbMax[1] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + 9));
        this.aabbMax[2] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + 10));
        //padding

        this.childA = buffer.get(index * STRUCT_SIZE + 12);
        this.childB = buffer.get(index * STRUCT_SIZE + 13);
        this.firstBody = buffer.get(index * STRUCT_SIZE + 14);
        this.bodyCount = buffer.get(index * STRUCT_SIZE + 15);

        this.readyChildren = buffer.get(index * STRUCT_SIZE + 16);
        this.parentId = buffer.get(index * STRUCT_SIZE + 17);
        //padding
        //padding

        this.isLeaf = index*STRUCT_SIZE<buffer.capacity()/2;

    }

    public static String getNodes(IntBuffer buffer, int startIndex, int endIndex) {
        Node[] nodes = fromBuffer(buffer, startIndex, endIndex);
        StringBuilder sb = new StringBuilder();
        for (Node node : nodes) {
            sb.append(node.toString()).append("\n");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        // Highlight problematic nodes
        String marker = "";
        if (readyChildren < 2) marker += " **STUCK**";
        if (readyChildren > 3) marker += " **CORRUPTED**";
        if (comMass[3] == 0.0f) marker += " **NO_MASS**";
        if (comMass[0] == 0.0f && comMass[1] == 0.0f && comMass[2] == 0.0f && comMass[3] > 0.0f) marker += " **NO_COM**";
        String leafString = isLeaf ? "LEAF" : "INTERNAL";
        return leafString + "{" +
                "comMass=" + Arrays.toString(comMass) +
                ", aabbMin=" + Arrays.toString(aabbMin) +
                ", aabbMax=" + Arrays.toString(aabbMax) +
                ", childA=" + childA +
                ", childB=" + childB +
                ", firstBody=" + firstBody +
                ", bodyCount=" + bodyCount +
                ", readyChildren=" + readyChildren +
                ", parentId=" + parentId +
                '}' + marker;
    }
}
