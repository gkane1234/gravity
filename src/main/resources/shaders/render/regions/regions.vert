#version 430
// =============================================================
//                      Regions vertex shader
// =============================================================

// Match compute shader memory layout (bh_common.glsl)
//Copy of the AABB struct from the compute shader
struct AABB {
	vec3 minCorner;
	vec3 maxCorner;
};



// Helper function to convert AABB to uint[6]
float[6] packAABB(AABB aabb) {
    return float[6](aabb.minCorner.x, aabb.minCorner.y, aabb.minCorner.z, aabb.maxCorner.x, aabb.maxCorner.y, aabb.maxCorner.z);
}

//Copy of the Node struct from the compute shader
struct Node {
	vec4 comMass;
	float[6] aabb;
	uint childA;
	uint childB;
	uint nodeDepth;
	uint bodiesContained;
	uint readyChildren;
	uint parentId;
};

//Copy of the InternalNodes SSBO from the compute shader
layout(std430, binding = 1) readonly buffer InternalNodes {
	Node nodes[];
};
//Copy of the SimulationValues SSBO from the compute shader
layout(std430, binding = 2) readonly buffer SimulationValues { 
	uint numBodies;
	uint initialNumBodies;
};

layout(location = 0) in vec3 aPos; // unit cube in [-0.5, 0.5]

uniform mat4 uMVP; // model-view-projection matrix
uniform ivec2 uMinMaxDepth; // min and max depth of node to render
out flat int uTreeDepth; // depth of the node to send to the fragment shader

// This vertex shader is used to render the regions.
// It is done by rendering a unit cube at the position of the node.
void main() {
	if (gl_InstanceID >= numBodies) {
		uTreeDepth = 0; // if the node is not in the range, set the depth to 0
		gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
		return;
	}
	int nodeIndex = gl_InstanceID;

	Node node = nodes[nodeIndex];

    if (node.nodeDepth < uMinMaxDepth.x || node.nodeDepth > uMinMaxDepth.y) {
        uTreeDepth = 0;
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        return;
    }
	vec3 minC = vec3(node.aabb[0], node.aabb[1], node.aabb[2]);
	vec3 maxC = vec3(node.aabb[3], node.aabb[4], node.aabb[5]);
	vec3 center = (minC + maxC) * 0.5;
	vec3 size = (maxC - minC);
	vec3 worldPos = center + aPos * size;
    uTreeDepth = int(node.nodeDepth);
	gl_Position = uMVP * vec4(worldPos, 1.0);
}