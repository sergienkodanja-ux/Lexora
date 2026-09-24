#version 150

uniform sampler2D Sampler0; // размытая (bloom) текстура силуэта
uniform sampler2D Sampler1; // чёткая маска силуэта
uniform vec3 color;   // горячий цвет (низ пламени)
uniform vec3 color2;  // холодный цвет (верх пламени)
uniform float time;
uniform float height; // высота подъёма пламени (в uv)
uniform float speed;
uniform float intensity;
uniform float windStrength; // сила ветра
uniform float waveStrength; // сила волн
uniform vec2 camOffset; // смещение пламени по скорости движения камеры

in vec2 TexCoord;
out vec4 OutColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));

    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 5; i++) {
        value += noise(p) * amplitude;
        p = p * 2.02 + vec2(8.4, 5.7);
        amplitude *= 0.5;
    }
    return value;
}

float ridged(vec2 p) {
    float value = 0.0;
    float amplitude = 0.55;
    for (int i = 0; i < 4; i++) {
        float r = 1.0 - abs(noise(p) * 2.0 - 1.0);
        value += r * amplitude;
        p = p * 2.18 + vec2(3.1, 9.2);
        amplitude *= 0.52;
    }
    return value;
}

void main() {
    vec2 uv = TexCoord;

    float t = time * max(speed, 0.001);
    vec2 flow = uv * 2.5;
    vec2 drift = vec2(t * 0.35, -t * 0.25);

    // Создаём эффект ветра, как в "Красивом" режиме ShaderHand
    vec2 warp = vec2(
        fbm(flow * 0.85 + drift * 0.85 + vec2(0.0, 4.1)),
        fbm(flow * 0.72 - drift * 0.55 + vec2(3.7, 1.8))
    );
    vec2 q = flow + (warp - 0.5) * 2.2;

    float mist = fbm(q * 0.75 - drift * 0.28 + vec2(4.2, 8.1));
    float veins = ridged(q * 1.75 + vec2(mist * 2.8, mist * 1.8) - drift * 0.58);
    veins = pow(clamp(veins, 0.0, 1.0), 2.4);

    float stripeA = 1.0 - abs(sin((q.x * 1.05 + q.y * 0.4) * 1.8 + time * 0.9 + mist * 4.5));
    float stripeB = 1.0 - abs(sin((q.x * -0.55 + q.y * 1.1) * 1.5 - time * 0.7 - mist * 3.0));
    stripeA = pow(clamp(stripeA, 0.0, 1.0), 5.0);
    stripeB = pow(clamp(stripeB, 0.0, 1.0), 5.5);

    float energy = clamp(mist * 0.2 + veins * 0.9 + stripeA * 0.6 + stripeB * 0.35, 0.0, 1.0);
    float core = smoothstep(0.15, 0.95, energy);

    // Основной ветер и порывы с учетом настроек
    float wind = (sin(time * 1.8) * 0.8 + sin(time * 0.9 + 1.5) * 0.6) * windStrength;
    float gust = (fbm(vec2(uv.x * 3.5, uv.y * 3.5 - time * speed * 1.2)) - 0.5) * windStrength;

    // Плюм с волновыми эффектами
    float plume = 0.0;
    const int STEPS = 20;
    for (int i = 1; i <= STEPS; i++) {
        float d = float(i) / float(STEPS);
        
        float wave1 = sin((d * 8.0 + time * 1.5)) * 0.03 * waveStrength;
        float wave2 = sin((d * 12.0 + time * 2.0 + 2.5)) * 0.02 * waveStrength;
        float wave3 = (warp.x - 0.5) * 0.04 * d * waveStrength;
        
        float below = uv.y - d * height + camOffset.y * d;
        float x = uv.x - (wind * 0.08 + gust * 0.07 + wave1 + wave2 + wave3) * d + camOffset.x * d;
        
        float m = texture(Sampler0, vec2(x, below)).a;
        plume = max(plume, m * (1.0 - d));
    }

    float fire = plume;
    
    float self = texture(Sampler1, uv).a;
    fire *= (1.0 - self);

    // Модуляция с живыми эффектами
    fire *= (0.4 + core * 0.6 + veins * 0.3 * waveStrength);
    fire *= smoothstep(0.05, 0.7, energy);
    fire = clamp(fire * intensity, 0.0, 1.0);
    fire = pow(fire, 0.75);
    if (fire <= 0.01) discard;

    vec3 col = mix(color2, color, fire * (0.7 + core * 0.3));
    OutColor = vec4(col, fire);
}
