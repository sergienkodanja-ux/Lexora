#version 150

in vec2 texCoord;
in vec4 vertexColor;
in float vDist;

out vec4 fragColor;

uniform float uTime;
uniform float uAlpha;
uniform vec3 uColor;

void main() {
    vec2 p = texCoord * 2.0 - 1.0;
    float distSq = dot(p, p);
    if (distSq > 1.0) {
        discard;
    }

    float d = sqrt(distSq);

    // Gorgeous soft snowflake core with feathered bokeh falloff
    float core = smoothstep(1.0, 0.08, d);
    float glow = exp(-d * 2.8) * 0.35;
    float shape = clamp(core + glow, 0.0, 1.0);

    // Subtle crystalline shimmer/twinkle
    float twinkle = 0.85 + 0.15 * sin(uTime * 4.5 + vertexColor.a * 31.4159);

    // Near camera clipping fade (smoothly dissolves if snowflake touches near camera lens)
    float nearFade = smoothstep(0.20, 0.70, vDist);
    // Far boundary fade (smoothly dissolves near max render distance)
    float farFade = smoothstep(42.0, 32.0, vDist);

    float a = shape * vertexColor.a * uAlpha * twinkle * nearFade * farFade;
    if (a <= 0.005) {
        discard;
    }

    vec3 col = mix(uColor, vec3(1.0), 0.70) * vertexColor.rgb;
    fragColor = vec4(col, a);
}
