// =============================================================
//                 Hierarchical node-glow vertex shader
// Flat screen-aligned quads at BH node AABB centers.
// Two passes. Rectangles use nodeGlowMinDepth..nodeGlowMaxDepth.
// Circles use nodeGlowCircleMinDepth..nodeGlowCircleMaxDepth.
// nodeGlowDrawRect / nodeGlowDrawCircle skip a pass. uNodeGlowShape is the pass.
// nodeGlowProjectBox: keep the longest-side square, then grow it to cover any
//   part of the projected AABB that sticks out, plus a 1px overlap.
//   Off: screen square of the AABB longest side only.
//   The projected rect alone is smaller than that square when the long axis
//   points along the view, and that shrink is what opened the gaps.
// nodeGlowOneLevel: only the coarsest node under theta on each branch.
// nodeGlowMinFrac: fine edge of the size band, as a fraction of theta.
// Draw only when ALL of:
//   1) camDist >= nodeGlowActivateDist  (far enough in view space)
//   2) projected AABB extent in (min, θ_r)  — BH open/accept
//      if projected > θ_r → open (do not draw; children cover)
//   3) nodeGlowMinDepth <= nodeDepth <= nodeGlowMaxDepth
//      nodeDepth = height above furthest leaf (set in treePropagate)
// θ_r from Settings.nodeGlowThetaPx (pixels → NDC on CPU).
// Color: luminosity-weighted avg star RGB aggregated onto Node.avgColor (w = subtree L).
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

// Circle mode (and not projecting the box) uses the old disk: the visible
// radius is 1.1 * half the longest side, and the quad is 1.5x that so the
// cosine reaches 0.5 at the rim without the quad edge cutting it.
const float CIRCLE_BOX = 1.5;
const float CIRCLE_PAD = 1.1;
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
	// (leaves = 0; root = tall). nodeGlowMinDepth drops near-leaf nodes.
	// nodeGlowMaxDepth keeps galaxy-scale parents from drawing one COM blob.
	uint depth = node.nodeDepth;
	uint minDepth = uint(max(uNodeGlowMinDepth, 0));
	if (depth < minDepth || depth > uint(uNodeGlowMaxDepth)) {
		cullNode();
		return;
	}

	vec3 minC = relativeLocation(vec3(node.aabb[0], node.aabb[1], node.aabb[2]), uRelativeTo) * cameraScale;
	vec3 maxC = relativeLocation(vec3(node.aabb[3], node.aabb[4], node.aabb[5]), uRelativeTo) * cameraScale;
	vec3 aabbSize = maxC - minC;
	float longestSide = max(aabbSize.x, max(aabbSize.y, aabbSize.z));
	float halfExtent = 0.5 * longestSide;

	// Square sits on the box, not the COM. A COM near one face left a gap on the other.
	vec3 boxCenter = 0.5 * (minC + maxC);
	vec4 centerView4 = uModelView * vec4(boxCenter, 1.0);
	vCenterView = centerView4.xyz;

	// Perspective size uses view-space depth, not Euclidean cam distance
	// (length() underestimates size off-axis → glow accepts too early).
	float camDist = max(-vCenterView.z, 1e-6);

	// Distance gate: hierarchical glow only when far enough (view-space -z).
	// Squares also wait until nodeGlowRectMinDist. 0 means no extra near gate.
	if (camDist < uNodeGlowActivateDist || (uNodeGlowShape == 0 && camDist < uNodeGlowRectMinDist)) {
		cullNode();
		return;
	}

	float tanHalfFov = tan(0.5 * uFovY);

	// Full AABB extent in NDC units where screen height == 2.
	// pixelsAcross = projectedExtent * (framebufferHeight / 2).
	float projectedExtent = longestSide / (camDist * tanHalfFov);

	float thetaNdc = max(uNodeGlowThetaNdc, 1e-6);
	float minNdc = thetaNdc * max(uNodeGlowMinFrac, 0.0);

	// BH-style structure LOD:
	//   projected >= θ_r → open (too coarse; children cover)
	//   projected <  min → too fine / noise
	if (projectedExtent >= thetaNdc || projectedExtent < minNdc) {
		cullNode();
		return;
	}

	// One quad per lineage when nodeGlowOneLevel is on: the coarsest node
	// that is still under θ and inside the depth cap. If the parent is
	// drawn, this child is not. Off draws every node in the size band.
	if (uNodeGlowOneLevel != 0 && node.parentId != 0xFFFFFFFFu && node.parentId >= sim.initialNumBodies) {
		uint pIdx = node.parentId - sim.initialNumBodies;
		if (pIdx < uint(sim.initialNumBodies) - 1u) {
			Node parent = internalNodes[pIdx];
			if (parent.nodeDepth >= minDepth && parent.nodeDepth <= uint(uNodeGlowMaxDepth) && !emptyAABB(parent.aabb)) {
				vec3 pMin = relativeLocation(vec3(parent.aabb[0], parent.aabb[1], parent.aabb[2]), uRelativeTo) * cameraScale;
				vec3 pMax = relativeLocation(vec3(parent.aabb[3], parent.aabb[4], parent.aabb[5]), uRelativeTo) * cameraScale;
				vec3 pSize = pMax - pMin;
				float pLong = max(pSize.x, max(pSize.y, pSize.z));
				vec3 pCenter = 0.5 * (pMin + pMax);
				vec4 pView = uModelView * vec4(pCenter, 1.0);
				float pDist = max(-pView.z, 1e-6);
				float pExtent = pLong / (pDist * tanHalfFov);
				bool parentTooClose = uNodeGlowShape == 0 && pDist < uNodeGlowRectMinDist;
				if (pExtent < thetaNdc && !parentTooClose) {
					cullNode();
					return;
				}
			}
		}
	}

	// Fade only as this node itself reaches θ and hands off to its children.
	float accept = 1.0 - smoothstep(thetaNdc * 0.75, thetaNdc, projectedExtent);

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

	bool circle = uNodeGlowShape != 0;
	bool project = uNodeGlowProjectBox != 0;
	float boxCorrection = circle ? CIRCLE_BOX : 1.0;

	int vid = gl_VertexID & 3;
	vec2 corner = vec2(vid < 2 ? -1.0 : 1.0, (vid & 1) == 0 ? -1.0 : 1.0);
	vMapping = corner * boxCorrection;

	float pad = circle ? CIRCLE_PAD : 1.0;
	// Square side = longest side. The min keeps a node at the fine edge from collapsing to nothing.
	float glowHalf = max(halfExtent * pad, minNdc * 0.5 * camDist * tanHalfFov);
	float ndcScaleY = glowHalf / (camDist * tanHalfFov);
	float ndcScaleX = ndcScaleY / max(uAspect, 1e-6);

	// Neighbor gaps sit on the axis-aligned splits. A square of the longest side
	// covers a gap that is foreshortened to 45 degrees (that view has no bands).
	// When the split faces the camera the same gap is sqrt(2) wider on screen,
	// so stretch the rect along that screen axis. At 45 degrees the factor is 1.
	vec2 ndcHalf = vec2(ndcScaleX, ndcScaleY);
	if (!circle) {
		for (int axis = 0; axis < 3; ++axis) {
			vec2 screen = vec2(uModelView[axis][0], uModelView[axis][1]);
			float facing = length(screen);
			float grow = max(0.0, facing * 1.41421356 - 1.0);
			if (grow <= 0.0) continue;
			ndcHalf += abs(screen) * vec2(ndcScaleX, ndcScaleY) * (grow / facing);
		}
	}

	// Longest-side square, centered on the projected AABB center.
	vec2 centerNdc = centerClip.xy / centerClip.w;
	vec2 coverMin = centerNdc - ndcHalf;
	vec2 coverMax = centerNdc + ndcHalf;

	if (project) {
		// Union with the real screen rectangle. Never shrink the square:
		// the screen rect is the smaller one when the long axis points at the camera.
		vec2 ndcMin = vec2(1e20);
		vec2 ndcMax = vec2(-1e20);
		int inFront = 0;
		for (int i = 0; i < 8; ++i) {
			vec3 c = vec3(
				(i & 1) == 0 ? minC.x : maxC.x,
				(i & 2) == 0 ? minC.y : maxC.y,
				(i & 4) == 0 ? minC.z : maxC.z);
			vec4 clip = uProj * (uModelView * vec4(c, 1.0));
			if (clip.w <= 1e-4) continue;
			vec2 ndc = clip.xy / clip.w;
			ndcMin = min(ndcMin, ndc);
			ndcMax = max(ndcMax, ndc);
			inFront++;
		}
		if (inFront > 0) {
			coverMin = min(coverMin, ndcMin);
			coverMax = max(coverMax, ndcMax);
		}
		// 1px overlap to close the remaining crack. Capped so a sub-pixel node
		// does not get blown up to a couple of pixels (that fill killed the large sim).
		// uBodyDisappearNdc is 2px of NDC height. NDC x spans aspect times more.
		vec2 onePx = vec2(0.5 * uBodyDisappearNdc / max(uAspect, 1e-6), 0.5 * uBodyDisappearNdc);
		vec2 outset = min(onePx, 0.15 * vec2(ndcScaleX, ndcScaleY));
		coverMin -= outset;
		coverMax += outset;
	}

	// Rect: scale the finished quad about its center. 1 = the normal square
	// (or the projected union). Circle keeps its own pad.
	if (!circle) {
		float rectScale = max(uNodeGlowRectScale, 0.0);
		vec2 rectCenter = 0.5 * (coverMin + coverMax);
		vec2 rectHalf = 0.5 * (coverMax - coverMin) * rectScale;
		coverMin = rectCenter - rectHalf;
		coverMax = rectCenter + rectHalf;
	}

	// Circle: quad is 1.5x the covered rect so the unit disk is that rect.
	if (circle) {
		vec2 c = 0.5 * (coverMin + coverMax);
		vec2 h = 0.5 * (coverMax - coverMin) * CIRCLE_BOX;
		coverMin = c - h;
		coverMax = c + h;
	}

	vNdcScaleX = 0.5 * (coverMax.x - coverMin.x);
	vNdcScaleY = 0.5 * (coverMax.y - coverMin.y);
	vec2 ndc = mix(coverMin, coverMax, corner * 0.5 + 0.5);
	gl_Position = vec4(ndc * centerClip.w, centerClip.z, centerClip.w);
}
