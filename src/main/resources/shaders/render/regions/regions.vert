#version 430

// Match compute shader memory layout (bh_common.glsl)
struct AABB {
	vec3 minCorner;
	vec3 maxCorner;
};



// Helper function to convert AABB to uint[6]
float[6] packAABB(AABB aabb) {
    return float[6](aabb.minCorner.x, aabb.minCorner.y, aabb.minCorner.z, aabb.maxCorner.x, aabb.maxCorner.y, aabb.maxCorner.z);
}

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

layout(std430, binding = 1) readonly buffer InternalNodes {
	Node nodes[];
};
layout(std430, binding = 2) readonly buffer SimulationValues { 
	uint numBodies;
	uint initialNumBodies;
};

layout(location = 0) in vec3 aPos; // unit cube in [-0.5, 0.5]

uniform mat4 uMVP;
uniform ivec2 uMinMaxDepth;
out flat int uTreeDepth;

void main() {
	if (gl_InstanceID >= numBodies) {
		uTreeDepth = 0;
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