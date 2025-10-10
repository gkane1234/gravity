// =============================================================
//                      Regions vertex shader
// =============================================================
#include "common/common.glsl"


layout(location = 0) in vec3 aPos; // unit cube in [-0.5, 0.5]


out flat int uTreeDepth; // depth of the node to send to the fragment shader

void cullRegion() {
	uTreeDepth = 0; // if the node is not in the range, set the depth to 0
	gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
}

// This vertex shader is used to render the regions.
// It is done by rendering a unit cube at the position of the node.
void main() {
	if (gl_InstanceID >= sim.numBodies) {
		cullRegion();
		return;
	}
	int nodeIndex = gl_InstanceID;

	Node node = internalNodes[nodeIndex];

	if (emptyAABB(node.aabb)) {
		cullRegion();
		return;
	}

    if (node.nodeDepth < uMinMaxDepth.x || node.nodeDepth > uMinMaxDepth.y) {
        cullRegion();
        return;
    }
	vec3 minC = relativeLocation(vec3(node.aabb[0], node.aabb[1], node.aabb[2]), uRelativeTo)*sim.units.len;
	vec3 maxC = relativeLocation(vec3(node.aabb[3], node.aabb[4], node.aabb[5]), uRelativeTo)*sim.units.len;
	vec3 center = (minC + maxC) * 0.5;
	vec3 size = (maxC - minC);
	vec3 worldPos = center + aPos * size;
    uTreeDepth = int(node.nodeDepth);
	gl_Position = uMVP * vec4(worldPos, 1.0);
}