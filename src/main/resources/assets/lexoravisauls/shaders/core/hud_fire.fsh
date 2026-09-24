#version 150

uniform vec4 Rect;       // x, y, width, height in framebuffer coords
uniform float Radius;    // corner radius
uniform float Time;      // time in seconds
uniform vec4 ThemeColor; // r, g, b, alpha
uniform vec4 FireParams; // x: intensity, y: speed, z: auraRadius, w: extinguish (0.0 to 1.0)
uniform float Angle;     // angle in radians for dashed lines

out vec4 fragColor;

float sdRoundedBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

void main() {
    vec2 frag = gl_FragCoord.xy;
    vec2 center = Rect.xy + Rect.zw * 0.5;
    vec2 halfSize = Rect.zw * 0.5;
    float rad = min(Radius, min(halfSize.x, halfSize.y));

    vec2 diff = frag - center;
    float ca = cos(-Angle);
    float sa = sin(-Angle);
    vec2 localPos = vec2(diff.x * ca - diff.y * sa, diff.x * sa + diff.y * ca);

    float dist = sdRoundedBox(localPos, halfSize, rad);
    float intensity = FireParams.x;
    float speed = FireParams.y;
    float auraDist = max(FireParams.z, 6.0);
    float extinguish = clamp(FireParams.w, 0.0, 1.0);

    if (intensity <= 0.001 || extinguish >= 0.99) {
        discard;
    }

    // ── УЛУЧШЕННЫЙ ФОНАРИК: ПЛАВНЫЙ КРУГОВОЙ ПРОЖЕКТОР С МЯГКИМ ОРЕОЛОМ ──
    float angle = atan(localPos.y, localPos.x);
    float rotSpeed = 3.6 * speed;

    // Главный направленный пучок света фонарика
    float dAngle1 = mod(angle - Time * rotSpeed + 3.14159265, 6.2831853) - 3.14159265;
    float beam1 = exp(-abs(dAngle1) * 2.0);
    float beamHot1 = exp(-abs(dAngle1) * 4.8);

    // Вторичный мягкий световой шлейф фонарика
    float dAngle2 = mod(angle - Time * (rotSpeed * 0.45) + 3.14159265, 6.2831853) - 3.14159265;
    float beam2 = exp(-abs(dAngle2) * 3.2) * 0.42;

    float orbitLight = clamp(beam1 + beamHot1 * 0.85 + beam2, 0.0, 1.8);
    float rayScatter = 0.92 + 0.08 * sin(angle * 6.0 + Time * 2.5);

    vec3 lightColor = vec3(0.97, 0.985, 1.0);
    vec3 hotCore = vec3(1.0, 1.0, 1.0);

    if (dist <= 0.0) {
        // Внутри карточки: мягкая линзовая подсветка
        float innerDist = -dist;
        float innerEdge = smoothstep(0.0, 8.0, innerDist);
        vec3 innerGlow = mix(hotCore * (0.24 + orbitLight * 0.36), vec3(0.06, 0.07, 0.09), innerEdge);
        float innerAlpha = ThemeColor.a * (0.75 + (1.0 - innerEdge) * 0.25) * (1.0 - extinguish);
        fragColor = vec4(innerGlow, clamp(innerAlpha, 0.0, 1.0));
    } else {
        // Снаружи: радиальный свет, ядро границы и ореол фонарика
        float maxReach = auraDist * (0.70 + orbitLight * 0.65) * rayScatter;
        if (dist > maxReach) discard;

        float coreFalloff = exp(-dist / 2.4);
        float haloFalloff = smoothstep(maxReach, 0.0, dist);
        float lightPower = (coreFalloff * 1.8 + pow(haloFalloff, 1.5) * (0.60 + orbitLight * 1.50)) * intensity * (1.0 - extinguish);
        float alpha = clamp(lightPower * ThemeColor.a * 0.90, 0.0, 1.0);

        if (alpha <= 0.005) discard;
        vec3 col = mix(lightColor, hotCore, clamp(coreFalloff * 0.85 + orbitLight * 0.40, 0.0, 1.0));
        fragColor = vec4(col, alpha);
    }
}
