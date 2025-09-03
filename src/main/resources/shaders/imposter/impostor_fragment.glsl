#version 430

in vec2 vMapping;
in vec4 vColor;
in float trueRadius;
in float renderRadius;
in float mass;
in vec3 vCenter;
in float ndcDepth;

uniform mat4 cameraToClipMatrix;
uniform vec3 uCameraPos;

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
        if (radius > trueRadius) discard;

        vec3 normal = vec3(vMapping, sqrt(max(0.0, 1.0 - r2)));
        float diffuse = max(dot(normal, vec3(0.0, 0.0, 1.0)), 0.0);

        vec3 color = vColor.rgb * (0.8 + 0.2 * diffuse);
        fragColor = vec4(color, 1.0);
    } else {
        // --- Glow pass ---
        //fragColor = vec4(1.0,0,0,1);
        if (radius <= trueRadius) discard;

        if (mass < SOLAR_THRESHOLD)
            discard;

        float glowRadius = 1 - trueRadius;
        float t = (radius - trueRadius) / glowRadius;
        float glow = exp(-4 * t * t);




        vec3 glowColor = mix(vec3(1.0), vColor.rgb, 0.7);
        fragColor = vec4(glowColor * glow, 1.0); // additive, alpha ignored

        
    }
      //vec4 clipPos = cameraToClipMatrix * vec4(uCameraPos, 1.0);
  //float ndcDepth = gl_Position.z / gl_Position.w;
  gl_FragDepth = ((gl_DepthRange.diff * ndcDepth) +
      gl_DepthRange.near + gl_DepthRange.far) / 2.0;
}
