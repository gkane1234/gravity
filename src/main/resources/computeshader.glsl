#version 430

layout(local_size_x = 128) in;
const uint WG_SIZE = 128u;

struct Body {
  vec4 posMass;
  vec4 velPad;
  vec4 color;
};

layout(std430, binding = 0) readonly buffer SrcBodies {
  Body bodies[];
} srcB;

layout(std430, binding = 1) writeonly buffer DstBodies {
  Body bodies[];
} dstB;

uniform float dt;
uniform uint numBodies;
uniform float softening;

shared Body tile[WG_SIZE];

void main() {
  uint gid = gl_GlobalInvocationID.x;
  if (gid >= numBodies) return;
  
  Body me = srcB.bodies[gid];
  vec3 pos = me.posMass.xyz;
  vec3 vel = me.velPad.xyz;
  float massMe = me.posMass.w;
  vec3 accel = vec3(0.0);
  
  for (uint tileStart = 0u; tileStart < numBodies; tileStart += WG_SIZE) {
    uint localIdx = gl_LocalInvocationID.x;
    uint idx = tileStart + localIdx;
    if (idx < numBodies) {
      tile[localIdx] = srcB.bodies[idx];
    } else {
      tile[localIdx].posMass = vec4(0.0);
      tile[localIdx].velPad = vec4(0.0);
      tile[localIdx].color = vec4(0.0);
    }
    barrier();
    
    for (uint j = 0u; j < WG_SIZE; ++j) {
      vec3 r = tile[j].posMass.xyz - pos;
      float distSqr = dot(r, r) + softening;
      float invDist = inversesqrt(distSqr);
      float invDist3 = invDist * invDist * invDist;
      float m = tile[j].posMass.w;
      accel += m * r * invDist3;
    }
    barrier();
  }
  
  vel += accel * dt;
  pos += vel * dt;
  dstB.bodies[gid].posMass = vec4(pos, massMe);
  dstB.bodies[gid].velPad = vec4(vel, 0.0);
  dstB.bodies[gid].color = me.color;
}