#version 430

in vec2 vMapping;
in vec4 vColor;
in float trueRadius;
in float renderRadius;

uniform int uPass; // 0 = sphere, 1 = glow

out vec4 fragColor;

void main() {
    float r2 = dot(vMapping, vMapping);
    if (r2 > 1.0) discard;

    float radius = sqrt(r2);

    if (uPass == 0) {
        // --- Sphere interior pass ---
        if (radius > trueRadius) discard;

        vec3 normal = vec3(vMapping, sqrt(max(0.0, 1.0 - r2)));
        float diffuse = max(dot(normal, vec3(0.0, 0.0, 1.0)), 0.0);

        vec3 color = vColor.rgb * (0.8 + 0.2 * diffuse);
        fragColor = vec4(color, 1.0); // fully opaque
    } else {
        // --- Glow pass ---
        //fragColor = vec4(1.0,0,0,1);
        if (radius <= trueRadius) discard;

        float glowRadius = 1 - trueRadius;
        float t = (radius - trueRadius) / glowRadius;
        float glow = 0.3*exp(-4 * t * t);

        vec3 glowColor = mix(vec3(1.0), vColor.rgb, 0.7);
        fragColor = vec4(glowColor * glow, 1.0); // additive, alpha ignored
    }
}