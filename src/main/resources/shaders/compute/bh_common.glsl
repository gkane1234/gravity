// =============================================================
// Common definitions, structs, buffers, and helpers for BH
// =============================================================

layout(local_size_x = 128) in;
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
const uint WG_SIZE = 128u;
//Softening values can cause slowdowns
const float SOFTENING = 0.1;



struct Body { vec4 posMass; vec4 velPad; vec4 color; };

const Body EMPTY_BODY = Body(vec4(0.0), vec4(0.0), vec4(0.0));
struct AABB {
    vec3 min;
    vec3 max;
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

layout(std430, binding = 0) readonly buffer BodiesIn  { uint numBodies; Body bodies[]; } srcB;

layout(std430, binding = 1) buffer BodiesOut { uint numBodies; Body bodies[]; } dstB;

layout(std430, binding = 2) buffer MortonKeys { uint64_t morton[]; };
layout(std430, binding = 3) buffer Indices    { uint index[]; };
layout(std430, binding = 4) buffer Nodes      { Node nodes[]; };
layout(std430, binding = 5) buffer AABBBuffer { AABB aabb[]; };

layout(std430, binding = 6) buffer WGHist      { uint wgHist[];      };
layout(std430, binding = 7) buffer WGScanned   { uint wgScanned[];   };
layout(std430, binding = 8) buffer GlobalBase  { uint globalBase[];  };
layout(std430, binding = 9) buffer BucketTotals { uint bucketTotals[]; };
layout(std430, binding = 10) buffer MortonOut   { uint64_t mortonOut[];   };
layout(std430, binding = 11) buffer IndicesOut  { uint indexOut[];    };
layout(std430, binding = 12) buffer WorkQueue { uint head; uint tail; uint activeThreads; uint items[]; };
layout(std430, binding = 13) buffer MergeQueue { uint mergeQueueTail; uvec2 mergeQueue[];};
layout(std430, binding = 14) buffer UIntDebug { uint uintDebug[]; };
layout(std430, binding = 15) buffer FloatDebug { float floatDebug[]; };

shared uint hist[WG_SIZE];
shared uint digits[WG_SIZE];
shared AABB sharedAABB[WG_SIZE];
shared uint temp[WG_SIZE];



AABB updateAABB(AABB a, AABB b) {
    AABB result;
    result.min = min(a.min, b.min);
    result.max = max(a.max, b.max);
    return result;
}

bool isEmpty(Body b) {
    return b.posMass.w == 0.0;
}

