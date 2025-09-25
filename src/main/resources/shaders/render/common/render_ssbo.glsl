// =============================================================
//                      Render SSBO bindings
// =============================================================

//These are the same as the compute SSBO bindings but are readonly

//SSBO bindings:
//Leaf nodes of the radix tree (node representation of a body).
//  -Initialized with numBodies nodes (In Java: numBodies * Node.STRUCT_SIZE * Integer.BYTES)
layout(std430, binding = 0)  readonly buffer LeafNodes          { Node leafNodes[]; };
//Internal nodes of the radix tree.
//  -Initialized with numBodies - 1 nodes (In Java: (numBodies - 1) * Node.STRUCT_SIZE * Integer.BYTES)
layout(std430, binding = 1)  readonly buffer InternalNodes      { Node internalNodes[]; };
//Simulation values
//  -Initialized to exactly fit the values. (In Java: 8*Integer.BYTES+8*Float.BYTES+100*Integer.BYTES+100*Float.BYTES)
layout(std430, binding = 2)  readonly buffer SimulationValues   { uint numBodies; uint initialNumBodies; uint justDied; uint merged; 
                                                        uint outOfBounds; uint pad0; uint pad1; uint pad2; 
                                                        AABB bounds; uint uintDebug[100]; float floatDebug[100]; } sim;
//Bodies of the simulation from the previous step
//  -Initialized with numBodies bodies (In Java: numBodies * Body.STRUCT_SIZE * Float.BYTES)
layout(std430, binding = 3)  readonly buffer BodiesIn           { Body bodies[]; } srcB;
//Bodies of the simulation to be used in the next step
//  -Initialized with numBodies bodies (In Java: numBodies * Body.STRUCT_SIZE * Float.BYTES)
layout(std430, binding = 4)  readonly buffer BodiesOut          { Body bodies[]; } dstB;
//Morton codes of the bodies of the simulation double buffered for dead partitioning and radix sort
//  -Initialized with numBodies morton codes (uint64_t's) (In Java: numBodies * Long.BYTES)
layout(std430, binding = 5)  readonly buffer MortonIn           { uint64_t mortonIn[]; };
layout(std430, binding = 6)  readonly buffer MortonOut          { uint64_t mortonOut[]; };
//Sorted index of the bodies of the simulation double buffered for dead partitioning and radix sort
//  -Initialized with numBodies indices (uints) (In Java: numBodies * Integer.BYTES)
layout(std430, binding = 7)  readonly buffer IndexIn            { uint indexIn[]; };
layout(std430, binding = 8)  readonly buffer IndexOut           { uint indexOut[]; };
//Work queue for propagating node data up the tree from the leaves. Double buffered for performance.
//  -Initialized with numBodies indices (uints) (In Java: (4 + numBodies) * Integer.BYTES)
layout(std430, binding = 9)  readonly buffer WorkQueueIn        { uint headIn; uint tailIn; uint itemsIn[]; };
layout(std430, binding = 10)  readonly buffer WorkQueueOut       { uint headOut; uint tailOut; uint itemsOut[]; };
//Per work group histogram of the radix sort buckets. Reused for dead sorting 
//  -Initialized with numWorkGroups * NUM_BUCKETS histogram bars (uints) (In Java: numWorkGroups * NUM_BUCKETS * Integer.BYTES)
layout(std430, binding = 11) readonly buffer RadixWGHist        { uint wgHist[];      };
//Per work group inclusive sum of the radix sort buckets across all work groups. Reused for dead sorting
//  -Initialized with numWorkGroups * NUM_BUCKETS inclusive sums (uints) (In Java: numWorkGroups * NUM_BUCKETS * Integer.BYTES)
layout(std430, binding = 12) readonly buffer RadixWGScanned     { uint wgScanned[];   };
//  -Initialized with numBodies buckets (uints) and numBodies global bases (uints) (In Java: NUM_BUCKETS * Integer.BYTES + NUM_BUCKETS * Integer.BYTES)
layout(std430, binding = 13) readonly buffer RadixBucketTotalsAndAABB  { uint bucketTotals[NUM_BUCKETS]; uint globalBase[NUM_BUCKETS]; };
//Merge queue for merging bodies identified in the force kernel
//  -Initialized with numBodies pairs of indices (uint[2]'s) (In Java: numBodies * Integer.BYTES)
layout(std430, binding = 14) readonly buffer MergeQueue         { uint mergeQueueHead; uint mergeQueueTail; uvec2 mergeQueue[];};
//Merge body locks to avoid races when merging bodies
//  -Initialized with numBodies locks (uints) (In Java: numBodies * Integer.BYTES)
layout(std430, binding = 15) readonly buffer MergeBodyLocks     { uint bodyLocks[]; };