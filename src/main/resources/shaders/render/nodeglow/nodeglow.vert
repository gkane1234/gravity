// =============================================================
//                 Hierarchical node-glow vertex shader
// Soft additive spherical billboards at BH internal-node COMs.
// Draw only when ALL of:
//   1) camDist >= nodeGlowActivateDist  (far enough in view space)
//   2) projected AABB extent in (min, θ_r)  — BH open/accept
//      if projected > θ_r → open (do not draw; children cover)
//   3) 1 <= nodeDepth <= nodeGlowMaxDepth
//      nodeDepth = height above furthest leaf (set in treePropagate)
// θ_r from Settings.nodeGlowThetaPx (pixels → NDC on CPU).
// Color: mass-weighted avg star RGB aggregated onto Node.avgColor.
// Intensity: mass luminosity with 1/r² (camDist), spread over AABB splat,
// then MASS_GLOW_SCALE * Settings.nodeGlowIntensity, then Reinhard.
// =============================================================
#include "common/common.glsl"

out vec2 vMapping;
out float vIntensity;
out vec3 vColor;
out vec3 vCenterView;
out float vNdcScaleX;
out float vNdcScaleY;

const float BOX_CORRECTION = 1.5;
// Keep glow splat close to the accepted node size (spherical soft radius)
const float GLOW_SOFT_PAD = 1.1;
// Deep accept band: kids stay drawable when a parent just above θ_r opens.
// Prefer many small cells over one coarse blob.
const float MIN_THETA_FRAC = 0.04;
// Surface-brightness scale after 1/r² luminosity / projected splat; Reinhard below.
// Tuned so many mid-depth splats sum near prior filled-haze / body glow feel.
const float MASS_GLOW_SCALE = 2.5e-4;
const float AREA_EPS = 1e-8;
// View-space depth where mass/projectedArea matched body glow. Flux scales as
// (REF/camDist)² from that calibration (same depth basis as activateDist / bodyRenderDistance).
const float REF_CAM_DIST = 1000.0;

void cullNode() {
	vIntensity = 0.0;
	vColor = vec3(0.0);
	vMapping = vec2(0.0);
	vCenterView = vec3(0.0);
	vNdcScaleX = 0.0;
	vNdcScaleY = 0.0;
	gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
}

void main() {
	// Instance over internalNodes (same precedent as regions.vert)
	if (gl_InstanceID >= int(sim.initialNumBodies) - 1) {
		cullNode();
		return;
	}

	Node node = internalNodes[gl_InstanceID];

	if (emptyAABB(node.aabb) || node.comMass.w <= 0.0 || node.bodiesContained == 0u) {
		cullNode();
		return;
	}

	// Depth gate: post-propagate nodeDepth is height from furthest leaf
	// (leaves = 0; root = tall). Cap at nodeGlowMaxDepth so galaxy-scale
	// parents never draw a single COM blob — children / mid nodes cover.
	uint depth = node.nodeDepth;
	if (depth < 1u || depth > uint(uNodeGlowMaxDepth)) {
		cullNode();
		return;
	}

	vec3 minC = relativeLocation(vec3(node.aabb[0], node.aabb[1], node.aabb[2]), uRelativeTo) * cameraScale;
	vec3 maxC = relativeLocation(vec3(node.aabb[3], node.aabb[4], node.aabb[5]), uRelativeTo) * cameraScale;
	vec3 aabbSize = maxC - minC;
	float longestSide = max(aabbSize.x, max(aabbSize.y, aabbSize.z));
	float halfExtent = 0.5 * longestSide;

	vec3 com = relativeLocation(node.comMass.xyz, uRelativeTo) * cameraScale;
	vec4 centerView4 = uModelView * vec4(com, 1.0);
	vCenterView = centerView4.xyz;

	// Perspective size uses view-space depth, not Euclidean cam distance
	// (length() underestimates size off-axis → glow accepts too early).
	float camDist = max(-vCenterView.z, 1e-6);

	// Distance gate: hierarchical glow only when far enough (view-space -z).
	if (camDist < uNodeGlowActivateDist) {
		cullNode();
		return;
	}

	float tanHalfFov = tan(0.5 * uFovY);

	// Full AABB extent in NDC units where screen height == 2.
	// pixelsAcross = projectedExtent * (framebufferHeight / 2).
	float projectedExtent = longestSide / (camDist * tanHalfFov);

	float thetaNdc = max(uNodeGlowThetaNdc, 1e-6);
	float minNdc = thetaNdc * MIN_THETA_FRAC;

	// BH-style structure LOD:
	//   projected >= θ_r → open (too coarse; children cover)
	//   projected <  min → too fine / noise
	if (projectedExtent >= thetaNdc || projectedExtent < minNdc) {
		cullNode();
		return;
	}

	// Soft acceptance near θ_r and at the fine edge (helps hard LOD / issue #8)
	float accept = 1.0 - smoothstep(thetaNdc * 0.75, thetaNdc, projectedExtent);
	accept *= smoothstep(minNdc, minNdc * 2.5, projectedExtent);

	// Luminosity ∝ mass with inverse-square falloff on camDist (view-space -z).
	// Spread over the projected AABB splat for peak surface brightness.
	// Equivalent to (mass/worldArea)*tanHalfFov²*REF² — does NOT use bare
	// mass/projectedArea, which climbed as camDist² when projectedArea shrank
	// and made haze diverge bright vs body impostors (constant peak color,
	// shrinking billboard ⇒ integrated ~1/r²).
	float projectedArea = max(projectedExtent * projectedExtent, AREA_EPS);
	float luminosity = node.comMass.w * ((REF_CAM_DIST * REF_CAM_DIST) / (camDist * camDist));
	float surface = luminosity / projectedArea;
	float raw = surface * MASS_GLOW_SCALE * accept * uNodeGlowIntensity;
	vIntensity = raw / (1.0 + raw);
	vColor = node.avgColor.rgb;

	if (vIntensity < 1e-4) {
		cullNode();
		return;
	}

	vec4 centerClip = uProj * centerView4;

	int vid = gl_VertexID & 3;
	if (vid == 0) vMapping = vec2(-1.0, -1.0) * BOX_CORRECTION;
	else if (vid == 1) vMapping = vec2(-1.0,  1.0) * BOX_CORRECTION;
	else if (vid == 2) vMapping = vec2( 1.0, -1.0) * BOX_CORRECTION;
	else               vMapping = vec2( 1.0,  1.0) * BOX_CORRECTION;

	// Spherical soft splat (longest-side ball), not elliptical AABB
	float glowHalf = max(halfExtent * GLOW_SOFT_PAD, minNdc * 0.5 * camDist * tanHalfFov);
	float ndcScaleY = glowHalf / (camDist * tanHalfFov);
	float ndcScaleX = ndcScaleY / max(uAspect, 1e-6);
	vNdcScaleX = ndcScaleX;
	vNdcScaleY = ndcScaleY;

	vec2 ndcOffset = vMapping * vec2(ndcScaleX, ndcScaleY);
	vec2 clipOffset = ndcOffset * centerClip.w;
	gl_Position = vec4(centerClip.xy + clipOffset, centerClip.z, centerClip.w);
}
