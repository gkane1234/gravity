// Radix sort kernels
//Sorts the morton codes of the alive bodies
//Dead bodies will be sorted to the end of the array
//Dispached with (numGroups,0,0) with workgroup size * numGroups = numBodies

uint aliveHistSize()    { return numWorkGroups * NUM_BUCKETS; }
uint aliveScannedSize() { return numWorkGroups * NUM_BUCKETS; }
uint deadHistOffset()   { return aliveHistSize(); }
uint deadScannedOffset(){ return aliveScannedSize(); }
shared uint hist[WG_SIZE];

void radixHistogramKernel()
{
    uint gid = gl_GlobalInvocationID.x;
    uint lid = gl_LocalInvocationID.x;
    uint wgId = gl_WorkGroupID.x;

    //reset the histogram for this workgroup

    if (lid < NUM_BUCKETS) hist[lid] = 0u; 

    //reset the dead body count for this workgroup
    if (lid == 0u) wgHist[wgId+ deadHistOffset()] = 0u;
    barrier();

    bool inRange = gid < srcB.initialNumBodies;
    bool alive = inRange && !isEmpty(srcB.bodies[gid]);

    //if the body is alive, add it to the histogram
    if (alive) {
        uint64_t key = morton[gid];
        uint digit = uint((key >> passShift) & (NUM_BUCKETS - 1u));
        atomicAdd(hist[digit], 1u);
    }

    //if the body is not alive, add it to the dead body count
    if (inRange && !alive) {
        atomicAdd(wgHist[wgId+ deadHistOffset()], 1u);
    }
    barrier();
    //publish the histogram for this workgroup
    if (lid < NUM_BUCKETS) {
        wgHist[wgId * NUM_BUCKETS + lid] = hist[lid];
    }
}
//Dispached with (NUM_RADIX_BUCKETS,0,0) with NUM_RADIX_BUCKETS = 2^RADIX_BITS where RADIX_BITS=4
void radixParallelScanKernel()
{
    uint b = gl_WorkGroupID.x;


    //calculate the total number of bodies in this bucket across all workgroups
    //Calculate as an inclusive sum
    if (gl_LocalInvocationID.x == 0u) {
        uint sum = 0u;
        for (uint wg = 0u; wg < numWorkGroups; ++wg) {
            uint v = wgHist[wg * NUM_BUCKETS + b];
            wgScanned[wg * NUM_BUCKETS + b] = sum;
            sum += v;
        }
        bucketTotals[b] = sum;
    }
}
//Dispached with (1,0,0)
shared uint temp[WG_SIZE];
void radixExclusiveScanKernel()
{ 
    //load the bucket total for each bucket
    uint lid = gl_LocalInvocationID.x;
    uint val = (lid < NUM_BUCKETS) ? bucketTotals[lid] : 0u;
    temp[lid] = val;
    barrier();
 
    //add bucket totals to find the global base using the inclusive sum algorithm
    //adds the one the came before it, then the one two before it, then 4 before it, etc.
    //done in parallel with the other threads to get inclusive sum
    for (uint offset = 1u; offset < WG_SIZE; offset <<= 1u) {
        uint add = 0u;
        if (lid>=offset) add = temp[lid-offset];
        barrier();
        temp[lid]+=add;
        barrier();
    }

    //calculate the exclusive sum for this bucket
    uint exclusive = temp[lid]-val;

    //publish the global base for this bucket
    if (lid < NUM_BUCKETS) {
        globalBase[lid] = exclusive;
    }

    //calculate the total number of alive bodies. 
    //This is used to determine the number of dead bodies.
    if (lid == NUM_BUCKETS-1u) {
        totalAlive = globalBase[lid]+bucketTotals[lid];
    }
}
//Dispached with (1,0,0)
// Uniforms: numWorkGroups
// SSBOs: DeadWG, DeadWGScanned
//Calculates the total number of dead bodies per workgroup
void deadExclusiveScanKernel() {
    if (gl_LocalInvocationID.x == 0u) {
        uint sum = 0u;
        for (uint wg = 0u; wg < numWorkGroups; ++wg) {
            uint v = wgHist[wg+ deadHistOffset()];
            wgScanned[wg+ deadScannedOffset()] = sum;
            sum += v;
        }
    }
}
//Dispached with (numGroups,0,0) with workgroup size * numGroups = numBodies
shared uint digits[WG_SIZE];
void radixScatterKernel()
{
    uint gid = gl_GlobalInvocationID.x;
    uint lid = gl_LocalInvocationID.x;
    uint wgId = gl_WorkGroupID.x;
    //check if body exists and is alive
    bool isActive = (gid < srcB.initialNumBodies && !isEmpty(srcB.bodies[gid]));
    //get the morton code for the body
    uint64_t key = isActive ? morton[gid] : 0ul;
    //get the digits for the body
    uint dig = uint((key >> passShift) & (NUM_BUCKETS - 1u));
    //set the digits
    digits[lid] = isActive ? dig : 0xFFFFFFFFu;
    barrier();

    //calculate the local rank for this body (how many repeats before)
    uint localRank = 0u;
    for (uint i = 0u; i < lid; ++i) {
        if (digits[i] == dig) localRank++;
    }
    //place the body in the correct position
    if (isActive) {
        uint base = globalBase[dig] + wgScanned[wgId * NUM_BUCKETS + dig];
        uint dstIndex = base + localRank;
        mortonOut[dstIndex] = key;
        indexOut[dstIndex]  = index[gid];
    }
}
shared uint deadFlags[WG_SIZE];
//Dispached with (numGroups,0,0) with workgroup size * numGroups = numBodies
// Uniforms: numWorkGroups
// SSBOs: DeadWGScanned, BodiesIn, MortonOut, IndicesOut
void deadScatterKernel() {
    uint lid = gl_LocalInvocationID.x;
    uint wgId = gl_WorkGroupID.x;
    uint gid = gl_GlobalInvocationID.x;
    bool inRange = (gid < srcB.initialNumBodies);
    bool isDead  = inRange && isEmpty(srcB.bodies[gid]);

    deadFlags[lid] = isDead ? 1u : 0u;
    barrier();

    uint localDeadRank = 0u;
    for (uint i = 0u; i < lid; ++i) {
        if (deadFlags[i] == 1u) localDeadRank++;
    }

    if (isDead) {
        uint dstIndex = totalAlive+ wgScanned[wgId+ deadScannedOffset()] + localDeadRank;
        mortonOut[dstIndex] = 0xFFFFFFFFFFFFFFFFul;
        indexOut[dstIndex]  = index[gid];
    }
}


