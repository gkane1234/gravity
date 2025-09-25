// =============================================================
//                          Points fragment shader
// =============================================================
#include "render/common/render_common.glsl"
// This fragment shader is used to render the points.
// It is done by rendering points at the position of the body.
out vec4 fragColor;
void main() {
  fragColor = vec4(1.0, 1.0, 1.0, 1.0);
}