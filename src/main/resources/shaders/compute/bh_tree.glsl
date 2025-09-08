// Binary radix tree build and helpers

uint longestCommonPrefix(uint64_t a, uint64_t b)
{
    if (a == b) return 64u;
    uint64_t x = a ^ b;
    uint highBits = uint(x >> 32);
    uint lowBits = uint(x);
    if (highBits != 0u) {
        return 31u - findMSB(uint(highBits));
    } else {
        return 63u - findMSB(uint(lowBits));
    }
}

int safeLCP(int i, int j)
{
    if (i < 0 || j < 0 || i >= int(srcB.numBodies) || j >= int(srcB.numBodies)) return -1;
    
    uint64_t mortonI = morton[i];
    uint64_t mortonJ = morton[j];

    if (mortonI == mortonJ) {
        uint iu = uint(i);
        uint ju = uint(j);
        if (iu == ju) {
            return 64;
        } else {
            return 64 + (31 - findMSB(iu ^ ju));
        }
    }
    return int(longestCommonPrefix(mortonI, mortonJ));
}
//Builds the binary radix tree by creating internal nodes
//Also assigns parents to leaves
void buildBinaryRadixTreeKernel()
{
    uint gid = gl_GlobalInvocationID.x;
    if (gid >= srcB.numBodies - 1u) {
        // For when there is only one body
        if (gid == 0) {
            uint headIdx = srcB.initialNumBodies;
            nodes[headIdx].parentId = 0xFFFFFFFFu;
            nodes[headIdx].childA = 1;
            nodes[headIdx].childB = 0xFFFFFFFFu;
            nodes[headIdx].readyChildren = 1u;
            nodes[headIdx].comMass = vec4(0.0);
            nodes[headIdx].aabb = AABB(vec3(1e38), vec3(-1e38));
            nodes[headIdx].nodeDepth = 0u;
            nodes[headIdx].bodiesContained = 0u;
            nodes[0].parentId = srcB.initialNumBodies;

        }
        return;
    }


    const int i = int(gid);

    int lcpRight = safeLCP(i, i + 1);
    int lcpLeft = safeLCP(i, i - 1);
    int direction = (lcpLeft > lcpRight) ? -1 : 1;

    int deltaMin = safeLCP(i, i - direction);
    int lmax = 2;
    while (safeLCP(i, i + direction * lmax) > deltaMin) {
        lmax *= 2;
    }

    int l = 0;
    int t = lmax / 2;
    while (t > 0) {
        if (safeLCP(i, i + direction * (l + t)) > deltaMin) {
            l = l + t;
        }
        t /= 2;
    }
    int j = i + l * direction;

    int deltaNode = safeLCP(i, j);
    int s = 0;
    t = l;
    while (t>1) {
        t = (t + 1) / 2;
        if (safeLCP(i, i + (s + t) * direction) > deltaNode) {
            s += t;
        }
    }
    int gamma = i + s*direction + min(direction,0);

    uint leftChild, rightChild;
    if (min(i,j)==gamma) {
        leftChild = uint(gamma);
    } else {
        leftChild = uint(gamma) + srcB.initialNumBodies;
    }
    if (max(i,j)==gamma+1) {
        rightChild = uint(gamma+1);
    } else {
        rightChild = uint(gamma+1) + srcB.initialNumBodies;
    }

    uint internalIdx = uint(i) + srcB.initialNumBodies;
    nodes[internalIdx].childA = leftChild;
    nodes[internalIdx].childB = rightChild;
    nodes[internalIdx].readyChildren = 0u;
    nodes[internalIdx].comMass = vec4(0.0);
    nodes[internalIdx].aabb = AABB(vec3(1e38), vec3(-1e38));
    nodes[leftChild].parentId = internalIdx;
    nodes[rightChild].parentId = internalIdx;
    nodes[internalIdx].nodeDepth = uint(min(i, j));
    nodes[internalIdx].bodiesContained = uint(max(i, j) - min(i, j) + 1);
    if (i == 0) {
        nodes[internalIdx].parentId = 0xFFFFFFFFu;
    }
}


