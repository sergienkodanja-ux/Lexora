#version 150

in vec2 fragCoord;

uniform float uTime;
uniform float uProgress;
uniform float uAlpha;
uniform vec4 uColor1;
uniform vec4 uColor2;

out vec4 fragColor;

void main() {
    vec2 uv = fragCoord * 2.0 - 1.0;
    float r = length(uv);
    if (r > 1.0) {
        discard;
    }

    float angle = atan(uv.y, uv.x);

    // 1. Concentric Runic Circles
    float ring1 = exp(-abs(r - 0.88) * 45.0);
    float ring2 = exp(-abs(r - 0.72) * 35.0);
    float ring3 = exp(-abs(r - 0.45) * 30.0);

    // 2. Rotating Runic Ticks on perimeter
    float ticks = pow(abs(cos(angle * 12.0 + uTime * 2.0)), 8.0) * smoothstep(0.82, 0.92, r);

    // 3. Inscribed Sacred Octagram (8-pointed star in polar coordinates)
    float aMod = mod(angle - uTime * 1.5, 3.14159265 / 4.0) - (3.14159265 / 8.0);
    float rStar = 0.52 / max(0.2, cos(aMod));
    float starGlow = exp(-abs(r - rStar) * 32.0) * smoothstep(0.65, 0.25, r);

    // 4. Radiating Divine Sunbeams
    float rays = pow(sin(angle * 8.0 + uTime * 2.5) * 0.5 + 0.5, 4.0) * (1.0 - r);

    // 5. Blinding Holy Core
    float core = exp(-r * 4.2) * 2.2;

    // 6. Subtle Edge Border & Cardinal Rune Accents (не сильно много на краях)
    float outerRim = exp(-abs(r - 0.96) * 60.0);
    float edgeNotches = pow(abs(cos(angle * 8.0 + uTime * 0.6)), 12.0) * smoothstep(0.90, 0.98, r) * 0.85;
    float edgeCorona = smoothstep(0.99, 0.94, r) * smoothstep(0.89, 0.96, r) * 0.35;

    // Color synthesis: White celestial center -> Sacred gold -> Amber rim
    vec3 col = mix(uColor1.rgb, uColor2.rgb, r);
    col += vec3(1.0, 0.98, 0.9) * (core + ring1 * 0.7 + starGlow * 0.6 + outerRim * 0.8);
    col += vec3(rays * 0.45 + edgeNotches * 0.6);

    float combinedAlpha = (ring1 + ring2 * 0.7 + ring3 * 0.5 + ticks * 0.8 + starGlow * 0.9 + rays * 0.4 + core + outerRim * 0.9 + edgeNotches + edgeCorona) * uAlpha;

    fragColor = vec4(col, clamp(combinedAlpha, 0.0, 1.0));
}
