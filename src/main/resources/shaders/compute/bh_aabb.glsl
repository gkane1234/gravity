
void computeNewAABBKernel() {
    uint gid = gl_GlobalInvocationID.x;
    uint lid = gl_LocalInvocationID.x;

    if (2*gid >= srcB.numBodies) {
        sharedAABB[lid] = AABB(vec3(1e9), vec3(-1e9));
    } else if (2*gid+1 >= srcB.numBodies) {
        vec3 pos = srcB.bodies[2*gid].posMass.xyz;
        sharedAABB[lid] = AABB(pos, pos);
    } else {
        vec3 pos0 = srcB.bodies[2*gid].posMass.xyz;
        vec3 pos1 = srcB.bodies[2*gid+1].posMass.xyz;
        sharedAABB[lid] = AABB(min(pos0, pos1), max(pos0, pos1));
    }
    barrier();

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
}

