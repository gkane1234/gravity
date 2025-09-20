#version 430
// =============================================================
//                    Sphere fragment shader
// =============================================================
out vec4 fragColor;

in vec3 vWorldPos;

uniform vec3 uCameraPos;
uniform float uNearDist;
uniform float uFarDist;
// This fragment shader is used to render the sphere.
void main() {
  fragColor = vec4(1.0, 1.0, 1.0, 1.0);
}


