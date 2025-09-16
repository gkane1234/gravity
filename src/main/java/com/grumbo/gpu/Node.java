package com.grumbo.gpu;

import java.util.Arrays;
import java.nio.IntBuffer;
import java.util.ArrayList;

/**
 * Java Analog to the Node struct in the shader code:
 * struct Node { vec4 comMass; vec3 aabbMin; vec3 aabbMax; uint childA; uint childB; uint nodeDepth; uint bodiesContained; uint readyChildren; uint parentId; };
 * 
 * @author Grumbo
 * @version 1.0
 * @since 1.0
 */
public class Node {

    // struct Node {
    //     vec4 comMass;      // xyz = center of mass, w = total mass
    //     float[6] aabb;
    //     uint childA;       // left child index
    //     uint childB;       // right child index
    //     uint nodeDepth;    // leaf body index or start
    //     uint bodiesContained;    // number of bodies in leaf
    //     uint readyChildren; // atomic counter representing the number of ready children. It is set to 0xFFFFFFFF once the node has been processed.
    //     uint parentId;     // parent node index (0xFFFFFFFF for root)
    //   };
    // -----

    public static final int COM_MASS_OFFSET = 0;
    public static final int AABB_OFFSET = 4;
    public static final int CHILD_A_OFFSET = 10;
    public static final int CHILD_B_OFFSET = 11;
    public static final int FIRST_BODY_OFFSET = 12;
    public static final int BODY_COUNT_OFFSET = 13;
    public static final int READY_CHILDREN_OFFSET = 14;
    public static final int PARENT_ID_OFFSET = 15;

    public static final int STRUCT_SIZE = 16;
    public static final GLSLVariable comMassGLSL = new GLSLVariable(VariableType.FLOAT, "comMass", 4);
    public static final GLSLVariable aabbGLSL = new GLSLVariable(VariableType.FLOAT, "aabb", 6);
    public static final GLSLVariable childAGLSL = new GLSLVariable(VariableType.UINT, "childA", 1);
    public static final GLSLVariable childBGLSL = new GLSLVariable(VariableType.UINT, "childB", 1);
    public static final GLSLVariable nodeDepthGLSL = new GLSLVariable(VariableType.UINT, "nodeDepth", 1);
    public static final GLSLVariable bodiesContainedGLSL = new GLSLVariable(VariableType.UINT, "bodiesContained", 1);
    public static final GLSLVariable readyChildrenGLSL = new GLSLVariable(VariableType.UINT, "readyChildren", 1);
    public static final GLSLVariable parentIdGLSL = new GLSLVariable(VariableType.UINT, "parentId", 1);
    public static final GLSLVariable nodeStruct = new GLSLVariable(new GLSLVariable[] {comMassGLSL, aabbGLSL, childAGLSL, childBGLSL, nodeDepthGLSL, bodiesContainedGLSL, readyChildrenGLSL, parentIdGLSL}, "Node");

    public float[] comMass;
    public float[] aabb;
    public int childA;
    public int childB;
    public int nodeDepth;
    public int bodiesContained;
    public int readyChildren;
    public int parentId;
    public boolean isLeaf;
    /**
     * Constructor for the Node class.
     * @param comMass the center of mass of the node (x,y,z,mass)
     * @param aabbMin the minimum point of the node's axis-aligned bounding box
     * @param aabbMax the maximum point of the node's axis-aligned bounding box
     * @param childA the index of the left child node
     * @param childB the index of the right child node
     * @param nodeDepth the height of the node in the tree (the distance from its furthest leaf node)
     * @param bodiesContained the number of bodies contained in the node
     * @param readyChildren the number of ready children of the node
     * @param parentId the index of the parent node
     * @param isLeaf whether the node is a leaf node
     */
    public Node(float[] comMass, float[] aabb, int childA, int childB, int nodeDepth, int bodiesContained, int readyChildren, int parentId, boolean isLeaf) {

        this.comMass = comMass;
        this.aabb = aabb;
        this.childA = childA;
        this.childB = childB;
        this.nodeDepth = nodeDepth;
        this.bodiesContained = bodiesContained;
        this.readyChildren = readyChildren;
        this.parentId = parentId;
        this.isLeaf = isLeaf;
    }

    
    /**
     * Constructor for the Node class from an int buffer that is taken from the SSBO
     * @param buffer the buffer to get the node from
     * @param index the index of the node in the buffer
     */
    public Node(IntBuffer buffer, int index) {
        this.comMass = new float[4];
        this.aabb = new float[6];

        this.comMass[0] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + COM_MASS_OFFSET));
        this.comMass[1] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + COM_MASS_OFFSET + 1));
        this.comMass[2] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + COM_MASS_OFFSET + 2));
        this.comMass[3] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + COM_MASS_OFFSET + 3));

        this.aabb[0] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + AABB_OFFSET));
        this.aabb[1] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + AABB_OFFSET + 1));
        this.aabb[2] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + AABB_OFFSET + 2));
        this.aabb[3] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + AABB_OFFSET + 3));
        this.aabb[4] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + AABB_OFFSET + 4));
        this.aabb[5] = Float.intBitsToFloat(buffer.get(index * STRUCT_SIZE + AABB_OFFSET + 5));

        this.childA = buffer.get(index * STRUCT_SIZE + CHILD_A_OFFSET);
        this.childB = buffer.get(index * STRUCT_SIZE + CHILD_B_OFFSET);
        this.nodeDepth = buffer.get(index * STRUCT_SIZE + FIRST_BODY_OFFSET);
        this.bodiesContained = buffer.get(index * STRUCT_SIZE + BODY_COUNT_OFFSET);

        this.readyChildren = buffer.get(index * STRUCT_SIZE + READY_CHILDREN_OFFSET);
        this.parentId = buffer.get(index * STRUCT_SIZE + PARENT_ID_OFFSET);

        this.isLeaf = index*STRUCT_SIZE<buffer.capacity()/2;

    }

    /**
     * Reads all nodes from an int buffer that is taken from the SSBO
     * @param buffer the buffer to get the node from
     * @return the nodes
     */
    public static Node[] fromBuffer(IntBuffer buffer) {
        int numNodes = buffer.capacity() / STRUCT_SIZE;
        Node[] nodes = new Node[numNodes];
        for (int i = 0; i < numNodes; i++) {
            nodes[i] = new Node(buffer, i);
        }
        return nodes;
    }
    /**
     * Reads a range of nodes from an int buffer that is taken from the SSBO
     * @param buffer the buffer to get the nodes from
     * @param startIndex the index of the first node to read
     * @param endIndex the index of the last node to read
     * @return the nodes
     */
    public static Node[] fromBuffer(IntBuffer buffer, int startIndex, int endIndex) {
        Node[] nodes = new Node[endIndex - startIndex];
        for (int i = startIndex; i < endIndex; i++) {
            nodes[i - startIndex] = new Node(buffer, i);
        }
        return nodes;
    }

    /**
     * Returns a string representation of a range of nodes in the buffer
     * @param buffer the buffer to get the nodes from
     * @param startIndex the index of the first node to read
     * @param endIndex the index of the last node to read
     * @return the string of all the nodes
     */
    public static String getNodes(IntBuffer buffer, int startIndex, int endIndex) {
        Node[] nodes = fromBuffer(buffer, startIndex, endIndex);
        StringBuilder sb = new StringBuilder();
        int index = startIndex;
        for (Node node : nodes) {
            sb.append(index).append(": ").append(node.toString()).append("\n");
            index++;
        }
        return sb.toString();
    }

    /**
     * Returns a string representation of the node
     * @return the string representation of the node
     */
    @Override
    public String toString() {
        // Highlight problematic nodes
        String marker = "";
        if (readyChildren != -1) marker += " **STUCK**";
        if (readyChildren > 2) marker += " **CORRUPTED**";
        if (comMass[3] == 0.0f) marker += " **NO_MASS**";
        if (comMass[0] == 0.0f && comMass[1] == 0.0f && comMass[2] == 0.0f && comMass[3] > 0.0f) marker += " **NO_COM**";
        String leafString = isLeaf ? "LEAF" : "INTERNAL";
        return leafString + "{" +
                "comMass=" + Arrays.toString(comMass) +
                ", aabb=" + Arrays.toString(aabb) +
                ", childA=" + childA +
                ", childB=" + childB +
                ", nodeDepth=" + nodeDepth +
                ", bodiesContained=" + bodiesContained +
                ", readyChildren=" + readyChildren +
                ", parentId=" + parentId +
                '}' + marker;
    }

    /**
     * Gets the children of a range of nodes
     * @param buffer the buffer to get the nodes from
     * @param nodes the nodes to get the children of
     * @return the children of the nodes
     */
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

    /**
     * Gets the volume of the bounding box of a node
     * @param node the node to get the volume of
     * @return the volume of the node
     */
    public static float volume(Node node) {
        return (node.aabb[3] - node.aabb[0]) * (node.aabb[4] - node.aabb[1]) * (node.aabb[5] - node.aabb[2]);
    }

    /**
     * Gets a string representation of a tree of the nodes in the buffer
     * @param buffer the buffer to get the nodes from
     * @param rootIndex the index of the root node
     * @param layers the number of layers to get
     * @return the string representation of the tree of the nodes
     */
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
