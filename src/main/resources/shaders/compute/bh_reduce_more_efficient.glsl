// Bottom-up reduction for COM and AABB

void initLeafNodesKernel()
{
    uint gid = gl_GlobalInvocationID.x;
    if (gid >= srcB.numBodies) return;

    

    uint bodyIdx = index[gid];
    Body body = srcB.bodies[bodyIdx];

    nodes[gid].comMass = vec4(body.posMass.xyz, body.posMass.w);
    nodes[gid].aabb = AABB(body.posMass.xyz, body.posMass.xyz);
    nodes[gid].childA = 0xFFFFFFFFu;
    nodes[gid].childB = 0xFFFFFFFFu;
    //nodes[gid].readyChildren = 0u;

    uint parentIdx = nodes[gid].parentId;
    uint previousValue = atomicAdd(nodes[parentIdx].readyChildren, 1u);
    if (previousValue == 1u) {
        uint idx = atomicAdd(tail, 1u);
        items[idx] = parentIdx;
    }
}


void resetQueueKernel() {
    uint gid = gl_GlobalInvocationID.x;
    if (gid == 0) {
        head = 0u;
        activeThreads = 0u;
        tail = 0u;
    }
}
bool tryPopQueue(out uint poppedIndex) {
    for (;;) {
        uint headSnapshot = atomicAdd(head, 0u);
        uint tailSnapshot = atomicAdd(tail, 0u);
        if (headSnapshot >= tailSnapshot) {
            return false;
        }
        if (atomicCompSwap(head, headSnapshot, headSnapshot+1u) == headSnapshot) {
            poppedIndex = headSnapshot;
            return true;
        }
    }
}
void propagateNodesKernel()
{
    for (;;) {
        //get next node in queue
        uint poppedIndex;
        if (!tryPopQueue(poppedIndex)) {
            //go back one
            if (atomicAdd(activeThreads, 0u) == 0u) break;
            continue;
        }

        uint nodeIdx = items[poppedIndex];
        if (atomicCompSwap(nodes[nodeIdx].readyChildren, 2u, 0xFFFFFFFFu) != 2u) {
            // Someone else has this node
            continue;
        }
        atomicAdd(activeThreads, 1u);
        uint leftChild = nodes[nodeIdx].childA;
        uint rightChild = nodes[nodeIdx].childB;
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

        memoryBarrierBuffer();
        //update the node
        nodes[nodeIdx].readyChildren = 3u;
        uint parentIdx = nodes[nodeIdx].parentId;
        if (parentIdx != 0xFFFFFFFFu) {
            uint prev = atomicAdd(nodes[parentIdx].readyChildren, 1u);
            if (prev == 1u) {
                uint nextOnQueue = atomicAdd(tail, 1u);
                items[nextOnQueue] = parentIdx;
            }
        }
        atomicAdd(activeThreads, 0xFFFFFFFFu);
    }

}


