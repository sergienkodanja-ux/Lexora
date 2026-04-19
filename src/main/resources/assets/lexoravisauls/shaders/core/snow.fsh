#version 150

in vec2 uv;
out vec4 outColor;

uniform sampler2D BeforeTexture;
uniform sampler2D AfterTexture;
uniform float time;
uniform vec2 resolution;
uniform vec3 customColor1;
uniform vec3 customColor2;
uniform float effectAlpha;

mat2 rotate2D(float r) {
    float c = cos(r);
    float s = sin(r);
    return mat2(c, s, -s, c);
}

float getDiff(vec2 coord) {
    vec4 b = texture(BeforeTexture, coord);
    vec4 a = texture(AfterTexture, coord);

    vec3 rgbDiff = abs(a.rgb - b.rgb);
    float maxRgb = max(max(rgbDiff.r, rgbDiff.g), rgbDiff.b);

    float lumA = dot(a.rgb, vec3(0.299, 0.587, 0.114));
    float lumB = dot(b.rgb, vec3(0.299, 0.587, 0.114));
    float lumDiff = abs(lumA - lumB);

    float alphaDiff = abs(a.a - b.a);

    return max(maxRgb, max(lumDiff * 1.35, alphaDiff * 2.0));
}

void main() {
    vec4 beforeColor = texture(BeforeTexture, uv);
    vec4 afterColor = texture(AfterTexture, uv);

    vec2 px = 1.0 / resolution;

    float center = getDiff(uv);
    float around = 0.0;

    around = max(around, getDiff(uv + vec2( px.x,  0.0)));
    around = max(around, getDiff(uv + vec2(-px.x,  0.0)));
    around = max(around, getDiff(uv + vec2( 0.0,  px.y)));
    around = max(around, getDiff(uv + vec2( 0.0, -px.y)));
    around = max(around, getDiff(uv + vec2( px.x,  px.y)));
    around = max(around, getDiff(uv + vec2(-px.x,  px.y)));
    around = max(around, getDiff(uv + vec2( px.x, -px.y)));
    around = max(around, getDiff(uv + vec2(-px.x, -px.y)));

    float diff = max(center, around * 0.9);
    float mask = smoothstep(0.0015, 0.016, diff);

    if (mask < 0.002) {
        discard;
    }

    vec2 fragCoord = uv * resolution;
    vec2 p = (fragCoord - 0.5 * resolution) / resolution.y;

    vec2 n = vec2(0.0);
    vec2 q = vec2(0.0);

    p.x += time / 16.0;

    float S = 12.0;
    float a = 0.0;
    mat2 m = rotate2D(5.0);

    for (int i = 0; i < 20; i++) {
        float j = float(i);

        p *= rotate2D(5.0);
        n *= m;
        q = p * S + time + j + n;

        a += sin(q.x) / S;
        n -= cos(q);

        S *= 1.2;
    }

    float intensity = a + 0.2;

    vec3 baseCol = mix(customColor1, customColor2, clamp(intensity * 0.35 + 0.5, 0.0, 1.0));
    vec3 plasma = baseCol * intensity * 2.4 + a * 0.6;

    plasma += vec3(1.0, 0.5, 0.15) * pow(max(intensity, 0.0), 2.0) * 0.25;

    plasma = max(plasma, vec3(0.0));

    vec4 effectColor = vec4(plasma, 1.0);
    vec4 finalColor = mix(afterColor, effectColor, clamp(mask * effectAlpha, 0.0, 1.0));

    outColor = vec4(finalColor.rgb, afterColor.a);
}