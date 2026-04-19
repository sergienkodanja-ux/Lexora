#version 150 compatibility

uniform float GameTime;
uniform vec4 OverlayColor;

in vec2 texCoord;
in vec3 localPos;

out vec4 fragColor;

// 🔥 БЕЗОПАСНЫЙ ХЭШ ДЛЯ INTEL (Без полос) 🔥
float hash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

// Плавный 3D-шум на основе безопасного хэша
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

// Полноценные фрактальные облака
float fbm(vec3 p) {
    float f = 0.0;
    float amp = 0.5;
    // 4 октавы для красоты и хорошего FPS
    for(int i = 0; i < 4; i++) {
        f += amp * noise(p);
        p *= 2.01;
        amp *= 0.5;
    }
    return f;
}

void main() {
    // 1. Координаты для 3D обволакивания блока
    vec3 pos = localPos * 8.0;

    // Плавная анимация
    float time = GameTime * 0.18;

    // 2. Движение облаков
    pos.y += time;
    pos.z -= time * 0.5;

    // 3. 🔥 ГЕНЕРАЦИЯ ОБЛАКОВ ПЛАЗМЫ 🔥
    // Применяем искажение пространства (Domain Warping), чтобы облака выглядели как дым
    float n = fbm(pos + fbm(pos + vec3(time * 0.2)));

    // Настраиваем мягкость облаков
    float intensity = smoothstep(0.1, 0.9, n) + 0.1;

    // 4. 🔥 ЦВЕТ И ФОН 🔥
    // Красим облака в твой цвет из меню (яркость 1.5)
    vec3 cloudColor = OverlayColor.rgb * intensity * 1.5;

    // Делаем приятный темный фон блока (смесь почти черного и твоего цвета)
    vec3 bg = mix(vec3(0.04), OverlayColor.rgb, 0.2);

    vec3 finalColor = bg + cloudColor;

    // Задаем плотность (0.95), чтобы скрыть ванильную текстуру блока
    fragColor = vec4(finalColor, 0.95);
}