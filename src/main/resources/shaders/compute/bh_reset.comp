uniform bool firstPass;

void resetKernel() {
    uint gid = gl_GlobalInvocationID.x;
    if (gid == 0) {
        if (firstPass) {
            head = 0u;
            activeThreads = 0u;
            tail = 0u;
            mergeQueueTail = 0u;
            //mergeQueueHead = 0u;

            sim.justDied = 0u;
        }
        else {
            uintDebug[99] = sim.justDied;
            uintDebug[98] = sim.numBodies;
            sim.numBodies -= sim.justDied;
            //sim.pad1 = sim.justDied;
            sim.justDied = 0u;
    }
        }
}
