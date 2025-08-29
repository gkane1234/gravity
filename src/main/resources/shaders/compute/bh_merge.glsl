Body mergeBodies(Body body1, Body body2) {
    Body mergedBody;    
    float newMass = body1.posMass.w + body2.posMass.w;
    if (newMass == 0.0) {
        return EMPTY_BODY;
    }
    vec3 newPos = (body1.posMass.xyz * body1.posMass.w + body2.posMass.xyz * body2.posMass.w) / newMass;
    mergedBody.posMass.xyz = newPos;
    mergedBody.posMass.w = newMass;
    mergedBody.velPad.xyz = (body1.velPad.xyz * body1.posMass.w + body2.velPad.xyz * body2.posMass.w) / newMass;
    mergedBody.velPad.w = 0;
    mergedBody.color = vec4(1.0, 1.0, 1.0, 1.0);
    floatDebug[0]=newMass;
    floatDebug[1]=mergedBody.velPad.xyz.x;
    floatDebug[2]=mergedBody.velPad.xyz.y;
    floatDebug[3]=mergedBody.velPad.xyz.z;
    floatDebug[4]=newPos.x;
    floatDebug[5]=newPos.y;
    floatDebug[6]=newPos.z;
    floatDebug[7]=mergedBody.color.x;
    floatDebug[8]=mergedBody.color.y;
    floatDebug[9]=mergedBody.color.z;
    //floatDebug[10]=mergedBody.color.w;
    return mergedBody;
}

void mergeBodiesKernel() {
    uint gid = gl_GlobalInvocationID.x;
    if (gid > 0) return;
    for (uint i = 0; i < mergeQueueTail; i++) {
        
        uvec2 bodies = mergeQueue[i];
        Body body1 = dstB.bodies[bodies.x];
        Body body2 = dstB.bodies[bodies.y];

        floatDebug[0]=body1.posMass.w;
        floatDebug[1]=body2.posMass.w;
        Body mergedBody = mergeBodies(body1, body2);
        dstB.bodies[bodies.x] = mergedBody;
        // when this happens, the body is set to empty on the swapped buffer
        // BUT it is still alive on the source buffer
        // After swapping, we need to make sure that at some point we set it to empty.
        dstB.bodies[bodies.y] = EMPTY_BODY; 

        if (isEmpty(body1) || isEmpty(body2)) {
            continue;
        }
        dstB.numBodies--;
    }
}