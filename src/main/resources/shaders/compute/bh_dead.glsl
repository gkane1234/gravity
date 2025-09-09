uint aliveHistSize()    { return numWorkGroups * NUM_BUCKETS; }
uint aliveScannedSize() { return numWorkGroups * NUM_BUCKETS; }
uint deadHistOffset()   { return aliveHistSize(); }
uint deadScannedOffset(){ return aliveScannedSize(); }

shared uint deadFlags[WG_SIZE];
shared uint aliveFlags[WG_SIZE];
bool outOfBounds(Body b) {
    return b.posMass.x < sim.bounds.minCorner.x || b.posMass.x > sim.bounds.maxCorner.x ||
           b.posMass.y < sim.bounds.minCorner.y || b.posMass.y > sim.bounds.maxCorner.y ||
           b.posMass.z < sim.bounds.minCorner.z || b.posMass.z > sim.bounds.maxCorner.z;
}
void deadCountKernel() {
    uint gid = gl_GlobalInvocationID.x;
    uint lid = gl_LocalInvocationID.x;
    uint wgId = gl_WorkGroupID.x;

    bool inRange = gid < sim.numBodies;
    uint bodyIdx = index[gid];
    bool alive = inRange && !isEmpty(srcB.bodies[bodyIdx]);
    bool dead = inRange && isEmpty(srcB.bodies[bodyIdx]);
    if (lid == 0u) {
        wgHist[wgId + deadHistOffset()] = 0u;
    }

    
    if (alive && outOfBounds(srcB.bodies[bodyIdx])) {
        dead = true;

    }


    // this makes sure that the body is set to empty correctly on the old buffer
    if (dead) {
       srcB.bodies[bodyIdx] = EMPTY_BODY;
       dstB.bodies[bodyIdx] = EMPTY_BODY;
    }

    deadFlags[lid] = dead ? 1u : 0u;
    //floatDebug[lid] = float(srcB.bodies[bodyIdx].posMass.w);
    barrier();

    if (lid == 0u) {
        uint sum = 0u;
        for (uint i = 0u; i < WG_SIZE; ++i) {
            sum += deadFlags[i];
        }
        atomicAdd(wgHist[wgId + deadHistOffset()], sum);
    }
    barrier();
    
}

//Dispached with (1,0,0)
// Uniforms: numWorkGroups
// SSBOs: DeadWG, DeadWGScanned
//Calculates the exclusive sum of the dead bodies per workgroup
void deadExclusiveScanKernel() {
    if (gl_LocalInvocationID.x == 0u) {
        uint sum = 0u;
        for (uint wg = 0u; wg < numWorkGroups; ++wg) {
            uint v = wgHist[wg+ deadHistOffset()];
            wgScanned[wg+ deadScannedOffset()] = sum;
            sum += v;
        }
        sim.justDied += sum;
    }
    
}


//Dispached with (numGroups,0,0) with workgroup size * numGroups = numBodies
// Uniforms: numWorkGroups
// SSBOs: DeadWGScanned, BodiesIn, MortonOut, IndicesOut
void deadScatterKernel() {
    uint lid = gl_LocalInvocationID.x;
    uint wgId = gl_WorkGroupID.x;
    uint gid = gl_GlobalInvocationID.x;
    bool inRange = (gid < sim.numBodies);
    uint bodyIdx = index[gid];
    bool isDead  = inRange && isEmpty(srcB.bodies[bodyIdx]);
    bool isAlive = inRange && !isEmpty(srcB.bodies[bodyIdx]);

    if (isDead) {
        deadFlags[lid] = 1u;
    }
    else {
        deadFlags[lid] = 0u;
    }
    aliveFlags[lid] = isAlive ? 1u : 0u;

    barrier();
    uint localDeadRank = 0u;
    for (uint i = 0u; i < lid; ++i) {
        if (deadFlags[i] == 1u) {
            localDeadRank++;
        }
    }
    uint localAliveRank = 0u;
    for (uint i = 0u; i < lid; ++i) {
        if (aliveFlags[i] == 1u) {
            localAliveRank++;
        }
    }

    if (isDead) {
        uint dstIndex = sim.numBodies - sim.justDied + wgScanned[wgId+ deadScannedOffset()] + localDeadRank;
        uintDebug[wgScanned[wgId+ deadScannedOffset()] + localDeadRank] = dstIndex;
        indexOut[dstIndex]  = bodyIdx;
        //sim.numBodies sets where the dead bodies start, so we dont need to set the dead value
        //mortonOut[dstIndex] = DEAD_VALUE;
    }
    else if (isAlive) {
        uint dstIndex =  WG_SIZE * wgId - wgScanned[wgId+ deadScannedOffset()] + localAliveRank;
        indexOut[dstIndex]  = bodyIdx;
        //mortonOut[dstIndex] = 10101010ul;
    }
}