#version 150

// ============================================================================
// SHATTER / REASSEMBLY — Voronoi-based cracked-glass effect.
//
// Renders an already-sampled texture (Sampler0) as if it were broken into
// irregular shards, each shard flying in from (or out to) a random direction
// and rotation, converging on the assembled image as Progress -> 1.
//
// This shader does NOT render arbitrary GUI content — Sampler0 must already
// be a texture (a static PNG, or eventually a render-to-texture capture of a
// panel, which this project does not yet have — see ScreenCaptureManager,
// which only captures the background BEHIND the GUI for blur, not GUI
// content itself). What this shader owns is purely the shatter/reassembly
// geometry: which shard a pixel belongs to, and how far/rotated that shard
// is from its rest position at the current Progress.
//
// Coordinate conventions match rounded_rect_lexora.fsh exactly: gl_FragCoord
// is framebuffer pixel space, Rect is [x, y, width, height] of the target
// area in that same space, UV is built the same way (with the same Y-flip)
// so this can sit as a drop-in alternative fill mode for that same rect-quad
// draw call.
// ============================================================================

uniform vec4 Rect;
uniform float Radius;
uniform vec4 FillColor;
uniform float Softness;

uniform sampler2D Sampler0;
uniform vec2 TexMin;
uniform vec2 TexMax;

// Shatter-specific uniforms.
// Progress: 0.0 = fully shattered/flown apart, 1.0 = fully assembled/whole.
// Same Progress value drives BOTH opening (0->1) and closing (1->0) — the
// Java side just runs the lerp in the opposite direction; the shader itself
// doesn't need to know which direction time is moving.
uniform float Progress;
// Roughly how many shards across the shorter axis of Rect — higher = smaller,
// more numerous shards. 6-10 is a reasonable range for a GUI panel; very high
// values get expensive (see PERF NOTE below) and visually noisy.
uniform float ShardScale;
// World-space-ish distance (in the same pixel units as Rect) shards travel
// at Progress == 0. Large flight requested — shards should originate well
// outside the panel's own bounds, not just jitter near their rest position.
uniform float FlyDistance;
// Seeds the hash functions below so repeated opens of the same panel don't
// always shatter in an identical pattern. Java side can pass e.g. the
// system time at panel-open, or leave at a fixed value for a deterministic
// look — either is valid, this is just an extra hash input.
uniform float RandomSeed;
// Continuously-growing seconds since assembly (Progress reaching ~1.0)
// finished — 0.0 means "no drip effect happening at all" (still mid-shatter,
// or closing). Drives the post-assembly glow/droplet/fall/merge/spread
// sequence layered on top of the shatter fill; see the DRIP SEQUENCE block
// below main() for the full breakdown of what happens at which DripTime
// range.
uniform float DripTime;

out vec4 fragColor;

float sdRoundRect(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// Standard cheap 2D hash — deterministic pseudo-random float in [0,1) from a
// 2D input. Not cryptographic, doesn't need to be; just needs to look random
// enough per-cell and be stable frame-to-frame for the same cell id.
float hash1(vec2 p) {
    p = fract(p * vec2(123.34, 456.21) + RandomSeed);
    p += dot(p, p + 34.45);
    return fract(p.x * p.y);
}

vec2 hash2(vec2 p) {
    return vec2(hash1(p), hash1(p + vec2(17.13, 91.7)));
}

// ----------------------------------------------------------------------------
// Per-shard flight parameters, factored into one function since both the
// search below and the final shading need the exact same formulas applied
// to whichever candidate cell they're looking at — keeping this as a single
// shared function is what guarantees the winning candidate found by the
// search and the shard actually drawn are computed identically, rather than
// two hand-copied formulas silently drifting apart from each other.
// ----------------------------------------------------------------------------
struct ShardFlight {
    vec2 restSeedUv;   // where this shard's content lives in the texture (rest position, unscaled uv)
    vec2 currentSeedUv; // where this shard's seed point is RIGHT NOW on screen, given Progress
    float shardProgress; // this shard's own (staggered) 0..1 progress
    float rotAngle;
};

ShardFlight computeShardFlight(vec2 cellId) {
    vec2 seedUv = (cellId + hash2(cellId)) / ShardScale;

    vec2 restPos = seedUv - vec2(0.5, 0.5);
    float radialAngle = atan(restPos.y, restPos.x);
    float distFromCenter = length(restPos);
    float fallbackAngle = hash1(cellId) * 6.28318530718;
    float baseAngle = distFromCenter > 0.001 ? radialAngle : fallbackAngle;
    float angleJitter = (hash1(cellId + vec2(41.7, 12.9)) - 0.5) * 1.1;
    float shardAngle = baseAngle + angleJitter;
    vec2 shardDir = vec2(cos(shardAngle), sin(shardAngle));

    float shardStagger = hash1(cellId + vec2(5.21, 2.13)) * 0.35;
    float shardRotSpeed = (hash1(cellId + vec2(9.73, 3.14)) - 0.5) * 4.0;

    float shardProgress = clamp((Progress - shardStagger) / max(0.0001, 1.0 - shardStagger), 0.0, 1.0);
    float flyAmount = 1.0 - shardProgress;

    float edgeDistanceScale = clamp(0.6 + distFromCenter * 0.8, 0.6, 1.0);
    vec2 flyOffsetUv = shardDir * (FlyDistance / Rect.zw) * flyAmount * edgeDistanceScale;

    ShardFlight result;
    result.restSeedUv = seedUv;
    result.currentSeedUv = seedUv + flyOffsetUv;
    result.shardProgress = shardProgress;
    result.rotAngle = shardRotSpeed * flyAmount;
    return result;
}

// ----------------------------------------------------------------------------
// THE actual shatter search: for a given screen pixel, which shard (if any)
// is currently covering it, given that shards have moved from their rest
// position? This replaces a plain "nearest rest-space Voronoi cell" lookup
// (which breaks down once shards have actually moved — see the two failed
// attempts in this file's edit history/commit messages) with a search over
// CURRENT (displaced) shard positions instead: try every rest-space cell
// within a wide neighborhood, compute where each one's seed currently sits
// on screen after flight, and keep whichever candidate's current position is
// closest to this screen pixel.
//
// Neighborhood is 15x15 (7 cells in each direction from the pixel's own
// grid cell) rather than the minimal 3x3 a stationary Voronoi lookup needs,
// specifically because a fully-flown shard can travel roughly that many
// cells at this file's default FlyDistance/ShardScale — see the PERF NOTE
// at the bottom of this file before increasing ShardScale, FlyDistance, or
// this search radius further, all three trade directly against this cost.
// ----------------------------------------------------------------------------
// SEARCH_RADIUS is written as the literal 7 directly in the loop bounds
// below (not as a named const) — some older/stricter GLSL 150 driver
// implementations are pickier about using a named constant as a loop bound
// than a plain integer literal, and this file already has enough unverified
// risk without adding one more axis of "might not compile on some drivers".
const float NO_SHARD_THRESHOLD = 0.74; // in units of 1 cell-width. MUST be >= sqrt(2)/2 (~0.707, the worst-case distance from a point inside a cell to that cell's own jittered seed) or pixels go transparent even at Progress==1 with zero displacement — verified numerically, 0.6 (an earlier value) was NOT sufficient and punched holes in the fully-assembled image. 0.74 leaves a small margin above the 0.707 floor.

// Duplicated from GpsEditorScreen.java's DRIP_TOTAL_SECONDS (3.2f) — this
// shader has no way to read that Java constant directly, so it's mirrored
// here by hand. If DRIP_TOTAL_SECONDS changes on the Java side, this MUST
// change to match, or the spread-wash timing below will silently drift out
// of sync with when Java actually stops advancing DripTime.
const float DRIP_TOTAL_SECONDS_GLSL = 3.2;

vec4 findCoveringShard(vec2 screenUv) {
    vec2 scaledScreenUv = screenUv * ShardScale;
    vec2 baseCell = floor(scaledScreenUv);

    float bestDist = 1e6;
    vec2 bestCellId = baseCell;

    for (int y = -7; y <= 7; y++) {
        for (int x = -7; x <= 7; x++) {
            vec2 candidateCellId = baseCell + vec2(float(x), float(y));
            ShardFlight flight = computeShardFlight(candidateCellId);

            float d = length(flight.currentSeedUv * ShardScale - scaledScreenUv);
            if (d < bestDist) {
                bestDist = d;
                bestCellId = candidateCellId;
            }
        }
    }

    float withinShard = bestDist <= NO_SHARD_THRESHOLD ? 1.0 : 0.0;
    return vec4(bestCellId, bestDist, withinShard);
}

// ----------------------------------------------------------------------------
// DRIP SEQUENCE — runs entirely on top of the already-assembled shatter fill,
// driven by DripTime (seconds since assembly finished) rather than Progress.
// Per shard (identified the same way as everywhere else in this file, by its
// rest-space grid cell): after an individual random delay (0..~1.0s, so
// shards don't all start dripping in lockstep), a droplet appears at that
// shard's rest position and falls straight down (+screenUv.y, see this
// file's edit history for why +Y is "down" in this coordinate convention)
// at a constant speed for ~1.2s, then stops.
//
// "Merging" between nearby droplets is NOT implemented as explicit droplet-
// to-droplet interaction (there's no cross-fragment memory in a fragment
// shader to track that kind of pairwise relationship) — it's a metaball
// field instead: every droplet contributes a falloff-with-distance glow
// value to every pixel, all contributions within reach of a given pixel are
// SUMMED, and the sum is what's compared against a brightness threshold.
// Where two droplets' fields overlap, the summed brightness there exceeds
// what either alone would produce — visually reading as the droplets
// pulling together / merging into a shared blob, purely as a side effect of
// summing overlapping fields, without any single droplet "knowing about"
// any other.
//
// After ~2.2s (last droplet has had time to fall + settle), a separate
// "spread" term fades in a soft glow wash across the ENTIRE panel — the
// "flows across the finished GUI as a highlight" ask — using DripTime's
// own remaining budget (see DRIP_TOTAL_SECONDS in GpsEditorScreen.java) to
// fade it in and hold it before the whole drip system stops advancing.
// ----------------------------------------------------------------------------
float dripDropletField(vec2 candidateCellId, vec2 screenUv) {
    vec2 restSeedUv = (candidateCellId + hash2(candidateCellId)) / ShardScale;

    float dropDelay = hash1(candidateCellId + vec2(71.3, 19.4)) * 1.0; // 0..1.0s stagger
    float localDripTime = DripTime - dropDelay;
    if (localDripTime <= 0.0) return 0.0; // hasn't started dripping yet

    float fallDuration = 1.2;
    float fallT = clamp(localDripTime / fallDuration, 0.0, 1.0);
    // Ease-in fall (accelerates like gravity) rather than constant speed —
    // reads as a droplet actually falling rather than sliding at fixed speed.
    float fallEase = fallT * fallT;
    float fallDistanceUv = fallEase * 0.5; // travels up to half the panel's own height

    vec2 dropletPos = restSeedUv + vec2(0.0, fallDistanceUv);

    // Droplet shrinks slightly as gravity "pulls" it into a tighter bead,
    // then holds size once it's done falling — purely cosmetic tightening,
    // not tied to any physical accuracy.
    float dropletRadius = mix(0.055, 0.032, fallEase);

    float d = length(screenUv - dropletPos);
    // Smooth inverse-falloff field, clamped so a droplet's contribution
    // can't blow up to infinity at d==0 (which plain 1/d^2 would do) —
    // standard metaball-field shaping.
    float field = dropletRadius * dropletRadius / max(d * d, 0.0001);
    return field;
}

vec3 computeDripGlow(vec2 screenUv, vec3 baseTint) {
    vec2 scaledScreenUv = screenUv * ShardScale;
    vec2 baseCell = floor(scaledScreenUv);

    float fieldSum = 0.0;
    // Narrower than findCoveringShard's 15x15 (7-cell) radius — droplets only
    // fall up to 0.5 UV units (see dripDropletField's fallDistanceUv, capped
    // at fallEase==1.0), which at this file's default ShardScale=8 is at
    // most 4 cells of travel. Radius 5 (11x11 = 121 candidates) covers that
    // with a one-cell margin, at roughly half the candidate count
    // findCoveringShard's full flight-distance radius needs — see the PERF
    // NOTE at the bottom of this file for the numeric derivation.
    for (int y = -5; y <= 5; y++) {
        for (int x = -5; x <= 5; x++) {
            vec2 candidateCellId = baseCell + vec2(float(x), float(y));
            fieldSum += dripDropletField(candidateCellId, screenUv);
        }
    }

    float dropletThreshold = 1.0;
    float dropletBrightness = smoothstep(dropletThreshold * 0.6, dropletThreshold, fieldSum);

    // Panel-wide spread wash — fades in only in DripTime's final stretch
    // (after individual droplets have had time to fall and settle), and
    // stays soft/uniform rather than following any particular droplet's
    // position, since by this point the ask is "the glow has finished
    // spreading across the whole finished panel", not "still tracking where
    // droplets used to be".
    float spreadStart = 2.2;
    float spreadT = clamp((DripTime - spreadStart) / max(0.0001, DRIP_TOTAL_SECONDS_GLSL - spreadStart), 0.0, 1.0);
    float spreadBrightness = smoothstep(0.0, 1.0, spreadT) * 0.35;

    float glowAmount = max(dropletBrightness, spreadBrightness);
    return baseTint * glowAmount;
}




void main() {
    vec2 center = Rect.xy + Rect.zw * 0.5;
    vec2 halfSize = Rect.zw * 0.5;
    vec2 p = gl_FragCoord.xy - center;

    // Overall panel silhouette (rounded rect) — the shatter's own coverage
    // test below (withinShard) is what actually clips shard visibility now;
    // this panel-shape edgeAlpha is layered on top so the panel's rounded
    // corners still read as rounded even where a shard happens to sample
    // right at that boundary, not as the primary clip.
    float dist = sdRoundRect(p, halfSize, Radius);
    float aa = max(fwidth(dist) * Softness, 1.0);
    float edgeAlpha = 1.0 - smoothstep(0.0, aa, dist);

    // Base UV across the rect, same construction/flip as rounded_rect_lexora.
    vec2 uv = (p + halfSize) / Rect.zw;
    vec2 screenUv = vec2(uv.x, 1.0 - uv.y);

    // Which shard, in its CURRENT (already-displaced) position, is covering
    // this screen pixel right now? See findCoveringShard's own comment block
    // for why this has to search displaced candidate positions rather than
    // just looking up the nearest rest-space cell — a stationary lookup is
    // what produced the earlier "everything appears to shatter FROM the
    // center" symptom.
    vec4 covering = findCoveringShard(screenUv);
    vec2 shardId = covering.xy;
    float withinShard = covering.w;

    // No shard currently covers this pixel — it's genuinely empty space
    // between flown-apart shards, not a stretched/clamped texture edge.
    // This is the other half of fixing the earlier symptom: the previous
    // version filled 100% of the rect regardless of shatter state (a
    // clamp always produced SOME color); now a pixel with nothing covering
    // it is actually transparent, which is what makes shards read as
    // discrete flying objects with real gaps between them instead of a
    // smeared full-rect texture.
    //
    // Still checks for drip glow even here (not an unconditional early
    // return to vec4(0.0) anymore) — DripTime only becomes nonzero once
    // shatterProgress is already ~1.0 on the Java side, so in practice this
    // branch is rarely hit while a drip is active (assembly leaves very few
    // uncovered pixels, verified earlier in this file's development), but a
    // droplet drifting slightly outside its parent shard's own coverage
    // radius shouldn't just vanish because of that technicality.
    if (withinShard < 0.5) {
        if (DripTime > 0.0) {
            vec3 glow = computeDripGlow(screenUv, vec3(0.65, 0.85, 1.0));
            fragColor = vec4(glow, (glow.r + glow.g + glow.b) / 3.0 * edgeAlpha);
        } else {
            fragColor = vec4(0.0);
        }
        return;
    }

    ShardFlight flight = computeShardFlight(shardId);

    // Sample color from the shard's REST texture position, rotated around
    // its own seed — NOT displaced by flyOffset, because flyOffset was
    // already fully consumed by findCoveringShard to decide WHICH shard
    // covers this pixel. Re-adding it here would double-apply the
    // displacement and sample the wrong part of the texture.
    float cosA = cos(flight.rotAngle);
    float sinA = sin(flight.rotAngle);
    vec2 localUv = screenUv - flight.currentSeedUv;
    vec2 rotatedLocalUv = vec2(
            localUv.x * cosA - localUv.y * sinA,
            localUv.x * sinA + localUv.y * cosA
    );
    vec2 sampleUv = flight.restSeedUv + rotatedLocalUv;
    vec2 clampedSampleUv = clamp(sampleUv, vec2(0.0), vec2(1.0));
    vec2 texUv = mix(TexMin, TexMax, clampedSampleUv);
    vec4 texColor = texture(Sampler0, texUv) * FillColor;

    // Shards fade in as they arrive — reads as materializing into place
    // rather than a hard pop, while staying visible for most of the flight
    // (0.35 floor, not 0.0) so they don't vanish mid-flight either.
    float shardAlpha = mix(0.35, 1.0, smoothstep(0.0, 0.6, flight.shardProgress));

    // Constant soft glow around every shard while it's still in motion (not
    // just at rest) — bestDist from the covering search is reused here
    // rather than recomputed, since it's already exactly "how far this pixel
    // is from its shard's covering point", which is exactly what a glow
    // falloff needs. Only visible while shardAlpha hasn't reached full
    // strength yet (still flying) — once a shard is fully settled, its
    // ambient glow hands off to the drip sequence above instead of both
    // glowing at once.
    float glowFalloff = 1.0 - smoothstep(0.0, NO_SHARD_THRESHOLD * 1.8, covering.z);
    float flightGlowAmount = glowFalloff * (1.0 - flight.shardProgress) * 0.5;
    vec3 flightGlowColor = vec3(0.65, 0.85, 1.0) * flightGlowAmount;

    vec3 dripGlow = DripTime > 0.0 ? computeDripGlow(screenUv, vec3(0.65, 0.85, 1.0)) : vec3(0.0);

    vec3 finalColor = texColor.rgb + flightGlowColor + dripGlow;
    fragColor = vec4(finalColor, texColor.a * shardAlpha * edgeAlpha);
}

// ----------------------------------------------------------------------------
// PERF NOTE — READ BEFORE TUNING ShardScale/FlyDistance/search radius UP:
// findCoveringShard scans a 15x15 neighborhood (225 candidate cells), and
// EACH candidate runs computeShardFlight (multiple hash1/hash2 evaluations,
// trig, etc.) — that's ~225x the cost of the earlier single-nearest-cell
// Voronoi lookup this replaced, PER FRAGMENT, every frame this is drawn.
// This was a deliberate accuracy-over-performance tradeoff (see this file's
// edit history: the plain-lookup version produced visibly wrong shatter
// behavior — shards appearing to converge in the center instead of flying
// outward — so this heavier search replaced it on purpose).
//
// At GUI-panel scale (a few hundred x few hundred pixels) this is still
// almost certainly fine on any GPU from the last decade, but it is real
// cost that scales with fragment count (panel size on screen) and with
// ShardScale (more, smaller cells = same 15x15 search radius covers less
// actual distance, so FlyDistance vs ShardScale need to stay roughly
// proportional — see the SEARCH_RADIUS=7 comment above computeShardFlight
// for why 7 cells was chosen for THIS file's default FlyDistance=260/
// ShardScale=8 pairing specifically).
//
// If this ends up feeling slow on lower-end GPUs, the first things to try,
// roughly cheapest-to-try first: (1) only run this shader while Progress is
// actually mid-animation (0 < Progress < 1) and fall back to the plain
// rounded_rect_lexora textured draw at rest — the calling Java code, not
// this file, decides when to use which shader; (2) lower ShardScale (fewer,
// bigger shards — quadratic win, half ShardScale is roughly a quarter the
// cell count); (3) only as a last resort, shrink the search radius literal
// in findCoveringShard's for-loops below 7 — doing so WILL cause shards that
// fly further than the new radius covers to stop being found/rendered, so
// FlyDistance should shrink proportionally alongside it, not independently.
//
// DRIP SEQUENCE ADDS A SECOND SEARCH ON TOP OF THE ABOVE:
// computeDripGlow -> dripDropletField runs its own independent candidate
// loop (11x11 = 121 candidates, narrower than findCoveringShard's 15x15 —
// see the radius comment inside computeDripGlow for the numeric derivation
// of why droplets don't need the full flight-distance search radius), so
// while DripTime > 0 (i.e. for DRIP_TOTAL_SECONDS after assembly finishes —
// see GpsEditorScreen.java), each fragment pays roughly 1.5x the per-frame
// cost described above: one 225-candidate search to find the covering
// shard, plus a separate 121-candidate search to sum the droplet field.
// This is still the single most expensive stretch of this whole shader's
// lifetime, just less severe than a naive same-radius search would have
// been. If this still needs to be cheaper, shrinking ShardScale (option 2
// above) helps both searches at once, quadratically.
// ----------------------------------------------------------------------------
