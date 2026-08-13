#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

void main() {
    // Берем сырой цвет из твоего MSDF атласа
    vec3 msd = texture(Sampler0, texCoord).rgb;
    float sd = median(msd.r, msd.g, msd.b);

    // 8.0 — это distanceRange из твоего font.json!
    float screenPxDistance = 8.0 * (sd - 0.5);

    // Функция fwidth автоматически делает идеальное сглаживание при любом размере
    float edgeWidth = max(0.5 * fwidth(screenPxDistance), 0.01);
    float opacity = smoothstep(-edgeWidth, edgeWidth, screenPxDistance);

    // Убираем фон
    if (opacity < 0.01) discard;

    // Красим текст в цвет из Java и применяем прозрачность
    fragColor = vertexColor * ColorModulator;
    fragColor.a *= opacity;
}