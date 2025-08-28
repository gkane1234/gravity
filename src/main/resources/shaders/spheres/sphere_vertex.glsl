#version 430
layout (location = 0) in vec3 aPos; // unit sphere position

struct Body {
  vec4 posMass;
  vec4 velPad;
  vec4 color;
};

layout(std430, binding = 0) readonly buffer SrcBodies {
  Body bodies[];
} srcB;

uniform mat4 uMVP;
uniform float uRadiusScale; // radius = sqrt(mass) * scale

out vec3 vWorldPos;
out vec4 vColor;

float cbrt(float x)
{
    return pow(x, 1.0/3.0);
}

bool isEmptyBody(float mass) { return mass <= 0.0; }

// We use gl_InstanceID to fetch per-planet data
void main() {
  if (isEmptyBody(srcB.bodies[gl_InstanceID].posMass.w)) {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0); // outside clip
    gl_PointSize = 0.0;
    vColor = vec4(0.0);
    return;
  }
  vec3 center = srcB.bodies[gl_InstanceID].posMass.xyz;
  float mass = srcB.bodies[gl_InstanceID].posMass.w;
  float radius = cbrt(max(mass, 0.0)) * uRadiusScale;
  vec3 worldPos = center + aPos * radius;
  vWorldPos = worldPos;
  vColor = srcB.bodies[gl_InstanceID].color;
  gl_Position = uMVP * vec4(worldPos, 1.0);
}


