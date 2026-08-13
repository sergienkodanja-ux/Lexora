#version 150

in vec3 vRayDir;
in vec2 vUV;
out vec4 fragColor;

uniform float GameTime;
uniform float StarDensity;
uniform float CloudSpeed;
uniform float CloudDensity;
uniform float ShowMoon;
uniform vec2  iResolution;

#define HASHSCALE1 .1031

float hash11(float p) {
    vec3 p3 = fract(vec3(p) * HASHSCALE1);
    p3 += dot(p3, p3.yzx + 19.19);
    return fract((p3.x + p3.y) * p3.z);
}
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * HASHSCALE1);
    p3 += dot(p3, p3.yzx + 19.19);
    return fract((p3.x + p3.y) * p3.z);
}
float smoothNoise13(in vec3 x) {
    vec3 p = floor(x), f = smoothstep(0.0, 1.0, fract(x));
    float n = p.x + p.y * 57.0 + 113.0 * p.z;
    return mix(
        mix(mix(hash11(n),hash11(n+1.),f.x), mix(hash11(n+57.),hash11(n+58.),f.x), f.y),
        mix(mix(hash11(n+113.),hash11(n+114.),f.x), mix(hash11(n+170.),hash11(n+171.),f.x), f.y),
        f.z);
}
mat3 fbmMat = mat3(0.0,1.6,1.2,-1.6,0.72,-0.96,-1.2,-0.96,1.28);
float fbm(vec3 p) {
    float f = 0.5*smoothNoise13(p); p = fbmMat*p*1.2;
    f += 0.25*smoothNoise13(p); p = fbmMat*p*1.3;
    f += 0.1666*smoothNoise13(p); p = fbmMat*p*1.4;
    f += 0.0834*smoothNoise13(p);
    return f;
}
float noisyStar(vec2 p, float thr) {
    float v = hash12(p);
    return v >= thr ? pow((v-thr)/(1.-thr), 6.0) : 0.0;
}
float stableStar(vec2 p, float thr) {
    float fx = fract(p.x), fy = fract(p.y);
    vec2  fp = floor(p);
    return noisyStar(fp,thr)*(1.-fx)*(1.-fy)
          +noisyStar(fp+vec2(0,1),thr)*(1.-fx)*fy
          +noisyStar(fp+vec2(1,0),thr)*fx*(1.-fy)
          +noisyStar(fp+vec2(1,1),thr)*fx*fy;
}

void main() {
    vec3 rd = normalize(vRayDir);

    // Плавный 360-градиент
    vec3 col = vec3(0.02, 0.04, 0.12) * (abs(rd.y) * 0.5 + 0.5);

    // Луна (рендерится везде)
    if (ShowMoon > 0.5) {
        vec3 moonDir = normalize(vec3(0.3, 0.6, 0.7));
        float md = max(0.0, dot(rd, moonDir));
        col += vec3(0.7,0.7,0.7) * pow(md, 350.0);
        col += vec3(0.3,0.35,0.4) * pow(md, 6.0) * 0.25;
    }

    // Звёзды (abs(rd.y) защищает от артефактов полюса)
    vec2 starUV = rd.xz / max(abs(rd.y), 0.001);
    float angle = 0.0005 * GameTime * 60.0 + atan(starUV.y, starUV.x);
    vec2 samplePos = (0.5*length(starUV)*vec2(cos(angle),sin(angle))+0.5) * iResolution.y;
    col += vec3(stableStar(samplePos, StarDensity));

    // Облака
    float t = GameTime * CloudSpeed * 0.1;
    vec3 fbmIn = vec3(rd.x/(abs(rd.y)+0.1)-t, rd.z/(abs(rd.y)+0.1), 0.0);
    col += vec3(0.5,0.5,0.75) * fbm(fbmIn) * CloudDensity;

    fragColor = vec4(col, 1.0);
}