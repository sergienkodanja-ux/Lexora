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

#define FAR 52.0

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

mat2 r2(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat2(c, s, -s, c);
}

float smax(float a, float b, float s) {
    float h = clamp(0.5 + 0.5 * (a - b) / s, 0.0, 1.0);
    return mix(b, a, h) + h * (1.0 - h) * s;
}

vec3 hash33(vec3 p) {
    float n = sin(dot(p, vec3(7.0, 157.0, 113.0)));
    return fract(vec3(2097152.0, 262144.0, 32768.0) * n);
}

float n3D(vec3 p) {
    const vec3 s = vec3(7.0, 157.0, 113.0);

    vec3 ip = floor(p);
    vec4 h = vec4(0.0, s.y, s.z, s.y + s.z) + dot(ip, s);

    p -= ip;
    p = p * p * (3.0 - 2.0 * p);

    h = mix(fract(sin(h) * 43758.5453), fract(sin(h + s.x) * 43758.5453), p.x);
    h.xy = mix(h.xz, h.yw, p.y);

    return mix(h.x, h.y, p.z);
}

vec2 path(float z) {
    return vec2(sin(z * 0.075) * 8.0, cos(z * 0.1) * 1.5);
}

float mapScene(vec3 p) {
    vec3 t = vec3(1.0, 0.5, 0.25) * time;

    float mainLayer =
        n3D(p * vec3(0.4, 1.0, 0.4)) * 0.66 +
        n3D(p * vec3(0.4, 1.0, 0.4) * 1.6) * 0.34;

    float detailLayer =
        n3D(p * 3.0 + t) * 0.57 +
        n3D(p * 6.015 + t * 2.0) * 0.28 +
        n3D(p * 12.01 + t * 4.0) * 0.15;

    float clouds = mainLayer * 0.84 + detailLayer * 0.16;

    p.xy -= path(p.z);

    return smax(
        clouds,
        -length(p.xy * vec2(1.0 / 32.0, 1.0)) + 1.1 + (clouds - 0.5),
        0.5
    ) + (clouds - 0.5);
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

    float diffMask = max(center, around * 0.9);
    float mask = smoothstep(0.0015, 0.016, diffMask);

    if (mask < 0.002) {
        discard;
    }

    vec2 suv = (uv * resolution - resolution * 0.5) / resolution.y;

    vec3 ro = vec3(0.0, 0.0, time * 4.0);
    vec3 lk = ro + vec3(0.0, 0.0, 0.25);

    ro.xy += path(ro.z);
    lk.xy += path(lk.z);

    float FOV = 3.14159 / 2.75;

    vec3 forward = normalize(lk - ro);
    vec3 right = normalize(vec3(forward.z, 0.0, -forward.x));
    vec3 up = cross(forward, right);

    vec3 rd = normalize(forward + FOV * suv.x * right + FOV * suv.y * up);

    vec2 sw = path(lk.z);
    rd.xy *= r2(-sw.x / 24.0);
    rd.yz *= r2(-sw.y / 16.0);

    vec3 rnd = hash33(rd.yzx + fract(time));

    float d = 1.0;
    float d2 = 0.0;
    float t = dot(rnd, vec3(0.333333));
    float td = 0.0;
    float w = 0.0;

    const float h = 0.5;

    vec3 col = vec3(0.0);
    vec3 sp;

    vec3 ld = normalize(vec3(-0.2, 0.3, 0.8));

    vec3 skyBase = mix(customColor2, customColor1, clamp(rd.y * 0.5 + 0.5, 0.0, 1.0));
    vec3 sky = mix(vec3(1.0, 1.0, 0.9), skyBase, 0.75);

    vec3 fakeLd = normalize(vec3(-0.2, 0.3, 1.2));
    float sun = clamp(dot(fakeLd, rd), 0.0, 1.0);

    sky += vec3(1.0, 0.3, 0.05) * pow(sun, 5.0) * 0.25;
    sky += vec3(1.0, 0.4, 0.05) * pow(sun, 8.0) * 0.35;
    sky += vec3(1.0, 0.9, 0.7) * pow(sun, 128.0) * 0.5;
    sky *= sqrt(max(sky, 0.0));

    vec3 cloudCol = mix(sky, mix(customColor1, vec3(1.0, 0.9, 0.8), 0.5), 0.66);

    for (int i = 0; i < 48; i++) {
        sp = ro + rd * t;
        d = mapScene(sp);

        if (d < 0.001 * (1.0 + t * 0.125) || td > 1.0 || t > FAR) {
            break;
        }

        w = d < h ? (1.0 - td) * (h - d) : 0.0;
        td += w + 1.0 / 64.0;

        d2 = mapScene(sp + ld * 0.2);
        float diff = max(d2 - d, 0.0) / 0.2;

        col += w * max(d * d * (1.0 - d2) * 3.0 - 0.05, 0.0) * (diff * cloudCol * 6.0 + sky) * 2.5;
        col *= 0.98 + fract(rnd * 289.0 + t * 41.13) * 0.04;

        d = max(-d + 2.0, 0.0);
        d = 1.0 - d * d / 3.0;
        t += clamp(d, 0.05, 0.5);
    }

    col = clamp(col, 0.0, 1.0);
    col = col + sky * (1.0 - col) * 0.1;
    col = mix(col, col + sky * (1.0 - col), smoothstep(0.0, 0.85, t / FAR));
    col = mix(col, col + sky * sky * 2.0 * (1.0 - col), 1.0 - 1.0 / (1.0 + t * t * 0.001));

    vec3 fCol = mix(pow(min(vec3(1.5, 1.0, 1.0).zyx * col, 1.0), vec3(1.0, 3.0, 16.0)), sky, 0.5);
    col = mix(fCol, col, dot(cos(rd * 6.0 + sin(rd.yzx * 6.0)), vec3(0.333333)) * 0.1 + 0.9);

    vec2 vignetteUV = uv;
    col = mix(
        pow(min(vec3(1.5, 1.0, 1.0).zyx * col, 1.0), vec3(1.0, 3.0, 16.0)),
        col,
        pow(16.0 * vignetteUV.x * vignetteUV.y * (1.0 - vignetteUV.x) * (1.0 - vignetteUV.y), 0.125) * 0.5 + 0.5
    );

    vec4 effectColor = vec4(pow(max(col, vec3(0.0)), vec3(1.0 / 2.2)), 1.0);
    vec4 finalColor = mix(afterColor, effectColor, clamp(mask * effectAlpha, 0.0, 1.0));

    outColor = vec4(finalColor.rgb, afterColor.a);
}