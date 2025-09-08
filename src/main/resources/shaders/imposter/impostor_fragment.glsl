#version 430

in vec2 vMapping;
in float bodyToGlowRatio;
in float worldRadius;
in float mass;
in vec3 vCenterView;
in float vCenterClipW;

uniform mat4 uProj;

uniform int uPass; // 0 = sphere, 1 = glow


out vec4 fragColor;

void main() {
    const float SOLAR_THRESHOLD = 10000;
    float r2 = dot(vMapping, vMapping);
    const float MASS_FACTOR = 10000;
    const float bodyRenderDistance = 100000;
    if (r2 > 1.0) discard;

    float radius = sqrt(r2);

    if (uPass == 0) {
        // --- Sphere interior pass ---
        if (radius > bodyToGlowRatio) discard;

        if (length(vCenterView) > bodyRenderDistance) discard;

        vec3 normal = vec3(vMapping, sqrt(max(0.0, 1.0 - r2)));
        float diffuse = max(dot(normal, vec3(0.0, 0.0, 1.0)), 0.0);

        vec3 color = vec3(0.8 + 0.2 * diffuse);
        fragColor = vec4(color, 1.0);
    } else {
        // --- Glow pass ---
        //fragColor = vec4(1.0,0,0,1);
        if (radius <= bodyToGlowRatio) discard;

        // if (mass < SOLAR_THRESHOLD)
        //     discard;

        float glowRadius = 1 - bodyToGlowRatio;
        float t = (radius - bodyToGlowRatio) / glowRadius;
        //float fOfT = (t/(1-t));
        float glow = cos(3.14159*t)/2+1/2;

        //map glow from 1 to 0 over glowRadius<t<1




        vec3 glowColor = vec3(0.5);
        fragColor = vec4(glowColor * glow, 1.0); // additive, alpha ignored

        
    }
    // Reconstruct NDC position of this fragment
    float tanHalfFovY = 1.0 / uProj[1][1];
    float tanHalfFovX = 1.0 / uProj[0][0];

    float camDist = length(vCenterView);
    float ndcScaleY = worldRadius / (camDist * tanHalfFovY);
    float ndcScaleX = worldRadius / (camDist * tanHalfFovX);

    vec4 centerClip = uProj * vec4(vCenterView, 1.0);
    vec2 centerNdc = centerClip.xy / centerClip.w;
    vec2 fragNdc = centerNdc + vMapping * vec2(ndcScaleX, ndcScaleY);

    // View-space ray through this fragment
    vec3 ray = normalize(vec3(fragNdc.x * tanHalfFovX,
                              fragNdc.y * tanHalfFovY,
                              -1.0));

        // Sphere intersection in view space
    vec3 L = vCenterView;          // center in view space
    float b = dot(ray, L);
    float c = dot(L, L) - worldRadius * worldRadius;
    float h = b * b - c;

    // Discard for solid sphere if no hit; for glow, allow tangent fallback
    if (uPass == 0 && h < 0.0) discard;

    float sqrtH = sqrt(max(h, 0.0));
    float tNear = b - sqrtH;
    float tFar  = b + sqrtH;

    // If the near root is behind the camera, use the far root for glow; discard for solid
    if (uPass == 0 && tNear < 0.0) discard;
    float t = (tNear >= 0.0) ? tNear : tFar;

    vec3 posView = t * ray;

    // Project to clip space and write depth
    vec4 posClip = uProj * vec4(posView, 1.0);
    float ndcDepth = posClip.z / posClip.w;
    gl_FragDepth = 0.5 * ndcDepth + 0.5;

}
