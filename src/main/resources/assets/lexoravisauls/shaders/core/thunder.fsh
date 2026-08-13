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

// New custom lightning settings
uniform float uThunderInterval;
uniform float uThunderChance;
uniform float uThunderGlow;

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

// Highly optimized fast hash function using GPU hardware dot & sin
float hash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453123);
}

float hash(float n) {
    return fract(sin(n) * 43758.5453123);
}

// Vectorized 3D noise for maximum GPU throughput
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

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution.xy;
    vec2 sp = uv * 2.0 - 1.0;
    float aspect = uResolution.x / uResolution.y;

    float tanV = tan(radians(uFov) * 0.5);
    vec3 rayV = normalize(vec3(sp.x * tanV * aspect, sp.y * tanV, 1.0));
    vec3 rayW = rotY(uCameraDir.x) * rotX(uCameraDir.y) * rayV;

    // 1. Storm clouds overhead
    float cloudDensity = 0.0;
    if (rayW.y > -0.15) {
        // FBM 3D noise for cloud density, scrolled over time
        vec3 p = rayW * uScale;
        p.x += uTime * uSpeed * 0.08;
        p.z += uTime * uSpeed * 0.04;
        
        cloudDensity = fbm(p);
        // Fade clouds near and below horizon
        cloudDensity *= smoothstep(-0.15, 0.3, rayW.y);
    }

    // 2. Procedural lightning generator
    float interval = max(1.0, uThunderInterval); // lightning triggers every interval seconds
    float timeIndex = floor(uTime / interval);
    float timeOffset = fract(uTime / interval);
    
    // Decisions based on time index
    float strikeHash = hash(timeIndex * 12.34);
    bool hasStrike = strikeHash < uThunderChance; // Custom chance of strike
    
    float flash = 0.0;
    float boltGlow = 0.0;
    
    if (hasStrike) {
        // Random horizontal angle for the lightning bolt
        float strikeAngle = hash(timeIndex * 45.67) * 6.28318;
        
        // Define bolt start (high sky) and end (near horizon)
        vec3 boltStart = normalize(vec3(cos(strikeAngle), 1.0, sin(strikeAngle)));
        vec3 boltEnd = normalize(vec3(cos(strikeAngle + (hash(timeIndex * 8.3) - 0.5) * 0.2), -0.2, sin(strikeAngle + (hash(timeIndex * 8.3) - 0.5) * 0.2)));
        
        float t = timeOffset;
        if (t < 0.55) {
            // Electrical flicker overlay (high frequency hum)
            float flicker = 0.8 + 0.2 * sin(uTime * 95.0) * cos(uTime * 135.0);

            // Realistic double-flash lighting pattern
            flash = (exp(-t * 22.0) * 1.5 + exp(-abs(t - 0.15) * 25.0) * 1.0 + exp(-abs(t - 0.35) * 15.0) * 0.4) * flicker;
            
            // Build the lightning bolt
            float boltIntensity = max(exp(-t * 18.0), exp(-abs(t - 0.15) * 22.0));
            if (boltIntensity > 0.02) {
                vec3 segment = boltEnd - boltStart;
                float segLen = length(segment);
                vec3 segDir = segment / segLen;
                
                // Project ray onto the segment
                float h = dot(rayW - boltStart, segDir);
                h = clamp(h, 0.0, segLen);
                vec3 projection = boltStart + segDir * h;
                
                // Generate detailed jagged offsets using multiple octaves of sine waves
                float noiseSeed = timeIndex * 73.19;
                float jagged = sin(h * 30.0 + noiseSeed) * 0.035;
                jagged += cos(h * 70.0 - noiseSeed) * 0.015;
                jagged += sin(h * 150.0 + noiseSeed * 1.5) * 0.007;
                jagged += cos(h * 350.0 - noiseSeed * 3.0) * 0.003; // extra high frequency details
                
                vec3 offsetProj = projection + vec3(jagged, 0.0, jagged * 0.6);
                
                // Distance from camera ray to the jagged bolt
                float distToBolt = length(rayW - normalize(offsetProj));
                
                // Hot white core + soft blue/purple glow
                boltGlow = (exp(-distToBolt * 280.0) * 2.5 + exp(-distToBolt * 30.0) * 0.6) * boltIntensity * flicker;
                
                // Branch 1 (going right)
                if (hash(timeIndex * 29.5) > 0.3 && h > segLen * 0.2) {
                    vec3 bStart = projection;
                    vec3 bEnd = normalize(boltEnd + vec3(sin(strikeAngle + 0.8), -0.15, cos(strikeAngle + 0.8)) * 0.4);
                    vec3 bSeg = bEnd - bStart;
                    float bLen = length(bSeg);
                    if (bLen > 0.0) {
                        vec3 bDir = bSeg / bLen;
                        float bh = dot(rayW - bStart, bDir);
                        bh = clamp(bh, 0.0, bLen);
                        vec3 bProj = bStart + bDir * bh;
                        float bJagged = sin(bh * 45.0 + noiseSeed) * 0.015 + cos(bh * 110.0) * 0.006;
                        vec3 bOffsetProj = bProj + vec3(bJagged, 0.0, bJagged);
                        float distToBranch = length(rayW - normalize(bOffsetProj));
                        
                        boltGlow += (exp(-distToBranch * 220.0) * 1.2 + exp(-distToBranch * 30.0) * 0.3) * boltIntensity * (1.0 - bh/bLen) * flicker;
                    }
                }

                // Branch 2 (going left, from a different position)
                if (hash(timeIndex * 83.2) > 0.5 && h > segLen * 0.4) {
                    vec3 bStart = projection;
                    vec3 bEnd = normalize(boltEnd + vec3(sin(strikeAngle - 0.8), -0.2, cos(strikeAngle - 0.8)) * 0.45);
                    vec3 bSeg = bEnd - bStart;
                    float bLen = length(bSeg);
                    if (bLen > 0.0) {
                        vec3 bDir = bSeg / bLen;
                        float bh = dot(rayW - bStart, bDir);
                        bh = clamp(bh, 0.0, bLen);
                        vec3 bProj = bStart + bDir * bh;
                        float bJagged = sin(bh * 50.0 - noiseSeed) * 0.015 + cos(bh * 120.0) * 0.006;
                        vec3 bOffsetProj = bProj + vec3(bJagged, 0.0, bJagged);
                        float distToBranch = length(rayW - normalize(bOffsetProj));
                        
                        boltGlow += (exp(-distToBranch * 220.0) * 1.2 + exp(-distToBranch * 30.0) * 0.3) * boltIntensity * (1.0 - bh/bLen) * flicker;
                    }
                }
            }
        }
    }

    // 3. Compose Colors
    // Theme color input is blended with a dark storm color scheme
    vec3 stormBase = mix(vec3(0.005, 0.005, 0.015), uColor * 0.05, 0.5);
    
    // Background sky flashes scaled by custom glow multiplier
    vec3 flashColor = vec3(0.75, 0.82, 1.0) * flash * uThunderGlow;
    vec3 finalSky = stormBase + flashColor * 0.25;
    
    // Ambient cloud lighting from within scaled by custom glow multiplier
    vec3 cloudBaseColor = mix(vec3(0.01, 0.01, 0.02), vec3(0.06, 0.07, 0.12), cloudDensity);
    vec3 cloudLitColor = mix(vec3(0.02, 0.025, 0.05), vec3(0.8, 0.85, 1.0), cloudDensity * flash * uThunderGlow * 0.7);
    
    vec3 finalCloud = mix(cloudBaseColor, cloudLitColor, min(flash * uThunderGlow, 1.0));
    
    // Blend background sky and storm clouds
    float cloudMix = smoothstep(0.18, 0.48, cloudDensity);
    vec3 finalColor = mix(finalSky, finalCloud, cloudMix);
    
    // Layer the bright lightning bolt on top scaled by custom glow multiplier
    finalColor += vec3(0.85, 0.92, 1.0) * boltGlow * uThunderGlow;

    // Apply global intensity adjustment and alpha transparency
    finalColor *= (1.0 + uIntensity * 10.0);
    
    fragColor = vec4(finalColor, uAlpha);
}
