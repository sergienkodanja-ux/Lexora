#version 150

in vec2 uv;
out vec4 outColor;

uniform sampler2D BeforeTexture;
uniform sampler2D AfterTexture;
uniform float time;
uniform vec2 resolution;
uniform vec3 stripesColor1;
uniform vec3 stripesColor2;
uniform float stripesWidth;
uniform float stripesSpeed;
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

float random1(float x) {
    return fract(
        sin(x * 123.456) * 23.4567 +
        sin(x * 345.678) * 45.6789 +
        sin(x * 456.789) * 56.789
    );
}

vec3 rotateX(vec3 p, float a) {
    float s = sin(a);
    float c = cos(a);
    return vec3(p.x, c * p.y + s * p.z, -s * p.y + c * p.z);
}

vec3 rotateY(vec3 p, float a) {
    float s = sin(a);
    float c = cos(a);
    return vec3(c * p.x + s * p.z, p.y, -s * p.x + c * p.z);
}

vec3 rotateZ(vec3 p, float a) {
    float s = sin(a);
    float c = cos(a);
    return vec3(c * p.x + s * p.y, -s * p.x + c * p.y, p.z);
}

vec3 getParticleColour(
    vec3 particlePos,
    float particleSize,
    float particleLength,
    vec3 rayDir,
    float depthFade,
    float brightness
) {
    vec2 normXY = normalize(rayDir.xy);
    float d1 = dot(particlePos.xy, normXY) / max(length(rayDir.xy), 0.0001);
    vec3 closest2d = rayDir * d1;

    vec3 clampedPos = particlePos;
    clampedPos.z = clamp(closest2d.z, particlePos.z - particleLength, particlePos.z + particleLength);

    float d = dot(clampedPos, rayDir);
    vec3 closestPos = rayDir * d;
    vec3 delta = clampedPos - closestPos;

    float closestDist = length(delta) / particleSize;
    float shade = clamp(1.0 - closestDist, 0.0, 1.0);
    shade *= exp2(-d * depthFade) * brightness;

    return vec3(shade);
}

vec3 getParticlePos(
    vec3 rayDir,
    float zPos,
    float seed,
    float stepsCount,
    float minDist,
    float maxDist,
    float repeatMin,
    float repeatMax
) {
    float angle = atan(rayDir.x, rayDir.y);
    float angleFraction = fract(angle / (3.14159265 * 2.0));

    float segment = floor(angleFraction * stepsCount + seed) + 0.5 - seed;
    float particleAngle = segment / stepsCount * (3.14159265 * 2.0);

    float segmentPos = segment / stepsCount;
    float radius = minDist + random1(segmentPos + seed) * (maxDist - minDist);

    float tunnelZ = rayDir.z / max(length(rayDir.xy / radius), 0.0001);
    tunnelZ += zPos;

    float repeatRate = repeatMin + random1(segmentPos + 0.1 + seed) * (repeatMax - repeatMin);
    float particleZ = (ceil(tunnelZ / repeatRate) - 0.5) * repeatRate - zPos;

    return vec3(sin(particleAngle) * radius, cos(particleAngle) * radius, particleZ);
}

vec3 starfield(
    vec3 rayDir,
    float zPos,
    float seed,
    float stepsCount,
    float minDist,
    float maxDist,
    float repeatMin,
    float repeatMax,
    float particleSize,
    float particleLength,
    float depthFade,
    float brightness
) {
    vec3 particlePos = getParticlePos(
        rayDir, zPos, seed,
        stepsCount, minDist, maxDist,
        repeatMin, repeatMax
    );

    return getParticleColour(
        particlePos,
        particleSize,
        particleLength,
        rayDir,
        depthFade,
        brightness
    );
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
    vec2 screenUV = fragCoord / resolution;
    vec2 screenPos = screenUV * 2.0 - 1.0;
    screenPos.x *= resolution.x / resolution.y;

    vec3 rayDir = normalize(vec3(screenPos, 1.0));

    float speedMul = max(0.35, stripesSpeed * 0.22);
    float sizeMul = max(0.65, stripesWidth * 0.06);

    vec3 euler = vec3(
        0.5 + sin(time * 0.2) * 0.125,
        0.5 + sin(time * 0.1) * 0.125,
        time * 0.1 * speedMul + sin(time * 0.3) * 0.5
    );

    rayDir = rotateX(rayDir, euler.x);
    rayDir = rotateY(rayDir, euler.y);
    rayDir = rotateZ(rayDir, euler.z);

    float a = 0.2;
    float b = 10.0;
    float c = 1.0 * speedMul;
    float zPos = 5.0 + time * c + sin(time * a) * b;
    float speed = c + a * b * cos(a * time);

    float particleLength = 0.25 * speed / 60.0;
    float particleSize = 0.015 * sizeMul;
    float brightness = 2.5;
    float stepsCount = 121.0;
    float minDist = 0.8;
    float maxDist = 5.0;
    float repeatMin = 1.0;
    float repeatMax = 2.0;
    float depthFade = 0.8;

    vec3 bg = mix(
        stripesColor2 * 0.08 + vec3(0.005, 0.0, 0.01),
        stripesColor1 * 0.08 + vec3(0.01, 0.005, 0.0),
        rayDir.y * 0.5 + 0.5
    );

    vec3 result = bg;

    float seed = 0.0;
    for (int i = 0; i < 2; i++) {
        vec3 sCol = starfield(
            rayDir, zPos, seed,
            stepsCount, minDist, maxDist,
            repeatMin, repeatMax,
            particleSize, particleLength,
            depthFade, brightness
        );

        vec3 tint = mix(stripesColor1, stripesColor2, float(i) * 0.5);
        result += sCol * mix(vec3(1.0), tint + vec3(0.35), 0.35);

        seed += 1.234;
    }

    vec4 effectColor = vec4(sqrt(max(result, 0.0)), 1.0);
    vec4 finalColor = mix(afterColor, effectColor, clamp(mask * effectAlpha, 0.0, 1.0));

    outColor = vec4(finalColor.rgb, afterColor.a);
}