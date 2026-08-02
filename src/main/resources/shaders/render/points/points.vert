// =============================================================
//                          Points vertex shader
// =============================================================
#include "common/common.glsl"
// This vertex shader is used to render simple points for the bodies.
// It is done by rendering points at the position of the body.
void main() {
  if (gl_InstanceID >= int(sim.numBodies)) {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    gl_PointSize = 0.0;
    return;
  }

  uint bodyIdx = indexIn[gl_InstanceID];
  Body b = srcB.bodies[bodyIdx];
  if (isEmpty(b)) {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    gl_PointSize = 0.0;
    return;
  }

  vec3 pos = relativeLocation(b, uRelativeTo)*sim.units.len; // world coords

  gl_Position = uMVP * vec4(pos, 1.0);
  gl_PointSize = 5;
}