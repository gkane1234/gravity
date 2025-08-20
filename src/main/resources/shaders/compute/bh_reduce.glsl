// Bottom-up reduction for COM and AABB

void initLeafNodesKernel()
{
    uint gid = gl_GlobalInvocationID.x;
    if (gid >= numBodies) return;

    uint bodyIdx = index[gid];
    Body body = srcB.bodies[bodyIdx];

    nodes[gid].comMass = vec4(body.posMass.xyz, body.posMass.w);
    nodes[gid].aabb = AABB(body.posMass.xyz, body.posMass.xyz);
    nodes[gid].childA = 0xFFFFFFFFu;
    nodes[gid].childB = 0xFFFFFFFFu;
    nodes[gid].readyChildren = 3u;

    uint parentIdx = nodes[gid].parentId;
    uint prev = atomicAdd(nodes[parentIdx].readyChildren, 1u);
    if (prev == 1u) {
        uint idx = atomicAdd(tail, 1u);
        items[idx] = parentIdx;
    }
}

void propagateNodesKernel()
{
    uint threadId = gl_GlobalInvocationID.x;
    uint totalThreads = gl_NumWorkGroups.x * gl_WorkGroupSize.x;

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
        uint parentIdx = nodes[nodeIdx].parentId;
        if (parentIdx != 0xFFFFFFFFu) {
            uint prev = atomicAdd(nodes[parentIdx].readyChildren, 1u);
            if (prev == 1u) {
                items[workIdx] = parentIdx;
            }
        }
        workIdx += totalThreads;
    }
}


