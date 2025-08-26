// Morton encoding kernel
uint64_t expandBits21(uint v)
{
    uint64_t x = uint64_t(v) & 0x1FFFFFul;
    x = (x | (x << 32)) & 0x1F00000000FFFFul;
    x = (x | (x << 16)) & 0x1F0000FF0000FFul;
    x = (x | (x << 8))  & 0x100F00F00F00F00Ful;
    x = (x | (x << 4))  & 0x10C30C30C30C30C3ul;
    x = (x | (x << 2))  & 0x1249249249249249ul;
    return x;
}

uint64_t morton3D64(uint x, uint y, uint z)
{
    return (expandBits21(x) << 2) | (expandBits21(y) << 1) | expandBits21(z);
}

uint64_t mortonEncode3D(vec3 pNorm)
{
    const float MAX_VALUE = 2097151.0;
    float fx = clamp(floor(pNorm.x * MAX_VALUE), 0.0, MAX_VALUE);
    float fy = clamp(floor(pNorm.y * MAX_VALUE), 0.0, MAX_VALUE);
    float fz = clamp(floor(pNorm.z * MAX_VALUE), 0.0, MAX_VALUE);
    uint xi = uint(floor(fx));
    uint yi = uint(floor(fy));
    uint zi = uint(floor(fz));
    return morton3D64(xi, yi, zi);
}
void encodeMortonKernel()
{
    uint gid = gl_GlobalInvocationID.x;
    if (gid >= srcB.numBodies) return;
    //If the body is empty, set code to the dead value
    if (isEmpty(srcB.bodies[gid])) {
        morton[gid] = 0xFFFFFFFFu;
        index[gid] = gid;
        return;
    }

    AABB scene = aabb[0];
    vec3 pos = srcB.bodies[gid].posMass.xyz;
    vec3 extent = max(scene.max - scene.min, vec3(1e-9));
    vec3 pNorm = (pos - scene.min) / extent;

    morton[gid] = mortonEncode3D(pNorm);
    index[gid]  = gid;
}


