// =============================================================
//                Hierarchical node-glow fragment shader
// Soft additive spherical splat; color from mass-weighted Node.avgColor
// (star RGB aggregated during tree propagate).
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

	float r2 = dot(vMapping, vMapping);
	if (r2 > 1.0) discard;

	// Soft spherical falloff toward billboard edge
	float glow = cos(3.14159265 * r2 * 0.5) * 0.5 + 0.5;
	vec3 color = vColor * (vIntensity * glow);

	fragColor = vec4(color, 1.0); // additive; alpha ignored

	// Cheap depth at COM (no sphere intersection needed for soft glow)
	vec4 centerClip = uProj * vec4(vCenterView, 1.0);
	float ndcDepth = centerClip.z / centerClip.w;
	gl_FragDepth = 0.5 * ndcDepth + 0.5;
}
