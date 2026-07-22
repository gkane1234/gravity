// =============================================================
//                          Impostor vertex shader
// =============================================================
#include "common/common.glsl"


out vec2 vMapping;
out float bodyToGlowRatio;
out vec3 color;

out vec3 vCenterView;
out float vCenterClipW;
out float ndcDepth;
out float worldRadius;
out float vNdcScaleX;
out float vNdcScaleY;
out float vProjectedTrueHalf;


const float GLOW_RADIUS_FACTOR = 10;
const float BOX_CORRECTION = 1.5;

// This vertex shader is used to render the impostor spheres.
// They are created as a billboard quad that is scaled to the size of the body.
// The quad is then mapped to the screen space and the depth is calculated.
// The depth is then used to determine if the sphere is close enough to be rendered as opaque,
// or far enough to be rendered as a glow.
void main() {
  Body b = srcB.bodies[gl_InstanceID];

  vec3 center = relativeLocation(b, uRelativeTo)*cameraScale;
  color = getStarColor(scaledMass(b), scaledDensity(b));
  float trueRadius = max (0.000001, radius(b))*cameraScale;
  bodyToGlowRatio = 1.0 / GLOW_RADIUS_FACTOR;

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

  // Compute projected quad size (view-space depth for perspective size)
  float camDist = max(-vCenterView.z, 1e-6);
  float tanHalfFov = tan(0.5 * uFovY);
  vProjectedTrueHalf = trueRadius / max(camDist * tanHalfFov, 1e-12);
  // Projected body size: true radius floored by minImpostorSize.
  // Glow pass pads the billboard by GLOW_RADIUS_FACTOR (same for impGlow / impNodeGlow bodies).
  float minBodyRadius = uMinImpostorSize * camDist * tanHalfFov;
  float effectiveBodyRadius = max(trueRadius, minBodyRadius);

  worldRadius = effectiveBodyRadius * GLOW_RADIUS_FACTOR;
  bodyToGlowRatio = effectiveBodyRadius / worldRadius;

  float ndcScaleY = worldRadius / (camDist * tanHalfFov);
  float ndcScaleX = ndcScaleY / max(uAspect, 1e-6);

  vNdcScaleX = ndcScaleX;
  vNdcScaleY = ndcScaleY;

  vec2 ndcOffset = vMapping * vec2(ndcScaleX, ndcScaleY);



  vec2 clipOffset = ndcOffset * centerClip.w;

  clipOffset = clipOffset;

  gl_Position = vec4(centerClip.xy + clipOffset, centerClip.z, centerClip.w);

}
