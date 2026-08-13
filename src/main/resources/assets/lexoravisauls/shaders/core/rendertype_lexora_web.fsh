// ─────────────────────────────────────────────────────────────────────────────
//  Water Turbulence — адаптировано из ShaderToy (David Hoskins / joltz0r)
//  Оригинальный тяжёлый snoise + fbm заменён на итеративный water-алгоритм.
//  Результат: в ~3-4× меньше операций, нет mat-операций, нет permute().
// ─────────────────────────────────────────────────────────────────────────────
#version 150

uniform float GameTime;     // секунды (тики / 20) — стандарт MC
uniform vec4  OverlayColor; // цветовой тинт поверхности

in  vec3 localPos;
out vec4 fragColor;

// Количество итераций воды.
// 5 — баланс качество/скорость; уменьши до 4 для слабых GPU.
#define MAX_ITER 5
#define TAU      6.28318530718

void main() {

    // ── Время ─────────────────────────────────────────────────────────────
    // GameTime в MC — секунды. Оригинальный ShaderToy: iTime * 0.5 + 23.0
    float t = GameTime * 0.5 + 23.0;

    // ── UV из локального положения ────────────────────────────────────────
    // Используем XZ-плоскость как поверхность воды (горизонталь).
    // fract() обеспечивает повтор тайла без артефактов на стыках.
    vec2 uv = fract(localPos.xz * 0.4 + 0.5);

    // ── Алгоритм Water Turbulence ─────────────────────────────────────────
    vec2 p      = mod(uv * TAU, TAU) - 250.0;
    vec2 iter   = p;
    float c     = 1.0;
    const float inten = 0.005;

    for (int n = 0; n < MAX_ITER; n++) {
        float nt = t * (1.0 - 3.5 / float(n + 1));
        iter = p + vec2(
            cos(nt - iter.x) + sin(nt + iter.y),
            sin(nt - iter.y) + cos(nt + iter.x)
        );
        // Яркость нити: обратная длина вектора
        c += 1.0 / length(vec2(
            p.x / (sin(iter.x + nt) / inten),
            p.y / (cos(iter.y + nt) / inten)
        ));
    }

    // ── Постобработка ─────────────────────────────────────────────────────
    c /= float(MAX_ITER);
    c  = 1.17 - pow(c, 1.4);

    // Базовый цвет воды (как в оригинале: синеватый)
    vec3 water = vec3(pow(abs(c), 8.0));
    water = clamp(water + vec3(0.0, 0.35, 0.5), 0.0, 1.0);

    // Тинт через OverlayColor: 0.0 = чистая вода, 1.0 = только оверлей
    // Используем mix чтобы сохранить анимацию но учесть туман/цвет блока
    vec3 finalColor = mix(water, water * OverlayColor.rgb * 2.0, 0.28);

    // Альфа: берём из OverlayColor (стандартное поведение MC-шейдера)
    // Минимум 0.85 чтобы поверхность не была полупрозрачной в ноль
    float alpha = max(OverlayColor.a, 0.85);

    fragColor = vec4(clamp(finalColor, 0.0, 1.0), alpha);
}
