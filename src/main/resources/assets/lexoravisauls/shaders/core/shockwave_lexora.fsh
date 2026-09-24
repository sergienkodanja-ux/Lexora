#version 150

in vec2 fragCoord;

uniform vec2 uCenter;
uniform float uRadius;
uniform float uWaveWidth;
uniform float uProgress;
uniform vec4 uStartColor;
uniform vec4 uEndColor;

out vec4 fragColor;

void main() {
    float dist = distance(fragCoord, uCenter);

    float innerR = max(0.0, uRadius - uWaveWidth);
    float waveFactor = smoothstep(innerR, uRadius, dist);

    vec4 col = mix(uEndColor, uStartColor, waveFactor);

    float waveDist = abs(dist - uRadius);
    float ringGlow = exp(-waveDist * 0.035) * (1.0 - uProgress);
    float frontBloom = exp(-waveDist * 0.10) * (1.0 - uProgress) * 1.2;

    col.rgb += vec3(ringGlow * 0.55 + frontBloom * 0.45);
    col.a = (1.0 - uProgress) * (0.55 + ringGlow * 0.45);

    fragColor = clamp(col, 0.0, 1.0);
}
