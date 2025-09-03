// =============================================================
// Common definitions, structs, buffers, and helpers for BH
// =============================================================

layout(local_size_x = 256) in;
uniform float softening;
uniform float theta;
uniform float dt;
uniform float elasticity;
uniform float density;
uniform float restitution;
uniform bool collision;
uniform uint numWorkGroups;
uniform uint passShift;

const uint RADIX_BITS = 4u;
const uint NUM_BUCKETS = 1u << RADIX_BITS;
const uint WG_SIZE = 256u;
//Softening values can cause slowdowns
const float SOFTENING = 0.1;



struct Body { vec4 posMass; vec4 velDensity; vec4 color; };

const Body EMPTY_BODY = Body(vec4(0.0), vec4(0.0), vec4(0.0));
struct AABB {
    vec3 minCorner;
    vec3 maxCorner;
};

struct Node {
    vec4 comMass;
    AABB aabb;
    uint childA;
    uint childB;
    uint firstBody;
    uint bodyCount;
    uint readyChildren;
    uint parentId;
};

layout(std430, binding = 0) readonly buffer  BodiesIn  { uint numBodies; uint initialNumBodies; uint pad0; uint pad1; Body bodies[]; } srcB;

layout(std430, binding = 1) buffer BodiesOut { uint numBodies; uint initialNumBodies; uint pad0; uint pad1; Body bodies[]; } dstB;

layout(std430, binding = 2) buffer MortonKeys { uint64_t morton[]; };
layout(std430, binding = 3) buffer Indices    { uint index[]; };
layout(std430, binding = 4) buffer Nodes      { Node nodes[]; };
layout(std430, binding = 5) buffer AABBBuffer { AABB aabb[]; };
// first half is for alive values second for dead values
layout(std430, binding = 6) buffer WGHist      { uint wgHist[];      };
// first half is for alive values second for dead values
layout(std430, binding = 7) buffer WGScanned   { uint wgScanned[];   };
layout(std430, binding = 8) buffer BucketTotals  { uint bucketTotals[NUM_BUCKETS]; uint globalBase[NUM_BUCKETS];  };
layout(std430, binding = 9) buffer MortonOut   { uint64_t mortonOut[];   };
layout(std430, binding = 10) buffer IndicesOut  { uint indexOut[];    };
layout(std430, binding = 11) buffer WorkQueue { uint head; uint tail; uint activeThreads; uint items[]; };
layout(std430, binding = 12) buffer MergeQueue { uint mergeQueueTail; uvec2 mergeQueue[];};
layout(std430, binding = 13) buffer Debug { uint uintDebug[100]; float floatDebug[100]; };





const uint64_t DEAD_VALUE = 0xFFFFFFFFFFFFFFFFul;

AABB updateAABB(AABB a, AABB b) {
    AABB result;
    result.minCorner = min(a.minCorner, b.minCorner);
    result.maxCorner = max(a.maxCorner, b.maxCorner);
    return result;
}

bool isEmpty(Body b) {
    return b.posMass.w == 0.0;
}

