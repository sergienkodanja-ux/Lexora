#version 150

// Feedback-шлейф: оптимизированное направленное смещение от камеры без темных элементов и с поддержкой любых кастомных анимаций.
//   Sampler0 — предыдущий кадр шлейфа
//   Sampler1 — сцена после рук (источник цвета шлейфа)
//   Sampler2 — маска руки (.r)

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform vec2 texSize;
uniform float time;
uniform float intensity;
uniform float speed;
uniform float length;
uniform float trailSoftness;
uniform float trailBlur;
uniform float smoke;
uniform float activity;
uniform float trailFade;
uniform float slash;
uniform float slashDir;
uniform float swingHand;
uniform vec2 camShift;   // экранный сдвиг feedback'а из-за поворота камеры (репроекция)
uniform vec4 glowColor;

in vec2 TexCoord;
out vec4 OutColor;

vec2 texelSize = vec2(0.0);

float sampleMask(vec2 uv) {
    return texture(Sampler2, clamp(uv, vec2(0.0), vec2(1.0))).r;
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));

    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    return noise(p) * 0.6 + noise(p * 2.1 + 5.2) * 0.4;
}

vec3 vividColor(vec3 color) {
    float peak = max(max(color.r, color.g), color.b);
    if (peak < 0.12) {
        return glowColor.rgb;
    }
    vec3 vivid = color / max(peak, 0.15);
    return clamp(mix(glowColor.rgb, vivid, 0.50), 0.0, 1.0);
}

void addSource(vec2 sourceUv, float weight, inout vec3 color, inout float alpha) {
    float mask = sampleMask(sourceUv);
    if (mask <= 0.001) return;

    vec3 sceneColor = texture(Sampler1, clamp(sourceUv, vec2(0.0), vec2(1.0))).rgb;
    float sourceWeight = mask * weight;
    color += vividColor(sceneColor) * sourceWeight;
    alpha += sourceWeight;
}

const vec2 DIRS[8] = vec2[](
    vec2( 1.0,  0.0), vec2(-1.0,  0.0),
    vec2( 0.0,  1.0), vec2( 0.0, -1.0),
    vec2( 0.7071,  0.7071), vec2(-0.7071,  0.7071),
    vec2( 0.7071, -0.7071), vec2(-0.7071, -0.7071)
);

void main() {
    vec2 texCoord = TexCoord;
    texelSize = 1.0 / max(texSize, vec2(1.0));

    float topDistance = 1.0 - texCoord.y;
    float topEdgeFade = smoothstep(0.012, 0.085, topDistance);
    float intensityC = clamp(intensity, 0.0, 1.5);
    float speedC = clamp(speed, 0.35, 2.4);
    float lengthC = clamp(length, 0.1, 1.0);
    float softness = clamp(trailSoftness, 0.55, 2.0);
    float blurRadius = clamp(trailBlur, 0.45, 2.5);
    float smokeC = clamp(smoke, 0.0, 0.8);
    float activityC = clamp(activity, 0.0, 1.0);
    float fadeSetting = clamp(trailFade, 0.55, 0.96);
    float slashC = clamp(slash, 0.0, 1.0);

    // Мягкая микро-турбулентность без кругового вращения
    vec2 curl = vec2(
        noise(texCoord * 16.0 + vec2(time * 0.18 * speedC, 3.1)) - 0.5,
        noise(texCoord * 16.0 + vec2(8.4, -time * 0.15 * speedC)) - 0.5
    ) * texelSize * (1.2 + blurRadius * 0.8);

    // 1. Репроекция камеры: шлейф движется ТОЛЬКО при движении мыши (влево/вправо/вверх/вниз)
    vec2 prevUv = texCoord + camShift + curl;

    vec4 prev = texture(Sampler0, clamp(prevUv, vec2(0.0), vec2(1.0)));
    vec2 edge = step(vec2(0.0), prevUv) * step(prevUv, vec2(1.0));
    prev *= edge.x * edge.y;

    float fade = mix(max(0.72, fadeSetting - 0.04), fadeSetting, min(softness, 1.8) / 1.8);
    fade = mix(fade - activityC * 0.010, 0.955, slashC * 0.15);
    prev.rgb *= fade;
    prev.a *= fade;
    prev *= topEdgeFade;
    if (prev.a < 0.006) {
        prev = vec4(0.0);
    }

    vec3 sourceColor = vec3(0.0);
    float sourceAlpha = 0.0;

    // 2. Статичная 8-направленная диффузия без вращения по кругу
    float spread = 2.0 + blurRadius * 2.5 + lengthC * 6.0;
    for (int d = 0; d < 8; d++) {
        vec2 dir = DIRS[d];
        vec2 sUv1 = texCoord + dir * texelSize * (spread * 0.4);
        vec2 sUv2 = texCoord + dir * texelSize * (spread * 0.9);
        vec2 sUv3 = texCoord + dir * texelSize * (spread * 1.5);

        addSource(sUv1, 0.50, sourceColor, sourceAlpha);
        addSource(sUv2, 0.32, sourceColor, sourceAlpha);
        addSource(sUv3, 0.18, sourceColor, sourceAlpha);
    }

    // 3. Динамический след от взмаха/движения для любых кастомных анимаций
    if (slashC > 0.001) {
        float slashLength = 5.0 + blurRadius * 4.0 + lengthC * 8.0;
        for (int d = 0; d < 8; d++) {
            vec2 dir = DIRS[d];
            vec2 sUv = texCoord - dir * texelSize * slashLength * (0.5 + slashC * 0.5);
            addSource(sUv, slashC * 0.45, sourceColor, sourceAlpha);
        }
    }

    float body = sampleMask(texCoord);
    float outside = 1.0 - body * 0.97;
    float wisps = mix(0.70, 1.15, fbm(texCoord * 40.0 + vec2(-time * 0.20 * speedC, time * 0.20 * speedC)));

    float newAlpha = 0.0;
    if (sourceAlpha > 0.001) {
        newAlpha = smoothstep(0.010, 0.18, sourceAlpha / 3.6);
        newAlpha *= outside * wisps;
        newAlpha *= (0.25 + intensityC * 0.25 + smokeC * 0.30 + activityC * 0.10 + slashC * 0.28);
        newAlpha *= topEdgeFade;
    }

    vec3 newColor = sourceAlpha > 0.001 ? sourceColor / sourceAlpha : glowColor.rgb;
    newColor = max(newColor, glowColor.rgb * 0.85);

    vec3 outColor = vec3(0.0);
    float outAlpha = 0.0;

    if (prev.a > 0.01 || newAlpha > 0.005) {
        outColor = mix(prev.rgb, newColor, clamp(newAlpha * (2.9 + slashC * 1.4), 0.0, 0.86));
        outAlpha = clamp(prev.a + newAlpha * (1.0 - prev.a), 0.0, 0.85 + slashC * 0.08);
    }

    if (outAlpha < 0.015) {
        outColor = vec3(0.0);
        outAlpha = 0.0;
    }

    OutColor = vec4(outColor, outAlpha);
}
