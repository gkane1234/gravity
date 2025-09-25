// =============================================================
//                    Sphere fragment shader
// =============================================================
#include "render/common/render_common.glsl"
out vec4 fragColor;

in vec3 vWorldPos;

// This fragment shader is used to render the sphere.
void main() {
  fragColor = vec4(1.0, 1.0, 1.0, 1.0);
}


