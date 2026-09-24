#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

// Вычисление гладкого расстояния для одной точки UV с поддержкой MTSDF
float sampleDistance(vec2 uv, float blendToSdf) {
    vec4 msd = texture(Sampler0, uv);
    float msdfDist = median(msd.r, msd.g, msd.b);
    // При маленьких размерах плавно переходим на истинный SDF (alpha канал),
    // что на 100% убирает ступенчатость, изломы и артефакты мульти-канального MSDF
    return mix(msd.a, msdfDist, blendToSdf);
}

void main() {
    // 1. Точный расчет экранного масштаба (screen-space pixel range)
    // 8.0 — distanceRange из font.json, 1024.0 — размер атласа
    vec2 unitRange = vec2(8.0 / 1024.0);
    vec2 fw = max(fwidth(texCoord), vec2(0.000001));
    vec2 screenTexSize = vec2(1.0) / fw;

    // Эффективный диапазон сглаживания в экранных пикселях
    float pxRange = 0.5 * dot(unitRange, screenTexSize);

    // Для мелкого шрифта принудительно расширяем зону сглаживания до 1.35 пикселя,
    // чтобы мелкие буквы никогда не пикселились и оставались идеально гладкими
    float screenPxRange = max(pxRange, 1.35);

    // При маленьких размерах шрифта (pxRange < 2.5) плавно подмешиваем истинный SDF (канал A)
    float blendToSdf = clamp((pxRange - 0.75) / 1.75, 0.0, 1.0);

    // 2. 4x Rotated Grid Supersampling (RGSS) для безупречной субпиксельной гладкости
    vec2 dx = dFdx(texCoord);
    vec2 dy = dFdy(texCoord);

    // Смещения субпикселей по стандарту RGSS
    vec2 uv0 = texCoord + dx * -0.375 + dy * -0.125;
    vec2 uv1 = texCoord + dx * -0.125 + dy *  0.375;
    vec2 uv2 = texCoord + dx *  0.125 + dy * -0.375;
    vec2 uv3 = texCoord + dx *  0.375 + dy *  0.125;

    float sd0 = sampleDistance(uv0, blendToSdf);
    float sd1 = sampleDistance(uv1, blendToSdf);
    float sd2 = sampleDistance(uv2, blendToSdf);
    float sd3 = sampleDistance(uv3, blendToSdf);

    // Небольшой оптический бонус толщины (+0.035) для мелких букв, чтобы тонкие штрихи не съедались темным фоном
    float bias = mix(0.035, 0.0, clamp(pxRange / 3.5, 0.0, 1.0));

    float op0 = smoothstep(-0.5, 0.5, screenPxRange * (sd0 - 0.5 + bias));
    float op1 = smoothstep(-0.5, 0.5, screenPxRange * (sd1 - 0.5 + bias));
    float op2 = smoothstep(-0.5, 0.5, screenPxRange * (sd2 - 0.5 + bias));
    float op3 = smoothstep(-0.5, 0.5, screenPxRange * (sd3 - 0.5 + bias));

    float opacity = (op0 + op1 + op2 + op3) * 0.25;

    // Перцептивная гамма-коррекция для мелкого текста на темных фонах
    if (pxRange < 2.2) {
        opacity = pow(opacity, 0.85);
    }

    // Отсекаем полностью невидимые пиксели
    if (opacity < 0.005) discard;

    // Итоговый цвет с поддержкой ванильной модуляции цвета и альфы
    fragColor = vertexColor * ColorModulator;
    fragColor.a *= opacity;
}