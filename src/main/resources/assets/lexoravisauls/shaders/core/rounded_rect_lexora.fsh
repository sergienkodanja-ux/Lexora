#version 150

uniform vec4 Rect;
uniform float Radius;
uniform vec4 FillColor;
uniform float Softness;
uniform float OutlineWidth;
uniform int UseGradient;
uniform int UseTexture;
uniform vec4 ColorTL;
uniform vec4 ColorTR;
uniform vec4 ColorBL;
uniform vec4 ColorBR;
uniform vec2 TexMin;
uniform vec2 TexMax;
uniform sampler2D Sampler0;

out vec4 fragColor;

float sdRoundedBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

void main() {
    vec2 center = Rect.xy + Rect.zw * 0.5;
    vec2 halfSize = Rect.zw * 0.5;
    float dist = sdRoundedBox(gl_FragCoord.xy - center, halfSize, Radius);

    float aa = max(fwidth(dist) * Softness, 0.75);
    float alpha;
    if (OutlineWidth > 0.0) {
        float innerDist = dist + OutlineWidth;
        alpha = (1.0 - smoothstep(0.0, aa, dist)) * smoothstep(-aa, 0.0, innerDist);
    } else {
        alpha = 1.0 - smoothstep(0.0, aa, dist);
    }

    if (alpha <= 0.0) {
        discard;
    }

    vec4 baseCol;
    if (UseGradient != 0) {
        vec2 uv = clamp((gl_FragCoord.xy - Rect.xy) / Rect.zw, 0.0, 1.0);
        vec4 top = mix(ColorBL, ColorBR, uv.x);
        vec4 bot = mix(ColorTL, ColorTR, uv.x);
        baseCol = mix(top, bot, uv.y);
    } else {
        baseCol = FillColor;
    }

    if (UseTexture != 0) {
        vec2 normUv = clamp((gl_FragCoord.xy - Rect.xy) / Rect.zw, 0.0, 1.0);
        vec2 texUv = mix(TexMin, vec2(TexMax.x, TexMin.y), normUv.x);
        texUv = mix(vec2(texUv.x, TexMax.y), texUv, normUv.y);
        vec4 texCol = texture(Sampler0, texUv);
        baseCol *= texCol;
    }

    fragColor = vec4(baseCol.rgb, baseCol.a * alpha);
}
