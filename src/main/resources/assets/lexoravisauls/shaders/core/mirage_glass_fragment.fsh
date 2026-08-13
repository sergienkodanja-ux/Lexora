#version 150

in vec2 FragPos;

uniform sampler2D Sampler0;
uniform vec4 u_rect;
uniform float u_radius;
uniform float u_blur;
uniform vec4 u_tint;
uniform float u_alpha;

out vec4 fragColor;

// ─── SDF скруглённого прямоугольника ───────────────────────────────────────
float roundedBoxSDF(vec2 centerPos, vec2 halfSize, float radius) {
    return length(max(abs(centerPos) - halfSize + radius, 0.0)) - radius;
}

// ─── Оптимизированный Гаусс-блюр (Не жрет ФПС, нет клонов) ────────
vec4 blur(sampler2D tex, vec2 uv, vec2 texel, float radius) {
    vec4 result = vec4(0.0);
    float totalWeight = 0.0;

    float sigma = max(radius * 0.5, 1.0);

    // Шаг адаптируется под радиус, чтобы покрывать большую площадь без лишних вычислений
    float stepSize = max(1.0, radius / 4.0);

    // Сетка от -4 до 4 (максимум 81 итерация, с отсечением углов ~50).
    // Это работает в 4 раза быстрее и не сажает FPS.
    for (float x = -4.0; x <= 4.0; x += 1.0) {
        for (float y = -4.0; y <= 4.0; y += 1.0) {
            // Отсекаем углы для формы идеального круга (4 * 4 = 16)
            if (x*x + y*y > 16.0) continue;

            vec2 offset = vec2(x, y) * stepSize * texel;

            float dist2 = (x*x + y*y) * (stepSize*stepSize);
            float w = exp(-0.5 * dist2 / (sigma*sigma));

            result += texture(tex, uv + offset) * w;
            totalWeight += w;
        }
    }

    return result / totalWeight;
}

void main() {
    vec2 halfSize = u_rect.zw * 0.5;
    vec2 center   = FragPos - u_rect.xy - halfSize;

    float dist = roundedBoxSDF(center, halfSize, u_radius);
    float mask = 1.0 - smoothstep(-0.5, 0.5, dist);
    if (mask <= 0.001) discard;

    vec2 resolution = vec2(textureSize(Sampler0, 0));
    if (resolution.x <= 1.0 || resolution.y <= 1.0) discard;

    vec2 uv    = vec2(gl_FragCoord.x, resolution.y - gl_FragCoord.y) / resolution;
    vec2 texel = 1.0 / resolution;

    float blurRadius = clamp(u_blur * 1.2, 1.0, 25.0);

    vec4 blurred = blur(Sampler0, uv, texel, blurRadius);

    // ФИКС ТЕМНОТЫ: Заменил 0.70 на 0.88.
    // Теперь фон значительно темнее, почти как легкий черный градиент, текст читается идеально.
    vec3 finalColor = mix(blurred.rgb, u_tint.rgb, u_tint.a * 0.88);

    fragColor = vec4(finalColor, mask * u_alpha);
}