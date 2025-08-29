
uint aliveHistSize()    { return numWorkGroups * NUM_BUCKETS; }
uint aliveScannedSize() { return numWorkGroups * NUM_BUCKETS; }
uint deadHistOffset()   { return aliveHistSize(); }
uint deadScannedOffset(){ return aliveScannedSize(); }

shared uint deadFlags[WG_SIZE];
void deadCountKernel() {
    uint gid = gl_GlobalInvocationID.x;
    uint lid = gl_LocalInvocationID.x;
    uint wgId = gl_WorkGroupID.x;

    bool inRange = gid < srcB.initialNumBodies;
    bool alive = inRange && !isEmpty(srcB.bodies[gid]);
    bool dead = inRange && isEmpty(srcB.bodies[gid]);
    if (lid == 0u) {
        wgHist[wgId + deadHistOffset()] = 0u;
    }
    deadFlags[lid] = dead ? 1u : 0u;
    // this makes sure that the body is set to empty correctly on the old buffer
    if (dead) {
        dstB.bodies[gid] = EMPTY_BODY;
    }
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

        uintDebug[0] = sum;
    }
}


//Dispached with (numGroups,0,0) with workgroup size * numGroups = numBodies
// Uniforms: numWorkGroups
// SSBOs: DeadWGScanned, BodiesIn, MortonOut, IndicesOut
void deadScatterKernel() {
    uint lid = gl_LocalInvocationID.x;
    uint wgId = gl_WorkGroupID.x;
    uint gid = gl_GlobalInvocationID.x;
    bool inRange = (gid < srcB.initialNumBodies);
    bool isDead  = inRange && isEmpty(srcB.bodies[gid]);
    bool isAlive = inRange && !isEmpty(srcB.bodies[gid]);

    if (isDead) {
        deadFlags[lid] = 1u;
    }
    else {
        deadFlags[lid] = 0u;
    }
    

    barrier();
    uint localDeadRank = 0u;
    for (uint i = 0u; i < lid; ++i) {
        if (deadFlags[i] == 1u) {
            localDeadRank++;
        }
    }

    uint localAliveRank = lid - localDeadRank;


    if (isDead) {
        uint dstIndex = srcB.numBodies+ wgScanned[wgId+ deadScannedOffset()] + localDeadRank;
        mortonOut[dstIndex] = 0xFFFFFFFFFFFFFFFFul;
        indexOut[dstIndex]  = index[gid];
    }
    else if (isAlive) {
        uint dstIndex =  WG_SIZE * wgId - wgScanned[wgId+ deadScannedOffset()] + localAliveRank;
        mortonOut[dstIndex] = morton[gid];
        indexOut[dstIndex]  = index[gid];
    }
}