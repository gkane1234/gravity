package com.grumbo.gpu;

import java.util.Arrays;
import java.nio.IntBuffer;
import java.util.ArrayList;

public class Node {

    // struct Node {
    //     vec4 comMass;      // xyz = center of mass, w = total mass
    //     vec3 aabbMin;
    //     vec3 aabbMax;
    //     uint childA;       // left child index
    //     uint childB;       // right child index
    //     uint nodeDepth;    // optional: leaf body index or start
    //     uint bodiesContained;    // optional: number of bodies in leaf
    //     uint readyChildren; // atomic counter: 0, 1, or 2
    //     uint parentId;     // parent node index (0xFFFFFFFF for root)
    //   };
    // -----

    public static final int COM_MASS_OFFSET = 0;
    public static final int AABB_MIN_OFFSET = 4;
    public static final int AABB_MAX_OFFSET = 8;
    public static final int CHILD_A_OFFSET = 12;
    public static final int CHILD_B_OFFSET = 13;
    public static final int FIRST_BODY_OFFSET = 14;
    public static final int BODY_COUNT_OFFSET = 15;
    public static final int READY_CHILDREN_OFFSET = 16;
    public static final int PARENT_ID_OFFSET = 17;

    public static final int STRUCT_SIZE = 20;
    public static final VariableType[] nodeTypes = new VariableType[] { VariableType.FLOAT, VariableType.FLOAT, VariableType.FLOAT, VariableType.FLOAT, 
        VariableType.FLOAT, VariableType.FLOAT, VariableType.FLOAT, VariableType.PADDING, 
        VariableType.FLOAT, VariableType.FLOAT, VariableType.FLOAT, VariableType.PADDING, 
        VariableType.UINT, VariableType.UINT, VariableType.UINT, VariableType.UINT, 
        VariableType.UINT, VariableType.UINT, VariableType.PADDING, VariableType.PADDING }; 

    public float[] comMass;
    public float[] aabbMin;
    public float[] aabbMax;
    public int childA;
    public int childB;
    public int nodeDepth;
    public int bodiesContained;
    public int readyChildren;
    public int parentId;
    public boolean isLeaf;

    public Node(float[] comMass, float[] aabbMin, float[] aabbMax, int childA, int childB, int nodeDepth, int bodiesContained, int readyChildren, int parentId, boolean isLeaf) {
        this.comMass = comMass;
        this.aabbMin = aabbMin;
        this.aabbMax = aabbMax;
        this.childA = childA;
        this.childB = childB;
        this.nodeDepth = nodeDepth;
        this.bodiesContained = bodiesContained;
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

        this.comMass[0] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + COM_MASS_OFFSET));
        this.comMass[1] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + COM_MASS_OFFSET + 1));
        this.comMass[2] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + COM_MASS_OFFSET + 2));
        this.comMass[3] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + COM_MASS_OFFSET + 3));

        this.aabbMin[0] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + AABB_MIN_OFFSET));
        this.aabbMin[1] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + AABB_MIN_OFFSET + 1));
        this.aabbMin[2] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + AABB_MIN_OFFSET + 2));
        //padding

        this.aabbMax[0] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + AABB_MAX_OFFSET));
        this.aabbMax[1] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + AABB_MAX_OFFSET + 1));
        this.aabbMax[2] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + AABB_MAX_OFFSET + 2));
        //padding

        this.childA = buffer.get(index * STRUCT_SIZE + CHILD_A_OFFSET);
        this.childB = buffer.get(index * STRUCT_SIZE + CHILD_B_OFFSET);
        this.nodeDepth = buffer.get(index * STRUCT_SIZE + FIRST_BODY_OFFSET);
        this.bodiesContained = buffer.get(index * STRUCT_SIZE + BODY_COUNT_OFFSET);

        this.readyChildren = buffer.get(index * STRUCT_SIZE + READY_CHILDREN_OFFSET);
        this.parentId = buffer.get(index * STRUCT_SIZE + PARENT_ID_OFFSET);
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
                ", nodeDepth=" + nodeDepth +
                ", bodiesContained=" + bodiesContained +
                ", readyChildren=" + readyChildren +
                ", parentId=" + parentId +
                '}' + marker;
    }

    private static ArrayList<Node> getChildren(IntBuffer buffer, ArrayList<Node> nodes) {
        ArrayList<Node> children = new ArrayList<Node>();
        for (Node node : nodes) {
            if (node.childA == 0xFFFFFFFF) {
                //children.add(null);
            } else {
                children.add(new Node(buffer, node.childA));
            }
            if (node.childB == 0xFFFFFFFF) {
                //children.add(null);
            } else {
                children.add(new Node(buffer, node.childB));
            }
        }
        return children;
    }

    public static float volume(Node node) {
        return (node.aabbMax[0] - node.aabbMin[0]) * (node.aabbMax[1] - node.aabbMin[1]) * (node.aabbMax[2] - node.aabbMin[2]);
    }

    public static String getTree(IntBuffer buffer, int rootIndex, int layers) {
        int layerCount = 0;
        StringBuilder sb = new StringBuilder();
        Node root = new Node(buffer, rootIndex);

        ArrayList<ArrayList<Node>> nodes = new ArrayList<ArrayList<Node>>();

        sb.append("Layer 0:\n");
        sb.append(String.format("%.2f", volume(root)) + " " + root.toString());
        sb.append("\n");

        nodes.add(new ArrayList<Node>(Arrays.asList(root)));



        while (layerCount < layers) {

            nodes.add(getChildren(buffer,nodes.getLast()));
            sb.append("Layer " + (layerCount+1) + ":\n");
            for (Node node : nodes.getLast()) {
                if (node != null) {
                    sb.append(String.format("%.2f", volume(node)) + " " + node.toString()).append("\n");
                }
            }
            sb.append("\n");

            layerCount++;
        }


        return sb.toString();

    }
}
