void resetKernel() {
    uint gid = gl_GlobalInvocationID.x;
    if (gid == 0) {
        head = 0u;
        activeThreads = 0u;
        tail = 0u;
        mergeQueueTail = 0u;
    }
}