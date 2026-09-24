#version 150

in vec2 fragCoord;

uniform float uTime;
uniform float uProgress;
uniform float uAlpha;
uniform vec4 uColor1; // Electric Cyan
uniform vec4 uColor2; // Deep Ultra-Violet

out vec4 fragColor;

void main() {
    vec2 uv = fragCoord * 2.0 - 1.0;
    float r = length(uv);
    if (r > 1.0) {
        discard;
    }

    float angle = atan(uv.y, uv.x);

    // Dynamic collapse near the end
    float collapse = smoothstep(0.60, 0.92, uProgress);
    float rHorizon = mix(0.32, 0.04, collapse);

    // 1. Accretion Swirl & Doppler Beaming
    float swirl = angle * 2.0 - 10.0 * r + uTime * 5.0;
    float spiralArms = pow(sin(swirl) * 0.5 + 0.5, 1.8);
    float doppler = 0.5 + 0.5 * sin(angle + 0.4);

    // 2. Einstein Photon Ring: Razor-thin blinding white-hot circle
    float photonRing = exp(-abs(r - rHorizon * 1.05) * 55.0) * 3.2;

    // 3. Gravitational Lensing Halo (smooth exponential falloff)
    float lensingHalo = exp(-pow(abs(r - rHorizon * 1.25) * 2.8, 1.4) * 2.0) * 0.85;
    float outerCorona = smoothstep(0.98, 0.45, r) * smoothstep(rHorizon, rHorizon * 1.4, r) * 0.5;

    // 4. Color Palette
    vec3 col = mix(uColor2.rgb, uColor1.rgb, smoothstep(0.85, 0.35, r));
    col = mix(col, vec3(0.92, 0.98, 1.0), doppler * 0.4);
    col += vec3(0.95, 0.99, 1.0) * photonRing;
    col += uColor1.rgb * (spiralArms * 0.5);

    // 5. Overall Alpha
    float edgeFade = smoothstep(1.0, 0.75, r);
    float alpha = (lensingHalo + outerCorona + spiralArms * 0.35 + photonRing) * edgeFade * uAlpha;

    // 6. Pitch-Black Absolute Event Horizon Core
    if (r < rHorizon) {
        float shadow = smoothstep(0.0, rHorizon * 0.88, r);
        col = vec3(0.005, 0.001, 0.01) * shadow;
        alpha = uAlpha * (1.0 - shadow * 0.15);
    }

    fragColor = vec4(col, clamp(alpha, 0.0, 1.0));
}
