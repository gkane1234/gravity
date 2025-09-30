// =============================================================
//                     Sphere vertex shader
// =============================================================
#include "common/common.glsl"
layout (location = 0) in vec3 aPos; // unit sphere position

out vec3 vWorldPos; // world position of the body

const float MIN_RADIUS = 10;
const float RADIUS_SCALE = 10;

// Calculates the radius of a body
float cbrt(float x)
{
    return pow(x, 1.0/3.0);
}


// This vertex shader is used to render the sphere.
// It is done by rendering a sphere at the position of the body.
void main() {
  if (isEmpty(srcB.bodies[gl_InstanceID])) {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0); // outside clip
    gl_PointSize = 0.0;
    return;
  }
  vec3 center = scaledDist(relativeLocation(srcB.bodies[gl_InstanceID], uRelativeTo));
  float radius = radius(srcB.bodies[gl_InstanceID]);
  vec3 worldPos = center + aPos * radius;
  vWorldPos = worldPos;
  gl_Position = uMVP * vec4(worldPos, 1.0);
}


