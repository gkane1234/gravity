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
        //debug[0]=bodies.x;
        //debug[1]=bodies.y;
        Body mergedBody = mergeBodies(body1, body2);
        dstB.bodies[bodies.x] = mergedBody;
        dstB.bodies[bodies.y] = EMPTY_BODY;
        dstB.numBodies--;
    }
}