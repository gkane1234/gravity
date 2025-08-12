#version 430
out vec4 fragColor;

void main() {
  // gl_PointCoord is in [0,1]
  vec2 uv = gl_PointCoord * 2.0 - 1.0; // -1..1
  float r2 = dot(uv, uv);
  if (r2 > 1.0) discard; // circle

  // Simple shading: sphere normal from uv
  float z = sqrt(max(0.0, 1.0 - r2));
  vec3 normal = normalize(vec3(uv, z));
  vec3 lightDir = normalize(vec3(0.3, 0.7, 0.6));
  float ndl = clamp(dot(normal, lightDir), 0.1, 1.0);
  vec3 base = vec3(1.0, 1.0, 1.0);
  fragColor = vec4(base * ndl, 1.0);
}


