// Radix sort kernels

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

void radixParallelScanKernel()
{
    uint b = gl_WorkGroupID.x;

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

void radixScatterKernel()
{
    uint gid = gl_GlobalInvocationID.x;
    uint lid = gl_LocalInvocationID.x;
    uint wgId = gl_WorkGroupID.x;
    bool isActive = (gid < numBodies);

    uint64_t key = isActive ? morton[gid] : 0ul;
    uint dig = uint((key >> passShift) & (NUM_BUCKETS - 1u));
    digits[lid] = isActive ? dig : 0xFFFFFFFFu;
    barrier();

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


