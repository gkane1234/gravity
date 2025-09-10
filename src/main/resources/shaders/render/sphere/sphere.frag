#version 430
out vec4 fragColor;

in vec3 vWorldPos;
in vec4 vColor;

uniform vec3 uCameraPos;
uniform float uNearDist;
uniform float uFarDist;
void main() {
  // float d = distance(uCameraPos, vWorldPos);
  // float t = clamp((d - uNearDist) / max(uFarDist - uNearDist, 1e-4), 0.0, 1.0);

  // // Use distance to modulate color intensity instead of alpha
  // vec3 color = mix(vColor.xyz, vec3(0.0,0.0,0.0), t); // Darken distant spheres
  fragColor = vColor;
}


