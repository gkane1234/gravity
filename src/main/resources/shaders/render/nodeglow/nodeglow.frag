// =============================================================
//                Hierarchical node-glow fragment shader
// rect: flat color over the quad. circle: cosine disk, discarded outside r=1.
// nodeGlowWriteDepth writes the box-center depth; otherwise the quad's own depth.
// A fragment shader that writes gl_FragDepth on any path must write it on all,
// so the off path stores the raster depth (same value the fixed function would).
// =============================================================
#include "common/common.glsl"

in vec2 vMapping;
in float vIntensity;
in vec3 vColor;
in vec3 vCenterView;
in float vNdcScaleX;
in float vNdcScaleY;

out vec4 fragColor;

void main() {
	if (vIntensity <= 0.0) discard;

	float glow = 1.0;
	if (uNodeGlowShape != 0) {
		float r2 = dot(vMapping, vMapping);
		if (r2 > 1.0) discard;
		glow = cos(PI * r2 * 0.5) * 0.5 + 0.5;
	}
	fragColor = vec4(vColor * (vIntensity * glow), 1.0); // additive; alpha ignored

	if (uNodeGlowWriteDepth != 0) {
		vec4 centerClip = uProj * vec4(vCenterView, 1.0);
		float ndcDepth = centerClip.z / centerClip.w;
		gl_FragDepth = 0.5 * ndcDepth + 0.5;
	} else {
		gl_FragDepth = gl_FragCoord.z;
	}
}
