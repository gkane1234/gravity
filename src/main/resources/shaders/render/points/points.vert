// =============================================================
//                          Points vertex shader
// =============================================================
#include "render/common/render_common.glsl"
// This vertex shader is used to render simple points for the bodies.
// It is done by rendering points at the position of the body.
void main() {
  vec3 pos = dstB.bodies[gl_InstanceID].posMass.xyz*ASTRONOMICAL_UNIT; // world coords
  gl_Position = uMVP * vec4(pos, 1.0);
  gl_PointSize = 1;
}