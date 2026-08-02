// =============================================================
//                     Sphere vertex shader
// =============================================================
#include "common/common.glsl"
layout (location = 0) in vec3 aPos; // unit sphere position

out vec3 vWorldPos; // world position of the body

const float MIN_RADIUS = 0.01;
const float RADIUS_SCALE = 1;

// Calculates the radius of a body
float cbrt(float x)
{
    return pow(x, 1.0/3.0);
}


// This vertex shader is used to render the sphere.
// It is done by rendering a sphere at the position of the body.
void main() {
  if (gl_InstanceID >= int(sim.numBodies)) {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    gl_PointSize = 0.0;
    return;
  }

  uint bodyIdx = indexIn[gl_InstanceID];
  if (isEmpty(srcB.bodies[bodyIdx])) {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0); // outside clip
    gl_PointSize = 0.0;
    return;
  }
  vec3 center = scaledDist(relativeLocation(srcB.bodies[bodyIdx], uRelativeTo));
  float radius = max(MIN_RADIUS, RADIUS_SCALE*radius(srcB.bodies[bodyIdx]));
  vec3 worldPos = center + aPos * radius;
  vWorldPos = worldPos;
  gl_Position = uMVP * vec4(worldPos, 1.0);
}


