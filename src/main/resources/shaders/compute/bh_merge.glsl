Body mergeBodies(Body body1, Body body2) {
    Body mergedBody;    
    float newMass = body1.posMass.w + body2.posMass.w;
    if (newMass == 0.0) {
        return EMPTY_BODY;
    }
    if (body1.posMass.w == 0.0) {
        return body2;
    }
    if (body2.posMass.w == 0.0) {
        return body1;
    }
    vec3 newPos = (body1.posMass.xyz * body1.posMass.w + body2.posMass.xyz * body2.posMass.w) / newMass;
    mergedBody.posMass.xyz = newPos;
    mergedBody.posMass.w = newMass;
    mergedBody.velDensity.xyz = (body1.velDensity.xyz * body1.posMass.w + body2.velDensity.xyz * body2.posMass.w) / newMass;
    mergedBody.velDensity.w = (body1.posMass.w+body2.posMass.w)/(body1.posMass.w/body1.velDensity.w+body2.posMass.w/body2.velDensity.w);
    mergedBody.color = (body1.color*body1.posMass.w+body2.color*body2.posMass.w)/newMass;
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
    }
}