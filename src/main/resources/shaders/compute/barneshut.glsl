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
const float SOFTENING = 0.01;
uniform float theta;
uniform float dt;
uniform uint numBodies;
// Scene AABB used to normalize positions into [0,1]^3
uniform vec3 aabbMin;
uniform vec3 aabbMax;
uniform uint numWorkGroups; // number of workgroups dispatched for histogram/scatter
// Radix parameters
const uint RADIX_BITS = 4u;                 // 4 bits per pass
const uint NUM_BUCKETS = 1u << RADIX_BITS;  // 16 buckets
const uint WG_SIZE = 128u;                  // must match layout(local_size_x)
// -------------------------------------------------------------

// -------------------------------------------------------------
// 2) Data structures (mirror your Java SSBO layouts)
struct Body { vec4 posMass; vec4 velPad; vec4 color; };
struct Node {
    vec4 comMass;      // xyz = center of mass, w = total mass
    vec4 centerSize;   // xyz = node center, w = halfSize (s)
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
layout(std430, binding = 3) buffer Indices    { uint index[]; };
layout(std430, binding = 4) buffer Nodes      { Node nodes[]; };
// - Optional: scratch buffers for radix sort / tree build
// Radix sort auxiliary buffers
layout(std430, binding = 5) buffer WGHist      { uint wgHist[];      }; // [numWorkGroups * NUM_BUCKETS]
layout(std430, binding = 6) buffer WGScanned   { uint wgScanned[];   }; // scanned per-workgroup bucket bases
layout(std430, binding = 7) buffer GlobalBase  { uint globalBase[];  }; // [NUM_BUCKETS] exclusive bases
layout(std430, binding = 8) buffer MortonOut   { uint64_t mortonOut[];   };
layout(std430, binding = 9) buffer IndicesOut  { uint indexOut[];    };
layout(std430, binding = 10) buffer RootNodeBuffer { uint rootNodeId; };
layout(std430, binding = 11) buffer DebugBuffer { uint debugStatus[]; }; // Debug: track node states
// Current pass shift (bit offset)
uniform uint passShift;
// -------------------------------------------------------------

// -------------------------------------------------------------
// Shared memory declarations (global scope)
shared uint hist[NUM_BUCKETS];
shared uint digits[WG_SIZE];

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
    float fx = clamp(pNorm.x, 0.0, 1.0) * 2097152.0;
    float fy = clamp(pNorm.y, 0.0, 1.0) * 2097152.0;
    float fz = clamp(pNorm.z, 0.0, 1.0) * 2097152.0;
    uint xi = uint(floor(fx));
    uint yi = uint(floor(fy));
    uint zi = uint(floor(fz));
    return morton3D64(xi, yi, zi);
}

bool acceptanceCriterion(float s, float d, float theta)
{
    return s / d < theta;
}
float invDist3(vec3 r, float softening)
{
    float dist2 = dot(r, r) + softening;
    float inv = inversesqrt(dist2);
    return inv * inv * inv;
}
// -------------------------------------------------------------

// -------------------------------------------------------------
// 5) Kernel A: Compute Morton codes (one thread per body)
// - Normalize positions into [0,1]^3 based on scene AABB passed as uniforms
// - morton[gid] = mortonEncode3D(normPos);
// - index[gid]  = gid; (stable key-value pair for sorting)
void encodeMortonKernel()
{
    uint gid = gl_GlobalInvocationID.x;
    if (gid >= numBodies) return;

    vec3 pos = srcB.bodies[gid].posMass.xyz;
    vec3 extent = max(aabbMax - aabbMin, vec3(1e-9));
    vec3 pNorm = (pos - aabbMin) / extent; // map to [0,1]

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

    // hist is now global
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

// Global scan kernel: run with a single invocation (global x = 1)
// Fills wgScanned with per-workgroup exclusive offsets and globalBase with bucket bases
void radixScanKernel()
{
    if (gl_GlobalInvocationID.x != 0u) return;

    // Compute per-workgroup exclusive offsets and bucket totals
    for (uint b = 0u; b < NUM_BUCKETS; ++b) {
        uint accum = 0u;
        for (uint wg = 0u; wg < numWorkGroups; ++wg) {
            uint idx = wg * NUM_BUCKETS + b;
            uint c = wgHist[idx];
            wgScanned[idx] = accum;      // exclusive prefix for this wg and bucket
            accum += c;
        }
        globalBase[b] = accum;            // temporarily store bucket totals
    }

    // Exclusive scan of bucket totals into globalBase
    uint running = 0u;
    for (uint b = 0u; b < NUM_BUCKETS; ++b) {
        uint t = globalBase[b];
        globalBase[b] = running;
        running += t;
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

    // digits is now global

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
// 7) Kernel C: Build linear octree (LBVH) from sorted keys
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
        return 31u - uint(findMSB(highBits));
    } else {
        return 63u - uint(findMSB(lowBits));
    }
}

// Safe LCP that handles array bounds
int safeLCP(int i, int j)
{
    if (i < 0 || j < 0 || i >= int(numBodies) || j >= int(numBodies)) return -1;
    uint64_t mortonI = morton[i];
    uint64_t mortonJ = morton[j];
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


    // Detect root node: the one covering the full range [0, numBodies-1]
    if (min(i, j) == 0 && max(i, j) == int(numBodies - 1)) {
        // This internal node covers the full range - it's the root
        rootNodeId = uint(i) + numBodies;
        nodes[rootNodeId].parentId = 0xFFFFFFFFu;
    }
    
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
    nodes[gid].centerSize = vec4(body.posMass.xyz, 0.0); // tight bounds
    // Atomically notify parent that this child is ready
    uint parentIdx = nodes[gid].parentId;
    if (parentIdx != 0xFFFFFFFFu) {
        atomicAdd(nodes[parentIdx].readyChildren, 1u);
    }
}

// Propagate mass/COM up the tree when children are ready
void propagateNodesKernel()
{
    uint gid = gl_GlobalInvocationID.x;
    if (gid >= numBodies - 1u) return; // Only internal nodes

    uint nodeIdx = gid + numBodies; // Internal nodes start at numBodies

    if (nodes[nodeIdx].readyChildren == 3u) return;
    
    // Debug: Mark this node as waiting if not ready
    if (nodes[nodeIdx].readyChildren < 2u) {
        debugStatus[nodeIdx] = 999u; // Mark as "waiting"
    }
    
    // Wait until both children are ready
    if (nodes[nodeIdx].readyChildren < 2u) {
        return; // Exit early if not ready (no freeze)
    }

    nodes[nodeIdx].readyChildren = 3u; // mark as processed
    
    // Debug: Mark this node as processing
    debugStatus[nodeIdx] = 1u;
    
    // Get children
    uint leftChild = nodes[nodeIdx].childA;
    uint rightChild = nodes[nodeIdx].childB;
    
    // Read child mass/COM data
    vec4 leftCOM = nodes[leftChild].comMass;
    vec4 rightCOM = nodes[rightChild].comMass;
    
    // Compute combined mass and center-of-mass
    float totalMass = leftCOM.w + rightCOM.w;
    vec3 centerOfMass;
    if (totalMass > 0.0) {
        centerOfMass = (leftCOM.w * leftCOM.xyz + rightCOM.w * rightCOM.xyz) / totalMass;
    } else {
        centerOfMass = (leftCOM.xyz + rightCOM.xyz) * 0.5; // fallback
    }
    
    // Compute bounding box (union of children + margin)
    vec3 leftCenter = nodes[leftChild].centerSize.xyz;
    float leftSize = nodes[leftChild].centerSize.w;
    vec3 rightCenter = nodes[rightChild].centerSize.xyz;
    float rightSize = nodes[rightChild].centerSize.w;
    
    // Simple bounding box union
    vec3 minCorner = min(leftCenter - leftSize, rightCenter - rightSize);
    vec3 maxCorner = max(leftCenter + leftSize, rightCenter + rightSize);
    vec3 center = (minCorner + maxCorner) * 0.5;
    float halfSize = length(maxCorner - center);
    
    // Store results
    nodes[nodeIdx].comMass = vec4(centerOfMass, totalMass);
    nodes[nodeIdx].centerSize = vec4(center, halfSize);
    
    // Atomically notify parent that this node is ready
    uint parentIdx = nodes[nodeIdx].parentId;
    if (parentIdx != 0xFFFFFFFFu) {
        atomicAdd(nodes[parentIdx].readyChildren, 1u);
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
void computeForce() 
{
    vec3 accel = vec3(0.0);
    uint gid = gl_GlobalInvocationID.x;
    if (gid >= numBodies) return;

    Body body = srcB.bodies[gid];

    uint stack[128];
    uint stackSize = 0;

    stack[stackSize++] = rootNodeId;

    while (stackSize > 0) {
        uint nodeIdx = stack[--stackSize];

        Node node = nodes[nodeIdx];

        vec3 r = node.comMass.xyz - body.posMass.xyz;
        float d = length(r) + SOFTENING;

        if (acceptanceCriterion(node.centerSize.w*2.0, d, theta)) {
            accel += node.comMass.w * r * invDist3(r, SOFTENING);
        } else {
            if (node.childA != 0xFFFFFFFFu) {
                stack[stackSize++] = node.childA;
            }
            if (node.childB != 0xFFFFFFFFu) {
                stack[stackSize++] = node.childB;
            }
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
#ifdef KERNEL_MORTON
    encodeMortonKernel();
#elif defined(KERNEL_RADIX_HIST)
    radixHistogramKernel();
#elif defined(KERNEL_RADIX_SCAN)
    radixScanKernel();
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
#else
    // Other kernels go here
#endif
}