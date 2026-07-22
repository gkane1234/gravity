# Known issues / backlog

Living list of bugs and visual problems noticed while running the sim.
Add new items here as they come up; fix or strike through when done.

## Open

### 10. Distant stars look like noise (rendering / LOD)
**Seen:** From far away, dense fields of stars read as speckly noise rather than coherent galaxies.
**Wanted:** Some form of LOD or aggregation — e.g. replace distant clumps with softer glow impostors / hierarchical glow instead of thousands of tiny point lights.
**Notes:** Related to existing glow impostor path and issue #8 (hard cut between glow and body). `minImpostorSize` helps a bit but does not aggregate clusters. Tree AABBs / regions could feed a distant glow pass later. Per-body glow alone is **not** a good fix — see #12.

### 11. Gravity appears to “die” after a while (physics)
**Seen:** After running for some time, bodies seem to stop accelerating from gravity and only coast on existing velocity (fly apart / stream outward).
**Suspects (unconfirmed):**
- Force kernel producing NaNs/Infs (softening is commented out in `bh_force.comp`: `dist2 = dot(r,r)` with no `+ soft`, so exact overlaps → `inversesqrt(0)`).
- Tree COM / AABB propagation failing or incomplete on later frames so acceptance always approximates with bad/zero mass.
- Bodies leaving bounds / becoming empty while others still integrate with broken tree.
- Extreme separations making all interactions “accept” with wrong COM after numerical blow-up.
**Next debug steps:** Log/read back `accel` or a few bodies’ velocities over time; check `uintDebug` / NaN in positions; re-enable softening; confirm propagate still fills root COM after many steps.

### 12. Per-star glow is a bad fix for distant visibility
**Seen:** Making each star also glow (impostor glow / point glow) does not solve distant starfields well — still noisy / wrong look.
**Wanted:** A different method than “every body draws a glow.” Need something that works at cluster/galaxy scale (aggregated LOD, soft density splats, hierarchical impostors, etc.), not per-point bloom.
**Notes:** Ties to #10 and #8. Treat current glow path as insufficient for this problem, not something to tune further as the main answer.

## Older (from README)

1. Galaxy generation sometimes tears itself apart  
2. Fixed SSBO names overwritten by swapping SSBOs  
3. Radix sort dispatches too many workgroups as body count drops  
4. Most shaders over-dispatch when body count drops  
5. Merged / OOB counts wrong at times (OOB can go negative)  
6. Radix bits stuck at 4  
7. Camera jumpy when far away  
8. Discrete jump between glow and body rendering when approaching  
9. Rename merge queue → merge tasks  
