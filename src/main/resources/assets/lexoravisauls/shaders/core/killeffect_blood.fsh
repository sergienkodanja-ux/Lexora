#version 150

in vec2 fragCoord;

uniform float uTime;
uniform float uProgress;
uniform float uAlpha;
uniform vec4 uColor1;
uniform vec4 uColor2;

out vec4 fragColor;

// 2D Voronoi cellular noise for crystal shards & fractures
vec2 hash2(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return fract(sin(p) * 43758.5453);
}

float voronoi(vec2 p) {
    vec2 n = floor(p);
    vec2 f = fract(p);
    float minDist = 1.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 g = vec2(float(i), float(j));
            vec2 o = hash2(n + g);
            vec2 r = g + o - f;
            float d = dot(r, r);
            minDist = min(minDist, d);
        }
    }
    return sqrt(minDist);
}

void main() {
    vec2 uv = fragCoord * 2.0 - 1.0;
    float r = length(uv);
    if (r > 1.0) {
        discard;
    }

    float angle = atan(uv.y, uv.x);

    // 1. Expanding High-Velocity Shockwave Front
    float wavePos = clamp(uProgress * 1.15, 0.05, 0.95);
    float waveDist = abs(r - wavePos);
    float shockWave = exp(-waveDist * 32.0) * 1.8;
    float shockGlow = exp(-waveDist * 8.0) * 0.6;

    // 2. Voronoi Crystalline Fracture Veins
    vec2 crystalCoord = uv * 7.0 + vec2(sin(uTime * 0.5) * 0.2, cos(uTime * 0.5) * 0.2);
    float v = voronoi(crystalCoord);
    float crystalRidges = smoothstep(0.04, 0.12, v) * smoothstep(0.45, 0.15, v);

    // 3. Radial Blood Fractures
    float fissures = pow(abs(sin(angle * 6.0 + sin(r * 12.0 - uTime * 4.0))), 12.0) * (1.0 - r);

    // 4. Blazing Core Detonation
    float core = exp(-r * 3.8) * 1.5;

    // Chromatic Blood Colors: Vermilion bright red + Deep dark ruby + Specular coral white
    vec3 col = mix(uColor2.rgb, uColor1.rgb, smoothstep(0.9, 0.2, r));
    col += vec3(1.0, 0.35, 0.45) * (shockWave + crystalRidges * 0.7);
    col += vec3(1.0, 0.9, 0.95) * (core * 0.6 + shockWave * 0.4);
    col += vec3(0.9, 0.1, 0.2) * fissures;

    float combinedAlpha = (shockWave + shockGlow + crystalRidges * 0.75 + fissures * 0.8 + core) * uAlpha * (1.0 - r * 0.4);

    fragColor = vec4(col, clamp(combinedAlpha, 0.0, 1.0));
}
