// Morton encoding kernel

void encodeMortonKernel()
{
    uint gid = gl_GlobalInvocationID.x;
    if (gid >= numBodies) return;

    AABB scene = aabb[0];
    vec3 pos = srcB.bodies[gid].posMass.xyz;
    vec3 extent = max(scene.max - scene.min, vec3(1e-9));
    vec3 pNorm = (pos - scene.min) / extent;

    morton[gid] = mortonEncode3D(pNorm);
    index[gid]  = gid;
}


