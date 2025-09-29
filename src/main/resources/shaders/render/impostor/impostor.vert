// =============================================================
//                          Impostor vertex shader
// =============================================================
#include "common/common.glsl"


out vec2 vMapping;
out float bodyToGlowRatio;
out vec3 color;

out vec3 vCenterView;
out float vCenterClipW;
out float ndcDepth;
out float worldRadius;


const float GLOW_RADIUS_FACTOR = 2;
const float BOX_CORRECTION = 1.5;

// Calculates the temperature of a star
// Input: mass and density
// Output: temperature in Kelvin
// source: https://www.quora.com/What-is-the-formula-between-the-temperature-and-mass-size-of-a-main-sequence-star
// substitute r with 4/3*pi*r^3 = m/d
float temp(float mass, float density) {

    float constant = 5.95589e-19;
    float temp = constant * pow(mass,0.875-1.0/6.0)* pow(density,1.0/6.0);

    return temp;
}

// Converts temperature to RGB color
// Input: temperature in Kelvin
// Output: RGB values 0–255
// Source: https://tannerhelland.com/2012/09/18/convert-temperature-rgb-algorithm-code.html
vec3 tempToColor(float kelvin) {
    float temp = kelvin / 100.0;
    float r, g, b;

    // Red
    if (temp <= 66.0) {
        r = 255.0;
    }
    else {
        r = temp - 60.0;
        r = 329.698727446 * pow(r, -0.1332047592);
    }

    // Green
    if (temp <= 66.0) {
        g = 99.4708025861 * log(temp) - 161.1195681661;
    }
    else {
        g = temp;
        g = 288.1221695283 * pow(g, -0.0755148492);
    }

    // Blue
    if (temp >= 66.0) {
        b = 255.0;
    }
    else {
        b = temp - 10.0;
        b = 138.5177312231 * log(b) - 305.0447927307;
    }

    return vec3(
        clamp(r, 0.0, 255.0)/255.0,
        clamp(g, 0.0, 255.0)/255.0,
        clamp(b, 0.0, 255.0)/255.0
    );
}
vec3 getStarColor(float mass, float density) {
    float temp = temp(mass, density);
    return tempToColor(temp);
}

// This vertex shader is used to render the impostor spheres.
// They are created as a billboard quad that is scaled to the size of the body.
// The quad is then mapped to the screen space and the depth is calculated.
// The depth is then used to determine if the sphere is close enough to be rendered as opaque,
// or far enough to be rendered as a glow.
void main() {
  Body b = srcB.bodies[gl_InstanceID];


  vec3 center = scaledDist(b.posMass.xyz);
  color = getStarColor(scaledMass(b), scaledDensity(b));
  float trueRadius = radius(b);
  worldRadius = trueRadius*GLOW_RADIUS_FACTOR;
  bodyToGlowRatio= 1/GLOW_RADIUS_FACTOR;

  // Transform center to view space
  vec4 centerView4 = uModelView * vec4(center, 1.0);
  vCenterView = centerView4.xyz;

  // Project to clip
  vec4 centerClip = uProj * centerView4;
  vCenterClipW = centerClip.w;

  // Which corner of the quad
  int vid = gl_VertexID & 3;
  if (vid == 0) vMapping = vec2(-1.0, -1.0) * BOX_CORRECTION;
  else if (vid == 1) vMapping = vec2(-1.0,  1.0) * BOX_CORRECTION;
  else if (vid == 2) vMapping = vec2( 1.0, -1.0) * BOX_CORRECTION;
  else               vMapping = vec2( 1.0,  1.0) * BOX_CORRECTION;

  // Compute projected quad size
  float camDist = length(vCenterView);
  float ndcScaleY = worldRadius / (camDist * tan(0.5 * uFovY));
  float ndcScaleX = ndcScaleY / max(uAspect, 1e-6);

  vec2 ndcOffset = vMapping * vec2(ndcScaleX, ndcScaleY);
  vec2 clipOffset = ndcOffset * centerClip.w;

  gl_Position = vec4(centerClip.xy + clipOffset, centerClip.z, centerClip.w);

}