#version 430

struct Body {
  vec4 posMass;
  vec4 velPad;
};

layout(std430, binding = 0) readonly buffer SrcBodies {
  Body bodies[];
} srcB;

uniform mat4 uMVP;
uniform float uPointScale; // converts mass to gl_PointSize

void main() {
  vec3 pos = srcB.bodies[gl_VertexID].posMass.xyz;
  float mass = srcB.bodies[gl_VertexID].posMass.w;
  gl_Position = uMVP * vec4(pos, 1.0);
  gl_PointSize = max(1.0, sqrt(max(mass, 0.0)) * uPointScale);
}


