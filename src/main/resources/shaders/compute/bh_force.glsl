// Force computation and merging
bool acceptanceCriterion(float s, float invDist, float thetaVal)
{
    return s * invDist < thetaVal;
}

float invDist(vec3 r, float soft)
{
    float dist2 = dot(r, r) + soft;
    float inv = inversesqrt(dist2);
    return inv;
}
void computeForce() 
{
    vec3 accel = vec3(0.0);
    uint gid = gl_GlobalInvocationID.x;
    if (gid >= numBodies) return;

    Body body = srcB.bodies[gid];

    uint stack[128];
    uint stackSize = 0;
    stack[stackSize++] = numBodies;

    while (stackSize > 0) {
        uint nodeIdx = stack[--stackSize];
        Node node = nodes[nodeIdx];
        vec3 r = node.comMass.xyz - body.posMass.xyz;
        float oneOverDist = invDist(r, SOFTENING);
        vec3 extent = node.aabb.max - node.aabb.min;
        float longestSide = max(extent.x, max(extent.y, extent.z));
        if (node.childA == 0xFFFFFFFFu) {
            accel += node.comMass.w * r * oneOverDist * oneOverDist * oneOverDist;
            // if (index[nodeIdx] != gid) {
            //     Body other = srcB.bodies[index[nodeIdx]];
            //     float bodyRadius = pow(body.posMass.w, 1.0/3.0);
            //     float otherRadius = pow(other.posMass.w, 1.0/3.0);
            //     float dist = length(r);
            //     if (collision && dist < bodyRadius + otherRadius) {
            //         vec3 velocityDifference = other.velPad.xyz - body.velPad.xyz;
            //         vec3 normal = normalize(r);
            //         float vImpact = dot(velocityDifference, normal);
            //         if (vImpact < 0) {
            //             float mEff = 1/(1/body.posMass.w + 1/other.posMass.w);
            //             float j = (1+elasticity)*mEff*vImpact;
            //             body.velPad.xyz += normal * j / body.posMass.w;
            //         }
            //         float penetration = bodyRadius + otherRadius - dist;
            //         if (penetration > 0) {
            //             vec3 correction = (penetration / (body.posMass.w + other.posMass.w)) * restitution * normal;
            //             body.posMass.xyz -= correction;
            //         }
            //     } else if (dist < bodyRadius + otherRadius) {
            //         if (mergeQueueTail < mergeQueue.length()) {
            //             mergeQueue[mergeQueueTail++] = uvec2(gid, index[nodeIdx]);
            //             mergeQueueTail = atomicAdd(mergeQueueTail, 1u);
            //         }
            //     } else {
            //         accel += node.comMass.w * r * oneOverDist * oneOverDist * oneOverDist;
            //     }
            // }
        }
        else if (acceptanceCriterion(longestSide/2, oneOverDist, 0.5)) {
            accel += node.comMass.w * r * oneOverDist * oneOverDist * oneOverDist;
        }
        else {
            stack[stackSize++] = node.childA;
            stack[stackSize++] = node.childB;
        }
    }

    vec3 newVel = body.velPad.xyz + accel * dt;
    vec3 newPos = body.posMass.xyz + newVel * dt;
    dstB.bodies[gid].velPad.xyz = newVel;
    dstB.bodies[gid].posMass.xyz = newPos;
    dstB.bodies[gid].posMass.w = body.posMass.w;
    dstB.bodies[gid].color = body.color;
}

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