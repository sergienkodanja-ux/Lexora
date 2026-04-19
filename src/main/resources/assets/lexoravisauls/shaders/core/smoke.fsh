#version 150

in vec2 uv;
out vec4 outColor;

uniform sampler2D BeforeTexture;
uniform sampler2D AfterTexture;
uniform float time;
uniform vec2 resolution;
uniform vec3 smokeColor;
uniform float smokeIntensity;
uniform float effectAlpha;

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
    float mask = smoothstep(0.002, 0.020, diff);

    if (mask < 0.003) {
        discard;
    }

    vec2 smokeUv = (2.0 * uv - 1.0) * resolution.y / min(resolution.x, resolution.y);

    for (float i = 1.0; i < 10.0; i++) {
        smokeUv.x += 0.6 / i * cos(i * 2.5 * smokeUv.y + time);
        smokeUv.y += 0.6 / i * cos(i * 1.5 * smokeUv.x + time);
    }

    float smokePattern = 0.1 / max(0.001, abs(sin(time - smokeUv.y - smokeUv.x)));
    vec3 col = smokeColor * smokePattern * smokeIntensity;

    vec4 effectColor = vec4(col, 1.0);
    vec4 finalColor = mix(afterColor, effectColor, clamp(mask * effectAlpha, 0.0, 1.0));

    outColor = vec4(finalColor.rgb, afterColor.a);
}