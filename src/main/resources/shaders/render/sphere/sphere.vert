#version 430
// =============================================================
//                     Sphere vertex shader
// =============================================================
layout (location = 0) in vec3 aPos; // unit sphere position

//Copy of the Body struct from the compute shader
struct Body {
  vec4 posMass;
  vec4 velDensity;
};

//Copy of the SrcBodies SSBO from the compute shader
layout(std430, binding = 3) readonly buffer SrcBodies {
  Body bodies[];
} srcB;

uniform mat4 uMVP; // model-view-projection matrix
uniform float uRadiusScale; // radius = sqrt(mass) * scale

out vec3 vWorldPos; // world position of the body


const float MIN_RADIUS = 10;
const float RADIUS_SCALE = 10;

// Calculates the radius of a body
float cbrt(float x)
{
    return pow(x, 1.0/3.0);
}

// Checks if the body is empty
bool isEmptyBody(float mass) { return mass <= 0.0; }

// This vertex shader is used to render the sphere.
// It is done by rendering a sphere at the position of the body.
void main() {
  if (isEmptyBody(srcB.bodies[gl_InstanceID].posMass.w)) {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0); // outside clip
    gl_PointSize = 0.0;
    return;
  }
  vec3 center = srcB.bodies[gl_InstanceID].posMass.xyz;
  float mass = srcB.bodies[gl_InstanceID].posMass.w;
  float radius = max(MIN_RADIUS,RADIUS_SCALE*cbrt(max(mass, 0.0)) * uRadiusScale);
  vec3 worldPos = center + aPos * radius;
  vWorldPos = worldPos;
  gl_Position = uMVP * vec4(worldPos, 1.0);
}


