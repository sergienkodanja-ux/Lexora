#version 150

uniform vec4 Rect;
uniform float Radius;
uniform vec4 FillColor;
uniform float Softness;

uniform vec4 ColorTL;
uniform vec4 ColorTR;
uniform vec4 ColorBL;
uniform vec4 ColorBR;
uniform int UseGradient;

uniform sampler2D Sampler0;
uniform int UseTexture;
uniform vec2 TexMin;
uniform vec2 TexMax;

out vec4 fragColor;

float sdRoundRect(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    vec2 center  = Rect.xy + Rect.zw * 0.5;
    vec2 halfSize = Rect.zw * 0.5;
    vec2 p = gl_FragCoord.xy - center;

    float dist  = sdRoundRect(p, halfSize, Radius);
    float aa    = max(fwidth(dist) * Softness, 1.0);
    float alpha = 1.0 - smoothstep(0.0, aa, dist);

    vec4 color;

    if (UseTexture == 1) {
        // UV внутри прямоугольника
        vec2 uv = (p + halfSize) / Rect.zw;
        // Переводим в диапазон TexMin..TexMax (регион скина)
        // gl_FragCoord.y идёт снизу вверх, поэтому flipY
        vec2 texUv = mix(TexMin, TexMax, vec2(uv.x, 1.0 - uv.y));
        color = texture(Sampler0, texUv) * FillColor;
    } else if (UseGradient == 1) {
        vec2 uv     = (p + halfSize) / Rect.zw;
        vec4 top    = mix(ColorTL, ColorTR, uv.x);
        vec4 bottom = mix(ColorBL, ColorBR, uv.x);
        color       = mix(bottom, top, uv.y);
    } else {
        color = FillColor;
    }

    fragColor = vec4(color.rgb, color.a * alpha);
}