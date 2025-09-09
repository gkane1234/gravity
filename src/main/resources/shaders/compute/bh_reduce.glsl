// Bottom-up reduction for COM and AABB

// Sets the proper values for the leaf nodes
// Enqueues internal nodes that have two leaves as children
void initLeafNodesKernel()
{
    uint gid = gl_GlobalInvocationID.x;
    if (gid >= sim.numBodies) return;

    uint bodyIdx = index[gid];
    Body body = srcB.bodies[bodyIdx];

    nodes[gid].comMass = vec4(body.posMass.xyz, body.posMass.w);
    nodes[gid].aabb = AABB(body.posMass.xyz, body.posMass.xyz);
    nodes[gid].childA = 0xFFFFFFFFu;
    nodes[gid].childB = 0xFFFFFFFFu;
    nodes[gid].readyChildren = 3u;
    nodes[gid].nodeDepth = 0u;

    uint parentIdx = nodes[gid].parentId;
    uint prev = atomicAdd(nodes[parentIdx].readyChildren, 1u);
    if (prev == 1u) {
        uint idx = atomicAdd(tail, 1u);
        items[idx] = parentIdx;
    }
}
// Each pass of this kernel propagates COM and AABB one level up the tree
void propagateNodesKernel()
{
    uint threadId = gl_GlobalInvocationID.x;
    uint totalThreads = gl_NumWorkGroups.x * gl_WorkGroupSize.x;

    if (gl_GlobalInvocationID.x == 0) {
        if (nodes[sim.initialNumBodies].readyChildren != 3u) {
            atomicAdd(uintDebug[0], 1u);
        }
    }


    uint workIdx = threadId;
    while (workIdx < tail) {
        uint nodeIdx = items[workIdx];
        if (nodes[nodeIdx].readyChildren >= 3u) {
            workIdx += totalThreads;
            continue;
        }


        uint leftChild = nodes[nodeIdx].childA;
        uint rightChild = nodes[nodeIdx].childB;
        if (nodes[leftChild].readyChildren < 2u || nodes[rightChild].readyChildren < 2u) {
            workIdx += totalThreads;

            continue;

            
        }
        if (atomicCompSwap(nodes[nodeIdx].readyChildren, 2u, 0xFFFFFFFFu) != 2u) {
            workIdx += totalThreads;
            continue;
        }
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
        nodes[nodeIdx].readyChildren = 3u;
        nodes[nodeIdx].nodeDepth = 1u+max(nodes[leftChild].nodeDepth, nodes[rightChild].nodeDepth);
        uint parentIdx = nodes[nodeIdx].parentId;
        if (parentIdx != 0xFFFFFFFFu) {
            uint prev = atomicAdd(nodes[parentIdx].readyChildren, 1u);
            if (prev == 1u) {
                uint idx = atomicAdd(tail, 1u);
                items[idx] = parentIdx;
            }
        }
        workIdx += totalThreads;
    }
}
