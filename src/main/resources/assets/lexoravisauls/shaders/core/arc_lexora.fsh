#version 150

uniform vec2  Center;    // центр кольца, framebuffer px (origin bottom-left)
uniform vec2  Radii;     // x = innerRadius, y = outerRadius
uniform vec2  Angles;    // x = startRad, y = endRad (0=12ч, по часовой)
uniform vec4  FillColor; // RGBA [0..1]
uniform float Softness;  // AA ширина в px (1.0)

out vec4 fragColor;

void main() {
    vec2  p    = gl_FragCoord.xy - Center;
    float dist = length(p);

    float ri  = Radii.x;
    float ro  = Radii.y;

    // ── SDF кольца ───────────────────────────────────────────────────────────
    // Положительный снаружи и внутри, 0 на краях ri и ro
    float inner = dist - ri;   // >0 снаружи внутреннего края
    float outer = ro - dist;   // >0 внутри внешнего края
    float ring  = min(inner, outer); // >0 только внутри кольца

    float aa       = max(fwidth(dist), 0.5) * Softness;
    float ringMask = smoothstep(0.0, aa, ring); // строго 0→1, нет инверсии

    if (ringMask <= 0.001) discard;

    // ── Угол пикселя: 0=12ч, по часовой стрелке ─────────────────────────────
    float TWO_PI   = 6.28318530718;
    float pixAngle = mod(atan(p.x, -p.y) + TWO_PI, TWO_PI);

    float a0 = mod(Angles.x + TWO_PI, TWO_PI);
    float a1 = mod(Angles.y + TWO_PI, TWO_PI);

    // ── Полный круг — обходим угловую логику ─────────────────────────────────
    float span = mod(a1 - a0 + TWO_PI, TWO_PI);
    bool  full = (span < 0.003 || span > (TWO_PI - 0.003));

    float arcMask = 1.0;
    if (!full) {
        // Принадлежность дуге
        float rel = mod(pixAngle - a0 + TWO_PI, TWO_PI);
        arcMask = (rel <= span) ? 1.0 : 0.0;

        // Плавные торцы (capAA в угловых единицах ~ 1px на среднем радиусе)
        float midR  = (ri + ro) * 0.5;
        float capAA = (midR > 0.5) ? (aa / midR) : 0.02;
        float t0    = rel;              // расстояние от начала
        float t1    = span - rel;       // расстояние до конца
        arcMask *= smoothstep(0.0, capAA, t0) * smoothstep(0.0, capAA, t1);
    }

    if (arcMask <= 0.001) discard;

    fragColor = vec4(FillColor.rgb, FillColor.a * ringMask * arcMask);
}
