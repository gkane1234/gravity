#version 430
#extension GL_NV_gpu_shader5 : enable
// =============================================================
// Barnes–Hut N-body compute shader (outline only)
// This file contains comments indicating where each piece goes.
// Fill in the sections based on your data layout and pipeline.
// =============================================================

// -------------------------------------------------------------
// 1) Constants and configuration
// - Define workgroup size, softening, Barnes–Hut theta, etc.
layout(local_size_x = 128) in;
const float SOFTENING = 0.1;
uniform float theta;
uniform float dt;
uniform uint numBodies;
uniform float elasticity;
uniform float density;

// Scene AABB used to normalize positions into [0,1]^3
uniform uint numWorkGroups; // number of workgroups dispatched for histogram/scatter
// Radix parameters
const uint RADIX_BITS = 4u;                 // 4 bits per pass
const uint NUM_BUCKETS = 1u << RADIX_BITS;  // 16 buckets
const uint WG_SIZE = 128u;                  // must match layout(local_size_x)
// -------------------------------------------------------------

// -------------------------------------------------------------
// 2) Data structures (mirror your Java SSBO layouts)
struct Body { vec4 posMass; vec4 velPad; vec4 color; };

struct AABB {
    vec3 min;

    vec3 max;

};
struct Node {
    vec4 comMass;      // xyz = center of mass, w = total mass
    AABB aabb;
    uint childA;       // left child index
    uint childB;       // right child index
    uint firstBody;    // optional: leaf body index or start
    uint bodyCount;    // optional: number of bodies in leaf
    uint readyChildren; // atomic counter: 0, 1, or 2
    uint parentId;     // parent node index (0xFFFFFFFF for root)
  };
// -------------------------------------------------------------

// -------------------------------------------------------------
// 3) Buffers
layout(std430, binding = 0) readonly buffer BodiesIn  { Body bodies[]; } srcB;
layout(std430, binding = 1) writeonly buffer BodiesOut { Body bodies[]; } dstB;
layout(std430, binding = 2) buffer MortonKeys { uint64_t morton[]; };
layout(std430, binding = 3) buffer Indices    { uint index[]; }; // index[nodeId] = bodyId
layout(std430, binding = 4) buffer Nodes      { Node nodes[]; };
layout(std430, binding = 5) buffer AABBBuffer { AABB aabb[]; };
// Radix sort auxiliary buffers
layout(std430, binding = 6) buffer WGHist      { uint wgHist[];      }; // [numWorkGroups * NUM_BUCKETS]
layout(std430, binding = 7) buffer WGScanned   { uint wgScanned[];   }; // scanned per-workgroup bucket bases
layout(std430, binding = 8) buffer GlobalBase  { uint globalBase[];  }; // [NUM_BUCKETS] exclusive bases
layout(std430, binding = 9) buffer BucketTotals { uint bucketTotals[]; }; // size NUM_BUCKETS
layout(std430, binding = 10) buffer MortonOut   { uint64_t mortonOut[];   };
layout(std430, binding = 11) buffer IndicesOut  { uint indexOut[];    };
// Work queue: first two uints are head and tail, followed by items
layout(std430, binding = 12) buffer WorkQueue { uint head; uint tail; uint items[]; };
// Current pass shift (bit offset)
uniform uint passShift;
// -------------------------------------------------------------

// -------------------------------------------------------------
// Shared memory declarations (global scope) - FIXED: Proper sizing for workgroup
shared uint hist[WG_SIZE];           // Increased from NUM_BUCKETS to WG_SIZE for proper workgroup coverage
shared uint digits[WG_SIZE];         // Already correct
shared AABB sharedAABB[WG_SIZE]; // Already correct
shared uint temp[WG_SIZE];           // Already correct

// -------------------------------------------------------------
// 4) Helper functions
// Spread the lower 10 bits of v so there are 2 zero bits between each original bit
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
    // Quantize to 21 bits per axis in [0, 2097151]
    const float MAX_VALUE = 2097151.0;
    float fx = clamp(floor(pNorm.x * MAX_VALUE), 0.0, MAX_VALUE);
    float fy = clamp(floor(pNorm.y * MAX_VALUE), 0.0, MAX_VALUE);
    float fz = clamp(floor(pNorm.z * MAX_VALUE), 0.0, MAX_VALUE);
    uint xi = uint(floor(fx));
    uint yi = uint(floor(fy));
    uint zi = uint(floor(fz));
    return morton3D64(xi, yi, zi);
}


// -------------------------------------------------------------
// Compute new AABB
//Needs 


AABB updateAABB(AABB a, AABB b) {
    AABB result;
    result.min = min(a.min, b.min);
    result.max = max(a.max, b.max);
    return result;
}
void computeNewAABBKernel() {
    uint gid = gl_GlobalInvocationID.x;
    uint lid = gl_LocalInvocationID.x;

    // Initialize shared memory
    if (2*gid >= numBodies) {
        sharedAABB[lid] = AABB(vec3(1e9), vec3(-1e9));
    } else if (2*gid+1 >= numBodies) {
        vec3 pos = srcB.bodies[2*gid].posMass.xyz;
        sharedAABB[lid] = AABB(pos, pos);
    } else {
        vec3 pos0 = srcB.bodies[2*gid].posMass.xyz;
        vec3 pos1 = srcB.bodies[2*gid+1].posMass.xyz;
        sharedAABB[lid] = AABB(min(pos0, pos1), max(pos0, pos1));
    }
    barrier();

    // Parallel reduction within workgroup
    uint activePairs = WG_SIZE;
    while (activePairs > 1u) {
        uint stride = activePairs / 2u;
        if (lid < stride) {
            uint other = lid + stride;
            sharedAABB[lid] = updateAABB(sharedAABB[lid], sharedAABB[other]);
        }
        barrier();
        activePairs = stride;
    }

    // Write result to global buffer
    if (lid == 0u) {
        uint wgId = gl_WorkGroupID.x;
        if (wgId < numWorkGroups)
            aabb[wgId] = sharedAABB[0];
    }
}
void collapseAABBKernel() {
    uint gid = gl_GlobalInvocationID.x;
    if (gid > 0) return;
    AABB newAABB = aabb[0];
    for (uint i = 1; i < numWorkGroups; ++i) {
        newAABB = updateAABB(newAABB, aabb[i]);
    }
    aabb[0] = newAABB;


    // // Load input AABBs safely
    // if (gid*2 >= numBodies) {
    //     sharedAABB[lid] = AABB(vec3(1e9), vec3(-1e9));
    // } else if (gid*2+1 >= numBodies) {
    //     sharedAABB[lid] = aabbIn[gid*2];
    // } else {
    //     AABB a = aabbIn[gid*2];
    //     AABB b = aabbIn[gid*2+1];
    //     sharedAABB[lid] = updateAABB(a, b);
    // }
    // barrier();

    // // Parallel reduction
    // uint activePairs = WG_SIZE;
    // while (activePairs > 1u) {
    //     uint stride = activePairs / 2u;
    //     if (lid < stride) {
    //         uint other = lid + stride;
    //         sharedAABB[lid] = updateAABB(sharedAABB[lid], sharedAABB[other]);
    //     }
    //     barrier();
    //     activePairs = stride;
    // }

    // if (lid == 0u && gl_WorkGroupID.x < numWorkGroups) {
    //     AABBBuffer[gl_WorkGroupID.x] = sharedAABB[0];
    // }
}
// -------------------------------------------------------------
// 5) Kernel A: Compute Morton codes (one thread per body)
// - Normalize positions into [0,1]^3 based on scene AABB passed as uniforms
// - morton[gid] = mortonEncode3D(normPos);
// - index[gid]  = gid; (stable key-value pair for sorting)
void encodeMortonKernel()
{
    uint gid = gl_GlobalInvocationID.x;
    if (gid >= numBodies) return;

    AABB scene = aabb[0];
    vec3 pos = srcB.bodies[gid].posMass.xyz;
    vec3 extent = max(scene.max - scene.min, vec3(1e-9));
    vec3 pNorm = (pos - scene.min) / extent; // map to [0,1]

    morton[gid] = mortonEncode3D(pNorm);
    index[gid]  = gid;
}
// -------------------------------------------------------------

// -------------------------------------------------------------
// 6) Kernel B: Radix sort by Morton codes (key = morton, value = index)
// - Implemented as three kernels: histogram, scan (prefix), scatter
// -------------------------------------------------------------

// Per-workgroup histogram kernel: dispatch X = numWorkGroups, local_size_x = WG_SIZE
void radixHistogramKernel()
{
    uint gid = gl_GlobalInvocationID.x;
    uint lid = gl_LocalInvocationID.x;
    uint wgId = gl_WorkGroupID.x;

    if (lid < NUM_BUCKETS) hist[lid] = 0u;
    barrier();

    if (gid < numBodies) {
        uint64_t key = morton[gid];
        uint digit = uint((key >> passShift) & (NUM_BUCKETS - 1u));
        atomicAdd(hist[digit], 1u);
    }
    barrier();

    if (lid < NUM_BUCKETS) {
        wgHist[wgId * NUM_BUCKETS + lid] = hist[lid];
    }
}

// Global scan kernel: Done in two steps
// Fills wgScanned with per-workgroup exclusive offsets and globalBase with bucket bases
// First step: parallel scan of per bucket totals

void radixParallelScanKernel()
{
    uint b = gl_WorkGroupID.x;

    if (gl_LocalInvocationID.x == 0u) {
        uint sum = 0u;
        for (uint wg = 0u; wg < numWorkGroups; ++wg) {
            uint v = wgHist[wg * NUM_BUCKETS + b];
            wgScanned[wg * NUM_BUCKETS + b] = sum; // exclusive offset for this workgroup
            sum += v;
        }
        bucketTotals[b] = sum;
    }
    // Other lanes idle; barrier not required since only lane 0 writes.
}

// Second step: scan of bucket totals
void radixExclusiveScanKernel()
{
    uint lid = gl_LocalInvocationID.x;
    uint val = (lid < NUM_BUCKETS) ? bucketTotals[lid] : 0u;
    temp[lid] = val;
    barrier();

    for (uint offset = 1u; offset < WG_SIZE; offset <<= 1u) {
        uint add = 0u;
        if (lid>=offset) add = temp[lid-offset];
        barrier();
        temp[lid]+=add;
        barrier();
    }

    uint exclusive = temp[lid]-val;

    if (lid < NUM_BUCKETS) {
        globalBase[lid] = exclusive;
    }
}

// Scatter kernel: dispatch X = numWorkGroups, local_size_x = WG_SIZE
// Writes to mortonOut/indexOut based on computed bases
void radixScatterKernel()
{
    uint gid = gl_GlobalInvocationID.x;
    uint lid = gl_LocalInvocationID.x;
    uint wgId = gl_WorkGroupID.x;
    bool isActive = (gid < numBodies);

     uint64_t key = isActive ? morton[gid] : 0ul;
    uint dig = uint((key >> passShift) & (NUM_BUCKETS - 1u));
    digits[lid] = isActive ? dig : 0xFFFFFFFFu; // sentinel so inactive lanes don't match
    barrier();

    // Stable local rank within the workgroup for our digit
    uint localRank = 0u;
    for (uint i = 0u; i < lid; ++i) {
        if (digits[i] == dig) localRank++;
    }

    if (isActive) {
        uint base = globalBase[dig] + wgScanned[wgId * NUM_BUCKETS + dig];
        uint dstIndex = base + localRank;
        mortonOut[dstIndex] = key;
        indexOut[dstIndex]  = index[gid];
    }
}

// -------------------------------------------------------------
// 7) Kernel C: Build linear binary radix tree from sorted keys
// - Generate internal node ranges from longest-common-prefix of neighbors
// - Link child indices and mark leaves
// - Store node center and halfSize (s)
// -------------------------------------------------------------

// Helper: count leading common bits between two Morton codes
uint longestCommonPrefix(uint64_t a, uint64_t b)
{
    if (a == b) return 64u;
    uint64_t x = a ^ b;
    uint highBits = uint(x >> 32);
    uint lowBits = uint(x);
    if (highBits != 0u) {
        return 31u - findMSB(uint(highBits));
    } else {
        return 63u - findMSB(uint(lowBits));
    }
}

// Safe LCP that handles array bounds
int safeLCP(int i, int j)
{
    if (i < 0 || j < 0 || i >= int(numBodies) || j >= int(numBodies)) return -1;
    uint64_t mortonI = morton[i];
    uint64_t mortonJ = morton[j];

    if (mortonI == mortonJ) {
        uint iu = uint(i);
        uint ju = uint(j);
        if (iu == ju) {
            return 64;
        } else {
            return 64 + (31 - findMSB(iu ^ ju));
        }
    }
    return int(longestCommonPrefix(mortonI, mortonJ));
}


// Build binary radix tree kernel: one thread per internal node
void buildBinaryRadixTreeKernel()
{
    uint gid = gl_GlobalInvocationID.x;
    if (gid >= numBodies - 1u) return; // N-1 internal nodes
    
    const int i = int(gid); // this corresponds to the body at the sorted index
    
    // Direction: compare LCP(i,i+1) with neighbors
    int lcpRight = safeLCP(i, i + 1);
    int lcpLeft = safeLCP(i, i - 1);
    //int lcpRight = safeLCP(i + 1, i + 2);
    //d ← sign(δ(i, i + 1) − δ(i, i − 1))
    int direction = (lcpLeft > lcpRight) ? -1 : 1;
    
    // Determine range for this internal node

    // Binary search to find range bounds
    //δmin ← δ(i, i − d)
    int deltaMin = safeLCP(i, i - direction);
    //lmax ← 2
    int lmax = 2;
    
    // Expand until we find a bound
    //while δ(i, i + lmax · d) > δmin do
    while (safeLCP(i, i + direction * lmax) > deltaMin) {
        //lmax ← lmax · 2
        lmax *= 2;
    }
    
    // Binary search within [0, lmax)

    //l ← 0
    int l = 0;
    int t = lmax / 2;
    //for t ← {lmax/2, lmax/4, . . . , 1} do
    while (t > 0) {
        //if δ(i, i + (l + t) · d) > δmin then
        if (safeLCP(i, i + direction * (l + t)) > deltaMin) {
            //l ← l + t
            l = l + t;
        }
        t /= 2;
    }
    //j ← i + l · d

    int j = i + l * direction;
    
    // Find split point
    //δnode ← δ(i, j)
    int deltaNode = safeLCP(i, j);
    //s ← 0
    int s = 0;
    //for t ← {⌈l/2⌉, ⌈l/4⌉, . . . , 1} do
    t = l;
    while (t>1) {
        t = (t + 1) / 2; // Start with ceiling of l/2
        if (safeLCP(i, i + (s + t) * direction) > deltaNode) {
            //s ← s + t
            s += t;
        }
    } 
    //γ ← i + s · d + min(d, 0)

    int gamma = i + s*direction + min(direction,0);
    // Output child pointers
    //if min(i, j) = γ then left ← Lγ else left ← Iγ
    uint leftChild, rightChild;

    if (min(i,j)==gamma) {
        leftChild = uint(gamma);
    } else {
        leftChild = uint(gamma) + numBodies;
    }
    //if max(i, j) = γ + 1 then right ← Lγ+1 else right ← Iγ+1
    if (max(i,j)==gamma+1) {
        rightChild = uint(gamma+1);
    } else {
        rightChild = uint(gamma+1) + numBodies;
    }
    //Ii ← (left, right)
    //return split;
    //int split = findSplit(left, right, direction);
    

    
    // Store in node structure (internal nodes start at index numBodies)
    uint internalIdx = uint(i) + numBodies;
    
    // Set left and right children (children[0] and children[1])
    nodes[internalIdx].childA = leftChild;
    nodes[internalIdx].childB = rightChild;

    // Set Other fields
    nodes[internalIdx].readyChildren = 0u;

    // Set parent id
    nodes[leftChild].parentId = internalIdx;
    nodes[rightChild].parentId = internalIdx;
    
    // Store range info
    nodes[internalIdx].firstBody = uint(min(i, j));
    nodes[internalIdx].bodyCount = uint(max(i, j) - min(i, j) + 1);

    if (i == 0) {
        nodes[internalIdx].parentId = 0xFFFFFFFFu;
    }
}

// -------------------------------------------------------------
// 8) Kernel D: Bottom-up pass to compute node mass and center-of-mass (COM)
// - For leaves: comMass = sum of contained bodies
// - For internal nodes: comMass = sum of children’s comMass
// - Requires a pass ordering or repeated relaxation until stable
// -------------------------------------------------------------

// Initialize leaf nodes with body data and notify parents
void initLeafNodesKernel()
{
    uint gid = gl_GlobalInvocationID.x;
    if (gid >= numBodies) return;

    // This is a leaf node (index 0 to numBodies-1)
    uint bodyIdx = index[gid];
    Body body = srcB.bodies[bodyIdx];
    
    // Set leaf mass and center-of-mass
    nodes[gid].comMass = vec4(body.posMass.xyz, body.posMass.w);
    nodes[gid].aabb = AABB(body.posMass.xyz, body.posMass.xyz);
    nodes[gid].childA = 0xFFFFFFFFu;
    nodes[gid].childB = 0xFFFFFFFFu;
    nodes[gid].readyChildren = 3u;
    
    // Atomically notify parent that this child is ready; if both ready, enqueue parent
    uint parentIdx = nodes[gid].parentId;
    uint prev = atomicAdd(nodes[parentIdx].readyChildren, 1u);
    if (prev == 1u) {
        uint idx = atomicAdd(tail, 1u);
        items[idx] = parentIdx;
    }

}

// Propagate mass/COM up the tree when children are ready
void propagateNodesKernel()
{
    // Persistent kernel: each thread processes multiple work items until queue is empty
    uint threadId = gl_GlobalInvocationID.x;
    uint totalThreads = gl_NumWorkGroups.x * gl_WorkGroupSize.x;
    
    // Process work items in round-robin fashion across all threads
    uint workIdx = threadId;
    while (workIdx < tail) {
        uint nodeIdx = items[workIdx];
        
        // Skip if already processed or not ready
        if (nodes[nodeIdx].readyChildren >= 3u) {
            workIdx += totalThreads;
            continue;
        }
        
        // Check if both children are ready
        uint leftChild = nodes[nodeIdx].childA;
        uint rightChild = nodes[nodeIdx].childB;
        if (nodes[leftChild].readyChildren < 2u || nodes[rightChild].readyChildren < 2u) {
            workIdx += totalThreads;
            continue;
        }
        
        // Mark as processing to avoid race conditions
        if (atomicCompSwap(nodes[nodeIdx].readyChildren, 2u, 0xFFFFFFFFu) != 2u) {
            workIdx += totalThreads;
            continue;
        }
        
        // Process this node
        vec4 leftCOM = nodes[leftChild].comMass;
        vec4 rightCOM = nodes[rightChild].comMass;
        
        float totalMass = leftCOM.w + rightCOM.w;
        vec3 centerOfMass;
        if (totalMass > 0.0) {
            centerOfMass = (leftCOM.w * leftCOM.xyz + rightCOM.w * rightCOM.xyz) / totalMass;
        } else {
            centerOfMass = (leftCOM.xyz + rightCOM.xyz) * 0.5;
        }
        

        AABB leftAABB = nodes[leftChild].aabb;
        AABB rightAABB = nodes[rightChild].aabb;
        AABB newAABB = updateAABB(leftAABB, rightAABB);
        
        nodes[nodeIdx].comMass = vec4(centerOfMass, totalMass);
        nodes[nodeIdx].aabb = newAABB;
        nodes[nodeIdx].readyChildren = 3u; // mark processed
        
        // Notify parent and enqueue if ready
        uint parentIdx = nodes[nodeIdx].parentId;
        if (parentIdx != 0xFFFFFFFFu) {
            uint prev = atomicAdd(nodes[parentIdx].readyChildren, 1u);
            if (prev == 1u) {
                items[workIdx] = parentIdx;
            }
        }
        // Exit the loop
        workIdx += totalThreads;
        
    }
}
// -------------------------------------------------------------
// 9) Kernel E: Force evaluation and integration (one thread per body)
// - For each body:
//   - Initialize a small traversal stack with root node index
//   - While stack not empty:
//       - Pop node id
//       - Compute r = node.com - pos, d = length(r) + softening
//       - If leaf or (node.halfSize / d) < theta:
//           - Accumulate accel += node.mass * r / d^3
//         Else:
//           - Push non-empty children onto stack
//   - Update velocity and position (e.g., semi-implicit Euler or Verlet)
//   - Write to dstB.bodies[gid]
// - Note: read from srcB, write to dstB (double-buffering)
// -------------------------------------------------------------

bool acceptanceCriterion(float s, float invDist, float theta)
{
    return s * invDist < theta;
}

float invDist(vec3 r, float softening)
{
    float dist2 = dot(r, r) + softening;
    float inv = inversesqrt(dist2);
    return inv;
}

float cbrt(float x)
{
    return pow(x, 1.0/3.0);
}

void computeForce() 
{
    vec3 accel = vec3(0.0);
    uint gid = gl_GlobalInvocationID.x;
    if (gid >= numBodies) return;

    Body body = srcB.bodies[gid];

    uint stack[128];
    uint stackSize = 0;

    stack[stackSize++] = numBodies;

    while (stackSize > 0) {
        uint nodeIdx = stack[--stackSize];

        Node node = nodes[nodeIdx];

        vec3 r = node.comMass.xyz - body.posMass.xyz;
        float oneOverDist = invDist(r, SOFTENING);
        vec3 extent = node.aabb.max - node.aabb.min;
        float longestSide = max(extent.x, max(extent.y, extent.z));
        // stop going deeper if the acceptance criterion is met or we are at a leaf
        if (node.childA == 0xFFFFFFFFu) {
            if (index[nodeIdx] != gid) {
                Body other = srcB.bodies[index[nodeIdx]];
                float bodyRadius = cbrt(body.posMass.w);
                float otherRadius = cbrt(other.posMass.w);
                float dist = length(r);
                if (dist < bodyRadius + otherRadius) {
                    vec3 velocityDifference = other.velPad.xyz - body.velPad.xyz;
                    vec3 normal = normalize(r);
                    float vImpact = dot(velocityDifference, normal);

                    if (vImpact < 0) {
                        float mEff = 1/(1/body.posMass.w + 1/other.posMass.w);
                    
                        float j = (1+elasticity)*mEff*vImpact;

                        body.velPad.xyz += normal * j / body.posMass.w;
                    }

                    float penetration = bodyRadius + otherRadius - dist;
                    if (penetration > 0) {
                        const float restitution = 0.2;
                        vec3 correction = (penetration / (body.posMass.w + other.posMass.w)) * restitution * normal;
                        body.posMass.xyz -= correction;
                    }
                }else {
                    accel += node.comMass.w * r * oneOverDist * oneOverDist * oneOverDist;
                }
            }
            
        }
        else if (acceptanceCriterion(longestSide/2, oneOverDist, theta)) {
            accel += node.comMass.w * r * oneOverDist * oneOverDist * oneOverDist;
        }
        else {
            stack[stackSize++] = node.childA;
            stack[stackSize++] = node.childB;
        }
    }

    // Update velocity and position
    vec3 newVel = body.velPad.xyz + accel * dt;
    vec3 newPos = body.posMass.xyz + newVel * dt;


    dstB.bodies[gid].velPad.xyz = newVel;
    dstB.bodies[gid].posMass.xyz = newPos;
    dstB.bodies[gid].posMass.w = body.posMass.w;
    dstB.bodies[gid].color = body.color;
}

void debugKernel() {
    uint gid = gl_GlobalInvocationID.x;
    // Only write for the first N slots to keep it simple
    if (gid >= numWorkGroups) return;

    // Write a unique, easily inspectable pattern:
    // min = (gid + 0.1, gid + 0.2, gid + 0.3)
    // max = (gid + 10.1, gid + 10.2, gid + 10.3)
    aabb[gid] = AABB(vec3(float(gid) + 0.1, float(gid) + 0.2, float(gid) + 0.3), vec3(float(gid) + 10.1, float(gid) + 10.2, float(gid) + 10.3));
}


// -------------------------------------------------------------
// 10) Optional optimizations
// - Use shared memory to stage near-leaf bodies for short-range exact sums
// - Reorder bodies by Morton index for better locality
// - Mixed precision (fp32 compute, fp16 storage) if acceptable
// - Tune stack size and theta
// -------------------------------------------------------------

// -------------------------------------------------------------
// Entry-point notes
// - Implement the above as separate shader variants or gate with #defines
// - Example: define KERNEL_MORTON, KERNEL_BUILD, KERNEL_FORCE, etc., and
//   compile/dispatch the appropriate variant from the host code.
// -------------------------------------------------------------
void main()
{
#ifdef KERNEL_COMPUTE_AABB
    computeNewAABBKernel();
#elif defined(KERNEL_COLLAPSE_AABB)
    collapseAABBKernel();
#elif defined(KERNEL_MORTON)
    encodeMortonKernel();
#elif defined(KERNEL_RADIX_HIST)
    radixHistogramKernel();
#elif defined(KERNEL_RADIX_PARALLEL_SCAN)
    radixParallelScanKernel();
#elif defined(KERNEL_RADIX_EXCLUSIVE_SCAN)
    radixExclusiveScanKernel();
#elif defined(KERNEL_RADIX_SCATTER)
    radixScatterKernel();
#elif defined(KERNEL_BUILD_BINARY_RADIX_TREE)
    buildBinaryRadixTreeKernel();
#elif defined(KERNEL_INIT_LEAVES)
    initLeafNodesKernel();
#elif defined(KERNEL_PROPAGATE_NODES)
    propagateNodesKernel();
#elif defined(KERNEL_COMPUTE_FORCE)
    computeForce();
#elif defined(KERNEL_DEBUG)
    debugKernel();
#else
    // Other kernels go here
#endif
}