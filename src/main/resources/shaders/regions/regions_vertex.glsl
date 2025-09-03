#version 430

// Match compute shader memory layout (bh_common.glsl)
struct AABB {
	vec3 minCorner;
	vec3 maxCorner;
};
struct Body {
  vec4 posMass;
  vec4 velPad;
  vec4 color;
};
struct Node {
	vec4 comMass;
	AABB aabb;
	uint childA;
	uint childB;
	uint firstBody;
	uint bodyCount;
	uint readyChildren;
	uint parentId;
};

layout(std430, binding = 0) readonly buffer  BodiesIn  { uint numBodies; uint initialNumBodies; uint pad0; uint pad1; Body bodies[]; } srcB;
layout(std430, binding = 4) readonly buffer Nodes      { Node nodes[]; };

layout(location = 0) in vec3 aPos; // unit cube in [-0.5, 0.5]

uniform mat4 uMVP;
uniform ivec2 uMinMaxDepth;

out flat int uTreeDepth;

void main() {
	int nodeIndex = gl_InstanceID + int(srcB.initialNumBodies);

	if (gl_InstanceID > srcB.numBodies) {
		uTreeDepth = 0;
		gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
		return;
	}

	Node node = nodes[nodeIndex];

    if (int(node.firstBody) < uMinMaxDepth.x || node.firstBody > uMinMaxDepth.y) {
        uTreeDepth = 0;
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        return;
    }
	vec3 minC = node.aabb.minCorner;
	vec3 maxC = node.aabb.maxCorner;
	vec3 center = (minC + maxC) * 0.5;
	vec3 size = (maxC - minC);
	vec3 worldPos = center + aPos * size;
    uTreeDepth = int(node.firstBody);
	gl_Position = uMVP * vec4(worldPos, 1.0);
}