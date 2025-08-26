// Force computation and merging
bool acceptanceCriterion(float longestRadius, float invDist, float thetaVal)

{
    // when longestRadius > distance, theta is greater than 1 so we always accept since we could be inside the node
    // when longestRadius < distance, theta is less than 1 so accept based on the ratio for example:
    // if theta is 0.5 and longestRadius is 10 and distance is 21, then we accept since 10 / 21 < 0.5
    return longestRadius * invDist < thetaVal;
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
    if (gid >= srcB.numBodies || isEmpty(srcB.bodies[gid])) return;

    Body body = srcB.bodies[gid];

    uint stack[64];
    uint stackSize = 0;
    stack[stackSize++] = srcB.numBodies;


    while (stackSize > 0) {
        
        uint nodeIdx = stack[--stackSize];
        Node node = nodes[nodeIdx];
        vec3 r = node.comMass.xyz - body.posMass.xyz;
        float oneOverDist = invDist(r, SOFTENING);
        vec3 extent = node.aabb.max - node.aabb.min;
        float longestSide = max(extent.x, max(extent.y, extent.z));
        if (node.childA == 0xFFFFFFFFu) {
            uintDebug[1] = 1;
            accel += node.comMass.w * r * oneOverDist * oneOverDist * oneOverDist;
            if (index[nodeIdx] != gid) {
                Body other = srcB.bodies[index[nodeIdx]];
                float bodyRadius = pow(body.posMass.w, 1.0/3.0);
                float otherRadius = pow(other.posMass.w, 1.0/3.0);
                float dist = length(r);
                // if (collision && dist < bodyRadius + otherRadius) {
                //     vec3 velocityDifference = other.velPad.xyz - body.velPad.xyz;
                //     vec3 normal = normalize(r);
                //     float vImpact = dot(velocityDifference, normal);
                //     if (vImpact < 0) {
                //         float mEff = 1/(1/body.posMass.w + 1/other.posMass.w);
                //         float j = (1+elasticity)*mEff*vImpact;
                //         body.velPad.xyz += normal * j / body.posMass.w;
                //     }
                //     float penetration = bodyRadius + otherRadius - dist;
                //     if (penetration > 0) {
                //         vec3 correction = (penetration / (body.posMass.w + other.posMass.w)) * restitution * normal;
                //         body.posMass.xyz -= correction;
                //     }
                // } else 
                if (dist < bodyRadius + otherRadius && gid < index[nodeIdx]) {
                    uintDebug[1] = 2;
                    mergeQueue[mergeQueueTail++] = uvec2(gid, index[nodeIdx]);
                    mergeQueueTail = atomicAdd(mergeQueueTail, 1u);
                } else {
                    accel += node.comMass.w * r * oneOverDist * oneOverDist * oneOverDist;
                }
            }
        }
        else if (acceptanceCriterion(longestSide/2, oneOverDist, theta)) {
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
    dstB.numBodies = srcB.numBodies;
}

