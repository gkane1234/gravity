// =============================================================
//                          Points vertex shader
// =============================================================
#include "common/common.glsl"
// This vertex shader is used to render simple points for the bodies.
// It is done by rendering points at the position of the body.
void main() {
  vec3 pos = relativeLocation(srcB.bodies[gl_InstanceID], uRelativeTo)*sim.units.len; // world coords
  gl_Position = uMVP * vec4(pos, 1.0);
  gl_PointSize = 1;
}