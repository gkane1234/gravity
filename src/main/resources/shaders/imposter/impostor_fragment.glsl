#version 430

in vec2 vMapping;
in vec4 vColor;
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
    if (r2 > 1.0) discard;

    float radius = sqrt(r2);

    if (uPass == 0) {
        // --- Sphere interior pass ---
        if (radius > bodyToGlowRatio) discard;

        vec3 normal = vec3(vMapping, sqrt(max(0.0, 1.0 - r2)));
        float diffuse = max(dot(normal, vec3(0.0, 0.0, 1.0)), 0.0);

        vec3 color = vColor.rgb * (0.8 + 0.2 * diffuse);
        fragColor = vec4(color, 1.0);
    } else {
        // --- Glow pass ---
        //fragColor = vec4(1.0,0,0,1);
        if (radius <= bodyToGlowRatio) discard;

        if (mass < SOLAR_THRESHOLD)
            discard;

        float glowRadius = 1 - bodyToGlowRatio;
        float t = (radius - bodyToGlowRatio) / glowRadius;
        float glow = exp(-4 * t * t);




        vec3 glowColor = mix(vec3(1.0), vColor.rgb, 0.7);
        fragColor = vec4(glowColor * glow, 1.0); // additive, alpha ignored

        
    }
    // Ray direction in view space
    vec3 ray = normalize(vec3(vMapping, -1.0)); // pointing into screen

    // Sphere intersection in view space
    vec3 oc = -vCenterView; // ray origin at camera (0,0,0), sphere center at vCenterView
    float b = dot(ray, oc);
    float c = dot(oc, oc) - worldRadius*worldRadius;
    float h = b*b - c;
    //if (h < 0.0) discard; // miss
    float t = b - sqrt(h); // nearest intersection
    vec3 posView = t * ray; // intersection point in view space

    // Project to clip space
    vec4 posClip = uProj * vec4(posView, 1.0);

    float ndcDepth = posClip.z / posClip.w;
    gl_FragDepth = 0.5 * ndcDepth + 0.5;

}