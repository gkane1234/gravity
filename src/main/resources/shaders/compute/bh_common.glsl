// =============================================================
// Common definitions, structs, buffers, and helpers for BH
// =============================================================

layout(local_size_x = 128) in;
uniform float softening;
uniform float theta;
uniform float dt;
uniform uint numBodies;
uniform float elasticity;
uniform float density;
uniform float restitution;
uniform bool collision;
uniform uint numWorkGroups;
uniform uint passShift;

const uint RADIX_BITS = 4u;
const uint NUM_BUCKETS = 1u << RADIX_BITS;
const uint WG_SIZE = 128u;

struct Body { vec4 posMass; vec4 velPad; vec4 color; };

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

layout(std430, binding = 0) readonly buffer BodiesIn  { Body bodies[]; } srcB;
layout(std430, binding = 1) writeonly buffer BodiesOut { Body bodies[]; } dstB;
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
layout(std430, binding = 12) buffer WorkQueue { uint head; uint tail; uint items[]; };
layout(std430, binding = 13) buffer MergeQueue { uint mergeQueueTail; uvec2 mergeQueue[];};

shared uint hist[WG_SIZE];
shared uint digits[WG_SIZE];
shared AABB sharedAABB[WG_SIZE];
shared uint temp[WG_SIZE];

uint64_t expandBits21(uint v)
{
    uint64_t x = uint64_t(v) & 0x1FFFFFul;
    x = (x | (x << 32)) & 0x1F00000000FFFFul;
    x = (x | (x << 16)) & 0x1F0000FF0000FFul;
    x = (x | (x << 8))  & 0x100F00F00F00F00Ful;
    x = (x | (x << 4))  & 0x10C30C30C30C30C3ul;
    x = (x | (x << 2))  & 0x1249249249249249ul;
    return x;
}

uint64_t morton3D64(uint x, uint y, uint z)
{
    return (expandBits21(x) << 2) | (expandBits21(y) << 1) | expandBits21(z);
}

uint64_t mortonEncode3D(vec3 pNorm)
{
    const float MAX_VALUE = 2097151.0;
    float fx = clamp(floor(pNorm.x * MAX_VALUE), 0.0, MAX_VALUE);
    float fy = clamp(floor(pNorm.y * MAX_VALUE), 0.0, MAX_VALUE);
    float fz = clamp(floor(pNorm.z * MAX_VALUE), 0.0, MAX_VALUE);
    uint xi = uint(floor(fx));
    uint yi = uint(floor(fy));
    uint zi = uint(floor(fz));
    return morton3D64(xi, yi, zi);
}

AABB updateAABB(AABB a, AABB b) {
    AABB result;
    result.min = min(a.min, b.min);
    result.max = max(a.max, b.max);
    return result;
}

bool acceptanceCriterion(float s, float invDist, float thetaVal)
{
    return s * invDist < thetaVal;
}

float invDist(vec3 r, float soft)
{
    float dist2 = dot(r, r) + soft;
    float inv = inversesqrt(dist2);
    return inv;
}