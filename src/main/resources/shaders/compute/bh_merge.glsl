Body mergeBodies(Body body1, Body body2) {
    Body mergedBody;    
    float newMass = body1.posMass.w + body2.posMass.w;
    vec3 newPos = (body1.posMass.xyz * body1.posMass.w + body2.posMass.xyz * body2.posMass.w) / newMass;
    mergedBody.posMass.xyz = newPos;
    mergedBody.posMass.w = newMass;
    mergedBody.velPad.xyz = (body1.velPad.xyz * body1.posMass.w + body2.velPad.xyz * body2.posMass.w) / newMass;
    mergedBody.velPad.w = newMass;
    mergedBody.color = vec4(1.0, 1.0, 1.0, 1.0);
    return mergedBody;
}

void mergeBodiesKernel() {
    uint gid = gl_GlobalInvocationID.x;
    if (gid > 0) return;
    for (uint i = 0; i < mergeQueueTail; i++) {
        uvec2 bodies = mergeQueue[i];
        Body body1 = srcB.bodies[bodies.x];
        Body body2 = srcB.bodies[bodies.y];
        Body mergedBody = mergeBodies(body1, body2);
        dstB.bodies[bodies.x] = mergedBody;
        dstB.bodies[bodies.y] = Body(vec4(0.0), vec4(0.0), vec4(0.0));
    }
}