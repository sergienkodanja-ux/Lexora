#version 150

// Финальный композит: сцена после рук + дым/огонь вокруг силуэта (порт hand_fire).
// Пишется поверх основного кадра без блендинга (replace).
//   Sampler0 — сцена после рук
//   Sampler1 — размытый шлейф (smoke)
//   Sampler2 — маска руки (.r)

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform vec2 texSize;
uniform float time;
uniform float intensity;
uniform float handSoftness;
uniform float handBlur;
uniform float smoke;
uniform float activity;
uniform vec4 glowColor;

in vec2 TexCoord;
out vec4 OutColor;

vec2 texelSize = vec2(0.0);

float sampleMask(vec2 uv) {
    return texture(Sampler2, clamp(uv, vec2(0.0), vec2(1.0))).r;
}

float edgeMask(vec2 uv) {
    float c = sampleMask(uv);
    float e = 0.0;
    e += abs(c - sampleMask(uv + vec2( texelSize.x, 0.0)));
    e += abs(c - sampleMask(uv + vec2(-texelSize.x, 0.0)));
    e += abs(c - sampleMask(uv + vec2(0.0,  texelSize.y)));
    e += abs(c - sampleMask(uv + vec2(0.0, -texelSize.y)));
    return clamp(e * 0.9, 0.0, 1.0);
}

void main() {
    vec2 texCoord = TexCoord;
    texelSize = 1.0 / max(texSize, vec2(1.0));

    float topDistance = 1.0 - texCoord.y;
    float topEdgeFade = smoothstep(0.012, 0.085, topDistance);
    vec4 smokeTex = texture(Sampler1, texCoord);

    float trail = clamp(smokeTex.a, 0.0, 1.0);
    float mask = sampleMask(texCoord);
    float edge = edgeMask(texCoord);

    if (trail <= 0.015 && edge <= 0.005) {
        discard;
    }

    float intensityC = clamp(intensity, 0.0, 1.5);
    float softness = clamp(handSoftness, 0.4, 2.5);
    float blurSetting = clamp(handBlur, 0.2, 3.0);
    float smokeSetting = clamp(smoke, 0.0, 0.8);
    float activityC = clamp(activity, 0.0, 1.0);

    float softMix = clamp((softness - 0.4) / 2.1, 0.0, 1.0);
    float blurMix = clamp((blurSetting - 0.2) / 2.8, 0.0, 1.0);
    float bloom = pow(trail, mix(0.78, 0.48, softMix));
    float aura = smoothstep(0.015, mix(0.22, 0.38, blurMix), trail);
    float smokeAlpha = clamp(max(bloom * 0.65, aura * 0.40), 0.0, 0.85);
    smokeAlpha *= (0.85 + intensityC * 0.45 + smokeSetting * 0.35 + activityC * 0.12 + softMix * 0.14);
    smokeAlpha = clamp(smokeAlpha, 0.0, 0.88);
    smokeAlpha *= 1.0 - mask * 0.15;
    smokeAlpha *= topEdgeFade;

    float totalAlpha = clamp(smokeAlpha + edge * bloom * (0.25 + intensityC * 0.20) * topEdgeFade, 0.0, 0.95);

    if (totalAlpha <= 0.01) {
        discard;
    }

    vec3 smokeColor = (length(smokeTex.rgb) > 0.05) ? smokeTex.rgb : glowColor.rgb;
    smokeColor = max(smokeColor, glowColor.rgb * 0.85);
    smokeColor = clamp(smokeColor * (1.20 + smokeSetting * 0.25), 0.0, 1.0);

    OutColor = vec4(smokeColor, totalAlpha);
}
