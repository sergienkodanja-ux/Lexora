#version 150

uniform sampler2D BeforeTexture;
uniform sampler2D AfterTexture;
uniform vec2 resolution;
uniform float time;
uniform float effectAlpha;

uniform vec3 flameColor;
uniform float fireStrength;
uniform float fireSpeed;
uniform vec2 camOffset;

in vec2 uv;
in vec2 TexCoord;
out vec4 fragColor;

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float getSilhouette(vec2 coord) {
    if (coord.x < 0.0 || coord.x > 1.0 || coord.y < 0.0 || coord.y > 1.0) return 0.0;
    vec4 b = texture(BeforeTexture, coord);
    vec4 a = texture(AfterTexture, coord);
    vec3 d = abs(a.rgb - b.rgb);
    float diff = max(max(d.r, d.g), d.b);
    return smoothstep(0.010, 0.040, diff);
}

const vec2 DIRS[8] = vec2[](
    vec2( 1.0,  0.0), vec2(-1.0,  0.0),
    vec2( 0.0,  1.0), vec2( 0.0, -1.0),
    vec2( 0.7071,  0.7071), vec2(-0.7071,  0.7071),
    vec2( 0.7071, -0.7071), vec2(-0.7071, -0.7071)
);

void main() {
    vec2 fragUv = gl_FragCoord.xy / resolution.xy;
    vec2 px = 1.0 / resolution;

    float selfMask = getSilhouette(fragUv);

    // 1. Динамический шлейф при разворотах мыши
    vec2 motionVec = camOffset * 30.0 * px;
    float turnSpeed = length(camOffset);
    float trail = 0.0;

    if (turnSpeed > 0.03) {
        for (int i = 1; i <= 4; i++) {
            float f = float(i) / 4.0;
            vec2 samplePos = fragUv + motionVec * f;
            float m = getSilhouette(samplePos);
            trail = max(trail, m * (1.0 - f * 0.6) * smoothstep(0.03, 0.30, turnSpeed));
        }
    }

    // 2. Красивая мягкая радиальная обводка (Outline & Bloom) вокруг оружия
    float bloom = 0.0;
    float radius = (1.8 + fireStrength * 1.5);
    for (int d = 0; d < 8; d++) {
        vec2 dir = DIRS[d] * px * radius;
        bloom += getSilhouette(fragUv + dir) * 0.40;
        bloom += getSilhouette(fragUv + dir * 2.0) * 0.25;
        bloom += getSilhouette(fragUv + dir * 3.5) * 0.15;
    }
    bloom = clamp(bloom / 8.0 * 2.2, 0.0, 1.0);

    // 3. Мягкая живая пульсация
    float t = time * max(fireSpeed, 0.8);
    float pulse = 0.85 + 0.15 * sin(t * 2.5);
    float totalAura = clamp((bloom * pulse + trail * 1.2), 0.0, 1.0);

    // Отсекаем пустые пиксели (небо, мир, блоки)
    if (selfMask <= 0.005 && totalAura <= 0.005) {
        discard;
    }

    // 4. Цветовая палитра: сочные неоновые цвета темы без темных элементов
    vec3 baseColor = (length(flameColor) > 0.05) ? flameColor : vec3(0.65, 0.25, 1.0);
    vec3 hotCore = min(baseColor * 2.0 + vec3(0.4, 0.3, 0.2), vec3(1.0));
    vec3 outlineColor = mix(baseColor, hotCore, pow(totalAura, 1.5));

    vec4 handColor = texture(AfterTexture, fragUv);
    vec3 finalRgb;
    float finalAlpha;

    if (selfMask > 0.01) {
        // На самом оружии: подсветка контуров и мягкий оверлей цвета темы
        float edge = clamp((1.0 - selfMask) * 0.8 + bloom * 0.4, 0.0, 1.0);
        vec3 lit = mix(handColor.rgb, hotCore, edge * effectAlpha * 0.6);
        finalRgb = lit;
        finalAlpha = 1.0;
    } else {
        // Вокруг оружия: чистая сияющая обводка и мягкий шлейф
        finalRgb = outlineColor;
        finalAlpha = clamp(totalAura * effectAlpha * 1.6, 0.0, 0.95);
    }

    fragColor = vec4(finalRgb, finalAlpha);
}