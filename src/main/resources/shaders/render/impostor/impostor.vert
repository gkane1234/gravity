#version 430
// =============================================================
//                          Impostor vertex shader
// =============================================================

struct Body {
  vec4 posMass;
  vec4 velDensity;
};

layout(std430, binding = 3) readonly buffer SrcBodies {
  Body bodies[];
} srcB;



//Numerical Constants
const float PI = 3.14159265358979323846;
const float THREE_OVER_FOUR_PI_TO_THE_THIRD = 0.6203504909; 
const float GRAVITATIONAL_CONSTANT = 6.67430e-11; //m^3 kg^-1 s^-2
const float STELLAR_DENSITY = 1.408e3; //kg/m^3
const float SOLAR_MASS = 1.989e30; //kg
const float ASTRONOMICAL_UNIT = 1.496e11; //m


//Checks if a body is empty
bool isEmpty(Body b) {
    return b.posMass.w == 0.0;
}


float d(Body b) {
    return b.velDensity.w*STELLAR_DENSITY;
}

float m(Body b) {
    return b.posMass.w*SOLAR_MASS;
}


//Calculates the radius of a body
float radius(Body b) {

    return THREE_OVER_FOUR_PI_TO_THE_THIRD * pow((m(b)/d(b)), 1.0/3.0);
}

uniform mat4 uModelView;
uniform mat4 uProj;
uniform float uPointScale; // world radius scale (applied to cbrt(mass))
uniform vec3 uCameraPos;
uniform vec3 uCameraFront;
uniform float uFovY; // radians
uniform float uAspect; // width/height



out vec2 vMapping;
out float bodyToGlowRatio;
out vec3 color;

out vec3 vCenterView;
out float vCenterClipW;
out float ndcDepth;
out float worldRadius;


const float GLOW_RADIUS_FACTOR = 2;
const float BOX_CORRECTION = 1.5;

vec3 red =vec3(1.0, 0.0, 0.0);
vec3 yellow = vec3(1.0, 1.0, 0.0);
vec3 blue = vec3(0.0, 0.4, 0.8);
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

vec3 scaledDist(vec3 a) {
    return a*ASTRONOMICAL_UNIT;
}

float scaledDist(float a) {
    return a*ASTRONOMICAL_UNIT;
}
// This vertex shader is used to render the impostor spheres.
// They are created as a billboard quad that is scaled to the size of the body.
// The quad is then mapped to the screen space and the depth is calculated.
// The depth is then used to determine if the sphere is close enough to be rendered as opaque,
// or far enough to be rendered as a glow.
void main() {
  Body b = srcB.bodies[gl_InstanceID];


  vec3 center = scaledDist(b.posMass.xyz);
  color = getStarColor(m(b), d(b));
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