// =============================================================
//           Common functions, uniforms, and constants
// =============================================================

//Compute Uniforms
//Update uniforms:
uniform uint resetValuesOrDecrementDeadBodies; //Used to determine if the update kernel is resetting values or decrementing dead bodies
//Force uniforms: 
uniform float softening; //Used to soften the force calculation (F ∝ (r+softening)^-2)
uniform float theta; //Used to determine acceptance criterion for force calculation in node traversal
uniform float dt; // Time step used to update the position and velocity of bodies
uniform uint collisionMergingOrNeither; // Selects collision, merging, or neither. 0 = neither, 1 = collision, 2 = merging, 3 = both

//Constants for the collisionMergingOrNeither uniform
const uint NEITHER = 0u;
const uint COLLISION = 1u;
const uint MERGING = 2u;
const uint BOTH = 3u;

uniform float elasticity; //Elasticity of collisions
uniform float restitution; //Restitution of overlapping bodies in collisions 
uniform float bothCriterion; //Used to determine if the body is colliding or merging
uniform bool wrapAround; //If the simulation wraps around or kills OOB bodies 
uniform uint staticOrDynamic; //If the simulation is static or dynamic
//Constants for the dynamic uniform
const uint STATIC = 0u;
const uint DYNAMIC = 1u;

//Radix sort uniforms:
uniform uint passShift; //Pass shift for radix sort passes.
//Common uniforms:
uniform uint numWorkGroups; //Used to determine the number of work groups during the radix sort


//Render Uniforms
uniform mat4 uMVP; // model-view-projection matrix
uniform float uPointScale; //Used to scale the points
uniform vec3 uCameraPos; //Camera position
uniform vec3 uCameraFront; //Camera front
uniform float uFovY; //FOV
uniform float uAspect; //Aspect ratio
uniform int uPass; //Used standard or glow pass in impostor spheres 
//Constants for the pass uniform
const int STANDARD = 0;
const int GLOW = 1;

uniform mat4 uProj; //Projection matrix
uniform mat4 uModelView; //Model view matrix
uniform float uRadiusScale; //Radius scale
uniform ivec2 uMinMaxDepth; //Min and max depth for regions



//Empty body constant for merged bodies or OOB bodies
const Body EMPTY_BODY = Body(vec4(0.0), vec4(0.0));

const AABB DEFAULT_AABB = AABB(vec3(1e38), vec3(-1e38));

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

//Checks if a body is out of bounds
bool outOfBounds(Body b) {
    return b.posMass.x < sim.bounds.minCorner.x || b.posMass.x > sim.bounds.maxCorner.x ||
           b.posMass.y < sim.bounds.minCorner.y || b.posMass.y > sim.bounds.maxCorner.y ||
           b.posMass.z < sim.bounds.minCorner.z || b.posMass.z > sim.bounds.maxCorner.z;
}

float scaledDensity(Body b) {
    return b.velDensity.w*STELLAR_DENSITY;
}

float scaledMass(Body b) {
    return b.posMass.w*SOLAR_MASS;
}

float scaledMass(Node node) {
    return node.comMass.w*SOLAR_MASS;
}

//Calculates the radius of a body
float scaledRadius(Body b) {

    return THREE_OVER_FOUR_PI_TO_THE_THIRD * pow((scaledMass(b)/scaledDensity(b)), 1.0/3.0);
}


vec3 scaledDist(vec3 a, vec3 b) {
    return (b - a)*ASTRONOMICAL_UNIT;
}

vec3 scaledDist(vec3 a) {
    return a*ASTRONOMICAL_UNIT;
}

float scaledDist(float a) {
    return a*ASTRONOMICAL_UNIT;
}


vec3 deScaledDist(vec3 a) {
    return a/ASTRONOMICAL_UNIT;
}

float deScaledDist(float a) {
    return a/ASTRONOMICAL_UNIT;
}


//Creates an AABB that is the union of two AABBs
AABB updateAABB(AABB a, AABB b) {
    AABB result;
    result.minCorner = min(a.minCorner, b.minCorner);
    result.maxCorner = max(a.maxCorner, b.maxCorner);
    return result;
}

// Converts a float[6] to an AABB
AABB unpackAABB(float[6] aabb) {
    return AABB(vec3(aabb[0], aabb[1], aabb[2]), vec3(aabb[3], aabb[4], aabb[5]));
}

// Converts an AABB to a float[6]
float[6] packAABB(AABB aabb) {
    return float[6](aabb.minCorner.x, aabb.minCorner.y, aabb.minCorner.z, aabb.maxCorner.x, aabb.maxCorner.y, aabb.maxCorner.z);
}



bool emptyAABB(AABB aabb) {
    return aabb.minCorner.x > aabb.maxCorner.x;
}

bool emptyAABB(float[6] aabb) {
    return aabb[0] > aabb[3];
}
// Gets a node from the leaf or internal nodes buffer depending on the index
Node getNode(uint nodeIdx) {
    return nodeIdx < sim.initialNumBodies ? leafNodes[nodeIdx] : internalNodes[nodeIdx-sim.initialNumBodies];
}
// Checks if a node is an internal node
bool isInternalNode(Node node) {
    return node.childA != 0xFFFFFFFFu;
}