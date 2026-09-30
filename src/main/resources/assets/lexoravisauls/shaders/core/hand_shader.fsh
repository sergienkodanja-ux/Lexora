#version 150

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D BeforeTexture;
uniform sampler2D AfterTexture;
uniform float time;
uniform vec2 screenSize;
uniform vec4 baseColor;
uniform float alpha;
uniform int shaderMode;     // 0=nebula, 1=stars, 2=web, 3=plasma, 4=fire, 5=smoke, 6=snow, 7=stripes, 8=solid
uniform int shaderOnlyMode; // 0=hand+effect, 1=effect only

#define NUM_OCTAVES 5

// ==================== Common functions ====================

float random(vec2 pos) {
    return fract(sin(dot(pos.xy, vec2(12.9898, 78.233))) * 43758.5453123);
}

float noise(vec2 pos) {
    vec2 i = floor(pos);
    vec2 f = fract(pos);
    float a = random(i);
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float fbm(vec2 pos, float t) {
    float v = 0.0;
    float a = 0.5;
    vec2 shift = vec2(100.0);
    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
    for (int i = 0; i < NUM_OCTAVES; i++) {
        float dir = mod(float(i), 2.0) > 0.5 ? 1.0 : -1.0;
        v += a * noise(pos + dir * t * 0.3);
        pos = rot * pos * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

// ==================== Mode 0: Nebula ====================

vec3 nebulaEffect(vec2 uv, float t) {
    vec2 p = (uv * 2.0 - 1.0) * 3.0;

    vec2 q = vec2(fbm(p, t * 0.8), fbm(p + vec2(1.0), t * 0.6));
    vec2 r = vec2(fbm(p + q + vec2(1.7, 1.2), t * 0.7), fbm(p + q + vec2(8.3, 2.8), t * 0.9));
    float f = fbm(p + r, t);

    vec3 color1 = baseColor.rgb * 1.4;
    vec3 color2 = baseColor.rgb * 0.5;
    vec3 color3 = vec3(baseColor.r * 0.4, baseColor.g * 0.3, baseColor.b * 1.4);

    vec3 color = mix(color1, color2, clamp(f * f * 4.0, 0.0, 1.0));
    color = mix(color, color1, clamp(length(q), 0.0, 1.0));
    color = mix(color, color3, clamp(length(r.x), 0.0, 1.0));
    color = (f * f * f + 0.6 * f * f + 0.5 * f) * color;

    return clamp(color * 1.8, 0.0, 1.0);
}

// ==================== Mode 1: Starfield ====================

#define iterations 14
#define formuparam2 0.79
#define volsteps 5
#define stepsize 0.390
#define zoom 0.900
#define tile 0.850
#define brightness 0.003
#define darkmatter 0.400
#define distfading 0.560
#define saturation 0.800
#define transverseSpeed 1.8
#define cloud 0.11

float field(in vec3 p, float t) {
    float strength = 7.0 + 0.03 * log(1.e-6 + fract(sin(t) * 4373.11));
    float accum = 0.0;
    float prev = 0.0;
    float tw = 0.0;

    float mag = dot(p, p);
    p = abs(p) / mag + vec3(-0.5, -0.8 + 0.1 * sin(t * 0.7 + 2.0), -1.1 + 0.3 * cos(t * 0.3));
    float w = exp(0.0);
    accum += w * exp(-strength * pow(abs(mag - prev), 2.3));
    tw += w;
    prev = mag;

    return max(0.0, 5.0 * accum / tw - 0.7);
}

vec3 starfieldEffect(vec2 screenUV, float t) {
    vec2 uvs = screenUV * 2.0 - 1.0;

    float time2 = t;
    float speed = 0.01 * cos(time2 * 0.02 + 3.1415926 / 4.0);
    float formuparam = formuparam2;

    vec2 uv = uvs;

    float a_xz = 0.9;
    float a_yz = -0.6;
    float a_xy = 0.9 + t * 0.08;

    mat2 rot_xz = mat2(cos(a_xz), sin(a_xz), -sin(a_xz), cos(a_xz));
    mat2 rot_yz = mat2(cos(a_yz), sin(a_yz), -sin(a_yz), cos(a_yz));
    mat2 rot_xy = mat2(cos(a_xy), sin(a_xy), -sin(a_xy), cos(a_xy));

    vec3 dir = vec3(uv * zoom, 1.0);
    vec3 from = vec3(0.0, 0.0, 0.0);

    vec3 forward = vec3(0.0, 0.0, 1.0);
    from.x += transverseSpeed * cos(0.01 * t) + 0.001 * t;
    from.y += transverseSpeed * sin(0.01 * t) + 0.001 * t;
    from.z += 0.003 * t;

    dir.xy *= rot_xy;
    forward.xy *= rot_xy;
    dir.xz *= rot_xz;
    forward.xz *= rot_xz;
    dir.yz *= rot_yz;
    forward.yz *= rot_yz;

    from.xy *= -rot_xy;
    from.xz *= rot_xz;
    from.yz *= rot_yz;

    float zooom = (time2 - 3311.0) * speed;
    from += forward * zooom;
    float sampleShift = mod(zooom, stepsize);
    float zoffset = -sampleShift;
    sampleShift /= stepsize;

    float s = 0.24;
    float s3 = s + stepsize / 2.0;
    vec3 v = vec3(0.0);
    float t3 = 0.0;

    vec3 backCol2 = vec3(0.0);
    for (int r = 0; r < volsteps; r++) {
        vec3 p2 = from + (s + zoffset) * dir;
        vec3 p3 = from + (s3 + zoffset) * dir;

        p2 = abs(vec3(tile) - mod(p2, vec3(tile * 2.0)));
        p3 = abs(vec3(tile) - mod(p3, vec3(tile * 2.0)));
        t3 = field(p3, t);

        float pa = 0.0, a = 0.0;
        for (int i = 0; i < iterations; i++) {
            p2 = abs(p2) / dot(p2, p2) - formuparam;
            float D = abs(length(p2) - pa);
            a += i > 7 ? min(12.0, D) : D;
            pa = length(p2);
        }

        a *= a * a;
        float s1 = s + zoffset;
        float fade = pow(distfading, max(0.0, float(r) - sampleShift));

        v += fade;

        if (r == 0)
            fade *= (1.0 - sampleShift);
        if (r == volsteps - 1)
            fade *= sampleShift;

        v += vec3(s1, s1 * s1, s1 * s1 * s1 * s1) * a * brightness * fade;
        backCol2 += mix(0.11, 1.0, 1.0) * vec3(1.8 * t3 * t3 * t3, 1.4 * t3 * t3, t3) * fade;

        s += stepsize;
        s3 += stepsize;
    }

    v = mix(vec3(length(v)), v, saturation);

    vec4 forCol2 = vec4(v * 0.01, 1.0);
    backCol2 *= cloud;
    backCol2.b *= 1.8;
    backCol2.r *= 0.05;

    backCol2.b = 0.5 * mix(backCol2.g, backCol2.b, 0.8);
    backCol2.g = 0.0;
    backCol2.bg = mix(backCol2.gb, backCol2.bg, 0.5 * (cos(t * 0.01) + 1.0));

    vec4 result = forCol2 + vec4(backCol2, 1.0);
    result.rgb = mix(result.rgb, result.rgb * baseColor.rgb * 2.0, 0.5);

    return result.rgb;
}

// ==================== Mode 2: Cobweb ====================

const vec3 eps = vec3(0.001, 0.0, 0.0);
const int iter = 64;
const float sq = 0.70710678;

float cobwebC(vec3 p) {
    vec3 q = abs(mod(p + vec3(cos(p.z * 0.5), cos(p.x * 0.5), cos(p.y * 0.5)), 2.0) - 1.0);
    float a = q.x + q.y + q.z - min(min(q.x, q.y), q.z) - max(max(q.x, q.y), q.z);
    q = vec3(p.x + p.y, p.y + p.z, p.z + p.x) * sq;
    q = abs(mod(q, 2.0) - 1.0);
    float b = q.x + q.y + q.z - min(min(q.x, q.y), q.z) - max(max(q.x, q.y), q.z);
    return min(a, b);
}

vec3 cobwebN(vec3 p) {
    float o = cobwebC(p);
    return normalize(o - vec3(cobwebC(p - eps), cobwebC(p - eps.zxy), cobwebC(p - eps.yzx)));
}

vec3 cobwebEffect(vec2 screenUV, float t) {
    float aspect = screenSize.x / screenSize.y;
    vec2 p = screenUV * 2.0 - 1.0;
    vec2 m = vec2(sin(t * 0.3) * 0.3, cos(t * 0.2) * 0.3);
    p.x *= aspect;
    m.x *= aspect;

    vec3 o = vec3(0.0, 0.0, t * 0.5);
    vec3 s = vec3(m, 0.0);
    vec3 b = vec3(0.0, 0.0, 0.0);
    vec3 d = vec3(p, 1.0) / 32.0;
    vec3 col = vec3(0.5);
    vec3 a;

    for (int i = 0; i < iter; ++i) {
        float h = cobwebC(b + s + o);
        b += h * 10.0 * d;
        col += h;
    }
    col /= float(iter);
    a = cobwebN(b + s + o);
    float x = dot(a, col);

    vec3 diffuse = baseColor.rgb;
    col = (col + pow(x, 4.0)) * (1.0 - col * 0.01) * diffuse;
    col *= b.z * 0.125;

    vec2 vig = p * 0.43;
    vig.y *= aspect;
    float vig_amount = 1.0 - length(vig) * 0.5;

    return col * 3.0 * vig_amount;
}

// ==================== Mode 3: Plasma ====================

vec3 plasmaEffect(vec2 uv, float t) {
    vec2 p = uv * 4.0;

    float v = sin(p.x + t);
    v += sin(p.y + t * 0.5);
    v += sin((p.x + p.y + t) * 0.5);
    v += sin(length(p) + t);
    v = v * 0.5;

    vec3 color = baseColor.rgb * (0.5 + 0.5 * sin(v * 3.14159));
    color += baseColor.rgb * 0.3 * (0.5 + 0.5 * cos(v * 3.14159 + 2.0));

    return clamp(color, 0.0, 1.0);
}

// ==================== Mode 4: Fire ====================

vec3 fireEffect(vec2 screenUV, float t) {
    float aspect = screenSize.x / screenSize.y;
    vec2 p = screenUV * 2.0 - 1.0;
    p.x *= aspect;

    vec2 flow = p * 2.8;
    vec2 drift = vec2(sin(t * 0.8) * 0.25, -t * 1.6);

    float f1 = fbm(flow + drift, t * 0.6);
    float f2 = fbm(flow * 2.2 + drift * 1.4 + vec2(4.1, 7.3), t * 0.9);
    float flame = pow(f1 * 0.6 + f2 * 0.4, 1.8) * 3.0;

    vec3 hotCore = vec3(1.0, 0.98, 0.7);
    vec3 fireBody = baseColor.rgb * 1.8;
    vec3 fireSmoke = baseColor.rgb * 0.3;

    vec3 col = mix(fireSmoke, fireBody, clamp(flame, 0.0, 1.0));
    col = mix(col, hotCore, clamp(flame * flame - 0.6, 0.0, 1.0));
    return clamp(col, 0.0, 1.0);
}

// ==================== Mode 5: Smoke ====================

vec3 smokeEffect(vec2 screenUV, float t) {
    float aspect = screenSize.x / screenSize.y;
    vec2 smokeUv = screenUV * 2.0 - 1.0;
    smokeUv.x *= aspect;

    for (float i = 1.0; i < 7.0; i += 1.0) {
        smokeUv.x += 0.5 / i * cos(i * 2.5 * smokeUv.y + t);
        smokeUv.y += 0.5 / i * cos(i * 1.5 * smokeUv.x + t);
    }

    float smokePattern = 0.12 / max(0.002, abs(sin(t - smokeUv.y - smokeUv.x)));
    vec3 col = baseColor.rgb * smokePattern * 2.0;
    return clamp(col, 0.0, 1.0);
}

// ==================== Mode 6: Snow ====================

vec3 snowEffect(vec2 screenUV, float t) {
    float aspect = screenSize.x / screenSize.y;
    vec2 p = screenUV;
    p.x *= aspect;
    vec3 col = vec3(0.0);
    for (int i = 1; i <= 5; i++) {
        float fi = float(i);
        vec2 pos = p * (fi * 4.0 + 5.0);
        pos.y += t * (0.7 + fi * 0.3);
        pos.x += sin(pos.y * 0.5 + t * (0.8 + fi * 0.2)) * 0.3;
        vec2 id = floor(pos);
        vec2 f = fract(pos) - 0.5;
        float r = random(id);
        if (r > 0.35) {
            float d = length(f - (vec2(r, fract(r * 19.1)) - 0.5) * 0.65);
            float flake = smoothstep(0.12 / fi, 0.0, d) * (0.6 + 0.4 * sin(t * 3.5 + r * 6.28));
            col += vec3(flake) * (0.7 + 0.5 * baseColor.rgb);
        }
    }
    return clamp(col * 2.2, 0.0, 1.0);
}

// ==================== Mode 7: Stripes ====================

vec3 stripesEffect(vec2 screenUV, float t) {
    float aspect = screenSize.x / screenSize.y;
    vec2 p = screenUV * 2.0 - 1.0;
    p.x *= aspect;
    float stripe = sin(p.x * 14.0 - p.y * 10.0 + t * 4.0) * 0.5 + 0.5;
    stripe = pow(stripe, 3.5);
    float stripe2 = sin(p.x * -7.0 + p.y * 12.0 - t * 2.5) * 0.5 + 0.5;
    stripe2 = pow(stripe2, 4.0);
    float glow = stripe * 0.7 + stripe2 * 0.5;
    vec3 col = mix(baseColor.rgb * 0.2, baseColor.rgb * 1.8 + vec3(0.2), glow);
    return clamp(col, 0.0, 1.0);
}

// ==================== Mode 8: Solid ====================

vec3 solidEffect(vec2 screenUV, float t) {
    vec2 p = (screenUV * 2.0 - 1.0) * 2.0;
    float f = fbm(p + vec2(t * 0.2, -t * 0.15), t * 0.3);
    float glow = 0.6 + 0.4 * sin(t * 2.0 + length(p) * 2.0);
    vec3 col = baseColor.rgb * (0.7 + 0.6 * f) * glow;
    return clamp(col * 1.5, 0.0, 1.0);
}

// ==================== Main ====================

float getDiff(vec2 coord) {
    vec4 b = texture(BeforeTexture, coord);
    vec4 a = texture(AfterTexture, coord);

    vec3 rgbDiff = abs(a.rgb - b.rgb);
    float maxRgb = max(max(rgbDiff.r, rgbDiff.g), rgbDiff.b);

    float lumA = dot(a.rgb, vec3(0.299, 0.587, 0.114));
    float lumB = dot(b.rgb, vec3(0.299, 0.587, 0.114));
    float lumDiff = abs(lumA - lumB);

    return max(maxRgb, lumDiff * 1.35);
}

void main(void) {
    vec2 px = 1.0 / screenSize;
    float center = getDiff(texCoord);
    float around = 0.0;
    around = max(around, getDiff(texCoord + vec2( px.x,  0.0)));
    around = max(around, getDiff(texCoord + vec2(-px.x,  0.0)));
    around = max(around, getDiff(texCoord + vec2( 0.0,  px.y)));
    around = max(around, getDiff(texCoord + vec2( 0.0, -px.y)));
    around = max(around, getDiff(texCoord + vec2( px.x,  px.y)));
    around = max(around, getDiff(texCoord + vec2(-px.x,  px.y)));
    around = max(around, getDiff(texCoord + vec2( px.x, -px.y)));
    around = max(around, getDiff(texCoord + vec2(-px.x, -px.y)));

    float diff = max(center, around * 0.90);
    float mask = smoothstep(0.002, 0.018, diff);

    if (mask < 0.005) {
        discard;
    }

    vec2 screenUV = texCoord;

    vec3 effectColor;

    if (shaderMode == 1) {
        effectColor = starfieldEffect(screenUV, time);
    } else if (shaderMode == 2) {
        effectColor = cobwebEffect(screenUV, time);
    } else if (shaderMode == 3) {
        effectColor = plasmaEffect(screenUV, time);
    } else if (shaderMode == 4) {
        effectColor = fireEffect(screenUV, time);
    } else if (shaderMode == 5) {
        effectColor = smokeEffect(screenUV, time);
    } else if (shaderMode == 6) {
        effectColor = snowEffect(screenUV, time);
    } else if (shaderMode == 7) {
        effectColor = stripesEffect(screenUV, time);
    } else if (shaderMode == 8) {
        effectColor = solidEffect(screenUV, time);
    } else {
        effectColor = nebulaEffect(screenUV, time);
    }

    vec4 handColor = texture(AfterTexture, texCoord);
    float glowFactor = clamp(alpha, 0.0, 1.0);

    if (shaderOnlyMode == 1) {
        vec4 beforeColor = texture(BeforeTexture, texCoord);
        vec3 col = mix(beforeColor.rgb, effectColor, mask * glowFactor);
        fragColor = vec4(col, 1.0);
    } else {
        vec3 blended = mix(handColor.rgb, effectColor, glowFactor * 0.55);
        vec3 finalCol = blended + effectColor * (glowFactor * 0.45);
        vec3 result = mix(handColor.rgb, finalCol, mask);
        fragColor = vec4(result, 1.0);
    }
}
