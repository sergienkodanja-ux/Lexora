#version 150 compatibility

uniform float GameTime;
uniform vec4 OverlayColor;

in vec3 localPos;
out vec4 fragColor;

// 🔥 INTEL-SAFE HASH 🔥
// Больше никаких огромных чисел и синусов, ломающих текстуру!
float hash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

// Плавный 3D шум на основе безопасного хэша
float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);

    // Плавная интерполяция
    f = f * f * (3.0 - 2.0 * f);

    return mix(mix(mix(hash(i + vec3(0,0,0)), hash(i + vec3(1,0,0)), f.x),
                   mix(hash(i + vec3(0,1,0)), hash(i + vec3(1,1,0)), f.x), f.y),
               mix(mix(hash(i + vec3(0,0,1)), hash(i + vec3(1,0,1)), f.x),
                   mix(hash(i + vec3(0,1,1)), hash(i + vec3(1,1,1)), f.x), f.y), f.z);
}

// Фрактальное движение облаков
float fbm(vec3 p) {
    float f = 0.0;
    f += 0.5000 * noise(p); p *= 2.01;
    f += 0.2500 * noise(p); p *= 2.02;
    f += 0.1250 * noise(p);
    return f;
}

void main() {
    // Ограничиваем время, чтобы оно не росло бесконечно
    float t = mod(GameTime, 1000.0) * 0.4;

    // Масштаб облаков
    vec3 p = localPos * 3.0;

    // Генерируем два слоя облаков для эффекта объема
    float q = fbm(p - vec3(0.0, t, t * 0.5));
    float n = fbm(p + vec3(q * 2.0) + vec3(t, 0.0, 0.0));

    // Настраиваем контраст (делаем облака сочными)
    float intensity = smoothstep(0.2, 0.8, n);

    // Добавляем красивый плотный фон
    vec3 bg = mix(vec3(0.04), OverlayColor.rgb, 0.2);
    vec3 color = bg + OverlayColor.rgb * intensity * 1.5;

    // Выводим пиксель с плотностью 0.95
    fragColor = vec4(color, 0.95);
}