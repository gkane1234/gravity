// Debug kernel

void debugKernel() {
    uint gid = gl_GlobalInvocationID.x;
    if (gid >= numWorkGroups) return;
    aabb[gid] = AABB(
        vec3(float(gid) + 0.1, float(gid) + 0.2, float(gid) + 0.3),
        vec3(float(gid) + 10.1, float(gid) + 10.2, float(gid) + 10.3)
    );
}