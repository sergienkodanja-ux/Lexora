#version 150

in vec2 fragCoord;

uniform float uTime;
uniform float uProgress;
uniform float uAlpha;
uniform vec4 uColor1; // Ethereal Cyan
uniform vec4 uColor2; // Mystic Lavender

out vec4 fragColor;

void main() {
    // uv.x: -1 (left) to +1 (right)
    // uv.y: -1 (feet) to +1 (head & above)
    vec2 uv = fragCoord * 2.0 - 1.0;

    // 1. Humanoid Silhouette Envelope
    float bodyW = mix(0.40, 0.58, smoothstep(-0.75, 0.2, uv.y));
    bodyW *= (1.0 - smoothstep(0.65, 0.92, uv.y) * 0.45); // Taper at head

    float horizDist = abs(uv.x) / max(0.01, bodyW);
    if (horizDist > 1.35) {
        discard;
    }

    // 2. Vertical Fade Envelope (Feet -1.0 to Head +0.85)
    float vertEnvelope = smoothstep(-1.0, -0.75, uv.y) * smoothstep(1.0, 0.75, uv.y);

    // 3. Silhouette Outline ("Обводка")
    // Peaks right at the perimeter of the body (horizDist ~ 0.92)
    // Drops off inside (so the player skin is clearly visible)
    // and drops off softly outside
    float outlinePeak = exp(-pow(abs(horizDist - 0.92) * 3.8, 1.8));
    float softInnerGlow = smoothstep(0.0, 0.85, horizDist) * 0.25;
    float outerFalloff = smoothstep(1.35, 0.95, horizDist);

    float combinedOutline = (outlinePeak * 1.1 + softInnerGlow) * outerFalloff * vertEnvelope;

    // Subtle gentle vertical shimmer along the outline
    float shimmer = sin(uv.y * 6.0 - uTime * 3.0 + sin(uv.x * 4.0)) * 0.15;
    combinedOutline = max(0.0, combinedOutline + shimmer * outlinePeak);

    // 4. Color: Ethereal Cyan -> Lavender, with white highlights on the outline peak
    vec3 col = mix(uColor1.rgb, uColor2.rgb, smoothstep(-0.4, 0.8, uv.y));
    col = mix(col, vec3(0.95, 0.98, 1.0), outlinePeak * 0.65);

    // 5. Half intensity as requested ("раза в 2 меньше")
    float finalAlpha = combinedOutline * (uAlpha * 0.48);

    fragColor = vec4(col, clamp(finalAlpha, 0.0, 1.0));
}
