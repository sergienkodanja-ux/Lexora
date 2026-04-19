#version 150

uniform vec4 Rect;
uniform float Radius;
uniform vec4 FillColor;
uniform float Softness;

out vec4 fragColor;

float sdRoundRect(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    vec2 center = Rect.xy + Rect.zw * 0.5;
    vec2 halfSize = Rect.zw * 0.5;
    vec2 p = gl_FragCoord.xy - center;

    float dist = sdRoundRect(p, halfSize, Radius);
    float aa = max(fwidth(dist) * Softness, 1.0);
    float alpha = 1.0 - smoothstep(0.0, aa, dist);

    fragColor = vec4(FillColor.rgb, FillColor.a * alpha);
}