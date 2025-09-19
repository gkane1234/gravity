#version 430
// =============================================================
//                          Points vertex shader
// =============================================================

//Copy of the Body struct from the compute shader
struct Body {
  vec4 posMass;
  vec4 velDensity;
};
//Copy of the SrcBodies SSBO from the compute shader
layout(std430, binding = 3) readonly buffer SrcBodies {
  Body bodies[];
} srcB;
uniform mat4 uMVP; // model-view-projection matrix
// This vertex shader is used to render simple points for the bodies.
// It is done by rendering points at the position of the body.
void main() {
  vec3 pos = srcB.bodies[gl_VertexID].posMass.xyz; // world coords
  gl_Position = uMVP * vec4(pos, 1.0);
  gl_PointSize = 1;
}