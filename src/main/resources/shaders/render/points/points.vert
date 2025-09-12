#version 430
struct Body {
  vec4 posMass;
  vec4 velPad;
  vec4 color;
};
layout(std430, binding = 2) readonly buffer SrcBodies {
  Body bodies[];
} srcB;
uniform mat4 uMVP; // model-view-projection matrix
out vec4 vColor;
void main() {
  vec3 pos = srcB.bodies[gl_VertexID].posMass.xyz; // world coords
  vec4 color = srcB.bodies[gl_VertexID].color;
  gl_Position = uMVP * vec4(pos, 1.0);
  gl_PointSize = 1;
  vColor = color;
}