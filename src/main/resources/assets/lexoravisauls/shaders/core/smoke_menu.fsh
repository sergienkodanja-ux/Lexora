#version 150

in vec2 fragCoord;

uniform float uTime;
uniform vec2 uResolution;
uniform vec2 uMouse;
uniform float uAlpha;

out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
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

mat2 rot2D(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat2(c, -s, s, c);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 r = rot2D(0.37);
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = r * p * 2.02 + vec2(0.1, 0.2);
        a *= 0.5;
    }
    return v;
}

void main() {
    vec2 res = max(uResolution.xy, vec2(1.0));
    vec2 uv = fragCoord.xy / res;
    vec2 p = (fragCoord.xy * 2.0 - res) / min(res.x, res.y);

    vec2 mouseNorm = (uMouse.xy * 2.0 - res) / min(res.x, res.y);
    float mouseDist = length(p - mouseNorm);
    p += (p - mouseNorm) * (0.08 / (mouseDist * mouseDist + 0.3));

    p *= 1.3;

    float t = uTime * 0.12;
    vec2 q = vec2(fbm(p + vec2(0.0, t * 0.4)),
                  fbm(p + vec2(5.2, 1.3) + vec2(t * 0.3, -t * 0.2)));

    vec2 r = vec2(fbm(p + 3.0 * q + vec2(1.7, 9.2) + vec2(t * 0.25, t * 0.35)),
                  fbm(p + 3.0 * q + vec2(8.3, 2.8) - vec2(t * 0.3, t * 0.2)));

    float f = fbm(p + 2.8 * r);

    vec3 colBg        = vec3(0.035, 0.035, 0.048);
    vec3 colSmoke1    = vec3(0.09, 0.08, 0.13);
    vec3 colSmoke2    = vec3(0.14, 0.16, 0.24);
    vec3 colHighlight = vec3(0.28, 0.24, 0.42);

    vec3 col = mix(colBg, colSmoke1, clamp(f * f * 2.0, 0.0, 1.0));
    col = mix(col, colSmoke2, clamp(length(q), 0.0, 1.0) * 0.7);
    col = mix(col, colHighlight, clamp(length(r), 0.0, 1.0) * f * 0.8);

    vec2 particleCoord = p * 4.0 + vec2(0.0, uTime * 0.2);
    float spark = pow(noise(particleCoord), 14.0) * 0.4;
    col += vec3(0.4, 0.35, 0.6) * spark;

    float vignette = 1.0 - length(uv - 0.5) * 0.75;
    col *= clamp(vignette, 0.2, 1.0);

    fragColor = vec4(col, uAlpha);
}
