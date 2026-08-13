#version 150

in vec3 vRayDir;
out vec4 fragColor;

uniform float GameTime;
uniform float PlasmaSpeed;
uniform float PlasmaIntensity;
uniform vec3  ThemeColor;
uniform float UseTheme;

// Матрица вращения для идеального 3D-шума без швов
mat3 m3 = mat3(
    0.36,  0.48, -0.80,
   -0.80,  0.60,  0.00,
    0.48,  0.64,  0.60
);

void main() {
    // Чистый 3D-вектор. Гарантирует идеальную сферу.
    vec3 rd = normalize(vRayDir);
    float t = GameTime * PlasmaSpeed * 0.4;

    vec3 p = rd * 3.0;
    float flow = 0.0;
    float amp = 1.0;

    // Генерация мягких 3D-облаков (перетекающая жидкость)
    for(int i = 0; i < 5; i++) {
        p += t * 0.5;
        flow += amp * abs(sin(p.x) * cos(p.y) + sin(p.z));
        p = m3 * p * 1.3;
        amp *= 0.6;
    }

    // Сглаживание и контраст, чтобы убрать резкие пиксели
    flow = smoothstep(0.5, 2.5, flow);

    vec3 col;
    if (UseTheme > 0.5) {
        vec3 bg = ThemeColor * 0.05;
        col = mix(bg, ThemeColor, flow * 1.5);
    } else {
        // Глубокий космос: Фиолетовый фон -> Розовая туманность -> Бирюзовые блики
        vec3 bg = vec3(0.05, 0.01, 0.1);
        vec3 c1 = vec3(0.6, 0.1, 0.4);
        vec3 c2 = vec3(0.1, 0.7, 0.8);

        col = mix(bg, c1, flow);
        col = mix(col, c2, smoothstep(0.6, 1.0, flow));
    }

    fragColor = vec4(col * PlasmaIntensity, 1.0);
}