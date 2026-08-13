#version 150

in vec3 vRayDir;
out vec4 fragColor;

uniform float GameTime;
uniform float AuroraSpeed;
uniform float AuroraIntensity;
uniform float AuroraStars;
uniform float AuroraReflect;
uniform vec2  iResolution;

#define time (GameTime)

mat2 mm2(in float a) { float c=cos(a),s=sin(a); return mat2(c,s,-s,c); }
mat2 m2 = mat2(0.95534,0.29552,-0.29552,0.95534);

float tri(in float x) { return clamp(abs(fract(x)-.5),0.01,0.49); }
vec2 tri2(in vec2 p)  { return vec2(tri(p.x)+tri(p.y), tri(p.y+tri(p.x))); }

float triNoise2d(in vec2 p, float spd) {
    float z=1.8, z2=2.5, rz=0.;
    p *= mm2(p.x*0.06);
    vec2 bp = p;
    for (float i=0.; i<5.; i++) {
        vec2 dg = tri2(bp*1.85)*.75;
        dg *= mm2(time*spd);
        p -= dg/z2;
        bp *= 1.3; z2 *= .45; z *= .42;
        p *= 1.21+(rz-1.0)*.02;
        rz += tri(p.x+tri(p.y))*z;
        p *= -m2;
    }
    return clamp(1./pow(rz*29.,1.3),0.,.55);
}

float hash21(in vec2 n) { return fract(sin(dot(n,vec2(12.9898,4.1414)))*43758.5453); }

vec4 aurora(vec3 ro, vec3 rd) {
    vec4 col=vec4(0), avgCol=vec4(0);
    for (float i=0.; i<50.; i++) {
        float of = 0.006*hash21(vRayDir.xz*100.)*smoothstep(0.,15.,i);
        float pt = ((.8+pow(i,1.4)*.002)-ro.y)/(rd.y*2.+0.4);
        pt -= of;
        vec3 bpos = ro+pt*rd;
        vec2 p = bpos.zx;
        float rzt = triNoise2d(p, AuroraSpeed);
        vec4 col2 = vec4(0,0,0,rzt);
        col2.rgb = (sin(1.-vec3(2.15,-.5,1.2)+i*0.043)*0.5+0.5)*rzt;
        avgCol = mix(avgCol, col2, .5);
        col += avgCol*exp2(-i*0.065-2.5)*smoothstep(0.,5.,i);
    }
    col *= clamp(rd.y*15.+.4, 0., 1.);
    return col * AuroraIntensity;
}

vec3 nmzHash33(vec3 q) {
    uvec3 p = uvec3(ivec3(q));
    p = p*uvec3(374761393U,1103515245U,668265263U)+p.zxy+p.yzx;
    p = p.yzx*(p.zxy^(p>>3U));
    return vec3(p^(p>>16U))*(1./vec3(0xffffffffU));
}
vec3 stars(in vec3 p) {
    vec3 c = vec3(0.);
    float res = iResolution.x;
    for (float i=0.; i<4.; i++) {
        vec3 q  = fract(p*(.15*res))-.5;
        vec3 id = floor(p*(.15*res));
        vec2 rn = nmzHash33(id).xy;
        float c2 = 1.-smoothstep(0.,.6,length(q));
        c2 *= step(rn.x,.0005+i*i*0.001);
        c += c2*(mix(vec3(1.,0.49,0.1),vec3(0.75,0.9,1.),rn.y)*0.1+0.9);
        p *= 1.3;
    }
    return c*c*.8*AuroraStars;
}
vec3 bg(in vec3 rd) {
    float sd = dot(normalize(vec3(-.5,-.6,.9)),rd)*0.5+0.5;
    sd = pow(sd,5.);
    return mix(vec3(0.05,0.1,0.2),vec3(0.1,0.05,0.2),sd)*.63;
}

void main() {
    vec3 rd = normalize(vRayDir);
    vec3 ro = vec3(0,0,-6.7);
    vec3 col = vec3(0.);

    col = bg(rd);

    // Симметричный луч для авроры, чтобы она была и сверху, и снизу
    vec3 rrd = vec3(rd.x, abs(rd.y), rd.z);

    vec4 aur = smoothstep(0., 1.5, aurora(ro, rrd));
    col += stars(rd);

    // Если отражение выключено, снизу рисуем только звезды
    if (AuroraReflect > 0.5 || rd.y > 0.0) {
        col = col*(1.-aur.a) + aur.rgb;
    }

    fragColor = vec4(col, 1.0);
}