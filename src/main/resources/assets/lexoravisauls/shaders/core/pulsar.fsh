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

// Optimized fast hash function
float hash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453123);
}

float hash(float n) {
    return fract(sin(n) * 43758.5453123);
}

// Vectorized 3D noise for FBM nebulae
float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    
    float n000 = hash(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash(i + vec3(1.0, 1.0, 1.0));

    vec4 a = mix(vec4(n000, n010, n001, n011),
                 vec4(n100, n110, n101, n111), f.x);
    vec2 b = mix(a.xz, a.yw, f.y);
    return mix(b.x, b.y, f.z);
}

float fbm(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    vec3 shift = vec3(100.0);
    for (int i = 0; i < 4; ++i) {
        v += a * noise(p);
        p = p * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

// Highly realistic twinkling star field with varied star colors
vec3 starField(vec3 rd) {
    // Reduced density (130.0 grid) and higher threshold (0.992) for sharper background stars
    vec3 gridPos = floor(rd * 130.0);
    float starHash = hash(gridPos);
    
    vec3 starColor = vec3(0.0);
    if (starHash > 0.992) {
        vec3 starOffset = vec3(hash(gridPos + 1.1), hash(gridPos + 2.2), hash(gridPos + 3.3)) - 0.5;
        vec3 starPos = (gridPos + 0.5 + starOffset * 0.85) / 130.0;
        
        float d = length(rd - normalize(starPos));
        
        // Twinkling effect
        float twinkle = 0.55 + 0.45 * sin(uTime * 3.5 + starHash * 120.0);
        float starIntensity = exp(-d * 600.0) * twinkle * (0.3 + 0.7 * hash(gridPos + 5.5));
        
        // Varied color temperatures for stars
        vec3 col = vec3(1.0);
        float colorHash = hash(gridPos + 7.7);
        if (colorHash < 0.22) {
            col = vec3(0.65, 0.8, 1.0); // Hot blue star
        } else if (colorHash < 0.38) {
            col = vec3(1.0, 0.82, 0.6); // Warm orange star
        } else if (colorHash < 0.43) {
            col = vec3(1.0, 0.55, 0.55); // Cool red star
        }
        
        starColor = col * starIntensity;
    }
    return starColor;
}

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution.xy;
    vec2 sp = uv * 2.0 - 1.0;
    float aspect = uResolution.x / uResolution.y;

    float tanV = tan(radians(uFov) * 0.5);
    vec3 rayV = normalize(vec3(sp.x * tanV * aspect, sp.y * tanV, 1.0));
    vec3 rayW = rotY(uCameraDir.x) * rotX(uCameraDir.y) * rayV;

    // 1. Cosmic background with stars and detailed dust lane nebulae
    float n1 = fbm(rayW * uScale * 0.4 + vec3(uTime * 0.003, 0.0, 0.0));
    float n2 = fbm(rayW * uScale * 0.9 - vec3(0.0, uTime * 0.002, 0.0));
    
    // Glowing active gas clouds
    vec3 gasColor = mix(vec3(0.001, 0.003, 0.012), vec3(0.035, 0.01, 0.055), n1);
    gasColor = mix(gasColor, vec3(0.008, 0.025, 0.035), n2 * n1);
    
    // Dark silhouette dust lanes blocking the gas
    float dust = smoothstep(0.3, 0.7, fbm(rayW * uScale * 1.2 + vec3(0.1)));
    vec3 skyBackground = mix(gasColor, vec3(0.0005, 0.0005, 0.0015), dust * 0.85);
    
    vec3 col = skyBackground + starField(rayW);

    // 2. Precessing Pulsar math
    vec3 pulsarCenter = normalize(vec3(0.55, 0.65, -0.45));
    
    // Only render pulsar on the front-facing hemisphere
    float frontMask = smoothstep(0.0, 0.4, dot(rayW, pulsarCenter));
    
    if (frontMask > 0.0) {
        // Orthogonal tangents relative to the pulsar direction
        vec3 tangent1 = normalize(cross(pulsarCenter, vec3(0.0, 1.0, 0.0)));
        vec3 tangent2 = cross(tangent1, pulsarCenter);
        
        // Local 2D coordinates centered on the pulsar core
        vec2 p2d = vec2(dot(rayW, tangent1), dot(rayW, tangent2));
        float dCore = length(p2d);
        
        // Core (Neutron star) - Pulsating size
        float pulse = 0.95 + 0.05 * sin(uTime * uSpeed * 8.0);
        float coreSize = 0.058 * pulse; // Make core large and visible
        float core = smoothstep(coreSize, 0.0, dCore);
        
        // Detailed solar corona/flares on the halo
        float flare = fbm(rayW * 12.0 + vec3(0.0, uTime * 0.25, 0.0));
        float halo = exp(-dCore * 16.0) * 3.2 + exp(-dCore * 3.5) * 0.9 * (0.7 + 0.3 * flare);

        // 3. Wobbling vertical plasma jets (shakes back and forth)
        // Instead of rotating 360 degrees, it oscillates left & right (shakes) around vertical axis (0, 1)
        float wobbleAngle = sin(uTime * uSpeed * 6.0) * 0.22; // Wobble range ~12.5 degrees
        vec2 jetDir2d = vec2(sin(wobbleAngle), cos(wobbleAngle));
        
        float distAlong = dot(p2d, jetDir2d);
        
        // Outward propagating plasma displacement waves
        float wave = sin(abs(distAlong) * 75.0 - uTime * uSpeed * 32.0) * 0.0055 * abs(distAlong);
        float distToJet = length(p2d - distAlong * jetDir2d) - wave;
        
        // Collimated core jet + soft glowing outer shell, fading along the distance
        float jetIntensity = (exp(-distToJet * 320.0) * 3.0 + exp(-distToJet * 38.0) * 0.8);
        jetIntensity *= exp(-abs(distAlong) * 0.85); // Fade slightly as it goes away from the star
        
        // High frequency electrical flicker/sparkle of the jets
        float jetFlicker = 0.82 + 0.18 * sin(uTime * 115.0) * cos(uTime * 145.0);

        // 4. Combine Pulsar Layers
        vec3 coreColor = vec3(1.0, 1.0, 1.0);
        vec3 haloColor = vec3(0.18, 0.45, 1.0) * uColor;
        vec3 jetColor = vec3(0.35, 0.68, 1.0) * uColor;

        vec3 pulsarCol = mix(vec3(0.0), coreColor, core);
        pulsarCol += haloColor * halo;
        pulsarCol += jetColor * jetIntensity * jetFlicker;
        
        // Apply front mask to ensure smooth fade at horizon/boundaries
        col += pulsarCol * frontMask;
    }

    // Apply global intensity adjustment and alpha transparency
    col *= (1.0 + uIntensity * 10.0);
    
    fragColor = vec4(col, uAlpha);
}
