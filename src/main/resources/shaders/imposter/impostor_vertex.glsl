#version 430

struct Body {
  vec4 posMass;
  vec4 velDensity;
  vec4 color;
};

layout(std430, binding = 0) readonly buffer SrcBodies {
  Body bodies[];
} srcB;

uniform mat4 uMVP;
uniform mat4 cameraToClipMatrix;
uniform float uPointScale; // world radius scale (applied to cbrt(mass))
uniform vec3 uCameraPos;
uniform vec3 uCameraFront;
uniform float uFovY; // radians
uniform float uAspect; // width/height



out vec2 vMapping;
out vec4 vColor;
out float trueRadius;
out float mass;
out float ndcDepth;

float cbrt(float x) { return pow(x, 1.0/3.0); }

const float GLOW_RADIUS_FACTOR = 3;
const float BOX_CORRECTION = 1.5;

void main() {
  Body b = srcB.bodies[gl_InstanceID];


  vec3 center = b.posMass.xyz;
  mass = b.posMass.w;
  trueRadius = cbrt(max(mass, 0.0)) * uPointScale / b.velDensity.w;
  float worldRadius = trueRadius*GLOW_RADIUS_FACTOR;
  trueRadius= trueRadius/ worldRadius;
  vColor = b.color;
  vec3 viewVec = center - uCameraPos;
  float viewZ = dot(viewVec, normalize(uCameraFront));
  if (viewZ <= 0.0) {
    gl_Position = uMVP * vec4(center, 1.0);
    return;
  }

  vec4 centerClip = uMVP * vec4(center, 1.0);
  ndcDepth = centerClip.z / centerClip.w;

  int vid = gl_VertexID & 3;
  if (vid == 0) vMapping = vec2(-1.0, -1.0)*BOX_CORRECTION;
  else if (vid == 1) vMapping = vec2(-1.0,  1.0)*BOX_CORRECTION;
  else if (vid == 2) vMapping = vec2( 1.0, -1.0)*BOX_CORRECTION;
  else               vMapping = vec2( 1.0,  1.0)*BOX_CORRECTION;

  float camDist = length(viewVec);
  float ndcScaleY = worldRadius / (camDist * tan(0.5 * uFovY));
  float ndcScaleX = ndcScaleY / max(uAspect, 1e-6);
  vec2 ndcOffset = vMapping * vec2(ndcScaleX, ndcScaleY);
  vec2 clipOffset = ndcOffset * centerClip.w;
  gl_Position = vec4(centerClip.xy + clipOffset, centerClip.z, centerClip.w);

}