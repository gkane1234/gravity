// =============================================================
//                  Common layout and structs
// =============================================================

//For compute shaders:
layout(local_size_x = 256u) in;
const uint WG_SIZE = 256u; // Must match local_size_x above and WORK_GROUP_SIZE in Java
//End for compute shaders

//For render shaders:
#version 430
#extension GL_NV_gpu_shader5 : enable
#extension GL_ARB_shading_language_include : enable
//End for render shaders


//To change these, you need to also change them in BarnesHut.java
//Radix sort constants:
const uint RADIX_BITS = 4u;
const uint NUM_BUCKETS = 1u << RADIX_BITS;


//Common structs:
//Representation of a celestial body
struct Body { 
    //position (x,y,z) and mass (w)
    vec4 posMass; 
    //velocity (x,y,z) and density (w)
    vec4 velDensity;
};

//Representation of an axis aligned bounding box
struct AABB {
    //minimum corner of the bounding box
    float[3] minCorner;
    //maximum corner of the bounding box
    uint diameter;
};

//Representation of a node in the radix tree
struct Node {
    //center of mass (x,y,z) and mass (w)
    vec4 comMass;
    //depth of the node for first 4 bits and ready children for last 4 bits
    uint depthReadyChildren;
    //parent of the node
    uint parentId;
    //number of bodies contained in the node
    uint bodiesContained;
    //pad to make the node 16 bytes
    uint pad;
};


struct Children {
    uint[8] children;
}


struct UnitSet {
    float mass; //body mass unit
    float density; //body density unit
    float len; //simulation length unit (length is a keyword)
    float time; //simulation time unit
    float cameraScale; //what one length unit is in camera units
    //Derived unit that is set during the init compute program
    float gravitationalConstant;
    float bodyLengthInSimulationLengthsConstant;
};

struct mortonInOut {
    uint64_t mortonIn;
    uint64_t mortonOut;
}

// =============================================================
//                       SSBO bindings
// =============================================================

//Note: in render shaders, these SSBO bindings are changed to readonly
//Simulation values
//  -Initialized to exactly fit the values. (In Java: 8*Integer.BYTES+8*Float.BYTES+100*Integer.BYTES+100*Float.BYTES)
layout(std430, binding = 0)  buffer SimulationValues   { uint numBodies; uint initialNumBodies; uint justDied; uint merged; 
                                                        uint outOfBounds; uint relativeTo; uint pad1; uint pad2; 
                                                        AABB bounds; UnitSet units; uint uintDebug[100]; float floatDebug[100]; } sim;
//Bodies of the simulation from the previous step
//  -Initialized with numBodies bodies (In Java: numBodies * Body.STRUCT_SIZE * Float.BYTES)
layout(std430, binding = 1)  buffer BodiesIn           { Body bodies[]; } srcB;
//Bodies of the simulation to be used in the next step
//  -Initialized with numBodies bodies (In Java: numBodies * Body.STRUCT_SIZE * Float.BYTES)
layout(std430, binding = 2)  buffer BodiesOut          { Body bodies[]; } dstB;
//Parents of the leaf nodes (bodies) and locks for the bodies during merging, factored to keep each buffer to below 8 * numBodies bytes
//  -Initialized with 2 * numBodies uints (In Java: 2 * numBodies * Integer.BYTES)
layout(std430, binding = 3)  buffer ParentsAndLocks          { uint parentsAndLocks[]; };
//Internal nodes of the radix tree.
//  -Initialized with numBodies - 1 nodes (In Java: (numBodies - 1) * Node.STRUCT_SIZE * Integer.BYTES)
layout(std430, binding = 4)  buffer InternalNodes      { Node internalNodes[]; };
//Children of the internal nodes, factored to keep each buffer to below 8 * numBodies bytes
//  -Initialized with numBodies - 1 nodes (In Java: (numBodies - 1) * Children.STRUCT_SIZE * Integer.BYTES)
layout(std430, binding = 5)  buffer Children      { Children children[]; };
//Morton codes of the bodies of the simulation double buffered for dead partitioning and radix sort
//  -Initialized with numBodies morton codes (uint64_t's) (In Java: numBodies * Long.BYTES)
layout(std430, binding = 6)  buffer MortonInOut           { mortonInOut mortonInOut[]; };
//Sorted index of the bodies of the simulation double buffered for dead partitioning and radix sort
//  -Initialized with numBodies indices (uints) (In Java: numBodies * Integer.BYTES)
layout(std430, binding = 7)  buffer IndexInOut            { uint indexInOut[]; };
//Work queue for propagating node data up the tree from the leaves. Double buffered for performance.
//  -Initialized with numBodies indices (uints) (In Java: (4 + numBodies) * Integer.BYTES)
layout(std430, binding = 8)  buffer WorkQueueInOut        { uint headIn; uint headOut; uint tailIn; uint tailOut; uint itemsInOut[]; };
//Per work group histogram of the radix sort buckets. Reused for dead sorting 
//  -Initialized with numWorkGroups * NUM_BUCKETS histogram bars (uints) (In Java: numWorkGroups * NUM_BUCKETS * Integer.BYTES)
layout(std430, binding = 9) buffer RadixWGHist        { uint wgHist[];      };
//Per work group inclusive sum of the radix sort buckets across all work groups. Reused for dead sorting
//  -Initialized with numWorkGroups * NUM_BUCKETS inclusive sums (uints) (In Java: numWorkGroups * NUM_BUCKETS * Integer.BYTES)
layout(std430, binding = 10) buffer RadixWGScanned     { uint wgScanned[];   };
//  -Initialized with numBodies buckets (uints) and numBodies global bases (uints) (In Java: NUM_BUCKETS * Integer.BYTES + NUM_BUCKETS * Integer.BYTES)
layout(std430, binding = 11) buffer RadixBucketTotalsAndAABB  { uint bucketTotals[NUM_BUCKETS]; uint globalBase[NUM_BUCKETS]; };
//Merge queue for merging bodies identified in the force kernel
//  -Initialized with numBodies pairs of indices (uint[2]'s) (In Java: numBodies * Integer.BYTES)
layout(std430, binding = 12) buffer MergeTasks         { uint mergeTasksHead; uint mergeTasksTail; uvec2 mergeTasks[];};


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
uniform uint mergingCollisionOrNeither; // Selects collision, merging, or neither. 0 = neither, 1 = collision, 2 = merging, 3 = both
uniform float cameraScale;
//Constants for the mergingCollisionOrNeither uniform
const uint NEITHER = 0u;
const uint MERGING = 1u;
const uint COLLISION = 2u;

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
uniform float mass; //Body mass unit
uniform float density; //Body density unit
uniform float len; //Simulation length unit
uniform float time; //Simulation time unit

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
uniform uint uRelativeTo; //Relative to
uniform float uMinImpostorSize; //Minimum impostor radius in NDC



//Empty body constant for merged bodies or OOB bodies
const Body EMPTY_BODY = Body(vec4(0.0), vec4(0.0));

const AABB DEFAULT_AABB = AABB(vec3(1e38), vec3(-1e38));

//Numerical Constants
const float PI = 3.14159265358979323846;
const float THREE_OVER_FOUR_PI_TO_THE_ONE_THIRD = 0.6203504909; 
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
    return b.velDensity.w*sim.units.density;
}

float scaledMass(Body b) {
    return b.posMass.w*sim.units.mass;
}

float scaledMass(Node node) {
    return node.comMass.w*sim.units.mass;
}


//Calculates the radius of a body
float radius(Body b) {
    return sim.units.bodyLengthInSimulationLengthsConstant * pow(b.posMass.w/b.velDensity.w,1.0/3.0);
}


vec3 scaledDist(vec3 a, vec3 b) {
    return (b - a)*sim.units.len;
}

vec3 scaledDist(vec3 a) {
    return a*sim.units.len;
}

float scaledDist(float a) {
    return a*sim.units.len;
}

vec3 relativeLocation(vec3 a, uint relativeTo) {
    if (relativeTo == 0xFFFFFFFFu) {
        return a;
    }
    if (relativeTo >= sim.initialNumBodies) {
        return a;
    }
    return a - srcB.bodies[relativeTo].posMass.xyz;
}

vec3 relativeLocation(Body b, uint relativeTo) {
    return relativeLocation(b.posMass.xyz, relativeTo);
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


//For compute shaders:
void setScaledGravitationalConstant(UnitSet units) {
    sim.units.gravitationalConstant = GRAVITATIONAL_CONSTANT* pow(sim.units.len, -3)* pow(sim.units.time, 2)* pow(sim.units.mass, 1);
}

void setScaledBodyLengthInSimulationLengthsConstant(UnitSet units) {
    sim.units.bodyLengthInSimulationLengthsConstant = THREE_OVER_FOUR_PI_TO_THE_ONE_THIRD * pow((sim.units.mass/ sim.units.density), 1.0/3.0)/sim.units.len;
}
void updateUnits(float mass, float density, float len, float time) {
    sim.units.mass = mass;
    sim.units.density = density;
    sim.units.len = len;
    sim.units.time = time;
    setScaledGravitationalConstant(sim.units);
    setScaledBodyLengthInSimulationLengthsConstant(sim.units);
}
//End for compute shaders

