#version 430

struct Body {
  vec4 posMass;
  vec4 velDensity;
  vec4 color;
};

layout(std430, binding = 2) readonly buffer SrcBodies {
  Body bodies[];
} srcB;

uniform mat4 uModelView;
uniform mat4 uProj;
uniform float uPointScale; // world radius scale (applied to cbrt(mass))
uniform vec3 uCameraPos;
uniform vec3 uCameraFront;
uniform float uFovY; // radians
uniform float uAspect; // width/height



out vec2 vMapping;
out vec4 vColor;
out float bodyToGlowRatio;
out float mass;
out vec3 vCenterView;
out float vCenterClipW;
out float ndcDepth;
out float worldRadius;

float cbrt(float x) { return pow(x, 1.0/3.0); }

const float GLOW_RADIUS_FACTOR = 10;
const float BOX_CORRECTION = 1.5;

float radius(Body b) {
    return pow(b.posMass.w, 1.0/3.0)/b.velDensity.w;
}

void main() {
  Body b = srcB.bodies[gl_InstanceID];


  vec3 center = b.posMass.xyz;
  mass = b.posMass.w;
  float trueRadius = radius(b);
  worldRadius = trueRadius*GLOW_RADIUS_FACTOR;
  bodyToGlowRatio= 1/GLOW_RADIUS_FACTOR;
    vColor = b.color;

  // Transform center to view space
  vec4 centerView4 = uModelView * vec4(center, 1.0);
  vCenterView = centerView4.xyz;

  // Project to clip
  vec4 centerClip = uProj * centerView4;
  vCenterClipW = centerClip.w;

  // Which corner of the quad
  int vid = gl_VertexID & 3;
  if (vid == 0) vMapping = vec2(-1.0, -1.0) * BOX_CORRECTION;
  else if (vid == 1) vMapping = vec2(-1.0,  1.0) * BOX_CORRECTION;
  else if (vid == 2) vMapping = vec2( 1.0, -1.0) * BOX_CORRECTION;
  else               vMapping = vec2( 1.0,  1.0) * BOX_CORRECTION;

  // Compute projected quad size
  float camDist = length(vCenterView);
  float ndcScaleY = worldRadius / (camDist * tan(0.5 * uFovY));
  float ndcScaleX = ndcScaleY / max(uAspect, 1e-6);

  vec2 ndcOffset = vMapping * vec2(ndcScaleX, ndcScaleY);
  vec2 clipOffset = ndcOffset * centerClip.w;

  gl_Position = vec4(centerClip.xy + clipOffset, centerClip.z, centerClip.w);

}