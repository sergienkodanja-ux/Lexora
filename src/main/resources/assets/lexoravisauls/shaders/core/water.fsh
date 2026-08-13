#version 150

uniform float uTime;
uniform vec2 uResolution;
uniform vec3 uColor;
uniform float uAlpha;
uniform float uSpeed;
uniform float uScale;
uniform float uIntensity;
uniform vec2 uCameraDir;
uniform float uFov;

out vec4 fragColor;

#define MAX_ITER 4

mat3 rotX(float a) {
    float c = cos(a), s = sin(a);
    return mat3(1.0, 0.0, 0.0,
                0.0,   c,   s,
                0.0,  -s,   c);
}
mat3 rotY(float a) {
    float c = cos(a), s = sin(a);
    return mat3(  c, 0.0,   s,
                0.0, 1.0, 0.0,
                 -s, 0.0,   c);
}

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution.xy;
    vec2 sp = uv * 2.0 - 1.0;
    float aspect = uResolution.x / uResolution.y;

    float tanV = tan(radians(uFov) * 0.5);
    vec3 rayV = normalize(vec3(sp.x * tanV * aspect, sp.y * tanV, 1.0));
    vec3 rayW = rotY(uCameraDir.x) * rotX(uCameraDir.y) * rayV;

    // 3D noise from the world ray direction – seamless on the full sphere.
    vec3 p = rayW * uScale;
    vec3 i = p;
    float c = 1.0;

    // Precompute constant multiplication outside the loop to save divisions
    vec3 p_inten = p * uIntensity;

    for (int n = 0; n < MAX_ITER; n++) {
        float t = uTime * uSpeed * (11.0 - (3.0 / float(n + 1)));
        i = p + vec3(
            cos(t - i.x) + sin(t + i.y),
            sin(t - i.y) + cos(t + i.z),
            cos(t - i.z) + sin(t + i.x)
        );
        
        // Fast vector math using GPU vector units
        vec3 sinCosVal = vec3(sin(i.x + t), cos(i.y + t), sin(i.z + t));
        c += 1.0 / length(p_inten / sinCosVal);
    }

    c /= float(MAX_ITER);
    c = 1.5 - sqrt(c);
    float brightness = c * c * c * c;

    vec3 color = uColor * brightness + uColor * 0.15;

    fragColor = vec4(color, uAlpha);
}
